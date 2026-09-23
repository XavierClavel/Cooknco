package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.AuthApi
import com.xavierclavel.cooknco.network.NotificationApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.IOException
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What signing out has to guarantee, over a mocked transport.
 *
 * The device is never registered for push here — `currentPushToken()` has no Firebase to
 * answer from in a unit test — which is the same shape as a handset that never got a token,
 * and is why the only requests these assert on are the session's own.
 */
class AuthRepositoryLogoutTest {

    private companion object {
        const val TOKEN = "session-token"
    }

    /** A store per test: DataStore refuses two instances over one file. */
    private val storeFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-auth-${Random.nextLong()}.preferences_pb"

    private lateinit var engine: MockEngine

    private val offlineRoot = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-offline-${Random.nextLong()}"
    private val offlineStore = OfflineStore(offlineRoot)

    @AfterTest
    fun deleteStore() {
        FileSystem.SYSTEM.delete(storeFile, mustExist = false)
        FileSystem.SYSTEM.deleteRecursively(offlineRoot, mustExist = false)
    }

    @Test
    fun logout_ends_the_session_on_both_sides() = runTest {
        val tokens = tokenStore()
        tokens.saveToken(TOKEN)
        val repository = repositoryOver(tokens) { respond("", HttpStatusCode.OK) }

        val result = repository.logout()

        assertTrue(result.isSuccess)
        assertNull(tokens.tokenFlow.first(), "the token must not survive a sign-out")
        val call = engine.requestHistory.single { it.url.encodedPath.endsWith("/auth/logout") }
        assertEquals("Bearer $TOKEN", call.headers[HttpHeaders.Authorization])
    }

    /**
     * The one that matters. A logout the network refused used to leave the token on disk
     * while the UI went back to the login screen — so the next launch read it and signed
     * the user straight back into the session they had just left.
     */
    @Test
    fun logout_ends_the_local_session_even_when_the_server_cannot_be_reached() = runTest {
        val tokens = tokenStore()
        tokens.saveToken(TOKEN)
        val repository = repositoryOver(tokens) { throw IOException("offline") }

        val result = repository.logout()

        assertTrue(result.isSuccess, "an unreachable server is not a failed sign-out")
        assertNull(tokens.tokenFlow.first(), "the device stays signed in until the token goes")
    }

    /**
     * The account's language and units are cached on the handset so a relaunch draws in
     * them straight away — which is exactly why they cannot outlive the account. Left
     * behind, they were what the *next* account signed in and was shown, with nothing on
     * any screen saying so and nothing but opening settings putting it right.
     */
    @Test
    fun logout_forgets_what_the_account_read_in() = runTest {
        val tokens = tokenStore()
        tokens.saveToken(TOKEN)
        val preferences = devicePreferences()
        preferences.setUnitSystem(AppUnitSystem.IMPERIAL.code)
        preferences.setLanguage(AppLocale.EN.code)
        preferences.setPushEnabled(false)
        val repository = repositoryOver(tokens) { respond("", HttpStatusCode.OK) }

        assertTrue(repository.logout().isSuccess)

        assertNull(preferences.unitSystem.first(), "the ladder belongs to the account, not the phone")
        assertNull(preferences.language.first(), "and so does the language")
        assertEquals(AppUnitSystem.METRIC, AppUnits.system.value, "and neither survives in memory")
        assertEquals(
            false,
            preferences.pushEnabled.first(),
            "whether this handset buzzes is its own business, and stays",
        )
    }

    @Test
    fun logout_without_a_session_calls_nothing() = runTest {
        val tokens = tokenStore()
        val repository = repositoryOver(tokens) { respond("", HttpStatusCode.OK) }

        assertTrue(repository.logout().isSuccess)

        assertTrue(engine.requestHistory.isEmpty(), "nothing to tell the server about")
        assertNull(tokens.tokenFlow.first())
    }

    /** One store per test, read through both faces of it. DataStore refuses a second. */
    private val store by lazy { createPreferencesDataStore(storeFile.toString()) }

    private fun tokenStore() = TokenDataStore(store)

    private fun devicePreferences() = DevicePreferences(store)

    private fun repositoryOver(tokens: TokenDataStore, handler: MockRequestHandler): AuthRepository {
        engine = MockEngine(handler)
        val client = HttpClient(engine)
        return AuthRepository(
            authApi = AuthApi(client),
            tokenDataStore = tokens,
            pushRepository = PushRepository(NotificationApi(client), tokens),
            devicePreferences = devicePreferences(),
            offlineStore = offlineStore,
        )
    }
}
