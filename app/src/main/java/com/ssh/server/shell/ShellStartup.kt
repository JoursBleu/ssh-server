package com.ssh.server.shell

import android.content.Context
import com.ssh.server.SshServerApp
import java.io.File

/** Optional, user-configured POSIX shell initialization; no knowledge of external tools. */
object ShellStartup {
    const val KEY = "shell_startup_script"
    enum class Issue { NONE, ABSOLUTE_PATH, READABLE_FILE }

    fun configuredPath(): String = SshServerApp.instance
        .getSharedPreferences("ssh_server_prefs", Context.MODE_PRIVATE)
        .getString(KEY, "").orEmpty().trim()

    fun validate(value: String): Issue {
        val path = value.trim()
        if (path.isEmpty()) return Issue.NONE
        if (path.any { it == '\n' || it == '\r' || it == '\u0000' } || !File(path).isAbsolute) {
            return Issue.ABSOLUTE_PATH
        }
        return if (File(path).isFile && File(path).canRead()) Issue.NONE else Issue.READABLE_FILE
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    fun prelude(value: String): String {
        val path = value.trim()
        if (path.isEmpty()) return ""
        val quoted = quote(path)
        // Check on every connection: a deleted script must not lock the user out.
        // Keep diagnostics on stderr so a missing file does not corrupt remote stdout.
        return "if [ -f $quoted ] && [ -r $quoted ]; then . $quoted; " +
            "else printf '%s\\n' 'SSH startup script is unavailable; continuing without it.' >&2; fi\n"
    }
}
