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
import com.ssh.relay.engine.StartResult
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

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "SshServer::CpuWakeLock"
        ).apply { acquire() }

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(lockMode, "SshServer::WifiLock").apply { acquire() }
        } catch (_: Exception) {}

        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val netRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build()
            val reqCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cm.bindProcessToNetwork(network)
                    updateNotification()
                }
                override fun onLost(network: Network) {
                    cm.bindProcessToNetwork(null)
                    updateNotificationText("No network")
                }
            }
            cm.requestNetwork(netRequest, reqCallback)
            networkRequestCallback = reqCallback

            val monRequest = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            val monCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { updateNotification() }
                override fun onLost(network: Network) { updateNotificationText("No network") }
            }
            cm.registerNetworkCallback(monRequest, monCallback)
            networkCallback = monCallback
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop action
        if (intent?.action == ACTION_STOP) {
            _serverState = ServerState.STOPPING
            executor.submit {
                try {
                    engine?.stop()
                } catch (_: Exception) {}
                engine = null
                _isRunning = false
                _activeSessions = emptySet()
                _serverState = ServerState.STOPPED
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, 2222) ?: 2222
        val user = intent?.getStringExtra(EXTRA_USER) ?: "red"
        val pass = intent?.getStringExtra(EXTRA_PASS) ?: ""

        _currentPort = port
        _currentUser = user
        _currentPass = pass
        _serverState = ServerState.STARTING

        val ip = getDeviceIp()
        startForeground(NOTIFICATION_ID, buildNotification(ip, port))

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

                val result = eng.start()
                when (result) {
                    is StartResult.Success -> {
                        engine = eng
                        _serverState = ServerState.RUNNING
                        _lastError = null
                        _isRunning = true
                    }
                    is StartResult.PortUsedByOther -> {
                        _serverState = ServerState.ERROR
                        _lastError = "端口 ${result.port} 被其他应用占用"
                        _isRunning = false
                        stopSelf()
                    }
                    is StartResult.Error -> {
                        _serverState = ServerState.ERROR
                        _lastError = result.message
                        _isRunning = false
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                _serverState = ServerState.ERROR
                _lastError = e.message
                _isRunning = false
                stopSelf()
            }
            updateNotification()
        }

        _activeSessions = emptySet()
        return START_STICKY
    }

    override fun onDestroy() {
        _serverState = ServerState.STOPPING

        try {
            executor.submit {
                engine?.stop()
                engine = null
            }.get(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {
            engine?.let { try { it.stop() } catch (_: Exception) {} }
            engine = null
        }

        _isRunning = false
        _activeSessions = emptySet()
        _serverState = ServerState.STOPPED

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
    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }

    private fun updateNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(getDeviceIp(), _currentPort))
        } catch (_: Exception) {}
    }

    private fun updateNotificationText(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(text, _currentPort))
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "SSH Server", NotificationManager.IMPORTANCE_LOW).apply {
            description = "SSH Server running notification"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(ip: String, port: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val sessionCount = _activeSessions.size
        val text = when (_serverState) {
            ServerState.STARTING -> "$ip:$port · Starting..."
            ServerState.STOPPING -> "$ip:$port · Stopping..."
            ServerState.ERROR -> "Error: ${_lastError}"
            else -> if (sessionCount > 0) "$ip:$port · $sessionCount session(s)" else "$ip:$port"
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SSH Server")
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
                        if (!addr.isLoopbackAddress && addr is Inet4Address) allIps.add(addr.hostAddress ?: "")
                    }
                }
            }
            allIps.find { it.startsWith("192.168.") }?.let { return it }
            allIps.find { it.startsWith("10.") || it.startsWith("172.") }?.let { return it }
            if (allIps.isNotEmpty()) return allIps.first()
        } catch (_: Exception) {}
        @Suppress("DEPRECATION")
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip != 0) return String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
        } catch (_: Exception) {}
        return "0.0.0.0"
    }

    enum class ServerState { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

    companion object {
        private const val CHANNEL_ID = "ssh_server_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.ssh.relay.STOP"
        const val EXTRA_PORT = "port"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"

        @Volatile var _isRunning = false; private set
        @Volatile var _currentPort = 2222; private set
        @Volatile var _currentUser = "red"; private set
        @Volatile var _currentPass = ""; private set
        @Volatile var _activeSessions: Set<SessionInfo> = emptySet()
        @Volatile var _serverState = ServerState.STOPPED; private set
        @Volatile var _lastError: String? = null; private set

        val isRunning get() = _isRunning
        val currentPort get() = _currentPort
        val currentUser get() = _currentUser
        val currentPass get() = _currentPass
        val activeSessions get() = _activeSessions
        val serverState get() = _serverState
        val lastError get() = _lastError

        fun start(context: Context, port: Int = 2222, user: String = "red", pass: String = "") {
            _serverState = ServerState.STARTING
            _lastError = null
            context.startForegroundService(Intent(context, SshServerService::class.java).apply {
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_USER, user)
                putExtra(EXTRA_PASS, pass)
            })
        }

        fun stop(context: Context) {
            _serverState = ServerState.STOPPING
            // Send stop action to service so it can stop engine async before destroying
            context.startService(Intent(context, SshServerService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
