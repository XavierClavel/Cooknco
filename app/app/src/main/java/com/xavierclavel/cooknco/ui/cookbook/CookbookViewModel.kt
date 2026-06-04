package com.xavierclavel.cooknco.ui.cookbook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.CookbookApi
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
import com.xavierclavel.cooknco.network.dto.CookbookUserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookbookUiState(
    val cookbook: CookbookInfo? = null,
    val isLoading: Boolean = true,
    val isAdmin: Boolean = false,
    val recipes: List<CookbookRecipeInfo> = emptyList(),
    val members: List<CookbookUserInfo> = emptyList(),
    val error: String? = null,
    val showLeaveConfirm: Boolean = false,
    val left: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val deleted: Boolean = false,
)

class CookbookViewModel(
    private val repo: CookbookRepository,
    private val cookbookId: Long,
    private val currentUserId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookbookUiState())
    val uiState: StateFlow<CookbookUiState> = _uiState.asStateFlow()

    init {
        loadCookbook()
        loadIsAdmin()
        loadRecipes()
        loadMembers()
    }

    private fun loadCookbook() {
        viewModelScope.launch {
            repo.getCookbook(cookbookId)
                .onSuccess { cookbook ->
                    _uiState.update { it.copy(cookbook = cookbook, isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    private fun loadIsAdmin() {
        viewModelScope.launch {
            repo.isAdminOfCookbook(cookbookId)
                .onSuccess { isAdmin ->
                    _uiState.update { it.copy(isAdmin = isAdmin) }
                }
                .onFailure {
                    // Not critical — default stays false
                }
        }
    }

    private fun loadRecipes() {
        viewModelScope.launch {
            repo.getCookbookRecipes(cookbookId)
                .onSuccess { recipes ->
                    _uiState.update { it.copy(recipes = recipes) }
                }
                .onFailure {
                    // Non-critical
                }
        }
    }

    private fun loadMembers() {
        viewModelScope.launch {
            repo.getCookbookUsers(cookbookId)
                .onSuccess { members ->
                    _uiState.update { it.copy(members = members) }
                }
                .onFailure {
                    // Non-critical
                }
        }
    }

    fun confirmLeave() {
        _uiState.update { it.copy(showLeaveConfirm = true) }
    }

    fun cancelLeave() {
        _uiState.update { it.copy(showLeaveConfirm = false) }
    }

    fun leave() {
        viewModelScope.launch {
            repo.leaveCookbook(cookbookId)
                .onSuccess {
                    _uiState.update { it.copy(left = true, showLeaveConfirm = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, showLeaveConfirm = false) }
                }
        }
    }

    fun confirmDelete() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun delete() {
        viewModelScope.launch {
            repo.deleteCookbook(cookbookId)
                .onSuccess {
                    _uiState.update { it.copy(deleted = true, showDeleteConfirm = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, showDeleteConfirm = false) }
                }
        }
    }

    companion object {
        fun factory(context: Context, cookbookId: Long, userId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenDataStore = TokenDataStore(context.applicationContext)
                    val cookbookApi = CookbookApi(ApiClient.httpClient)
                    val repo = CookbookRepository(cookbookApi, tokenDataStore)
                    return CookbookViewModel(repo, cookbookId, userId) as T
                }
            }
    }
}
