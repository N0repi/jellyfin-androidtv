package org.jellyfin.androidtv.integration.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Configuration for a single Sonarr/Radarr instance.
 */
data class ArrConfig(
	val kind: MediaKind,
	val baseUrl: String,
	val apiKey: String,
)

/**
 * Minimal client for the Sonarr/Radarr v3 API. Sonarr and Radarr share an almost identical surface;
 * the differences (path segment, external id field, and search command) are derived from [MediaKind].
 */
class ArrClient(
	private val httpClient: OkHttpClient,
	private val json: Json,
) {
	private val jsonMediaType = "application/json".toMediaType()

	suspend fun lookup(config: ArrConfig, term: String): Result<List<MediaLookupResult>> = runArr {
		val encodedTerm = java.net.URLEncoder.encode(term, "UTF-8")
		val response = get(config, "/api/v3/${config.pathSegment}/lookup?term=$encodedTerm")
		val array = json.parseToJsonElement(response).jsonArray
		array.mapNotNull { element -> element.jsonObject.toLookupResult(config.kind) }
	}

	suspend fun getQualityProfiles(config: ArrConfig): Result<List<QualityProfile>> = runArr {
		val response = get(config, "/api/v3/qualityprofile")
		json.parseToJsonElement(response).jsonArray.map { element ->
			val obj = element.jsonObject
			QualityProfile(
				id = obj["id"]!!.jsonPrimitive.int,
				name = obj["name"]?.jsonPrimitive?.contentOrNull().orEmpty(),
			)
		}
	}

	suspend fun getRootFolders(config: ArrConfig): Result<List<RootFolder>> = runArr {
		val response = get(config, "/api/v3/rootfolder")
		json.parseToJsonElement(response).jsonArray.mapNotNull { element ->
			element.jsonObject["path"]?.jsonPrimitive?.contentOrNull()?.let(::RootFolder)
		}
	}

	/**
	 * Adds media (monitored) and requests an immediate search. If the item already exists, only the
	 * search command is issued so the action stays idempotent from the user's perspective.
	 *
	 * For series, [seriesMonitor] controls which seasons/episodes Sonarr monitors.
	 */
	suspend fun addAndSearch(
		config: ArrConfig,
		lookup: MediaLookupResult,
		qualityProfileId: Int,
		rootFolderPath: String,
		seriesMonitor: SeriesMonitorSelection = SeriesMonitorSelection.Preset(SeriesMonitorType.ALL),
	): Result<MediaRequestResult> = runArr {
		if (lookup.existingId != null) {
			triggerSearch(config, lookup.existingId)
			return@runArr MediaRequestResult(mediaId = lookup.existingId, searchTriggered = true)
		}

		val payload = buildAddPayload(config, lookup, qualityProfileId, rootFolderPath, seriesMonitor)
		val response = post(config, "/api/v3/${config.pathSegment}", payload.toString())
		val created = json.parseToJsonElement(response).jsonObject
		val mediaId = created["id"]!!.jsonPrimitive.int
		// The add request already sets addOptions to search, but issuing the command explicitly is
		// more reliable across service versions.
		triggerSearch(config, mediaId)
		MediaRequestResult(mediaId = mediaId, searchTriggered = true)
	}

	private fun buildAddPayload(
		config: ArrConfig,
		lookup: MediaLookupResult,
		qualityProfileId: Int,
		rootFolderPath: String,
		seriesMonitor: SeriesMonitorSelection,
	): JsonObject = buildJsonObject {
		val raw = lookup.raw
		// Echo back every field from the lookup response...
		for ((key, value) in raw) {
			if (key != "seasons") put(key, value)
		}
		// ...then override the fields required to add the item.
		put("qualityProfileId", JsonPrimitive(qualityProfileId))
		put("rootFolderPath", JsonPrimitive(rootFolderPath))
		put("monitored", JsonPrimitive(true))
		if (config.kind == MediaKind.SERIES) {
			put("seasonFolder", JsonPrimitive(true))
			put("seasons", buildSeasonsArray(lookup.seasons, seriesMonitor))
			put(
				"addOptions",
				buildJsonObject {
					put("monitor", JsonPrimitive(monitorApiValue(seriesMonitor)))
					put("searchForMissingEpisodes", JsonPrimitive(true))
				},
			)
		} else {
			put(
				"addOptions",
				buildJsonObject {
					put("searchForMovie", JsonPrimitive(true))
				},
			)
		}
	}

	private fun monitorApiValue(selection: SeriesMonitorSelection): String = when (selection) {
		is SeriesMonitorSelection.Preset -> selection.type.apiValue
		is SeriesMonitorSelection.Seasons -> SeriesMonitorType.NONE.apiValue
	}

	private fun buildSeasonsArray(
		seasons: List<SeriesSeason>,
		selection: SeriesMonitorSelection,
	): JsonArray = buildJsonArray {
		val monitoredNumbers = when (selection) {
			is SeriesMonitorSelection.Seasons -> selection.seasonNumbers
			is SeriesMonitorSelection.Preset -> null
		}
		for (season in seasons) {
			val monitored = when (selection) {
				is SeriesMonitorSelection.Seasons -> season.seasonNumber in monitoredNumbers.orEmpty()
				is SeriesMonitorSelection.Preset -> when (selection.type) {
					SeriesMonitorType.ALL, SeriesMonitorType.MISSING, SeriesMonitorType.EXISTING ->
						season.seasonNumber > 0
					SeriesMonitorType.FUTURE, SeriesMonitorType.NONE -> false
					SeriesMonitorType.FIRST_SEASON -> season.seasonNumber == seasons
						.filter { it.seasonNumber > 0 }
						.minOfOrNull { it.seasonNumber }
					SeriesMonitorType.LAST_SEASON -> season.seasonNumber == seasons
						.filter { it.seasonNumber > 0 }
						.maxOfOrNull { it.seasonNumber }
					SeriesMonitorType.PILOT -> season.seasonNumber == 1
				}
			}
			add(
				buildJsonObject {
					put("seasonNumber", JsonPrimitive(season.seasonNumber))
					put("monitored", JsonPrimitive(monitored == true))
				}
			)
		}
	}

	private fun triggerSearch(config: ArrConfig, mediaId: Int) {
		val command = buildJsonObject {
			when (config.kind) {
				MediaKind.SERIES -> {
					put("name", JsonPrimitive("SeriesSearch"))
					put("seriesId", JsonPrimitive(mediaId))
				}

				MediaKind.MOVIE -> {
					put("name", JsonPrimitive("MoviesSearch"))
					put("movieIds", JsonArray(listOf(JsonPrimitive(mediaId))))
				}
			}
		}
		post(config, "/api/v3/command", command.toString())
	}

	private fun JsonObject.toLookupResult(kind: MediaKind): MediaLookupResult? {
		val title = this["title"]?.jsonPrimitive?.contentOrNull() ?: return null
		val existingId = this["id"]?.jsonPrimitive?.intOrNull()?.takeIf { it > 0 }
		val poster = (this["images"] as? JsonArray)
			?.firstOrNull { image ->
				image.jsonObject["coverType"]?.jsonPrimitive?.contentOrNull() == "poster"
			}
			?.jsonObject
			?.let { it["remoteUrl"] ?: it["url"] }
			?.jsonPrimitive?.contentOrNull()

		val seasons = (this["seasons"] as? JsonArray)?.mapNotNull { element ->
			val obj = element.jsonObject
			val number = obj["seasonNumber"]?.jsonPrimitive?.intOrNull() ?: return@mapNotNull null
			val monitored = obj["monitored"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
			SeriesSeason(
				seasonNumber = number,
				episodeCount = obj["statistics"]?.jsonObject
					?.get("totalEpisodeCount")
					?.jsonPrimitive
					?.intOrNull(),
				monitored = monitored,
			)
		}.orEmpty()

		return MediaLookupResult(
			kind = kind,
			title = title,
			year = this["year"]?.jsonPrimitive?.intOrNull()?.takeIf { it > 0 },
			overview = this["overview"]?.jsonPrimitive?.contentOrNull(),
			posterUrl = poster,
			existingId = existingId,
			seasons = seasons,
			raw = this,
		)
	}

	private fun get(config: ArrConfig, path: String): String {
		val request = Request.Builder()
			.url(config.baseUrl.trimEnd('/') + path)
			.header("X-Api-Key", config.apiKey)
			.get()
			.build()
		return execute(request)
	}

	private fun post(config: ArrConfig, path: String, body: String): String {
		val request = Request.Builder()
			.url(config.baseUrl.trimEnd('/') + path)
			.header("X-Api-Key", config.apiKey)
			.post(body.toRequestBody(jsonMediaType))
			.build()
		return execute(request)
	}

	private fun execute(request: Request): String = httpClient.newCall(request).execute().use { response ->
		val body = response.body?.string().orEmpty()
		check(response.isSuccessful) { "HTTP ${response.code}" }
		body
	}

	private suspend inline fun <T> runArr(crossinline block: () -> T): Result<T> = try {
		Result.success(withContext(Dispatchers.IO) { block() })
	} catch (error: Exception) {
		Timber.e(error, "arr request failed")
		Result.failure(error)
	}

	private val ArrConfig.pathSegment: String
		get() = when (kind) {
			MediaKind.SERIES -> "series"
			MediaKind.MOVIE -> "movie"
		}

	private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else content.ifBlank { null }

	private fun JsonPrimitive.intOrNull(): Int? = content.toIntOrNull()
}
