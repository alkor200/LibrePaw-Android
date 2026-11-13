package org.librepaw.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDecodingTest {
  @Test
  fun sampleSize_smallEnoughImage_decodesAtFullSize() {
    assertEquals(1, ImageDecoding.sampleSize(width = 512, height = 300, maxDimension = 512))
  }

  @Test
  fun sampleSize_largePhoto_halvesUntilBothSidesFit() {
    // A 48 MP phone photo, 8000x6000, down to 1000x750 for a 1024 limit.
    assertEquals(8, ImageDecoding.sampleSize(width = 8000, height = 6000, maxDimension = 1024))
  }

  @Test
  fun sampleSize_decompressionBomb_isStillBounded() {
    // A tiny JPEG claiming 65000x65000 pixels would be ~17 GB decoded at full size.
    val sampleSize = ImageDecoding.sampleSize(width = 65_000, height = 65_000, maxDimension = 512)!!
    assertTrue(65_000 / sampleSize <= 512)
  }

  @Test
  fun sampleSize_undecodableBounds_isNull() {
    assertNull(ImageDecoding.sampleSize(width = -1, height = -1, maxDimension = 512))
    assertNull(ImageDecoding.sampleSize(width = 0, height = 100, maxDimension = 512))
  }
}
