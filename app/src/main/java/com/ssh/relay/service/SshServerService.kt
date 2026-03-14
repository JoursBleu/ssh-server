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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.ssh.relay.engine.SessionInfo
import com.ssh.relay.engine.SshServerEngine
import com.ssh.relay.ui.MainActivity
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors

class SshServerService : Service() {

    private var engine: SshServerEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkRequestCallback: ConnectivityManager.NetworkCallback? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // CPU wake lock - prevent CPU sleep
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "SshServer::CpuWakeLock"
        ).apply {
            acquire()
        }

        // WiFi lock - keep WiFi radio active
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(lockMode, "SshServer::WifiLock").apply {
                acquire()
            }
        } catch (_: Exception) {}

        // Actively request network to prevent Android from releasing it
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // Request to keep a network connection alive
            val netRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build()
            val reqCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Bind process to this network to prevent switching
                    cm.bindProcessToNetwork(network)
                    android.util.Log.i("SshServerService", "Bound to network: $network")
                    updateNotification()
                }
                override fun onLost(network: Network) {
                    cm.bindProcessToNetwork(null)
                    android.util.Log.w("SshServerService", "Lost network: $network")
                    updateNotificationText("No network")
                }
            }
            cm.requestNetwork(netRequest, reqCallback)
            networkRequestCallback = reqCallback

            // Also register a passive listener for IP changes
            val monitorRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val monCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { updateNotification() }
                override fun onLost(network: Network) { updateNotificationText("No network") }
            }
            cm.registerNetworkCallback(monitorRequest, monCallback)
            networkCallback = monCallback
        } catch (e: Exception) {
            android.util.Log.e("SshServerService", "Network setup failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 2222) ?: 2222
        val user = intent?.getStringExtra(EXTRA_USER) ?: "red"
        val pass = intent?.getStringExtra(EXTRA_PASS) ?: ""

        _currentPort = port
        _currentUser = user
        _currentPass = pass

        val ip = getDeviceIp()
        startForeground(NOTIFICATION_ID, buildNotification(ip, port))
        _isRunning = true

        executor.submit {
            try {
                engine?.stop()
                engine = null

                val eng = SshServerEngine()
                eng.port = port
                eng.username = user
                eng.password = pass
                eng.onSessionsChanged = { sessions ->
                    _activeSessions = sessions.toSet()
                    updateNotification()
                }
                eng.start()
                engine = eng
            } catch (e: Exception) {
                android.util.Log.e("SshServerService", "Failed to start engine", e)
            }
        }

        _activeSessions = emptySet()
        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning = false
        _activeSessions = emptySet()

        try {
            executor.submit { engine?.stop(); engine = null }.get(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {
            engine?.let { try { it.stop() } catch (_: Exception) {} }
            engine = null
        }

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        networkCallback = null
        networkRequestCallback?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        networkRequestCallback = null

        try { cm.bindProcessToNetwork(null) } catch (_: Exception) {}

        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running when swiped from recents
        super.onTaskRemoved(rootIntent)
    }

    private fun updateNotification() {
        try {
            val ip = getDeviceIp()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(ip, _currentPort))
        } catch (_: Exception) {}
    }

    private fun updateNotificationText(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(text, _currentPort))
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "SSH Server running notification"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(ip: String, port: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val sessionCount = _activeSessions.size
        val text = if (sessionCount > 0) {
            "$ip:$port · $sessionCount session(s)"
        } else {
            "$ip:$port"
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SSH Server Running")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun getDeviceIp(): String {
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
            val wifiIp = allIps.find { it.startsWith("192.168.") }
            if (wifiIp != null) return wifiIp
            val privateIp = allIps.find { it.startsWith("10.") || it.startsWith("172.") }
            if (privateIp != null) return privateIp
            if (allIps.isNotEmpty()) return allIps.first()
        } catch (_: Exception) {}

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
        @Volatile var _activeSessions: Set<SessionInfo> = emptySet()

        val isRunning get() = _isRunning
        val currentPort get() = _currentPort
        val currentUser get() = _currentUser
        val currentPass get() = _currentPass
        val activeSessions get() = _activeSessions

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
