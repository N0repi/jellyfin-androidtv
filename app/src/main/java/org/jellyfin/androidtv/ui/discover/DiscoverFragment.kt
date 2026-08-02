package org.jellyfin.androidtv.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.integration.media.MediaKind
import org.jellyfin.androidtv.integration.media.MediaLookupResult
import org.jellyfin.androidtv.ui.base.LocalShapes
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.androidx.compose.koinViewModel

class DiscoverFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val viewModel = koinViewModel<DiscoverViewModel>()
		val state by viewModel.state.collectAsState()
		var query by rememberSaveable { mutableStateOf("") }

		Column(modifier = Modifier.fillMaxSize()) {
			MainToolbar(MainToolbarActiveButton.Discover)

			Column(
				modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				SearchTextInput(
					query = query,
					onQueryChange = {
						query = it
						viewModel.searchDebounced(it)
					},
					onQuerySubmit = { viewModel.searchImmediately(query) },
					placeholder = stringResource(R.string.discover_search_placeholder),
					modifier = Modifier.focusable(),
				)

				val caption = when {
					state.loading -> stringResource(R.string.discover_searching)
					state.error != null -> state.error!!
					else -> null
				}
				if (caption != null) Text(caption)
			}

			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 48.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				items(state.results, key = { DiscoverViewModel.keyFor(it) }) { result ->
					val requestState = state.requestStates[DiscoverViewModel.keyFor(result)] ?: RequestState.IDLE
					DiscoverResultRow(
						result = result,
						requestState = requestState,
						onClick = { viewModel.onResultSelected(result) },
					)
				}
			}
		}

		SeriesMonitorDialog(
			result = state.pendingSeries,
			onDismiss = viewModel::dismissMonitorDialog,
			onConfirm = viewModel::confirmSeriesMonitor,
		)
	}
}

@Composable
private fun DiscoverResultRow(
	result: MediaLookupResult,
	requestState: RequestState,
	onClick: () -> Unit,
) {
	val kindLabel = when (result.kind) {
		MediaKind.SERIES -> stringResource(R.string.lbl_tv_series)
		MediaKind.MOVIE -> stringResource(R.string.lbl_movies)
	}
	val title = result.year?.let { "${result.title} ($it)" } ?: result.title
	val trailing = when {
		result.alreadyAdded -> stringResource(R.string.discover_already_added)
		requestState == RequestState.REQUESTING -> stringResource(R.string.discover_requesting)
		requestState == RequestState.REQUESTED -> stringResource(R.string.discover_requested)
		requestState == RequestState.FAILED -> stringResource(R.string.discover_request_failed)
		else -> stringResource(R.string.discover_request)
	}

	ListButton(
		onClick = onClick,
		enabled = requestState != RequestState.REQUESTING,
		leadingContent = {
			AsyncImage(
				url = result.posterUrl,
				aspectRatio = 2f / 3f,
				scaleType = ImageView.ScaleType.CENTER_CROP,
				modifier = Modifier
					.size(width = 96.dp, height = 144.dp)
					.clip(LocalShapes.current.small),
			)
		},
		overlineContent = { Text(kindLabel) },
		headingContent = { Text(title) },
		captionContent = {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				result.overview?.let { Text(it, maxLines = 3) }
				if (result.kind == MediaKind.SERIES && result.seasons.isNotEmpty()) {
					val seasonCount = result.seasons.count { it.seasonNumber > 0 }
					Text(stringResource(R.string.discover_season_count, seasonCount))
				}
			}
		},
		trailingContent = { Text(trailing) },
	)
}
