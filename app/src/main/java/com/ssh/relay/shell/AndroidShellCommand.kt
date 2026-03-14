package com.ssh.relay.shell

import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream

/**
 * Command implementation that spawns a shell process using ProcessBuilder.
 * Relays I/O between the SSH channel streams and the process streams.
 * Falls back from PTY to pipe-based I/O for maximum Android compatibility.
 */
class AndroidShellCommand : Command {

    private val log = LoggerFactory.getLogger(AndroidShellCommand::class.java)

    private var sshIn: InputStream? = null
    private var sshOut: OutputStream? = null
    private var sshErr: OutputStream? = null
    private var exitCallback: ExitCallback? = null

    private var process: Process? = null
    private var readerThread: Thread? = null
    private var writerThread: Thread? = null
    private var errThread: Thread? = null
    private var waitThread: Thread? = null

    override fun setInputStream(input: InputStream) { sshIn = input }
    override fun setOutputStream(output: OutputStream) { sshOut = output }
    override fun setErrorStream(err: OutputStream) { sshErr = err }
    override fun setExitCallback(callback: ExitCallback) { exitCallback = callback }

    override fun start(channel: ChannelSession, env: Environment) {
        try {
            val home = com.ssh.relay.SshServerApp.instance.filesDir.absolutePath
            log.info("Starting shell, HOME={}", home)

            // Try PTY first, fall back to ProcessBuilder
            if (tryStartWithPty(env, home)) {
                log.info("Shell started with PTY")
                return
            }

            log.info("PTY unavailable, using ProcessBuilder")
            startWithProcessBuilder(home)

        } catch (e: Exception) {
            log.error("Failed to start shell", e)
            try {
                sshErr?.write("Error: ${e.message}\r\n".toByteArray())
                sshErr?.flush()
            } catch (_: Exception) {}
            exitCallback?.onExit(1, e.message)
        }
    }

    private fun tryStartWithPty(env: Environment, home: String): Boolean {
        return try {
            val result = PtyCompat.createSubprocess(
                "/system/bin/sh",
                arrayOf("-"),
                arrayOf(
                    "TERM=xterm-256color",
                    "HOME=$home",
                    "TMPDIR=$home/tmp",
                    "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin:/product/bin"
                )
            )
            val pid = result[0]
            val masterFd = result[1]

            if (pid <= 0 || masterFd < 0) {
                log.warn("PTY fork returned invalid pid={} fd={}", pid, masterFd)
                return false
            }

            log.info("PTY shell started: pid={}, masterFd={}", pid, masterFd)

            // Set window size
            val rows = env.env["LINES"]?.toIntOrNull() ?: 24
            val cols = env.env["COLUMNS"]?.toIntOrNull() ?: 80
            PtyCompat.setWindowSize(masterFd, rows, cols)

            // Create streams via ParcelFileDescriptor (Android's proper API)
            val pfd = android.os.ParcelFileDescriptor.adoptFd(masterFd)
            val ptyIn = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
            val ptyOut = java.io.FileOutputStream(pfd.fileDescriptor)

            // SSH -> PTY
            writerThread = Thread({
                try {
                    val buf = ByteArray(4096)
                    while (true) {
                        val n = sshIn?.read(buf) ?: -1
                        if (n <= 0) break
                        ptyOut.write(buf, 0, n)
                        ptyOut.flush()
                    }
                } catch (e: Exception) {
                    log.debug("ssh-to-pty ended: {}", e.message)
                }
            }, "ssh-to-pty-$pid").apply { isDaemon = true; start() }

            // PTY -> SSH
            readerThread = Thread({
                try {
                    val buf = ByteArray(4096)
                    while (true) {
                        val n = ptyIn.read(buf)
                        if (n <= 0) break
                        sshOut?.write(buf, 0, n)
                        sshOut?.flush()
                    }
                } catch (e: Exception) {
                    log.debug("pty-to-ssh ended: {}", e.message)
                }
                val exitCode = PtyCompat.waitFor(pid)
                log.info("PTY shell exited: pid={}, code={}", pid, exitCode)
                exitCallback?.onExit(exitCode)
            }, "pty-to-ssh-$pid").apply { isDaemon = true; start() }

            true
        } catch (e: Throwable) {
            log.warn("PTY startup failed: {}", e.message)
            false
        }
    }

    private fun startWithProcessBuilder(home: String) {
        val tmpDir = java.io.File(home, "tmp")
        tmpDir.mkdirs()

        val pb = ProcessBuilder("/system/bin/sh", "-")
            .redirectErrorStream(false)
        pb.environment().apply {
            put("HOME", home)
            put("TMPDIR", tmpDir.absolutePath)
            put("TERM", "dumb")
            put("PATH", "/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin:/product/bin")
        }
        pb.directory(java.io.File(home))

        val proc = pb.start()
        process = proc
        log.info("ProcessBuilder shell started")

        // SSH -> Process stdin
        writerThread = Thread({
            try {
                val buf = ByteArray(4096)
                val procOut = proc.outputStream
                while (proc.isAlive) {
                    val n = sshIn?.read(buf) ?: -1
                    if (n <= 0) break
                    procOut.write(buf, 0, n)
                    procOut.flush()
                }
            } catch (e: Exception) {
                log.debug("ssh-to-proc ended: {}", e.message)
            }
        }, "ssh-to-proc").apply { isDaemon = true; start() }

        // Process stdout -> SSH
        readerThread = Thread({
            try {
                val buf = ByteArray(4096)
                val procIn = proc.inputStream
                while (true) {
                    val n = procIn.read(buf)
                    if (n <= 0) break
                    sshOut?.write(buf, 0, n)
                    sshOut?.flush()
                }
            } catch (e: Exception) {
                log.debug("proc-to-ssh ended: {}", e.message)
            }
        }, "proc-stdout-to-ssh").apply { isDaemon = true; start() }

        // Process stderr -> SSH err
        errThread = Thread({
            try {
                val buf = ByteArray(4096)
                val procErr = proc.errorStream
                while (true) {
                    val n = procErr.read(buf)
                    if (n <= 0) break
                    sshErr?.write(buf, 0, n)
                    sshErr?.flush()
                }
            } catch (e: Exception) {
                log.debug("proc-stderr-to-ssh ended: {}", e.message)
            }
        }, "proc-stderr-to-ssh").apply { isDaemon = true; start() }

        // Wait for process exit
        waitThread = Thread({
            try {
                val exitCode = proc.waitFor()
                log.info("Shell process exited with code {}", exitCode)
                exitCallback?.onExit(exitCode)
            } catch (e: Exception) {
                log.debug("wait ended: {}", e.message)
                exitCallback?.onExit(1)
            }
        }, "proc-wait").apply { isDaemon = true; start() }
    }

    override fun destroy(channel: ChannelSession) {
        log.info("Destroying shell command")
        process?.destroyForcibly()
        readerThread?.interrupt()
        writerThread?.interrupt()
        errThread?.interrupt()
        waitThread?.interrupt()
    }
}
