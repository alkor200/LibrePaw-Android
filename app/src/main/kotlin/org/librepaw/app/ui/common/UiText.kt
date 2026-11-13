package org.librepaw.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.librepaw.app.R
import org.librepaw.app.data.auth.CredentialStorageException
import org.librepaw.app.data.traccar.TraccarError

/**
 * Text a ViewModel wants shown, resolved only by the UI, in the app's current language. A
 * ViewModel survives a language switch (the activity is recreated, its ViewModels aren't), so one
 * holding an already-resolved String would keep showing it in the old language.
 */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
  constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
}

@Composable
fun UiText.asString(): String = stringResource(id, *args.toTypedArray())

/**
 * What to show the user for this error, worded in the app's own language. The data layer's own
 * exception messages are English-only fallbacks for logs, never shown as is (same as the iOS app's
 * `Error.userFacingMessage`).
 */
fun Throwable.toUiText(): UiText =
  when (this) {
    is TraccarError.InvalidCredentials -> UiText(R.string.error_invalid_credentials)
    is TraccarError.Forbidden -> UiText(R.string.error_forbidden)
    is TraccarError.Server -> UiText(R.string.error_server_status, statusCode)
    is TraccarError.Decoding -> UiText(R.string.error_unreadable_response)
    is TraccarError.NotFound -> UiText(R.string.error_user_not_found)
    is TraccarError.Transport -> UiText(R.string.error_transport, detail)
    is TraccarError.InvalidServerUrl -> UiText(R.string.error_invalid_server_url)
    is CredentialStorageException -> UiText(R.string.error_credential_storage)
    else -> UiText(R.string.error_generic)
  }
