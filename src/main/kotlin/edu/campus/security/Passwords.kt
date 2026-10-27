package edu.campus.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Passwords {
    private const val ITERATIONS = 120_000
    private fun derive(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)).encoded

    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(derive(password, salt))}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 2) return false
        return MessageDigest.isEqual(
            derive(password, Base64.getDecoder().decode(parts[0])),
            Base64.getDecoder().decode(parts[1])
        )
    }
}
