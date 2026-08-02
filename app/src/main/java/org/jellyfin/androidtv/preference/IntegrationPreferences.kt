package org.jellyfin.androidtv.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.preference.store.SharedPreferenceStore
import org.jellyfin.preference.stringPreference
import timber.log.Timber

/**
 * Integration settings. Sensitive values (API keys, qBittorrent credentials) and the rest of this
 * store live in [EncryptedSharedPreferences], backed by an Android Keystore [MasterKey].
 */
class IntegrationPreferences(context: Context) : SharedPreferenceStore(
	sharedPreferences = createSecurePreferences(context)
) {
	init {
		migrateFromPlaintext(context)
	}

	private fun migrateFromPlaintext(context: Context) {
		val plaintext = context.getSharedPreferences(PLAINTEXT_PREFERENCES_NAME, Context.MODE_PRIVATE)
		if (plaintext.all.isEmpty()) return

		Timber.i("Migrating integration preferences into encrypted storage")
		val editor = sharedPreferences.edit()
		for ((key, value) in plaintext.all) {
			when (value) {
				is String -> editor.putString(key, value)
				is Int -> editor.putInt(key, value)
				is Boolean -> editor.putBoolean(key, value)
				is Long -> editor.putLong(key, value)
				is Float -> editor.putFloat(key, value)
			}
		}
		editor.apply()
		plaintext.edit().clear().apply()
	}

	companion object {
		const val PLAINTEXT_PREFERENCES_NAME = "integrations"
		const val SECURE_PREFERENCES_NAME = "integrations_secure"

		var sonarrEnabled = booleanPreference("sonarr_enabled", false)
		var sonarrHost = stringPreference("sonarr_host", "")
		var sonarrPort = intPreference("sonarr_port", 8989)
		var sonarrApiKey = stringPreference("sonarr_api_key", "")

		var radarrEnabled = booleanPreference("radarr_enabled", false)
		var radarrHost = stringPreference("radarr_host", "")
		var radarrPort = intPreference("radarr_port", 7878)
		var radarrApiKey = stringPreference("radarr_api_key", "")

		var qbittorrentEnabled = booleanPreference("qbittorrent_enabled", false)
		var qbittorrentHost = stringPreference("qbittorrent_host", "")
		var qbittorrentPort = intPreference("qbittorrent_port", 8080)
		var qbittorrentUsername = stringPreference("qbittorrent_username", "")
		var qbittorrentPassword = stringPreference("qbittorrent_password", "")

		private fun createSecurePreferences(context: Context): SharedPreferences {
			val masterKey = MasterKey.Builder(context)
				.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
				.build()

			return EncryptedSharedPreferences.create(
				context,
				SECURE_PREFERENCES_NAME,
				masterKey,
				EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
				EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
			)
		}
	}
}
