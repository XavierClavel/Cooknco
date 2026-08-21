package com.xavierclavel.cooknco.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.AuthRepository
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.platform.PickedImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserEditUiState(
    val user: UserInfo? = null,
    val username: String = "",
    val bio: String = "",
    val pendingImage: PickedImage? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class UserEditViewModel(
    private val userRepo: UserRepository,
    private val authRepo: AuthRepository,
    private val currentUserId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserEditUiState())
    val uiState: StateFlow<UserEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepo.getUser(currentUserId)
                .onSuccess { user ->
                    _uiState.update { it.copy(
                        user = user,
                        username = user.username,
                        bio = user.bio,
                        isLoading = false,
                    ) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun updateUsername(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun updateBio(value: String) = _uiState.update { it.copy(bio = value, error = null) }
    fun setPendingImage(image: PickedImage?) = _uiState.update { it.copy(pendingImage = image) }

    fun save() {
        val state = _uiState.value
        if (state.username.isBlank()) {
            _uiState.update { it.copy(error = "Username cannot be empty") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            // Upload image if a new one was selected
            val pendingImage = state.pendingImage
            if (pendingImage != null) {
                userRepo.uploadProfileImage(currentUserId, pendingImage.bytes, pendingImage.mimeType)
            }

            // Update text fields
            userRepo.updateUser(state.username.trim(), state.bio.trim())
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saved = true) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(
                        isSaving = false,
                        error = when {
                            "USERNAME_ALREADY_USED" in (err.message ?: "") -> "Username already taken"
                            else -> err.message ?: "Failed to save"
                        },
                    ) }
                }
        }
    }

    companion object {
        fun factory(currentUserId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { UserEditViewModel(AppGraph.userRepository, AppGraph.authRepository, currentUserId) }
        }
    }
}
