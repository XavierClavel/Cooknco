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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xavierclavel.cooknco.ui.auth.AuthState
import com.xavierclavel.cooknco.ui.auth.AuthViewModel
import com.xavierclavel.cooknco.ui.auth.EmailVerificationSentScreen
import com.xavierclavel.cooknco.ui.auth.LoginScreen
import com.xavierclavel.cooknco.ui.auth.SignupScreen
import com.xavierclavel.cooknco.ui.cookbook.CookbookEditScreen
import com.xavierclavel.cooknco.ui.cookbook.CookbookEditViewModel
import com.xavierclavel.cooknco.ui.cookbook.CookbookScreen
import com.xavierclavel.cooknco.ui.cookbook.CookbookViewModel
import com.xavierclavel.cooknco.ui.main.MainScreen
import com.xavierclavel.cooknco.ui.recipe.RecipeEditScreen
import com.xavierclavel.cooknco.ui.user.UserEditScreen
import com.xavierclavel.cooknco.ui.user.UserEditViewModel
import com.xavierclavel.cooknco.ui.user.UserProfileScreen
import com.xavierclavel.cooknco.ui.user.UserProfileViewModel
import com.xavierclavel.cooknco.ui.recipe.RecipeEditViewModel
import com.xavierclavel.cooknco.ui.recipe.RecipeScreen
import com.xavierclavel.cooknco.ui.recipe.RecipeViewModel

