package com.ssh.server.shell

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one exec shell and never signals it after its Process has exited. */
internal class ShellProcess(
    private val serverPid: Int,
    private val kill: (Int) -> Unit
) {
    companion object {
        // The bootstrap execs the requested shell, retaining its PID. Its private
        // stderr header is consumed before either output stream is relayed.
        const val BOOTSTRAP = "printf '%s\\n' \"\$\$\" >&2\nexec \"\$@\""
    }

    @Volatile private var process: Process? = null
    @Volatile private var pid: Int? = null
    private val closeRequested = AtomicBoolean()
    private val signalled = AtomicBoolean()

    fun attach(child: Process) {
        check(process == null) { "A shell process is already attached" }
        process = child
        val header = StringBuilder()
        while (header.length < 32) {
            val next = child.errorStream.read()
            if (next < 0 || next == 10) break
            header.append(next.toChar())
        }
        val parsed = header.toString().trim().toIntOrNull()
        if (parsed == null || parsed <= 1 || parsed == serverPid) {
            throw IOException("Cannot determine the SSH child process ID")
        }
        pid = parsed
        if (closeRequested.get()) stopNow()
    }

    fun awaitExit(disconnected: () -> Boolean): Int {
        val child = checkNotNull(process) { "No shell process is attached" }
        // Channel teardown may wait for command completion before destroy() is
        // invoked, so observe the closing state while waiting for the shell.
        while (!child.waitFor(250, TimeUnit.MILLISECONDS)) {
            if (disconnected()) stop()
        }
        return child.exitValue()
    }

    fun stop() {
        closeRequested.set(true)
        stopNow()
    }

    private fun stopNow() {
        val child = process ?: return
        try {
            synchronized(child) {
                val childPid = pid
                if (child.isAlive && childPid != null && signalled.compareAndSet(false, true)) {
                    // Android's Process.destroyForcibly() can delegate to SIGTERM,
                    // which a shell waiting for its foreground command may ignore.
                    kill(childPid)
                }
            }
        } finally {
            child.destroy()
        }
    }
}
