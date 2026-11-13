package org.librepaw.app.ui.editpet

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.common.ImageDecoding
import org.librepaw.app.ui.home.BottomBarClearance

private val CROP_DIAMETER = 220.dp

/** Plenty for a 512px crop output (see renderCircularCropJpeg) even when zoomed in. */
private const val PICKED_PHOTO_MAX_DIMENSION_PX = 2048

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPetScreen(
  petId: Int,
  petRepository: PetRepository,
  onSaved: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: EditPetViewModel = viewModel(key = "edit-pet-$petId") { EditPetViewModel(petId, petRepository) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val density = LocalDensity.current
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val cropDiameterPx = with(density) { CROP_DIAMETER.toPx() }

  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  LaunchedEffect(viewModel.selectedImage) {
    scale = 1f
    offset = Offset.Zero
  }

  val pickPhotoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      coroutineScope.launch {
        ImageDecoding.decode(context.contentResolver, uri, PICKED_PHOTO_MAX_DIMENSION_PX)?.let(viewModel::selectImage)
      }
    }

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Edit Pet") },
        navigationIcon = {
          IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel") }
        },
        actions = {
          val canSave = viewModel.hasChanges() && !viewModel.isSaving && viewModel.name.isNotBlank()
          TextButton(
            enabled = canSave,
            onClick = {
              val image = viewModel.selectedImage
              val (cropScale, cropOffset) = scale to offset
              viewModel.save(
                renderCroppedJpeg = {
                  image?.let { withContext(Dispatchers.Default) { renderCircularCropJpeg(it, cropScale, cropOffset, cropDiameterPx) } }
                },
                onSuccess = onSaved,
              )
            },
          ) {
            Text("Save")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
      when (val state = uiState) {
        EditPetUiState.Loading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is EditPetUiState.Failed ->
          Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text("Couldn't Load Pet", style = MaterialTheme.typography.titleLarge)
            Text(
              state.message,
              modifier = Modifier.padding(top = 8.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        is EditPetUiState.Loaded ->
          Column(
            modifier =
              Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = BottomBarClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            val displayedBitmap = viewModel.selectedImage ?: state.currentPhoto
            if (displayedBitmap != null) {
              CropCircle(
                bitmap = displayedBitmap,
                scale = scale,
                offset = offset,
                cropDiameter = CROP_DIAMETER,
                interactive = viewModel.selectedImage != null,
                onScaleOffsetChange = { newScale, newOffset -> scale = newScale; offset = newOffset },
              )
            }
            Button(
              onClick = {
                pickPhotoLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              modifier = Modifier.padding(top = 16.dp),
            ) {
              Text(if (displayedBitmap != null) "Choose a Different Photo" else "Choose a Photo")
            }

            OutlinedTextField(
              value = viewModel.name,
              onValueChange = viewModel::updateName,
              label = { Text("Name") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
              modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )

            viewModel.errorMessage?.let { message ->
              Text(
                message,
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
              )
            }

            if (viewModel.isSaving) {
              CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }
          }
      }
    }
  }
}

@Composable
private fun CropCircle(
  bitmap: Bitmap,
  scale: Float,
  offset: Offset,
  cropDiameter: Dp,
  interactive: Boolean,
  onScaleOffsetChange: (Float, Offset) -> Unit,
) {
  val density = LocalDensity.current
  val cropDiameterPx = with(density) { cropDiameter.toPx() }
  val fillScale = max(cropDiameterPx / bitmap.width, cropDiameterPx / bitmap.height)
  val displayWidthPx = bitmap.width * fillScale * scale
  val displayHeightPx = bitmap.height * fillScale * scale

  Box(
    modifier =
      Modifier.size(cropDiameter).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).let {
        if (interactive) {
          it.pointerInput(bitmap) {
            detectTransformGestures { _, pan, zoom, _ ->
              val newScale = (scale * zoom).coerceAtLeast(1f)
              val newOffset =
                clampCropOffset(bitmap.width, bitmap.height, cropDiameterPx, newScale, offset + pan)
              onScaleOffsetChange(newScale, newOffset)
            }
          }
        } else {
          it
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = null,
      // requiredSize, not size: the image is deliberately larger than the circle on one side (it
      // fills it, see fillScale), and size() would be clamped to the circle's own constraints,
      // fitting a non-square photo inside it instead, unlike the crop renderCircularCropJpeg uploads.
      modifier =
        Modifier.requiredSize(with(density) { displayWidthPx.toDp() }, with(density) { displayHeightPx.toDp() })
          .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) },
    )
  }
}
