package com.ssh.server.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ssh.server.engine.SessionInfo
import com.ssh.server.service.SshServerService
import com.ssh.server.service.SshServerService.ServerState
import java.io.File
import java.io.ObjectInputStream
import java.security.KeyPair
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.*

private const val PREFS_NAME = "ssh_server_prefs"
private const val KEY_PORT = "port"
private const val KEY_USERNAME = "username"
private const val KEY_PASSWORD = "password"
private const val KEY_HIDE_BG_TIP = "hide_background_tip"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val langState = LocalLanguage.current
    val lang = langState.value  // read to trigger recomposition

    // Background tip dialog
    var showBgTipDialog by remember { mutableStateOf(!prefs.getBoolean(KEY_HIDE_BG_TIP, false)) }

    // Poll server state every 500ms
    var serverState by remember { mutableStateOf(SshServerService.serverState) }
    var lastError by remember { mutableStateOf(SshServerService.lastError) }
    var sessions by remember { mutableStateOf<Set<SessionInfo>>(SshServerService.activeSessions) }

    LaunchedEffect(Unit) {
        while (true) {
            serverState = SshServerService.serverState
            lastError = SshServerService.lastError
            sessions = SshServerService.activeSessions
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

    // Host key fingerprint
    val fingerprint = remember { getHostKeyFingerprint(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied */ }

    // Language switch menu
    var showLangMenu by remember { mutableStateOf(false) }

    // Background keep-alive tip dialog
    if (showBgTipDialog) {
        AlertDialog(
            onDismissRequest = { showBgTipDialog = false },
            title = { Text(S.bgTipTitle) },
            text = {
                Text(S.bgTipContent, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { showBgTipDialog = false }) {
                    Text(S.bgTipOk)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean(KEY_HIDE_BG_TIP, true).apply()
                    showBgTipDialog = false
                }) {
                    Text(S.bgTipDontShow)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with language switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(S.sshServer, style = MaterialTheme.typography.headlineMedium)
            Box {
                IconButton(onClick = { showLangMenu = true }) {
                    Icon(Icons.Default.Language, contentDescription = S.language)
                }
                DropdownMenu(expanded = showLangMenu, onDismissRequest = { showLangMenu = false }) {
                    Language.entries.forEach { l ->
                        DropdownMenuItem(
                            text = { Text(l.label) },
                            onClick = {
                                S.currentLang = l
                                langState.value = l
                                saveLanguage(context, l)
                                showLangMenu = false
                            },
                            leadingIcon = {
                                if (l == lang) {
                                    Icon(Icons.Default.Language, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
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

        // Status card (merged with active sessions)
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
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTransitioning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            buildAnnotatedString {
                                when (serverState) {
                                    ServerState.STOPPED -> {
                                        withStyle(SpanStyle(color = Color.Gray)) { append("● ") }
                                        append(if (S.currentLang == Language.ZH) "已停止" else "Stopped")
                                    }
                                    ServerState.STARTING -> append(S.starting)
                                    ServerState.RUNNING -> {
                                        withStyle(SpanStyle(color = Color(0xFF4CAF50))) { append("● ") }
                                        append(if (S.currentLang == Language.ZH) "运行中" else "Running")
                                    }
                                    ServerState.STOPPING -> append(S.stopping)
                                    ServerState.ERROR -> append(S.error)
                                }
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (isRunning) {
                            val authMethods = buildList {
                                if (SshServerService.currentPass.isNotEmpty()) add("password")
                                add("publickey")
                            }.joinToString(" + ")
                            Text(
                                "Port ${SshServerService.currentPort} · User: ${SshServerService.currentUser} · Auth: $authMethods",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                // Active sessions (inline)
                if (isRunning) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text("${S.sessions}: ${sessions.size}", style = MaterialTheme.typography.titleSmall)
                    }
                    if (sessions.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        sessions.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${s.username}@${s.remoteAddress}", style = MaterialTheme.typography.bodySmall)
                                Text(dateFormat.format(Date(s.connectedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            }
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(S.noActiveConnections, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Config fields
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text(S.port) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && !isTransitioning
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(S.username) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && !isTransitioning
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(S.password) },
            placeholder = { Text(S.passwordHintEmpty) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) S.hide else S.show)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && !isTransitioning
        )

        if (password.isEmpty() && !isRunning && !isTransitioning) {
            Text(S.passwordAuthDisabled, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Host Key fingerprint (bottom)
        if (fingerprint != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(S.hostKeyFingerprint, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("RSA SHA-256", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString(fingerprint))
                                Toast.makeText(context, S.fingerprintCopied, Toast.LENGTH_SHORT).show()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            fingerprint,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(fingerprint))
                            Toast.makeText(context, S.fingerprintCopied, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, S.copy, Modifier.size(18.dp))
                        }
                    }
                }
            }
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
                    ServerState.STARTING -> S.starting
                    ServerState.STOPPING -> S.stopping
                    ServerState.RUNNING -> S.stopServer
                    else -> S.startServer
                }
            )
        }
    }
}

private fun getHostKeyFingerprint(context: Context): String? {
    return try {
        val keyFile = File(context.filesDir, "hostkeys/host_rsa.ser")
        if (!keyFile.exists()) return null
        val keyPair = ObjectInputStream(keyFile.inputStream().buffered()).use { it.readObject() as KeyPair }
        val pubKey = keyPair.public
        val blob = when (pubKey) {
            is RSAPublicKey -> {
                val typeBytes = "ssh-rsa".toByteArray()
                val eBytes = pubKey.publicExponent.toByteArray()
                val nBytes = pubKey.modulus.toByteArray()
                buildSshBlob(typeBytes, eBytes, nBytes)
            }
            is ECPublicKey -> {
                val typeBytes = "ecdsa-sha2-nistp256".toByteArray()
                val idBytes = "nistp256".toByteArray()
                val point = pubKey.w
                val fieldSize = (pubKey.params.curve.field as java.security.spec.ECFieldFp).p.bitLength()
                val byteLen = (fieldSize + 7) / 8
                val xBytes = point.affineX.toByteArray().let { if (it.size > byteLen) it.takeLast(byteLen).toByteArray() else it }
                val yBytes = point.affineY.toByteArray().let { if (it.size > byteLen) it.takeLast(byteLen).toByteArray() else it }
                val qBuf = ByteArray(1 + byteLen * 2)
                qBuf[0] = 0x04
                System.arraycopy(xBytes, 0, qBuf, 1 + byteLen - xBytes.size, xBytes.size)
                System.arraycopy(yBytes, 0, qBuf, 1 + byteLen * 2 - yBytes.size, yBytes.size)
                buildSshBlob(typeBytes, idBytes, qBuf)
            }
            else -> pubKey.encoded
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        "SHA256:" + java.util.Base64.getEncoder().encodeToString(digest).trimEnd('=')
    } catch (_: Exception) { null }
}

private fun buildSshBlob(vararg parts: ByteArray): ByteArray {
    val total = parts.sumOf { 4 + it.size }
    val buf = java.nio.ByteBuffer.allocate(total)
    for (part in parts) { buf.putInt(part.size); buf.put(part) }
    return buf.array()
}
