package com.ssh.relay.engine

import com.ssh.relay.SshRelayApp
import org.slf4j.LoggerFactory
import java.io.File
import java.security.PublicKey
import java.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

/**
 * Manages authorized_keys file for SSH public key authentication.
 * Stores keys in OpenSSH authorized_keys format (one per line):
 *   ssh-rsa AAAA... comment
 *   ssh-ed25519 AAAA... comment
 */
class AuthorizedKeysManager {

    private val log = LoggerFactory.getLogger(AuthorizedKeysManager::class.java)

    private val sshDir: File
        get() {
            val dir = File(SshRelayApp.instance.filesDir, ".ssh")
            dir.mkdirs()
            return dir
        }

    private val authorizedKeysFile: File
        get() = File(sshDir, "authorized_keys")

    /**
     * Get all authorized key entries (raw lines from the file).
     */
    fun getAuthorizedKeys(): List<String> {
        val file = authorizedKeysFile
        if (!file.exists()) return emptyList()
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
    }

    /**
     * Add a public key entry. Input should be in OpenSSH format:
     *   ssh-rsa AAAA... comment
     */
    fun addKey(keyLine: String): Boolean {
        val trimmed = keyLine.trim()
        if (!isValidKeyLine(trimmed)) {
            log.warn("Invalid key format: {}", trimmed.take(50))
            return false
        }

        // Check for duplicates
        val existing = getAuthorizedKeys()
        val keyPart = extractKeyData(trimmed)
        if (existing.any { extractKeyData(it) == keyPart }) {
            log.info("Key already exists")
            return false
        }

        authorizedKeysFile.appendText(trimmed + "\n")
        log.info("Added authorized key: {}", extractComment(trimmed))
        return true
    }

    /**
     * Remove a key by its index (0-based).
     */
    fun removeKey(index: Int): Boolean {
        val keys = getAuthorizedKeys().toMutableList()
        if (index < 0 || index >= keys.size) return false
        keys.removeAt(index)
        authorizedKeysFile.writeText(keys.joinToString("\n") { it } + if (keys.isNotEmpty()) "\n" else "")
        log.info("Removed key at index {}", index)
        return true
    }

    /**
     * Check if a given PublicKey matches any authorized key.
     * Used by the SSH server's PublickeyAuthenticator.
     */
    fun isAuthorized(key: PublicKey): Boolean {
        val keys = getAuthorizedKeys()
        if (keys.isEmpty()) {
            // If no keys configured, reject all (require password)
            return false
        }

        val incomingBytes = key.encoded
        for (line in keys) {
            try {
                val parts = line.split(" ", limit = 3)
                if (parts.size < 2) continue
                val storedBytes = Base64.getDecoder().decode(parts[1])

                // Compare the raw key bytes from the authorized_keys entry
                // with the incoming key. The authorized_keys stores the SSH wire
                // format, while key.encoded is X509. We need to compare properly.
                if (sshKeyMatchesPublicKey(parts[0], storedBytes, key)) {
                    return true
                }
            } catch (e: Exception) {
                log.debug("Failed to parse key line: {}", e.message)
            }
        }
        return false
    }

    /**
     * Compare an SSH wire-format key blob with a Java PublicKey.
     */
    private fun sshKeyMatchesPublicKey(keyType: String, sshBlob: ByteArray, pubKey: PublicKey): Boolean {
        return try {
            // Re-encode the incoming PublicKey to SSH wire format and compare
            val incomingBlob = encodePublicKeyToSshBlob(pubKey)
            if (incomingBlob != null) {
                sshBlob.contentEquals(incomingBlob)
            } else {
                false
            }
        } catch (e: Exception) {
            log.debug("Key comparison failed: {}", e.message)
            false
        }
    }

