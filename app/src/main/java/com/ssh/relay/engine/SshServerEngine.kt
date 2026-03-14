package com.ssh.relay.engine

import com.ssh.relay.SshServerApp
import com.ssh.relay.shell.AndroidShellFactory
import com.ssh.relay.shell.AndroidCommandFactory
import org.apache.sshd.common.forward.PortForwardingEventListener
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.session.SessionListener
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

data class SessionInfo(
    val id: Long = System.nanoTime(),
    val remoteAddress: String,
    val username: String,
    val connectedAt: Long = System.currentTimeMillis()
)

class SshServerEngine {

    private val log = LoggerFactory.getLogger(SshServerEngine::class.java)
    private var sshd: SshServer? = null

    val authorizedKeysManager = AuthorizedKeysManager()

    var port: Int = 2222
    var username: String = "red"
    var password: String = ""

    val isRunning: Boolean get() = sshd?.isStarted == true

    // Active session tracking
    private val _activeSessions = CopyOnWriteArraySet<SessionInfo>()
    val activeSessions: Set<SessionInfo> get() = _activeSessions

    var onSessionsChanged: ((Set<SessionInfo>) -> Unit)? = null

    // Map session to SessionInfo for proper removal
    private val sessionMap = java.util.concurrent.ConcurrentHashMap<Long, SessionInfo>()

    interface Listener {
        fun onServerStarted(port: Int)
        fun onServerStopped()
        fun onServerError(error: Throwable)
        fun onClientConnected(remoteAddress: String)
        fun onForwardingEstablished(local: String, remote: String)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    fun start() {
        if (sshd != null) {
            log.info("Stopping existing server before restart")
            stop()
        }

        try {
            val server = SshServer.setUpDefaultServer().apply {
                this.port = this@SshServerEngine.port

                keyPairProvider = loadOrGenerateHostKey()

                // ===== Keepalive & Timeout =====
                // Server sends keepalive every 15 seconds to keep connection alive
                CoreModuleProperties.HEARTBEAT_INTERVAL.set(this, Duration.ofSeconds(15))
                // Wait up to 10 seconds for heartbeat reply
                CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(this, Duration.ofSeconds(10))
                // No idle timeout (keep sessions alive indefinitely)
                CoreModuleProperties.IDLE_TIMEOUT.set(this, Duration.ZERO)
                // Long auth timeout
                CoreModuleProperties.AUTH_TIMEOUT.set(this, Duration.ofMinutes(5))
                // Disable disconnect on no-reply (tolerate missed keepalives)
                CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(this, 10)

                // Password auth (disabled when password is empty)
                passwordAuthenticator = PasswordAuthenticator { inputUser, inputPass, _ ->
                    val pwd = this@SshServerEngine.password
                    pwd.isNotEmpty() &&
                            inputUser == this@SshServerEngine.username &&
                            inputPass == pwd
                }

                // Public key auth
                publickeyAuthenticator = PublickeyAuthenticator { inputUser, key, _ ->
                    val authorized = authorizedKeysManager.isAuthorized(key)
                    if (authorized) {
                        log.info("Pubkey auth succeeded for user '{}'", inputUser)
                    } else {
                        log.debug("Pubkey auth failed for user '{}'", inputUser)
                    }
                    authorized
                }

                shellFactory = AndroidShellFactory()
                commandFactory = AndroidCommandFactory()
                subsystemFactories = listOf(SftpSubsystemFactory())
                forwardingFilter = AcceptAllForwardingFilter.INSTANCE

                // Session listener
                addSessionListener(object : SessionListener {
                    override fun sessionCreated(session: Session) {
                        val remote = session.ioSession?.remoteAddress?.toString() ?: "unknown"
                        log.info("Session created from {}", remote)
                    }

                    override fun sessionEvent(session: Session, event: SessionListener.Event) {
                        if (event == SessionListener.Event.Authenticated) {
                            val remote = session.ioSession?.remoteAddress?.toString()?.removePrefix("/") ?: "unknown"
                            val user = if (session is ServerSession) {
                                session.username ?: "unknown"
                            } else "unknown"
                            val info = SessionInfo(
                                id = session.ioSession?.id ?: System.nanoTime(),
                                remoteAddress = remote,
                                username = user
                            )
                            _activeSessions.add(info)
                            sessionMap[session.ioSession?.id ?: 0L] = info
                            log.info("Session authenticated: {} from {}", user, remote)
                            onSessionsChanged?.invoke(_activeSessions)
                            listeners.forEach { it.onClientConnected(remote) }
                        }
                    }

                    override fun sessionClosed(session: Session) {
                        val ioId = session.ioSession?.id ?: 0L
                        val info = sessionMap.remove(ioId)
                        if (info != null) {
                            _activeSessions.remove(info)
                        }
                        log.info("Session closed: ioId={}", ioId)
                        onSessionsChanged?.invoke(_activeSessions)
                    }
                })

                addPortForwardingEventListener(object : PortForwardingEventListener {
                    override fun establishedExplicitTunnel(
                        session: Session?,
                        local: SshdSocketAddress?,
                        remote: SshdSocketAddress?,
                        localForwarding: Boolean,
                        boundAddress: SshdSocketAddress?,
                        t: Throwable?
                    ) {
                        if (t == null) {
                            log.info("Tunnel established: {} -> {}", local, remote)
                            listeners.forEach {
                                it.onForwardingEstablished(
                                    local?.toString() ?: "?",
                                    remote?.toString() ?: "?"
                                )
                            }
                        }
                    }
                })
            }

            // Retry binding up to 5 times with increasing delay
            var lastError: Exception? = null
            for (attempt in 1..5) {
                try {
                    server.start()
                    sshd = server
                    _activeSessions.clear()
                    sessionMap.clear()
                    log.info("SSH server started on port {} (attempt {})", port, attempt)
                    listeners.forEach { it.onServerStarted(port) }
                    return
                } catch (e: java.io.IOException) {
                    lastError = e
                    log.warn("Failed to bind port {} (attempt {}): {}", port, attempt, e.message)
                    try { server.stop(true) } catch (_: Exception) {}
                    if (attempt < 5) {
                        Thread.sleep(attempt * 500L)
                    }
                }
            }
            // All retries failed
            sshd = null
            log.error("Failed to start SSH server after 5 attempts", lastError)
            listeners.forEach { it.onServerError(lastError ?: Exception("Bind failed")) }

        } catch (e: Exception) {
            log.error("Failed to start SSH server", e)
            sshd = null
            listeners.forEach { it.onServerError(e) }
        }
    }

