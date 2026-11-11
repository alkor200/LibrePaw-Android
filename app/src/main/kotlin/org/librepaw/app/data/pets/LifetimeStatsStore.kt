package org.librepaw.app.data.pets

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Persists each pet's accumulated lifetime excursion totals, per pet+server. */
class LifetimeStatsStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true }

  fun load(petId: Int, serverHost: String): CachedLifetimeStats? {
    val raw = prefs.getString(key(petId, serverHost), null) ?: return null
    return try {
      json.decodeFromString<CachedLifetimeStats>(raw)
    } catch (e: SerializationException) {
      null
    }
  }

  fun save(cache: CachedLifetimeStats, petId: Int, serverHost: String) {
    prefs.edit().putString(key(petId, serverHost), json.encodeToString(cache)).apply()
  }

  fun clear(petId: Int, serverHost: String) {
    prefs.edit().remove(key(petId, serverHost)).apply()
  }

  private fun key(petId: Int, serverHost: String) = "$serverHost:$petId"

  companion object {
    private const val PREFS_FILE_NAME = "librepaw_lifetime_stats"
  }
}
