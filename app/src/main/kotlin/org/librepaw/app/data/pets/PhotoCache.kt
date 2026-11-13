package org.librepaw.app.data.pets

import okhttp3.HttpUrl

/**
 * Recently fetched pet photos, in memory for one session. Without it every avatar on the pet list
 * and every switch back to the map re-downloaded every photo (up to ~500 KB each).
 *
 * Entries expire after [ttlMillis]: Traccar stores every upload under the same filename
 * (`device.jpg`), so the URL alone can't tell a photo someone else replaced since. A photo set
 * from this app is dropped right away instead, see [remove].
 */
class PhotoCache(
  private val maxEntries: Int = 32,
  private val ttlMillis: Long = 10 * 60 * 1000L,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private data class Entry(val url: HttpUrl, val bytes: ByteArray, val fetchedAt: Long)

  private val entries =
    object : LinkedHashMap<Int, Entry>(16, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Entry>): Boolean = size > maxEntries
    }

  @Synchronized
  fun get(petId: Int, url: HttpUrl): ByteArray? {
    val entry = entries[petId] ?: return null
    if (entry.url != url || now() - entry.fetchedAt >= ttlMillis) {
      entries.remove(petId)
      return null
    }
    return entry.bytes
  }

  @Synchronized
  fun put(petId: Int, url: HttpUrl, bytes: ByteArray) {
    entries[petId] = Entry(url, bytes, now())
  }

  @Synchronized
  fun remove(petId: Int) {
    entries.remove(petId)
  }
}
