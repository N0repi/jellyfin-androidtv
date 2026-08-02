package org.jellyfin.androidtv.ui.settings.screen.integration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.integration.arr.IntegrationConnectionTester
import org.jellyfin.androidtv.integration.arr.IntegrationService
import org.jellyfin.androidtv.preference.IntegrationPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.settings.composable.SettingsTextField
import org.koin.compose.koinInject

@Composable
fun SettingsIntegrationsScreen() {
	val preferences = koinInject<IntegrationPreferences>()
	val connectionTester = koinInject<IntegrationConnectionTester>()

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_integrations)) },
				captionContent = { Text(stringResource(R.string.pref_integrations_description)) },
			)
		}

		item {
			var enabled by rememberPreference(preferences, IntegrationPreferences.sonarrEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_sonarr)) },
				captionContent = { Text(stringResource(R.string.pref_sonarr_description)) },
				trailingContent = { Checkbox(checked = enabled) },
				onClick = { enabled = !enabled },
			)
		}
		item {
			var host by rememberPreference(preferences, IntegrationPreferences.sonarrHost)
			SettingsTextField(
				value = host,
				onValueChange = { host = it },
				label = stringResource(R.string.pref_host),
			)
		}
		item {
			var port by rememberPreference(preferences, IntegrationPreferences.sonarrPort)
			PortTextField(port = port, onPortChange = { port = it })
		}
		item {
			var apiKey by rememberPreference(preferences, IntegrationPreferences.sonarrApiKey)
			SettingsTextField(
				value = apiKey,
				onValueChange = { apiKey = it },
				label = stringResource(R.string.pref_api_key),
				isSecret = true,
			)
		}
		item {
			ConnectionTestButton(connectionTester, IntegrationService.SONARR)
		}

		item {
			var enabled by rememberPreference(preferences, IntegrationPreferences.radarrEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_radarr)) },
				captionContent = { Text(stringResource(R.string.pref_radarr_description)) },
				trailingContent = { Checkbox(checked = enabled) },
				onClick = { enabled = !enabled },
			)
		}
		item {
			var host by rememberPreference(preferences, IntegrationPreferences.radarrHost)
			SettingsTextField(
				value = host,
				onValueChange = { host = it },
				label = stringResource(R.string.pref_host),
			)
		}
		item {
			var port by rememberPreference(preferences, IntegrationPreferences.radarrPort)
			PortTextField(port = port, onPortChange = { port = it })
		}
		item {
			var apiKey by rememberPreference(preferences, IntegrationPreferences.radarrApiKey)
			SettingsTextField(
				value = apiKey,
				onValueChange = { apiKey = it },
				label = stringResource(R.string.pref_api_key),
				isSecret = true,
			)
		}
		item {
			ConnectionTestButton(connectionTester, IntegrationService.RADARR)
		}

		item {
			var enabled by rememberPreference(preferences, IntegrationPreferences.qbittorrentEnabled)
			ListButton(
				headingContent = { Text(stringResource(R.string.pref_qbittorrent)) },
				captionContent = { Text(stringResource(R.string.pref_qbittorrent_description)) },
				trailingContent = { Checkbox(checked = enabled) },
				onClick = { enabled = !enabled },
			)
		}
		item {
			var host by rememberPreference(preferences, IntegrationPreferences.qbittorrentHost)
			SettingsTextField(
				value = host,
				onValueChange = { host = it },
				label = stringResource(R.string.pref_host),
			)
		}
		item {
			var port by rememberPreference(preferences, IntegrationPreferences.qbittorrentPort)
			PortTextField(port = port, onPortChange = { port = it })
		}
		item {
			var username by rememberPreference(preferences, IntegrationPreferences.qbittorrentUsername)
			SettingsTextField(
				value = username,
				onValueChange = { username = it },
				label = stringResource(R.string.pref_username),
			)
		}
		item {
			var password by rememberPreference(preferences, IntegrationPreferences.qbittorrentPassword)
			SettingsTextField(
				value = password,
				onValueChange = { password = it },
				label = stringResource(R.string.pref_password),
				isSecret = true,
			)
		}
		item {
			ConnectionTestButton(connectionTester, IntegrationService.QBITTORRENT)
		}
	}
}

@Composable
private fun ConnectionTestButton(
	connectionTester: IntegrationConnectionTester,
	service: IntegrationService,
) {
	val scope = rememberCoroutineScope()
	val testingMessage = stringResource(R.string.pref_connection_testing)
	var testing by remember { mutableStateOf(false) }
	var resultMessage by remember { mutableStateOf<String?>(null) }

	ListButton(
		headingContent = { Text(stringResource(R.string.pref_connection_test)) },
		captionContent = {
			val message = resultMessage
			if (message != null) Text(message)
		},
		enabled = !testing,
		onClick = {
			testing = true
			resultMessage = testingMessage
			scope.launch {
				resultMessage = connectionTester.test(service).message
				testing = false
			}
		},
	)
}

@Composable
private fun PortTextField(
	port: Int,
	onPortChange: (Int) -> Unit,
) {
	var text by remember(port) { mutableStateOf(port.toString()) }
	SettingsTextField(
		value = text,
		onValueChange = { value ->
			if (value.all(Char::isDigit) && value.length <= 5) {
				text = value
				value.toIntOrNull()?.takeIf { it in 1..65535 }?.let(onPortChange)
			}
		},
		label = stringResource(R.string.pref_port),
		keyboardType = KeyboardType.Number,
	)
}
