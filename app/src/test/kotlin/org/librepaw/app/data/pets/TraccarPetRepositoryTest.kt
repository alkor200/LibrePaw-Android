package org.librepaw.app.data.pets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.librepaw.app.data.traccar.TraccarDates
import org.librepaw.app.data.traccar.TraccarEvent
import org.librepaw.app.data.traccar.TraccarPosition

class TraccarPetRepositoryTest {
  // MARK: - liveFetchStart

  @Test
  fun liveFetchStart_noCache_returnsFrom() {
    assertEquals(1000L, TraccarPetRepository.liveFetchStart(cached = null, from = 1000L))
  }

  @Test
  fun liveFetchStart_cacheDoesNotReachBackToFrom_returnsFrom() {
    val cached = CachedExcursions(excursions = emptyList(), coveredFrom = 500L, cachedThroughEndTime = 2000L)
    // from (100) is before the cache's own coverage (500), so there's a gap: don't trust the watermark.
    assertEquals(100L, TraccarPetRepository.liveFetchStart(cached, from = 100L))
  }

  @Test
  fun liveFetchStart_cacheCoversFrom_returnsWatermark() {
    val cached = CachedExcursions(excursions = emptyList(), coveredFrom = 0L, cachedThroughEndTime = 2000L)
    assertEquals(2000L, TraccarPetRepository.liveFetchStart(cached, from = 1000L))
  }

  @Test
  fun liveFetchStart_fromAfterWatermark_stillStartsAtWatermark() {
    // Everything after the watermark isn't final yet, and the exit that opened it can lie before
    // `from`: starting at `from` would miss every walk split out of that still-open pair.
    val cached = CachedExcursions(excursions = emptyList(), coveredFrom = 0L, cachedThroughEndTime = 2000L)
    assertEquals(2000L, TraccarPetRepository.liveFetchStart(cached, from = 5000L))
  }

  // MARK: - pairHomeExitsWithEnters

  @Test
  fun pairHomeExitsWithEnters_pairsEachExitWithNextEnter() {
    val exit1 = event(1, "2024-01-01T10:00:00.000+00:00")
    val enter1 = event(2, "2024-01-01T11:00:00.000+00:00")
    val exit2 = event(3, "2024-01-01T12:00:00.000+00:00")
    val enter2 = event(4, "2024-01-01T13:00:00.000+00:00")

    val pairs = TraccarPetRepository.pairHomeExitsWithEnters(listOf(exit1, exit2), listOf(enter1, enter2))

    assertEquals(2, pairs.size)
    assertEquals(enter1, pairs[0].second)
    assertEquals(enter2, pairs[1].second)
  }

  @Test
  fun pairHomeExitsWithEnters_unmatchedExit_pairsWithNull() {
    val exit = event(1, "2024-01-01T10:00:00.000+00:00")
    val pairs = TraccarPetRepository.pairHomeExitsWithEnters(listOf(exit), emptyList())
    assertNull(pairs.single().second)
  }

  // MARK: - isDegenerate

  @Test
  fun isDegenerate_shortExcursion_isTrue() {
    val excursion = excursion(id = 1, startTime = 0L, endTime = 30_000L)
    assertTrue(TraccarPetRepository.isDegenerate(excursion))
  }

  @Test
  fun isDegenerate_ongoingExcursion_isFalse() {
    val excursion = excursion(id = 1, startTime = 0L, endTime = null)
    assertTrue(!TraccarPetRepository.isDegenerate(excursion))
  }

  // MARK: - firstHomecomingPause

  @Test
  fun firstHomecomingPause_findsQuietPositionNearHome() {
    val home = HomeGeofence(id = 1, name = "Home", center = Coordinate(0.0, 0.0), radiusMeters = 50.0)
    val trail =
      listOf(
        position(0.0, 1.0, "2024-01-01T10:00:00.000+00:00"), // far from home
        position(0.0001, 0.0001, "2024-01-01T10:05:00.000+00:00"), // near home
        // 10-minute gap after the near-home position, exceeding the 5-minute quiet threshold
        position(0.0001, 0.0001, "2024-01-01T10:15:00.000+00:00"),
      )

    val homecoming = TraccarPetRepository.firstHomecomingPause(trail, listOf(home))

    assertEquals(1, homecoming?.homeId)
  }

  @Test
  fun firstHomecomingPause_noPositionNearHome_returnsNull() {
    val home = HomeGeofence(id = 1, name = "Home", center = Coordinate(0.0, 0.0), radiusMeters = 50.0)
    val trail =
      listOf(
        position(0.0, 1.0, "2024-01-01T10:00:00.000+00:00"),
        position(0.0, 2.0, "2024-01-01T10:01:00.000+00:00"),
      )
    assertNull(TraccarPetRepository.firstHomecomingPause(trail, listOf(home)))
  }

