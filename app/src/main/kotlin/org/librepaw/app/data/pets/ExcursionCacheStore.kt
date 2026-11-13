package org.librepaw.app.data.pets

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Persists closed excursions per pet+server, so they never need recomputing (see ADR-0026 on iOS). */
class ExcursionCacheStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true }

  fun load(petId: Int, serverHost: String): CachedExcursions? {
    val raw = prefs.getString(key(petId, serverHost), null) ?: return null
    return try {
      json.decodeFromString<CachedExcursions>(raw)
    } catch (e: SerializationException) {
      null
    }
  }

  fun save(cache: CachedExcursions, petId: Int, serverHost: String) {
    prefs.edit().putString(key(petId, serverHost), json.encodeToString(cache)).apply()
  }

  fun clear(petId: Int, serverHost: String) {
    prefs.edit().remove(key(petId, serverHost)).apply()
  }

  // v2: caches written before the coverage-gap fix in TraccarPetRepository.mergeExcursions may
  // already be missing excursions for good, a new key rebuilds every one of them once.
  private fun key(petId: Int, serverHost: String) = "v2:$serverHost:$petId"

  companion object {
    private const val PREFS_FILE_NAME = "librepaw_excursion_cache"
  }
}
