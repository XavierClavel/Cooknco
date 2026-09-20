package com.xavierclavel.cooknco

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.AppUnits
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
    LaunchedEffect(Unit) { AppLanguage.restore(AppGraph.devicePreferences) }
    // The ladder the account reads amounts on, and the catalogue that converts onto it.
    // Restored from the last session first so the first recipe draws in the right units
    // rather than in metric until the account answers, and the catalogue refreshed behind
    // it — the packaged copy is complete, so nothing waits for that.
    //
    // Both are only a head start. What the account actually carries is asked for as soon as
    // there is a session to ask under — see the sync in [AppNavigation] — and neither
    // restore writes over it if it gets there first.
    LaunchedEffect(Unit) {
        AppUnits.restore(AppGraph.devicePreferences)
        AppUnits.refresh(AppGraph.unitRepository)
    }
    // A timer started in an earlier process is still counting — its deadline outlived it.
    // Read back here, before cook mode is reachable, so the screen opens on the real one
    // rather than on its step's default. See [CookTimer].
    LaunchedEffect(Unit) { AppGraph.cookTimer.restore() }
    // And the recipe it belongs to. A cook who has been pressing Next on the notification for
    // the last half hour is several steps in, and opening cook mode — which a tap on that
    // notification does — has to land there rather than back at step one. See [CookSession].
    LaunchedEffect(Unit) { AppGraph.cookSession.restore() }

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
