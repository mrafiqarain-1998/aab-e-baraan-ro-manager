package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 password hashing utility.
 * Uses pure standard-library JCA + hex encoding so it works identically on Android API 24+,
 * Robolectric, and plain JVM unit tests without Base64 SDK version differences.
 */
object PasswordHashUtil {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(bytes)
        return toHex(bytes)
    }

    fun hashPassword(password: String, salt: String): String {
        if (password.isEmpty() || salt.isBlank()) return ""
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val hashBytes = factory.generateSecret(spec).encoded
            "pbkdf2_sha256\$$ITERATIONS\$${toHex(hashBytes)}"
        } finally {
            spec.clearPassword()
        }
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        if (password.isEmpty() || salt.isBlank() || expectedHash.isBlank()) return false
        return try {
            val parts = expectedHash.split('$')
            val actualHash = if (parts.size == 3 && parts[0] == "pbkdf2_sha256") {
                val iters = parts[1].toIntOrNull()?.coerceIn(1_000, 200_000) ?: ITERATIONS
                val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), iters, KEY_LENGTH_BITS)
                try {
                    val factory = SecretKeyFactory.getInstance(ALGORITHM)
                    val hashBytes = factory.generateSecret(spec).encoded
                    "pbkdf2_sha256\$$iters\$${toHex(hashBytes)}"
                } finally {
                    spec.clearPassword()
                }
            } else {
                hashPassword(password, salt)
            }
            MessageDigest.isEqual(
                actualHash.toByteArray(Charsets.UTF_8),
                expectedHash.toByteArray(Charsets.UTF_8)
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun toHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        val hexDigits = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = hexDigits[v ushr 4]
            chars[i * 2 + 1] = hexDigits[v and 0x0F]
        }
        return String(chars)
    }
}
