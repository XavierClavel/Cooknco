package com.xavierclavel.cooknco.ui.cookbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
import com.xavierclavel.cooknco.network.dto.CookbookUserInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.components.CookbookImage
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoBlueDark
import com.xavierclavel.cooknco.ui.theme.CookncoBlueLight
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

@Composable
fun CookbookScreen(
    cookbookId: Long,
    currentUserId: Long,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    viewModel: CookbookViewModel,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.left) { if (uiState.left) onNavigateBack() }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onNavigateBack() }

    val cookbook = uiState.cookbook

    Surface(modifier = modifier.fillMaxSize(), color = CookncoGreen) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }

            uiState.error != null && cookbook == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = CookncoNavy, modifier = Modifier.padding(16.dp))
            }

            cookbook != null -> CookbookContent(
                cookbook = cookbook,
                recipes = uiState.recipes,
                members = uiState.members,
                isAdmin = uiState.isAdmin,
                currentUserId = currentUserId,
                error = uiState.error,
                onLeave = viewModel::confirmLeave,
                onEdit = { onNavigateToEdit(cookbook.id) },
                onDelete = viewModel::confirmDelete,
                onNavigateToRecipe = onNavigateToRecipe,
                onNavigateToUser = onNavigateToUser,
                onNavigateBack = onNavigateBack,
            )
        }
    }

    if (uiState.showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelLeave() },
            title = { Text(s.leaveCookbook, fontWeight = FontWeight.Bold) },
            text = { Text(s.leaveCookbookQuestion) },
            confirmButton = {
                Button(
                    onClick = { viewModel.leave() },
                    colors = ButtonDefaults.buttonColors(containerColor = CookncoOrange, contentColor = CookncoWhite),
                ) { Text(s.leave, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLeave() }) {
                    Text(s.cancel, color = CookncoNavy.copy(alpha = 0.7f))
                }
            },
        )
    }

    if (uiState.showDeleteConfirm) {
        val recipesCount = cookbook?.recipesCount ?: 0
        val usersCount = cookbook?.usersCount ?: 0
        StickerConfirmDialog(
            icon = Icons.Outlined.Delete,
            title = s.deleteCookbookQuestion(cookbook?.title ?: ""),
            message = s.deleteCookbookMessage(usersCount, recipesCount),
            confirmText = s.deleteCookbook,
            dismissText = s.keepIt,
            onConfirm = viewModel::delete,
            onDismissRequest = viewModel::cancelDelete,
            isConfirming = uiState.isDeleting,
        )
    }
}

// ── Cookbook content ────────────────────────────────────────────────────────────

@Composable
private fun CookbookContent(
    cookbook: CookbookInfo,
    recipes: List<CookbookRecipeInfo>,
    members: List<CookbookUserInfo>,
    isAdmin: Boolean,
    currentUserId: Long,
    error: String?,
    onLeave: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = strings()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── Banner image with the back / edit / delete overlay ───────────────
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                CookbookImage(
                    cookbookId = cookbook.id,
                    version = cookbook.version,
                    contentDescription = cookbook.title,
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                )
                // The mockup's banner sits on a hard navy rule where it meets the
                // overlapping info card, rather than fading straight into it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(CookncoNavy),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StickerIconButton(onClick = onNavigateBack, shadowOffset = 3.dp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back)
                    }
                    if (isAdmin) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StickerIconButton(onClick = onEdit, shadowOffset = 3.dp) {
                                Icon(Icons.Outlined.Edit, contentDescription = s.edit)
                            }
                            StickerIconButton(onClick = onDelete, shadowOffset = 3.dp) {
                                Icon(Icons.Outlined.Delete, contentDescription = s.delete, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // ── Info card ────────────────────────────────────────────────────────
        item {
            StickerCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Text(
                        text = cookbook.title,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        lineHeight = 33.sp,
                    )
                    if (cookbook.description.isNotBlank()) {
                        Text(
                            text = cookbook.description,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = CookncoNavy.copy(alpha = 0.72f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip("${cookbook.recipesCount} recipe${if (cookbook.recipesCount != 1) "s" else ""}")
                        StatChip(
                            text = s.memberCount(cookbook.usersCount),
                            fillColor = CookncoBlueLight,
                            textColor = CookncoBlueDark,
                        )
                    }
                }
            }
        }

        // ── Recipes section ──────────────────────────────────────────────────
        if (recipes.isNotEmpty()) {
            item {
                SectionHeader(title = s.recipes, count = recipes.size)
            }
            item {
                StickerCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column {
                        recipes.forEachIndexed { index, recipe ->
                            RecipeRow(
                                recipe = recipe,
                                showDivider = index != recipes.lastIndex,
                                onClick = { onNavigateToRecipe(recipe.id) },
                            )
                        }
                    }
                }
            }
        }

        // ── Members section ──────────────────────────────────────────────────
        if (members.isNotEmpty()) {
            item {
                SectionHeader(title = s.members, count = members.size)
            }
            item {
                StickerCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column {
                        members.forEachIndexed { index, member ->
                            MemberRow(
                                member = member,
                                isCurrentUser = member.id == currentUserId,
                                showDivider = index != members.lastIndex,
                                onClick = { onNavigateToUser(member.id) },
                            )
                        }
                    }
                }
            }
        }

        // ── Leave cookbook ───────────────────────────────────────────────────
        item {
            StickerCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                fillColor = CookncoGreenDark,
                onClick = onLeave,
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.ExitToApp, contentDescription = null, tint = CookncoBackground, modifier = Modifier.size(18.dp))
                    Text(s.leaveCookbook, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CookncoBackground)
                }
            }
        }

        if (error != null) {
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(CookncoOrange)
                .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = CookncoWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StatChip(
    text: String,
    modifier: Modifier = Modifier,
    fillColor: Color = CookncoWhite,
    textColor: Color = CookncoNavy,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(fillColor)
            .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

@Composable
private fun RecipeRow(
    recipe: CookbookRecipeInfo,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipeImage(
                recipeId = recipe.id,
                version = 1L,
                contentDescription = recipe.title,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, CookncoNavy, RoundedCornerShape(12.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = recipe.title,
                    fontSize = 15.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = s.addedBy(recipe.addedByUsername),
                    fontSize = 12.sp,
                    color = CookncoGreenDark,
                )
            }
            Text("›", fontSize = 18.sp, color = CookncoGreenDark)
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(CookncoNavy.copy(alpha = 0.1f)))
        }
    }
}

