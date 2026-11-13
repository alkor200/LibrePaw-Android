package org.librepaw.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.librepaw.app.R
import org.librepaw.app.data.auth.AuthenticationService
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.ui.common.asString
import org.librepaw.app.ui.theme.LibrePawTheme

@Composable
fun LoginScreen(
  authenticationService: AuthenticationService,
  onLoggedIn: (StoredSession) -> Unit,
  modifier: Modifier = Modifier,
  /** Set when the server stopped accepting the previous session, see SessionExpiryDetectingPetRepository. */
  expiredSession: StoredSession? = null,
  viewModel: LoginViewModel = viewModel { LoginViewModel(authenticationService, expiredSession) },
) {
  LaunchedEffect(viewModel.loggedInSession) { viewModel.loggedInSession?.let(onLoggedIn) }

  LoginForm(
    serverUrlString = viewModel.serverUrlString,
    onServerUrlChange = viewModel::updateServerUrl,
    email = viewModel.email,
    onEmailChange = viewModel::updateEmail,
    password = viewModel.password,
    onPasswordChange = viewModel::updatePassword,
    usesInsecureConnection = viewModel.usesInsecureConnection,
    canSubmit = viewModel.canSubmit,
    isLoggingIn = viewModel.isLoggingIn,
    errorMessage = viewModel.errorMessage?.asString(),
    onSubmit = viewModel::logIn,
    modifier = modifier,
  )
}

@Composable
private fun LoginForm(
  serverUrlString: String,
  onServerUrlChange: (String) -> Unit,
  email: String,
  onEmailChange: (String) -> Unit,
  password: String,
  onPasswordChange: (String) -> Unit,
  usesInsecureConnection: Boolean,
  canSubmit: Boolean,
  isLoggingIn: Boolean,
  errorMessage: String?,
  onSubmit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  val emailFocusRequester = remember { FocusRequester() }
  val passwordFocusRequester = remember { FocusRequester() }

  Column(
    modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(48.dp))
    Icon(
      Icons.Filled.Pets,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(56.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(32.dp))

    OutlinedTextField(
      value = serverUrlString,
      onValueChange = onServerUrlChange,
      label = { Text(stringResource(R.string.login_server)) },
      placeholder = { Text("traccar.example.com") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
      keyboardActions = KeyboardActions(onNext = { emailFocusRequester.requestFocus() }),
      modifier = Modifier.fillMaxWidth(),
    )
    if (usesInsecureConnection) {
      Text(
        stringResource(R.string.login_insecure_connection),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      )
    }

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
      value = email,
      onValueChange = onEmailChange,
      label = { Text(stringResource(R.string.login_email)) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
      keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
      modifier = Modifier.fillMaxWidth().focusRequester(emailFocusRequester),
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
      value = password,
      onValueChange = onPasswordChange,
      label = { Text(stringResource(R.string.login_password)) },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
      keyboardActions =
        KeyboardActions(
          onDone = {
            focusManager.clearFocus()
            if (canSubmit) onSubmit()
          }
        ),
      modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
    )

    errorMessage?.let { message ->
      Spacer(Modifier.height(16.dp))
      Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    Spacer(Modifier.height(24.dp))

    Button(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
      if (isLoggingIn) {
        CircularProgressIndicator(
          modifier = Modifier.height(20.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          strokeWidth = 2.dp,
        )
      } else {
        Text(stringResource(R.string.login_log_in))
      }
    }
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginFormPreview() {
  LibrePawTheme {
    LoginForm(
      serverUrlString = "traccar.example.com",
      onServerUrlChange = {},
      email = "user@example.com",
      onEmailChange = {},
      password = "hunter2",
      onPasswordChange = {},
      usesInsecureConnection = false,
      canSubmit = true,
      isLoggingIn = false,
      errorMessage = null,
      onSubmit = {},
    )
  }
}
