package org.librepaw.app.data.traccar

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A minimal Traccar REST client, authenticated via HTTP Basic Auth.
 *
 * Only `fetchDevices()` exists so far, since it's also how a login attempt
 * is validated (Traccar has no dedicated "check credentials" endpoint).
 */
class TraccarClient(private val credentials: TraccarCredentials) {
  private val json = Json { ignoreUnknownKeys = true }

  private val httpClient =
    OkHttpClient.Builder()
      .addInterceptor { chain ->
        val request =
          chain.request().newBuilder().header("Authorization", credentials.basicAuthHeaderValue).build()
        chain.proceed(request)
      }
      .build()

  suspend fun fetchDevices(): List<TraccarDevice> {
    val url = credentials.serverUrl.newBuilder().addPathSegments("api/devices").build()
    val request = Request.Builder().url(url).get().build()

    val body =
      withContext(Dispatchers.IO) {
        try {
          httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
              when (response.code) {
                401,
                403, -> throw TraccarError.InvalidCredentials()
                else -> throw TraccarError.Server(response.code)
              }
            }
            response.body.string()
          }
        } catch (e: IOException) {
          throw TraccarError.Transport(e.message ?: "unknown transport error")
        }
      }

    return try {
      json.decodeFromString<List<TraccarDevice>>(body)
    } catch (e: SerializationException) {
      throw TraccarError.Decoding(e.message ?: "unknown decoding error")
    }
  }
}
