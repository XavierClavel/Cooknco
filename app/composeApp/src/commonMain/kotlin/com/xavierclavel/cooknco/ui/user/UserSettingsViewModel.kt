package com.xavierclavel.cooknco.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.AccountSettings
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.AppUnitSystem
import com.xavierclavel.cooknco.data.AppUnits
import com.xavierclavel.cooknco.data.AppLocale
import com.xavierclavel.cooknco.data.DevicePreferences
import com.xavierclavel.cooknco.data.PushRepository
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.UserSettingsDTO
import com.xavierclavel.cooknco.platform.deviceLocale
import com.xavierclavel.cooknco.ui.i18n.stringsFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The languages the backend can write to an account in — `shared.enums.Locale`. */
enum class AccountLocale(val code: String, val label: String) {
    FR("FR", "Français"),
    EN("EN", "English"),
}

data class UserSettingsUiState(
    val autoAcceptFollowRequests: Boolean = false,
    val isAccountPublic: Boolean = false,
    /**
     * The language the account actually carries, or null when nothing has ever told the
     * backend one. Null is not the same as FR or EN: a save must leave it alone rather than
     * write a language the user never chose — see [UserSettingsViewModel.save].
     */
    val accountLocale: AccountLocale? = null,
    /**
     * The ladder amounts are shown on. Not nullable the way [accountLocale] is: nothing
     * reports one, so there is no report to keep apart from a choice — the backend always
     * answers with a real value and metric is what it answers until the user says.
     */
    val unitSystem: AppUnitSystem = AppUnitSystem.METRIC,
    val mailNotificationsEnabled: Boolean = false,
    val pushEnabled: Boolean = true,
    /** How many MCP clients this account has approved — the badge on the "MCP access" row. */
    val mcpClientCount: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** True while `DELETE /user` is in flight — the confirmation stays up and spins on it. */
    val isDeletingAccount: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /**
     * What the switch shows: the account's language, or the handset's while it has none.
     * The backend would adopt exactly that on the next sign-in, so it is not a guess — but
     * it is only *shown*, never saved, until the user taps one.
     */
    val locale: AccountLocale
        get() = accountLocale ?: AccountLocale.entries.firstOrNull { it.code.equals(deviceLocale, ignoreCase = true) } ?: AccountLocale.EN
}

/**
 * Backs the settings screen. Every switch saves on the spot — the mockup has no Save button
 * — and each save sends the whole settings object, so one that is still loading cannot
 * overwrite another with a default.
 *
 * Push is the exception: it belongs to the handset rather than the account, so it is stored
 * in [DevicePreferences] and applied by registering or detaching this device.
 */
