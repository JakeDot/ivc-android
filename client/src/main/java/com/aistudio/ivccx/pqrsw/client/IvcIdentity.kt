package com.aistudio.ivccx.pqrsw.client

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import android.util.Base64

class IvcIdentity(username: String) {
    val username: String = username.replace("@", "")
    private val privateKey: Ed25519PrivateKeyParameters
    private val publicKey: Ed25519PublicKeyParameters

    init {
        val keyPairGenerator = Ed25519KeyPairGenerator()
        keyPairGenerator.init(Ed25519KeyGenerationParameters(SecureRandom()))

        val keyPair = keyPairGenerator.generateKeyPair()
        this.privateKey = keyPair.private as Ed25519PrivateKeyParameters
        this.publicKey = keyPair.public as Ed25519PublicKeyParameters
    }

    fun getPublicKeyBase64(): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Generates Zero-Trust Cryptographic Headers for the IVC protocol.
     */
    fun generateAuthHeaders(method: String, path: String, body: String?): Map<String, String> {
        val timestamp = System.currentTimeMillis().toString()
        val bodyStr = if (body != null && body.isNotEmpty()) body else ""
        val message = "$timestamp:$method:$path:$bodyStr"

        val msgBytes = message.toByteArray(StandardCharsets.UTF_8)

        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(msgBytes, 0, msgBytes.size)
        val signatureBytes = signer.generateSignature()

        val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

        val headers = mutableMapOf<String, String>()
        headers["X-IVC-User"] = "@${this.username}"
        headers["X-IVC-PubKey"] = getPublicKeyBase64()
        headers["X-IVC-Signature"] = signatureBase64
        headers["X-IVC-Timestamp"] = timestamp

        return headers
    }
}
