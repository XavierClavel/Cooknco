package com.xavierclavel.cooknco.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xavierclavel.cooknco.ui.auth.AuthState
import com.xavierclavel.cooknco.ui.auth.AuthViewModel
import com.xavierclavel.cooknco.ui.auth.EmailVerificationSentScreen
import com.xavierclavel.cooknco.ui.auth.LoginScreen
import com.xavierclavel.cooknco.ui.auth.SignupScreen
import com.xavierclavel.cooknco.ui.home.HomeScreen

private object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val EMAIL_VERIFICATION_SENT = "email_verification_sent"
    const val HOME = "home"
}

@Composable
fun AppNavigation(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier,
    ) {
        composable(Routes.SPLASH) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        composable(Routes.LOGIN) {
            val state by viewModel.loginState.collectAsState()
            val context = LocalContext.current
            LoginScreen(
                state = state,
                onEmailChange = viewModel::updateLoginEmail,
                onPasswordChange = viewModel::updateLoginPassword,
                onTogglePasswordVisibility = viewModel::toggleLoginPasswordVisibility,
                onLogin = viewModel::login,
                onNavigateToSignup = { navController.navigate(Routes.SIGNUP) },
                onForgotPassword = { /* TODO: password reset screen */ },
                onLoginWithGoogle = { viewModel.loginWithGoogle(context) },
            )
        }

        composable(Routes.SIGNUP) {
            val state by viewModel.signupState.collectAsState()
            val signupSuccess by viewModel.signupSuccess.collectAsState()
            val context = LocalContext.current

            LaunchedEffect(signupSuccess) {
                if (signupSuccess) {
                    viewModel.resetSignupSuccess()
                    navController.navigate(Routes.EMAIL_VERIFICATION_SENT) {
                        popUpTo(Routes.SIGNUP) { inclusive = true }
                    }
                }
            }

            SignupScreen(
                state = state,
                onUsernameChange = viewModel::updateSignupUsername,
                onEmailChange = viewModel::updateSignupEmail,
                onPasswordChange = viewModel::updateSignupPassword,
                onConfirmPasswordChange = viewModel::updateSignupConfirmPassword,
                onTogglePasswordVisibility = viewModel::toggleSignupPasswordVisibility,
                onToggleConfirmPasswordVisibility = viewModel::toggleSignupConfirmPasswordVisibility,
                onSignup = viewModel::signup,
                onNavigateToLogin = { navController.popBackStack() },
                onSignupWithGoogle = { viewModel.loginWithGoogle(context) },
            )
        }

        composable(Routes.EMAIL_VERIFICATION_SENT) {
            EmailVerificationSentScreen(
                onBackToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            val user = (authState as? AuthState.Authenticated)?.user ?: return@composable
            HomeScreen(
                user = user,
                onLogout = viewModel::logout,
            )
        }
    }

    // Drive navigation from auth state changes
    LaunchedEffect(authState) {
        val destination = when (authState) {
            is AuthState.Loading -> return@LaunchedEffect
            is AuthState.Authenticated -> Routes.HOME
            is AuthState.Unauthenticated -> Routes.LOGIN
        }
        navController.navigate(destination) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }
}
