package com.xavierclavel.cooknco.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.stickerSwitchSpec
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
 * The gear icon on the Profile tab's own header now opens a real Settings screen
 * (`onNavigateToSettings`) — logging out lives there instead of behind a bare tap.
 * Jumping to another user's profile by tapping the avatar is gone for good — Profile is
 * a first-class tab now, so that shortcut has no reason to exist.
 */
@Composable
fun MainScreen(
    user: UserInfo,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToEditRecipe: (Long?) -> Unit = {},
    onNavigateToCookbook: (Long) -> Unit = {},
    onNavigateToIngredient: (Long) -> Unit = {},
    onNavigateToEditCookbook: (Long?) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFollowers: () -> Unit = {},
    onNavigateToFollowing: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(user.id))
    val recipesViewModel: RecipesViewModel = viewModel(factory = RecipesViewModel.factory())
    val cookbooksViewModel: CookbooksViewModel = viewModel(factory = CookbooksViewModel.factory(user.id))
    val profileViewModel: UserProfileViewModel = viewModel(factory = UserProfileViewModel.factory(user.id, user.id))

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.FEED) }

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
                onProfileClick = { selectedTab = MainTab.PROFILE },
                modifier = Modifier.padding(innerPadding),
            )
            MainTab.SEARCH -> RecipesScreen(
                viewModel = recipesViewModel,
                // A tab has nowhere to pop back to, so no back button — RecipesScreen's
                // onNavigateBack is nullable exactly like UserProfileScreen's is.
                onNavigateBack = null,
                onRecipeClick = onNavigateToRecipe,
                onUserClick = onNavigateToUser,
                onCookbookClick = onNavigateToCookbook,
                onIngredientClick = onNavigateToIngredient,
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
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToFollowers = onNavigateToFollowers,
                onNavigateToFollowing = onNavigateToFollowing,
                modifier = Modifier.padding(innerPadding),
            )
        }
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
    val s = strings()
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
                label = s.navFeed,
                onClick = { onTabSelected(MainTab.FEED) },
            ) { tint -> Icon(Icons.Outlined.Home, contentDescription = s.navFeed, tint = tint, modifier = Modifier.size(20.dp)) }

            NavTabItem(
                selected = selectedTab == MainTab.SEARCH,
                label = s.navSearch,
                onClick = { onTabSelected(MainTab.SEARCH) },
            ) { tint -> Icon(Icons.Outlined.Search, contentDescription = s.navSearch, tint = tint, modifier = Modifier.size(20.dp)) }

            NavTabItem(
                selected = selectedTab == MainTab.COOKBOOKS,
                label = s.navBooks,
                onClick = { onTabSelected(MainTab.COOKBOOKS) },
            ) { tint -> Icon(Icons.Outlined.MenuBook, contentDescription = s.cookbooks, tint = tint, modifier = Modifier.size(20.dp)) }

            NavTabItem(
                selected = selectedTab == MainTab.PROFILE,
                label = s.navMe,
                onClick = { onTabSelected(MainTab.PROFILE) },
            ) { tint ->
                UserAvatar(
                    userId = user.id,
                    version = user.version,
                    contentDescription = s.profile,
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
            Icon(Icons.Outlined.Add, contentDescription = s.newRecipe, tint = CookncoNavy, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * One nav-bar slot: an orange pill with its label when selected, a bare icon when not.
 *
 * Every slot is a weight, and the weight is what animates — the selected one grows to make
 * room for its label while the other three give the width up, all on the same spec, so the
 * pill slides along the bar instead of appearing in the next slot. Doing it the obvious way
 * (wrap-content when selected, `weight(1f)` when not) cannot be animated at all: the slot
 * changes how it is measured, and there is no value in between to tween.
 *
 * The label rides its own expand/fade inside that, which is what keeps it from being
 * squashed out of the pill on the way in.
 */
@Composable
private fun RowScope.NavTabItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    val weight by animateFloatAsState(
        targetValue = if (selected) SELECTED_NAV_WEIGHT else 1f,
        animationSpec = stickerSwitchSpec(),
        label = "nav_weight",
    )
    val fill by animateColorAsState(
        targetValue = if (selected) CookncoOrange else Color.Transparent,
        animationSpec = stickerSwitchSpec(),
        label = "nav_fill",
    )
    val content by animateColorAsState(
        targetValue = if (selected) CookncoWhite else CookncoNavy,
        animationSpec = stickerSwitchSpec(),
        label = "nav_content",
    )
    Row(
        modifier = Modifier
            .weight(weight)
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        icon(content)
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(stickerSwitchSpec(), clip = false) + fadeIn(stickerSwitchSpec()),
            exit = shrinkHorizontally(stickerSwitchSpec(), clip = false) + fadeOut(stickerSwitchSpec()),
        ) {
            Text(
                text = label,
                color = content,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * How much wider the selected slot is than an unselected one.
 *
 * Enough for the longest label the bar carries and no more: the three icons it takes the
 * room from still have to be comfortably tappable.
 */
private const val SELECTED_NAV_WEIGHT = 2.6f

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
