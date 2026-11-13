package org.librepaw.app.ui.petdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.R
import org.librepaw.app.data.pets.AccountPermissions
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.common.NavigationLauncher
import org.librepaw.app.ui.common.PetAvatar
import org.librepaw.app.ui.common.PetStatusPresentation
import org.librepaw.app.ui.common.asString
import org.librepaw.app.ui.common.rememberFormatting
import org.librepaw.app.ui.home.BottomBarClearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetDetailScreen(
  petId: Int,
  petRepository: PetRepository,
  onBack: () -> Unit,
  onEditClick: (Pet) -> Unit,
  onHomesClick: (Pet) -> Unit,
  onExcursionsClick: (Pet) -> Unit,
  onTrailClick: (Pet) -> Unit,
  onSharePetClick: (Pet) -> Unit,
  onViewOnMapClick: (Pet) -> Unit,
  onFindPetClick: (Pet) -> Unit,
  modifier: Modifier = Modifier,
  /** Bumped by the caller after a successful edit, so a fresh [PetDetailViewModel] re-fetches instead of reusing stale cached state. */
  dataVersion: Int = 0,
  viewModel: PetDetailViewModel =
    viewModel(key = "pet-detail-$petId-$dataVersion") { PetDetailViewModel(petId, petRepository) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val title = (uiState as? PetDetailUiState.Loaded)?.pet?.name ?: stringResource(R.string.pet_fallback_title)

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
        PetDetailUiState.Loading ->
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            CircularProgressIndicator()
            Text(
              stringResource(R.string.loading_pet),
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
            Text(stringResource(R.string.error_couldnt_load_pet), style = MaterialTheme.typography.titleLarge)
            Text(
              state.message.asString(),
              modifier = Modifier.padding(top = 8.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 24.dp)) { Text(stringResource(R.string.action_try_again)) }
          }
        is PetDetailUiState.Loaded ->
          PetDetailContent(
            pet = state.pet,
            permissions = state.permissions,
            petRepository = petRepository,
            viewModel = viewModel,
            onEditClick = onEditClick,
            onHomesClick = onHomesClick,
            onExcursionsClick = onExcursionsClick,
            onTrailClick = onTrailClick,
            onSharePetClick = onSharePetClick,
            onViewOnMapClick = onViewOnMapClick,
            onFindPetClick = onFindPetClick,
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
  viewModel: PetDetailViewModel,
  onEditClick: (Pet) -> Unit,
  onHomesClick: (Pet) -> Unit,
  onExcursionsClick: (Pet) -> Unit,
  onTrailClick: (Pet) -> Unit,
  onSharePetClick: (Pet) -> Unit,
  onViewOnMapClick: (Pet) -> Unit,
  onFindPetClick: (Pet) -> Unit,
) {
  val context = LocalContext.current
  Column(
    modifier =
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = BottomBarClearance),
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
              contentDescription = stringResource(R.string.action_edit),
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

    // Its own standalone, full-width button below the header rather than a row in the actions
    // list below, same as iOS's own findButton.
    if (pet.lastPosition != null) {
      Button(
        onClick = { onFindPetClick(pet) },
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
      ) {
        Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.pet_detail_find, pet.name), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
      }
    }

    Column(
      modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Same order as iOS's own actionsCard: Navigate/View on Map need a known position; Trail
      // and Excursions are both built from Traccar's reports endpoint, hidden when the account
      // has reports disabled; Sharing stays admin-only, granting another Traccar account access
      // to a device requires an administrator server-side.
      val position = pet.lastPosition
      if (position != null) {
        ActionRow(
          icon = Icons.Filled.Navigation,
          label = stringResource(R.string.pet_detail_navigate, pet.name),
          onClick = { NavigationLauncher.navigateTo(context, position.latitude, position.longitude, pet.name) },
        )
        ActionRow(icon = Icons.Filled.Map, label = stringResource(R.string.pet_detail_view_on_map), onClick = { onViewOnMapClick(pet) })
      }
      if (permissions?.canViewExcursions == true) {
        ActionRow(icon = Icons.Filled.Route, label = stringResource(R.string.pet_detail_trail), onClick = { onTrailClick(pet) })
        ActionRow(icon = Icons.Filled.Pets, label = stringResource(R.string.pet_detail_excursions), onClick = { onExcursionsClick(pet) })
      }
      ActionRow(icon = Icons.Filled.Home, label = stringResource(R.string.pet_detail_homes, pet.name), onClick = { onHomesClick(pet) })
      if (permissions?.isAdministrator == true) {
        ActionRow(icon = Icons.Filled.PersonAdd, label = stringResource(R.string.pet_detail_share, pet.name), onClick = { onSharePetClick(pet) })
      }
    }

    Column(
      modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      statTiles(pet, viewModel).chunked(2).forEach { rowTiles ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          rowTiles.forEach { tile ->
            StatTile(
              icon = tile.icon,
              label = tile.label,
              value = tile.value,
              modifier = Modifier.weight(1f),
              onRecalculate = tile.onRecalculate,
              isRecalculating = viewModel.isRecalculating,
            )
          }
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
        Text(stringResource(R.string.pet_detail_location), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(locationText(pet), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Text(label, modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatTile(
  icon: ImageVector,
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  onRecalculate: (() -> Unit)? = null,
  isRecalculating: Boolean = false,
) {
  var showMenu by remember { mutableStateOf(false) }
  val tileModifier = if (onRecalculate != null) modifier.combinedClickable(onClick = {}, onLongClick = { showMenu = true }) else modifier

  Surface(modifier = tileModifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
    Box {
      Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(value, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium)
        Text(label, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (onRecalculate != null) {
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.pet_detail_recalculate)) },
            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            enabled = !isRecalculating,
            onClick = {
              showMenu = false
              onRecalculate()
            },
          )
        }
      }
    }
  }
}

private data class Tile(val icon: ImageVector, val label: String, val value: String, val onRecalculate: (() -> Unit)? = null)

@Composable
private fun statTiles(pet: Pet, viewModel: PetDetailViewModel): List<Tile> {
  val format = rememberFormatting()
  val tiles = mutableListOf<Tile>()
  val position = pet.lastPosition
  tiles += Tile(Icons.Filled.Speed, stringResource(R.string.pet_detail_speed), position?.let { format.speed(it.speedKilometersPerHour) } ?: "—")
  tiles += Tile(Icons.Filled.Schedule, stringResource(R.string.pet_detail_last_update), pet.lastSeenAt?.let { format.relative(it) } ?: "—")
  position?.batteryLevelPercent?.let { tiles += Tile(batteryIcon(it), stringResource(R.string.pet_detail_battery), format.percent(it)) }
  viewModel.totalTrailDistanceMeters?.let {
    tiles += Tile(Icons.Filled.Map, stringResource(R.string.pet_detail_distance_today), format.distance(it))
  }
  viewModel.totalTimeAwayTodayMillis?.let {
    tiles += Tile(Icons.Filled.Pets, stringResource(R.string.pet_detail_time_away_today), format.duration(it))
  }
  viewModel.lifetimeStats?.takeIf { it.totalDistanceMeters > 0 }?.let {
    tiles +=
      Tile(Icons.Filled.Map, stringResource(R.string.pet_detail_total_distance), format.distance(it.totalDistanceMeters), onRecalculate = viewModel::recalculate)
  }
  viewModel.lifetimeStats?.takeIf { it.totalDurationMillis > 0 }?.let {
    tiles +=
      Tile(Icons.Filled.Pets, stringResource(R.string.pet_detail_total_time_away), format.duration(it.totalDurationMillis), onRecalculate = viewModel::recalculate)
  }
  return tiles
}

/** Same breakpoints as iOS's own `batteryIcon(for:)` (PetStatus+Presentation.swift). */
private fun batteryIcon(percent: Double): ImageVector =
  when {
    percent < 13 -> Icons.Filled.Battery0Bar
    percent < 38 -> Icons.Filled.Battery2Bar
    percent < 63 -> Icons.Filled.Battery4Bar
    percent < 88 -> Icons.Filled.Battery6Bar
    else -> Icons.Filled.BatteryFull
  }

@Composable
private fun locationText(pet: Pet): String {
  val position = pet.lastPosition ?: return stringResource(R.string.pet_detail_no_location)
  return position.address ?: rememberFormatting().coordinate(position.latitude, position.longitude)
}

@Composable
private fun statusCaption(pet: Pet): String {
  val label = stringResource(PetStatusPresentation.label(pet.status))
  if (pet.status == Pet.Status.ONLINE) return label
  val lastSeenAt = pet.lastSeenAt ?: return label
  return stringResource(R.string.pet_detail_status_line, label, rememberFormatting().relative(lastSeenAt))
}