    /**
     * Encode a Java PublicKey to SSH wire format blob.
     */
    private fun encodePublicKeyToSshBlob(key: PublicKey): ByteArray? {
        return try {
            when (key.algorithm) {
                "RSA" -> {
                    val rsaKey = key as java.security.interfaces.RSAPublicKey
                    val e = rsaKey.publicExponent.toByteArray()
                    val n = rsaKey.modulus.toByteArray()
                    val type = "ssh-rsa".toByteArray()
                    buildSshBlob(type, e, n)
                }
                "EC" -> {
                    val ecKey = key as java.security.interfaces.ECPublicKey
                    val params = ecKey.params
                    val curveName = when (params.order.bitLength()) {
                        256 -> "nistp256"
                        384 -> "nistp384"
                        521 -> "nistp521"
                        else -> return null
                    }
                    val type = "ecdsa-sha2-$curveName".toByteArray()
                    val curveId = curveName.toByteArray()
                    val point = ecKey.w
                    val x = point.affineX.toByteArray()
                    val y = point.affineY.toByteArray()
                    // EC point in uncompressed form: 0x04 + x + y
                    val fieldSize = (params.order.bitLength() + 7) / 8
                    val pointBytes = ByteArray(1 + fieldSize * 2)
                    pointBytes[0] = 0x04
                    System.arraycopy(x, maxOf(0, x.size - fieldSize), pointBytes, 1 + fieldSize - minOf(x.size, fieldSize), minOf(x.size, fieldSize))
                    System.arraycopy(y, maxOf(0, y.size - fieldSize), pointBytes, 1 + fieldSize * 2 - minOf(y.size, fieldSize), minOf(y.size, fieldSize))
                    buildSshBlob(type, curveId, pointBytes)
                }
                "EdDSA", "Ed25519" -> {
                    val type = "ssh-ed25519".toByteArray()
                    // For Ed25519, the raw key is the last 32 bytes of the encoded form
                    val encoded = key.encoded
                    val rawKey = if (encoded.size > 32) encoded.copyOfRange(encoded.size - 32, encoded.size) else encoded
                    buildSshBlob(type, rawKey)
                }
                else -> null
            }
        } catch (e: Exception) {
            log.debug("Failed to encode key to SSH blob: {}", e.message)
            null
        }
    }

    private fun buildSshBlob(vararg parts: ByteArray): ByteArray {
        val totalSize = parts.sumOf { 4 + it.size }
        val buf = java.nio.ByteBuffer.allocate(totalSize)
        for (part in parts) {
            buf.putInt(part.size)
            buf.put(part)
        }
        return buf.array()
    }

    companion object {
        private val VALID_KEY_TYPES = setOf(
            "ssh-rsa", "ssh-ed25519", "ssh-dss",
            "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521",
            "sk-ecdsa-sha2-nistp256@openssh.com", "sk-ssh-ed25519@openssh.com"
        )

        fun isValidKeyLine(line: String): Boolean {
            val parts = line.trim().split(" ", limit = 3)
            if (parts.size < 2) return false
            if (parts[0] !in VALID_KEY_TYPES) return false
            return try {
                Base64.getDecoder().decode(parts[1])
                true
            } catch (_: Exception) {
                false
            }
        }

        fun extractKeyData(line: String): String {
            val parts = line.trim().split(" ", limit = 3)
            return if (parts.size >= 2) "${parts[0]} ${parts[1]}" else line
        }

        fun extractComment(line: String): String {
            val parts = line.trim().split(" ", limit = 3)
            return if (parts.size >= 3) parts[2] else "(no comment)"
        }

        fun extractKeyType(line: String): String {
            return line.trim().split(" ", limit = 2).firstOrNull() ?: "unknown"
        }

        /** Get a short fingerprint-like display of the key */
        fun shortFingerprint(line: String): String {
            return try {
                val parts = line.trim().split(" ", limit = 3)
                if (parts.size < 2) return "???"
                val decoded = Base64.getDecoder().decode(parts[1])
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val hash = md.digest(decoded)
                "SHA256:" + Base64.getEncoder().encodeToString(hash).trimEnd('=').take(16)
            } catch (_: Exception) {
                "???"
            }
        }
    }
}
