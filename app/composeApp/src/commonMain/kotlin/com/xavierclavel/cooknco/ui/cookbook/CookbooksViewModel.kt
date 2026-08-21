package com.xavierclavel.cooknco.ui.cookbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookbooksUiState(
    val cookbooks: List<CookbookInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class CookbooksViewModel(
    private val cookbookRepository: CookbookRepository,
    private val userId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookbooksUiState())
    val uiState: StateFlow<CookbooksUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            cookbookRepository.listCookbooks(userId)
                .onSuccess { cookbooks ->
                    _uiState.update { it.copy(isLoading = false, cookbooks = cookbooks) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    companion object {
        fun factory(userId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { CookbooksViewModel(AppGraph.cookbookRepository, userId) }
        }
    }
}
