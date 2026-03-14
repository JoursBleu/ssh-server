package com.ssh.relay.engine

import android.os.Process
import java.io.File
import java.net.ServerSocket

/**
 * Utility to check TCP port usage on Android.
 * Reads /proc/net/tcp to find which process holds a port.
 */
object PortChecker {

    enum class PortStatus {
        FREE,            // Port is available
        USED_BY_US,      // Port is held by our own process (stale)
        USED_BY_OTHER    // Port is held by another app
    }

    /**
     * Check if a TCP port is in use and by whom.
     */
    fun checkPort(port: Int): PortStatus {
        // First, try binding the port directly
        try {
            ServerSocket(port).use {
                // Successfully bound -> port is free
                return PortStatus.FREE
            }
        } catch (_: Exception) {
            // Port is in use, figure out by whom
        }

        // Parse /proc/net/tcp and /proc/net/tcp6 to find the owner
        val myUid = Process.myUid()
        val portHex = String.format("%04X", port)

        for (tcpFile in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
            try {
                File(tcpFile).readLines().drop(1).forEach { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 8) {
                        // local_address is in format "ADDR:PORT" in hex
                        val localAddr = parts[1]
                        val localPort = localAddr.substringAfter(":")
                        val state = parts[3]  // "0A" = LISTEN

                        if (localPort.equals(portHex, ignoreCase = true) && state == "0A") {
                            val uid = parts[7].toIntOrNull() ?: -1
                            return if (uid == myUid) PortStatus.USED_BY_US else PortStatus.USED_BY_OTHER
                        }
                    }
                }
            } catch (_: Exception) {
                // /proc/net/tcp might not be readable on some devices
            }
        }

        // Couldn't read /proc, assume it's us (conservative fallback)
        return PortStatus.USED_BY_US
    }
}
