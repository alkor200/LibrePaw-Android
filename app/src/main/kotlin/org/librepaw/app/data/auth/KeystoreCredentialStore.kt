package org.librepaw.app.data.auth

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Stores the current [StoredSession] encrypted with an AES-256-GCM key that lives in the Android
 * Keystore and never leaves it (not even to this app's own process memory).
 *
 * The Android equivalent of the iOS app's Keychain storage: the file is excluded from Auto Backup
 * and device-to-device transfer (see `backup_rules.xml` / `data_extraction_rules.xml`), and the key
 * itself can't be backed up at all, so a session never leaves this device, matching the iOS app's
 * deliberate `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` choice.
 *
 * Replaces Jetpack Security's `EncryptedSharedPreferences`, which is deprecated and had no handling
 * here for its well-known failure mode: a Keystore key that becomes unusable (some OEM updates,
 * a restore onto new hardware) made it throw on creation, and since the session is restored while
 * composing the very first screen, that was a crash on every launch. Any such failure now just
 * reads as "not logged in", see [loadSession]. A session stored by that earlier implementation is
 * migrated once, see [migrateLegacySession].
 */
class KeystoreCredentialStore(
  context: Context,
  private val legacySession: () -> StoredSession? = { null },
  private val clearLegacySession: () -> Unit = {},
) : CredentialStore {
  private val json = Json { ignoreUnknownKeys = true }
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

  override fun save(session: StoredSession) {
    val plaintext = json.encodeToString(session).toByteArray(Charsets.UTF_8)
    val encrypted =
      try {
        encrypt(plaintext)
      } catch (e: GeneralSecurityException) {
        // An unusable key (see the class doc): start over with a fresh one, once.
        deleteKey()
        try {
          encrypt(plaintext)
        } catch (e: GeneralSecurityException) {
          throw CredentialStorageException(e)
        }
      }
    if (!prefs.edit().putString(SESSION_KEY, encrypted).commit()) {
      throw CredentialStorageException(null)
    }
  }

  override fun loadSession(): StoredSession? {
    val stored = prefs.getString(SESSION_KEY, null) ?: return migrateLegacySession()
    return try {
      json.decodeFromString<StoredSession>(String(decrypt(stored), Charsets.UTF_8))
    } catch (e: GeneralSecurityException) {
      discardUnreadableSession()
    } catch (e: IllegalArgumentException) {
      // Malformed Base64 or JSON (kotlinx's SerializationException is one too).
      discardUnreadableSession()
    }
  }

  // commit(), not apply(): a logout must be on disk before anything else happens, not whenever a
  // background write gets to it (or never, if the process dies first).
  @SuppressLint("ApplySharedPref")
  override fun clear() {
    prefs.edit().remove(SESSION_KEY).commit()
    clearLegacySession()
  }

  /** Moves a session stored by the earlier `EncryptedSharedPreferences` implementation over, once. */
  private fun migrateLegacySession(): StoredSession? {
    val session = legacySession() ?: return null
    return try {
      save(session)
      session
    } catch (e: CredentialStorageException) {
      null
    } finally {
      clearLegacySession()
    }
  }

  /** There's no way to recover it, so it's treated exactly like being logged out, see [clear]. */
  @SuppressLint("ApplySharedPref")
  private fun discardUnreadableSession(): StoredSession? {
    prefs.edit().remove(SESSION_KEY).commit()
    deleteKey()
    return null
  }

  private fun encrypt(plaintext: ByteArray): String {
    val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
    val ciphertext = cipher.doFinal(plaintext)
    return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
  }

  private fun decrypt(stored: String): ByteArray {
    val (iv, ciphertext) = stored.split(":").takeIf { it.size == 2 } ?: throw IllegalArgumentException("Malformed session")
    val key = existingKey() ?: throw GeneralSecurityException("Session key is gone")
    val cipher =
      Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
      }
    return cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
  }

  private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

  private fun existingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

  private fun key(): SecretKey =
    existingKey()
      ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
          init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setKeySize(256)
              .build()
          )
        }
        .generateKey()

  private fun deleteKey() {
    try {
      keyStore().deleteEntry(KEY_ALIAS)
    } catch (e: GeneralSecurityException) {
      // Nothing left to clean up then.
    }
  }

  companion object {
    /** Must match the exclusion rules in `backup_rules.xml` / `data_extraction_rules.xml`. */
    const val PREFS_FILE_NAME = "librepaw_session"

    private const val SESSION_KEY = "current_session"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "librepaw_session_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
  }
}

/** The session couldn't be stored securely on this device (an unusable Keystore, a full disk). */
class CredentialStorageException(cause: Throwable?) :
  Exception("Couldn't store the login securely on this device.", cause)
