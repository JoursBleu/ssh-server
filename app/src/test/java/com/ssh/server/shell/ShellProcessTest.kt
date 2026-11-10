package com.ssh.server.shell

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ShellProcessTest {
    private fun start(command: String): Process {
        assumeTrue("Requires a POSIX shell", File("/bin/sh").canExecute())
        return ProcessBuilder(
            "/bin/sh", "-c", ShellProcess.BOOTSTRAP,
            "ssh-command", "/bin/sh", "-c", command
        ).start()
    }

    private fun hardKill(pid: Int) {
        assumeTrue("Requires kill", File("/bin/kill").canExecute())
        val kill = ProcessBuilder("/bin/kill", "-KILL", pid.toString()).start()
        assertTrue(kill.waitFor(3, TimeUnit.SECONDS))
        assertEquals(0, kill.exitValue())
    }

    @Test fun privatePidHeaderPreservesOutputAndExitStatus() {
        val child = start("printf 'output'; printf 'error' >&2; exit 7")
        val signalled = mutableListOf<Int>()
        val owner = ShellProcess(-1) { signalled.add(it); hardKill(it) }
        try {
            owner.attach(child)
            assertEquals(7, owner.awaitExit { false })
            assertEquals("output", child.inputStream.bufferedReader().readText())
            assertEquals("error", child.errorStream.bufferedReader().readText())
            owner.stop()
            assertTrue("Never signal an already-exited process", signalled.isEmpty())
        } finally { child.destroyForcibly() }
    }

    @Test fun disconnectKillsAShellThatIgnoresSigterm() {
        val child = start("trap '' TERM; printf '%s\\n' \"\$\$\"; read value")
        val disconnected = AtomicBoolean()
        val signalled = mutableListOf<Int>()
        val owner = ShellProcess(-1) { signalled.add(it); hardKill(it) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            owner.attach(child)
            val fixturePid = child.inputStream.bufferedReader().readLine().toInt()
            val wait = executor.submit<Int> { owner.awaitExit { disconnected.get() } }
            // Confirm this fixture really survives a soft termination signal.
            val term = ProcessBuilder("/bin/kill", "-TERM", fixturePid.toString()).start()
            assertTrue(term.waitFor(3, TimeUnit.SECONDS))
            assertEquals(0, term.exitValue())
            assertFalse(child.waitFor(150, TimeUnit.MILLISECONDS))
            disconnected.set(true)
            wait.get(3, TimeUnit.SECONDS)
            assertFalse(child.isAlive)
            assertEquals(listOf(fixturePid), signalled)
        } finally {
            child.destroyForcibly()
            executor.shutdownNow()
        }
    }

    @Test fun closeBeforeAttachStillTerminatesTheNewShell() {
        val child = start("read value")
        val owner = ShellProcess(-1) { hardKill(it) }
        try {
            owner.stop()
            owner.attach(child)
            assertTrue(child.waitFor(3, TimeUnit.SECONDS))
            assertFalse(child.isAlive)
        } finally { child.destroyForcibly() }
    }

    @Test fun invalidPidHeadersAreRejectedWithoutSignallingTheServer() {
        assumeTrue("Requires a POSIX shell", File("/bin/sh").canExecute())
        val child = ProcessBuilder("/bin/sh", "-c", "printf '1234\\n' >&2; exit 0").start()
        val signalled = mutableListOf<Int>()
        val owner = ShellProcess(1234) { signalled.add(it) }
        try {
            try {
                owner.attach(child)
                fail("The server PID must not be accepted as a child")
            } catch (_: IOException) { }
            assertTrue(child.waitFor(3, TimeUnit.SECONDS))
            owner.stop()
            assertTrue(signalled.isEmpty())
        } finally { child.destroyForcibly() }
    }
}
