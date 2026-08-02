package org.jellyfin.androidtv.integration.media

import org.jellyfin.androidtv.preference.IntegrationPreferences

/**
 * Reads integration configuration from [IntegrationPreferences] and exposes high level operations
 * used by the discover and torrent activity screens.
 */
class MediaIntegrationRepository(
	private val preferences: IntegrationPreferences,
	private val arrClient: ArrClient,
	private val qBittorrentClient: QBittorrentClient,
) {
	val sonarrEnabled: Boolean
		get() = preferences[IntegrationPreferences.sonarrEnabled] &&
			preferences[IntegrationPreferences.sonarrHost].isNotBlank()

	val radarrEnabled: Boolean
		get() = preferences[IntegrationPreferences.radarrEnabled] &&
			preferences[IntegrationPreferences.radarrHost].isNotBlank()

	val qBittorrentEnabled: Boolean
		get() = preferences[IntegrationPreferences.qbittorrentEnabled] &&
			preferences[IntegrationPreferences.qbittorrentHost].isNotBlank()

	suspend fun lookup(kind: MediaKind, term: String): Result<List<MediaLookupResult>> {
		val config = arrConfig(kind) ?: return Result.success(emptyList())
		return arrClient.lookup(config, term)
	}

	suspend fun requestMedia(
		lookup: MediaLookupResult,
		seriesMonitor: SeriesMonitorSelection = SeriesMonitorSelection.Preset(SeriesMonitorType.ALL),
	): Result<MediaRequestResult> {
		val config = arrConfig(lookup.kind)
			?: return Result.failure(IllegalStateException("Service not configured"))

		val qualityProfileId = arrClient.getQualityProfiles(config)
			.getOrElse { return Result.failure(it) }
			.firstOrNull()?.id
			?: return Result.failure(IllegalStateException("No quality profiles configured"))

		val rootFolder = arrClient.getRootFolders(config)
			.getOrElse { return Result.failure(it) }
			.firstOrNull()?.path
			?: return Result.failure(IllegalStateException("No root folders configured"))

		return arrClient.addAndSearch(config, lookup, qualityProfileId, rootFolder, seriesMonitor)
	}

	suspend fun getTorrents(): Result<List<TorrentInfo>> {
		val config = qBittorrentConfig()
			?: return Result.failure(IllegalStateException("qBittorrent not configured"))
		return qBittorrentClient.getTorrents(config)
	}

	suspend fun stopTorrent(hash: String): Result<Unit> {
		val config = qBittorrentConfig()
			?: return Result.failure(IllegalStateException("qBittorrent not configured"))
		return qBittorrentClient.stopTorrent(config, hash)
	}

	suspend fun removeTorrent(hash: String, deleteFiles: Boolean = false): Result<Unit> {
		val config = qBittorrentConfig()
			?: return Result.failure(IllegalStateException("qBittorrent not configured"))
		return qBittorrentClient.removeTorrent(config, hash, deleteFiles)
	}

	private fun arrConfig(kind: MediaKind): ArrConfig? = when (kind) {
		MediaKind.SERIES -> if (!sonarrEnabled) null else ArrConfig(
			kind = MediaKind.SERIES,
			baseUrl = buildBaseUrl(
				preferences[IntegrationPreferences.sonarrHost],
				preferences[IntegrationPreferences.sonarrPort],
			),
			apiKey = preferences[IntegrationPreferences.sonarrApiKey],
		)

		MediaKind.MOVIE -> if (!radarrEnabled) null else ArrConfig(
			kind = MediaKind.MOVIE,
			baseUrl = buildBaseUrl(
				preferences[IntegrationPreferences.radarrHost],
				preferences[IntegrationPreferences.radarrPort],
			),
			apiKey = preferences[IntegrationPreferences.radarrApiKey],
		)
	}

	private fun qBittorrentConfig(): QBittorrentConfig? {
		if (!qBittorrentEnabled) return null
		return QBittorrentConfig(
			baseUrl = buildBaseUrl(
				preferences[IntegrationPreferences.qbittorrentHost],
				preferences[IntegrationPreferences.qbittorrentPort],
			),
			username = preferences[IntegrationPreferences.qbittorrentUsername],
			password = preferences[IntegrationPreferences.qbittorrentPassword],
		)
	}

	private fun buildBaseUrl(host: String, port: Int): String {
		val trimmed = host.trim().trimEnd('/')
		val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
			trimmed
		} else {
			"http://$trimmed"
		}
		// Only append the configured port when the host doesn't already specify one.
		val afterScheme = withScheme.substringAfter("://")
		return if (afterScheme.contains(":")) withScheme else "$withScheme:$port"
	}
}
