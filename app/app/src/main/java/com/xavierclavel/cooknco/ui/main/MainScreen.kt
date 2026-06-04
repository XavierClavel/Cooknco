package com.xavierclavel.cooknco.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.ui.cookbook.CookbooksScreen
import com.xavierclavel.cooknco.ui.cookbook.CookbooksViewModel
import com.xavierclavel.cooknco.ui.home.HomeScreen
import com.xavierclavel.cooknco.ui.home.HomeViewModel
import com.xavierclavel.cooknco.ui.user.UserProfileScreen
import com.xavierclavel.cooknco.ui.user.UserProfileViewModel
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite

private val tabs = listOf("Home", "Cookbooks", "Profile")

@Composable
fun MainScreen(
    user: UserInfo,
    onLogout: () -> Unit,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToEditRecipe: (Long?) -> Unit = {},
    onNavigateToCookbook: (Long) -> Unit = {},
    onNavigateToEditCookbook: (Long?) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(context, user.id))
    val cookbooksViewModel: CookbooksViewModel = viewModel(factory = CookbooksViewModel.factory(context, user.id))
    val profileViewModel: UserProfileViewModel = viewModel(factory = UserProfileViewModel.factory(context, user.id, user.id))

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            MainTopBar(
                user = user,
                onMenuClick = { /* TODO: side drawer */ },
                onSearchClick = onNavigateToSearch,
                onProfileClick = { selectedTab = 2 },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CookncoBackground,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text(tabs[0], fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CookncoOrange,
                        selectedTextColor = CookncoOrange,
                        indicatorColor = CookncoBackground,
                        unselectedIconColor = CookncoNavy.copy(alpha = 0.5f),
                        unselectedTextColor = CookncoNavy.copy(alpha = 0.5f),
                    ),
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) },
                    label = { Text(tabs[1], fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CookncoOrange,
                        selectedTextColor = CookncoOrange,
                        indicatorColor = CookncoBackground,
                        unselectedIconColor = CookncoNavy.copy(alpha = 0.5f),
                        unselectedTextColor = CookncoNavy.copy(alpha = 0.5f),
                    ),
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                    label = { Text(tabs[2], fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CookncoOrange,
                        selectedTextColor = CookncoOrange,
                        indicatorColor = CookncoBackground,
                        unselectedIconColor = CookncoNavy.copy(alpha = 0.5f),
                        unselectedTextColor = CookncoNavy.copy(alpha = 0.5f),
                    ),
                )
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> FloatingActionButton(
                    onClick = { onNavigateToEditRecipe(null) },
                    containerColor = CookncoOrange,
                    contentColor = CookncoWhite,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "New recipe")
                }
                1 -> FloatingActionButton(
                    onClick = { onNavigateToEditCookbook(null) },
                    containerColor = CookncoOrange,
                    contentColor = CookncoWhite,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "New cookbook")
                }
            }
        },
        containerColor = CookncoBackground,
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
            2 -> UserProfileScreen(
                viewModel = profileViewModel,
                onNavigateToEdit = onNavigateToEditProfile,
                onNavigateToRecipe = onNavigateToRecipe,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun MainTopBar(
    user: UserInfo,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CookncoBackground,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Hamburger menu button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(44.dp)
                    .border(1.5.dp, CookncoNavy, RoundedCornerShape(10.dp))
                    .background(CookncoWhite, RoundedCornerShape(10.dp)),
            ) {
                Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = CookncoNavy)
            }

            // Search bar — clickable, navigates to search screen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(CookncoOrange)
                    .border(1.5.dp, CookncoNavy, RoundedCornerShape(50.dp))
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = CookncoWhite,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Search a recipe...",
                        color = CookncoWhite.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // User avatar — tapping opens the profile tab
            AsyncImage(
                model = "${ApiClient.IMAGE_URL}/users/${user.id}-v${user.version}.webp",
                contentDescription = "My profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, CookncoNavy, CircleShape)
                    .clickable(onClick = onProfileClick),
            )
        }
    }
}

private val previewUser = UserInfo(
    id = 1L, version = 1L, username = "Xavier",
    role = "USER", joinDate = 0L, bio = "",
    recipesCount = 0, likesCount = 0, cookbooksCount = 0,
    followersCount = 0, followsCount = 0,
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainTopBarPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            MainTopBar(user = previewUser, onMenuClick = {}, onSearchClick = {}, onProfileClick = {})
        }
    }
}
