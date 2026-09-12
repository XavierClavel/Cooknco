package com.xavierclavel.cooknco.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.AuthRepository
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.stringsFor
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.ApiException
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.platform.UrlOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val user: UserInfo) : AuthState()
    data object Unauthenticated : AuthState()
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class SignupUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _signupState = MutableStateFlow(SignupUiState())
    val signupState: StateFlow<SignupUiState> = _signupState.asStateFlow()

    private val _signupSuccess = MutableStateFlow(false)
    val signupSuccess: StateFlow<Boolean> = _signupSuccess.asStateFlow()

    /**
     * Whether a sign-out is in flight.
     *
     * Worth showing: [AuthRepository.logout] unregisters the device and drops the server
     * session before it returns, and no timeout is configured on the client, so on a bad
     * network the tap can sit there for as long as the platform's socket timeout.
     */
    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _authState.value = if (user != null) AuthState.Authenticated(user)
            else AuthState.Unauthenticated
        }
    }

    fun login() {
        val state = _loginState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _loginState.update { it.copy(error = copy().fillInAllFields) }
            return
        }
        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, error = null) }
            authRepository.login(state.email.trim(), state.password)
                .onSuccess { user ->
                    _loginState.update { it.copy(isLoading = false) }
                    _authState.value = AuthState.Authenticated(user)
                }
                .onFailure { error ->
                    _loginState.update { it.copy(isLoading = false, error = parseError(error)) }
                }
        }
    }

    fun signup() {
        val state = _signupState.value
        when {
            state.username.isBlank() || state.email.isBlank() || state.password.isBlank() ->
                _signupState.update { it.copy(error = copy().fillInAllFields) }
            state.password.length < 8 ->
                _signupState.update { it.copy(error = copy().passwordMinEight) }
            state.password != state.confirmPassword ->
                _signupState.update { it.copy(error = copy().passwordsDoNotMatch) }
            else -> viewModelScope.launch {
                _signupState.update { it.copy(isLoading = true, error = null) }
                authRepository.signup(state.username.trim(), state.email.trim(), state.password)
                    .onSuccess {
                        _signupState.update { it.copy(isLoading = false) }
                        _signupSuccess.value = true
                    }
                    .onFailure { error ->
                        _signupState.update { it.copy(isLoading = false, error = parseError(error)) }
                    }
            }
        }
    }

    fun logout() {
        if (_isLoggingOut.value) return
        viewModelScope.launch {
            _isLoggingOut.value = true
            // Unauthenticated either way: logout only fails at the parts that are best
            // effort anyway, and the local session is gone by the time it returns.
            authRepository.logout()
            _isLoggingOut.value = false
            _loginState.value = LoginUiState()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun handleOAuthToken(token: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.saveOAuthToken(token)
                .onSuccess { user -> _authState.value = AuthState.Authenticated(user) }
                .onFailure { _authState.value = AuthState.Unauthenticated }
        }
    }

    fun loginWithGoogle(urlOpener: UrlOpener) {
        urlOpener.open("${ApiClient.BASE_URL}/auth/login-oauth-google?redirect=app")
    }

    fun updateLoginEmail(value: String) = _loginState.update { it.copy(email = value, error = null) }
    fun updateLoginPassword(value: String) = _loginState.update { it.copy(password = value, error = null) }
    fun toggleLoginPasswordVisibility() = _loginState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun updateSignupUsername(value: String) = _signupState.update { it.copy(username = value, error = null) }
    fun updateSignupEmail(value: String) = _signupState.update { it.copy(email = value, error = null) }
    fun updateSignupPassword(value: String) = _signupState.update { it.copy(password = value, error = null) }
    fun updateSignupConfirmPassword(value: String) = _signupState.update { it.copy(confirmPassword = value, error = null) }
    fun toggleSignupPasswordVisibility() = _signupState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    fun toggleSignupConfirmPasswordVisibility() = _signupState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }

    fun resetSignupSuccess() { _signupSuccess.value = false }

    /**
     * The copy for the language the app is in right now.
     *
     * A view model has no composition to read [com.xavierclavel.cooknco.ui.i18n.LocalStrings]
     * from, so it asks [AppLanguage] directly. Read at the moment the message is produced
     * rather than held: an error written before a language change keeps the words it was
     * written in, which is the right answer for something the user is looking at.
     */
    private fun copy(): Strings = stringsFor(AppLanguage.current.value)

    private fun parseError(throwable: Throwable): String {
        val s = copy()
        val body = (throwable as? ApiException)?.body ?: throwable.message ?: s.anErrorOccurred
        // The backend answers with a cause rather than a sentence, which is what makes this
        // translatable at all — anything unrecognised is passed through as it came.
        return when {
            "INVALID_MAIL_OR_PASSWORD" in body -> s.invalidEmailOrPassword
            "USER_NOT_VERIFIED" in body -> s.verifyEmailFirst
            "USERNAME_ALREADY_USED" in body -> s.usernameAlreadyTaken
            "MAIL_ALREADY_USED" in body -> s.emailAlreadyRegistered
            "OAUTH_ONLY" in body -> s.accountUsesGoogle
            else -> body
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(AppGraph.authRepository) }
        }
    }
}
