package com.ssh.relay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import com.ssh.relay.engine.SshServerEngine
import com.ssh.relay.ui.MainActivity

class SshServerService : Service() {

    private val engine = SshServerEngine()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Acquire partial wake lock to keep CPU running
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SshServer::WakeLock").apply {
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 2222) ?: 2222
        val user = intent?.getStringExtra(EXTRA_USER) ?: "red"
        val pass = intent?.getStringExtra(EXTRA_PASS) ?: ""

        engine.port = port
        engine.username = user
        engine.password = pass
        engine.start()

        val ip = getDeviceIp()
        startForeground(NOTIFICATION_ID, buildNotification(ip, port))

        // Track running state
        _isRunning = true
        _currentPort = port
        _currentUser = user
        _currentPass = pass

        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning = false
        engine.stop()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Service keeps running even when app is swiped from recents
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "SSH Server running notification"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(ip: String, port: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SSH Server Running")
            .setContentText("$ip:$port")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun getDeviceIp(): String {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff, ip shr 8 and 0xff,
                    ip shr 16 and 0xff, ip shr 24 and 0xff
                )
            }
        } catch (_: Exception) {}

        try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                ni.inetAddresses?.toList()?.forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (_: Exception) {}
        return "0.0.0.0"
    }

    companion object {
        private const val CHANNEL_ID = "ssh_server_channel"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_PORT = "port"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"

        // Static state so UI can check if service is running
        @Volatile var _isRunning = false
            private set
        @Volatile var _currentPort = 2222
            private set
        @Volatile var _currentUser = "red"
            private set
        @Volatile var _currentPass = ""
            private set

        val isRunning get() = _isRunning
        val currentPort get() = _currentPort
        val currentUser get() = _currentUser
        val currentPass get() = _currentPass

        fun start(context: Context, port: Int = 2222, user: String = "red", pass: String = "") {
            val intent = Intent(context, SshServerService::class.java).apply {
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_USER, user)
                putExtra(EXTRA_PASS, pass)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SshServerService::class.java))
        }
    }
}
