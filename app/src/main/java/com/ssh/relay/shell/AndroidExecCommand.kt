package com.ssh.relay.shell

import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Executes a single command via shell -c "command".
 * Used by ssh-copy-id, scp, and any other exec-based SSH operations.
 *
 * Special handling: commands referencing .ssh/authorized_keys are rewritten
 * to use the app's internal directory, since /sdcard doesn't support mkdir.
 */
class AndroidExecCommand(private val command: String) : Command {

    private val log = LoggerFactory.getLogger(AndroidExecCommand::class.java)

    private var sshIn: InputStream? = null
    private var sshOut: OutputStream? = null
    private var sshErr: OutputStream? = null
    private var exitCallback: ExitCallback? = null

    private var process: Process? = null

    override fun setInputStream(input: InputStream) { sshIn = input }
    override fun setOutputStream(output: OutputStream) { sshOut = output }
    override fun setErrorStream(err: OutputStream) { sshErr = err }
    override fun setExitCallback(callback: ExitCallback) { exitCallback = callback }

    override fun start(channel: ChannelSession, env: Environment) {
        Thread({
            try {
                val home = AndroidShellCommand.getHomeDir()
                val shell = AndroidShellCommand.getShell()

                // App internal .ssh directory (where AuthorizedKeysManager stores keys)
                val appHome = com.ssh.relay.SshServerApp.instance.filesDir.absolutePath
                val sshDir = File(appHome, ".ssh")
                sshDir.mkdirs()

                // Rewrite command: replace .ssh/authorized_keys paths with app internal path
                // ssh-copy-id sends commands like:
                //   exec sh -c 'mkdir -p .ssh && cat >> .ssh/authorized_keys ...'
                //   or: sh -c 'umask 077; ... mkdir -p .ssh && cat >> .ssh/authorized_keys'
                val actualCommand = rewriteSshCopyIdCommand(command, sshDir.absolutePath)

                log.info("Executing: {} -c '{}' (HOME={}, original='{}')", shell, actualCommand, home, command)

                val pb = ProcessBuilder(shell, "-c", actualCommand)
                    .redirectErrorStream(false)
                pb.environment().apply {
                    put("HOME", appHome)  // Use app home for exec so .ssh resolves correctly
                    put("TMPDIR", File(home, "tmp").apply { mkdirs() }.absolutePath)
                    put("PATH", "/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin:/product/bin")
                    put("SHELL", shell)
                    put("USER", "shell")
                }
                pb.directory(File(appHome))  // Working dir = app home for .ssh relative paths

                val proc = pb.start()
                process = proc

                // Pipe SSH stdin -> process stdin (for ssh-copy-id piping the key)
                val stdinThread = Thread({
                    try {
                        val buf = ByteArray(4096)
                        val procOut = proc.outputStream
                        while (true) {
                            val n = sshIn?.read(buf) ?: -1
                            if (n <= 0) break
                            procOut.write(buf, 0, n)
                            procOut.flush()
                        }
                        procOut.close()
                    } catch (e: Exception) {
                        log.debug("exec stdin relay ended: {}", e.message)
                    }
                }, "exec-stdin").apply { isDaemon = true; start() }

                // Process stdout -> SSH stdout
                val stdoutThread = Thread({
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
                        log.debug("exec stdout relay ended: {}", e.message)
                    }
                }, "exec-stdout").apply { isDaemon = true; start() }

                // Process stderr -> SSH stderr
                val stderrThread = Thread({
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
                        log.debug("exec stderr relay ended: {}", e.message)
                    }
                }, "exec-stderr").apply { isDaemon = true; start() }

                // Wait for process to finish
                val exitCode = proc.waitFor()
                // Wait for I/O threads to drain
                stdoutThread.join(2000)
                stderrThread.join(2000)

                log.info("Exec finished: exitCode={}, cmd='{}'", exitCode, actualCommand)
                exitCallback?.onExit(exitCode)

            } catch (e: Exception) {
                log.error("Exec failed: {}", e.message, e)
                try {
                    sshErr?.write("Error: ${e.message}\r\n".toByteArray())
                    sshErr?.flush()
                } catch (_: Exception) {}
                exitCallback?.onExit(1, e.message)
            }
        }, "exec-cmd").apply { isDaemon = true; start() }
    }

    override fun destroy(channel: ChannelSession) {
        process?.destroyForcibly()
    }

    companion object {
        /**
         * Rewrite ssh-copy-id style commands to use the app's internal .ssh directory.
         * Replaces relative .ssh paths with absolute paths to the app's .ssh dir.
         */
        fun rewriteSshCopyIdCommand(cmd: String, absSshDir: String): String {
            // If the command references .ssh/authorized_keys, rewrite paths
            if (!cmd.contains("authorized_keys")) return cmd

            return cmd
                .replace("~/.ssh/authorized_keys", "$absSshDir/authorized_keys")
                .replace("\$HOME/.ssh/authorized_keys", "$absSshDir/authorized_keys")
                .replace(".ssh/authorized_keys", "$absSshDir/authorized_keys")
                .replace("mkdir -p .ssh", "mkdir -p $absSshDir")
                .replace("mkdir -p ~/.ssh", "mkdir -p $absSshDir")
        }
    }
}
