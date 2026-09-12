package com.xavierclavel.cooknco.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.FollowInfoDto
import com.xavierclavel.cooknco.network.dto.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowingUiState(
    val profileUser: UserInfo? = null,
    val entries: List<FollowInfoDto> = emptyList(),
    val isLoading: Boolean = true,
    val allLoaded: Boolean = false,
    /** Non-null while a cancel/unfollow request for that account is in flight. */
    val actioningUserId: Long? = null,
    val error: String? = null,
) {
    val requested get() = entries.filter { it.pending }
    val following get() = entries.filterNot { it.pending }
}

/**
 * Backs `FollowingScreen` — the accounts [profileUserId] follows. `FollowController.getFollows`
 * returns requested (still pending) and accepted entries together (`FollowInfoDto.pending`),
 * matching the mockup's two sections from one paged feed.
 *
 * Both "Cancel" on a still-pending request and "Unfollow" on an accepted one delete the same
 * underlying row (`DELETE /follow/{id}` — `FollowController.unfollow` deletes the follow
 * regardless of its `pending` flag), so both go through [cancelOrUnfollow] /
 * [UserRepository.unfollow] rather than needing a separate endpoint.
 */
class FollowingViewModel(
    private val userRepo: UserRepository,
    val profileUserId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowingUiState())
    val uiState: StateFlow<FollowingUiState> = _uiState.asStateFlow()

    private var page = 0
    private val entries = mutableListOf<FollowInfoDto>()
    private val pageSize = 20

    init {
        viewModelScope.launch { userRepo.getUser(profileUserId).onSuccess { user -> _uiState.update { it.copy(profileUser = user) } } }
        // Unguarded: the initial page always loads regardless of the UI state's starting
        // `isLoading = true` — only later, explicit scroll-triggered calls go through [loadMore].
        loadPage()
    }

    /** Called as the list scrolls near its end; a no-op while a page is already in flight. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.allLoaded) return
        loadPage()
    }

    private fun loadPage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userRepo.getFollows(profileUserId, page)
                .onSuccess { newEntries ->
                    entries.addAll(newEntries)
                    page++
                    _uiState.update {
                        it.copy(
                            entries = entries.toList(),
                            isLoading = false,
                            allLoaded = newEntries.size < pageSize,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    /** Cancels a still-pending outgoing request, or unfollows an accepted one — see class doc. */
    fun cancelOrUnfollow(userId: Long) {
        _uiState.update { it.copy(actioningUserId = userId) }
        viewModelScope.launch {
            userRepo.unfollow(userId)
                .onSuccess {
                    entries.removeAll { it.user.id == userId }
                    _uiState.update { it.copy(entries = entries.toList(), actioningUserId = null) }
                }
                .onFailure { err -> _uiState.update { it.copy(actioningUserId = null, error = err.message) } }
        }
    }

    companion object {
        fun factory(profileUserId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { FollowingViewModel(AppGraph.userRepository, profileUserId) }
        }
    }
}
