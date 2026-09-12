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

/**
 * Which grid the profile is showing.
 *
 * Two tabs on one screen rather than a second screen for likes: the back arrow would
 * otherwise move between them, which is the thing that was wrong with followers and
 * following before they were merged.
 */
enum class ProfileTab { RECIPES, LIKED }

data class UserProfileUiState(
    val user: UserInfo? = null,
    val recipes: List<RecipeOverview> = emptyList(),
    /** Recipes this cook has liked. Only ever loaded for their own profile. */
    val liked: List<RecipeOverview> = emptyList(),
    val tab: ProfileTab = ProfileTab.RECIPES,
    val isLoading: Boolean = true,
    val isLikedLoading: Boolean = false,
    val isFollowing: Boolean = false,
    val isFollowLoading: Boolean = false,
    val allRecipesLoaded: Boolean = false,
    val allLikedLoaded: Boolean = false,
    val error: String? = null,
) {
    /** What the grid is actually drawing, which is all the screen needs to know. */
    val shownRecipes: List<RecipeOverview>
        get() = if (tab == ProfileTab.LIKED) liked else recipes
}

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
    private var likedPage = 0
    private val liked = mutableListOf<RecipeOverview>()
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
    private var isLoadingMoreLiked = false

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val userDeferred = async { userRepo.getUser(profileUserId) }
            val followDeferred = if (!isOwnProfile) async { userRepo.isFollowing(profileUserId, currentUserId) } else null
            val recipesDeferred = async { userRepo.getUserRecipes(profileUserId, 0) }

            val user = userDeferred.await().getOrNull()
            val following = followDeferred?.await()?.getOrNull() ?: false
            val initialRecipes = recipesDeferred.await().getOrNull() ?: emptyList()

            recipes.clear()
            recipes.addAll(initialRecipes)
            recipePage = 1

            _uiState.update {
                it.copy(
                    isLoading = false,
                    user = user,
                    isFollowing = following,
                    recipes = recipes.toList(),
                    allRecipesLoaded = initialRecipes.size < pageSize,
                    error = if (user == null) "Failed to load profile" else null,
                )
            }
        }
    }

    /**
     * Switches the grid, loading the likes the first time they are asked for.
     *
     * Not loaded with the profile: most visits never open this tab, and the cost is a
     * whole extra page of recipes on a screen that already fetches three things.
     */
    fun selectTab(tab: ProfileTab) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab) }
        if (tab == ProfileTab.LIKED && liked.isEmpty() && !_uiState.value.allLikedLoaded) loadLiked()
    }

    private fun loadLiked() {
        if (isLoadingMoreLiked) return
        isLoadingMoreLiked = true
        _uiState.update { it.copy(isLikedLoading = true) }
        viewModelScope.launch {
            userRepo.getUserRecipes(profileUserId, 0, liked = true)
                .onSuccess { page ->
                    liked.clear()
                    liked.addAll(page)
                    likedPage = 1
                    _uiState.update {
                        it.copy(
                            liked = liked.toList(),
                            isLikedLoading = false,
                            allLikedLoaded = page.size < pageSize,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLikedLoading = false, error = err.message) }
                }
        }.invokeOnCompletion { isLoadingMoreLiked = false }
    }

    /** Pages whichever grid is on screen; the two keep their own offset and their own guard. */
    fun loadMoreShown() {
        if (_uiState.value.tab == ProfileTab.LIKED) loadMoreLiked() else loadMoreRecipes()
    }

    private fun loadMoreLiked() {
        val state = _uiState.value
        if (state.isLikedLoading || state.allLikedLoaded || isLoadingMoreLiked) return
        if (liked.isEmpty()) return
        isLoadingMoreLiked = true
        viewModelScope.launch {
            userRepo.getUserRecipes(profileUserId, likedPage, liked = true)
                .onSuccess { page ->
                    val known = liked.mapTo(HashSet()) { it.id }
                    liked.addAll(page.filterNot { it.id in known })
                    likedPage++
                    _uiState.update {
                        it.copy(liked = liked.toList(), allLikedLoaded = page.size < pageSize)
                    }
                }
        }.invokeOnCompletion { isLoadingMoreLiked = false }
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
