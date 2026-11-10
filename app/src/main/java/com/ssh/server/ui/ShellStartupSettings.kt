package com.ssh.server.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ssh.server.shell.ShellStartup

@Composable
internal fun ShellStartupSettings() {
    val context = LocalContext.current
    val lang = LocalLanguage.current.value // Observe runtime language changes.
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS, 0) }
    var savedPath by remember { mutableStateOf(prefs.getString(ShellStartup.KEY, "").orEmpty()) }
    var path by remember { mutableStateOf(savedPath) }
    var issue by remember { mutableStateOf(ShellStartup.Issue.NONE) }
    var saved by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val save: () -> Unit = {
        issue = ShellStartup.validate(path)
        if (issue == ShellStartup.Issue.NONE) {
            val normalized = path.trim()
            prefs.edit().putString(ShellStartup.KEY, normalized).apply()
            path = normalized
            savedPath = normalized
            saved = true
        }
    }

    OutlinedTextField(
        value = path,
        onValueChange = {
            path = it
            saved = false
            issue = ShellStartup.Issue.NONE
        },
        label = { Text(S.startupScript) },
        modifier = Modifier.fillMaxWidth().onFocusChanged {
            if (focused && !it.isFocused) issue = ShellStartup.validate(path)
            focused = it.isFocused
        },
        singleLine = true,
        isError = issue != ShellStartup.Issue.NONE,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        supportingText = {
            Text(when (issue) {
                ShellStartup.Issue.ABSOLUTE_PATH -> S.startupAbsolutePath
                ShellStartup.Issue.READABLE_FILE -> S.startupReadableFile
                ShellStartup.Issue.NONE -> S.startupScriptHint
            })
        }
    )
    Button(
        onClick = save,
        enabled = path != savedPath,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) { Text(S.saveStartupScript) }
    if (saved) {
        Text(
            S.startupSaved,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}
