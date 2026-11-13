package org.librepaw.app.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Reads (once) and removes a session stored by builds before [KeystoreCredentialStore], which used
 * Jetpack Security's now-deprecated `EncryptedSharedPreferences`. Only kept for that one-time
 * migration: once no installs from before it are left, this file and the `security-crypto`
 * dependency can go.
 */
class LegacyEncryptedSession(context: Context) {
  private val appContext = context.applicationContext
  private val json = Json { ignoreUnknownKeys = true }

  /** `null` when there is none, or it can't be read for any reason (it's then simply gone). */
  @Suppress("DEPRECATION")
  fun read(): StoredSession? {
    // Checked first so a fresh install never creates the file (and a Keystore key) just to find
    // nothing in it.
    if (!File(appContext.dataDir, "shared_prefs/$PREFS_FILE_NAME.xml").exists()) return null
    return try {
      val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
      val prefs =
        EncryptedSharedPreferences.create(
          appContext,
          PREFS_FILE_NAME,
          masterKey,
          EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
          EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
      prefs.getString(SESSION_KEY, null)?.let { json.decodeFromString<StoredSession>(it) }
    } catch (e: Exception) {
      // Anything at all (an unusable Keystore key, a corrupt file, an unexpected format): that's
      // exactly the crash this migration exists to get away from.
      null
    }
  }

  fun clear() {
    appContext.deleteSharedPreferences(PREFS_FILE_NAME)
  }

  companion object {
    /** Still excluded in `backup_rules.xml` / `data_extraction_rules.xml` until it's migrated. */
    const val PREFS_FILE_NAME = "librepaw_secure_session"

    private const val SESSION_KEY = "current_session"
  }
}
