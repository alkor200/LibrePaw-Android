package org.librepaw.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.ui.common.PetStatusPresentation

/**
 * Renders a pet marker icon as a bitmap, since MapLibre's symbol layer needs one, not a live
 * Compose view (the same constraint the iOS map hits, rasterizing `PetAnnotationView` to a
 * `UIImage`). Mirrors [org.librepaw.app.ui.petlist.PetListScreen]'s initials-avatar look.
 */
object PetMarkerIconFactory {
  private const val SIZE_PX = 128
  private const val STROKE_WIDTH_PX = 8f

  fun icon(pet: Pet): Bitmap {
    val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = SIZE_PX / 2f
    val circleRadius = radius - STROKE_WIDTH_PX / 2

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE6, 0xE1, 0xF0) }
    canvas.drawCircle(radius, radius, circleRadius, fillPaint)

    val strokePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH_PX
        color = PetStatusPresentation.colorArgb(pet.status)
      }
    canvas.drawCircle(radius, radius, circleRadius, strokePaint)

    val textPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x46, 0x3C, 0x50)
        textAlign = Paint.Align.CENTER
        textSize = SIZE_PX * 0.45f
      }
    val letter = pet.name.take(1).uppercase()
    val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(letter, radius, textY, textPaint)

    return bitmap
  }
}
