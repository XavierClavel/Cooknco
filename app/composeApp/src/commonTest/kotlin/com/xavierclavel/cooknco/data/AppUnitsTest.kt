package com.xavierclavel.cooknco.data

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which answer wins when the disk and the account disagree about the ladder.
 *
 * They disagree more often than it sounds: the cached one is what the *last* session
 * resolved, and it survives a relaunch, a language change made from the web, and — until
 * [AccountSettings.forget] — the account that set it. It is a head start and nothing more,
 * so the one rule worth holding is that it never lands on top of an account's own answer.
 *
 * Each test writes the cache exactly once: DataStore renames a temporary file over the real
 * one to save, which Windows refuses when the file already exists — see the rest of
 * [AuthRepositoryLogoutTest], whose two token tests are red on Windows for that reason and
 * green everywhere else.
 */
class AppUnitsTest {

    /** A store per test: DataStore refuses two instances over one file. */
    private val storeFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-units-${Random.nextLong()}.preferences_pb"

    private val preferences by lazy { DevicePreferences(createPreferencesDataStore(storeFile.toString())) }

    /** The ladder is a singleton, so each test says where it starts from. */
    @BeforeTest
    fun forget() = AppUnits.forget()

    @AfterTest
    fun deleteStore() {
        AppUnits.forget()
        FileSystem.SYSTEM.delete(storeFile, mustExist = false)
    }

    @Test
    fun restores_the_ladder_the_last_session_resolved() = runTest {
        preferences.setUnitSystem(AppUnitSystem.IMPERIAL.code)

        AppUnits.restore(preferences)

        assertEquals(AppUnitSystem.IMPERIAL, AppUnits.system.value)
    }

    @Test
    fun a_launch_with_nothing_cached_reads_metric() = runTest {
        AppUnits.restore(preferences)

        assertEquals(AppUnitSystem.METRIC, AppUnits.system.value)
    }

    /**
     * The one that matters. The restore reads a disk and the sign-in sync reads a network,
     * so on a warm start the account can answer first — and the cache landing on top of it
     * would put the app back on the ladder the account has just stopped reading on.
     */
    @Test
    fun the_cache_never_lands_on_top_of_what_the_account_said() = runTest {
        preferences.setUnitSystem(AppUnitSystem.IMPERIAL.code)
        AppUnits.set(AppUnitSystem.METRIC)

        AppUnits.restore(preferences)

        assertEquals(AppUnitSystem.METRIC, AppUnits.system.value)
    }
}
