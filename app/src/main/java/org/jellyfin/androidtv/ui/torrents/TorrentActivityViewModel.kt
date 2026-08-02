package org.jellyfin.androidtv.ui.torrents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.integration.media.MediaIntegrationRepository
import org.jellyfin.androidtv.integration.media.TorrentInfo
import kotlin.time.Duration.Companion.seconds

data class TorrentActivityUiState(
	val loading: Boolean = true,
	val torrents: List<TorrentInfo> = emptyList(),
	val error: String? = null,
	val busyHashes: Set<String> = emptySet(),
)

class TorrentActivityViewModel(
	private val repository: MediaIntegrationRepository,
) : ViewModel() {
	private companion object {
		val POLL_INTERVAL = 3.seconds
		val ERROR_RETRY_INTERVAL = 10.seconds
	}

	private val busyHashes = MutableStateFlow<Set<String>>(emptySet())

	private val polled = flow {
		while (true) {
			val result = repository.getTorrents()
			emit(
				result.fold(
					onSuccess = { TorrentActivityUiState(loading = false, torrents = it) },
					onFailure = {
						TorrentActivityUiState(
							loading = false,
							error = it.message ?: "Unable to reach qBittorrent",
						)
					},
				)
			)
			delay(if (result.isSuccess) POLL_INTERVAL else ERROR_RETRY_INTERVAL)
		}
	}

	val state = combine(polled, busyHashes) { snapshot, busy ->
		snapshot.copy(busyHashes = busy)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
		initialValue = TorrentActivityUiState(),
	)

	fun stopTorrent(hash: String) = runTorrentAction(hash) {
		repository.stopTorrent(hash)
	}

	fun removeTorrent(hash: String) = runTorrentAction(hash) {
		repository.removeTorrent(hash, deleteFiles = false)
	}

	private fun runTorrentAction(hash: String, action: suspend () -> Result<Unit>) {
		if (hash in busyHashes.value) return
		busyHashes.value = busyHashes.value + hash
		viewModelScope.launch {
			try {
				action()
			} finally {
				busyHashes.value = busyHashes.value - hash
			}
		}
	}
}
