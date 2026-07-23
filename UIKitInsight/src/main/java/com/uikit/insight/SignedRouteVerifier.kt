package com.uikit.insight

import android.net.Uri
import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

internal object SignedRouteVerifier {
    private const val routePublicKeyPem = """
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtqt5ej21Rj9tY2b545Oz
H0wUJH02dRLZqCM0xeYkXMgAOZNEqyiojSxb9K1EFUasm6Xqp4QllWalvNPL7rUm
4Srm3wsPrwVF9xmL4Y6X3Yt7g1bhjgKAcqUwSzywC9kcxzqtLsx8SdqVQLpuE3Sg
8irnVokoA2p6Vpq8M+0PnXfu4j3BD4FYQjZiWtRNCvvJR3yFsPHg5zX0j6M/Za5d
q+QsdZzFRzD1u+y6fry27ttGJi0t/2OOOHt2FpzT2ulYumzeQEmKfCJnvt2QEAbn
CguSUOvNFZjkxFMjcFUoJ9Y2+yaDCctNqy7WT9nVUjUQMWccjg5ueuAuzWWwNHN8
wQIDAQAB
-----END PUBLIC KEY-----
"""

    private val publicKey by lazy {
        val der = routePublicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
            .let { Base64.decode(it, Base64.DEFAULT) }
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    fun requireMainRoute(
        route: String,
        fieldName: String,
        bypassSignature: Boolean = false
    ): String {
        val routeText = requireRouteText(route, fieldName)
        if (bypassSignature) return routeText
        val uri = Uri.parse(routeText)
        val segments = signedSegments(uri, fieldName)
        require(verify(segments.nonce, segments.signature)) {
            "$fieldName has an invalid route signature"
        }
        return routeText
    }

    fun requireProviderRoute(
        route: String,
        fieldName: String,
        bypassSignature: Boolean = false
    ): String {
        val routeText = requireRouteText(route, fieldName)
        if (bypassSignature) return routeText
        val uri = Uri.parse(routeText)
        val segments = signedSegments(uri, fieldName)
        require(verify(segments.nonce, segments.signature)) {
            "$fieldName has an invalid route signature"
        }
        return appendProviderIfMissing(uri).toString()
    }

    private fun requireRouteText(route: String, fieldName: String): String {
        val trimmed = route.trim()
        require(trimmed.isNotEmpty()) { "$fieldName must not be blank" }
        return trimmed
    }

    private fun signedSegments(uri: Uri, fieldName: String): SignedSegments {
        val segments = uri.pathSegments
        require(segments.size >= 2) {
            "$fieldName must use /<nonce>/<signature>"
        }
        val nonce = segments[0]
        val signature = segments[1]
        return SignedSegments(nonce, signature)
    }

    private fun appendProviderIfMissing(uri: Uri): Uri {
        if (uri.pathSegments.lastOrNull() == "provider") return uri
        return uri.buildUpon().appendPath("provider").build()
    }

    private fun verify(nonce: String, signatureText: String): Boolean {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(nonce.toByteArray(Charsets.UTF_8))
            val prefix = digest.joinToString("") { "%02x".format(it) }
                .substring(0, 12)
            val signatureBytes = Base64.decode(
                signatureText,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            Signature.getInstance("SHA256withRSA").run {
                initVerify(publicKey)
                update(prefix.toByteArray(Charsets.UTF_8))
                verify(signatureBytes)
            }
        }.getOrDefault(false)
    }

    private data class SignedSegments(
        val nonce: String,
        val signature: String
    )
}
