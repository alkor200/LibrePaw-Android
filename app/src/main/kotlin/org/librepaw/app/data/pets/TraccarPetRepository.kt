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
      freshExcursions =
        pairs
          .concurrentMap(maxConcurrent = 20) { pair -> makeExcursion(petId, pair.first, pair.second, homes) }
          .filter { !isDegenerate(it) }
    }

    val (all, newCache) = mergeExcursions(cached, freshExcursions, liveFrom)
    excursionCacheStore.save(newCache, petId, serverHost)

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

    val recentExcursions = fetchExcursions(petId, cached?.cachedThroughEndTime ?: 0L, System.currentTimeMillis())

    val (stats, newCache) = mergeLifetimeStats(cached, recentExcursions)
    lifetimeStatsStore.save(newCache, petId, serverHost)
    return stats
  }

  override suspend fun resetLifetimeStats(petId: Int) {
    lifetimeStatsStore.clear(petId, client.serverUrl.host)
  }

  private suspend fun makeExcursion(petId: Int, exit: TraccarEvent, enter: TraccarEvent?, homes: List<HomeGeofence>): Excursion {
    val exitTime = TraccarDates.parse(exit.eventTime) ?: 0L
    val fetchedEndTime = enter?.eventTime?.let(TraccarDates::parse)
    // Also the bound a trailing position's "quiet gap" is measured against when nothing newer has
    // been recorded yet (see firstHomecomingPause): for a still-open excursion this is genuinely
    // "now", not an unconditional pass, since a single position moments after leaving (e.g. checking
    // the app 50m from the front door) would otherwise look exactly like an infinitely long silence
    // purely because a second position hasn't streamed in yet, not because the pet actually stopped.
    val trailEndTime = fetchedEndTime ?: System.currentTimeMillis()
    // Not caught: a failed fetch here must fail the whole call rather than produce an excursion
    // with no route and no distance, a closed one of those would be cached for good and folded
    // into lifetime stats as zero meters, never recomputed. Failing instead just means the next
    // load tries again.
    val fullTrail = client.fetchPositionHistory(petId, exitTime, trailEndTime)

    var endTime = fetchedEndTime
    var endHomeId = enter?.geofenceId
    var trail = fullTrail

    // Only required while the excursion is still open (no real enter event exists yet): the whole
    // fetched trail necessarily belongs to this one still-ongoing excursion (Traccar hasn't closed
    // it), so checking that the pet stays near home for the rest of what's known so far safely tells
    // a real early return apart from a brief mid-walk signal gap, without any risk of a later,
    // separately-reopened excursion's data bleeding in (see the already-closed branch below). Once a
    // real enter event exists, the original, simpler check applies: that case is already bounded by
    // two real Traccar events, confirmed live against a real tracker's multi-hour buffering delay.
    val homecoming =
      firstHomecomingPause(
        fullTrail,
        homes,
        requireStaysHomeAfterward = fetchedEndTime == null,
        trailEndTimeMillis = trailEndTime,
      )
    if (homecoming != null && shouldPreferHomecoming(homecoming, fetchedEndTime)) {
      endTime = homecoming.fixTime
      endHomeId = homecoming.homeId
      trail = fullTrail.filter { (TraccarDates.parse(it.fixTime) ?: 0L) <= homecoming.fixTime }
    }

    val coordinates = trail.sortedBy { TraccarDates.parse(it.fixTime) ?: 0L }.map { Coordinate(it.latitude, it.longitude) }
    val distance =
      if (coordinates.size > 1) coordinates.zipWithNext().sumOf { (a, b) -> a.distanceMeters(b) } else null

    return Excursion(
      id = exit.id,
      startTime = exitTime,
      endTime = endTime,
      startCoordinate = coordinates.firstOrNull(),
      endCoordinate = coordinates.lastOrNull(),
      distanceMeters = distance,
      startHomeId = exit.geofenceId,
      endHomeId = endHomeId,
    )
  }

  companion object {
    /** Below this, an exit immediately followed by an enter is GPS/geofence boundary jitter, not a real trip. */
    const val MINIMUM_EXCURSION_DURATION_MILLIS = 60_000L

    /** Extra distance beyond a home's own configured radius that still counts as "close enough" for a homecoming pause. */
    const val HOME_PROXIMITY_SLACK_METERS = 100.0

    /** How long the trail has to go quiet right after a near-home position before trusting it as a real homecoming. */
    const val QUIET_GAP_AFTER_HOME_PROXIMITY_MILLIS = 5 * 60 * 1000L

    /**
     * Where to start a live fetch from, given what's already cached. Only narrows to the watermark
     * when the cache's own coverage already reaches back to (or before) `from`.
     */
    fun liveFetchStart(cached: CachedExcursions?, from: Long): Long {
      val coveredFrom = cached?.coveredFrom
      val watermark = cached?.cachedThroughEndTime
      if (coveredFrom != null && coveredFrom <= from && watermark != null) {
        return maxOf(from, watermark)
      }
      return from
    }

    fun mergeExcursions(cached: CachedExcursions?, freshExcursions: List<Excursion>, liveFrom: Long): Pair<List<Excursion>, CachedExcursions> {
      val byId = LinkedHashMap<Long, Excursion>()
      cached?.excursions?.forEach { byId[it.id] = it }
      var watermark = cached?.cachedThroughEndTime

      for (excursion in freshExcursions) {
        byId[excursion.id] = excursion
        val endTime = excursion.endTime
        if (endTime != null && (watermark == null || endTime > watermark)) {
          watermark = endTime
        }
      }

      val coveredFrom = listOfNotNull(cached?.coveredFrom, liveFrom).minOrNull()

      val closed = byId.values.filter { it.endTime != null }
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
     * Finds the earliest trail position that's both near a home (within its radius plus slack) and
     * followed by a long enough quiet gap, treating that as the excursion's true end even without a
     * real `geofenceEnter` event (a tracker's own firmware sleep mode can trigger a few meters short
     * of the boundary).
     *
     * [requireStaysHomeAfterward], set only for a still-open excursion (see [makeExcursion]), rejects
     * a candidate if the trail resumes with the pet clearly elsewhere: a quiet gap right after leaving
     * (e.g. a brief signal dead spot while still chatting by the front door) can otherwise look
     * identical to a real, early return, closing out what's actually about to become a much longer
     * walk — and since Traccar hasn't closed this excursion yet, the real enter event that eventually
     * arrives would have no exit left to pair with (the cached, too-early closure already consumed
     * it), silently losing the whole walk rather than just mis-timing it.
     *
     * [trailEndTimeMillis] is what a trailing position (one with nothing recorded after it yet)
     * measures its own "quiet gap" against, instead of treating that as an unconditional, infinite
     * pass: for a still-open excursion that bound is genuinely "now" (see [makeExcursion]), since a
     * single position moments after leaving (e.g. checking the app 50m from the front door, with no
     * second fix having streamed in yet) would otherwise look exactly like an infinitely long silence
     * purely from the absence of more data so far, not because the pet actually went quiet. For an
     * already-closed pair it's the real enter event's own time, matching the original, validated
     * tracker-fell-asleep scenario (confirmed live: nothing else was ever recorded before that event).
     */
    fun firstHomecomingPause(
      trail: List<TraccarPosition>,
      homes: List<HomeGeofence>,
      proximitySlackMeters: Double = HOME_PROXIMITY_SLACK_METERS,
      minimumQuietGapMillis: Long = QUIET_GAP_AFTER_HOME_PROXIMITY_MILLIS,
      requireStaysHomeAfterward: Boolean = false,
      trailEndTimeMillis: Long = Long.MAX_VALUE,
    ): Homecoming? {
      val sorted = trail.sortedBy { TraccarDates.parse(it.fixTime) ?: 0L }
      fun isNearAnyHome(position: TraccarPosition): Boolean {
        val coordinate = Coordinate(position.latitude, position.longitude)
        return homes.any { coordinate.distanceMeters(it.center) <= it.radiusMeters + proximitySlackMeters }
      }
      for (index in sorted.indices) {
        val position = sorted[index]
        val coordinate = Coordinate(position.latitude, position.longitude)
        val nearestHome =
          homes.firstOrNull { coordinate.distanceMeters(it.center) <= it.radiusMeters + proximitySlackMeters } ?: continue
        val positionTime = TraccarDates.parse(position.fixTime) ?: 0L
        val remainder = sorted.subList(index + 1, sorted.size)
        val nextTime = remainder.firstOrNull()?.let { TraccarDates.parse(it.fixTime) } ?: trailEndTimeMillis
        val gap = nextTime - positionTime
        if (gap >= minimumQuietGapMillis && (!requireStaysHomeAfterward || remainder.all(::isNearAnyHome))) {
          return Homecoming(positionTime, nearestHome.id)
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
