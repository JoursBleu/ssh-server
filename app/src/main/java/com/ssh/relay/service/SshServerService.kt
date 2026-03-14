package com.ssh.relay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import com.ssh.relay.engine.SshServerEngine
import com.ssh.relay.ui.MainActivity
import java.net.Inet4Address
import java.net.NetworkInterface

class SshServerService : Service() {

    private var engine: SshServerEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // CPU wake lock
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SshServer::CpuWakeLock").apply {
            acquire()
        }

        // WiFi lock - keep WiFi active when screen off
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SshServer::WifiLock").apply {
                acquire()
            }
        } catch (_: Exception) {}

        // Request network keep-alive (works for both WiFi and mobile data)
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Network is available, update notification with new IP
                    val ip = getDeviceIp()
                    val port = _currentPort
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification(ip, port))
                }

                override fun onLost(network: Network) {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification("No network", _currentPort))
                }
            }
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 2222) ?: 2222
        val user = intent?.getStringExtra(EXTRA_USER) ?: "red"
        val pass = intent?.getStringExtra(EXTRA_PASS) ?: ""

        // Stop existing engine if already running
        engine?.stop()

        val eng = SshServerEngine()
        eng.port = port
        eng.username = user
        eng.password = pass
        eng.start()
        engine = eng

        val ip = getDeviceIp()
        startForeground(NOTIFICATION_ID, buildNotification(ip, port))

        _isRunning = true
        _currentPort = port
        _currentUser = user
        _currentPass = pass

        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning = false
        engine?.stop()
        engine = null

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null

        networkCallback?.let {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkCallback = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
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

    private fun getDeviceIp(): String {
        // Try all network interfaces (works for both WiFi and mobile data)
        try {
            val allIps = mutableListOf<String>()
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (ni.isUp && !ni.isLoopback) {
                    ni.inetAddresses?.toList()?.forEach { addr ->
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            allIps.add(addr.hostAddress ?: "")
                        }
                    }
                }
            }
            // Prefer 192.168.x.x (WiFi) over others, but return any if available
            val wifiIp = allIps.find { it.startsWith("192.168.") }
            if (wifiIp != null) return wifiIp
            val privateIp = allIps.find { it.startsWith("10.") || it.startsWith("172.") }
            if (privateIp != null) return privateIp
            if (allIps.isNotEmpty()) return allIps.first()
        } catch (_: Exception) {}

        // Fallback: WiFi manager
        @Suppress("DEPRECATION")
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

        return "0.0.0.0"
    }

    companion object {
        private const val CHANNEL_ID = "ssh_server_channel"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_PORT = "port"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"

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
