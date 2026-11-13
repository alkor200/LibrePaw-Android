package org.librepaw.app.data.pets

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoCacheTest {
  private var now = 0L
  private val cache = PhotoCache(maxEntries = 2, ttlMillis = 1000L, now = { now })
  private val url = "https://traccar.example.com/api/media/1/device.jpg".toHttpUrl()
  private val photo = byteArrayOf(1, 2, 3)

  @Test
  fun get_servesAFreshPhoto() {
    cache.put(1, url, photo)
    assertArrayEquals(photo, cache.get(1, url))
  }

  @Test
  fun get_expiredPhoto_isAMiss() {
    // Traccar reuses the filename, so only time tells a photo replaced elsewhere.
    cache.put(1, url, photo)
    now = 1000L
    assertNull(cache.get(1, url))
  }

  @Test
  fun get_differentUrlForThePet_isAMiss() {
    cache.put(1, url, photo)
    assertNull(cache.get(1, "https://traccar.example.com/api/media/1/other.jpg".toHttpUrl()))
  }

  @Test
  fun remove_dropsThePetsPhoto() {
    cache.put(1, url, photo)
    cache.remove(1)
    assertNull(cache.get(1, url))
  }

  @Test
  fun put_beyondMaxEntries_evictsTheLeastRecentlyUsed() {
    cache.put(1, url, photo)
    cache.put(2, url, photo)
    cache.get(1, url)
    cache.put(3, url, photo)

    assertNull(cache.get(2, url))
    assertArrayEquals(photo, cache.get(1, url))
  }
}
