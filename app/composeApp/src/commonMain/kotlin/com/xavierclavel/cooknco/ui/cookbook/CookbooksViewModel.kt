package com.xavierclavel.cooknco.ui.cookbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookbooksUiState(
    val cookbooks: List<CookbookInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** The server has answered at least once, so [cookbooks] says something rather than nothing. */
    val hasLoaded: Boolean = false,
) {
    /**
     * The cook belongs to no cookbook at all. Not before the first answer, and not after a
     * failure: telling an offline cook they have no cookbooks is a claim nothing has checked.
     */
    val isEmpty: Boolean get() = hasLoaded && cookbooks.isEmpty() && error == null
}

class CookbooksViewModel(
    private val cookbookRepository: CookbookRepository,
    private val userId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookbooksUiState())
    val uiState: StateFlow<CookbooksUiState> = _uiState.asStateFlow()

    /** The load in flight, so the screen asking on arrival does not double the one from `init`. */
    private var loadJob: Job? = null

    init {
        load()
    }

    /**
     * Asks for the list again. Called from `init` and each time the tab comes back on screen:
     * a cookbook is created, joined or left on other screens, and nothing else reloads this one —
     * so the first cookbook made from the empty state would otherwise leave the tab saying there
     * is none.
     *
     * The spinner is for the first answer only. After that the list stays where it is while the
     * new one is fetched, or every return to the tab would blank it for a round trip.
     */
    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !it.hasLoaded, error = null) }
            cookbookRepository.listCookbooks(userId)
                .onSuccess { cookbooks ->
                    _uiState.update { it.copy(isLoading = false, hasLoaded = true, cookbooks = cookbooks) }
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
