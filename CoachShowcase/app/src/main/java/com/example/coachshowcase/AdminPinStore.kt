package com.example.coachshowcase

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal class AdminPinStore(context: Context) {
    private val preferences = context.getSharedPreferences("kiosk_admin", Context.MODE_PRIVATE)

    val isConfigured: Boolean
        get() = preferences.contains("pin_hash") && preferences.contains("pin_salt")

    fun save(pin: String) {
        require(pin.length in 6..12 && pin.all(Char::isDigit))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        preferences.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .remove("failed_attempts")
            .remove("blocked_until")
            .apply()
    }

    fun verify(pin: String): PinResult {
        val remaining = preferences.getLong("blocked_until", 0L) - System.currentTimeMillis()
        if (remaining > 0) return PinResult.Blocked((remaining + 999) / 1000)
        val saltText = preferences.getString("pin_salt", null) ?: return PinResult.NotConfigured
        val hashText = preferences.getString("pin_hash", null) ?: return PinResult.NotConfigured
        val expected = Base64.decode(hashText, Base64.NO_WRAP)
        val actual = derive(pin, Base64.decode(saltText, Base64.NO_WRAP))
        if (MessageDigest.isEqual(expected, actual)) {
            preferences.edit().remove("failed_attempts").remove("blocked_until").apply()
            return PinResult.Correct
        }
        val failures = preferences.getInt("failed_attempts", 0) + 1
        if (failures >= 5) {
            preferences.edit()
                .putInt("failed_attempts", 0)
                .putLong("blocked_until", System.currentTimeMillis() + 30_000L)
                .apply()
            return PinResult.Blocked(30)
        }
        preferences.edit().putInt("failed_attempts", failures).apply()
        return PinResult.Incorrect(5 - failures)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

internal sealed interface PinResult {
    data object Correct : PinResult
    data object NotConfigured : PinResult
    data class Incorrect(val attemptsLeft: Int) : PinResult
    data class Blocked(val secondsLeft: Long) : PinResult
}
