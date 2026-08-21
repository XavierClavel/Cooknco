package com.xavierclavel.cooknco

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xavierclavel.cooknco.navigation.AppNavigation
import com.xavierclavel.cooknco.ui.auth.AuthViewModel
import com.xavierclavel.cooknco.ui.theme.CookncoTheme

@Composable
fun App() {
    CookncoTheme {
        val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory())

        LaunchedEffect(authViewModel) {
            DeepLinks.oauthTokens.collect(authViewModel::handleOAuthToken)
        }

        AppNavigation(viewModel = authViewModel)
    }
}
