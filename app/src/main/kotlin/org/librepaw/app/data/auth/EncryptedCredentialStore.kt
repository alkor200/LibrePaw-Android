package org.librepaw.app.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Stores the current [StoredSession] in an encrypted preferences file.
 *
 * The Android equivalent of the iOS app's Keychain storage: the file is
 * excluded from Auto Backup and device-to-device transfer (see
 * `backup_rules.xml` / `data_extraction_rules.xml`), so a session never
 * leaves this device, matching the iOS app's deliberate
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` choice.
 */
class EncryptedCredentialStore(context: Context) : CredentialStore {
  private val json = Json { ignoreUnknownKeys = true }
  private val appContext = context.applicationContext

  private val prefs by lazy {
    val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    EncryptedSharedPreferences.create(
      appContext,
      PREFS_FILE_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  override fun save(session: StoredSession) {
    prefs.edit().putString(SESSION_KEY, json.encodeToString(session)).apply()
  }

  override fun loadSession(): StoredSession? {
    val raw = prefs.getString(SESSION_KEY, null) ?: return null
    return try {
      json.decodeFromString<StoredSession>(raw)
    } catch (e: SerializationException) {
      null
    }
  }

  override fun clear() {
    prefs.edit().remove(SESSION_KEY).apply()
  }

  companion object {
    /** Must match the exclusion rules in `backup_rules.xml` / `data_extraction_rules.xml`. */
    const val PREFS_FILE_NAME = "librepaw_secure_session"

    private const val SESSION_KEY = "current_session"
  }
}
