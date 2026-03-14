package com.ssh.server.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssh.server.engine.AuthorizedKeysManager
import com.ssh.server.engine.SshServerEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keysManager = remember { AuthorizedKeysManager() }
    var authorizedKeys by remember { mutableStateOf(keysManager.getAuthorizedKeys()) }
    var showAddKeyDialog by remember { mutableStateOf(false) }

    // Read language to trigger recomposition
    val langState = LocalLanguage.current
    val currentLang = langState.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== Authorized Keys Section =====
        Text(S.authorizedKeys, style = MaterialTheme.typography.headlineMedium)

        Text(
            S.keysDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Add key button
        FilledTonalButton(
            onClick = { showAddKeyDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(S.addPublicKey)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (authorizedKeys.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(S.noAuthorizedKeys, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        S.noKeysHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                S.keysConfigured(authorizedKeys.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            authorizedKeys.forEachIndexed { index, keyLine ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                AuthorizedKeysManager.extractComment(keyLine),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                AuthorizedKeysManager.extractKeyType(keyLine),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                AuthorizedKeysManager.shortFingerprint(keyLine),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Copy key
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(keyLine))
                            Toast.makeText(context, S.keyCopied, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = S.copy,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Delete key
                        IconButton(onClick = {
                            keysManager.removeKey(index)
                            authorizedKeys = keysManager.getAuthorizedKeys()
                            Toast.makeText(context, S.keyRemoved, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = S.remove,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ===== Host Key Section =====
        HostKeyCard(clipboardManager, context)
    }

    // Add Key Dialog
    if (showAddKeyDialog) {
        AddKeyDialog(
            onDismiss = { showAddKeyDialog = false },
            onAdd = { keyLine ->
                if (keysManager.addKey(keyLine)) {
                    authorizedKeys = keysManager.getAuthorizedKeys()
                    showAddKeyDialog = false
                    Toast.makeText(context, S.keyAdded, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, S.invalidKey, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun HostKeyCard(
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context
) {
    val privateKeyPem = remember { SshServerEngine.getHostPrivateKeyPem() }
    val publicKeyOpenSSH = remember { SshServerEngine.getHostPublicKeyOpenSSH() }
    var showPrivateKey by remember { mutableStateOf(false) }

    Text(S.hostKey, style = MaterialTheme.typography.headlineMedium)
    Text(
        S.hostKeyDescription,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (privateKeyPem == null || publicKeyOpenSSH == null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(S.hostKeyNotGenerated, style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        // Public key card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.hostPublicKey, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(publicKeyOpenSSH))
                        Toast.makeText(context, S.publicKeyCopied, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = S.copy,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    publicKeyOpenSSH,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Private key card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.hostPrivateKey, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        showPrivateKey = !showPrivateKey
                    }) {
                        Icon(
                            if (showPrivateKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPrivateKey) S.hidePrivateKey else S.showPrivateKey,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(privateKeyPem))
                        Toast.makeText(context, S.privateKeyCopied, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = S.copy,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showPrivateKey) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            privateKeyPem,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "••••••••••••••••••••",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AddKeyDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var keyText by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    // Auto-extract comment from pasted key
    val pastedComment = remember(keyText) {
        val parts = keyText.trim().split(" ", limit = 3)
        if (parts.size >= 3) parts[2] else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text(S.addKeyTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(S.addKeyHint, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text(S.publicKey) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 6
                )
                TextButton(
                    onClick = {
                        clipboardManager.getText()?.text?.let { keyText = it }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(S.pasteFromClipboard)
                }
                OutlinedTextField(
                    value = if (pastedComment.isNotEmpty()) pastedComment else comment,
                    onValueChange = { if (pastedComment.isEmpty()) comment = it },
                    label = { Text(S.comment) },
                    placeholder = { Text("e.g. user@laptop") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = pastedComment.isEmpty()
                )
                if (pastedComment.isNotEmpty()) {
                    Text(S.commentAutoExtracted, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalKey = if (pastedComment.isNotEmpty()) {
                        keyText.trim()
                    } else {
                        val base = keyText.trim().split(" ", limit = 3).take(2).joinToString(" ")
                        if (comment.isNotBlank()) "$base $comment" else base
                    }
                    onAdd(finalKey)
                },
                enabled = keyText.isNotBlank()
            ) { Text(S.addKey) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.cancel) }
        }
    )
}
