package org.jellyfin.androidtv.integration.arr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.preference.IntegrationPreferences
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class IntegrationService {
	SONARR,
	RADARR,
	QBITTORRENT,
}

data class ConnectionTestResult(
	val successful: Boolean,
	val message: String,
)

class IntegrationConnectionTester(
	private val preferences: IntegrationPreferences,
) {
	suspend fun test(service: IntegrationService): ConnectionTestResult = withContext(Dispatchers.IO) {
		runCatching {
			when (service) {
				IntegrationService.SONARR -> testArr(
					host = preferences[IntegrationPreferences.sonarrHost],
					port = preferences[IntegrationPreferences.sonarrPort],
					apiKey = preferences[IntegrationPreferences.sonarrApiKey],
				)

				IntegrationService.RADARR -> testArr(
					host = preferences[IntegrationPreferences.radarrHost],
					port = preferences[IntegrationPreferences.radarrPort],
					apiKey = preferences[IntegrationPreferences.radarrApiKey],
				)

				IntegrationService.QBITTORRENT -> testQBittorrent(
					host = preferences[IntegrationPreferences.qbittorrentHost],
					port = preferences[IntegrationPreferences.qbittorrentPort],
					username = preferences[IntegrationPreferences.qbittorrentUsername],
					password = preferences[IntegrationPreferences.qbittorrentPassword],
				)
			}
		}.getOrElse { error ->
			ConnectionTestResult(
				successful = false,
				message = error.message ?: "Unable to connect",
			)
		}
	}

	private fun testArr(
		host: String,
		port: Int,
		apiKey: String,
	): ConnectionTestResult {
		require(host.isNotBlank()) { "Enter a host or IP address" }
		require(apiKey.isNotBlank()) { "Enter an API key" }

		val connection = openConnection("${
			buildBaseUrl(host, port)
		}/api/v3/system/status").apply {
			requestMethod = "GET"
			setRequestProperty("X-Api-Key", apiKey)
		}
		return connection.useResponse { status ->
			if (status in 200..299) ConnectionTestResult(true, "Connection successful")
			else ConnectionTestResult(false, "Server returned HTTP $status")
		}
	}

	private fun testQBittorrent(
		host: String,
		port: Int,
		username: String,
		password: String,
	): ConnectionTestResult {
		require(host.isNotBlank()) { "Enter a host or IP address" }

		val body = buildString {
			append("username=")
			append(encodeFormValue(username))
			append("&password=")
			append(encodeFormValue(password))
		}.toByteArray()
		val connection = openConnection("${buildBaseUrl(host, port)}/api/v2/auth/login").apply {
			requestMethod = "POST"
			doOutput = true
			setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
			setFixedLengthStreamingMode(body.size)
			outputStream.use { it.write(body) }
		}
		return connection.useResponse { status ->
			val response = if (status in 200..299) inputStream.bufferedReader().use { it.readText() } else ""
			if (status in 200..299 && response.trim() == "Ok.") {
				ConnectionTestResult(true, "Connection successful")
			} else {
				ConnectionTestResult(false, "Login failed (HTTP $status)")
			}
		}
	}

	private fun openConnection(url: String): HttpURLConnection =
		(URI(url).toURL().openConnection() as HttpURLConnection).apply {
			connectTimeout = CONNECTION_TIMEOUT_MILLISECONDS
			readTimeout = CONNECTION_TIMEOUT_MILLISECONDS
		}

	private fun buildBaseUrl(host: String, port: Int): String {
		val normalizedHost = host.trim().trimEnd('/').let {
			if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
		}
		val uri = URI(normalizedHost)
		if (uri.port != -1) return normalizedHost

		return URI(
			uri.scheme,
			uri.userInfo,
			uri.host,
			port,
			uri.path,
			uri.query,
			uri.fragment,
		).toString().trimEnd('/')
	}

	private inline fun <T> HttpURLConnection.useResponse(block: HttpURLConnection.(Int) -> T): T = try {
		block(responseCode)
	} finally {
		disconnect()
	}

	private fun encodeFormValue(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

	private companion object {
		const val CONNECTION_TIMEOUT_MILLISECONDS = 5_000
	}
}
