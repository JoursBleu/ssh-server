package com.ssh.relay.engine

import com.ssh.relay.SshServerApp
import com.ssh.relay.shell.AndroidShellFactory
import com.ssh.relay.shell.AndroidCommandFactory
import org.apache.sshd.common.forward.PortForwardingEventListener
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.concurrent.CopyOnWriteArrayList

class SshServerEngine {

    private val log = LoggerFactory.getLogger(SshServerEngine::class.java)
    private var sshd: SshServer? = null

    val authorizedKeysManager = AuthorizedKeysManager()

    var port: Int = 2222
    var username: String = "admin"
    var password: String = "admin"

    val isRunning: Boolean get() = sshd?.isStarted == true

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
        if (sshd != null) return
        try {
            val server = SshServer.setUpDefaultServer().apply {
                this.port = this@SshServerEngine.port

                // Host key - persisted to disk
                keyPairProvider = loadOrGenerateHostKey()

                // Password auth
                passwordAuthenticator = PasswordAuthenticator { inputUser, inputPass, _ ->
                    inputUser == this@SshServerEngine.username &&
                            inputPass == this@SshServerEngine.password
                }

                // Public key auth - check against authorized_keys
                publickeyAuthenticator = PublickeyAuthenticator { inputUser, key, _ ->
                    val authorized = authorizedKeysManager.isAuthorized(key)
                    if (authorized) {
                        log.info("Pubkey auth succeeded for user '{}'", inputUser)
                    } else {
                        log.debug("Pubkey auth failed for user '{}'", inputUser)
                    }
                    authorized
                }

                // Shell
                shellFactory = AndroidShellFactory()

                // Exec commands (ssh-copy-id, scp, etc.)
                commandFactory = AndroidCommandFactory()

                // SFTP
                subsystemFactories = listOf(SftpSubsystemFactory())

                // Port forwarding
                forwardingFilter = AcceptAllForwardingFilter.INSTANCE

                // Forwarding event listener
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

            server.start()
            sshd = server
            log.info("SSH server started on port {}", port)
            listeners.forEach { it.onServerStarted(port) }
        } catch (e: Exception) {
            log.error("Failed to start SSH server", e)
            listeners.forEach { it.onServerError(e) }
        }
    }

    fun stop() {
        try {
            sshd?.stop(true)
            sshd = null
            log.info("SSH server stopped")
            listeners.forEach { it.onServerStopped() }
        } catch (e: Exception) {
            log.error("Error stopping SSH server", e)
        }
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
