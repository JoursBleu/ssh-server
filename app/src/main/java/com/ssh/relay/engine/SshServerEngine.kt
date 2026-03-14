package com.ssh.relay.engine

import com.ssh.relay.SshRelayApp
import com.ssh.relay.shell.AndroidShellFactory
import org.apache.sshd.common.forward.PortForwardingEventListener
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import java.util.concurrent.CopyOnWriteArrayList

class SshServerEngine {

    private val log = LoggerFactory.getLogger(SshServerEngine::class.java)
    private var sshd: SshServer? = null

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

                // Host key - generate RSA 3072
                keyPairProvider = generateHostKey()

                // Auth
                passwordAuthenticator = PasswordAuthenticator { inputUser, inputPass, _ ->
                    inputUser == this@SshServerEngine.username &&
                            inputPass == this@SshServerEngine.password
                }
                publickeyAuthenticator = AcceptAllPublickeyAuthenticator.INSTANCE

                // Shell
                shellFactory = AndroidShellFactory()

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

    private fun generateHostKey(): KeyPairProvider {
        val keyDir = SshRelayApp.instance.filesDir.resolve("hostkeys")
        keyDir.mkdirs()
        val keyFile = keyDir.resolve("host_rsa")

        // For now, generate in-memory. TODO: persist to keyFile
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(3072)
        val keyPair = kpg.generateKeyPair()

        return KeyPairProvider { listOf(keyPair) }
    }
}
