package com.xavierclavel.cooknco.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.ui.cookbook.CookbooksScreen
import com.xavierclavel.cooknco.ui.cookbook.CookbooksViewModel
import com.xavierclavel.cooknco.ui.home.HomeScreen
import com.xavierclavel.cooknco.ui.home.HomeViewModel

private val tabs = listOf("Home", "Cookbooks")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    user: UserInfo,
    onLogout: () -> Unit,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToEditRecipe: (Long?) -> Unit = {},
    onNavigateToCookbook: (Long) -> Unit = {},
    onNavigateToEditCookbook: (Long?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(context, user.id))
    val cookbooksViewModel: CookbooksViewModel = viewModel(factory = CookbooksViewModel.factory(context, user.id))

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Cook'n'Co", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Log out")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text(tabs[0]) },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) },
                    label = { Text(tabs[1]) },
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(onClick = { onNavigateToEditCookbook(null) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create cookbook")
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> HomeScreen(
                user = user,
                viewModel = homeViewModel,
                onRecipeClick = onNavigateToRecipe,
                modifier = Modifier.padding(innerPadding),
            )
            1 -> CookbooksScreen(
                viewModel = cookbooksViewModel,
                onCookbookClick = onNavigateToCookbook,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
