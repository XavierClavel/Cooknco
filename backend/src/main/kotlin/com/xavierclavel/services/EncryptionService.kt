package com.xavierclavel.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.xavierclavel.utils.Configuration
import io.ktor.utils.io.core.toByteArray
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

class EncryptionService: KoinComponent {
    val configuration: Configuration by inject<Configuration>()

    fun encrypt(email: String): String {
        val ivBytes = ByteArray(16)
        SecureRandom().nextBytes(ivBytes)
        val iv = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, configuration.encryption.aesKey, iv)
        val encrypted = cipher.doFinal(email.toByteArray())

        return Base64.getEncoder().encodeToString(ivBytes + encrypted)
    }

    fun encryptPassword(password: String?): String? =
        password?.let {
            BCrypt.withDefaults().hashToString(configuration.encryption.passwordCost, password.toCharArray())
        }



    fun decrypt(encryptedEmail: String): String {
        val decoded = Base64.getDecoder().decode(encryptedEmail)
        val ivBytes = decoded.copyOfRange(0, 16)
        val encryptedBytes = decoded.copyOfRange(16, decoded.size)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, configuration.encryption.aesKey, IvParameterSpec(ivBytes))
        return String(cipher.doFinal(encryptedBytes))
    }

    fun hash(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}