@Composable
private fun MemberRow(
    member: CookbookUserInfo,
    isCurrentUser: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserAvatar(
                userId = member.id,
                version = 1L,
                contentDescription = member.username,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, CookncoNavy, CircleShape),
            )

            Text(
                text = member.username,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = CookncoNavy,
                modifier = Modifier.weight(1f),
            )

            when {
                isCurrentUser -> RoleBadge(label = s.you, fillColor = CookncoBackground, textColor = CookncoNavy)
                member.isAdmin -> RoleBadge(label = s.admin, fillColor = CookncoOrange, textColor = CookncoWhite)
            }
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(CookncoNavy.copy(alpha = 0.1f)))
        }
    }
}

@Composable
private fun RoleBadge(
    label: String,
    fillColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(fillColor)
            .border(2.dp, CookncoNavy, RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
        )
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Xavier")
private val previewCookbook = CookbookInfo(
    id = 1L, version = 1L,
    title = "My Favourites",
    description = "A collection of my absolute favourite recipes.",
    recipesCount = 3,
    usersCount = 2,
    members = listOf(
        previewOwner,
        RecipeOwner(id = 2L, version = 1L, username = "Aya Amayri", role = "ADMIN"),
    ),
)
private val previewMembers = listOf(
    CookbookUserInfo(id = 1L, username = "Xavier", isAdmin = true, joinDate = 0L),
    CookbookUserInfo(id = 2L, username = "Aya Amayri", isAdmin = false, joinDate = 0L),
)
private val previewRecipes = listOf(
    CookbookRecipeInfo(id = 10L, title = "Harcha", addedById = 2L, addedByUsername = "Aya Amayri", additionDate = 0L),
    CookbookRecipeInfo(id = 11L, title = "Chocolate Fondant", addedById = 1L, addedByUsername = "Xavier", additionDate = 0L),
)

@Preview(showBackground = true)
@Composable
fun CookbookContentPreview() {
    CookncoTheme {
        Surface(color = CookncoGreen) {
            CookbookContent(
                cookbook = previewCookbook,
                recipes = previewRecipes,
                members = previewMembers,
                isAdmin = true,
                currentUserId = 1L,
                error = null,
                onLeave = {},
                onEdit = {},
                onDelete = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Cookbook - Not Admin")
@Composable
fun CookbookContentMemberPreview() {
    CookncoTheme {
        Surface(color = CookncoGreen) {
            CookbookContent(
                cookbook = previewCookbook,
                recipes = previewRecipes,
                members = previewMembers,
                isAdmin = false,
                currentUserId = 2L,
                error = null,
                onLeave = {},
                onEdit = {},
                onDelete = {},
            )
        }
    }
}
