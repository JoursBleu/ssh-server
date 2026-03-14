package com.ssh.relay.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssh.relay.engine.AuthorizedKeysManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keysManager = remember { AuthorizedKeysManager() }
    var authorizedKeys by remember { mutableStateOf(keysManager.getAuthorizedKeys()) }
    var showAddKeyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Authorized Keys", style = MaterialTheme.typography.headlineMedium)

        Text(
            "Manage SSH public keys for key-based authentication.\nUse ssh-copy-id or add keys manually.",
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
            Text("Add Public Key")
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
                    Text(
                        "No authorized keys",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Only password authentication is enabled.\n\n" +
                            "To add a key from your computer:\n" +
                            "  ssh-copy-id -p 2222 user@phone-ip\n\n" +
                            "Or paste a public key manually using the button above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                "${authorizedKeys.size} key(s) configured",
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
                            Toast.makeText(context, "Key copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Delete key
                        IconButton(onClick = {
                            keysManager.removeKey(index)
                            authorizedKeys = keysManager.getAuthorizedKeys()
                            Toast.makeText(context, "Key removed", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Key Dialog
    if (showAddKeyDialog) {
        AddKeyDialog(
            onDismiss = { showAddKeyDialog = false },
            onAdd = { keyLine ->
                if (keysManager.addKey(keyLine)) {
                    authorizedKeys = keysManager.getAuthorizedKeys()
                    showAddKeyDialog = false
                    Toast.makeText(context, "Key added", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid key or already exists", Toast.LENGTH_SHORT).show()
                }
            }
        )
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
        title = { Text("Add SSH Public Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste an OpenSSH public key:\nssh-rsa AAAA... user@host\nssh-ed25519 AAAA... user@host",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("Public Key") },
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
                    Text("Paste from clipboard")
                }
                OutlinedTextField(
                    value = if (pastedComment.isNotEmpty()) pastedComment else comment,
                    onValueChange = { if (pastedComment.isEmpty()) comment = it },
                    label = { Text("Comment") },
                    placeholder = { Text("e.g. user@laptop") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = pastedComment.isEmpty()
                )
                if (pastedComment.isNotEmpty()) {
                    Text("Comment 已从公钥中自动提取", style = MaterialTheme.typography.bodySmall,
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
            ) { Text("Add Key") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
