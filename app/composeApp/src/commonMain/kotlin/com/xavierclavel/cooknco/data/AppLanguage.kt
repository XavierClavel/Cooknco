package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.platform.deviceLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A language the app speaks. The backend's `Locale` has exactly these two. */
enum class AppLocale(val code: String) {
    FR("FR"),
    EN("EN"),
    ;

    companion object {
        /** Anything we do not speak reads EN, the same fallback the rest of the product uses. */
        fun of(code: String?): AppLocale =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
    }
}

/**
 * What language the app is written in right now.
 *
 * Held here rather than read from the platform at each call site, because it is not only the
 * platform's to decide: an account that has chosen a language in settings is written to in
 * that language everywhere else in the product, and the app it is reading is no exception.
 * The order is the same as the backend's — the account decides, the handset reports:
 *
 * 1. what the account carries (`UserSettingsDTO.locale`), once it is known;
 * 2. otherwise the handset's ([deviceLocale]).
 *
 * The last resolved value is kept in [DevicePreferences] so a relaunch opens in the right
 * language immediately, rather than in the handset's until the settings request comes back.
 *
 * It also drives what the API is asked for *content* in ([ApiClient.locale] — ingredient
 * names, exports). Those used to be pinned to EN because the app's own copy was English
 * only; now that it is not, asking for French ingredient names inside a French screen is
 * simply right.
 */
object AppLanguage {

    private val _current = MutableStateFlow(AppLocale.of(deviceLocale))
    val current: StateFlow<AppLocale> = _current.asStateFlow()

    init {
        ApiClient.locale = _current.value.code
    }

    /**
     * Restores the language the last session resolved, before anything asks the network.
     * Called once, from the app's entry point.
     */
    fun restore(preferences: DevicePreferences, scope: CoroutineScope) {
        scope.launch {
            preferences.language.first()?.let { set(AppLocale.of(it)) }
        }
    }

    /** Adopts a language and remembers it. Called when settings load, and when one is picked. */
    fun set(locale: AppLocale, preferences: DevicePreferences? = null, scope: CoroutineScope? = null) {
        _current.value = locale
        ApiClient.locale = locale.code
        if (preferences != null && scope != null) {
            scope.launch { preferences.setLanguage(locale.code) }
        }
    }
}
