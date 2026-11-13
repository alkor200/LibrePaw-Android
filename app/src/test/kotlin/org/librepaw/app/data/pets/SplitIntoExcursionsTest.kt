package org.librepaw.app.data.pets

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.librepaw.app.data.traccar.TraccarEvent
import org.librepaw.app.data.traccar.TraccarPosition

/**
 * Shaped like Phoebe's real 2026-11-13 trail (see ADR-0029 on the iOS side): one exit at 08:39 and
 * one enter at 16:12 for two separate walks, the tracker having fallen asleep 42m from the center
 * of a 40m home in between and woken up 126m away. Only the times and distances from home are
 * real, the home itself is an arbitrary made-up location, never the real one. Mirrors the iOS
 * app's own SplitIntoExcursionsTests case for case.
 */
class SplitIntoExcursionsTest {
  private val homeCenter = Coordinate(51.3091, 12.4178)
  private val home = HomeGeofence(id = 24, name = "Home", center = homeCenter, radiusMeters = 40.0)
  private val day = 1_794_556_800_000L // 2026-11-13 00:00 UTC

  private fun time(hour: Int, minute: Int, second: Int = 0): Long = day + (hour * 3_600L + minute * 60L + second) * 1000L

  private fun iso(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMillis))

  /** A position [meters] due north of the home's center. */
  private fun position(id: Long, fixTime: Long, meters: Double) =
    TraccarPosition(
      id = id,
      deviceId = 1,
      fixTime = iso(fixTime),
      latitude = homeCenter.latitude + meters / 111_320.0,
      longitude = homeCenter.longitude,
      speed = 0.0,
    )

  private fun event(id: Long, type: String, eventTime: Long) =
    TraccarEvent(id = id, type = type, eventTime = iso(eventTime), deviceId = 1, geofenceId = 24)

  private val exit = event(281, TraccarEvent.EventType.GEOFENCE_EXIT, time(8, 39, 36))
  private val enter = event(305, TraccarEvent.EventType.GEOFENCE_ENTER, time(16, 12, 18))

  // Thinned out from the real ~30 second fixes, but never with a gap of five minutes or more near
  // home, which would read as the tracker falling asleep there.
  private val morningWalk =
    listOf(
      position(1803, time(8, 39, 36), 85.0),
      position(1804, time(8, 40, 6), 96.0),
      position(1806, time(8, 41, 6), 116.0),
      position(1813, time(8, 44, 38), 239.0),
      position(1833, time(8, 56, 42), 400.0),
      position(1853, time(9, 10, 15), 171.0),
      position(1863, time(9, 15, 47), 112.0),
      position(1866, time(9, 17, 17), 53.0),
      // Asleep from here, 2m short of the 40m boundary.
      position(1867, time(9, 17, 47), 42.0),
      position(1870, time(9, 17, 47), 42.0),
    )

  private val afternoonWalk =
    listOf(
      // Awake again, already outside, so no exit event.
      position(1871, time(15, 12, 0), 126.0),
      position(1873, time(15, 13, 0), 170.0),
      position(1903, time(15, 34, 7), 570.0),
      position(1953, time(16, 5, 16), 307.0),
      position(1963, time(16, 10, 48), 113.0),
      position(1965, time(16, 11, 48), 51.0),
      position(1966, time(16, 12, 18), 23.0),
    )

  private fun split(exit: TraccarEvent, enter: TraccarEvent?, trail: List<TraccarPosition>, trailEnd: Long) =
    TraccarPetRepository.splitIntoExcursions(exit, enter, trail, listOf(home), trailEnd)

  @Test
  fun closedPair_splitsIntoBothWalks() {
    val excursions = split(exit, enter, morningWalk + afternoonWalk, time(16, 12, 18))

    assertEquals(2, excursions.size)
    val (morning, afternoon) = excursions

    assertEquals(281L, morning.id)
    assertEquals(time(8, 39, 36), morning.startTime)
    assertEquals(time(9, 17, 47), morning.endTime)
    assertEquals(24, morning.endHomeId)

    assertEquals(-1871L, afternoon.id)
    assertEquals(time(15, 12, 0), afternoon.startTime)
    assertEquals(time(16, 12, 18), afternoon.endTime)
    assertEquals(24, afternoon.startHomeId)
    assertEquals(24, afternoon.endHomeId)
    // Each walk's distance only covers its own positions: 85 to 400 and back to 42m is 673m, 126 to
    // 570 and back to 23m is 991m.
    assertEquals(673.0, morning.distanceMeters!!, 5.0)
    assertEquals(991.0, afternoon.distanceMeters!!, 5.0)
  }

  @Test
  fun openPair_midSecondWalk_morningClosedAndAfternoonOngoing() {
    val now = time(15, 34, 30)
    val soFar = morningWalk + afternoonWalk.filter { it.fixTime <= iso(now) }

    val excursions = split(exit, null, soFar, now)

    assertEquals(2, excursions.size)
    assertEquals(time(9, 17, 47), excursions[0].endTime)
    assertEquals(-1871L, excursions[1].id)
    assertTrue(excursions[1].isOngoing)
  }

  @Test
  fun openPair_betweenWalks_onlyTheMorningWalkExists_closedAtThePause() {
    val excursions = split(exit, null, morningWalk, time(10, 0))

    assertEquals(listOf(281L), excursions.map { it.id })
    assertEquals(time(9, 17, 47), excursions.first().endTime)
  }

  @Test
  fun deadSpotByTheFrontDoor_rightAfterLeaving_doesNotSplitOffAStub() {
    val exit = event(1, TraccarEvent.EventType.GEOFENCE_EXIT, time(8, 0))
    val enter = event(2, TraccarEvent.EventType.GEOFENCE_ENTER, time(9, 0))
    val trail =
      listOf(
        position(10, time(8, 0, 0), 50.0),
        // Six minutes without a fix, still by the door.
        position(11, time(8, 0, 30), 60.0),
        position(12, time(8, 6, 30), 150.0),
        position(13, time(8, 30), 600.0),
        position(14, time(9, 0), 20.0),
      )

    val excursions = split(exit, enter, trail, time(9, 0))

    // One excursion, still the exit's own, starting where the walk actually got going after the
    // dead spot.
    assertEquals(listOf(1L), excursions.map { it.id })
    assertEquals(time(8, 6, 30), excursions.first().startTime)
    assertEquals(time(9, 0), excursions.first().endTime)
  }

  @Test
  fun jitterExitAtTheFence_followedByANightAtHome_doesNotStretchIntoTheNextDaysWalk() {
    // Phoebe's real 2026-11-10/11 trail: "left" at 48m from a 40m home, slept there, briefly
    // reported 86 to 123m in the evening (still within the slack), slept again, and only really
    // left at 12:59 the next day.
    val nextDay = day + 86_400_000L
    fun nextDayTime(hour: Int, minute: Int, second: Int) = nextDay + (hour * 3_600L + minute * 60L + second) * 1000L
    val exit = event(197, TraccarEvent.EventType.GEOFENCE_EXIT, time(14, 17))
    val enter = event(209, TraccarEvent.EventType.GEOFENCE_ENTER, nextDayTime(13, 30, 45))
    val trail =
      listOf(
        position(1, time(14, 17), 48.0),
        position(2, time(22, 55, 35), 86.0),
        position(3, time(22, 56, 36), 123.0),
        position(4, nextDayTime(12, 59, 35), 120.0),
        position(41, nextDayTime(13, 0, 5), 160.0),
        position(5, nextDayTime(13, 5, 37), 413.0),
        position(6, nextDayTime(13, 13, 10), 599.0),
        position(7, nextDayTime(13, 28, 44), 125.0),
        position(8, nextDayTime(13, 30, 45), 14.0),
      )

    val excursions = split(exit, enter, trail, nextDayTime(13, 30, 45))

    assertEquals(1, excursions.size)
    assertEquals(197L, excursions[0].id)
    assertEquals(nextDayTime(12, 59, 35), excursions[0].startTime)
    assertEquals(nextDayTime(13, 30, 45), excursions[0].endTime)
  }

  @Test
  fun potteringAboutJustOutsideTheFence_afterComingHome_isNoNewWalk() {
    val garden = listOf(position(1900, time(12, 0), 60.0), position(1901, time(12, 0, 30), 75.0))

    val excursions = split(exit, null, morningWalk + garden, time(13, 0))

    assertEquals(listOf(281L), excursions.map { it.id })
  }

  @Test
  fun ordinaryWalk_endingWithARealEnterEvent_staysOneExcursion() {
    val excursions = split(event(1, TraccarEvent.EventType.GEOFENCE_EXIT, time(15, 12)), enter, afternoonWalk, time(16, 12, 18))

    assertEquals(listOf(1L), excursions.map { it.id })
    assertEquals(time(16, 12, 18), excursions.first().endTime)
  }
}
