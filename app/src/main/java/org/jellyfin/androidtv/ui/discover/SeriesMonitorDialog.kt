package org.jellyfin.androidtv.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.integration.media.MediaLookupResult
import org.jellyfin.androidtv.integration.media.SeriesMonitorSelection
import org.jellyfin.androidtv.integration.media.SeriesMonitorType
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalShapes
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection

@Composable
fun SeriesMonitorDialog(
	result: MediaLookupResult?,
	onDismiss: () -> Unit,
	onConfirm: (SeriesMonitorSelection) -> Unit,
) {
	DialogBase(
		visible = result != null,
		onDismissRequest = onDismiss,
	) {
		if (result == null) return@DialogBase

		var customMode by remember(result) { mutableStateOf(false) }
		var selectedSeasons by remember(result) {
			mutableStateOf(
				result.seasons
					.filter { it.seasonNumber > 0 }
					.map { it.seasonNumber }
					.toSet()
			)
		}

		Column(
			modifier = Modifier
				.widthIn(max = 720.dp)
				.heightIn(max = 600.dp)
				.background(JellyfinTheme.colorScheme.background, LocalShapes.current.large)
				.padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			ListSection(
				overlineContent = { Text(stringResource(R.string.discover_monitor_overline).uppercase()) },
				headingContent = {
					Text(result.year?.let { "${result.title} ($it)" } ?: result.title)
				},
				captionContent = { Text(stringResource(R.string.discover_monitor_description)) },
			)

			if (!customMode) {
				PresetButton(R.string.discover_monitor_all) {
					onConfirm(SeriesMonitorSelection.Preset(SeriesMonitorType.ALL))
				}
				PresetButton(R.string.discover_monitor_future) {
					onConfirm(SeriesMonitorSelection.Preset(SeriesMonitorType.FUTURE))
				}
				PresetButton(R.string.discover_monitor_first_season) {
					onConfirm(SeriesMonitorSelection.Preset(SeriesMonitorType.FIRST_SEASON))
				}
				PresetButton(R.string.discover_monitor_last_season) {
					onConfirm(SeriesMonitorSelection.Preset(SeriesMonitorType.LAST_SEASON))
				}
				PresetButton(R.string.discover_monitor_pilot) {
					onConfirm(SeriesMonitorSelection.Preset(SeriesMonitorType.PILOT))
				}
				PresetButton(R.string.discover_monitor_none) {
					onConfirm(SeriesMonitorSelection.Preset(SeriesMonitorType.NONE))
				}
				if (result.seasons.isNotEmpty()) {
					Button(
						onClick = { customMode = true },
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(stringResource(R.string.discover_monitor_custom_seasons))
					}
				}
			} else {
				LazyColumn(
					modifier = Modifier.heightIn(max = 360.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					items(result.seasons, key = { it.seasonNumber }) { season ->
						val selected = season.seasonNumber in selectedSeasons
						val label = if (season.seasonNumber == 0) {
							stringResource(R.string.discover_season_specials)
						} else {
							stringResource(R.string.discover_season_number, season.seasonNumber)
						}
						val caption = season.episodeCount?.let {
							stringResource(R.string.discover_season_episodes, it)
						}
						ListButton(
							headingContent = { Text(label) },
							captionContent = caption?.let { { Text(it) } },
							trailingContent = { Checkbox(checked = selected) },
							onClick = {
								selectedSeasons = if (selected) {
									selectedSeasons - season.seasonNumber
								} else {
									selectedSeasons + season.seasonNumber
								}
							},
						)
					}
				}

				Button(
					onClick = {
						onConfirm(SeriesMonitorSelection.Seasons(selectedSeasons))
					},
					enabled = selectedSeasons.isNotEmpty(),
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(stringResource(R.string.discover_monitor_confirm))
				}
				Button(
					onClick = { customMode = false },
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(stringResource(R.string.lbl_cancel))
				}
			}
		}
	}
}

@Composable
private fun PresetButton(labelRes: Int, onClick: () -> Unit) {
	Button(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
	) {
		Text(stringResource(labelRes))
	}
}
