package org.librepaw.app.ui.homes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.librepaw.app.data.pets.Coordinate

class CirclePolygonTest {
  @Test
  fun circlePolygon_everyVertexIsApproximatelyRadiusAway() {
    val center = Coordinate(51.34, 12.37)
    val radiusMeters = 50.0
    val polygon = circlePolygon(center, radiusMeters, segments = 32)

    val ring = polygon.coordinates().single()
    ring.forEach { point ->
      val vertex = Coordinate(point.latitude(), point.longitude())
      assertEquals(radiusMeters, center.distanceMeters(vertex), 0.5)
    }
  }

  @Test
  fun circlePolygon_isClosedRing() {
    val polygon = circlePolygon(Coordinate(0.0, 0.0), 10.0, segments = 8)
    val ring = polygon.coordinates().single()
    assertEquals(ring.first().latitude(), ring.last().latitude(), 1e-9)
    assertEquals(ring.first().longitude(), ring.last().longitude(), 1e-9)
  }
}
