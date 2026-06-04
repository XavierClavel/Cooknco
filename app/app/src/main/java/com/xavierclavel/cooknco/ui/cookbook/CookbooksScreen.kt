package com.xavierclavel.cooknco.ui.cookbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite

@Composable
fun CookbooksScreen(
    viewModel: CookbooksViewModel,
    onCookbookClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    CookbooksScreenContent(
        cookbooks = uiState.cookbooks,
        isLoading = uiState.isLoading,
        onCookbookClick = onCookbookClick,
        modifier = modifier,
    )
}

@Composable
private fun CookbooksScreenContent(
    cookbooks: List<CookbookInfo>,
    isLoading: Boolean,
    onCookbookClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = CookncoBackground) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp)
                }
            }

            cookbooks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = CookncoOrange.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "No cookbooks yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "Tap + to create your first one",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CookncoNavy.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(cookbooks, key = { it.id }) { cookbook ->
                        CookbookCard(
                            cookbook = cookbook,
                            onClick = { onCookbookClick(cookbook.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CookbookCard(cookbook: CookbookInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoGreen),
        border = BorderStroke(1.5.dp, CookncoNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            AsyncImage(
                model = "${ApiClient.IMAGE_URL}/cookbooks/${cookbook.id}-v${cookbook.version}.webp",
                contentDescription = cookbook.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = cookbook.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Text(
                    text = "${cookbook.recipesCount} recipe${if (cookbook.recipesCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CookncoNavy.copy(alpha = 0.6f),
                )
                if (cookbook.members.isNotEmpty()) {
                    MemberAvatars(members = cookbook.members)
                }
            }
        }
    }
}

@Composable
private fun MemberAvatars(members: List<RecipeOwner>, modifier: Modifier = Modifier) {
    val visible = members.take(5)
    val overflow = members.size - visible.size
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
    ) {
        visible.forEach { member ->
            AsyncImage(
                model = "${ApiClient.IMAGE_URL}/users/${member.id}-v${member.version}.webp",
                contentDescription = member.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .then(
                        Modifier.background(CookncoBackground, CircleShape)
                            .padding(1.5.dp)
                            .clip(CircleShape)
                    ),
            )
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(CookncoNavy.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = CookncoNavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Xavier")
private val previewCookbooks = listOf(
    CookbookInfo(id = 1L, version = 1L, title = "My Favourites", description = "Best picks", recipesCount = 12, usersCount = 3, members = listOf(previewOwner)),
    CookbookInfo(id = 2L, version = 1L, title = "Vegan Delights", description = "", recipesCount = 5, usersCount = 1, members = listOf(previewOwner)),
    CookbookInfo(id = 3L, version = 1L, title = "Quick Meals for Busy Weekdays", description = "", recipesCount = 8, usersCount = 2, members = listOf(previewOwner)),
    CookbookInfo(id = 4L, version = 1L, title = "Desserts", description = "", recipesCount = 4, usersCount = 1, members = emptyList()),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CookbooksScreenPreview() {
    CookncoTheme {
        CookbooksScreenContent(
            cookbooks = previewCookbooks,
            isLoading = false,
            onCookbookClick = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Cookbooks - Empty")
@Composable
fun CookbooksEmptyPreview() {
    CookncoTheme {
        CookbooksScreenContent(cookbooks = emptyList(), isLoading = false, onCookbookClick = {})
    }
}
