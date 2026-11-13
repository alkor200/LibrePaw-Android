package org.librepaw.app.data.pets

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.librepaw.app.data.traccar.TraccarClient
import org.librepaw.app.data.traccar.TraccarDates
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.data.traccar.TraccarEvent
import org.librepaw.app.data.traccar.TraccarGeofence
import org.librepaw.app.data.traccar.TraccarGeofenceArea
import org.librepaw.app.data.traccar.TraccarPosition

/** [PetRepository] backed by a live Traccar server. */
class TraccarPetRepository(
  private val client: TraccarClient,
  private val excursionCacheStore: ExcursionCacheStore,
  private val lifetimeStatsStore: LifetimeStatsStore,
) : PetRepository {
  override suspend fun fetchPets(): List<Pet> =
    coroutineScope {
      val devicesDeferred = async { client.fetchDevices() }
      val positionsDeferred = async { client.fetchLatestPositions() }
      val positionsByDeviceId = positionsDeferred.await().associateBy { it.deviceId }
      devicesDeferred.await().map { device ->
        PetMapping.pet(device, positionsByDeviceId[device.id], client.serverUrl)
      }
    }

  override suspend fun fetchPhoto(pet: Pet): ByteArray? {
    val photoUrl = pet.photoUrl ?: return null
    return client.fetchData(photoUrl)
  }

  override suspend fun currentUserPermissions(): AccountPermissions {
    val user = client.fetchCurrentUser()
    return AccountPermissions(
      isAdministrator = user.administrator,
      canManageHomes = user.administrator || !user.readonly,
      canManageDevice = user.administrator || (!user.readonly && !user.deviceReadonly),
      canViewExcursions = user.administrator || !user.disableReports,
    )
  }

  override suspend fun renamePet(petId: Int, name: String) {
    client.updateDeviceName(petId, name)
  }

  override suspend fun setPhoto(petId: Int, jpegData: ByteArray) {
    client.updateDeviceImage(petId, jpegData, "image/jpeg")
  }

  override suspend fun fetchTrail(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<PetPosition> {
    val positions = client.fetchPositionHistory(petId, fromEpochMillis, toEpochMillis)
    return positions.sortedBy { TraccarDates.parse(it.fixTime) ?: 0L }.map(PetMapping::position)
  }

  // MARK: - Home geofences (see ADR-0014/ADR-0016 on the iOS side)

  /**
   * Marks a geofence as one LibrePaw manages as a "home", so it can be found again regardless of
   * whether the owner renames it in Traccar. Kept matching the iOS app's own stored attribute key
   * exactly: it's an internal key already present on any live server's existing homes, never shown
   * to a user, so there's no reason for the two platforms to disagree on it.
   */
  private val homeAttributeKey = "scentrailHome"

  override suspend fun homeGeofences(petId: Int): List<HomeGeofence> = homeTraccarGeofences(petId).mapNotNull(::toHomeGeofence)

  override suspend fun addHomeGeofence(petId: Int, name: String, center: Coordinate, radiusMeters: Double) {
    val area = TraccarGeofenceArea.circleWkt(center.latitude, center.longitude, radiusMeters)
    val created = client.createGeofence(name, area, mapOf(homeAttributeKey to JsonPrimitive(true)))
    client.linkGeofenceToDevice(created.id, petId)
    syncGeofenceVisibility(created.id)
  }

  override suspend fun updateHomeGeofence(homeId: Int, petId: Int, name: String, center: Coordinate, radiusMeters: Double) {
    val area = TraccarGeofenceArea.circleWkt(center.latitude, center.longitude, radiusMeters)
    client.updateGeofence(homeId, name, area, mapOf(homeAttributeKey to JsonPrimitive(true)))
    syncGeofenceVisibility(homeId)
  }

  override suspend fun deleteHomeGeofence(homeId: Int) {
    client.deleteGeofence(homeId)
  }

  /**
   * Grants whoever is using the app right now the same link to this geofence: linking a geofence
   * to a device only controls which device's crossings generate events for it, it does not by
   * itself make the geofence visible to any user (ADR-0017). Best-effort and never throws, same as
   * iOS; unlike iOS, this doesn't also propagate to every other device-permitted user, since that
   * needs an admin-only, param-order-sensitive endpoint for marginal benefit over each person's own
   * grant from having created or edited the home themselves.
   */
  private suspend fun syncGeofenceVisibility(geofenceId: Int) {
    try {
      val currentUser = client.fetchCurrentUser()
      client.linkGeofenceToUser(geofenceId, currentUser.id)
    } catch (e: TraccarError) {
      // Best-effort.
    }
  }

  private suspend fun homeTraccarGeofences(petId: Int): List<TraccarGeofence> =
    client.fetchGeofences(petId).filter { (it.attributes[homeAttributeKey] as? JsonPrimitive)?.booleanOrNull == true }

  private fun toHomeGeofence(geofence: TraccarGeofence): HomeGeofence? {
    val circle = TraccarGeofenceArea.parseCircle(geofence.area) ?: return null
    return HomeGeofence(geofence.id, geofence.name, Coordinate(circle.latitude, circle.longitude), circle.radiusMeters)
  }

  // MARK: - Excursions and lifetime stats (see ADR-0025/ADR-0026 on the iOS side)

  override suspend fun fetchExcursions(petId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<Excursion> {
    val homeTraccar = homeTraccarGeofences(petId)
    if (homeTraccar.isEmpty()) return emptyList()
    val homeIds = homeTraccar.map { it.id }.toSet()
    val homes = homeTraccar.mapNotNull(::toHomeGeofence)

    val serverHost = client.serverUrl.host
    val cached = excursionCacheStore.load(petId, serverHost)
    val liveFrom = liveFetchStart(cached, fromEpochMillis)

    var freshExcursions = emptyList<Excursion>()
    var provisionalIds = emptySet<Long>()
    if (liveFrom < toEpochMillis) {
      val (exits, enters) =
        coroutineScope {
          val exitsDeferred = async {
            client.fetchEvents(petId, liveFrom, toEpochMillis, TraccarEvent.EventType.GEOFENCE_EXIT)
          }
          val entersDeferred = async {
            client.fetchEvents(petId, liveFrom, toEpochMillis, TraccarEvent.EventType.GEOFENCE_ENTER)
          }
          val exits = exitsDeferred.await().filter { homeIds.contains(it.geofenceId ?: -1) }
          val enters = entersDeferred.await().filter { homeIds.contains(it.geofenceId ?: -1) }
          exits to enters
        }

      val pairs = pairHomeExitsWithEnters(exits, enters)
      // Bounded rather than fully unbounded: a pet with years of history backfilling the cache for
      // the first time would otherwise fire a request per excursion all at once.
      val resolved =
        pairs.concurrentMap(maxConcurrent = 20) { (exit, enter) ->
          makeExcursions(petId, exit, enter, homes) to (enter != null)
        }
      freshExcursions = resolved.flatMap { it.first }.filter { !isDegenerate(it) }
      // Everything split out of a pair Traccar hasn't closed yet is provisional, even a part that
      // already ended at a homecoming pause: later positions can still turn the rest of the pair
      // into further excursions, see mergeExcursions.
      provisionalIds = resolved.filter { !it.second }.flatMap { (excursions, _) -> excursions.map { it.id } }.toSet()
    }

    // Merged against whatever is stored *now*, not `cached` from before the fetch: another
    // concurrent call for the same pet may have saved in the meantime (see ExcursionCacheStore.update).
    val all =
      excursionCacheStore.update(petId, serverHost) { latest ->
        val (all, newCache) = mergeExcursions(latest, freshExcursions, liveFrom, provisionalIds)
        newCache to all
      }

    return all
      .filter { it.startTime in fromEpochMillis..toEpochMillis }
      .filter { !isDegenerate(it) }
      .sortedByDescending { it.startTime }
  }

  override suspend fun resetExcursionsCache(petId: Int) {
    excursionCacheStore.clear(petId, client.serverUrl.host)
  }

  override suspend fun lifetimeExcursionStats(petId: Int): LifetimeStats {
    val serverHost = client.serverUrl.host
    val cached = lifetimeStatsStore.load(petId, serverHost)

    // The whole history, not just from this cache's own watermark: that can fall in the middle of a
    // still-open exit/enter pair (a walk closed by a homecoming pause, Traccar still waiting for an
    // enter), and the next walk split out of that pair only exists when the pair is evaluated from
    // its exit. mergeLifetimeStats skips whatever was already counted, and fetchExcursions serves
    // everything final from its own cache.
    val recentExcursions = fetchExcursions(petId, 0L, System.currentTimeMillis())

    val (stats, newCache) = mergeLifetimeStats(cached, recentExcursions)
    lifetimeStatsStore.save(newCache, petId, serverHost)
    return stats
  }

  override suspend fun resetLifetimeStats(petId: Int) {
    lifetimeStatsStore.clear(petId, client.serverUrl.host)
  }

  /** Every excursion one exit/enter pair stands for, see [splitIntoExcursions]. */
  private suspend fun makeExcursions(petId: Int, exit: TraccarEvent, enter: TraccarEvent?, homes: List<HomeGeofence>): List<Excursion> {
    val exitTime = TraccarDates.parse(exit.eventTime) ?: 0L
    // For a still-open pair, "now" is what a trailing position's quiet gap is measured against: a
    // single position moments after leaving (e.g. checking the app 50m from the front door) would
    // otherwise look exactly like an infinitely long silence purely because a second fix hasn't
    // streamed in yet, not because the pet stopped.
    val trailEndTime = enter?.eventTime?.let(TraccarDates::parse) ?: System.currentTimeMillis()
    // Not caught: a failed fetch here must fail the whole call rather than produce an excursion
    // with no route and no distance, a closed one of those would be cached for good and folded
    // into lifetime stats as zero meters, never recomputed. Failing instead just means the next
    // load tries again.
    val trail = client.fetchPositionHistory(petId, exitTime, trailEndTime)
    return splitIntoExcursions(exit, enter, trail, homes, trailEndTime)
  }

  companion object {
    /** Below this, an exit immediately followed by an enter is GPS/geofence boundary jitter, not a real trip. */
    const val MINIMUM_EXCURSION_DURATION_MILLIS = 60_000L

    /** Extra distance beyond a home's own configured radius that still counts as "close enough" for a homecoming pause. */
    const val HOME_PROXIMITY_SLACK_METERS = 100.0

    /** How long the trail has to go quiet right after a near-home position before trusting it as a real homecoming. */
    const val QUIET_GAP_AFTER_HOME_PROXIMITY_MILLIS = 5 * 60 * 1000L

    /**
     * Where to start a live fetch from, given what's already cached. Uses the watermark when the
     * cache's own coverage already reaches back to (or before) `from`, never when there's a gap
     * between the true start of history and wherever an earlier, narrower call first populated
     * this cache (see [CachedExcursions.coveredFrom]).
     *
     * The watermark even when `from` is later: everything after it is what isn't final yet, and the
     * exit event that opened it can lie before `from`. Starting at `from` instead missed that exit,
     * and with it every walk split out of a still-open pair (see [splitIntoExcursions]), e.g. this
     * afternoon's walk for a request starting after this morning's.
     */
    fun liveFetchStart(cached: CachedExcursions?, from: Long): Long {
      val coveredFrom = cached?.coveredFrom
      val watermark = cached?.cachedThroughEndTime
      if (coveredFrom != null && coveredFrom <= from && watermark != null) {
        return watermark
      }
      return from
    }

    /**
     * Folds [freshExcursions] into [cached], keyed by id so re-fetching one already cached never
     * duplicates it, a fresh copy simply wins. Only final excursions are kept in the returned cache
     * and advance its watermark: closed (`endTime != null`) and not in [provisionalIds], i.e. not
     * split out of a pair Traccar hasn't closed yet. Anything else is included in the returned list
     * (what the caller actually sees) but never persisted. A provisional excursion that already
     * ended at a homecoming pause used to be cached anyway, moving the watermark past the still-open
     * exit event, so the next walk, which Traccar never gives an exit of its own, was never looked
     * for again.
     */
    fun mergeExcursions(
      cached: CachedExcursions?,
      freshExcursions: List<Excursion>,
      liveFrom: Long,
      provisionalIds: Set<Long> = emptySet(),
    ): Pair<List<Excursion>, CachedExcursions> {
      val byId = LinkedHashMap<Long, Excursion>()
      cached?.excursions?.forEach { byId[it.id] = it }
      var watermark = cached?.cachedThroughEndTime

      for (excursion in freshExcursions) {
        byId[excursion.id] = excursion
        if (excursion.id in provisionalIds) continue
        val endTime = excursion.endTime
        if (endTime != null && (watermark == null || endTime > watermark)) {
          watermark = endTime
        }
      }

      // Whichever is earlier, the cache's own prior coverage start or this call's liveFrom, but
      // only while the two ranges actually connect: liveFrom no later than the cache's watermark
      // (or its own coverage start). A request starting *after* the watermark (e.g. a 24-hour
      // Trail window opened days after the cache was last updated) leaves a gap nothing ever
      // fetched, so coverage restarts at liveFrom instead. Otherwise the advanced watermark would
      // make every later, wider request skip that gap forever, lifetime stats included.
      val previousCoveredFrom = cached?.coveredFrom
      val coveredFrom =
        if (previousCoveredFrom != null && liveFrom <= maxOf(previousCoveredFrom, cached.cachedThroughEndTime ?: previousCoveredFrom)) {
          minOf(previousCoveredFrom, liveFrom)
        } else {
          liveFrom
        }

      val closed = byId.values.filter { it.endTime != null && it.id !in provisionalIds }
      val newCache = CachedExcursions(excursions = closed, coveredFrom = coveredFrom, cachedThroughEndTime = watermark)
      return byId.values.toList() to newCache
    }

    /** Pairs each home-geofence exit with the next home-geofence enter chronologically after it. */
    fun pairHomeExitsWithEnters(exits: List<TraccarEvent>, enters: List<TraccarEvent>): List<Pair<TraccarEvent, TraccarEvent?>> {
      val sortedExits = exits.sortedBy { TraccarDates.parse(it.eventTime) ?: 0L }
      val sortedEnters = enters.sortedBy { TraccarDates.parse(it.eventTime) ?: 0L }

      val pairs = mutableListOf<Pair<TraccarEvent, TraccarEvent?>>()
      var enterIndex = 0
      for (exit in sortedExits) {
        val exitTime = TraccarDates.parse(exit.eventTime) ?: 0L
        while (enterIndex < sortedEnters.size && (TraccarDates.parse(sortedEnters[enterIndex].eventTime) ?: 0L) <= exitTime) {
          enterIndex++
        }
        val matchingEnter = sortedEnters.getOrNull(enterIndex)
        if (matchingEnter != null) enterIndex++
        pairs.add(exit to matchingEnter)
      }
      return pairs
    }

    fun isDegenerate(excursion: Excursion): Boolean {
      val duration = excursion.durationMillis ?: return false
      return duration < MINIMUM_EXCURSION_DURATION_MILLIS
    }

    /**
     * Turns one exit/enter pair, and the position trail between them, into the excursions it
     * actually stands for: usually one, but several when the trail shows the pet coming home and
     * leaving again in between without Traccar ever noticing (see ADR-0029 on the iOS side).
     *
     * Traccar only logs `geofenceEnter`/`geofenceExit` when a reported position crosses the exact
     * boundary. A tracker that falls asleep a few meters short of it on the way home never reports
     * the crossing, and when it wakes up for the next walk it's often already outside again, so
     * that walk gets no exit event either. Confirmed live with Phoebe: one exit at 08:39 and one
     * enter at 16:12 for what were really two walks, 08:39 to 09:17 (asleep 42m from the center of
     * a 40m home) and 15:12 to 16:12 (woke up 126m away). Treating the pair as one excursion lost
     * the afternoon walk entirely.
     *
     * Walks the trail segment by segment:
     * - A segment ends at its first homecoming pause (see [firstHomecomingPause]) if that's
     *   genuinely earlier than the real enter event (see [shouldPreferHomecoming]), otherwise at the
     *   enter event itself, or not at all while the pair is still open.
     * - After a pause, the next segment starts where the trail next goes clearly away from every
     *   home (beyond radius plus [HOME_PROXIMITY_SLACK_METERS]), backdated over the moving
     *   positions right before it that are already outside the home's own radius (see
     *   [nextDepartureIndex]). No such position, no further segment: the pet stayed home.
     * - A segment that never got clearly away before its pause, and is followed by a departure,
     *   wasn't a trip of its own: an exit that was only GPS jitter at the fence (confirmed live,
     *   Phoebe "left" at 48m from a 40m home and slept there for hours before the real walk the
     *   next day), or a few minutes without signal by the front door. That stub is dropped, and the
     *   walk after it keeps the exit's id but starts at its real departure, rather than stretching
     *   from the jitter all the way through the night. This replaces the earlier
     *   `requireStaysHomeAfterward` guard, which protected against the front door case by refusing
     *   any pause the pet later walked away from, and with it also refused the genuine homecoming
     *   above.
     *
     * The first segment keeps the exit event's id. A later one has no event of its own, so it's
     * identified by its first position's id, negated so it can never collide with an event id, and
     * stable across recomputations since history only ever gets appended to.
     */
    fun splitIntoExcursions(
      exit: TraccarEvent,
      enter: TraccarEvent?,
      trail: List<TraccarPosition>,
      homes: List<HomeGeofence>,
      trailEndTimeMillis: Long,
    ): List<Excursion> {
      val sorted = trail.sortedBy(::fixTimeOf)
      val enterTime = enter?.eventTime?.let(TraccarDates::parse)
      val excursions = mutableListOf<Excursion>()

      var segmentId = exit.id
      var segmentStartTime = TraccarDates.parse(exit.eventTime) ?: 0L
      var segmentStartHomeId = exit.geofenceId
      var segmentStartIndex = 0

      while (true) {
        val pause =
          homecomingPauseIndex(sorted, segmentStartIndex, homes, trailEndTimeMillis = trailEndTimeMillis)?.takeIf { (index, homeId) ->
            shouldPreferHomecoming(Homecoming(fixTimeOf(sorted[index]), homeId), enterTime)
          }

        if (pause == null) {
          excursions +=
            excursion(
              id = segmentId,
              startTime = segmentStartTime,
              endTime = enterTime,
              positions = sorted.subList(minOf(segmentStartIndex, sorted.size), sorted.size),
              startHomeId = segmentStartHomeId,
              endHomeId = enter?.geofenceId,
            )
          return excursions
        }

        val (pauseIndex, pauseHomeId) = pause
        val departureIndex = nextDepartureIndex(sorted, pauseIndex, homes)
        val segmentWentAway = sorted.subList(segmentStartIndex, pauseIndex + 1).any { !isNearAnyHome(it, homes) }

        if (segmentWentAway || departureIndex == null) {
          excursions +=
            excursion(
              id = segmentId,
              startTime = segmentStartTime,
              endTime = fixTimeOf(sorted[pauseIndex]),
              positions = sorted.subList(segmentStartIndex, pauseIndex + 1),
              startHomeId = segmentStartHomeId,
              endHomeId = pauseHomeId,
            )
        }

        if (departureIndex == null) return excursions
        if (segmentWentAway) {
          // A walk of its own, with no event of its own.
          segmentId = -sorted[departureIndex].id
          segmentStartHomeId = pauseHomeId
        }
        // Otherwise the stub before it was no trip at all (see the doc comment), the walk is still
        // this exit's own excursion.
        segmentStartTime = fixTimeOf(sorted[departureIndex])
        segmentStartIndex = departureIndex
      }
    }

    /**
     * Where the trail next leaves home for real after [pauseIndex]: the first position clearly away
     * from every home (beyond its radius plus [HOME_PROXIMITY_SLACK_METERS], so pottering about just
     * outside the fence doesn't count), backdated over the positions right before it that are
     * already outside every home's own radius, i.e. to where the pet actually crossed out.
     * Backdating stops at a quiet gap: a position followed by [QUIET_GAP_AFTER_HOME_PROXIMITY_MILLIS]
     * of silence is the tracker asleep at home, not the start of the walk, even if it happens to lie
     * just outside the fence. `null` if it never leaves.
     */
    private fun nextDepartureIndex(sorted: List<TraccarPosition>, pauseIndex: Int, homes: List<HomeGeofence>): Int? {
      val awayIndex = (pauseIndex + 1 until sorted.size).firstOrNull { !isNearAnyHome(sorted[it], homes) } ?: return null
      var departureIndex = awayIndex
      while (departureIndex - 1 > pauseIndex) {
        val previous = sorted[departureIndex - 1]
        val gap = fixTimeOf(sorted[departureIndex]) - fixTimeOf(previous)
        if (isNearAnyHome(previous, homes, proximitySlackMeters = 0.0) || gap >= QUIET_GAP_AFTER_HOME_PROXIMITY_MILLIS) break
        departureIndex--
      }
      return departureIndex
    }

    private fun excursion(
      id: Long,
      startTime: Long,
      endTime: Long?,
      positions: List<TraccarPosition>,
      startHomeId: Int?,
      endHomeId: Int?,
    ): Excursion {
      val coordinates = positions.map { Coordinate(it.latitude, it.longitude) }
      val distance = if (coordinates.size > 1) coordinates.zipWithNext().sumOf { (a, b) -> a.distanceMeters(b) } else null
      return Excursion(
        id = id,
        startTime = startTime,
        endTime = endTime,
        startCoordinate = coordinates.firstOrNull(),
        endCoordinate = coordinates.lastOrNull(),
        distanceMeters = distance,
        startHomeId = startHomeId,
        endHomeId = endHomeId,
      )
    }

    private fun fixTimeOf(position: TraccarPosition): Long = TraccarDates.parse(position.fixTime) ?: 0L

    private fun nearHome(position: TraccarPosition, homes: List<HomeGeofence>, proximitySlackMeters: Double): HomeGeofence? {
      val coordinate = Coordinate(position.latitude, position.longitude)
      return homes.firstOrNull { coordinate.distanceMeters(it.center) <= it.radiusMeters + proximitySlackMeters }
    }

    private fun isNearAnyHome(
      position: TraccarPosition,
      homes: List<HomeGeofence>,
      proximitySlackMeters: Double = HOME_PROXIMITY_SLACK_METERS,
    ): Boolean = nearHome(position, homes, proximitySlackMeters) != null

    /**
     * Finds the earliest trail position that's both near a home (within its radius plus slack) and
     * followed by a long enough quiet gap, treating that as the excursion's true end even without a
     * real `geofenceEnter` event (a tracker's own firmware sleep mode can trigger a few meters short
     * of the boundary). A quiet gap that has already happened in the historical trail is a fixed
     * fact: re-running this later, even with a second walk's positions now appended after it, finds
     * the same gap in the same place. What follows the pause is [splitIntoExcursions]'s concern,
     * not this function's.
     *
     * [trailEndTimeMillis] is what a trailing position (one with nothing recorded after it yet)
     * measures its own "quiet gap" against, instead of treating that as an unconditional, infinite
     * pass: for a still-open excursion that bound is genuinely "now" (see [makeExcursions]), since a
     * single position moments after leaving (e.g. checking the app 50m from the front door, with no
     * second fix having streamed in yet) would otherwise look exactly like an infinitely long silence
     * purely from the absence of more data so far, not because the pet actually went quiet. For an
     * already-closed pair it's the real enter event's own time.
     */
    fun firstHomecomingPause(
      trail: List<TraccarPosition>,
      homes: List<HomeGeofence>,
      proximitySlackMeters: Double = HOME_PROXIMITY_SLACK_METERS,
      minimumQuietGapMillis: Long = QUIET_GAP_AFTER_HOME_PROXIMITY_MILLIS,
      trailEndTimeMillis: Long = Long.MAX_VALUE,
    ): Homecoming? {
      val sorted = trail.sortedBy(::fixTimeOf)
      val (index, homeId) =
        homecomingPauseIndex(sorted, 0, homes, proximitySlackMeters, minimumQuietGapMillis, trailEndTimeMillis) ?: return null
      return Homecoming(fixTimeOf(sorted[index]), homeId)
    }

    /**
     * [firstHomecomingPause] on an already-sorted trail, starting at [startIndex], returning the
     * pause's index (and its home's id) so [splitIntoExcursions] can pick up right after it.
     */
    private fun homecomingPauseIndex(
      sorted: List<TraccarPosition>,
      startIndex: Int,
      homes: List<HomeGeofence>,
      proximitySlackMeters: Double = HOME_PROXIMITY_SLACK_METERS,
      minimumQuietGapMillis: Long = QUIET_GAP_AFTER_HOME_PROXIMITY_MILLIS,
      trailEndTimeMillis: Long,
    ): Pair<Int, Int>? {
      for (index in startIndex until sorted.size) {
        val home = nearHome(sorted[index], homes, proximitySlackMeters) ?: continue
        val nextTime = if (index + 1 < sorted.size) fixTimeOf(sorted[index + 1]) else trailEndTimeMillis
        if (nextTime - fixTimeOf(sorted[index]) >= minimumQuietGapMillis) {
          return index to home.id
        }
      }
      return null
    }

    /** Never prefers a homecoming at or after a real enter event's own time, only a genuinely earlier one. */
    fun shouldPreferHomecoming(homecoming: Homecoming, fetchedEndTime: Long?): Boolean {
      if (fetchedEndTime == null) return true
      return homecoming.fixTime < fetchedEndTime
    }

    fun mergeLifetimeStats(cached: CachedLifetimeStats?, recentExcursions: List<Excursion>): Pair<LifetimeStats, CachedLifetimeStats> {
      val originalWatermark = cached?.cachedThroughEndTime
      var totalDistance = cached?.totalDistanceMeters ?: 0.0
      var totalDuration = cached?.totalDurationMillis ?: 0L
      var newWatermark = originalWatermark

      for (excursion in recentExcursions) {
        val endTime = excursion.endTime ?: continue
        if (originalWatermark != null && endTime <= originalWatermark) continue
        if (!excursion.isLikelyVehicleTrip) {
          totalDistance += excursion.distanceMeters ?: 0.0
          totalDuration += excursion.durationMillis ?: 0L
        }
        if (newWatermark == null || endTime > newWatermark) {
          newWatermark = endTime
        }
      }

      val newCache = CachedLifetimeStats(totalDistance, totalDuration, newWatermark)

      val ongoing = recentExcursions.firstOrNull { it.isOngoing }
      if (ongoing != null) {
        totalDistance += ongoing.distanceMeters ?: 0.0
        totalDuration += System.currentTimeMillis() - ongoing.startTime
      }

      return LifetimeStats(totalDistance, totalDuration) to newCache
    }
  }

  // MARK: - Pet sharing (see ADR-0018 on the iOS side)

  override suspend fun sharePet(petId: Int, email: String) {
    val users = client.fetchUsers()
    val user =
      users.firstOrNull { it.email.equals(email, ignoreCase = true) }
        ?: throw TraccarError.NotFound("No user with that email was found on this server.")
    client.linkDeviceToUser(petId, user.id)

    // Best-effort: also grant the new user access to every home already set up for this pet,
    // otherwise they'd see the pet but not its excursions until someone later edits a home.
    try {
      homeTraccarGeofences(petId).forEach { home -> client.linkGeofenceToUser(home.id, user.id) }
    } catch (e: TraccarError) {
      // Best-effort.
    }
  }
}

private suspend fun <T, R> List<T>.concurrentMap(maxConcurrent: Int, transform: suspend (T) -> R): List<R> =
  coroutineScope {
    val semaphore = Semaphore(maxConcurrent)
    map { element -> async { semaphore.withPermit { transform(element) } } }.awaitAll()
  }

data class Homecoming(val fixTime: Long, val homeId: Int)
