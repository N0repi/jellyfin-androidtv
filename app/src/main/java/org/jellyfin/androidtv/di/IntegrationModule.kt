package org.jellyfin.androidtv.di

import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.jellyfin.androidtv.integration.media.ArrClient
import org.jellyfin.androidtv.integration.media.MediaIntegrationRepository
import org.jellyfin.androidtv.integration.media.QBittorrentClient
import org.jellyfin.androidtv.ui.discover.DiscoverViewModel
import org.jellyfin.androidtv.ui.torrents.TorrentActivityViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

private val integrationJson = named("integrationJson")
private val arrHttpClient = named("arrHttpClient")
private val qBittorrentHttpClient = named("qBittorrentHttpClient")

val integrationModule = module {
	single(integrationJson) {
		Json {
			ignoreUnknownKeys = true
			isLenient = true
		}
	}

	single(arrHttpClient) {
		OkHttpClient.Builder()
			.connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.build()
	}

	single(qBittorrentHttpClient) {
		OkHttpClient.Builder()
			.connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.cookieJar(InMemoryCookieJar())
			.build()
	}

	single { ArrClient(get(arrHttpClient), get(integrationJson)) }
	single { QBittorrentClient(get(qBittorrentHttpClient), get(integrationJson)) }
	single { MediaIntegrationRepository(get(), get(), get()) }

	viewModel { DiscoverViewModel(get()) }
	viewModel { TorrentActivityViewModel(get()) }
}

private const val HTTP_TIMEOUT_SECONDS = 15L

/**
 * A minimal per-host in-memory cookie store, sufficient to persist the qBittorrent session cookie
 * for the lifetime of the process.
 */
private class InMemoryCookieJar : CookieJar {
	private val store = mutableMapOf<String, List<Cookie>>()

	@Synchronized
	override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
		store[url.host] = cookies
	}

	@Synchronized
	override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()
}
