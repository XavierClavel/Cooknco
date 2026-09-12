package com.xavierclavel.cooknco.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.McpClientInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class McpClientsUiState(
    val clients: List<McpClientInfo> = emptyList(),
    val isLoading: Boolean = true,
    /** Non-null while that client's grant is being withdrawn. */
    val revokingClientId: String? = null,
    val error: String? = null,
)

/**
 * Backs [McpClientsScreen] — the MCP clients this account has approved.
 *
 * Revoking removes the row locally as soon as the backend confirms rather than reloading the
 * list: the answer to "is it gone" is the 200, and a reload would only be a second chance for
 * the network to fail after the thing already worked.
 */
class McpClientsViewModel(private val userRepo: UserRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(McpClientsUiState())
    val uiState: StateFlow<McpClientsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            userRepo.getMcpClients()
                .onSuccess { clients -> _uiState.update { it.copy(clients = clients, isLoading = false) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    fun revoke(clientId: String) {
        _uiState.update { it.copy(revokingClientId = clientId) }
        viewModelScope.launch {
            userRepo.revokeMcpClient(clientId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            clients = state.clients.filterNot { it.clientId == clientId },
                            revokingClientId = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(revokingClientId = null, error = err.message) }
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { McpClientsViewModel(AppGraph.userRepository) }
        }
    }
}
