package com.xavierclavel.cooknco.ui.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.ReportRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.ApiException
import com.xavierclavel.cooknco.network.ReportReason
import com.xavierclavel.cooknco.network.ReportTargetType
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.stringsFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    /**
     * Pre-selected rather than blank: the sheet is opened by someone who has already
     * decided something is wrong, and "inappropriate content" is what most reports are.
     * Every other reason is one tap away, and none of them is destructive to pick.
     */
    val reason: ReportReason = ReportReason.INAPPROPRIATE_CONTENT,
    val comment: String = "",
    val isSending: Boolean = false,
    /** Once true the sheet shows the acknowledgement instead of the form. */
    val sent: Boolean = false,
    val error: String? = null,
)

/**
 * One report being written, for one target.
 *
 * Scoped to the sheet rather than to the screen behind it: nothing outside the sheet reads
 * it, and a report that was half-written and abandoned should not come back next time the
 * menu is opened.
 */
class ReportViewModel(
    private val repo: ReportRepository,
    private val targetType: ReportTargetType,
    private val targetId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun selectReason(reason: ReportReason) = _uiState.update { it.copy(reason = reason, error = null) }

    /**
     * The column is 1023 characters and the backend truncates past it, so the field stops
     * there too — a moderator reading half a sentence would not know a half was missing.
     */
    fun updateComment(value: String) =
        _uiState.update { it.copy(comment = value.take(MAX_COMMENT_LENGTH), error = null) }

    /** Back to a blank report, for when the sheet closes. See `ReportSheet`. */
    fun reset() = _uiState.update { ReportUiState() }

    fun send() {
        if (_uiState.value.isSending || _uiState.value.sent) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            val state = _uiState.value
            repo.report(targetType, targetId, state.reason, state.comment)
                .onSuccess { _uiState.update { it.copy(isSending = false, sent = true) } }
                .onFailure { error -> _uiState.update { it.copy(isSending = false, error = messageFor(error)) } }
        }
    }

    /**
     * The sentence for a refusal.
     *
     * The backend answers with the cause's key rather than a sentence — the lower-case
     * strings on `BadRequestCause` and `NotFoundCause` — which is what makes this
     * translatable. Anything else is reported as the one thing the user can act on: it did
     * not go through.
     */
    private fun messageFor(error: Throwable): String {
        val s = copy()
        val body = (error as? ApiException)?.body ?: ""
        return when {
            "already_reported" in body -> s.alreadyReported
            "cannot_report_own_content" in body -> s.cannotReportOwnContent
            "report_target_not_found" in body -> s.reportTargetGone
            else -> s.reportFailed
        }
    }

    /** See [com.xavierclavel.cooknco.ui.auth.AuthViewModel]: a view model has no composition to read. */
    private fun copy(): Strings = stringsFor(AppLanguage.current.value)

    companion object {
        const val MAX_COMMENT_LENGTH = 1023

        fun factory(targetType: ReportTargetType, targetId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportViewModel(AppGraph.reportRepository, targetType, targetId) }
        }
    }
}
