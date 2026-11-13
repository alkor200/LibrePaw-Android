package org.librepaw.app.ui.find

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.data.pets.Coordinate
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.common.LocationHeadingService
import org.librepaw.app.ui.petdetail.formattedDistance

/**
 * An AirTag-style "Find" screen: a compass arrow that rotates to always point toward the pet's
 * last known position, with the distance below it. Falls back to pointing relative to magnetic
 * north on devices with no rotation-vector sensor (porting iOS's FindPetView, see ADR-0010).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindPetScreen(
  petId: Int,
  petName: String,
  petRepository: PetRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: FindPetViewModel = viewModel(key = "find-pet-$petId") { FindPetViewModel(petId, petRepository) },
) {
  val context = LocalContext.current
  val locationService = remember { LocationHeadingService(context.applicationContext) }
  var hasPermission by remember { mutableStateOf(locationService.hasLocationPermission()) }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
      hasPermission = grants.values.any { it } || locationService.hasLocationPermission()
    }

  // Both, not just fine: from Android 12 on, the documented way to let the user pick precise or
  // approximate location is requesting the two together (fine alone may be ignored outright).
  // Approximate is enough to point roughly the right way, it's the user's call.
  LaunchedEffect(Unit) {
    if (!hasPermission) {
      permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
  }

  // Only while the screen is actually visible, not while the app sits in the background.
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner, viewModel) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.pollPositions() }
  }

  DisposableEffect(hasPermission) {
    if (hasPermission) locationService.startUpdating()
    onDispose { locationService.stopUpdating() }
  }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val coordinate by locationService.coordinate.collectAsStateWithLifecycle()
  val headingDegrees by locationService.headingDegrees.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Find $petName") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
      val loadedPet = (uiState as? FindPetUiState.Loaded)?.pet
      when {
        !hasPermission -> DeniedContent(petName = petName, onOpenSettings = { openAppSettings(context) })
        uiState is FindPetUiState.Failed -> FailedContent((uiState as FindPetUiState.Failed).message, onRetry = viewModel::refresh)
        uiState is FindPetUiState.Loading -> LoadingContent()
        loadedPet?.lastPosition == null -> NoPositionContent(petName)
        coordinate == null -> FindingLocationContent()
        else ->
          CompassContent(
            pet = loadedPet,
            userCoordinate = coordinate!!,
            headingDegrees = headingDegrees,
            isHeadingAvailable = locationService.isHeadingAvailable,
          )
      }
    }
  }
}

@Composable
private fun CompassContent(pet: Pet, userCoordinate: Coordinate, headingDegrees: Double?, isHeadingAvailable: Boolean) {
  val position = pet.lastPosition!!
  val petCoordinate = Coordinate(position.latitude, position.longitude)
  val bearing = userCoordinate.initialBearingDegrees(petCoordinate)
  val heading = if (isHeadingAvailable) (headingDegrees ?: bearing) else 0.0
  val rotation = ((bearing - heading) % 360 + 360) % 360
  val animatedRotation by animateFloatAsState(targetValue = rotation.toFloat(), label = "compassRotation")
  val distanceMeters = userCoordinate.distanceMeters(petCoordinate)

  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier = Modifier.size(260.dp).padding(2.dp),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(color = Color.Gray.copy(alpha = 0.4f), style = Stroke(width = 4f))
      }
      Icon(
        Icons.Filled.Navigation,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp).rotate(animatedRotation),
      )
    }

    Column(
      modifier = Modifier.padding(top = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(formattedDistance(distanceMeters), fontSize = 40.sp, style = MaterialTheme.typography.headlineLarge)
      Text(
        "Last position ${relativeTime(position.recordedAt)}",
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (!isHeadingAvailable) {
      Row(
        modifier = Modifier.padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
          "Compass unavailable, pointing from magnetic north instead.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun LoadingContent() {
  Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    CircularProgressIndicator()
    Text("Loading pet…", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun FindingLocationContent() {
  Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    CircularProgressIndicator()
    Text("Finding your location…", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun NoPositionContent(petName: String) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(Icons.Filled.LocationOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("No Known Position", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
    Text(
      "$petName's tracker hasn't reported a position yet.",
      modifier = Modifier.padding(top = 8.dp),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun DeniedContent(petName: String, onOpenSettings: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(Icons.Filled.LocationOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Location Access Needed", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
    Text(
      "LibrePaw needs your location to point you toward $petName.",
      modifier = Modifier.padding(top = 8.dp),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 24.dp)) { Text("Open Settings") }
  }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text("Couldn't Load Pet", style = MaterialTheme.typography.titleLarge)
    Text(message, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp)) { Text("Try Again") }
  }
}

private fun relativeTime(epochMillis: Long): String =
  DateUtils.getRelativeTimeSpanString(epochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun openAppSettings(context: Context) {
  val intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
  context.startActivity(intent)
}
