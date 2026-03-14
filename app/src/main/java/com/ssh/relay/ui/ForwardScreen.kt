package com.ssh.relay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

enum class ForwardType(val label: String, val description: String) {
    LOCAL("-L", "Local → Remote"),
    REMOTE("-R", "Remote → Local"),
    DYNAMIC("-D", "SOCKS Proxy")
}

data class ForwardRule(
    val type: ForwardType,
    val localPort: Int,
    val remoteHost: String = "",
    val remotePort: Int = 0,
    val active: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardScreen() {
    var selectedType by remember { mutableStateOf(ForwardType.LOCAL) }
    var localPort by remember { mutableStateOf("") }
    var remoteHost by remember { mutableStateOf("") }
    var remotePort by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf(listOf<ForwardRule>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Port Forwarding", style = MaterialTheme.typography.headlineMedium)

        // Type selector
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ForwardType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, ForwardType.entries.size),
                    onClick = { selectedType = type },
                    selected = selectedType == type,
                    label = { Text(type.label) }
                )
            }
        }

        // Description
        Text(
            selectedType.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Input fields
        OutlinedTextField(
            value = localPort,
            onValueChange = { localPort = it.filter { c -> c.isDigit() } },
            label = { Text(if (selectedType == ForwardType.DYNAMIC) "SOCKS Port" else "Local Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedType != ForwardType.DYNAMIC) {
            OutlinedTextField(
                value = remoteHost,
                onValueChange = { remoteHost = it },
                label = { Text("Remote Host") },
                placeholder = { Text("127.0.0.1") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = remotePort,
                onValueChange = { remotePort = it.filter { c -> c.isDigit() } },
                label = { Text("Remote Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Add button
        Button(
            onClick = {
                val lp = localPort.toIntOrNull() ?: return@Button
                val rp = remotePort.toIntOrNull() ?: 0
                val rule = ForwardRule(
                    type = selectedType,
                    localPort = lp,
                    remoteHost = remoteHost.ifBlank { "127.0.0.1" },
                    remotePort = rp
                )
                rules = rules + rule
                localPort = ""
                remoteHost = ""
                remotePort = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = localPort.isNotBlank()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Rule")
        }

        // Rules list
        if (rules.isNotEmpty()) {
            Text("Active Rules", style = MaterialTheme.typography.titleMedium)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(rules) { rule ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${rule.type.label} :${rule.localPort}" +
                                    if (rule.type != ForwardType.DYNAMIC)
                                        " → ${rule.remoteHost}:${rule.remotePort}"
                                    else "",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                rule.type.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            rules = rules - rule
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}