  @Test
  fun firstHomecomingPause_lastPositionNearHome_alwaysCounts() {
    // The last position in a trail has no "next" position, so its gap is treated as infinite —
    // it always counts as a homecoming if it's near a home, regardless of how soon it was.
    val home = HomeGeofence(id = 1, name = "Home", center = Coordinate(0.0, 0.0), radiusMeters = 50.0)
    val trail =
      listOf(
        position(0.0, 1.0, "2024-01-01T10:00:00.000+00:00"),
        position(0.0001, 0.0001, "2024-01-01T10:01:00.000+00:00"),
      )
    assertEquals(1, TraccarPetRepository.firstHomecomingPause(trail, listOf(home))?.homeId)
  }

  @Test
  fun firstHomecomingPause_singlePositionJustLeft_notEnoughTimeElapsed_doesNotCountYet() {
    // Checking the app moments after leaving (e.g. 50m from the front door): exactly one position
    // has been recorded so far, near home. Without measuring its gap against the real current time,
    // "nothing recorded after it yet" would default to an infinite gap and look exactly like a
    // homecoming, even though barely any time has actually passed.
    val home = HomeGeofence(id = 1, name = "Home", center = Coordinate(0.0, 0.0), radiusMeters = 40.0)
    val exitTime = TraccarDates.parse("2024-01-01T10:00:00.000+00:00")!!
    val trail = listOf(position(0.0004, 0.0004, "2024-01-01T10:00:00.000+00:00")) // ~50m from home

    val homecoming =
      TraccarPetRepository.firstHomecomingPause(
        trail,
        listOf(home),
        trailEndTimeMillis = exitTime + 30_000, // only 30 seconds have actually elapsed
      )

    assertNull(homecoming)
  }

  @Test
  fun firstHomecomingPause_singlePositionNearHome_enoughTimeElapsed_counts() {
    // Same shape, but a genuine 6 minutes have now passed with nothing further reported.
    val home = HomeGeofence(id = 1, name = "Home", center = Coordinate(0.0, 0.0), radiusMeters = 40.0)
    val exitTime = TraccarDates.parse("2024-01-01T10:00:00.000+00:00")!!
    val trail = listOf(position(0.0004, 0.0004, "2024-01-01T10:00:00.000+00:00"))

    val homecoming =
      TraccarPetRepository.firstHomecomingPause(
        trail,
        listOf(home),
        trailEndTimeMillis = exitTime + 6 * 60_000,
      )

    assertEquals(1, homecoming?.homeId)
  }

  @Test
  fun shouldPreferHomecoming_noRealEndTime_true() {
    assertTrue(TraccarPetRepository.shouldPreferHomecoming(Homecoming(100L, 1), null))
  }

  @Test
  fun shouldPreferHomecoming_homecomingEarlierThanRealEnd_true() {
    assertTrue(TraccarPetRepository.shouldPreferHomecoming(Homecoming(100L, 1), 200L))
  }

  @Test
  fun shouldPreferHomecoming_homecomingAtOrAfterRealEnd_false() {
    assertTrue(!TraccarPetRepository.shouldPreferHomecoming(Homecoming(200L, 1), 200L))
  }

  // MARK: - mergeExcursions

  @Test
  fun mergeExcursions_dedupesByIdAndAdvancesWatermark() {
    val cached = CachedExcursions(excursions = listOf(excursion(1, 0L, 1000L)), coveredFrom = 0L, cachedThroughEndTime = 1000L)
    val fresh = listOf(excursion(2, 2000L, 3000L))

    val (all, newCache) = TraccarPetRepository.mergeExcursions(cached, fresh, liveFrom = 1000L)

    assertEquals(2, all.size)
    assertEquals(3000L, newCache.cachedThroughEndTime)
  }

  @Test
  fun mergeExcursions_neverCachesAnOngoingExcursion() {
    val fresh = listOf(excursion(1, 0L, null))
    val (all, newCache) = TraccarPetRepository.mergeExcursions(cached = null, freshExcursions = fresh, liveFrom = 0L)

    assertEquals(1, all.size)
    assertTrue(newCache.excursions.isEmpty())
  }

