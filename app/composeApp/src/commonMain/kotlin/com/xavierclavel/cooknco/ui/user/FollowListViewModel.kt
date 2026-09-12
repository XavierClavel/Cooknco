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

/** The two halves of [FollowListScreen]; `entries` is also the order they are shown in. */
enum class FollowTab { FOLLOWERS, FOLLOWING }

/**
 * One of the two lists. `FollowController.getFollowers`/`getFollows` both return pending
 * and accepted entries together (`FollowInfoDto.pending`), so one paged feed covers each
 * tab's two sections and [pending]/[accepted] just partition what has loaded so far.
 */
data class FollowSection(
    val entries: List<FollowInfoDto> = emptyList(),
    val isLoading: Boolean = false,
    val allLoaded: Boolean = false,
    /** False until this tab has been opened once — see [FollowListViewModel.select]. */
    val started: Boolean = false,
) {
    val pending get() = entries.filter { it.pending }
    val accepted get() = entries.filterNot { it.pending }
}

data class FollowListUiState(
    val tab: FollowTab = FollowTab.FOLLOWERS,
    val profileUser: UserInfo? = null,
    val followers: FollowSection = FollowSection(),
    val following: FollowSection = FollowSection(),
    /** Non-null while an accept/decline/unfollow request for that account is in flight. */
    val actioningUserId: Long? = null,
    val error: String? = null,
) {
    fun section(tab: FollowTab) = if (tab == FollowTab.FOLLOWERS) followers else following
}

/**
 * Backs [FollowListScreen] — both the accounts following [profileUserId] and the ones they
 * follow, in one view model rather than one per tab, because the two are a switch inside a
 * single screen: the header name and *both* counts have to be on screen before either list
 * has loaded, and fetching the user twice to show them would be wasted.
 *
 * The other tab's first page is only fetched when it is first shown ([select]), so opening
 * the screen still costs exactly one list request.
 */
class FollowListViewModel(
    private val userRepo: UserRepository,
    val profileUserId: Long,
    initialTab: FollowTab,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowListUiState(tab = initialTab))
    val uiState: StateFlow<FollowListUiState> = _uiState.asStateFlow()

    private val pages = mutableMapOf(FollowTab.FOLLOWERS to 0, FollowTab.FOLLOWING to 0)
    private val entries = mapOf(
        FollowTab.FOLLOWERS to mutableListOf<FollowInfoDto>(),
        FollowTab.FOLLOWING to mutableListOf(),
    )
    private val pageSize = 20

    init {
        viewModelScope.launch {
            userRepo.getUser(profileUserId).onSuccess { user -> _uiState.update { it.copy(profileUser = user) } }
        }
        loadPage(initialTab)
    }

    /** Switches tab, loading that list's first page the first time it is shown. */
    fun select(tab: FollowTab) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab) }
        if (!_uiState.value.section(tab).started) loadPage(tab)
    }

    /** Called as a list scrolls near its end; a no-op while a page is already in flight. */
    fun loadMore(tab: FollowTab) {
        val section = _uiState.value.section(tab)
        if (section.isLoading || section.allLoaded) return
        loadPage(tab)
    }

    private fun loadPage(tab: FollowTab) {
        // Marked before the coroutine starts, so a second call in the same frame — a
        // scroll that reaches the end as the tab opens — sees the page as in flight.
        updateSection(tab) { it.copy(isLoading = true, started = true) }
        viewModelScope.launch {
            val page = pages.getValue(tab)
            val result = when (tab) {
                FollowTab.FOLLOWERS -> userRepo.getFollowers(profileUserId, page)
                FollowTab.FOLLOWING -> userRepo.getFollows(profileUserId, page)
            }
            result
                .onSuccess { newEntries ->
                    val list = entries.getValue(tab)
                    // Nothing here is keyed, so a repeat only shows twice rather than
                    // crashing — but it is the same offset paging, and twice is wrong.
                    val known = list.mapTo(HashSet()) { it.user.id }
                    list.addAll(newEntries.filterNot { it.user.id in known })
                    pages[tab] = page + 1
                    updateSection(tab) {
                        it.copy(
                            entries = list.toList(),
                            isLoading = false,
                            allLoaded = newEntries.size < pageSize,
                        )
                    }
                }
                .onFailure { err ->
                    updateSection(tab) { it.copy(isLoading = false) }
                    _uiState.update { it.copy(error = err.message) }
                }
        }
    }

    fun accept(followerId: Long) {
        _uiState.update { it.copy(actioningUserId = followerId) }
        viewModelScope.launch {
            userRepo.acceptFollowRequest(followerId)
                .onSuccess {
                    val list = entries.getValue(FollowTab.FOLLOWERS)
                    val idx = list.indexOfFirst { it.user.id == followerId }
                    if (idx >= 0) list[idx] = list[idx].copy(pending = false)
                    updateSection(FollowTab.FOLLOWERS) { it.copy(entries = list.toList()) }
                    _uiState.update { it.copy(actioningUserId = null) }
                }
                .onFailure { err -> _uiState.update { it.copy(actioningUserId = null, error = err.message) } }
        }
    }

    fun decline(followerId: Long) = removeFrom(FollowTab.FOLLOWERS, followerId) { userRepo.declineFollowRequest(it) }

    /**
     * Cancels a still-pending request you sent, or unfollows an accepted one: both delete
     * the same row (`FollowController.unfollow` deletes the follow whatever its `pending`
     * flag), so there is no separate endpoint to call for the two.
     */
    fun cancelOrUnfollow(userId: Long) = removeFrom(FollowTab.FOLLOWING, userId) { userRepo.unfollow(it) }

    private fun removeFrom(tab: FollowTab, userId: Long, action: suspend (Long) -> Result<*>) {
        _uiState.update { it.copy(actioningUserId = userId) }
        viewModelScope.launch {
            action(userId)
                .onSuccess {
                    val list = entries.getValue(tab)
                    list.removeAll { it.user.id == userId }
                    updateSection(tab) { it.copy(entries = list.toList()) }
                    _uiState.update { it.copy(actioningUserId = null) }
                }
                .onFailure { err -> _uiState.update { it.copy(actioningUserId = null, error = err.message) } }
        }
    }

    private fun updateSection(tab: FollowTab, transform: (FollowSection) -> FollowSection) {
        _uiState.update {
            when (tab) {
                FollowTab.FOLLOWERS -> it.copy(followers = transform(it.followers))
                FollowTab.FOLLOWING -> it.copy(following = transform(it.following))
            }
        }
    }

    companion object {
        fun factory(profileUserId: Long, initialTab: FollowTab): ViewModelProvider.Factory = viewModelFactory {
            initializer { FollowListViewModel(AppGraph.userRepository, profileUserId, initialTab) }
        }
    }
}
