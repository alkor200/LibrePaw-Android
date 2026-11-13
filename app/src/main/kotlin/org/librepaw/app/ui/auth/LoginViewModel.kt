package org.librepaw.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.librepaw.app.R
import org.librepaw.app.data.auth.AuthenticationService
import org.librepaw.app.data.auth.CredentialStorageException
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.data.traccar.TraccarError
import org.librepaw.app.ui.common.UiText
import org.librepaw.app.ui.common.toUiText

/**
 * [expiredSession], when the server stopped accepting the previous session, prefills its server and
 * email and says why the user is back here, same as the iOS app's LoginViewModel.
 */
class LoginViewModel(private val authenticationService: AuthenticationService, expiredSession: StoredSession? = null) :
  ViewModel() {
  var serverUrlString by mutableStateOf("")
    private set

  var email by mutableStateOf("")
    private set

  var password by mutableStateOf("")
    private set

  var isLoggingIn by mutableStateOf(false)
    private set

  var errorMessage by mutableStateOf<UiText?>(null)
    private set

  var loggedInSession by mutableStateOf<StoredSession?>(null)
    private set

  init {
    if (expiredSession != null) {
      // Shown the way it was most likely typed, "traccar.example.com" rather than "https://traccar.example.com/".
      serverUrlString = expiredSession.serverUrl.removePrefix("https://").removeSuffix("/")
      email = expiredSession.email
      errorMessage = UiText(R.string.login_session_expired)
    }
  }

  fun updateServerUrl(value: String) {
    serverUrlString = value
  }

  fun updateEmail(value: String) {
    email = value
  }

  fun updatePassword(value: String) {
    password = value
  }

  val usesInsecureConnection: Boolean
    get() = serverUrlString.trim().startsWith("http://", ignoreCase = true)

  val canSubmit: Boolean
    get() =
      serverUrlString.isNotBlank() && email.isNotBlank() && password.isNotBlank() &&
        !isLoggingIn &&
        !usesInsecureConnection

  fun logIn() {
    if (!canSubmit) return
    errorMessage = null

    val serverUrl = normalizedServerUrl()
    if (serverUrl == null) {
      errorMessage = UiText(R.string.error_invalid_server_url)
      return
    }

    isLoggingIn = true
    viewModelScope.launch {
      try {
        loggedInSession = authenticationService.logIn(serverUrl, email.trim(), password)
      } catch (e: TraccarError) {
        errorMessage = e.toUiText()
      } catch (e: CredentialStorageException) {
        errorMessage = e.toUiText()
      } finally {
        isLoggingIn = false
      }
    }
  }

  private fun normalizedServerUrl() =
    serverUrlString.trim().let { if ("://" in it) it else "https://$it" }.toHttpUrlOrNull()
}
