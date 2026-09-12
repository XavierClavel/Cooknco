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

data class FollowersUiState(
    val profileUser: UserInfo? = null,
    val entries: List<FollowInfoDto> = emptyList(),
    val isLoading: Boolean = true,
    val allLoaded: Boolean = false,
    /** Non-null while an accept/decline request for that follower is in flight. */
    val actioningUserId: Long? = null,
    val error: String? = null,
) {
    val pending get() = entries.filter { it.pending }
    val accepted get() = entries.filterNot { it.pending }
}

/**
 * Backs `FollowersScreen` — the accounts following [profileUserId]. `FollowController.getFollowers`
 * returns pending and accepted entries together (`FollowInfoDto.pending`), so one paged feed
 * covers both of the mockup's sections; [FollowersUiState.pending]/[accepted] just partition
 * what has loaded so far.
 */
class FollowersViewModel(
    private val userRepo: UserRepository,
    val profileUserId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowersUiState())
    val uiState: StateFlow<FollowersUiState> = _uiState.asStateFlow()

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
            userRepo.getFollowers(profileUserId, page)
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

    fun accept(followerId: Long) {
        _uiState.update { it.copy(actioningUserId = followerId) }
        viewModelScope.launch {
            userRepo.acceptFollowRequest(followerId)
                .onSuccess {
                    val idx = entries.indexOfFirst { it.user.id == followerId }
                    if (idx >= 0) entries[idx] = entries[idx].copy(pending = false)
                    _uiState.update { it.copy(entries = entries.toList(), actioningUserId = null) }
                }
                .onFailure { err -> _uiState.update { it.copy(actioningUserId = null, error = err.message) } }
        }
    }

    fun decline(followerId: Long) {
        _uiState.update { it.copy(actioningUserId = followerId) }
        viewModelScope.launch {
            userRepo.declineFollowRequest(followerId)
                .onSuccess {
                    entries.removeAll { it.user.id == followerId }
                    _uiState.update { it.copy(entries = entries.toList(), actioningUserId = null) }
                }
                .onFailure { err -> _uiState.update { it.copy(actioningUserId = null, error = err.message) } }
        }
    }

    companion object {
        fun factory(profileUserId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { FollowersViewModel(AppGraph.userRepository, profileUserId) }
        }
    }
}