  @Test
  fun mergeExcursions_liveFetchAfterWatermark_restartsCoverageInsteadOfClaimingTheGap() {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    val coveredFrom = now - 30 * day
    val watermark = now - 4 * day
    val cached =
      CachedExcursions(excursions = listOf(excursion(1, watermark - 3_600_000L, watermark)), coveredFrom = coveredFrom, cachedThroughEndTime = watermark)
    // E.g. a 24-hour Trail window opened four days after the cache was last updated: the three
    // days between the watermark and liveFrom were never fetched.
    val liveFrom = now - day
    val fresh = listOf(excursion(2, now - 7_200_000L, now - 3_600_000L))

    val (_, newCache) = TraccarPetRepository.mergeExcursions(cached, fresh, liveFrom)

    assertEquals(liveFrom, newCache.coveredFrom)
    assertEquals(now - 3_600_000L, newCache.cachedThroughEndTime)
    // And so a later, wider request re-fetches the gap instead of trusting the advanced watermark.
    assertEquals(coveredFrom, TraccarPetRepository.liveFetchStart(newCache, from = coveredFrom))
  }

  @Test
  fun mergeExcursions_liveFetchBeforeCoverage_extendsCoverageBackToIt() {
    val now = System.currentTimeMillis()
    val cached = CachedExcursions(excursions = emptyList(), coveredFrom = now - 86_400_000L, cachedThroughEndTime = now - 3_600_000L)
    val liveFrom = now - 7 * 86_400_000L

    val (_, newCache) = TraccarPetRepository.mergeExcursions(cached, emptyList(), liveFrom)

    assertEquals(liveFrom, newCache.coveredFrom)
  }

  @Test
  fun mergeExcursions_provisionalExcursion_isShownButNeitherCachedNorAdvancingTheWatermark() {
    val now = System.currentTimeMillis()
    val watermark = now - 86_400_000L
    val cached = CachedExcursions(excursions = emptyList(), coveredFrom = now - 30 * 86_400_000L, cachedThroughEndTime = watermark)
    val closedAtPause = excursion(281, now - 7 * 3_600_000L, now - 6 * 3_600_000L, distanceMeters = 800.0)

    val (all, newCache) =
      TraccarPetRepository.mergeExcursions(cached, listOf(closedAtPause), liveFrom = watermark, provisionalIds = setOf(281L))

    assertEquals(listOf(281L), all.map { it.id })
    assertTrue(newCache.excursions.isEmpty())
    assertEquals(watermark, newCache.cachedThroughEndTime)
  }

  // MARK: - mergeLifetimeStats

  @Test
  fun mergeLifetimeStats_addsOnlyExcursionsAfterWatermark() {
    val cached = CachedLifetimeStats(totalDistanceMeters = 100.0, totalDurationMillis = 1000L, cachedThroughEndTime = 500L)
    val alreadyCounted = excursion(1, 0L, 400L, distanceMeters = 999.0)
    val new = excursion(2, 600L, 600_700L, distanceMeters = 50.0)

    val (stats, newCache) = TraccarPetRepository.mergeLifetimeStats(cached, listOf(alreadyCounted, new))

    assertEquals(150.0, stats.totalDistanceMeters, 0.01)
    assertEquals(600_700L, newCache.cachedThroughEndTime)
  }

  @Test
  fun mergeLifetimeStats_excludesLikelyVehicleTrips() {
    // 100 km in 1 minute is unmistakably a vehicle trip, not a walk.
    val vehicleTrip = excursion(1, 0L, 60_000L, distanceMeters = 100_000.0)
    val (stats, _) = TraccarPetRepository.mergeLifetimeStats(cached = null, recentExcursions = listOf(vehicleTrip))
    assertEquals(0.0, stats.totalDistanceMeters, 0.01)
  }

  @Test
  fun mergeLifetimeStats_includesOngoingExcursionInStatsButNotCache() {
    val ongoing = excursion(1, startTime = System.currentTimeMillis() - 10_000, endTime = null, distanceMeters = 20.0)
    val (stats, newCache) = TraccarPetRepository.mergeLifetimeStats(cached = null, recentExcursions = listOf(ongoing))

    assertTrue(stats.totalDistanceMeters >= 20.0)
    assertNull(newCache.cachedThroughEndTime)
  }

  private fun event(id: Int, eventTime: String, geofenceId: Int = 1) =
    TraccarEvent(id = id.toLong(), type = "geofenceExit", eventTime = eventTime, deviceId = 1, geofenceId = geofenceId)

  private fun position(lat: Double, lon: Double, fixTime: String) =
    TraccarPosition(id = 1, deviceId = 1, fixTime = fixTime, latitude = lat, longitude = lon, speed = 0.0)

  private fun excursion(id: Int, startTime: Long, endTime: Long?, distanceMeters: Double? = null) =
    Excursion(
      id = id.toLong(),
      startTime = startTime,
      endTime = endTime,
      startCoordinate = null,
      endCoordinate = null,
      distanceMeters = distanceMeters,
      startHomeId = null,
      endHomeId = null,
    )
}
