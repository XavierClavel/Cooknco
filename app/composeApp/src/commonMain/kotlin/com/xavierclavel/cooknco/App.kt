package com.xavierclavel.cooknco

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.UpdateRequirement
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.navigation.AppNavigation
import com.xavierclavel.cooknco.platform.rememberUrlOpener
import com.xavierclavel.cooknco.ui.auth.AuthViewModel
import com.xavierclavel.cooknco.ui.i18n.LocalStrings
import com.xavierclavel.cooknco.ui.i18n.stringsFor
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.update.UpdateAvailableDialog
import com.xavierclavel.cooknco.ui.update.UpdateRequiredScreen

@Composable
fun App() {
    // Read once, above everything: a language change recomposes the whole tree, which is
    // exactly what it should do — every screen is written in it.
    val language by AppLanguage.current.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { AppLanguage.restore(AppGraph.devicePreferences, scope) }

    CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
    CookncoTheme {
        val update by AppGraph.appVersionRepository.update.collectAsState()
        val urlOpener = rememberUrlOpener()
        val openStore = update.storeUrl
            .takeIf { it.isNotBlank() }
            ?.let { url -> { urlOpener.open(url) } }

        /**
         * Asked once, and not waited for.
         *
         * The app draws first and blocks a moment later if the answer says to, rather than
         * holding a splash until the round trip returns. Holding would mean every launch
         * pays for the check and an offline launch pays for a timeout — for a rule that
         * fires on the rare build, on the rare day. See [AppGraph.appVersionRepository],
         * which answers "carry on" to everything it cannot reach.
         */
        LaunchedEffect(Unit) { AppGraph.appVersionRepository.refresh() }

        if (update.requirement == UpdateRequirement.REQUIRED) {
            // Replaces the app rather than covering it: there is nothing behind this to
            // come back to, and a navigation graph left mounted underneath would still be
            // reachable from a notification tap.
            UpdateRequiredScreen(latestVersion = update.latestVersion, onOpenStore = openStore)
            return@CookncoTheme
        }

        val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory())

        LaunchedEffect(authViewModel) {
            DeepLinks.oauthTokens.collect(authViewModel::handleOAuthToken)
        }

        AppNavigation(viewModel = authViewModel)

        if (update.requirement == UpdateRequirement.SUGGESTED) {
            UpdateAvailableDialog(
                latestVersion = update.latestVersion,
                onOpenStore = openStore,
                onDismiss = AppGraph.appVersionRepository::dismissSuggestion,
            )
        }
    }
    }
}
