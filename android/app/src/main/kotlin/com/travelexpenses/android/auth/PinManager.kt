package com.travelexpenses.android.auth

import android.content.Context
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Manages 6-digit PIN storage and verification.
 * PIN is stored as a PBKDF2-hashed value with a random salt in SharedPreferences.
 */
class PinManager(context: Context) {

    private val prefs = context.getSharedPreferences("travel_expenses_auth", Context.MODE_PRIVATE)

    fun isPinSet(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    fun setPin(pin: String) {
        require(pin.length == 6 && pin.all { it.isDigit() }) { "PIN must be 6 digits" }
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, salt.toHex())
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val saltHex = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val salt = saltHex.hexToBytes()
        return hashPin(pin, salt) == storedHash
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = factory.generateSecret(spec).encoded
        return hashBytes.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val SALT_LENGTH = 16
        private const val ITERATIONS = 10_000
        private const val KEY_LENGTH = 256
    }
}
