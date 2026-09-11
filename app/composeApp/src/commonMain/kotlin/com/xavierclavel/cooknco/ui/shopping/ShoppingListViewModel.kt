package com.xavierclavel.cooknco.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.ShoppingListRepository
import com.xavierclavel.cooknco.data.ShoppingListUiState
import com.xavierclavel.cooknco.di.AppGraph
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper over [ShoppingListRepository]'s own state — the repository already holds
 * the shared, in-memory list, so there is nothing else for this ViewModel to own.
 */
class ShoppingListViewModel(private val repo: ShoppingListRepository) : ViewModel() {

    val uiState: StateFlow<ShoppingListUiState> = repo.state

    fun setChecked(id: Long, checked: Boolean) = repo.setChecked(id, checked)

    fun clear() = repo.clear()

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { ShoppingListViewModel(AppGraph.shoppingListRepository) }
        }
    }
}
