package org.librepaw.app.ui.editpet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import java.io.ByteArrayOutputStream
import kotlin.math.max

private const val OUTPUT_DIAMETER_PX = 512
private const val MAX_JPEG_BYTES = 480_000

/**
 * How far the pan gesture may move the image within the crop circle at the given pinch-zoom
 * `scale` (>= 1) before it would reveal empty space beyond the image's edge.
 */
fun clampCropOffset(bitmapWidth: Int, bitmapHeight: Int, cropDiameterPx: Float, scale: Float, rawOffset: Offset): Offset {
  val fillScale = max(cropDiameterPx / bitmapWidth, cropDiameterPx / bitmapHeight)
  val scaledWidth = bitmapWidth * fillScale * scale
  val scaledHeight = bitmapHeight * fillScale * scale
  val maxX = max(0f, (scaledWidth - cropDiameterPx) / 2f)
  val maxY = max(0f, (scaledHeight - cropDiameterPx) / 2f)
  return Offset(rawOffset.x.coerceIn(-maxX, maxX), rawOffset.y.coerceIn(-maxY, maxY))
}

/**
 * Renders the live crop (the same pinch-zoom `scale` and pan `offset` the crop circle is
 * currently showing) into a 512x512 circular JPEG, compressed to fit under Traccar's upload size
 * cap (its real limit is 500KB; 480KB leaves a safety margin).
 */
fun renderCircularCropJpeg(source: Bitmap, scale: Float, offset: Offset, cropDiameterPx: Float): ByteArray {
  val outputDiameter = OUTPUT_DIAMETER_PX.toFloat()
  val renderScale = outputDiameter / cropDiameterPx
  val fillScale = max(outputDiameter / source.width, outputDiameter / source.height)
  val scaledWidth = source.width * fillScale * scale
  val scaledHeight = source.height * fillScale * scale
  val originX = outputDiameter / 2f - scaledWidth / 2f + offset.x * renderScale
  val originY = outputDiameter / 2f - scaledHeight / 2f + offset.y * renderScale

  val output = Bitmap.createBitmap(OUTPUT_DIAMETER_PX, OUTPUT_DIAMETER_PX, Bitmap.Config.ARGB_8888)
  Canvas(output).apply {
    clipPath(Path().apply { addCircle(outputDiameter / 2f, outputDiameter / 2f, outputDiameter / 2f, Path.Direction.CW) })
    drawBitmap(
      source,
      null,
      RectF(originX, originY, originX + scaledWidth, originY + scaledHeight),
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
  }

  var quality = 85
  var bytes = compress(output, quality)
  while (bytes.size > MAX_JPEG_BYTES && quality > 30) {
    quality -= 10
    bytes = compress(output, quality)
  }
  return bytes
}

private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
  val stream = ByteArrayOutputStream()
  bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
  return stream.toByteArray()
}
