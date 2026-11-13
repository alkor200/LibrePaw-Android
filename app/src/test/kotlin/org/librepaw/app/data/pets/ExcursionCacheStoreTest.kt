package org.librepaw.app.data.pets

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test
import org.librepaw.app.testing.InMemorySharedPreferences

class ExcursionCacheStoreTest {
  @Test
  fun update_concurrentUpdatesForTheSamePet_neverLoseEachOthersExcursions() {
    val store = ExcursionCacheStore(InMemorySharedPreferences())
    val start = CountDownLatch(1)

    val threads =
      (1..50).map { id ->
        thread {
          start.await()
          store.update(petId = 1, serverKey = "example.org") { latest ->
            val excursions = latest?.excursions.orEmpty() + excursion(id.toLong())
            CachedExcursions(excursions, coveredFrom = 0L, cachedThroughEndTime = null) to Unit
          }
        }
      }
    start.countDown()
    threads.forEach { it.join() }

    assertEquals((1L..50L).toSet(), store.load(1, "example.org")?.excursions?.map { it.id }?.toSet())
  }

  private fun excursion(id: Long) =
    Excursion(
      id = id,
      startTime = 0L,
      endTime = 1L,
      startCoordinate = null,
      endCoordinate = null,
      distanceMeters = null,
      startHomeId = null,
      endHomeId = null,
    )
}
