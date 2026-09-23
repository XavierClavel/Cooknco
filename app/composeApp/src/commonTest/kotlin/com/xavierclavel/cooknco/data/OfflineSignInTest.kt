package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.AuthApi
import com.xavierclavel.cooknco.network.NotificationApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.ContentType
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.UserInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.IOException
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * That a launch with no network stays signed in — and that one the server has refused does not.
 *
 * This is the whole of the bug the offline work started from. `getCurrentUser` cleared the
 * token on *any* failure, and a dropped connection is a failure, so a launch in a kitchen with
 * no signal deleted the session, landed on a login screen that offline can never be passed, and
 * took the account's cached language and units with it. Nothing had gone wrong.
 *
 * The three cases below are the three answers a `whoami` can produce, and they must stay told
 * apart.
 */
class OfflineSignInTest {

    private companion object {
        const val TOKEN = "session-token"
        val USER = UserInfo(
            id = 7,
            version = 1,
            username = "xavier",
            joinDate = 0,
            bio = "",
            recipesCount = 0,
            likesCount = 0,
            cookbooksCount = 0,
            followersCount = 0,
            followsCount = 0,
        )
    }

    private val storeFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-auth-${Random.nextLong()}.preferences_pb"
    private val offlineRoot = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-offline-${Random.nextLong()}"
    private val offlineStore = OfflineStore(offlineRoot)

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.delete(storeFile, mustExist = false)
        FileSystem.SYSTEM.deleteRecursively(offlineRoot, mustExist = false)
    }

    @Test
    fun a_session_the_server_confirms_is_remembered_for_next_time() = runTest {
        val tokens = tokenStore()
        tokens.saveToken(TOKEN)
        val repository = repositoryOver(tokens) { respondUser() }

        assertEquals(USER, repository.getCurrentUser())

        assertEquals(USER, offlineStore.readSession(), "so an offline launch has it")
    }

    /**
     * The one that matters. Nothing is known to be wrong with the session, so nothing is
     * touched — and the cook lands in the app rather than on a login screen they cannot pass.
     */
    @Test
    fun an_unreachable_server_leaves_the_session_alone() = runTest {
        val tokens = tokenStore()
        tokens.saveToken(TOKEN)
        offlineStore.writeSession(USER)
        val repository = repositoryOver(tokens) { throw IOException("offline") }

        assertEquals(USER, repository.getCurrentUser(), "the last account this device saw")
        assertEquals(TOKEN, tokens.tokenFlow.first(), "and the session it saw it under")
    }

    /**
     * The opposite, and the case the original code was written for: the server answered, and
     * what it answered was no. An expired or revoked session is dead and everything goes with
     * it — including the recipes, which are somebody's and no longer reachable.
     */
    @Test
    fun a_refused_session_is_cleared_along_with_the_store() = runTest {
        val tokens = tokenStore()
        tokens.saveToken(TOKEN)
        offlineStore.writeSession(USER)
        val repository = repositoryOver(tokens) { respond("", HttpStatusCode.Unauthorized) }

        assertNull(repository.getCurrentUser())

        assertNull(tokens.tokenFlow.first(), "a refused token must not survive")
        assertNull(offlineStore.readSession(), "nor what it could read")
    }

    /** A first launch offline has nothing to hand back, and must not invent one. */
    @Test
    fun an_unreachable_server_with_nothing_stored_is_still_signed_out() = runTest {
        val tokens = tokenStore()
        tokens.saveToken(TOKEN)
        val repository = repositoryOver(tokens) { throw IOException("offline") }

        assertNull(repository.getCurrentUser())
        assertEquals(TOKEN, tokens.tokenFlow.first(), "but the token is still not ours to drop")
    }

    @Test
    fun signing_out_forgets_the_recipes_as_well_as_the_session() = runTest {
        val tokens = tokenStore()
        tokens.saveToken(TOKEN)
        offlineStore.writeSession(USER)
        offlineStore.writeIndex(OfflineIndex(userId = USER.id, syncedAt = 1))
        val repository = repositoryOver(tokens) { respond("", HttpStatusCode.OK) }

        repository.logout()

        assertNull(offlineStore.readSession(), "a handset gets passed around")
        assertNull(offlineStore.readIndex())
    }

    /**
     * The banner has to be right before a screen has drawn.
     *
     * `whoami` is the first request of a launch; if it did not report what it found, the app
     * would look online until the cook happened to open something that failed — which on the
     * feed tab, where nothing falls back, is a plain error with no explanation next to it.
     */
    @Test
    fun the_launch_request_is_what_tells_the_app_it_is_offline() = runTest {
        val tokens = tokenStore()
        tokens.saveToken(TOKEN)
        offlineStore.writeSession(USER)

        repositoryOver(tokens) { respondUser() }.getCurrentUser()
        assertEquals(false, OfflineState.isOffline.value)

        repositoryOver(tokens) { throw IOException("offline") }.getCurrentUser()
        assertEquals(true, OfflineState.isOffline.value)
    }

    @Test
    fun a_stored_session_survives_a_restart_of_the_store() = runTest {
        offlineStore.writeSession(USER)
        assertNotNull(OfflineStore(offlineRoot).readSession())
    }

    private val store by lazy { createPreferencesDataStore(storeFile.toString()) }

    private fun tokenStore() = TokenDataStore(store)

    private fun MockRequestHandleScope.respondUser() = respond(
        content = """{"id":7,"version":1,"username":"xavier","joinDate":0,"bio":"","recipesCount":0,
            |"likesCount":0,"cookbooksCount":0,"followersCount":0,"followsCount":0}""".trimMargin(),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun repositoryOver(tokens: TokenDataStore, handler: MockRequestHandler): AuthRepository {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(ApiClient.json) }
        }
        return AuthRepository(
            authApi = AuthApi(client),
            tokenDataStore = tokens,
            pushRepository = PushRepository(NotificationApi(client), tokens),
            devicePreferences = DevicePreferences(store),
            offlineStore = offlineStore,
        )
    }
}
