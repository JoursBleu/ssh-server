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
import androidx.compose.material.icons.filled.BatteryAlert
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

    var isRunning by remember { mutableStateOf(SshServerService.isRunning) }
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
    var statusText by remember { mutableStateOf(
        if (SshServerService.isRunning) {
            val authMethods = buildList {
                if (SshServerService.currentPass.isNotEmpty()) add("password")
                add("publickey")
            }.joinToString(" + ")
            "Listening on port ${SshServerService.currentPort}\nUser: ${SshServerService.currentUser}\nAuth: $authMethods"
        } else "Server stopped"
    ) }

    var sessions by remember { mutableStateOf<Set<SessionInfo>>(emptySet()) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (true) {
                sessions = SshServerService.activeSessions
                kotlinx.coroutines.delay(1000)
            }
        } else {
            sessions = emptySet()
        }
    }

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

        // Battery optimization warning
        if (isBatteryOptimized) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryAlert, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("请关闭电池优化", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("否则切换到其他应用后 SSH 连接会中断。", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        settingsLauncher.launch(intent)
                    }) {
                        Icon(Icons.Default.BatteryAlert, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("关闭电池优化")
                    }
                }
            }
        }

        // Manufacturer-specific background tips
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text("后台保活设置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "华为/荣耀：设置 → 应用启动管理 → SSH Server → 手动管理 → 打开全部开关\n" +
                    "小米/红米：设置 → 应用设置 → 省电策略 → 无限制\n" +
                    "OPPO/一加：设置 → 电池 → 后台耗电管理 → 允许后台运行\n" +
                    "vivo：设置 → 电池 → 后台高耗电 → 允许",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Try to open manufacturer-specific settings
                    OutlinedButton(onClick = {
                        tryOpenBackgroundSettings(context)
                    }) {
                        Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("后台设置")
                    }
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        settingsLauncher.launch(intent)
                    }) {
                        Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("应用详情")
                    }
                }
            }
        }

        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isRunning)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (isRunning) "● Running" else "○ Stopped",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(statusText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Active sessions
        if (isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Active Sessions: ${sessions.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    if (sessions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        sessions.forEach { session ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${session.username}@${session.remoteAddress}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(dateFormat.format(Date(session.connectedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
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
            enabled = !isRunning
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            placeholder = { Text("Leave empty to disable password auth") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        )

        if (password.isEmpty() && !isRunning) {
            Text("Password auth disabled. Use SSH keys to connect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.weight(1f))

        // Start / Stop button
        Button(
            onClick = {
                if (isRunning) {
                    SshServerService.stop(context)
                    isRunning = false
                    statusText = "Server stopped"
                    sessions = emptySet()
                } else {
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

                    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        } catch (_: Exception) {}
                    }

                    val p = port.toIntOrNull() ?: 2222
                    SshServerService.start(context, p, username, password)
                    isRunning = true
                    isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
                    val authMethods = buildList {
                        if (password.isNotEmpty()) add("password")
                        add("publickey")
                    }.joinToString(" + ")
                    statusText = "Listening on port $p\nUser: $username\nAuth: $authMethods"
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isRunning) "Stop Server" else "Start Server")
        }
    }
}

/** Try to open manufacturer-specific background/autostart settings */
private fun tryOpenBackgroundSettings(context: Context) {
    val intents = listOf(
        // Huawei
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
        // Xiaomi
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        // OPPO
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        // vivo
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        // Samsung
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
    // Fallback: open app detail settings
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {}
}
