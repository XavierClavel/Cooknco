package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.UnitInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A ladder amounts are read on. The backend's `UnitSystem` has exactly these two. */
enum class AppUnitSystem(val code: String) {
    METRIC("METRIC"),
    IMPERIAL("IMPERIAL"),
    ;

    companion object {
        /** Anything we do not recognise reads metric, the same fallback the backend uses. */
        fun of(code: String?): AppUnitSystem =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: METRIC
    }
}

/**
 * What units the app shows amounts in, and the catalogue it converts them with.
 *
 * The same shape as [AppLanguage] and for the same reason: this is the account's to decide,
 * every screen that prints a number reads it, and a screen reading it from wherever it
 * happened to be asked would be a second answer waiting to disagree with the first.
 *
 * Unlike a language, nothing *reports* a ladder — a handset has no preferred one to tell us
 * — so there is no adoption rule here. There is only what the account saved, and metric
 * until it has said. That value is cached in [DevicePreferences] so a relaunch draws the
 * right units immediately rather than in metric until the settings request comes back.
 *
 * The cache is a head start, never the answer: [AccountSettings.sync] asks the account on
 * every sign-in, and [AccountSettings.forget] drops it on the way out. A cache that was
 * only ever refreshed by opening the settings screen kept drawing the ladder of whoever
 * used the handset last, or the one this account read on before it was changed from the
 * web — and reading it back on the settings screen repaired it, so the one screen that
 * could show the fault was the one screen that could not.
 *
 * [catalog] is what [com.xavierclavel.cooknco.ui.recipe.convertToPreferred] converts with,
 * seeded with [UnitRepository.DEFAULT_UNITS] so it is never empty and never blocks a screen.
 * Holding it here rather than fetching it per screen keeps one copy in front of the whole
 * app, and it is the server's copy as soon as the server answers.
 */
object AppUnits {

    private val _system = MutableStateFlow(AppUnitSystem.METRIC)
    val system: StateFlow<AppUnitSystem> = _system.asStateFlow()

    private val _catalog = MutableStateFlow(UnitRepository.DEFAULT_UNITS)
    val catalog: StateFlow<List<UnitInfo>> = _catalog.asStateFlow()

    /**
     * Whether the ladder in front of us came from the account rather than from the cache.
     *
     * [restore] and [sync] race by nature — one reads a disk, the other a network — and the
     * account's answer is the one that must survive whichever order they land in.
     */
    private var resolved = false

    /**
     * Writes the remembered ladder back to disk, on a scope that outlives every screen.
     *
     * A ladder picked in settings used to be persisted on the settings screen's own scope,
     * so leaving the screen in the same breath could cancel the write: the app read on the
     * new ladder until it was relaunched, and on the old one for ever after.
     */
    private val persistence = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Restores the ladder the last session resolved, before anything asks the network.
     * Called once, from the app's entry point.
     *
     * Never over a ladder the account has already given us: on a warm start the settings
     * request can answer before the disk does.
     */
    suspend fun restore(preferences: DevicePreferences) {
        val cached = preferences.unitSystem.first() ?: return
        if (!resolved) _system.value = AppUnitSystem.of(cached)
    }

    /** Adopts a ladder and remembers it. Called when settings load, and when one is picked. */
    fun set(system: AppUnitSystem, preferences: DevicePreferences? = null) {
        resolved = true
        _system.value = system
        if (preferences != null) {
            persistence.launch { preferences.setUnitSystem(system.code) }
        }
    }

    /**
     * Drops what the account that just signed out read on. See [AccountSettings.forget] —
     * the next account is asked for its own, and reads metric until it answers rather than
     * on the ladder of the person who was holding the phone before.
     */
    fun forget() {
        resolved = false
        _system.value = AppUnitSystem.METRIC
    }

    /**
     * Replaces the packaged catalogue with the server's, once.
     *
     * Silent on failure, because [UnitRepository] already falls back to the packaged copy:
     * a unit the app does not know about yet is a unit nothing in an existing recipe uses.
     */
    suspend fun refresh(repository: UnitRepository) {
        _catalog.value = repository.getUnits()
    }
}
