package org.librepaw.app.data.pets

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Persists closed excursions per pet+server (see TraccarPetRepository.serverKey), so they never need recomputing (see ADR-0026 on iOS). */
class ExcursionCacheStore(private val prefs: SharedPreferences) {
  constructor(context: Context) : this(context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE))

  private val json = Json { ignoreUnknownKeys = true }

  fun load(petId: Int, serverKey: String): CachedExcursions? {
    val raw = prefs.getString(key(petId, serverKey), null) ?: return null
    return try {
      json.decodeFromString<CachedExcursions>(raw)
    } catch (e: SerializationException) {
      null
    }
  }

  fun save(cache: CachedExcursions, petId: Int, serverKey: String) {
    prefs.edit().putString(key(petId, serverKey), json.encodeToString(cache)).apply()
  }

  fun clear(petId: Int, serverKey: String) {
    prefs.edit().remove(key(petId, serverKey)).apply()
  }

  /**
   * Removes every pet's cached excursions, earlier key versions included. Neither this store's
   * keys nor [LifetimeStatsStore]'s include the account, only server host and pet id, so whatever
   * one account cached (start/end coordinates, home ids, including excursions against homes only
   * that account could see) would otherwise surface for the next account to sign in to the same
   * server.
   */
  fun clearAll() {
    synchronized(UPDATE_LOCK) { prefs.edit().clear().apply() }
  }

  /**
   * Loads, transforms and saves in one step that no other [update] for any pet can interleave
   * with, unlike a separate [load] then [save]. fetchExcursions runs concurrently for the same pet
   * (e.g. Pet Detail loads lifetime stats and today's excursions side by side), and a plain
   * load/save pair let whichever finished last silently overwrite the other's freshly fetched
   * excursions.
   */
  fun <R> update(petId: Int, serverKey: String, transform: (CachedExcursions?) -> Pair<CachedExcursions, R>): R =
    synchronized(UPDATE_LOCK) {
      val (updated, result) = transform(load(petId, serverKey))
      save(updated, petId, serverKey)
      result
    }

  // v3: caches written before TraccarPetRepository.splitIntoExcursions can hold a walk closed at a
  // homecoming pause as final while its exit/enter pair was still open, with the watermark past the
  // exit, so the next walk is missing for good. v2 was the same for the coverage-gap fix in
  // mergeExcursions. A new key rebuilds every one of them once.
  private fun key(petId: Int, serverKey: String) = "v3:$serverKey:$petId"

  companion object {
    /** Must match the exclusion rules in `backup_rules.xml` / `data_extraction_rules.xml`. */
    const val PREFS_FILE_NAME = "librepaw_excursion_cache"

    /** Shared by every instance, they all read and write the same preferences file. */
    private val UPDATE_LOCK = Any()
  }
}
