package com.ssh.relay.shell

import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.slf4j.LoggerFactory
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Command implementation that spawns a PTY-backed shell process.
 * Relays I/O between the SSH channel streams and the native shell.
 */
class AndroidShellCommand : Command {

    private val log = LoggerFactory.getLogger(AndroidShellCommand::class.java)

    private var sshIn: InputStream? = null
    private var sshOut: OutputStream? = null
    private var sshErr: OutputStream? = null
    private var exitCallback: ExitCallback? = null

    private var pid: Int = -1
    private var masterFd: Int = -1
    private var readerThread: Thread? = null
    private var writerThread: Thread? = null

    override fun setInputStream(input: InputStream) { sshIn = input }
    override fun setOutputStream(output: OutputStream) { sshOut = output }
    override fun setErrorStream(err: OutputStream) { sshErr = err }
    override fun setExitCallback(callback: ExitCallback) { exitCallback = callback }

    override fun start(channel: ChannelSession, env: Environment) {
        try {
            // Spawn a PTY subprocess running /system/bin/sh
            val result = PtyCompat.createSubprocess(
                "/system/bin/sh",
                arrayOf("-l"),
                arrayOf(
                    "TERM=xterm-256color",
                    "HOME=${com.ssh.relay.SshRelayApp.instance.filesDir.absolutePath}",
                    "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin"
                )
            )
            pid = result[0]
            masterFd = result[1]
            log.info("Shell started: pid={}, masterFd={}", pid, masterFd)

            // Set initial window size
            val rows = env.env["LINES"]?.toIntOrNull() ?: 24
            val cols = env.env["COLUMNS"]?.toIntOrNull() ?: 80
            PtyCompat.setWindowSize(masterFd, rows, cols)

            // Create streams from the master PTY fd
            val masterFdObj = createFileDescriptor(masterFd)
            val ptyIn = FileInputStream(masterFdObj)
            val ptyOut = FileOutputStream(masterFdObj)

            // SSH -> PTY (what the user types)
            writerThread = Thread({
                try {
                    val buf = ByteArray(4096)
                    while (true) {
                        val n = sshIn?.read(buf) ?: -1
                        if (n <= 0) break
                        ptyOut.write(buf, 0, n)
                        ptyOut.flush()
                    }
                } catch (_: Exception) {}
            }, "ssh-to-pty-$pid").apply { isDaemon = true; start() }

            // PTY -> SSH (shell output)
            readerThread = Thread({
                try {
                    val buf = ByteArray(4096)
                    while (true) {
                        val n = ptyIn.read(buf)
                        if (n <= 0) break
                        sshOut?.write(buf, 0, n)
                        sshOut?.flush()
                    }
                } catch (_: Exception) {}
                // Shell exited
                val exitCode = PtyCompat.waitFor(pid)
                PtyCompat.closeFd(masterFd)
                log.info("Shell exited: pid={}, exitCode={}", pid, exitCode)
                exitCallback?.onExit(exitCode)
            }, "pty-to-ssh-$pid").apply { isDaemon = true; start() }

        } catch (e: Exception) {
            log.error("Failed to start shell", e)
            exitCallback?.onExit(1, e.message)
        }
    }

    override fun destroy(channel: ChannelSession) {
        if (pid > 0) {
            try { PtyCompat.sendSignal(pid, 9) } catch (_: Exception) {}
        }
        readerThread?.interrupt()
        writerThread?.interrupt()
    }

    private fun createFileDescriptor(fd: Int): FileDescriptor {
        val fileDescriptor = FileDescriptor()
        val field = FileDescriptor::class.java.getDeclaredField("fd")
        field.isAccessible = true
        field.setInt(fileDescriptor, fd)
        return fileDescriptor
    }
}
