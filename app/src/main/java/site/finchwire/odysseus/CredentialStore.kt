package site.finchwire.odysseus

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Credentials(val username: String, val password: String)

object CredentialStore {
    private const val FILE   = "odysseus_creds"
    private const val KEY_U  = "username"
    private const val KEY_P  = "password"

    private const val KEY_PIN = "app_pin"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(context: Context, creds: Credentials) {
        prefs(context).edit()
            .putString(KEY_U, creds.username)
            .putString(KEY_P, creds.password)
            .apply()
    }

    fun load(context: Context): Credentials? {
        val p = prefs(context)
        val u = p.getString(KEY_U, null) ?: return null
        val pw = p.getString(KEY_P, null) ?: return null
        return Credentials(u, pw)
    }

    fun saveAppPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN, pin).apply()
    }

    fun getAppPin(context: Context): String? {
        return prefs(context).getString(KEY_PIN, null)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
