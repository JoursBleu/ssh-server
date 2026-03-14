package com.ssh.relay.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ssh.relay.engine.SessionInfo
import com.ssh.relay.service.SshServerService
import com.ssh.relay.service.SshServerService.ServerState
import java.text.SimpleDateFormat
import java.util.*

private const val PREFS_NAME = "ssh_server_prefs"
private const val KEY_PORT = "port"
private const val KEY_USERNAME = "username"
private const val KEY_PASSWORD = "password"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isBatteryOptimized by remember {
        mutableStateOf(!pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    // Poll server state every 500ms
    var serverState by remember { mutableStateOf(SshServerService.serverState) }
    var lastError by remember { mutableStateOf(SshServerService.lastError) }
    var sessions by remember { mutableStateOf<Set<SessionInfo>>(SshServerService.activeSessions) }

    LaunchedEffect(Unit) {
        while (true) {
            serverState = SshServerService.serverState
            lastError = SshServerService.lastError
            sessions = SshServerService.activeSessions
            isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
            kotlinx.coroutines.delay(500)
        }
    }

    val isRunning = serverState == ServerState.RUNNING
    val isTransitioning = serverState == ServerState.STARTING || serverState == ServerState.STOPPING

    var port by remember { mutableStateOf(
        when {
            SshServerService.isRunning -> SshServerService.currentPort.toString()
            else -> prefs.getString(KEY_PORT, "2222") ?: "2222"
        }
    ) }
    var username by remember { mutableStateOf(
        when {
            SshServerService.isRunning -> SshServerService.currentUser
            else -> prefs.getString(KEY_USERNAME, "red") ?: "red"
        }
    ) }
    var password by remember { mutableStateOf(
        when {
            SshServerService.isRunning -> SshServerService.currentPass
            else -> prefs.getString(KEY_PASSWORD, "") ?: ""
        }
    ) }
    var showPassword by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied */ }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SSH Server", style = MaterialTheme.typography.headlineMedium)

        // Background keep-alive tips (merged battery optimization + manufacturer tips)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isBatteryOptimized) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning, null,
                        tint = if (isBatteryOptimized) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "后台保活设置",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isBatteryOptimized) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(4.dp))

                if (isBatteryOptimized) {
                    Text(
                        "⚠ 电池优化未关闭，切到后台后 SSH 连接可能中断",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    "华为/荣耀：设置 → 应用启动管理 → 手动管理 → 全部开启\n" +
                    "小米/红米：设置 → 省电策略 → 无限制\n" +
                    "OPPO/vivo：设置 → 电池 → 允许后台运行",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isBatteryOptimized) {
                        OutlinedButton(onClick = {
                            settingsLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        }) {
                            Text("关闭电池优化")
                        }
                    }
                    OutlinedButton(onClick = { tryOpenBackgroundSettings(context) }) {
                        Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("后台设置")
                    }
                    OutlinedButton(onClick = {
                        settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    }) {
                        Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("应用详情")
                    }
                }
            }
        }

        // Error card
        if (serverState == ServerState.ERROR && lastError != null) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(lastError!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (serverState) {
                    ServerState.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                    ServerState.ERROR -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isTransitioning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(
                        when (serverState) {
                            ServerState.STOPPED -> "○ Stopped"
                            ServerState.STARTING -> "Starting..."
                            ServerState.RUNNING -> "● Running"
                            ServerState.STOPPING -> "Stopping..."
                            ServerState.ERROR -> "✕ Error"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isRunning) {
                        val authMethods = buildList {
                            if (SshServerService.currentPass.isNotEmpty()) add("password")
                            add("publickey")
                        }.joinToString(" + ")
                        Text("Port ${SshServerService.currentPort} · User: ${SshServerService.currentUser} · Auth: $authMethods",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Active sessions
        if (isRunning) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Active Sessions: ${sessions.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    if (sessions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        sessions.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${s.username}@${s.remoteAddress}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(dateFormat.format(Date(s.connectedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                            }
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text("No active connections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Config fields
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && !isTransitioning
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && !isTransitioning
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            placeholder = { Text("Leave empty to disable password auth") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && !isTransitioning
        )

        if (password.isEmpty() && !isRunning && !isTransitioning) {
            Text("Password auth disabled. Use SSH keys.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.weight(1f))

        // Start / Stop button
        Button(
            onClick = {
                if (isRunning) {
                    SshServerService.stop(context)
                } else if (!isTransitioning) {
                    prefs.edit()
                        .putString(KEY_PORT, port)
                        .putString(KEY_USERNAME, username)
                        .putString(KEY_PASSWORD, password)
                        .apply()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    if (isBatteryOptimized) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        } catch (_: Exception) {}
                    }

                    val p = port.toIntOrNull() ?: 2222
                    SshServerService.start(context, p, username, password)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isTransitioning,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            if (isTransitioning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(24.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when (serverState) {
                    ServerState.STARTING -> "Starting..."
                    ServerState.STOPPING -> "Stopping..."
                    ServerState.RUNNING -> "Stop Server"
                    else -> "Start Server"
                }
            )
        }
    }
}

private fun tryOpenBackgroundSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
    )
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}
    }
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {}
}
