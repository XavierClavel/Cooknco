package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.ExportApi
import com.xavierclavel.cooknco.network.ExportedDocument
import kotlinx.coroutines.flow.first

/**
 * The PDF exports, which only an admin's session can obtain.
 *
 * The language and the ladder are read here rather than passed in by each caller: they are
 * whatever the app is currently showing ([AppLanguage], [AppUnits]), and a sheet that
 * printed in a language the reader had not chosen would be a second answer to a question
 * the settings already answer.
 */
class ExportRepository(
    private val exportApi: ExportApi,
    private val tokenDataStore: TokenDataStore,
) {
    private suspend fun requireToken(): String =
        tokenDataStore.tokenFlow.first() ?: throw IllegalStateException("Not authenticated")

    suspend fun exportRecipe(recipeId: Long): Result<ExportedDocument> = runCatching {
        exportApi.exportRecipe(requireToken(), recipeId, locale(), unitSystem())
    }

    suspend fun exportCookbook(cookbookId: Long): Result<ExportedDocument> = runCatching {
        exportApi.exportCookbook(requireToken(), cookbookId, locale(), unitSystem())
    }

    private fun locale(): String = AppLanguage.current.value.code

    private fun unitSystem(): String = AppUnits.system.value.code
}
