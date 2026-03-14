package com.ssh.relay.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

const val SETTINGS_PREFS = "ssh_server_prefs"
const val KEY_HOME_DIR = "home_directory"
const val KEY_SHELL = "default_shell"
const val KEY_AUTO_START = "auto_start_on_boot"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE) }
    val langState = LocalLanguage.current
    val lang = langState.value  // read to trigger recomposition

    var homeDir by remember { mutableStateOf(prefs.getString(KEY_HOME_DIR, "/sdcard") ?: "/sdcard") }
    var shell by remember { mutableStateOf(prefs.getString(KEY_SHELL, "/system/bin/sh") ?: "/system/bin/sh") }
    var autoStart by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_START, false)) }

    // Available shells
    val availableShells = remember { detectAvailableShells() }

    // Version info
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }
    }
    val versionCode = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toString()
            }
        } catch (_: Exception) { "?" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(S.settings, style = MaterialTheme.typography.headlineMedium)

        // ====== Shell Settings ======
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(S.shell, style = MaterialTheme.typography.titleMedium)
                }

                OutlinedTextField(
                    value = homeDir,
                    onValueChange = {
                        homeDir = it
                        prefs.edit().putString(KEY_HOME_DIR, it).apply()
                    },
                    label = { Text(S.homeDir) },
                    leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(S.homeDirHint) }
                )

                // Shell selector
                var shellExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = shellExpanded,
                    onExpandedChange = { shellExpanded = !shellExpanded }
                ) {
                    OutlinedTextField(
                        value = shell,
                        onValueChange = {
                            shell = it
                            prefs.edit().putString(KEY_SHELL, it).apply()
                        },
                        label = { Text(S.defaultShell) },
                        leadingIcon = { Icon(Icons.Default.Terminal, null, Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shellExpanded) },
                        supportingText = { Text(S.shellHint) }
                    )
                    ExposedDropdownMenu(
                        expanded = shellExpanded,
                        onDismissRequest = { shellExpanded = false }
                    ) {
                        availableShells.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    shell = s
                                    prefs.edit().putString(KEY_SHELL, s).apply()
                                    shellExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // ====== Boot Settings ======
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestartAlt, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(S.boot, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S.autoStartOnBoot, style = MaterialTheme.typography.bodyLarge)
                        Text(S.autoStartHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoStart,
                        onCheckedChange = {
                            autoStart = it
                            prefs.edit().putBoolean(KEY_AUTO_START, it).apply()
                        }
                    )
                }
            }
        }

        // ====== Language ======
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(S.language, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Language.entries.forEach { l ->
                        FilterChip(
                            selected = lang == l,
                            onClick = {
                                S.currentLang = l
                                langState.value = l
                                saveLanguage(context, l)
                            },
                            label = { Text(l.label) }
                        )
                    }
                }
            }
        }

        // ====== About ======
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(S.about, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                SettingsInfoRow(S.appName, "SSH Server")
                SettingsInfoRow(S.version, "$versionName ($versionCode)")
                SettingsInfoRow(S.packageName, context.packageName)
                SettingsInfoRow(S.sshEngine, "Apache MINA SSHD 2.17.1")
                SettingsInfoRow(S.cryptoLib, "BouncyCastle 1.80")
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun detectAvailableShells(): List<String> {
    val candidates = listOf(
        "/system/bin/sh",
        "/system/bin/mksh",
        "/system/bin/bash",
        "/system/xbin/bash",
        "/data/data/com.termux/files/usr/bin/bash",
        "/data/data/com.termux/files/usr/bin/zsh",
    )
    return candidates.filter { File(it).exists() }.ifEmpty { listOf("/system/bin/sh") }
}