    fun stop() {
        try {
            sshd?.stop(true)
        } catch (e: Exception) {
            log.error("Error stopping SSH server", e)
        } finally {
            sshd = null
            _activeSessions.clear()
            sessionMap.clear()
            onSessionsChanged?.invoke(_activeSessions)
            log.info("SSH server stopped")
            listeners.forEach { it.onServerStopped() }
        }
        // Give OS time to release the port
        try { Thread.sleep(1000) } catch (_: InterruptedException) {}
    }

    private fun loadOrGenerateHostKey(): KeyPairProvider {
        val keyDir = SshServerApp.instance.filesDir.resolve("hostkeys")
        keyDir.mkdirs()
        val keyFile = File(keyDir, "host_rsa.ser")

        val keyPair: KeyPair = if (keyFile.exists()) {
            try {
                ObjectInputStream(keyFile.inputStream().buffered()).use { ois ->
                    ois.readObject() as KeyPair
                }.also {
                    log.info("Loaded host key from {}", keyFile.absolutePath)
                }
            } catch (e: Exception) {
                log.warn("Failed to load host key, regenerating", e)
                generateAndSaveKey(keyFile)
            }
        } else {
            log.info("No host key found, generating new one")
            generateAndSaveKey(keyFile)
        }

        return KeyPairProvider { listOf(keyPair) }
    }

    private fun generateAndSaveKey(keyFile: File): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(3072)
        val keyPair = kpg.generateKeyPair()

        try {
            ObjectOutputStream(keyFile.outputStream().buffered()).use { oos ->
                oos.writeObject(keyPair)
            }
            log.info("Host key saved to {}", keyFile.absolutePath)
        } catch (e: Exception) {
            log.warn("Failed to save host key", e)
        }

        return keyPair
    }
}
