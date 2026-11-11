package org.librepaw.app.ui.petdetail

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.data.pets.AccountPermissions
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.common.PetAvatar
import org.librepaw.app.ui.common.PetStatusPresentation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetDetailScreen(
  petId: Int,
  petRepository: PetRepository,
  onBack: () -> Unit,
  onEditClick: (Pet) -> Unit,
  onHomesClick: (Pet) -> Unit,
  modifier: Modifier = Modifier,
  /** Bumped by the caller after a successful edit, so a fresh [PetDetailViewModel] re-fetches instead of reusing stale cached state. */
  dataVersion: Int = 0,
  viewModel: PetDetailViewModel =
    viewModel(key = "pet-detail-$petId-$dataVersion") { PetDetailViewModel(petId, petRepository) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val title = (uiState as? PetDetailUiState.Loaded)?.pet?.name ?: "Pet"

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
          ),
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
      when (val state = uiState) {
        PetDetailUiState.Loading ->
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            CircularProgressIndicator()
            Text(
              "Loading pet…",
              modifier = Modifier.padding(top = 16.dp),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        is PetDetailUiState.Failed ->
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
            Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 24.dp)) { Text("Try Again") }
          }
        is PetDetailUiState.Loaded ->
          PetDetailContent(
            pet = state.pet,
            permissions = state.permissions,
            petRepository = petRepository,
            onEditClick = onEditClick,
            onHomesClick = onHomesClick,
          )
      }
    }
  }
}

@Composable
private fun PetDetailContent(
  pet: Pet,
  permissions: AccountPermissions?,
  petRepository: PetRepository,
  onEditClick: (Pet) -> Unit,
  onHomesClick: (Pet) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box {
      PetAvatar(pet = pet, petRepository = petRepository, size = 96.dp, showBatteryBadge = false)
      if (permissions?.canManageDevice == true) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).clickable { onEditClick(pet) },
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
              Icons.Filled.Edit,
              contentDescription = "Edit",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }

    Text(
      pet.name,
      modifier = Modifier.padding(top = 16.dp),
      style = MaterialTheme.typography.headlineSmall,
    )
    Text(
      statusCaption(pet),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(
      modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      statTiles(pet).chunked(2).forEach { rowTiles ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          rowTiles.forEach { (label, value) -> StatTile(label = label, value = value, modifier = Modifier.weight(1f)) }
          if (rowTiles.size == 1) Box(modifier = Modifier.weight(1f))
        }
      }
    }

    Surface(
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(locationText(pet), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
      }
    }

    Surface(
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { onHomesClick(pet) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("${pet.name}'s Homes", modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium)
    }
  }
}

private fun statTiles(pet: Pet): List<Pair<String, String>> {
  val tiles = mutableListOf<Pair<String, String>>()
  val position = pet.lastPosition
  tiles += "Speed" to (position?.let { "%.1f km/h".format(it.speedKilometersPerHour) } ?: "—")
  tiles += "Last Update" to (pet.lastSeenAt?.let { relativeTime(it) } ?: "—")
  position?.batteryLevelPercent?.let { tiles += "Battery" to "${it.toInt()}%" }
  return tiles
}

private fun locationText(pet: Pet): String {
  val position = pet.lastPosition ?: return "No location yet."
  return position.address ?: "%.5f, %.5f".format(position.latitude, position.longitude)
}

private fun statusCaption(pet: Pet): String {
  val label = PetStatusPresentation.label(pet.status)
  if (pet.status == Pet.Status.ONLINE) return label
  val lastSeenAt = pet.lastSeenAt ?: return label
  return "$label · ${relativeTime(lastSeenAt)}"
}

private fun relativeTime(epochMillis: Long): String =
  DateUtils.getRelativeTimeSpanString(epochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
