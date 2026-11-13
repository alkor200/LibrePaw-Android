package org.librepaw.app.data.pets

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Persists each pet's accumulated lifetime excursion totals, per pet+server (see TraccarPetRepository.serverKey). */
class LifetimeStatsStore(private val prefs: SharedPreferences) {
  constructor(context: Context) : this(context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE))

  private val json = Json { ignoreUnknownKeys = true }

  fun load(petId: Int, serverKey: String): CachedLifetimeStats? {
    val raw = prefs.getString(key(petId, serverKey), null) ?: return null
    return try {
      json.decodeFromString<CachedLifetimeStats>(raw)
    } catch (e: SerializationException) {
      null
    }
  }

  fun save(cache: CachedLifetimeStats, petId: Int, serverKey: String) {
    prefs.edit().putString(key(petId, serverKey), json.encodeToString(cache)).apply()
  }

  fun clear(petId: Int, serverKey: String) {
    prefs.edit().remove(key(petId, serverKey)).apply()
  }

  /** Removes every pet's totals, earlier key versions included, see [ExcursionCacheStore.clearAll]. */
  fun clearAll() {
    prefs.edit().clear().apply()
  }

  // v3 (and v2 before it): rebuilt together with the excursions cache (see its own key), totals
  // folded from a cache missing walks would stay short forever.
  private fun key(petId: Int, serverKey: String) = "v3:$serverKey:$petId"

  companion object {
    /** Must match the exclusion rules in `backup_rules.xml` / `data_extraction_rules.xml`. */
    const val PREFS_FILE_NAME = "librepaw_lifetime_stats"
  }
}
