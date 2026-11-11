package org.librepaw.app.ui.editpet

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CircularCropTest {
  @Test
  fun clampCropOffset_atScale1_squareImage_allowsNoPan() {
    // A square image exactly filling the crop circle has no overflow to pan into.
    val result = clampCropOffset(bitmapWidth = 100, bitmapHeight = 100, cropDiameterPx = 220f, scale = 1f, rawOffset = Offset(50f, 50f))
    assertEquals(Offset.Zero, result)
  }

  @Test
  fun clampCropOffset_widerImage_allowsHorizontalPanOnly() {
    // A 2:1 landscape image at scale 1: fillScale makes height match the circle, width overflows.
    val result =
      clampCropOffset(bitmapWidth = 200, bitmapHeight = 100, cropDiameterPx = 220f, scale = 1f, rawOffset = Offset(1000f, 1000f))
    // Overflow width = 220*2 - 220 = 220, so max pan is 110 either way; vertical pan stays 0.
    assertEquals(110f, result.x, 0.01f)
    assertEquals(0f, result.y, 0.01f)
  }

  @Test
  fun clampCropOffset_higherScale_allowsMorePan() {
    val atScale1 = clampCropOffset(bitmapWidth = 100, bitmapHeight = 100, cropDiameterPx = 220f, scale = 1f, rawOffset = Offset(1000f, 0f))
    val atScale2 = clampCropOffset(bitmapWidth = 100, bitmapHeight = 100, cropDiameterPx = 220f, scale = 2f, rawOffset = Offset(1000f, 0f))
    assert(atScale2.x > atScale1.x)
  }
}
