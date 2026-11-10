package com.ssh.server.shell

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class ShellStartupTest {
    private fun run(script: String): Triple<Int, String, String> {
        assumeTrue("Requires a POSIX shell", File("/bin/sh").canExecute())
        val process = ProcessBuilder("/bin/sh", "-c", script).start()
        assertTrue(process.waitFor(5, TimeUnit.SECONDS))
        return Triple(process.exitValue(), process.inputStream.bufferedReader().readText(),
            process.errorStream.bufferedReader().readText())
    }

    @Test fun blankSettingLeavesCommandsUntouched() {
        assertEquals(ShellStartup.Issue.NONE, ShellStartup.validate(""))
        assertEquals("", ShellStartup.prelude("  "))
        assertEquals(Triple(0, "unchanged", ""), run(ShellStartup.prelude("") + "printf unchanged"))
    }

    @Test fun sourcedFunctionsAndVariablesAreAvailableToTheCommand() {
        val directory = Files.createTempDirectory("startup-fixture-").toFile()
        try {
            val file = File(directory, "init ' ; 中文.sh")
            file.writeText("fixture_value='loaded'\nfixture_command() { printf '%s' \"\$fixture_value\"; }\n")
            assertEquals(ShellStartup.Issue.NONE, ShellStartup.validate(file.absolutePath))
            assertEquals(Triple(0, "loaded", ""), run(ShellStartup.prelude(file.absolutePath) + "fixture_command"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun missingScriptWarnsWithoutBlockingLoginOrPollutingStdout() {
        val directory = Files.createTempDirectory("startup-missing-").toFile()
        try {
            val file = File(directory, "missing.sh")
            assertEquals(ShellStartup.Issue.READABLE_FILE, ShellStartup.validate(file.absolutePath))
            val result = run(ShellStartup.prelude(file.absolutePath) + "printf connected; exit 7")
            assertEquals(7, result.first)
            assertEquals("connected", result.second)
            assertTrue(result.third.contains("unavailable"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun pathsCannotInjectCommands() {
        val directory = Files.createTempDirectory("startup-injection-").toFile()
        try {
            val marker = File(directory, "must-not-exist")
            val path = "missing'; touch ${marker.absolutePath}; #"
            val result = run(ShellStartup.prelude(path) + "printf safe")
            assertEquals(0, result.first)
            assertEquals("safe", result.second)
            assertFalse(marker.exists())
            assertEquals(ShellStartup.Issue.ABSOLUTE_PATH, ShellStartup.validate("relative/init.sh"))
            assertEquals(ShellStartup.Issue.ABSOLUTE_PATH, ShellStartup.validate("/tmp/a\nb"))
        } finally { directory.deleteRecursively() }
    }
}
