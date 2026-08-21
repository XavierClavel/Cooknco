package com.xavierclavel.cooknco.ui.cookbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.CookbookSaveDto
import com.xavierclavel.cooknco.network.dto.CookbookUserSaveDto
import com.xavierclavel.cooknco.network.dto.UserSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditMember(
    val userId: Long,
    val username: String,
    val isAdmin: Boolean,
    val searchResults: List<UserSummary> = emptyList(),
    val searchQuery: String = "",
    val showDropdown: Boolean = false,
)

data class CookbookEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val title: String = "",
    val description: String = "",
    val visibility: String = "PUBLIC",
    val members: List<EditMember> = emptyList(),
    val error: String? = null,
    val cookbookId: Long? = null,
    val cookbookVersion: Long? = null,
)

class CookbookEditViewModel(
    private val repo: CookbookRepository,
    private val cookbookId: Long?,
    private val currentUserId: Long,
    private val currentUsername: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookbookEditUiState(cookbookId = cookbookId))
    val uiState: StateFlow<CookbookEditUiState> = _uiState.asStateFlow()

    private val searchJobs = mutableMapOf<Int, Job>()

    init {
        if (cookbookId != null) {
            loadCookbook(cookbookId)
        } else {
            // New cookbook: add current user as admin member
            _uiState.update {
                it.copy(
                    members = listOf(
                        EditMember(
                            userId = currentUserId,
                            username = currentUsername,
                            isAdmin = true,
                            searchQuery = currentUsername,
                        ),
                    ),
                )
            }
        }
    }

    private fun loadCookbook(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val cookbookResult = repo.getCookbook(id)
            val usersResult = repo.getCookbookUsers(id)

            cookbookResult.onSuccess { cookbook ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        title = cookbook.title,
                        description = cookbook.description,
                        cookbookId = cookbook.id,
                        cookbookVersion = cookbook.version,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }

            usersResult.onSuccess { users ->
                val editMembers = users.map { u ->
                    EditMember(
                        userId = u.id,
                        username = u.username,
                        isAdmin = u.isAdmin,
                        searchQuery = u.username,
                    )
                }
                _uiState.update { it.copy(members = editMembers) }
            }
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value, error = null) }
    fun updateDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun updateVisibility(value: String) = _uiState.update { it.copy(visibility = value) }

    fun addMember() {
        _uiState.update { it.copy(members = it.members + EditMember(userId = 0L, username = "", isAdmin = false)) }
    }

    fun removeMember(index: Int) {
        searchJobs[index]?.cancel()
        searchJobs.remove(index)
        _uiState.update { state ->
            state.copy(members = state.members.toMutableList().also { it.removeAt(index) })
        }
    }

    fun updateMemberQuery(index: Int, query: String) {
        _uiState.update { state ->
            val list = state.members.toMutableList()
            if (index < list.size) {
                list[index] = list[index].copy(searchQuery = query, showDropdown = query.isNotBlank())
            }
            state.copy(members = list)
        }
        searchJobs[index]?.cancel()
        if (query.isNotBlank()) {
            searchJobs[index] = viewModelScope.launch {
                delay(300)
                repo.searchUsers(query)
                    .onSuccess { result ->
                        _uiState.update { state ->
                            val list = state.members.toMutableList()
                            if (index < list.size) {
                                list[index] = list[index].copy(
                                    searchResults = result.items,
                                    showDropdown = result.items.isNotEmpty(),
                                )
                            }
                            state.copy(members = list)
                        }
                    }
            }
        } else {
            _uiState.update { state ->
                val list = state.members.toMutableList()
                if (index < list.size) {
                    list[index] = list[index].copy(searchResults = emptyList(), showDropdown = false)
                }
                state.copy(members = list)
            }
        }
    }

    fun selectMember(index: Int, user: UserSummary) {
        _uiState.update { state ->
            val list = state.members.toMutableList()
            if (index < list.size) {
                list[index] = list[index].copy(
                    userId = user.id,
                    username = user.username,
                    searchQuery = user.username,
                    searchResults = emptyList(),
                    showDropdown = false,
                )
            }
            state.copy(members = list)
        }
    }

    fun updateMemberRole(index: Int, isAdmin: Boolean) {
        _uiState.update { state ->
            val list = state.members.toMutableList()
            if (index < list.size) {
                list[index] = list[index].copy(isAdmin = isAdmin)
            }
            state.copy(members = list)
        }
    }

    fun dismissMemberDropdown(index: Int) {
        _uiState.update { state ->
            val list = state.members.toMutableList()
            if (index < list.size) {
                list[index] = list[index].copy(showDropdown = false)
            }
            state.copy(members = list)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "Title is required") }
            return
        }

        val dto = CookbookSaveDto(
            title = state.title.trim(),
            description = state.description.trim(),
            visibility = state.visibility,
        )

        val userDtos = state.members
            .filter { it.userId > 0L }
            .map { CookbookUserSaveDto(id = it.userId, isAdmin = it.isAdmin) }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val cookbookResult = if (state.cookbookId != null) {
                repo.updateCookbook(state.cookbookId, dto)
            } else {
                repo.createCookbook(dto)
            }

            cookbookResult
                .onSuccess { savedCookbook ->
                    repo.setCookbookUsers(savedCookbook.id, userDtos)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saved = true,
                            cookbookId = savedCookbook.id,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message) }
                }
        }
    }

    companion object {
        fun factory(cookbookId: Long?, userId: Long, username: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { CookbookEditViewModel(AppGraph.cookbookRepository, cookbookId, userId, username) }
        }
    }
}
