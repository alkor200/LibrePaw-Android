package org.librepaw.app.ui.common

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes images downsampled to at most `maxDimension` pixels per side, off the main thread.
 *
 * Plain `BitmapFactory.decode*` allocates the full resolution up front: a 48 MP photo from the
 * gallery is ~190 MB of ARGB, an out-of-memory crash on most phones, and a pet photo comes from
 * the server, where anyone with write access to a shared device can upload a small JPEG with
 * enormous pixel dimensions that would crash every viewer's app the same way. Reading the bounds
 * first and decoding with an `inSampleSize` keeps every decode bounded regardless of the input.
 */
object ImageDecoding {
  /** `null` if [bytes] isn't a decodable image. */
  suspend fun decode(bytes: ByteArray, maxDimension: Int): Bitmap? =
    withContext(Dispatchers.Default) {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      val sampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension) ?: return@withContext null
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

  /**
   * A picked photo, also rotated/flipped upright per its EXIF orientation: camera photos are
   * usually stored sideways with an orientation tag, which `BitmapFactory` ignores, so a portrait
   * shot would otherwise land in the cropper lying on its side. `null` if it can't be read.
   */
  suspend fun decode(contentResolver: ContentResolver, uri: Uri, maxDimension: Int): Bitmap? =
    withContext(Dispatchers.IO) {
      try {
        fun <T> read(block: (InputStream) -> T): T? = contentResolver.openInputStream(uri)?.use(block)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read { BitmapFactory.decodeStream(it, null, bounds) }
        val sampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension) ?: return@withContext null
        val bitmap =
          read { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize }) }
            ?: return@withContext null
        val orientation =
          read { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
        applyOrientation(bitmap, orientation)
      } catch (e: IOException) {
        null
      } catch (e: SecurityException) {
        // The picker's grant for this uri can be revoked before we get to read it.
        null
      }
    }

  /**
   * The smallest power-of-two `inSampleSize` that brings both sides to at most [maxDimension]
   * (`BitmapFactory` rounds anything else down to a power of two anyway). `null` for bounds that
   * aren't a decodable image at all.
   */
  fun sampleSize(width: Int, height: Int, maxDimension: Int): Int? {
    if (width <= 0 || height <= 0) return null
    var sampleSize = 1
    while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
      sampleSize *= 2
    }
    return sampleSize
  }

  private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { postRotate(90f); postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { postRotate(270f); postScale(-1f, 1f) }
      else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }
}
