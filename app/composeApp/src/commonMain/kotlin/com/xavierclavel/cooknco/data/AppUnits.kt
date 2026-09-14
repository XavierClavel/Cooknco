package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.UnitInfo
import kotlinx.coroutines.CoroutineScope
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
     * Restores the ladder the last session resolved, before anything asks the network.
     * Called once, from the app's entry point.
     */
    fun restore(preferences: DevicePreferences, scope: CoroutineScope) {
        scope.launch {
            preferences.unitSystem.first()?.let { set(AppUnitSystem.of(it)) }
        }
    }

    /** Adopts a ladder and remembers it. Called when settings load, and when one is picked. */
    fun set(system: AppUnitSystem, preferences: DevicePreferences? = null, scope: CoroutineScope? = null) {
        _system.value = system
        if (preferences != null && scope != null) {
            scope.launch { preferences.setUnitSystem(system.code) }
        }
    }

    /**
     * Replaces the packaged catalogue with the server's, once.
     *
     * Silent on failure, because [UnitRepository] already falls back to the packaged copy:
     * a unit the app does not know about yet is a unit nothing in an existing recipe uses.
     */
    fun refresh(repository: UnitRepository, scope: CoroutineScope) {
        scope.launch { _catalog.value = repository.getUnits() }
    }
}
