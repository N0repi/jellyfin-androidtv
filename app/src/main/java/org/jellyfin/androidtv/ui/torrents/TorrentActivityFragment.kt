package org.jellyfin.androidtv.ui.torrents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.integration.media.TorrentInfo
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.list.ListControl
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

class TorrentActivityFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val viewModel = koinViewModel<TorrentActivityViewModel>()
		val state by viewModel.state.collectAsState()

		Column(modifier = Modifier.fillMaxSize()) {
			MainToolbar(MainToolbarActiveButton.Torrents)

			val caption = when {
				state.loading -> stringResource(R.string.loading)
				state.error != null -> state.error
				state.torrents.isEmpty() -> stringResource(R.string.torrents_empty)
				else -> null
			}
			if (caption != null) {
				Text(caption, modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp))
			}

			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 48.dp, vertical = 12.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				items(state.torrents, key = { it.hash }) { torrent ->
					TorrentRow(
						torrent = torrent,
						busy = torrent.hash in state.busyHashes,
						onStop = { viewModel.stopTorrent(torrent.hash) },
						onRemove = { viewModel.removeTorrent(torrent.hash) },
					)
				}
			}
		}
	}
}

@Composable
private fun TorrentRow(
	torrent: TorrentInfo,
	busy: Boolean,
	onStop: () -> Unit,
	onRemove: () -> Unit,
) {
	val percent = (torrent.progress * 100).toInt()
	val down = formatSpeed(torrent.downloadSpeed)
	val up = formatSpeed(torrent.uploadSpeed)
	val eta = torrent.eta?.let { formatEta(it) } ?: "—"
	val stopped = torrent.state.contains("paused", ignoreCase = true) ||
		torrent.state.contains("stopped", ignoreCase = true)

	ListControl(
		overlineContent = { Text(torrent.state) },
		headingContent = { Text(torrent.name, maxLines = 1) },
		captionContent = {
			Text(stringResource(R.string.torrents_progress, percent, down, up, eta))
		},
		footerContent = {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Button(
					onClick = onStop,
					enabled = !busy && !stopped,
				) {
					Text(stringResource(R.string.torrents_stop))
				}
				Button(
					onClick = onRemove,
					enabled = !busy,
				) {
					Text(stringResource(R.string.torrents_remove))
				}
			}
		},
	)
}

private fun formatSpeed(bytesPerSecond: Long): String {
	if (bytesPerSecond <= 0) return "0 B/s"
	val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
	var value = bytesPerSecond.toDouble()
	var unitIndex = 0
	while (value >= 1024 && unitIndex < units.lastIndex) {
		value /= 1024
		unitIndex++
	}
	return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

private fun formatEta(seconds: Long): String {
	val hours = seconds / 3600
	val minutes = (seconds % 3600) / 60
	val secs = seconds % 60
	return when {
		hours > 0 -> String.format(Locale.US, "%dh %dm", hours, minutes)
		minutes > 0 -> String.format(Locale.US, "%dm %ds", minutes, secs)
		else -> String.format(Locale.US, "%ds", secs)
	}
}
