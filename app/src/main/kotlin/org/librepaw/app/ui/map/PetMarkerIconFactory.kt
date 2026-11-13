package org.librepaw.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.ui.common.PetStatusPresentation

/**
 * Renders a pet marker icon as a bitmap, since MapLibre's symbol layer needs one, not a live
 * Compose view (the same constraint the iOS map hits, rasterizing `PetAnnotationView` to a
 * `UIImage`): the pet's own photo center-cropped into a circle when one is set, a paw silhouette
 * otherwise, both with a status-tinted ring.
 */
object PetMarkerIconFactory {
  /** Also how much room a marker needs around its position, see PetMapScreen's camera fitting. */
  const val SIZE_PX = 128
  private const val STROKE_WIDTH_PX = 8f

  fun icon(pet: Pet, photo: Bitmap?, fallbackBackgroundColor: Int, pawColor: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = SIZE_PX / 2f
    val circleRadius = center - STROKE_WIDTH_PX / 2

    if (photo != null) {
      drawCenterCroppedPhoto(canvas, photo, center, circleRadius)
    } else {
      val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fallbackBackgroundColor }
      canvas.drawCircle(center, center, circleRadius, fillPaint)
      drawPaw(canvas, center, pawColor)
    }

    val strokePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH_PX
        color = PetStatusPresentation.colorArgb(pet.status)
      }
    canvas.drawCircle(center, center, circleRadius, strokePaint)

    return bitmap
  }

  private fun drawCenterCroppedPhoto(canvas: Canvas, photo: Bitmap, center: Float, radius: Float) {
    val cropSize = minOf(photo.width, photo.height)
    val left = (photo.width - cropSize) / 2
    val top = (photo.height - cropSize) / 2
    val srcRect = Rect(left, top, left + cropSize, top + cropSize)
    val dstRect = RectF(center - radius, center - radius, center + radius, center + radius)

    canvas.save()
    canvas.clipPath(Path().apply { addCircle(center, center, radius, Path.Direction.CW) })
    canvas.drawBitmap(photo, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
  }

  /** A simple geometric paw (one pad + four toes), matching [androidx.compose.material.icons.filled.Pets]
   * in spirit without needing to rasterize a Compose [androidx.compose.ui.graphics.vector.ImageVector]
   * outside a composition. */
  private fun drawPaw(canvas: Canvas, center: Float, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    val padWidth = center * 0.62f
    val padHeight = center * 0.46f
    val padCenterY = center + center * 0.22f
    canvas.drawOval(
      RectF(center - padWidth / 2, padCenterY - padHeight / 2, center + padWidth / 2, padCenterY + padHeight / 2),
      paint,
    )

    val toeRadius = center * 0.17f
    val toeDistance = center * 0.62f
    val toeCenterY = center - center * 0.32f
    listOf(-0.62f, -0.22f, 0.22f, 0.62f).forEach { fraction ->
      canvas.drawCircle(center + fraction * toeDistance, toeCenterY - kotlin.math.abs(fraction) * center * 0.12f, toeRadius, paint)
    }
  }
}
