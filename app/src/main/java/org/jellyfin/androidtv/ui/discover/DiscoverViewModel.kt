package org.jellyfin.androidtv.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.integration.media.MediaIntegrationRepository
import org.jellyfin.androidtv.integration.media.MediaKind
import org.jellyfin.androidtv.integration.media.MediaLookupResult
import org.jellyfin.androidtv.integration.media.SeriesMonitorSelection
import org.jellyfin.androidtv.integration.media.SeriesMonitorType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class RequestState {
	IDLE,
	REQUESTING,
	REQUESTED,
	FAILED,
}

data class DiscoverUiState(
	val loading: Boolean = false,
	val results: List<MediaLookupResult> = emptyList(),
	val requestStates: Map<String, RequestState> = emptyMap(),
	val error: String? = null,
	/** Series awaiting a monitor selection before the request is sent. */
	val pendingSeries: MediaLookupResult? = null,
)

class DiscoverViewModel(
	private val repository: MediaIntegrationRepository,
) : ViewModel() {
	companion object {
		private val debounceDuration = 600.milliseconds

		fun keyFor(result: MediaLookupResult): String = "${result.kind}:${result.title}:${result.year}"
	}

	private var searchJob: Job? = null
	private var previousQuery: String? = null

	private val _state = MutableStateFlow(DiscoverUiState())
	val state = _state.asStateFlow()

	val sonarrEnabled: Boolean get() = repository.sonarrEnabled
	val radarrEnabled: Boolean get() = repository.radarrEnabled

	fun searchImmediately(query: String) = searchDebounced(query, 0.milliseconds)

	fun searchDebounced(query: String, debounce: Duration = debounceDuration) {
		val trimmed = query.trim()
		if (trimmed == previousQuery) return
		previousQuery = trimmed

		searchJob?.cancel()

		if (trimmed.isBlank()) {
			_state.value = DiscoverUiState()
			return
		}

		_state.update { it.copy(loading = true, error = null) }

		searchJob = viewModelScope.launch {
			delay(debounce)

			val kinds = buildList {
				if (repository.sonarrEnabled) add(MediaKind.SERIES)
				if (repository.radarrEnabled) add(MediaKind.MOVIE)
			}

			if (kinds.isEmpty()) {
				_state.value = DiscoverUiState(error = "No services enabled")
				return@launch
			}

			val results = kinds.map { kind ->
				async { repository.lookup(kind, trimmed).getOrDefault(emptyList()) }
			}.awaitAll().flatten()

			_state.update {
				it.copy(
					loading = false,
					results = results,
					error = if (results.isEmpty()) "No results" else null,
				)
			}
		}
	}

	/**
	 * Movies are requested immediately. Series open the monitor selection dialog first.
	 */
	fun onResultSelected(result: MediaLookupResult) {
		when (result.kind) {
			MediaKind.MOVIE -> requestMedia(result)
			MediaKind.SERIES -> _state.update { it.copy(pendingSeries = result) }
		}
	}

	fun dismissMonitorDialog() {
		_state.update { it.copy(pendingSeries = null) }
	}

	fun confirmSeriesMonitor(selection: SeriesMonitorSelection) {
		val pending = _state.value.pendingSeries ?: return
		_state.update { it.copy(pendingSeries = null) }
		requestMedia(pending, selection)
	}

	fun requestMedia(
		result: MediaLookupResult,
		seriesMonitor: SeriesMonitorSelection = SeriesMonitorSelection.Preset(SeriesMonitorType.ALL),
	) {
		val key = keyFor(result)
		if (_state.value.requestStates[key] == RequestState.REQUESTING) return

		setRequestState(key, RequestState.REQUESTING)
		viewModelScope.launch {
			val outcome = repository.requestMedia(result, seriesMonitor)
			setRequestState(key, if (outcome.isSuccess) RequestState.REQUESTED else RequestState.FAILED)
		}
	}

	private fun setRequestState(key: String, state: RequestState) {
		_state.update { it.copy(requestStates = it.requestStates + (key to state)) }
	}
}
