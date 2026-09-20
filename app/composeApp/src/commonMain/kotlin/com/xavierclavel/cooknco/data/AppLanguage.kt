package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.platform.deviceLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
     * Whether the language in front of us came from the account rather than from the cache.
     * The same guard, and for the same reason, as the one [AppUnits] keeps: the restore and
     * the account's own answer race, and the account's is the one that must win either way.
     */
    private var resolved = false

    /**
     * Writes the remembered language back to disk, on a scope that outlives every screen,
     * exactly as [AppUnits] does — a write on the settings screen's own scope is one the
     * screen can cancel by closing.
     */
    private val persistence = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Restores the language the last session resolved, before anything asks the network.
     * Called once, from the app's entry point, and never over what the account has already
     * said.
     */
    suspend fun restore(preferences: DevicePreferences) {
        val cached = preferences.language.first() ?: return
        if (!resolved) apply(AppLocale.of(cached))
    }

    /** Adopts a language and remembers it. Called when settings load, and when one is picked. */
    fun set(locale: AppLocale, preferences: DevicePreferences? = null) {
        resolved = true
        apply(locale)
        if (preferences != null) {
            persistence.launch { preferences.setLanguage(locale.code) }
        }
    }

    /**
     * Drops what the account that just signed out was written in, back to the handset's —
     * which is what an account with no language of its own reads in anyway. See
     * [AccountSettings.forget].
     */
    fun forget() {
        resolved = false
        apply(AppLocale.of(deviceLocale))
    }

    private fun apply(locale: AppLocale) {
        _current.value = locale
        ApiClient.locale = locale.code
    }
}
