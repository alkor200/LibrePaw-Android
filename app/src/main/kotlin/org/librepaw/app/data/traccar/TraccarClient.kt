package org.librepaw.app.data.traccar

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A minimal Traccar REST client, authenticated via HTTP Basic Auth.
 *
 * Grows endpoint by endpoint as each feature needs it; there is no attempt
 * to model Traccar's whole API up front.
 */
class TraccarClient(private val credentials: TraccarCredentials) {
  val serverUrl: HttpUrl get() = credentials.serverUrl

  private val json = Json { ignoreUnknownKeys = true }

  private val httpClient =
    OkHttpClient.Builder()
      .addInterceptor { chain ->
        val request =
          chain.request().newBuilder().header("Authorization", credentials.basicAuthHeaderValue).build()
        chain.proceed(request)
      }
      .build()

  /** Also how a login attempt is validated: Traccar has no dedicated login endpoint. */
  suspend fun fetchDevices(): List<TraccarDevice> = getJson("api/devices")

  /** The latest known position for every device the user can see. */
  suspend fun fetchLatestPositions(): List<TraccarPosition> = getJson("api/positions")

  /** Fetches raw bytes from a Traccar-hosted URL (e.g. a device photo), with the same auth as everything else. */
  suspend fun fetchData(url: HttpUrl): ByteArray =
    withContext(Dispatchers.IO) {
      try {
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
          if (!response.isSuccessful) throw statusError(response.code)
          response.body.bytes()
        }
      } catch (e: IOException) {
        throw TraccarError.Transport(e.message ?: "unknown transport error")
      }
    }

  private suspend inline fun <reified T> getJson(path: String): T {
    val url = credentials.serverUrl.newBuilder().addPathSegments(path).build()
    val body =
      withContext(Dispatchers.IO) {
        try {
          httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw statusError(response.code)
            response.body.string()
          }
        } catch (e: IOException) {
          throw TraccarError.Transport(e.message ?: "unknown transport error")
        }
      }
    return try {
      json.decodeFromString(body)
    } catch (e: SerializationException) {
      throw TraccarError.Decoding(e.message ?: "unknown decoding error")
    }
  }

  private fun statusError(code: Int): TraccarError =
    when (code) {
      401,
      403, -> TraccarError.InvalidCredentials()
      else -> TraccarError.Server(code)
    }
}
