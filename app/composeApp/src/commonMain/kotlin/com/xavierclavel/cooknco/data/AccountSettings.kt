package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.UserSettingsDTO

/**
 * What the account says the app reads in — its language and its ladder — applied wherever
 * the app learns them.
 *
 * One rule in one place, because the app learns them from two directions: the settings
 * screen, which has just asked for the whole object anyway, and [sync], which asks on every
 * sign-in. It used to be the settings screen alone, and that is the whole of the bug this
 * exists to close: the ladder was cached on the handset ([DevicePreferences]) and never
 * reconciled with the account unless that one screen was opened, so a choice made from the
 * web, or on another phone, or by the person who used this one before, went on being drawn
 * indefinitely — and opening settings to check silently repaired it, which is why it read
 * as "it showed imperial once".
 *
 * The web does the same thing, once per session, in `frontend/src/scripts/unitSystem.ts`.
 */
object AccountSettings {

    /**
     * Asks the account what it reads in, and adopts the answer.
     *
     * Silent on failure and on a missing session: the cache restored at launch is a
     * perfectly good answer for a launch that cannot reach the server, and no screen is
     * worth not drawing over this.
     */
    suspend fun sync(userRepository: UserRepository, preferences: DevicePreferences) {
        userRepository.getSettings().onSuccess { adopt(it, preferences) }
    }

    /**
     * Adopts the settings just read, and remembers them for the next launch.
     *
     * Each field only when the server actually named one: `locale` is null until something
     * has told the backend a language, and a null there means "nothing has ever said"
     * rather than "English" — the handset's own language is the better answer for as long
     * as that is true. `unitSystem` is non-null from any backend that knows about ladders
     * at all, and null from one that does not, which is not an account preference either.
     */
    fun adopt(settings: UserSettingsDTO, preferences: DevicePreferences) {
        AppLocale.entries.firstOrNull { it.code.equals(settings.locale, ignoreCase = true) }
            ?.let { AppLanguage.set(it, preferences) }
        settings.unitSystem?.let { AppUnits.set(AppUnitSystem.of(it), preferences) }
    }

    /**
     * Drops what the account that just signed out read in, from memory and from the disk.
     *
     * The cache belongs to the account rather than to the handset — unlike push, which is
     * the phone's own business and stays. Leaving it behind is what let the next account
     * sign in and be shown the previous one's units, with nothing on any screen to say so.
     */
    suspend fun forget(preferences: DevicePreferences) {
        AppLanguage.forget()
        AppUnits.forget()
        preferences.clearAccountSettings()
    }
}
