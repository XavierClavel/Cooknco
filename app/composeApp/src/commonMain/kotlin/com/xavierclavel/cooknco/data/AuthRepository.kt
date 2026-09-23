package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.AuthApi
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.network.isOffline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore,
    private val pushRepository: PushRepository,
    private val devicePreferences: DevicePreferences,
    private val offlineStore: OfflineStore,
) {
    val tokenFlow: Flow<String?> = tokenDataStore.tokenFlow

    suspend fun login(email: String, password: String): Result<UserInfo> = runCatching {
        val session = authApi.login(email, password)
        tokenDataStore.saveToken(session.token)
        authApi.whoami(session.token).also { offlineStore.writeSession(it) }
    }

    suspend fun signup(username: String, email: String, password: String): Result<Unit> = runCatching {
        authApi.signup(username, email, password)
        Unit
    }

    /**
     * Ends the session on this device, and on the server if it can be reached.
     *
     * The server call is best effort on purpose: its session expires on its own, but the
     * token on disk does not. Letting a failed request skip [TokenDataStore.clearToken]
     * would leave the user on the login screen and signed straight back in at the next
     * launch — a sign-out that only looked like one.
     */
    suspend fun logout(): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        if (token != null) {
            // Before the session goes: it is an authenticated call, and an account left
            // registered would keep pushing to a handset somebody else may now be holding.
            pushRepository.unregisterCurrentDevice()
            runCatching { authApi.logout(token) }
        }
        tokenDataStore.clearToken()
        // And what that account read in. It is cached on the handset so a relaunch draws
        // the right units and words straight away, which is exactly what would otherwise
        // hand them to whoever signs in next. See [AccountSettings.forget].
        AccountSettings.forget(devicePreferences)
        // And their recipes. A handset gets passed around, and this is somebody's whole
        // collection sitting in a directory — the same rule as above, over very much more.
        offlineStore.clear()
    }

    /**
     * Who is signed in, from the server if it answers and from this device if it does not.
     *
     * The distinction is the whole of it, and getting it wrong is what this used to do. The
     * token was cleared on *any* failure — and a dropped connection is a failure — so a launch
     * with no network deleted the session, landed on a login screen that offline can never be
     * passed, and took the account's cached language and units with it. The cook was then
     * signed out until they found signal, over nothing having gone wrong.
     *
     * So the three cases are told apart:
     *
     * - the server answers: that is the truth, and it is written down for next time;
     * - the server refuses ([com.xavierclavel.cooknco.network.ApiException]): the session
     *   really is dead — expired, revoked, the account deleted — and everything goes, which is
     *   what the original code was written for;
     * - the server cannot be reached: the session is not known to be anything, so nothing is
     *   touched and the last account this device saw is handed back.
     *
     * A device that has never reached the server has nothing to hand back and answers null, as
     * before — there is no offline first launch, and there is nothing this could invent.
     */
    suspend fun getCurrentUser(): UserInfo? {
        val token = tokenDataStore.tokenFlow.first() ?: return null
        // Reported as well as acted on: this is the first request of the launch, so it is what
        // decides whether the banner is up before a single screen has drawn. Without it the
        // app would look online until the cook happened to open something.
        return OfflineState.observe(runCatching { authApi.whoami(token) })
            .onSuccess { offlineStore.writeSession(it) }
            .getOrElse { error ->
                if (error.isOffline) return offlineStore.readSession()
                // The server answered, and what it answered was no.
                tokenDataStore.clearToken()
                offlineStore.clear()
                null
            }
    }

    suspend fun saveOAuthToken(token: String): Result<UserInfo> = runCatching {
        tokenDataStore.saveToken(token)
        authApi.whoami(token).also { offlineStore.writeSession(it) }
    }
}
