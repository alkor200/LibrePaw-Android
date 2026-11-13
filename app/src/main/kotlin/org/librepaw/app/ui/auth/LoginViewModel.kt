package org.librepaw.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.librepaw.app.data.auth.AuthenticationService
import org.librepaw.app.data.auth.CredentialStorageException
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.data.traccar.TraccarError

class LoginViewModel(private val authenticationService: AuthenticationService) : ViewModel() {
  var serverUrlString by mutableStateOf("")
    private set

  var email by mutableStateOf("")
    private set

  var password by mutableStateOf("")
    private set

  var isLoggingIn by mutableStateOf(false)
    private set

  var errorMessage by mutableStateOf<String?>(null)
    private set

  var loggedInSession by mutableStateOf<StoredSession?>(null)
    private set

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
      errorMessage = "The server address is not a valid URL."
      return
    }

    isLoggingIn = true
    viewModelScope.launch {
      try {
        loggedInSession = authenticationService.logIn(serverUrl, email.trim(), password)
      } catch (e: TraccarError) {
        errorMessage = e.message
      } catch (e: CredentialStorageException) {
        errorMessage = e.message
      } finally {
        isLoggingIn = false
      }
    }
  }

  private fun normalizedServerUrl() =
    serverUrlString.trim().let { if ("://" in it) it else "https://$it" }.toHttpUrlOrNull()
}