class UserSettingsViewModel(
    private val userRepo: UserRepository,
    private val devicePreferences: DevicePreferences,
    private val pushRepository: PushRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSettingsUiState())
    val uiState: StateFlow<UserSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Failing to count the MCP clients must not stop the settings loading: the row
            // is a link either way, and it says nothing rather than a wrong number.
            userRepo.getMcpClients().onSuccess { clients ->
                _uiState.update { it.copy(mcpClientCount = clients.size) }
            }
        }
        viewModelScope.launch {
            val pushEnabled = devicePreferences.pushEnabled.first()
            userRepo.getSettings()
                .onSuccess { settings ->
                    val locale = AccountLocale.entries.firstOrNull { it.code.equals(settings.locale, ignoreCase = true) }
                    // The account's language is the app's, and so is its ladder: what the
                    // account reads in is what every screen draws in, from this moment on.
                    // Through [AccountSettings] rather than applied here, because the same
                    // adoption runs at sign-in and two copies of it would drift.
                    AccountSettings.adopt(settings, devicePreferences)
                    val unitSystem = AppUnitSystem.of(settings.unitSystem)
                    _uiState.update {
                        it.copy(
                            autoAcceptFollowRequests = settings.autoAcceptFollowRequests,
                            isAccountPublic = settings.isAccountPublic,
                            accountLocale = locale,
                            unitSystem = unitSystem,
                            mailNotificationsEnabled = settings.mailNotificationsEnabled ?: false,
                            pushEnabled = pushEnabled,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, pushEnabled = pushEnabled, error = err.message) }
                }
        }
    }

    fun toggleAutoAccept() = updateAndSave { it.copy(autoAcceptFollowRequests = !it.autoAcceptFollowRequests) }

    fun toggleAccountPublic() = updateAndSave { it.copy(isAccountPublic = !it.isAccountPublic) }

    fun toggleMailNotifications() = updateAndSave { it.copy(mailNotificationsEnabled = !it.mailNotificationsEnabled) }

    /**
     * Applies a ladder at once, and puts it back if the account would not take it.
     *
     * The rollback is the point: what is applied here is remembered on the handset and read
     * by every recipe screen, so a save that did not land would otherwise leave the app
     * drawing on a ladder the account does not carry — for this session and every one
     * after it, since the cache outlives the process.
     */
    fun selectUnitSystem(system: AppUnitSystem) {
        val previous = AppUnits.system.value
        AppUnits.set(system, devicePreferences)
        updateAndSave(revert = {
            AppUnits.set(previous, devicePreferences)
            _uiState.update { it.copy(unitSystem = previous) }
        }) { it.copy(unitSystem = system) }
    }

    /** The same, for the language — which the app is written in as well as written to in. */
    fun selectLocale(locale: AccountLocale) {
        val previous = AppLanguage.current.value
        val previousAccountLocale = _uiState.value.accountLocale
        AppLanguage.set(AppLocale.of(locale.code), devicePreferences)
        updateAndSave(revert = {
            AppLanguage.set(previous, devicePreferences)
            _uiState.update { it.copy(accountLocale = previousAccountLocale) }
        }) { it.copy(accountLocale = locale) }
    }

    /**
     * Switches push for this handset: the preference is what survives a restart, the
     * register/unregister call is what the backend acts on.
     */
    fun togglePush() {
        val enabled = !_uiState.value.pushEnabled
        _uiState.update { it.copy(pushEnabled = enabled) }
        viewModelScope.launch {
            devicePreferences.setPushEnabled(enabled)
            val result = if (enabled) {
                pushRepository.registerCurrentDevice()
            } else {
                pushRepository.unregisterCurrentDevice()
            }
            result.onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    /**
     * Deletes the account, then hands over to [onDeleted] — the sign-out, because the token
     * this screen was using stopped existing along with the account.
     *
     * A failure leaves everything as it was, with the reason on the screen: the one thing
     * that must not happen here is the app behaving as though the account were gone when the
     * server still holds it. The copy is resolved rather than composed, the way every other
     * view model's is, so it comes out in the language the account reads.
     *
     * What this removes, and what outlives it, is written at cooknco.eu/account-deletion —
     * the page the Play Console's data deletion entry points at, and the one this screen is
     * reviewed against.
     */
    fun deleteAccount(onDeleted: () -> Unit) {
        if (_uiState.value.isDeletingAccount) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAccount = true, error = null) }
            userRepo.deleteAccount()
                .onSuccess {
                    _uiState.update { it.copy(isDeletingAccount = false) }
                    onDeleted()
                }
                .onFailure {
                    val message = stringsFor(AppLanguage.current.value).deleteAccountFailed
                    _uiState.update { state -> state.copy(isDeletingAccount = false, error = message) }
                }
        }
    }

    private fun updateAndSave(
        revert: (() -> Unit)? = null,
        transform: (UserSettingsUiState) -> UserSettingsUiState,
    ) {
        _uiState.update(transform)
        save(revert)
    }

    /**
     * Sends the whole settings object.
     *
     * [revert] undoes what was applied ahead of the answer, for the two settings the app
     * acts on the moment they are picked rather than only when the server agrees.
     */
    fun save(revert: (() -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val state = _uiState.value
            userRepo.updateSettings(
                UserSettingsDTO(
                    autoAcceptFollowRequests = state.autoAcceptFollowRequests,
                    isAccountPublic = state.isAccountPublic,
                    // Null until the user has picked one — "leave it alone", so a privacy
                    // toggle cannot write a language nobody chose.
                    locale = state.accountLocale?.code,
                    mailNotificationsEnabled = state.mailNotificationsEnabled,
                    unitSystem = state.unitSystem.code,
                )
            )
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saved = true) }
                }
                .onFailure { err ->
                    revert?.invoke()
                    _uiState.update { it.copy(isSaving = false, error = err.message ?: "Failed to save") }
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UserSettingsViewModel(
                    AppGraph.userRepository,
                    AppGraph.devicePreferences,
                    AppGraph.pushRepository,
                )
            }
        }
    }
}
