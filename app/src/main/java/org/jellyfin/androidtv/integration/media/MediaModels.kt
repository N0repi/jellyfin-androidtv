package org.jellyfin.androidtv.integration.media

import kotlinx.serialization.json.JsonObject

/**
 * The kind of media managed by an *arr service.
 */
enum class MediaKind {
	SERIES,
	MOVIE,
}

/**
 * A season entry returned by Sonarr's series lookup.
 */
data class SeriesSeason(
	val seasonNumber: Int,
	val episodeCount: Int?,
	val monitored: Boolean,
)

/**
 * Sonarr's built-in monitor presets (addOptions.monitor).
 */
enum class SeriesMonitorType(val apiValue: String) {
	ALL("all"),
	FUTURE("future"),
	MISSING("missing"),
	EXISTING("existing"),
	FIRST_SEASON("firstSeason"),
	LAST_SEASON("lastSeason"),
	PILOT("pilot"),
	NONE("none"),
}

/**
 * How to monitor a series when requesting it from Discover.
 */
sealed class SeriesMonitorSelection {
	data class Preset(val type: SeriesMonitorType) : SeriesMonitorSelection()
	/** Monitor only the given season numbers (specials = 0). */
	data class Seasons(val seasonNumbers: Set<Int>) : SeriesMonitorSelection()
}

/**
 * A single lookup result returned by Sonarr or Radarr. The [raw] payload is retained because adding
 * media requires echoing the full object returned by the lookup endpoint back to the service.
 */
data class MediaLookupResult(
	val kind: MediaKind,
	val title: String,
	val year: Int?,
	val overview: String?,
	val posterUrl: String?,
	/** Non-null when the item already exists in the service (its internal id). */
	val existingId: Int?,
	val seasons: List<SeriesSeason> = emptyList(),
	val raw: JsonObject,
) {
	val alreadyAdded: Boolean get() = existingId != null
}

data class QualityProfile(
	val id: Int,
	val name: String,
)

data class RootFolder(
	val path: String,
)

/**
 * The outcome of requesting media: whether it was newly added and the id used to trigger a search.
 */
data class MediaRequestResult(
	val mediaId: Int,
	val searchTriggered: Boolean,
)

/**
 * A snapshot of a single torrent from qBittorrent.
 */
data class TorrentInfo(
	val hash: String,
	val name: String,
	/** Progress in the range 0..1. */
	val progress: Float,
	val state: String,
	val downloadSpeed: Long,
	val uploadSpeed: Long,
	/** Seconds remaining, or null when unknown/complete. */
	val eta: Long?,
	val size: Long,
)
