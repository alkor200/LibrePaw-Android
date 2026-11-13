package org.librepaw.app.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.traccar.TraccarError

/**
 * A pet's circular avatar: its photo if one loads, else an initials placeholder, with a
 * status-tinted border. Shared between the Pet List grid and the Pet Detail header.
 */
@Composable
fun PetAvatar(
  pet: Pet,
  petRepository: PetRepository,
  size: Dp,
  modifier: Modifier = Modifier,
  showBatteryBadge: Boolean = true,
) {
  val photo by
    produceState<Bitmap?>(initialValue = null, key1 = pet.photoUrl) {
      value =
        try {
          petRepository.fetchPhoto(pet)?.let { bytes -> ImageDecoding.decode(bytes, maxDimension = AVATAR_MAX_DIMENSION_PX) }
        } catch (e: TraccarError) {
          null
        }
    }

  Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
    Surface(
      shape = CircleShape,
      modifier = Modifier.fillMaxSize().border(2.dp, Color(PetStatusPresentation.colorArgb(pet.status)), CircleShape),
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
      val bitmap = photo
      if (bitmap != null) {
        Image(
          painter = BitmapPainter(bitmap.asImageBitmap()),
          contentDescription = pet.name,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            pet.name.take(1).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    val batteryPercent = pet.lastPosition?.batteryLevelPercent
    if (showBatteryBadge && batteryPercent != null) {
      Surface(
        shape = CircleShape,
        color = if (batteryPercent < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        modifier = Modifier.align(Alignment.BottomEnd),
      ) {
        Text(
          "${batteryPercent.toInt()}%",
          modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondary,
        )
      }
    }
  }
}

/** The largest avatar is well under this, and the server's own photos are 512px crops anyway. */
private const val AVATAR_MAX_DIMENSION_PX = 512
