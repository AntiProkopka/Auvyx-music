package com.nexus.music.data.catalog

import android.net.Uri
import android.util.Base64
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class SpotifyItem(val id: String, val type: String, val title: String, val subtitle: String)

internal data class SpotifyPlayback(
    val title: String, val playing: Boolean, val positionMs: Long, val durationMs: Long
)

internal object SpotifyWebApi {
    const val redirect = "http://127.0.0.1/callback"
    private const val accounts = "https://accounts.spotify.com"
    private const val api = "https://api.spotify.com/v1"

    private fun nonce() = Base64.encodeToString(
        ByteArray(32).also(SecureRandom()::nextBytes),
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )

    suspend fun authorize(clientId: String, open: (String) -> Unit): String = withContext(Dispatchers.IO) {
        require(clientId.matches(Regex("[a-fA-F0-9]{32}"))) { "Проверь Spotify Client ID" }
        val verifier = nonce()
        val state = nonce()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 1000
            val callback = "http://127.0.0.1:${server.localPort}/callback"
            val login = Uri.parse("$accounts/authorize").buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "user-read-playback-state user-modify-playback-state")
                .appendQueryParameter("redirect_uri", callback)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .build()
            withContext(Dispatchers.Main) { open(login.toString()) }
            val deadline = android.os.SystemClock.elapsedRealtime() + 120_000L
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                val response = runInterruptible {
                    val socket = try { server.accept() } catch (_: java.net.SocketTimeoutException) { return@runInterruptible null }
                    socket.use {
                        it.soTimeout = 1000
                        val request = try { it.getInputStream().bufferedReader().readLine().orEmpty() }
                            catch (_: java.net.SocketTimeoutException) { return@use null }
                        val uri = Uri.parse(request.split(' ').getOrNull(1).orEmpty())
                        if (uri.path != "/callback" || uri.getQueryParameter("state") != state) return@use null
                        val message = "Return to AUVYX to see the Spotify sign-in result."
                        it.getOutputStream().write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\n" +
                                "Content-Length: ${message.length}\r\nConnection: close\r\n\r\n$message").toByteArray()
                        )
                        uri
                    }
                } ?: continue
                val code = response.getQueryParameter("code") ?: throw IOException("Spotify не разрешил вход")
                val form = listOf("grant_type" to "authorization_code", "client_id" to clientId,
                    "code" to code, "redirect_uri" to callback, "code_verifier" to verifier)
                    .joinToString("&") { (key, value) -> "$key=${Uri.encode(value)}" }
                return@withContext request("$accounts/api/token", form = form).getString("access_token")
            }
            throw IOException("Время входа в Spotify истекло")
        }
    }

    suspend fun search(token: String, query: String, type: String, offset: Int) =
        withContext(Dispatchers.IO) {
            entries(request("$api/search?q=${Uri.encode(query)}&type=$type&limit=10&offset=$offset", token)
                .getJSONObject("${type}s"), type)
        }

    suspend fun collection(token: String, item: SpotifyItem, offset: Int) = withContext(Dispatchers.IO) {
        val artist = item.type == "artist"
        val path = if (artist) "artists/${item.id}/albums" else "albums/${item.id}/tracks"
        entries(request("$api/$path?limit=10&offset=$offset", token), if (artist) "album" else "track")
    }

    suspend fun playback(token: String): SpotifyPlayback? = withContext(Dispatchers.IO) {
        val response = request("$api/me/player", token)
        val track = response.optJSONObject("item") ?: return@withContext null
        SpotifyPlayback(track.optString("name"), response.optBoolean("is_playing"),
            response.optLong("progress_ms"), track.optLong("duration_ms"))
    }

    suspend fun play(token: String, track: SpotifyItem) = withContext(Dispatchers.IO) {
        request("$api/me/player/play", token, method = "PUT",
            payload = JSONObject().put("uris", org.json.JSONArray().put("spotify:track:${track.id}")))
    }

    suspend fun enqueue(token: String, track: SpotifyItem) = withContext(Dispatchers.IO) {
        val uri = Uri.encode("spotify:track:${track.id}")
        request("$api/me/player/queue?uri=$uri", token, method = "POST")
    }

    suspend fun command(token: String, command: String, positionMs: Long = 0) = withContext(Dispatchers.IO) {
        val path = if (command == "seek") "seek?position_ms=$positionMs" else command
        request("$api/me/player/$path", token, method = if (command in setOf("next", "previous")) "POST" else "PUT")
    }

    private fun entries(page: JSONObject, type: String): Pair<List<SpotifyItem>, Boolean> {
        val rows = page.getJSONArray("items")
        val items = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id")
            if (!id.matches(Regex("[A-Za-z0-9]{22}"))) return@mapNotNull null
            val artists = row.optJSONArray("artists")
            val labels = (0 until (artists?.length() ?: 0)).mapNotNull {
                artists?.optJSONObject(it)?.optString("name")
            }
            SpotifyItem(id, type, row.getString("name"), labels.joinToString(", "))
        }
        return items to !page.isNull("next")
    }

    private fun request(
        url: String, token: String? = null, form: String? = null,
        method: String = "GET", payload: JSONObject? = null
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.requestMethod = method
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            }
            if (form != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(form.toByteArray()) }
            }
            when (val status = connection.responseCode) {
                200 -> return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                204 -> return JSONObject()
                404 -> throw IOException("Нет активного устройства Spotify. Запусти Spotify, включи трек и повтори.")
                401 -> throw IOException("Сессия Spotify истекла. Войди снова.")
                403 -> throw IOException("Spotify отклонил запрос. Проверь доступ аккаунта к приложению и наличие Premium для управления звуком.")
                429 -> throw IOException("Spotify ограничил частоту запросов. Попробуй позже.")
                else -> throw IOException("Spotify вернул HTTP $status.")
            }
        } finally {
            connection.disconnect()
        }
    }
}
