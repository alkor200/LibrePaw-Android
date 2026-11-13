package org.librepaw.app.data.traccar

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * A minimal Traccar REST client, authenticated via a Bearer token (or, for a server that can't
 * issue tokens, HTTP Basic Auth) for ordinary JSON endpoints, see [TraccarCredentials].
 *
 * A handful of endpoints (the current-session user, media/photo fetches) ignore that header and
 * need a cookie-backed session instead, established lazily on first use (see
 * [ensureCookieSession]) and reused by OkHttp's own in-memory cookie jar for the rest of this
 * client's lifetime, mirroring the iOS client's own lazy `ensureCookieSession()`.
 *
 * Grows endpoint by endpoint as each feature needs it; there is no attempt to model Traccar's
 * whole API up front.
 */
class TraccarClient(private val credentials: TraccarCredentials) {
  val serverUrl: HttpUrl get() = credentials.serverUrl

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Guards [cookieSessionGeneration]: several requests needing the cookie session at once (e.g. the
   * map loading every pet's photo concurrently) used to each see none yet and each log in, opening
   * a server-side session apiece.
   */
  private val cookieSessionMutex = Mutex()

  /** 0 while there's no cookie session, bumped every time one is (re-)established. */
  private var cookieSessionGeneration = 0
  private var nextCookieSessionGeneration = 1

  /**
   * In-memory only, so the cookie dies with this client. Keeps cookies by name, domain and path
   * and honors their expiry, domain, path and `Secure` flag: it used to keep only the latest
   * response's cookies per host, so any later response setting some other cookie (a load
   * balancer's, say) silently dropped the session cookie.
   */
  private val cookieJar =
    object : CookieJar {
      private val cookies = mutableListOf<Cookie>()

      @Synchronized
      override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
          this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
          this.cookies += cookie
        }
      }

