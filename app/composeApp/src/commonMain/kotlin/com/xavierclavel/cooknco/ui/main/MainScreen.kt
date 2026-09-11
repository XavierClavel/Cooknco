package com.xavierclavel.cooknco.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.cookbook.CookbooksScreen
import com.xavierclavel.cooknco.ui.cookbook.CookbooksViewModel
import com.xavierclavel.cooknco.ui.home.HomeScreen
import com.xavierclavel.cooknco.ui.home.HomeViewModel
import com.xavierclavel.cooknco.ui.recipe.RecipesScreen
import com.xavierclavel.cooknco.ui.recipe.RecipesViewModel
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.stickerShadow
import com.xavierclavel.cooknco.ui.user.UserProfileScreen
import com.xavierclavel.cooknco.ui.user.UserProfileViewModel

private enum class MainTab { FEED, SEARCH, COOKBOOKS, PROFILE }

/**
 * The four tabs behind the sticker-pill bottom bar, in the order the mockup lays them
 * out left to right — see `Cooknco Mobile.dc.html`, turn 5 / option `5a`, the Feed /
 * Search / Cookbooks / Profile screenshots (their nav bar is otherwise identical, only
 * the active segment differs).
 *
 * The old shared `MainTopBar` (hamburger + search pill + avatar) that used to sit above
 * whichever tab was showing is gone — each tab draws its own header instead (Feed's
 * "Friday, 11 September / What's cooking?" greeting, Profile's avatar card, ...).
 * Logging out — the account menu's only exit before — now lives behind the gear icon
 * on the Profile tab's own header: `onLogoutClick` below just flips [showLogoutConfirm],
 * which this screen still owns since it also owns `onLogout`/`isLoggingOut`. Jumping to
 * another user's profile by tapping the avatar is gone for good — Profile is a
 * first-class tab now, so that shortcut has no reason to exist.
 */
@Composable
fun MainScreen(
    user: UserInfo,
    onLogout: () -> Unit,
    isLoggingOut: Boolean = false,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToEditRecipe: (Long?) -> Unit = {},
    onNavigateToCookbook: (Long) -> Unit = {},
    onNavigateToEditCookbook: (Long?) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(user.id))
    val recipesViewModel: RecipesViewModel = viewModel(factory = RecipesViewModel.factory())
    val cookbooksViewModel: CookbooksViewModel = viewModel(factory = CookbooksViewModel.factory(user.id))
    val profileViewModel: UserProfileViewModel = viewModel(factory = UserProfileViewModel.factory(user.id, user.id))

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.FEED) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            MainBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onCreateClick = { onNavigateToEditRecipe(null) },
                user = user,
            )
        },
        // Green everywhere behind content, cream only inside cards — the one background
        // every tab shares, so it lives here rather than being repeated per screen.
        containerColor = CookncoGreen,
        // Each tab already applies its own statusBarsPadding()/navigationBarsPadding()
        // (they have to — the same composables render standalone, without this Scaffold,
        // via other routes, e.g. RecipesScreen via Routes.RECIPES). Scaffold's default
        // contentWindowInsets would reserve that same top inset a second time here, since
        // there's no topBar to consume it — hence the large empty gap under the status bar.
        // Zero it out and let each screen keep owning its own insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        when (selectedTab) {
            MainTab.FEED -> HomeScreen(
                user = user,
                viewModel = homeViewModel,
                onRecipeClick = onNavigateToRecipe,
                onUserClick = onNavigateToUser,
                modifier = Modifier.padding(innerPadding),
            )
            MainTab.SEARCH -> RecipesScreen(
                viewModel = recipesViewModel,
                // A tab has nowhere to pop back to, so no back button — RecipesScreen's
                // onNavigateBack is nullable exactly like UserProfileScreen's is.
                onNavigateBack = null,
                onRecipeClick = onNavigateToRecipe,
                onUserClick = onNavigateToUser,
                modifier = Modifier.padding(innerPadding),
            )
            MainTab.COOKBOOKS -> CookbooksScreen(
                viewModel = cookbooksViewModel,
                onCookbookClick = onNavigateToCookbook,
                onNewCookbook = { onNavigateToEditCookbook(null) },
                modifier = Modifier.padding(innerPadding),
            )
            MainTab.PROFILE -> UserProfileScreen(
                viewModel = profileViewModel,
                onNavigateToEdit = onNavigateToEditProfile,
                onNavigateToRecipe = onNavigateToRecipe,
                onLogoutClick = { showLogoutConfirm = true },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (showLogoutConfirm) {
        // Nothing dismisses this once the sign-out is in flight: the session is already
        // being torn down, and there is nothing to come back to if it is cancelled. It
        // goes away with the screen, which the auth state pops as soon as logout lands.
        AlertDialog(
            onDismissRequest = { if (!isLoggingOut) showLogoutConfirm = false },
            title = { Text("Log out", fontWeight = FontWeight.Bold) },
            text = { Text("You will need to sign in again to reach your recipes on this device.") },
            confirmButton = {
                Button(
                    onClick = onLogout,
                    enabled = !isLoggingOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CookncoOrange,
                        contentColor = CookncoWhite,
                    ),
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(
                            color = CookncoWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text("Log out", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }, enabled = !isLoggingOut) {
                    Text("Cancel", color = CookncoNavy.copy(alpha = 0.7f))
                }
            },
        )
    }
}

