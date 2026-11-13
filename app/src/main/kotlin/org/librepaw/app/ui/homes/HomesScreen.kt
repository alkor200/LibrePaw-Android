package org.librepaw.app.ui.homes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.R
import org.librepaw.app.data.pets.AccountPermissions
import org.librepaw.app.data.pets.HomeGeofence
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.common.asString
import org.librepaw.app.ui.home.BottomBarClearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomesScreen(
  petId: Int,
  petName: String,
  petRepository: PetRepository,
  onBack: () -> Unit,
  onAddHome: () -> Unit,
  onEditHome: (HomeGeofence) -> Unit,
  modifier: Modifier = Modifier,
  dataVersion: Int = 0,
  viewModel: HomesViewModel = viewModel(key = "homes-$petId-$dataVersion") { HomesViewModel(petId, petRepository) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val canManageHomes = (uiState as? HomesUiState.Loaded)?.permissions?.canManageHomes == true

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.homes_title, petName)) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
    floatingActionButton = {
      if (canManageHomes) {
        FloatingActionButton(onClick = onAddHome, modifier = Modifier.padding(bottom = BottomBarClearance)) {
          Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.homes_add))
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
      when (val state = uiState) {
        HomesUiState.Loading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is HomesUiState.Failed ->
          Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(stringResource(R.string.homes_couldnt_load), style = MaterialTheme.typography.titleLarge)
            Text(
              state.message.asString(),
              modifier = Modifier.padding(top = 8.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 24.dp)) { Text(stringResource(R.string.action_try_again)) }
          }
        is HomesUiState.Loaded ->
          HomesList(
            homes = state.homes,
            permissions = state.permissions,
            onEditHome = onEditHome,
            onDeleteHome = viewModel::deleteHome,
            onAddHome = onAddHome,
          )
      }
    }
  }
}

@Composable
private fun HomesList(
  homes: List<HomeGeofence>,
  permissions: AccountPermissions?,
  onEditHome: (HomeGeofence) -> Unit,
  onDeleteHome: (Int) -> Unit,
  onAddHome: () -> Unit,
) {
  val canManageHomes = permissions?.canManageHomes == true

  if (homes.isEmpty()) {
    Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(stringResource(R.string.homes_empty_title), style = MaterialTheme.typography.titleLarge)
      Text(
        stringResource(R.string.homes_empty_description),
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (canManageHomes) {
        Button(onClick = onAddHome, modifier = Modifier.padding(top = 24.dp)) { Text(stringResource(R.string.homes_add)) }
      }
    }
    return
  }

  Column(modifier = Modifier.fillMaxSize()) {
    VisibilityNotice(permissions)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomBarClearance)) {
      items(homes, key = { it.id }) { home ->
        HomeRow(home = home, canManage = canManageHomes, onClick = { onEditHome(home) }, onDelete = { onDeleteHome(home.id) })
      }
    }
  }
}

@Composable
private fun VisibilityNotice(permissions: AccountPermissions?) {
  if (permissions == null) return
  val message =
    when {
      permissions.isAdministrator -> stringResource(R.string.homes_notice_admin)
      permissions.canManageHomes -> stringResource(R.string.homes_notice_manage)
      else -> stringResource(R.string.homes_notice_read_only)
    }
  Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
    Text(
      message,
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun HomeRow(home: HomeGeofence, canManage: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.width(16.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(home.name, style = MaterialTheme.typography.bodyLarge)
      Text(
        stringResource(R.string.homes_radius_short, home.radiusMeters.toInt()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (canManage) {
      IconButton(onClick = onDelete) {
        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.homes_delete_named, home.name), tint = MaterialTheme.colorScheme.error)
      }
    }
  }
}
