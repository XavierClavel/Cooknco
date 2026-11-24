package com.xavierclavel.cooknco.ui.auth

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.AuthRepository
import com.xavierclavel.cooknco.data.DevicePreferences
import com.xavierclavel.cooknco.data.PushRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.createPreferencesDataStore
import com.xavierclavel.cooknco.data.deleteTestStore
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.AuthApi
import com.xavierclavel.cooknco.network.NotificationApi
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.stringsFor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the sign-in and sign-up screens say when the backend refuses.
 *
 * The bodies are written the way the backend sends them — the cause's lowercase key and
 * nothing else (`UnauthorizedCause.USER_NOT_VERIFIED.key`) — because that is the whole point:
 * matched against anything else, a refusal reaches the screen as the bare key.
 */
class AuthErrorMessagesTest {

    private val strings: Strings get() = stringsFor(AppLanguage.current.value)

    @Test
    fun an_unverified_account_is_told_to_verify_its_email() =
        loginRefusedWith(HttpStatusCode.Unauthorized, "user_not_verified") { assertEquals(strings.verifyEmailFirst, it) }

    @Test
    fun a_wrong_password_is_told_so() =
        loginRefusedWith(HttpStatusCode.Unauthorized, "invalid_mail_or_password") { assertEquals(strings.invalidEmailOrPassword, it) }

    @Test
    fun a_google_account_is_sent_to_the_google_button() =
        loginRefusedWith(HttpStatusCode.BadRequest, "oauth_only") { assertEquals(strings.accountUsesGoogle, it) }

    @Test
    fun a_taken_username_is_told_so() =
        signupRefusedWith("username_already_used") { assertEquals(strings.usernameAlreadyTaken, it) }

    @Test
    fun a_registered_email_is_told_so() =
        signupRefusedWith("mail_already_used") { assertEquals(strings.emailAlreadyRegistered, it) }

    private fun loginRefusedWith(status: HttpStatusCode, body: String, check: (String?) -> Unit) =
        authTest(status, body) { viewModel ->
            viewModel.updateLoginEmail("cook@example.com")
            viewModel.updateLoginPassword("password1")
            viewModel.login()
            check(viewModel.loginState.first { !it.isLoading && it.error != null }.error)
        }

    private fun signupRefusedWith(body: String, check: (String?) -> Unit) =
        authTest(HttpStatusCode.BadRequest, body) { viewModel ->
            viewModel.updateSignupUsername("cook")
            viewModel.updateSignupEmail("cook@example.com")
            viewModel.updateSignupPassword("password1")
            viewModel.updateSignupConfirmPassword("password1")
            viewModel.signup()
            check(viewModel.signupState.first { !it.isLoading && it.error != null }.error)
        }

    private fun authTest(status: HttpStatusCode, body: String, test: suspend (AuthViewModel) -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val storeFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cooknco-auth-errors-${Random.nextLong()}.preferences_pb"
        val store = createPreferencesDataStore(storeFile.toString())
        val tokens = TokenDataStore(store)
        val (offlineStore, offlineRoot) = testOfflineStore()
        val client = HttpClient(MockEngine { respond(body, status) }) {
            install(ContentNegotiation) { json(ApiClient.json) }
        }
        val viewModel = AuthViewModel(
            AuthRepository(
                authApi = AuthApi(client),
                tokenDataStore = tokens,
                pushRepository = PushRepository(NotificationApi(client), tokens),
                devicePreferences = DevicePreferences(store),
                offlineStore = offlineStore,
            ),
        )
        try {
            test(viewModel)
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
            FileSystem.SYSTEM.delete(storeFile, mustExist = false)
            deleteTestStore(offlineRoot)
        }
    }
}
