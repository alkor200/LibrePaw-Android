package org.librepaw.app.ui.excursions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.librepaw.app.data.pets.Excursion
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.home.BottomBarClearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcursionsScreen(
  petId: Int,
  petName: String,
  petRepository: PetRepository,
  onBack: () -> Unit,
  onExcursionClick: (Excursion) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ExcursionsViewModel = viewModel(key = "excursions-$petId") { ExcursionsViewModel(petId, petRepository) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Excursions") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = {
          IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
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
        ExcursionsUiState.Loading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is ExcursionsUiState.Failed ->
          Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text("Couldn't Load Excursions", style = MaterialTheme.typography.titleLarge)
            Text(
              state.message,
              modifier = Modifier.padding(top = 8.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 24.dp)) { Text("Try Again") }
          }
        is ExcursionsUiState.Loaded ->
          if (state.excursions.isEmpty()) {
            Column(
              modifier = Modifier.fillMaxSize().padding(32.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Text("No Excursions Yet", style = MaterialTheme.typography.titleLarge)
              Text(
                "Once $petName leaves home and comes back, excursions will show up here. Make sure a home location is set first.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomBarClearance)) {
              items(state.excursions, key = { it.id }) { excursion ->
                ExcursionRow(excursion = excursion, homeNamesById = state.homeNamesById, onClick = { onExcursionClick(excursion) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              }
            }
          }
      }
    }
  }
}

@Composable
private fun ExcursionRow(excursion: Excursion, homeNamesById: Map<Int, String>, onClick: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(dateTimeText(excursion.startTime), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
      if (excursion.isOngoing) {
        Badge(icon = Icons.Filled.Pets, text = "Away now", color = MaterialTheme.colorScheme.tertiary)
      } else if (excursion.isLikelyVehicleTrip) {
        Badge(icon = Icons.Filled.DirectionsCar, text = "Car ride?", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    routeText(excursion, homeNamesById)?.let { text ->
      Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Filled.SwapHoriz,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text,
          modifier = Modifier.padding(start = 4.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      excursion.durationMillis?.let { IconLabel(icon = Icons.Filled.Schedule, text = durationText(it)) }
      excursion.distanceMeters?.let { IconLabel(icon = Icons.Filled.Map, text = distanceText(it)) }
    }
  }
}

@Composable
private fun Badge(icon: ImageVector, text: String, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
    Text(text, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = color)
  }
}

@Composable
private fun IconLabel(icon: ImageVector, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    Text(text, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/**
 * `"Home → Cabin"` when both ends resolve to a known home *and* they differ, an ordinary round
 * trip to the same home says nothing new here.
 */
private fun routeText(excursion: Excursion, homeNamesById: Map<Int, String>): String? {
  val start = homeNamesById[excursion.startHomeId] ?: return null
  val end = homeNamesById[excursion.endHomeId] ?: return null
  if (start == end) return null
  return "$start → $end"
}

private fun dateTimeText(epochMillis: Long): String =
  SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(epochMillis))

internal fun durationText(millis: Long): String {
  val minutes = millis / 60_000
  if (minutes < 60) return "$minutes min"
  return "${minutes / 60}h ${minutes % 60}m"
}

internal fun distanceText(meters: Double): String = if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000)
