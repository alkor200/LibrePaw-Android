package org.librepaw.app.ui.find

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassRotationTest {
  @Test
  fun crossingNorthClockwise_turnsTwoDegrees_notBackwardsAlmostAFullTurn() {
    assertEquals(361f, shortestRotationTarget(current = 359f, target = 1f), 0.001f)
  }

  @Test
  fun crossingNorthCounterClockwise_turnsTwoDegreesBack() {
    assertEquals(-1f, shortestRotationTarget(current = 1f, target = 359f), 0.001f)
  }

  @Test
  fun afterSeveralTurns_staysContinuous() {
    // Already accumulated two full turns plus 10 degrees, target 20 degrees: just 10 further.
    assertEquals(740f, shortestRotationTarget(current = 730f, target = 20f), 0.001f)
  }

  @Test
  fun ordinaryChange_isUnaffected() {
    assertEquals(90f, shortestRotationTarget(current = 45f, target = 90f), 0.001f)
  }
}
