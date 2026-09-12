package com.xavierclavel.cooknco.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.ui.i18n.stringsFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val isSaving: Boolean = false,
    val changed: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSaving && current.isNotBlank() && new.length >= MIN_LENGTH && new == confirm

    companion object {
        const val MIN_LENGTH = 8
    }
}

/**
 * Backs [ChangePasswordScreen]. The only rules enforced here are the two the backend
 * cannot check — that the new password was typed the same way twice, and that it is long
 * enough to be worth changing to. Whether the *current* one is right is the backend's
 * answer (401), and it is reported as it comes back rather than guessed at.
 */
class ChangePasswordViewModel(private val userRepo: UserRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun updateCurrent(value: String) = _uiState.update { it.copy(current = value, error = null) }
    fun updateNew(value: String) = _uiState.update { it.copy(new = value, error = null) }
    fun updateConfirm(value: String) = _uiState.update { it.copy(confirm = value, error = null) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        // The copy for the language the app is in right now; a view model has no
        // composition to read it from. See AuthViewModel.
        val s = stringsFor(AppLanguage.current.value)
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            userRepo.updatePassword(state.current, state.new)
                .onSuccess { _uiState.update { it.copy(isSaving = false, changed = true) } }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            // 401 here means one thing only: the current password is wrong.
                            error = if (err.message?.contains("401") == true) {
                                s.notYourCurrentPassword
                            } else {
                                err.message ?: s.couldNotChangePassword
                            },
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChangePasswordViewModel(AppGraph.userRepository) }
        }
    }
}
