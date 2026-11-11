package org.librepaw.app.ui.petlist

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.common.PetAvatar
import org.librepaw.app.ui.common.PetStatusPresentation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetListScreen(
  petRepository: PetRepository,
  onPetClick: (Pet) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: PetListViewModel = viewModel { PetListViewModel(petRepository) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Pets") },
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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
        PetListUiState.Loading -> LoadingContent()
        is PetListUiState.Failed -> FailedContent(message = state.message, onRetry = viewModel::refresh)
        is PetListUiState.Loaded ->
          if (state.pets.isEmpty()) {
            EmptyContent()
          } else {
            PetGrid(pets = state.pets, petRepository = petRepository, onPetClick = onPetClick)
          }
      }
    }
  }
}

@Composable
private fun LoadingContent() {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator()
    Text("Loading pets…", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit) {
  EmptyStateMessage(
    title = "Couldn't Load Pets",
    description = message,
    actionLabel = "Try Again",
    onAction = onRetry,
  )
}

@Composable
private fun EmptyContent() {
  EmptyStateMessage(
    title = "No Pets Yet",
    description = "Pets you add as devices in Traccar will show up here.",
  )
}

@Composable
private fun EmptyStateMessage(
  title: String,
  description: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(
      description,
      modifier = Modifier.padding(top = 8.dp),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (actionLabel != null && onAction != null) {
      Button(onClick = onAction, modifier = Modifier.padding(top = 24.dp)) {
        Text(actionLabel)
      }
    }
  }
}

@Composable
private fun PetGrid(pets: List<Pet>, petRepository: PetRepository, onPetClick: (Pet) -> Unit) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 100.dp),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
    horizontalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    items(pets, key = { it.id }) { pet -> PetGridCell(pet = pet, petRepository = petRepository, onClick = { onPetClick(pet) }) }
  }
}

@Composable
private fun PetGridCell(pet: Pet, petRepository: PetRepository, onClick: () -> Unit) {
  Column(
    modifier = Modifier.clickable(onClick = onClick).fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Top,
  ) {
    PetAvatar(pet = pet, petRepository = petRepository, size = 84.dp)
    Text(
      pet.name,
      modifier = Modifier.padding(top = 8.dp),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      subtitle(pet),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun subtitle(pet: Pet): String =
  when {
    pet.status == Pet.Status.ONLINE -> "Online"
    pet.lastSeenAt != null ->
      DateUtils.getRelativeTimeSpanString(pet.lastSeenAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        .toString()
    else -> PetStatusPresentation.label(pet.status)
  }
