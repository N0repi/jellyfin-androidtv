package org.jellyfin.androidtv.integration.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

data class QBittorrentConfig(
	val baseUrl: String,
	val username: String,
	val password: String,
)

/**
 * Minimal client for the qBittorrent WebUI API (v2). Authentication uses a session cookie which the
 * provided [httpClient] is expected to persist via a CookieJar. Calls transparently re-authenticate
 * once when the session has expired.
 *
 * qBittorrent 5.x uses /torrents/stop (replacing /pause). /delete removes the torrent from the list.
 */
class QBittorrentClient(
	private val httpClient: OkHttpClient,
	private val json: Json,
) {
	suspend fun getTorrents(config: QBittorrentConfig): Result<List<TorrentInfo>> = runCatchingIo {
		get(config, "/api/v2/torrents/info").let(::parseTorrents)
	}

	suspend fun stopTorrent(config: QBittorrentConfig, hash: String): Result<Unit> = runCatchingIo {
		postForm(config, "/api/v2/torrents/stop", "hashes" to hash)
	}

	/**
	 * Removes the torrent from qBittorrent. When [deleteFiles] is false, downloaded data is kept.
	 */
	suspend fun removeTorrent(
		config: QBittorrentConfig,
		hash: String,
		deleteFiles: Boolean = false,
	): Result<Unit> = runCatchingIo {
		postForm(
			config,
			"/api/v2/torrents/delete",
			"hashes" to hash,
			"deleteFiles" to deleteFiles.toString(),
		)
	}

	private fun get(config: QBittorrentConfig, path: String, allowRetry: Boolean = true): String {
		val request = Request.Builder()
			.url(config.baseUrl.trimEnd('/') + path)
			.header("Referer", config.baseUrl.trimEnd('/'))
			.get()
			.build()

		httpClient.newCall(request).execute().use { response ->
			if (response.code == HTTP_FORBIDDEN && allowRetry) {
				response.close()
				authenticate(config)
				return get(config, path, allowRetry = false)
			}
			val body = response.body?.string().orEmpty()
			check(response.isSuccessful) { "HTTP ${response.code}" }
			return body
		}
	}

	private fun postForm(
		config: QBittorrentConfig,
		path: String,
		vararg fields: Pair<String, String>,
		allowRetry: Boolean = true,
	) {
		val bodyBuilder = FormBody.Builder()
		for ((key, value) in fields) bodyBuilder.add(key, value)
		val request = Request.Builder()
			.url(config.baseUrl.trimEnd('/') + path)
			.header("Referer", config.baseUrl.trimEnd('/'))
			.post(bodyBuilder.build())
			.build()

		httpClient.newCall(request).execute().use { response ->
			if (response.code == HTTP_FORBIDDEN && allowRetry) {
				response.close()
				authenticate(config)
				postForm(config, path, *fields, allowRetry = false)
				return
			}
			check(response.isSuccessful) { "HTTP ${response.code}" }
		}
	}

	fun authenticate(config: QBittorrentConfig) {
		val body = FormBody.Builder()
			.add("username", config.username)
			.add("password", config.password)
			.build()
		val request = Request.Builder()
			.url(config.baseUrl.trimEnd('/') + "/api/v2/auth/login")
			.header("Referer", config.baseUrl.trimEnd('/'))
			.post(body)
			.build()
		httpClient.newCall(request).execute().use { response ->
			val payload = response.body?.string().orEmpty()
			check(response.isSuccessful && payload.trim() == "Ok.") { "Login failed (HTTP ${response.code})" }
		}
	}

	private fun parseTorrents(body: String): List<TorrentInfo> =
		json.parseToJsonElement(body).jsonArray.map { element ->
			val obj = element.jsonObject
			TorrentInfo(
				hash = obj["hash"]?.jsonPrimitive?.content.orEmpty(),
				name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
				progress = obj["progress"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
				state = obj["state"]?.jsonPrimitive?.content.orEmpty(),
				downloadSpeed = obj["dlspeed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
				uploadSpeed = obj["upspeed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
				eta = obj["eta"]?.jsonPrimitive?.content?.toLongOrNull()?.takeIf { it in 1 until ETA_INFINITY },
				size = obj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
			)
		}

	private suspend inline fun <T> runCatchingIo(crossinline block: () -> T): Result<T> = try {
		Result.success(withContext(Dispatchers.IO) { block() })
	} catch (error: Exception) {
		Timber.e(error, "qBittorrent request failed")
		Result.failure(error)
	}

	private companion object {
		const val HTTP_FORBIDDEN = 403
		const val ETA_INFINITY = 8_640_000L
	}
}