private object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val EMAIL_VERIFICATION_SENT = "email_verification_sent"
    const val MAIN = "main"
    const val RECIPE = "recipe/{recipeId}"
    const val RECIPE_EDIT = "recipe/{recipeId}/edit"
    const val RECIPE_CREATE = "recipe/create"
    const val COOKBOOK = "cookbook/{cookbookId}"
    const val COOKBOOK_EDIT = "cookbook/{cookbookId}/edit"
    const val COOKBOOK_CREATE = "cookbook/create"
    const val USER = "user/{userId}"
    const val USER_EDIT = "user/{userId}/edit"
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
            val context = androidx.compose.ui.platform.LocalContext.current
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
            val context = androidx.compose.ui.platform.LocalContext.current

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

        composable(Routes.MAIN) {
            val user = (authState as? AuthState.Authenticated)?.user ?: return@composable
            MainScreen(
                user = user,
                onLogout = viewModel::logout,
                onNavigateToEditProfile = { navController.navigate("user/${user.id}/edit") },
                onNavigateToRecipe = { recipeId ->
                    navController.navigate("recipe/$recipeId")
                },
                onNavigateToEditRecipe = { recipeId ->
                    if (recipeId != null) {
                        navController.navigate("recipe/$recipeId/edit")
                    } else {
                        navController.navigate(Routes.RECIPE_CREATE)
                    }
                },
                onNavigateToCookbook = { id ->
                    navController.navigate("cookbook/$id")
                },
                onNavigateToEditCookbook = { id ->
                    if (id != null) {
                        navController.navigate("cookbook/$id/edit")
                    } else {
                        navController.navigate(Routes.COOKBOOK_CREATE)
                    }
                },
            )
        }

        composable(
            route = Routes.RECIPE,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getLong("recipeId") ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val context = LocalContext.current
            val recipeViewModel: RecipeViewModel = viewModel(
                key = "recipe_$recipeId",
                factory = RecipeViewModel.factory(context, recipeId, currentUserId),
            )
            RecipeScreen(
                recipeId = recipeId,
                currentUserId = currentUserId,
                onNavigateToEdit = { id ->
                    navController.navigate("recipe/$id/edit")
                },
                onNavigateBack = { navController.popBackStack() },
                viewModel = recipeViewModel,
            )
        }

        composable(
            route = Routes.RECIPE_EDIT,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getLong("recipeId") ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val context = LocalContext.current
            val editViewModel: RecipeEditViewModel = viewModel(
                key = "recipe_edit_$recipeId",
                factory = RecipeEditViewModel.factory(context, recipeId, currentUserId),
            )
            RecipeEditScreen(
                recipeId = recipeId,
                currentUserId = currentUserId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    navController.navigate("recipe/$savedId") {
                        popUpTo("recipe/$recipeId/edit") { inclusive = true }
                    }
                },
                viewModel = editViewModel,
            )
        }

        composable(route = Routes.RECIPE_CREATE) {
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val context = LocalContext.current
            val createViewModel: RecipeEditViewModel = viewModel(
                key = "recipe_create",
                factory = RecipeEditViewModel.factory(context, null, currentUserId),
            )
            RecipeEditScreen(
                recipeId = null,
                currentUserId = currentUserId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    navController.navigate("recipe/$savedId") {
                        popUpTo(Routes.RECIPE_CREATE) { inclusive = true }
                    }
                },
                viewModel = createViewModel,
            )
        }

        // Cookbook create — must be declared BEFORE cookbook/{cookbookId}
        composable(route = Routes.COOKBOOK_CREATE) {
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val currentUsername = (authState as? AuthState.Authenticated)?.user?.username ?: ""
            val context = LocalContext.current
            val createViewModel: CookbookEditViewModel = viewModel(
                key = "cookbook_create",
                factory = CookbookEditViewModel.factory(context, null, currentUserId, currentUsername),
            )
            CookbookEditScreen(
                cookbookId = null,
                currentUserId = currentUserId,
                currentUsername = currentUsername,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    navController.navigate("cookbook/$savedId") {
                        popUpTo(Routes.COOKBOOK_CREATE) { inclusive = true }
                    }
                },
                viewModel = createViewModel,
            )
        }

        composable(
            route = Routes.COOKBOOK,
            arguments = listOf(navArgument("cookbookId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val cookbookId = backStackEntry.arguments?.getLong("cookbookId") ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val context = LocalContext.current
            val cookbookViewModel: CookbookViewModel = viewModel(
                key = "cookbook_$cookbookId",
                factory = CookbookViewModel.factory(context, cookbookId, currentUserId),
            )
            CookbookScreen(
                cookbookId = cookbookId,
                currentUserId = currentUserId,
                onNavigateToEdit = { id ->
                    navController.navigate("cookbook/$id/edit")
                },
                onNavigateBack = { navController.popBackStack() },
                viewModel = cookbookViewModel,
            )
        }

        composable(
            route = Routes.COOKBOOK_EDIT,
            arguments = listOf(navArgument("cookbookId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val cookbookId = backStackEntry.arguments?.getLong("cookbookId") ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val currentUsername = (authState as? AuthState.Authenticated)?.user?.username ?: ""
            val context = LocalContext.current
            val editViewModel: CookbookEditViewModel = viewModel(
                key = "cookbook_edit_$cookbookId",
                factory = CookbookEditViewModel.factory(context, cookbookId, currentUserId, currentUsername),
            )
            CookbookEditScreen(
                cookbookId = cookbookId,
                currentUserId = currentUserId,
                currentUsername = currentUsername,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    navController.navigate("cookbook/$savedId") {
                        popUpTo("cookbook/$cookbookId/edit") { inclusive = true }
                    }
                },
                viewModel = editViewModel,
            )
        }

        // ── User profile ──────────────────────────────────────────────────────
        composable(
            route = Routes.USER,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getLong("userId") ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val context = LocalContext.current
            val profileViewModel: UserProfileViewModel = viewModel(
                key = "user_$userId",
                factory = UserProfileViewModel.factory(context, userId, currentUserId),
            )
            UserProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { navController.navigate("user/$userId/edit") },
                onNavigateToRecipe = { recipeId -> navController.navigate("recipe/$recipeId") },
            )
        }

        composable(
            route = Routes.USER_EDIT,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getLong("userId") ?: return@composable
            val context = LocalContext.current
            val editViewModel: UserEditViewModel = viewModel(
                key = "user_edit_$userId",
                factory = UserEditViewModel.factory(context, userId),
            )
            UserEditScreen(
                viewModel = editViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }

    LaunchedEffect(authState) {
        val destination = when (authState) {
            is AuthState.Loading -> return@LaunchedEffect
            is AuthState.Authenticated -> Routes.MAIN
            is AuthState.Unauthenticated -> Routes.LOGIN
        }
        navController.navigate(destination) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }
}
