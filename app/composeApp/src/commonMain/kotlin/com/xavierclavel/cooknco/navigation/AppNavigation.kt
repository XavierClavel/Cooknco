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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.savedstate.read
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xavierclavel.cooknco.PushNotifications
import com.xavierclavel.cooknco.di.AppGraph
import kotlinx.coroutines.flow.first
import com.xavierclavel.cooknco.ui.auth.AuthState
import com.xavierclavel.cooknco.ui.auth.AuthViewModel
import com.xavierclavel.cooknco.platform.EnsureNotificationPermission
import com.xavierclavel.cooknco.platform.rememberUrlOpener
import com.xavierclavel.cooknco.ui.auth.EmailVerificationSentScreen
import com.xavierclavel.cooknco.ui.auth.LoginScreen
import com.xavierclavel.cooknco.ui.auth.SignupScreen
import com.xavierclavel.cooknco.ui.cookbook.CookbookEditScreen
import com.xavierclavel.cooknco.ui.cookbook.CookbookEditViewModel
import com.xavierclavel.cooknco.ui.cookbook.CookbookScreen
import com.xavierclavel.cooknco.ui.cookbook.CookbookViewModel
import com.xavierclavel.cooknco.ui.main.MainScreen
import com.xavierclavel.cooknco.ui.recipe.CookModeScreen
import com.xavierclavel.cooknco.ui.recipe.IngredientScreen
import com.xavierclavel.cooknco.ui.recipe.IngredientViewModel
import com.xavierclavel.cooknco.ui.recipe.RecipeEditScreen
import com.xavierclavel.cooknco.ui.user.ChangePasswordScreen
import com.xavierclavel.cooknco.ui.user.ChangePasswordViewModel
import com.xavierclavel.cooknco.ui.user.FollowListScreen
import com.xavierclavel.cooknco.ui.user.FollowListViewModel
import com.xavierclavel.cooknco.ui.user.McpClientsScreen
import com.xavierclavel.cooknco.ui.user.McpClientsViewModel
import com.xavierclavel.cooknco.ui.user.FollowTab
import com.xavierclavel.cooknco.ui.user.UserEditScreen
import com.xavierclavel.cooknco.ui.user.UserEditViewModel
import com.xavierclavel.cooknco.ui.user.UserProfileScreen
import com.xavierclavel.cooknco.ui.user.UserProfileViewModel
import com.xavierclavel.cooknco.ui.user.UserSettingsScreen
import com.xavierclavel.cooknco.ui.user.UserSettingsViewModel
import com.xavierclavel.cooknco.ui.recipe.RecipeEditViewModel
import com.xavierclavel.cooknco.ui.recipe.RecipeScreen
import com.xavierclavel.cooknco.ui.recipe.RecipesScreen
import com.xavierclavel.cooknco.ui.recipe.RecipesViewModel
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
    const val RECIPE_COOK_MODE = "recipe/{recipeId}/cook"
    const val COOKBOOK = "cookbook/{cookbookId}"
    const val COOKBOOK_EDIT = "cookbook/{cookbookId}/edit"
    const val COOKBOOK_CREATE = "cookbook/create"
    const val USER = "user/{userId}"
    const val USER_EDIT = "user/{userId}/edit"
    const val USER_FOLLOWERS = "user/{userId}/followers"
    const val USER_FOLLOWING = "user/{userId}/following"
    const val RECIPES = "recipes"
    const val SETTINGS = "settings"
    const val PASSWORD = "settings/password"
    const val MCP_CLIENTS = "settings/mcp-clients"
    const val INGREDIENT = "ingredient/{ingredientId}"
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
            val urlOpener = rememberUrlOpener()
            LoginScreen(
                state = state,
                onEmailChange = viewModel::updateLoginEmail,
                onPasswordChange = viewModel::updateLoginPassword,
                onTogglePasswordVisibility = viewModel::toggleLoginPasswordVisibility,
                onLogin = viewModel::login,
                onNavigateToSignup = { navController.navigate(Routes.SIGNUP) },
                onForgotPassword = { /* TODO: password reset screen */ },
                onLoginWithGoogle = { viewModel.loginWithGoogle(urlOpener) },
            )
        }

        composable(Routes.SIGNUP) {
            val state by viewModel.signupState.collectAsState()
            val signupSuccess by viewModel.signupSuccess.collectAsState()
            val urlOpener = rememberUrlOpener()

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
                onSignupWithGoogle = { viewModel.loginWithGoogle(urlOpener) },
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
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToUser = { userId -> navController.navigate("user/$userId") },
                onNavigateToEditProfile = { navController.navigate("user/${user.id}/edit") },
                onNavigateToFollowers = { navController.navigate("user/${user.id}/followers") },
                onNavigateToFollowing = { navController.navigate("user/${user.id}/following") },
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
                onNavigateToIngredient = { navController.navigate("ingredient/$it") },
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
            val recipeId = backStackEntry.arguments?.read { getLongOrNull("recipeId") } ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val recipeViewModel: RecipeViewModel = viewModel(
                key = "recipe_$recipeId",
                factory = RecipeViewModel.factory(recipeId, currentUserId),
            )
            RecipeScreen(
                recipeId = recipeId,
                currentUserId = currentUserId,
                onNavigateToEdit = { id -> navController.navigate("recipe/$id/edit") },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUser = { userId -> navController.navigate("user/$userId") },
                onNavigateToCookMode = { id -> navController.navigate("recipe/$id/cook") },
                viewModel = recipeViewModel,
            )
        }

        composable(
            route = Routes.RECIPE_EDIT,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.read { getLongOrNull("recipeId") } ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val editViewModel: RecipeEditViewModel = viewModel(
                key = "recipe_edit_$recipeId",
                factory = RecipeEditViewModel.factory(recipeId, currentUserId),
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
            val createViewModel: RecipeEditViewModel = viewModel(
                key = "recipe_create",
                factory = RecipeEditViewModel.factory(null, currentUserId),
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

        composable(
            route = Routes.RECIPE_COOK_MODE,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.read { getLongOrNull("recipeId") } ?: return@composable
            CookModeScreen(
                recipeId = recipeId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // Cookbook create — must be declared BEFORE cookbook/{cookbookId}
        composable(route = Routes.COOKBOOK_CREATE) {
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val currentUsername = (authState as? AuthState.Authenticated)?.user?.username ?: ""
            val createViewModel: CookbookEditViewModel = viewModel(
                key = "cookbook_create",
                factory = CookbookEditViewModel.factory(null, currentUserId, currentUsername),
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
            val cookbookId = backStackEntry.arguments?.read { getLongOrNull("cookbookId") } ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val cookbookViewModel: CookbookViewModel = viewModel(
                key = "cookbook_$cookbookId",
                factory = CookbookViewModel.factory(cookbookId, currentUserId),
            )
            CookbookScreen(
                cookbookId = cookbookId,
                currentUserId = currentUserId,
                onNavigateToEdit = { id -> navController.navigate("cookbook/$id/edit") },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecipe = { recipeId -> navController.navigate("recipe/$recipeId") },
                onNavigateToUser = { userId -> navController.navigate("user/$userId") },
                viewModel = cookbookViewModel,
            )
        }

        composable(
            route = Routes.COOKBOOK_EDIT,
            arguments = listOf(navArgument("cookbookId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val cookbookId = backStackEntry.arguments?.read { getLongOrNull("cookbookId") } ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val currentUsername = (authState as? AuthState.Authenticated)?.user?.username ?: ""
            val editViewModel: CookbookEditViewModel = viewModel(
                key = "cookbook_edit_$cookbookId",
                factory = CookbookEditViewModel.factory(cookbookId, currentUserId, currentUsername),
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

        // ── Recipes browse / search ───────────────────────────────────────────
        composable(Routes.RECIPES) {
            val recipesViewModel: RecipesViewModel = viewModel(
                factory = RecipesViewModel.factory(),
            )
            RecipesScreen(
                viewModel = recipesViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRecipeClick = { recipeId -> navController.navigate("recipe/$recipeId") },
                onUserClick = { userId -> navController.navigate("user/$userId") },
            )
        }

        // ── User profile ──────────────────────────────────────────────────────
        composable(
            route = Routes.USER,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.read { getLongOrNull("userId") } ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val profileViewModel: UserProfileViewModel = viewModel(
                key = "user_$userId",
                factory = UserProfileViewModel.factory(userId, currentUserId),
            )
            UserProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { navController.navigate("user/$userId/edit") },
                onNavigateToRecipe = { recipeId -> navController.navigate("recipe/$recipeId") },
                onNavigateToSettings = null,
                onNavigateToFollowers = { navController.navigate("user/$userId/followers") },
                onNavigateToFollowing = { navController.navigate("user/$userId/following") },
            )
        }

        // Followers and Following are one screen with a switch (see FollowListScreen), so
        // both routes land on it and only differ in which tab opens.
        listOf(
            Routes.USER_FOLLOWERS to FollowTab.FOLLOWERS,
            Routes.USER_FOLLOWING to FollowTab.FOLLOWING,
        ).forEach { (route, initialTab) ->
            composable(
                route = route,
                arguments = listOf(navArgument("userId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.read { getLongOrNull("userId") } ?: return@composable
                val followListViewModel: FollowListViewModel = viewModel(
                    key = "follow_list_$userId",
                    factory = FollowListViewModel.factory(userId, initialTab),
                )
                FollowListScreen(
                    viewModel = followListViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToUser = { navController.navigate("user/$it") },
                )
            }
        }

        composable(
            route = Routes.USER_EDIT,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.read { getLongOrNull("userId") } ?: return@composable
            val editViewModel: UserEditViewModel = viewModel(
                key = "user_edit_$userId",
                factory = UserEditViewModel.factory(userId),
            )
            UserEditScreen(
                viewModel = editViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            val isLoggingOut by viewModel.isLoggingOut.collectAsState()
            val settingsViewModel: UserSettingsViewModel = viewModel(factory = UserSettingsViewModel.factory())
            UserSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPassword = { navController.navigate(Routes.PASSWORD) },
                onNavigateToMcpClients = { navController.navigate(Routes.MCP_CLIENTS) },
                onLogout = viewModel::logout,
                isLoggingOut = isLoggingOut,
            )
        }

        composable(
            route = Routes.INGREDIENT,
            arguments = listOf(navArgument("ingredientId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val ingredientId = backStackEntry.arguments?.read { getLongOrNull("ingredientId") } ?: return@composable
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: 0L
            val ingredientViewModel: IngredientViewModel = viewModel(
                key = "ingredient_$ingredientId",
                factory = IngredientViewModel.factory(ingredientId, currentUserId),
            )
            IngredientScreen(
                viewModel = ingredientViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRecipeClick = { navController.navigate("recipe/$it") },
            )
        }

        composable(Routes.MCP_CLIENTS) {
            val mcpViewModel: McpClientsViewModel = viewModel(factory = McpClientsViewModel.factory())
            McpClientsScreen(
                viewModel = mcpViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PASSWORD) {
            val passwordViewModel: ChangePasswordViewModel = viewModel(factory = ChangePasswordViewModel.factory())
            ChangePasswordScreen(
                viewModel = passwordViewModel,
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
        // The whole stack goes, not just the splash. Popping to SPLASH stops working the
        // moment it leaves the stack — which is on the very first transition — so signing
        // out would otherwise leave the session's screens one back-press away, and signing
        // back in would stack a second MAIN on top of the first, ViewModels and all.
        navController.navigate(destination) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Asked for once there is an account to notify, not on the login screen: Android shows
    // this prompt exactly once, so it is worth spending on a user who has somewhere to be
    // notified about. See EnsureNotificationPermission.
    EnsureNotificationPermission(request = authState is AuthState.Authenticated)

    /**
     * Registers this device once there is an account to register it against, and again on
     * every launch: a token can be rotated while the app is not running, in which case
     * `onNewToken` fired with no session to send it under.
     *
     * Keyed on *whether* we are signed in rather than on [authState] itself. The state
     * carries the user, so re-reading the profile would otherwise restart this effect and
     * cancel the retries `registerCurrentDevice` needs on a fresh install.
     */
    val signedIn = authState is AuthState.Authenticated
    LaunchedEffect(signedIn) {
        // Skipped when push is switched off for this handset (settings): registration runs
        // on every launch, so without this it would undo the switch the next morning.
        if (signedIn && AppGraph.devicePreferences.pushEnabled.first()) {
            AppGraph.pushRepository.registerCurrentDevice()
        }
    }

    /**
     * Opens what a tapped notification pointed at.
     *
     * Gated on being signed in, and replayed by [PushNotifications], so a tap that cold-starts
     * the app waits for the session to be restored rather than bouncing off the login screen.
     */
    LaunchedEffect(authState) {
        if (authState !is AuthState.Authenticated) return@LaunchedEffect
        PushNotifications.taps.collect { route ->
            navController.navigate(route)
        }
    }
}
