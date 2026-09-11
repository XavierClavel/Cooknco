package com.xavierclavel.cooknco.ui.cookbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.components.CookbookImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoBlueLight
import com.xavierclavel.cooknco.ui.theme.CookncoBlueDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.StickerCard

@Composable
fun CookbooksScreen(
    viewModel: CookbooksViewModel,
    onCookbookClick: (Long) -> Unit = {},
    onNewCookbook: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    CookbooksScreenContent(
        cookbooks = uiState.cookbooks,
        isLoading = uiState.isLoading,
        onCookbookClick = onCookbookClick,
        onNewCookbook = onNewCookbook,
        modifier = modifier,
    )
}

@Composable
private fun CookbooksScreenContent(
    cookbooks: List<CookbookInfo>,
    isLoading: Boolean,
    onCookbookClick: (Long) -> Unit,
    onNewCookbook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
        ) {
            Text("Cookbooks", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, lineHeight = 33.sp)
            Text(
                text = "${cookbooks.size} book${if (cookbooks.size == 1) "" else "s"}",
                fontSize = 13.sp,
                color = CookncoNavy,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(cookbooks, key = { it.id }) { cookbook ->
                    CookbookRow(cookbook = cookbook, onClick = { onCookbookClick(cookbook.id) })
                }
                item {
                    NewCookbookRow(onClick = onNewCookbook)
                }
            }
        }
    }
}

@Composable
private fun NewCookbookRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(shape, CookncoNavy)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("+", fontSize = 20.sp, color = CookncoNavy)
        Text("New cookbook", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

/** A dashed outline in [shape] — the "+ New cookbook" row is the only sticker element
 * without a solid border, so this isn't part of [com.xavierclavel.cooknco.ui.theme.Sticker]. */
private fun Modifier.dashedBorder(shape: RoundedCornerShape, color: Color, width: Dp = 3.dp): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    onDrawBehind { drawOutline(outline, color = color, style = stroke) }
}

@Composable
private fun CookbookRow(cookbook: CookbookInfo, onClick: () -> Unit) {
    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = onClick) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CookbookImage(
                cookbookId = cookbook.id,
                version = cookbook.version,
                contentDescription = cookbook.title,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, CookncoNavy, RoundedCornerShape(14.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = cookbook.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                )
                Text(
                    text = "${cookbook.recipesCount} recipe${if (cookbook.recipesCount != 1) "s" else ""} · " +
                        "${cookbook.usersCount} member${if (cookbook.usersCount != 1) "s" else ""}",
                    fontSize = 12.5.sp,
                    color = CookncoNavy.copy(alpha = 0.62f),
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cookbook.members.isNotEmpty()) {
                        MemberAvatars(members = cookbook.members)
                    }
                    if (cookbook.usersCount > 1) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(CookncoBlueLight)
                                .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                                .padding(horizontal = 9.dp, vertical = 2.dp),
                        ) {
                            Text("Shared", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CookncoBlueDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberAvatars(members: List<RecipeOwner>, modifier: Modifier = Modifier) {
    val visible = members.take(3)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        visible.forEach { member ->
            UserAvatar(
                userId = member.id,
                version = member.version,
                contentDescription = member.username,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(CookncoBackground, CircleShape)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .border(2.dp, CookncoNavy, CircleShape),
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Xavier")
private val previewCookbooks = listOf(
    CookbookInfo(id = 1L, version = 1L, title = "My Favourites", description = "Best picks", recipesCount = 12, usersCount = 3, members = listOf(previewOwner)),
    CookbookInfo(id = 2L, version = 1L, title = "Vegan Delights", description = "", recipesCount = 5, usersCount = 1, members = listOf(previewOwner)),
)

@Preview(showBackground = true)
@Composable
fun CookbooksScreenPreview() {
    CookncoTheme {
        CookbooksScreenContent(cookbooks = previewCookbooks, isLoading = false, onCookbookClick = {}, onNewCookbook = {})
    }
}

@Preview(showBackground = true, name = "Cookbooks - Empty")
@Composable
fun CookbooksEmptyPreview() {
    CookncoTheme {
        CookbooksScreenContent(cookbooks = emptyList(), isLoading = false, onCookbookClick = {}, onNewCookbook = {})
    }
}
