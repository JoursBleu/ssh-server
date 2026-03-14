package com.ssh.relay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ssh.relay.engine.SshClientEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen() {
    val scope = rememberCoroutineScope()
    val clientEngine = remember { SshClientEngine() }

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isConnected by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Not connected") }
    var isLoading by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        clientEngine.start()
        onDispose { clientEngine.shutdown() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SSH Client", style = MaterialTheme.typography.headlineMedium)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (isConnected) "● Connected" else "○ Disconnected",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(statusText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            placeholder = { Text("192.168.1.100") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        if (isConnected) {
                            withContext(Dispatchers.IO) {
                                clientEngine.disconnect(sessionId)
                            }
                            isConnected = false
                            sessionId = ""
                            statusText = "Disconnected"
                        } else {
                            val p = port.toIntOrNull() ?: 22
                            val id = withContext(Dispatchers.IO) {
                                clientEngine.connect(host, p, username, password)
                            }
                            sessionId = id
                            isConnected = true
                            statusText = "Connected to $username@$host:$p"
                        }
                    } catch (e: Exception) {
                        statusText = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && (isConnected || (host.isNotBlank() && username.isNotBlank())),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isConnected) "Disconnect" else "Connect")
            }
        }
    }
}
