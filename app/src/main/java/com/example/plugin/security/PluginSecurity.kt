package com.example.plugin.security

import android.util.Base64
import com.example.plugin.sdk.model.PluginManifest
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

enum class PluginPermission(val code: String, val description: String) {
    NETWORK("network", "Allows access to network resources and API endpoints"),
    STORAGE("storage", "Allows storing local configuration and credentials"),
    MEDIA_HEADERS("media_headers", "Allows injecting custom HTTP headers into Media3 playback");

    companion object {
        fun fromCode(code: String): PluginPermission? = values().find { it.code == code }
    }
}

class PluginSignatureVerifier {

    /**
     * Verifies RSA-SHA256 signature of a plugin manifest against a trusted public key (DER Base64).
     */
    fun verifySignature(manifest: PluginManifest, publicKeyBase64: String): Boolean {
        val signatureStr = manifest.signature ?: return false
        return try {
            val contentToSign = "${manifest.id}:${manifest.version}:${manifest.author}:${manifest.entryFile}"
            val keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(contentToSign.toByteArray(Charsets.UTF_8))
            
            sig.verify(Base64.decode(signatureStr, Base64.DEFAULT))
        } catch (e: Exception) {
            false
        }
    }
}

class PluginSandboxPermissions(
    val grantedPermissions: Set<PluginPermission>
) {
    fun checkPermission(permission: PluginPermission) {
        if (!grantedPermissions.contains(permission)) {
            throw SecurityException("Plugin operation denied: missing permission '${permission.code}'")
        }
    }
}
