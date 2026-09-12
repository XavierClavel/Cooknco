package com.xavierclavel.cooknco.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.UserInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val user: UserInfo? = null,
    val recipes: List<RecipeOverview> = emptyList(),
    val isLoading: Boolean = true,
    val isFollowing: Boolean = false,
    /**
     * Whether they follow *this* account back. Only meaningful on someone else's profile,
     * and only worth showing when true — "does not follow you back" is not news anyone
     * asked for.
     */
    val followsMe: Boolean = false,
    val isFollowLoading: Boolean = false,
    val allRecipesLoaded: Boolean = false,
    val error: String? = null,
)

class UserProfileViewModel(
    private val userRepo: UserRepository,
    val profileUserId: Long,
    val currentUserId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    val isOwnProfile get() = profileUserId == currentUserId

    private var recipePage = 0
    private val recipes = mutableListOf<RecipeOverview>()
    private val pageSize = 20

    /**
     * Whether a further page is in flight.
     *
     * Held here and set *before* the coroutine starts, rather than read off
     * [UserProfileUiState.isLoading] — that one only covers the initial load, so two
     * scroll-triggered calls in the same frame both passed the guard, both fetched
     * [recipePage], and both appended it. A LazyVerticalGrid keyed by recipe id then found
     * the same key twice and threw.
     */
    private var isLoadingMore = false

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val userDeferred = async { userRepo.getUser(profileUserId) }
            val followDeferred = if (!isOwnProfile) async { userRepo.isFollowing(profileUserId) } else null
            val followsMeDeferred =
                if (!isOwnProfile) async { userRepo.isFollowedBy(currentUserId, profileUserId) } else null
            val recipesDeferred = async { userRepo.getUserRecipes(profileUserId, 0) }

            val user = userDeferred.await().getOrNull()
            val following = followDeferred?.await()?.getOrNull() ?: false
            val followsMe = followsMeDeferred?.await()?.getOrNull() ?: false
            val initialRecipes = recipesDeferred.await().getOrNull() ?: emptyList()

            recipes.clear()
            recipes.addAll(initialRecipes)
            recipePage = 1

            _uiState.update {
                it.copy(
                    isLoading = false,
                    user = user,
                    isFollowing = following,
                    followsMe = followsMe,
                    recipes = recipes.toList(),
                    allRecipesLoaded = initialRecipes.size < pageSize,
                    error = if (user == null) "Failed to load profile" else null,
                )
            }
        }
    }

    fun loadMoreRecipes() {
        val state = _uiState.value
        if (state.isLoading || state.allRecipesLoaded || isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            userRepo.getUserRecipes(profileUserId, recipePage)
                .onSuccess { newRecipes ->
                    // Pages are offsets into a list that can change under us — a recipe
                    // added while reading shifts the window and repeats one. Dropping what
                    // is already held costs a set and makes that unremarkable instead of
                    // fatal.
                    val known = recipes.mapTo(HashSet()) { it.id }
                    recipes.addAll(newRecipes.filterNot { it.id in known })
                    recipePage++
                    _uiState.update {
                        it.copy(
                            recipes = recipes.toList(),
                            allRecipesLoaded = newRecipes.size < pageSize,
                        )
                    }
                }
        }.invokeOnCompletion { isLoadingMore = false }
    }

    fun toggleFollow() {
        val following = _uiState.value.isFollowing
        _uiState.update { it.copy(isFollowLoading = true) }
        viewModelScope.launch {
            val result = if (following) userRepo.unfollow(profileUserId) else userRepo.follow(profileUserId)
            result.onSuccess {
                _uiState.update { it.copy(isFollowing = !following, isFollowLoading = false) }
                // Refresh user to update follower count
                userRepo.getUser(profileUserId).onSuccess { user ->
                    _uiState.update { it.copy(user = user) }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isFollowLoading = false, error = err.message) }
            }
        }
    }

    companion object {
        fun factory(profileUserId: Long, currentUserId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { UserProfileViewModel(AppGraph.userRepository, profileUserId, currentUserId) }
        }
    }
}
