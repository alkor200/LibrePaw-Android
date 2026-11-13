package org.librepaw.app.data.traccar

/** Errors surfaced by [TraccarClient]. */
sealed class TraccarError(message: String) : Exception(message) {
  /** The server rejected the supplied credentials (HTTP 401). */
  class InvalidCredentials : TraccarError("The email or password is incorrect.")

  /**
   * The credentials are fine, but this account may not do that (HTTP 403), or something in front
   * of the server (a reverse proxy, a firewall) refused the request.
   */
  class Forbidden : TraccarError("This account isn't allowed to do that on this server.")

  /** The server returned a non-success status code other than 401/403. */
  data class Server(val statusCode: Int) :
    TraccarError("The server responded with an unexpected status ($statusCode).")

  /** The response body could not be decoded into the expected type. */
  data class Decoding(val detail: String) : TraccarError("The server response could not be understood.")

  /** Something the app looked up by a user-supplied value (e.g. an email) doesn't exist on the server. */
  data class NotFound(val detail: String) : TraccarError(detail)

  /** The underlying HTTP transport failed (no connectivity, a timeout, etc). */
  data class Transport(val detail: String) : TraccarError("Could not reach the server: $detail")

  /**
   * The server was reached, but no secure connection could be established with it: a certificate
   * this device doesn't trust (self-signed, or a root CA too new for it, e.g. Let's Encrypt on
   * Android 7.0) or TLS settings it doesn't support. Unlike [Transport], retrying won't help.
   */
  data class SecureConnection(val detail: String) : TraccarError("Could not establish a secure connection to the server: $detail")

  /** The supplied server URL could not be turned into a valid request URL. */
  class InvalidServerUrl : TraccarError("The server address is not a valid URL.")
}