      @Synchronized
      override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
      }
    }

  private val httpClient =
    OkHttpClient.Builder()
      .cookieJar(cookieJar)
      // OkHttp follows an https -> http redirect by default. It drops the Authorization header
      // when the scheme changes, but a 307/308 re-sends the body as is, e.g. the email/password
      // form of a password-based cookie-session login. The network security config refuses
      // cleartext anyway, this keeps it from ever being attempted.
      .followSslRedirects(false)
      .addInterceptor { chain ->
        val request =
          chain.request().newBuilder().header("Authorization", credentials.authorizationHeaderValue).build()
        chain.proceed(request)
      }
      .build()

  /** Also how a login attempt is validated: Traccar has no dedicated login endpoint. */
  suspend fun fetchDevices(): List<TraccarDevice> = getJson("api/devices")

  /** The latest known position for every device the user can see. */
  suspend fun fetchLatestPositions(): List<TraccarPosition> = getJson("api/positions")

  /** Position history for one device within a time range, Traccar's own ordering (usually oldest first). */
  suspend fun fetchPositionHistory(deviceId: Int, fromEpochMillis: Long, toEpochMillis: Long): List<TraccarPosition> {
    val url =
      credentials.serverUrl.newBuilder().addPathSegments("api/positions")
        .addQueryParameter("deviceId", deviceId.toString())
        .addQueryParameter("from", TraccarDates.queryParam(fromEpochMillis))
        .addQueryParameter("to", TraccarDates.queryParam(toEpochMillis))
        .build()
    return getJson(url)
  }

  /** Geofences visible to the user, optionally scoped to one device. */
  suspend fun fetchGeofences(deviceId: Int? = null): List<TraccarGeofence> {
    val builder = credentials.serverUrl.newBuilder().addPathSegments("api/geofences")
    if (deviceId != null) builder.addQueryParameter("deviceId", deviceId.toString())
    return getJson(builder.build())
  }

  suspend fun createGeofence(name: String, area: String, attributes: Map<String, JsonElement>): TraccarGeofence {
    val url = credentials.serverUrl.newBuilder().addPathSegments("api/geofences").build()
    val payload = geofencePayload(id = null, name = name, area = area, attributes = attributes)
    return postJson(url, payload)
  }

  suspend fun updateGeofence(id: Int, name: String, area: String, attributes: Map<String, JsonElement>) {
    val url = credentials.serverUrl.newBuilder().addPathSegments("api/geofences/$id").build()
    val payload = geofencePayload(id = id, name = name, area = area, attributes = attributes)
    executeVoid(Request.Builder().url(url).put(payload.toString().toRequestBody("application/json".toMediaType())).build())
  }

  suspend fun deleteGeofence(id: Int) {
    val url = credentials.serverUrl.newBuilder().addPathSegments("api/geofences/$id").build()
    executeVoid(Request.Builder().url(url).delete().build())
  }

  suspend fun linkGeofenceToDevice(geofenceId: Int, deviceId: Int) {
    linkPermission(mapOf("deviceId" to JsonPrimitive(deviceId), "geofenceId" to JsonPrimitive(geofenceId)))
  }

  suspend fun linkGeofenceToUser(geofenceId: Int, userId: Int) {
    linkPermission(mapOf("userId" to JsonPrimitive(userId), "geofenceId" to JsonPrimitive(geofenceId)))
  }

  suspend fun linkDeviceToUser(deviceId: Int, userId: Int) {
    linkPermission(mapOf("userId" to JsonPrimitive(userId), "deviceId" to JsonPrimitive(deviceId)))
  }

  private suspend fun linkPermission(fields: Map<String, JsonElement>) {
    val url = credentials.serverUrl.newBuilder().addPathSegments("api/permissions").build()
    val payload = JsonObject(fields)
    executeVoid(Request.Builder().url(url).post(payload.toString().toRequestBody("application/json".toMediaType())).build())
  }

  /** Events of one [type] (e.g. `geofenceEnter`/`geofenceExit`) for a device within a time range. */
  suspend fun fetchEvents(deviceId: Int, fromEpochMillis: Long, toEpochMillis: Long, type: String): List<TraccarEvent> {
    val url =
      credentials.serverUrl.newBuilder().addPathSegments("api/reports/events")
        .addQueryParameter("deviceId", deviceId.toString())
        .addQueryParameter("from", TraccarDates.queryParam(fromEpochMillis))
        .addQueryParameter("to", TraccarDates.queryParam(toEpochMillis))
        .addQueryParameter("type", type)
        .build()
    return getJson(url)
  }

  /**
   * Issues a new API token for the signed-in account, valid until [expiresAtEpochMillis]. Traccar
   * returns the token itself as the plain response body.
   */
  suspend fun createToken(expiresAtEpochMillis: Long): String {
    val url = credentials.serverUrl.newBuilder().addPathSegments("api/session/token").build()
    val body = FormBody.Builder().add("expiration", TraccarDates.queryParam(expiresAtEpochMillis)).build()
    return withResponse(Request.Builder().url(url).post(body).build()) { response ->
      if (!response.isSuccessful) throw statusError(response.code)
      response.body.string().trim()
    }
  }

  /** Invalidates [token] on the server, so it stops working even before it expires. */
  suspend fun revokeToken(token: String) {
    val url = credentials.serverUrl.newBuilder().addPathSegments("api/session/token/revoke").build()
    executeVoid(Request.Builder().url(url).post(FormBody.Builder().add("token", token).build()).build())
  }

  /**
   * Ends the cookie session on the server (`DELETE /api/session`), if one was ever established,
   * rather than leaving it valid until it times out. The cookie itself only lives in this client's
   * in-memory jar anyway.
   */
  suspend fun endSession() {
    val hadCookieSession =
      cookieSessionMutex.withLock {
        val had = cookieSessionGeneration != 0
        cookieSessionGeneration = 0
        had
      }
    if (!hadCookieSession) return
    executeVoid(Request.Builder().url(credentials.serverUrl.newBuilder().addPathSegments("api/session").build()).delete().build())
  }

  /** Every user on the server; admin-only (used to look up a user by email for pet sharing). */
  suspend fun fetchUsers(): List<TraccarUser> = getJson("api/users")

  /**
   * The signed-in account. Needs the cookie session, not Basic Auth: `GET /api/session` returns
   * the current session's user only when a valid session cookie is attached.
   */
  suspend fun fetchCurrentUser(): TraccarUser {
    ensureCookieSession()
    return getJson("api/session")
  }

  /** Fetches raw bytes from a Traccar-hosted URL (e.g. a device photo); needs the cookie session. */
  suspend fun fetchData(url: HttpUrl): ByteArray {
    val generation = ensureCookieSession()
    val request = Request.Builder().url(url).get().build()
    val firstAttempt = withResponse(request) { response -> if (response.code == 401) null else readBytes(response) }
    if (firstAttempt != null) return firstAttempt

    // The session expired server-side: renewed once, however many requests ran into it at once.
    ensureCookieSession(replacing = generation)
    return withResponse(request, ::readBytes)
  }

  private fun readBytes(response: Response): ByteArray {
    if (!response.isSuccessful) throw statusError(response.code)
    return response.body.bytes()
  }

  suspend fun updateDeviceName(deviceId: Int, name: String) {
    updateDeviceObject(deviceId) { device ->
      val fields = device.toMutableMap()
      fields["name"] = JsonPrimitive(name)
      JsonObject(fields)
    }
  }

  /** Uploads a photo, then links it to the device via its `deviceImage` attribute. Returns the stored filename. */
  suspend fun updateDeviceImage(deviceId: Int, imageData: ByteArray, contentType: String): String {
    val url = credentials.serverUrl.newBuilder().addPathSegments("api/devices/$deviceId/image").build()
    val request = Request.Builder().url(url).post(imageData.toRequestBody(contentType.toMediaType())).build()
    val filename =
      withResponse(request) { response ->
        if (!response.isSuccessful) throw statusError(response.code)
        response.body.string()
      }

    updateDeviceObject(deviceId) { device ->
      val attributes = (device["attributes"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
      attributes["deviceImage"] = JsonPrimitive(filename)
      val fields = device.toMutableMap()
      fields["attributes"] = JsonObject(attributes)
      JsonObject(fields)
    }

    return filename
  }

  /**
   * Traccar's `PUT` replaces every column on a device, so a partial payload would null out
   * whatever it omits. Reads the device as a raw JSON object (not [TraccarDevice], which only
   * models the fields this app reads) so fields it doesn't know about round-trip untouched.
   */
  private suspend fun updateDeviceObject(deviceId: Int, mutate: (JsonObject) -> JsonObject) {
    val url = credentials.serverUrl.newBuilder().addPathSegments("api/devices/$deviceId").build()

    val current =
      withResponse(Request.Builder().url(url).get().build()) { response ->
        if (!response.isSuccessful) throw statusError(response.code)
        try {
          json.parseToJsonElement(response.body.string()).jsonObject
        } catch (e: IllegalArgumentException) {
          // Both malformed JSON (SerializationException is one) and valid JSON that isn't an
          // object, which `.jsonObject` reports as a plain IllegalArgumentException that no caller
          // catches, crashing the app instead of failing the save.
          throw TraccarError.Decoding(e.message ?: "unknown decoding error")
        }
      }

    val updated = mutate(current)
    val putRequest =
      Request.Builder().url(url).put(updated.toString().toRequestBody("application/json".toMediaType())).build()
    withResponse(putRequest) { response -> if (!response.isSuccessful) throw statusError(response.code) }
  }

  private fun geofencePayload(id: Int?, name: String, area: String, attributes: Map<String, JsonElement>): JsonObject {
    val fields = mutableMapOf<String, JsonElement>()
    if (id != null) fields["id"] = JsonPrimitive(id)
    fields["name"] = JsonPrimitive(name)
    fields["area"] = JsonPrimitive(area)
    fields["attributes"] = JsonObject(attributes)
    return JsonObject(fields)
  }

  /**
   * `GET /api/session?token=...` with a token (a Bearer header alone doesn't establish one), or
   * `POST /api/session` with form-urlencoded credentials with a password. Either way Traccar sets
   * the session cookie via `Set-Cookie`.
   */
  private suspend fun ensureCookieSession(replacing: Int? = null): Int =
    cookieSessionMutex.withLock {
      // Only the session the caller saw fail is replaced, not one another caller already renewed.
      if (replacing != null && cookieSessionGeneration == replacing) cookieSessionGeneration = 0
      if (cookieSessionGeneration != 0) return@withLock cookieSessionGeneration
      openCookieSession()
      cookieSessionGeneration = nextCookieSessionGeneration++
      cookieSessionGeneration
    }

  private suspend fun openCookieSession() {
    val sessionUrl = credentials.serverUrl.newBuilder().addPathSegments("api/session")
    val request =
      when (val secret = credentials.secret) {
        is TraccarCredentials.Secret.Token -> Request.Builder().url(sessionUrl.addQueryParameter("token", secret.value).build()).get()
        is TraccarCredentials.Secret.Password ->
          Request.Builder()
            .url(sessionUrl.build())
            .post(FormBody.Builder().add("email", credentials.email).add("password", secret.value).build())
      }
    withResponse(request.build()) { response -> if (!response.isSuccessful) throw statusError(response.code) }
  }

  private suspend inline fun <reified T> getJson(path: String): T =
    getJson(credentials.serverUrl.newBuilder().addPathSegments(path).build())

  private suspend inline fun <reified T> getJson(url: HttpUrl): T = executeJson(Request.Builder().url(url).get().build())

  private suspend inline fun <reified T> postJson(url: HttpUrl, payload: JsonObject): T =
    executeJson(Request.Builder().url(url).post(payload.toString().toRequestBody("application/json".toMediaType())).build())

  private suspend inline fun <reified T> executeJson(request: Request): T {
    val body =
      withResponse(request) { response ->
        if (!response.isSuccessful) throw statusError(response.code)
        response.body.string()
      }
    return try {
      json.decodeFromString(body)
    } catch (e: SerializationException) {
      throw TraccarError.Decoding(e.message ?: "unknown decoding error")
    }
  }

  private suspend fun executeVoid(request: Request) {
    withResponse(request) { response -> if (!response.isSuccessful) throw statusError(response.code) }
  }

  /**
   * Executes [request] and runs [block] against its response, all on [Dispatchers.IO] — reading
   * the response body is itself blocking I/O, so it must happen inside the same dispatcher
   * switch as the request, not after returning to the caller's (possibly main) thread.
   */
  private suspend fun <T> withResponse(request: Request, block: (Response) -> T): T =
    withContext(Dispatchers.IO) {
      try {
        httpClient.newCall(request).execute().use(block)
      } catch (e: IOException) {
        throw TraccarError.Transport(e.message ?: "unknown transport error")
      }
    }

  private fun statusError(code: Int): TraccarError =
    when (code) {
      401 -> TraccarError.InvalidCredentials()
      // Not InvalidCredentials: telling someone whose password is fine that it's wrong sends them
      // off retyping it, or worse, resetting it.
      403 -> TraccarError.Forbidden()
      else -> TraccarError.Server(code)
    }
}
