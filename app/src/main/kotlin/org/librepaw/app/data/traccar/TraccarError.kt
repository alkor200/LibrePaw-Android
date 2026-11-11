package org.librepaw.app.data.traccar

/** Errors surfaced by [TraccarClient]. */
sealed class TraccarError(message: String) : Exception(message) {
  /** The server rejected the supplied credentials (HTTP 401/403). */
  class InvalidCredentials : TraccarError("The email or password is incorrect.")

  /** The server returned a non-success status code other than 401/403. */
  data class Server(val statusCode: Int) :
    TraccarError("The server responded with an unexpected status ($statusCode).")

  /** The response body could not be decoded into the expected type. */
  data class Decoding(val detail: String) : TraccarError("The server response could not be understood.")

  /** Something the app looked up by a user-supplied value (e.g. an email) doesn't exist on the server. */
  data class NotFound(val detail: String) : TraccarError(detail)

  /** The underlying HTTP transport failed (no connectivity, TLS error, etc). */
  data class Transport(val detail: String) : TraccarError("Could not reach the server: $detail")

  /** The supplied server URL could not be turned into a valid request URL. */
  class InvalidServerUrl : TraccarError("The server address is not a valid URL.")
}
