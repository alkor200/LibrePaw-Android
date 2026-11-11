package org.librepaw.app.data.traccar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TraccarGeofenceAreaTest {
  @Test
  fun circleWkt_isLatitudeFirst() {
    // Not standard WKT (lon, lat) order: Traccar's own circle format is latitude first (ADR-0014).
    assertEquals("CIRCLE (51.34 12.37, 50.0)", TraccarGeofenceArea.circleWkt(51.34, 12.37, 50.0))
  }

  @Test
  fun parseCircle_roundTripsWithCircleWkt() {
    val wkt = TraccarGeofenceArea.circleWkt(centerLatitude = 51.34, centerLongitude = 12.37, radiusMeters = 75.0)
    val parsed = TraccarGeofenceArea.parseCircle(wkt)

    assertEquals(51.34, parsed?.latitude ?: 0.0, 0.0001)
    assertEquals(12.37, parsed?.longitude ?: 0.0, 0.0001)
    assertEquals(75.0, parsed?.radiusMeters ?: 0.0, 0.0001)
  }

  @Test
  fun parseCircle_invalidWkt_returnsNull() {
    assertNull(TraccarGeofenceArea.parseCircle("POLYGON ((0 0, 1 1))"))
  }
}
