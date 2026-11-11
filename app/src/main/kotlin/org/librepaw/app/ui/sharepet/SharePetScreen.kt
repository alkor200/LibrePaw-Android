package org.librepaw.app.ui.sharepet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.data.pets.PetRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePetScreen(
  petId: Int,
  petName: String,
  petRepository: PetRepository,
  onShared: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SharePetViewModel =
    viewModel(key = "share-pet-$petId") { SharePetViewModel(petId, petName, petRepository) },
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Share $petName") },
        navigationIcon = {
          IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel") }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(24.dp)) {
      OutlinedTextField(
        value = viewModel.email,
        onValueChange = viewModel::updateEmail,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
      )
      Text(
        "The Traccar account with this email will be able to see $petName in their own copy of LibrePaw, the same as you.",
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      viewModel.errorMessage?.let { message ->
        Text(
          message,
          modifier = Modifier.padding(top = 16.dp),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      Button(
        onClick = { viewModel.share(onSuccess = onShared) },
        enabled = !viewModel.isSharing,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
      ) {
        if (viewModel.isSharing) {
          CircularProgressIndicator(modifier = Modifier.padding(2.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
          Text("Share $petName")
        }
      }
    }
  }
}
