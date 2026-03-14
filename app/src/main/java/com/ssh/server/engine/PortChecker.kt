package com.ssh.server.engine

import android.os.Process
import org.slf4j.LoggerFactory
import java.io.File
import java.net.ServerSocket

/**
 * Utility to check TCP port usage on Android.
 * Reads /proc/net/tcp to find which process holds a port.
 */
object PortChecker {

    private val log = LoggerFactory.getLogger(PortChecker::class.java)

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
                        val localAddr = parts[1]
                        val localPort = localAddr.substringAfter(":")
                        val state = parts[3]  // "0A" = LISTEN

                        if (localPort.equals(portHex, ignoreCase = true) && state == "0A") {
                            val uid = parts[7].toIntOrNull() ?: -1
                            return if (uid == myUid) PortStatus.USED_BY_US else PortStatus.USED_BY_OTHER
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Couldn't read /proc, assume it's us (conservative fallback)
        return PortStatus.USED_BY_US
    }

    /**
     * Kill our own process's socket holding a port.
     * On Android, we can't kill individual sockets, but we can find and kill
     * the threads/connections. The most reliable approach is to find the
     * inode from /proc/net/tcp and close the corresponding fd.
     *
     * Returns true if the port was freed.
     */
    fun forceReleaseOurPort(port: Int): Boolean {
        val portHex = String.format("%04X", port)
        val myPid = Process.myPid()

        // Find the inode of the listening socket
        var targetInode: String? = null
        for (tcpFile in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
            try {
                File(tcpFile).readLines().drop(1).forEach { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 10) {
                        val localPort = parts[1].substringAfter(":")
                        val state = parts[3]
                        if (localPort.equals(portHex, ignoreCase = true) && state == "0A") {
                            targetInode = parts[9]  // inode column
                            return@forEach
                        }
                    }
                }
            } catch (_: Exception) {}
            if (targetInode != null) break
        }

        if (targetInode == null) {
            log.warn("Could not find inode for port {}", port)
            return false
        }

        log.info("Found socket inode {} for port {}, searching fds in pid {}", targetInode, port, myPid)

        // Find the fd that points to this socket inode and close it
        val fdDir = File("/proc/$myPid/fd")
        try {
            fdDir.listFiles()?.forEach { fd ->
                try {
                    val link = java.nio.file.Files.readSymbolicLink(fd.toPath()).toString()
                    if (link.contains("socket:[$targetInode]")) {
                        log.info("Found fd {} -> {}, closing", fd.name, link)
                        // We can't directly close an fd from Java, but we can use
                        // the file descriptor number with native close
                        val fdNum = fd.name.toIntOrNull()
                        if (fdNum != null) {
                            try {
                                // Use Os.close via reflection
                                val osClass = Class.forName("android.system.Os")
                                val fdClass = Class.forName("java.io.FileDescriptor")
                                val constructor = fdClass.getDeclaredConstructor()
                                constructor.isAccessible = true
                                val fileDesc = constructor.newInstance()
                                val setIntMethod = fdClass.getDeclaredMethod("setInt$", Int::class.javaPrimitiveType)
                                setIntMethod.isAccessible = true
                                setIntMethod.invoke(fileDesc, fdNum)
                                val closeMethod = osClass.getMethod("close", fdClass)
                                closeMethod.invoke(null, fileDesc)
                                log.info("Closed fd {}", fdNum)
                            } catch (e: Exception) {
                                log.warn("Failed to close fd {}: {}", fdNum, e.message)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            log.warn("Failed to scan fds: {}", e.message)
        }

        // Wait a bit and re-check
        Thread.sleep(500)
        return checkPort(port) == PortStatus.FREE
    }
}