/**
 * The sticker-pill bottom nav: a cream, navy-bordered, fully-rounded pill holding the
 * four tabs, plus a separate raised gold Create square that always starts a new recipe.
 * Inactive tabs show only a navy icon; the active one becomes an orange segment with a
 * white icon and label, sized to its content rather than sharing the pill equally — the
 * three inactive icon-only slots split whatever width that leaves.
 */
@Composable
private fun MainBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onCreateClick: () -> Unit,
    user: UserInfo,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .stickerShadow(pillShape, CookncoNavy, offsetX = 5.dp, offsetY = 5.dp)
                .clip(pillShape)
                .background(CookncoBackground)
                .border(3.dp, CookncoNavy, pillShape)
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTabItem(
                selected = selectedTab == MainTab.FEED,
                label = "Feed",
                onClick = { onTabSelected(MainTab.FEED) },
            ) { tint -> Icon(Icons.Outlined.Home, contentDescription = "Feed", tint = tint, modifier = Modifier.size(20.dp)) }

            NavTabItem(
                selected = selectedTab == MainTab.SEARCH,
                label = "Search",
                onClick = { onTabSelected(MainTab.SEARCH) },
            ) { tint -> Icon(Icons.Outlined.Search, contentDescription = "Search", tint = tint, modifier = Modifier.size(20.dp)) }

            NavTabItem(
                selected = selectedTab == MainTab.COOKBOOKS,
                label = "Books",
                onClick = { onTabSelected(MainTab.COOKBOOKS) },
            ) { tint -> Icon(Icons.Outlined.MenuBook, contentDescription = "Cookbooks", tint = tint, modifier = Modifier.size(20.dp)) }

            NavTabItem(
                selected = selectedTab == MainTab.PROFILE,
                label = "Me",
                onClick = { onTabSelected(MainTab.PROFILE) },
            ) { tint ->
                UserAvatar(
                    userId = user.id,
                    version = user.version,
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(2.dp, tint, CircleShape),
                )
            }
        }

        StickerIconButton(
            onClick = onCreateClick,
            size = 58.dp,
            shape = RoundedCornerShape(20.dp),
            fillColor = CookncoGold,
            shadowOffset = 5.dp,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "New recipe", tint = CookncoNavy, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * One nav-bar slot. Selected: an orange pill sized to its icon+label, opaque white
 * content. Unselected: icon only, navy tint, sharing the remaining width equally with
 * the other unselected slots (`Modifier.weight(1f)`, hence the [RowScope] receiver).
 */
@Composable
private fun RowScope.NavTabItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    if (selected) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(CookncoOrange)
                .clickable(onClick = onClick)
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            icon(CookncoWhite)
            Text(label, color = CookncoWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            icon(CookncoNavy)
        }
    }
}

private val previewUser = UserInfo(
    id = 1L, version = 1L, username = "Xavier",
    role = "USER", joinDate = 0L, bio = "",
    recipesCount = 0, likesCount = 0, cookbooksCount = 0,
    followersCount = 0, followsCount = 0,
)

@Preview(showBackground = true)
@Composable
fun MainBottomBarPreview() {
    CookncoTheme {
        Box(modifier = Modifier.background(CookncoGreen)) {
            MainBottomBar(
                selectedTab = MainTab.FEED,
                onTabSelected = {},
                onCreateClick = {},
                user = previewUser,
            )
        }
    }
}
