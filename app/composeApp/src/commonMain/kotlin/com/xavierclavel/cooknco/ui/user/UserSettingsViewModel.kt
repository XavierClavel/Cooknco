package com.xavierclavel.cooknco.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.UserSettingsDTO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserSettingsUiState(
    val autoAcceptFollowRequests: Boolean = false,
    val isAccountPublic: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class UserSettingsViewModel(private val userRepo: UserRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSettingsUiState())
    val uiState: StateFlow<UserSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepo.getSettings()
                .onSuccess { settings ->
                    _uiState.update {
                        it.copy(
                            autoAcceptFollowRequests = settings.autoAcceptFollowRequests,
                            isAccountPublic = settings.isAccountPublic,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun toggleAutoAccept() = _uiState.update { it.copy(autoAcceptFollowRequests = !it.autoAcceptFollowRequests) }
    fun toggleAccountPublic() = _uiState.update { it.copy(isAccountPublic = !it.isAccountPublic) }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val state = _uiState.value
            userRepo.updateSettings(
                UserSettingsDTO(
                    autoAcceptFollowRequests = state.autoAcceptFollowRequests,
                    isAccountPublic = state.isAccountPublic,
                )
            )
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saved = true) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSaving = false, error = err.message ?: "Failed to save") }
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { UserSettingsViewModel(AppGraph.userRepository) }
        }
    }
}
