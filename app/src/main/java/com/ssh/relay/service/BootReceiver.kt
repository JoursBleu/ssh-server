package com.ssh.relay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("ssh_server_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start_on_boot", false)

            if (autoStart) {
                val port = prefs.getString("port", "2222")?.toIntOrNull() ?: 2222
                val user = prefs.getString("username", "red") ?: "red"
                val pass = prefs.getString("password", "") ?: ""
                Log.i("BootReceiver", "Auto-starting SSH server on port $port")
                SshServerService.start(context, port, user, pass)
            }
        }
    }
}
