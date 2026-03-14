package com.ssh.relay.engine

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SshClientEngine {

    private val log = LoggerFactory.getLogger(SshClientEngine::class.java)
    private var client: SshClient? = null
    private val sessions = ConcurrentHashMap<String, ClientSession>()

    fun start() {
        if (client != null) return
        client = SshClient.setUpDefaultClient().also { it.start() }
        log.info("SSH client engine started")
    }

    fun shutdown() {
        sessions.values.forEach { runCatching { it.close() } }
        sessions.clear()
        client?.stop()
        client = null
        log.info("SSH client engine stopped")
    }

    /**
     * Connect to a remote SSH server.
     * Returns session ID for future operations.
     */
    fun connect(
        host: String,
        port: Int = 22,
        username: String,
        password: String? = null,
        timeoutSec: Long = 30
    ): String {
        val c = client ?: throw IllegalStateException("Client not started")
        val future = c.connect(username, host, port)
        val session = future.verify(timeoutSec, TimeUnit.SECONDS).session

        if (password != null) {
            session.addPasswordIdentity(password)
        }
        session.auth().verify(timeoutSec, TimeUnit.SECONDS)

        val sessionId = "${host}:${port}:${username}"
        sessions[sessionId] = session
        log.info("Connected to {}@{}:{}", username, host, port)
        return sessionId
    }

    /**
     * Local port forwarding: -L localPort:remoteHost:remotePort
     * Binds a local port, forwards traffic to remoteHost:remotePort via SSH tunnel
     */
    fun startLocalForward(
        sessionId: String,
        localPort: Int,
        remoteHost: String,
        remotePort: Int
    ): SshdSocketAddress {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        val local = SshdSocketAddress("127.0.0.1", localPort)
        val remote = SshdSocketAddress(remoteHost, remotePort)
        val bound = session.startLocalPortForwarding(local, remote)
        log.info("Local forward: {} -> {} (bound={})", local, remote, bound)
        return bound
    }

    /**
     * Remote port forwarding: -R remotePort:localHost:localPort
     * Asks the SSH server to listen on remotePort and forward to localHost:localPort
     */
    fun startRemoteForward(
        sessionId: String,
        remotePort: Int,
        localHost: String,
        localPort: Int
    ): SshdSocketAddress {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        val remote = SshdSocketAddress("0.0.0.0", remotePort)
        val local = SshdSocketAddress(localHost, localPort)
        val bound = session.startRemotePortForwarding(remote, local)
        log.info("Remote forward: {} -> {} (bound={})", remote, local, bound)
        return bound
    }

    /**
     * Dynamic port forwarding: -D localPort (SOCKS proxy)
     */
    fun startDynamicForward(sessionId: String, localPort: Int): SshdSocketAddress {
        val session = sessions[sessionId] ?: throw IllegalStateException("No session: $sessionId")
        val local = SshdSocketAddress("127.0.0.1", localPort)
        val bound = session.startDynamicPortForwarding(local)
        log.info("Dynamic forward (SOCKS): {} (bound={})", local, bound)
        return bound
    }

    fun disconnect(sessionId: String) {
        sessions.remove(sessionId)?.let {
            it.close()
            log.info("Disconnected: {}", sessionId)
        }
    }

    fun getActiveSessionIds(): Set<String> = sessions.keys.toSet()
}
