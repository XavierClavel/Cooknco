package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.components.LikeCount
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.cookbook.AddToCookbookSheet
import com.xavierclavel.cooknco.ui.components.SheetAction
import com.xavierclavel.cooknco.ui.components.StickerActionSheet
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.StickerSegmentedControl
import com.xavierclavel.cooknco.ui.theme.StickerTextArea
import com.xavierclavel.cooknco.ui.theme.stickerSwitchSpec
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun unitLabel(unit: String): String = when (unit) {
    "NONE", "UNIT" -> ""
    "GRAM" -> "g"
    "KILOGRAM" -> "kg"
    "POUND" -> "lb"
    "MILLILITERS" -> "mL"
    "CENTILITER" -> "cL"
    "LITER" -> "L"
    "TEASPOON" -> "teaspoons"
    "TABLESPOON" -> "tablespoons"
    "CUP" -> "cup"
    else -> unit
}

private fun scaleAmount(amount: Float?, selectedYield: Int, recipeYield: Int): String {
    if (amount == null) return ""
    val scaled = amount * selectedYield.toFloat() / recipeYield.toFloat()
    return if (scaled == scaled.roundToInt().toFloat()) scaled.roundToInt().toString()
    else ((scaled * 100).roundToInt() / 100f).toString().trimEnd('0').trimEnd('.')
}

/**
 * "published 22 March", for the owner-actions sheet.
 *
 * The date is built by the catalogue rather than by a `LocalDate.Format` here: the format
 * carried `MonthNames.ENGLISH_FULL`, which is a month table in one language sitting outside
 * the one place month names are supposed to live.
 */
private fun publishedLabel(creationDate: Long, s: Strings): String {
    val date = Instant.fromEpochSeconds(creationDate).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return s.publishedOn(s.dayAndMonth(date))
}

private enum class RecipeTab { INGREDIENTS, STEPS, NOTES }

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun RecipeScreen(
    recipeId: Long,
    currentUserId: Long,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateToCookMode: (Long) -> Unit = {},
    viewModel: RecipeViewModel,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val uiState by viewModel.uiState.collectAsState()
    // LocalClipboardManager is deprecated in favour of LocalClipboard, but ClipEntry
    // has no common-code constructor yet, so this stays the multiplatform option.
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onNavigateBack() }

    val recipe = uiState.recipe

    Surface(modifier = modifier.fillMaxSize(), color = CookncoGreen) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }

            uiState.error != null && recipe == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = CookncoNavy, modifier = Modifier.padding(16.dp))
            }

            recipe != null -> RecipeContent(
                recipe = recipe,
                uiState = uiState,
                isOwner = viewModel.isOwner,
                onToggleLike = viewModel::toggleLike,
                onShare = { clipboardManager.setText(AnnotatedString("cooknco.eu/recipe?id=${recipe.id}")) },
                onEdit = { onNavigateToEdit(recipe.id) },
                onAddToCookbook = viewModel::openCookbookPicker,
                onDelete = viewModel::confirmDelete,
                onYieldMinus = { viewModel.setYield(uiState.selectedYield - 1) },
                onYieldPlus = { viewModel.setYield(uiState.selectedYield + 1) },
                onStartEditNotes = viewModel::startEditNotes,
                onNotesChange = viewModel::updateNotes,
                onSaveNotes = viewModel::saveNotes,
                onCancelNoteEdit = viewModel::cancelNoteEdit,
                onNavigateToUser = onNavigateToUser,
                onNavigateBack = onNavigateBack,
                onStartCooking = { onNavigateToCookMode(recipe.id) },
            )
        }
    }

    // Mounted here rather than inside the list: it is a dialog, and the list it would sit
    // in is the one the sheet's toggles can send the recipe in and out of.
    uiState.cookbookPicker?.let { picker ->
        AddToCookbookSheet(
            cookbooks = uiState.cookbooks,
            state = picker,
            onToggle = viewModel::toggleCookbook,
            onDismissRequest = viewModel::closeCookbookPicker,
        )
    }

    if (uiState.showDeleteConfirm && recipe != null) {
        StickerConfirmDialog(
            icon = Icons.Outlined.Delete,
            title = s.deleteThisRecipe,
            message = s.deleteRecipeMessage(recipe.title),
            confirmText = s.deleteRecipe,
            dismissText = s.keepIt,
            onConfirm = { viewModel.deleteRecipe() },
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
        )
    }
}

// ── Recipe content ────────────────────────────────────────────────────────────

@Composable
private fun RecipeContent(
    recipe: RecipeInfo,
    uiState: RecipeUiState,
    isOwner: Boolean,
    onToggleLike: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onAddToCookbook: () -> Unit,
    onDelete: () -> Unit,
    onYieldMinus: () -> Unit,
    onYieldPlus: () -> Unit,
    onStartEditNotes: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit,
    onCancelNoteEdit: () -> Unit,
    onStartCooking: () -> Unit,
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val recipeYield = recipe.yield ?: 1
    var selectedTab by rememberSaveable { mutableStateOf(RecipeTab.INGREDIENTS) }
    var showMenu by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── Banner image, overlapped by the info card, with the back / like / more overlay ──
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                RecipeImage(
                    recipeId = recipe.id,
                    version = recipe.version,
                    contentDescription = recipe.title,
                    // The banner is ruled off from the green below it (the artboard's
                    // `border-bottom:3px solid #0d1821`). Drawn over the image rather than
                    // laid out under it: the info card overlaps the banner's last 30dp, and
                    // a rule that took layout space would push that overlap out by 3dp.
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .drawWithContent {
                            drawContent()
                            val stroke = 3.dp.toPx()
                            drawRect(
                                color = CookncoNavy,
                                topLeft = Offset(0f, size.height - stroke),
                                size = Size(size.width, stroke),
                            )
                        },
                    thumbnail = false,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Liked fills the button coral, the same treatment the nav bar and
                        // the switches give a selected state — so the heart on top of it is
                        // white, not coral on coral.
                        val likeFill by animateColorAsState(
                            targetValue = if (uiState.isLiked) CookncoOrange else CookncoBackground,
                            animationSpec = stickerSwitchSpec(),
                            label = "like_fill",
                        )
                        val likeContent by animateColorAsState(
                            targetValue = if (uiState.isLiked) CookncoWhite else CookncoNavy,
                            animationSpec = stickerSwitchSpec(),
                            label = "like_content",
                        )
                        StickerIconButton(
                            onClick = onToggleLike,
                            shadowOffset = 3.dp,
                            fillColor = likeFill,
                            contentColor = likeContent,
                        ) {
                            Icon(
                                imageVector = if (uiState.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = s.like,
                                tint = likeContent,
                            )
                        }
                        // Filled when the recipe is in at least one cookbook: the same
                        // coral-fill treatment the heart beside it gets, so the two read as
                        // one pair of states rather than two unrelated buttons.
                        val bookmarkFill by animateColorAsState(
                            targetValue = if (uiState.isBookmarked) CookncoGold else CookncoBackground,
                            animationSpec = stickerSwitchSpec(),
                            label = "bookmark_fill",
                        )
                        StickerIconButton(
                            onClick = onAddToCookbook,
                            shadowOffset = 3.dp,
                            fillColor = bookmarkFill,
                            contentColor = CookncoNavy,
                        ) {
                            Icon(
                                // Gold rather than the heart's coral, and navy on top of it
                                // either way: gold is the colour this app already uses for
                                // "put away for later", on the Create button and the tips
                                // card, and it keeps liked and saved from looking alike.
                                imageVector = if (uiState.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = s.addToCookbook,
                                tint = CookncoNavy,
                            )
                        }
                        val menuFill by animateColorAsState(
                            targetValue = if (showMenu) CookncoNavy else CookncoBackground,
                            animationSpec = stickerSwitchSpec(),
                            label = "menu_fill",
                        )
                        val menuContent by animateColorAsState(
                            targetValue = if (showMenu) CookncoWhite else CookncoNavy,
                            animationSpec = stickerSwitchSpec(),
                            label = "menu_content",
                        )
                        StickerIconButton(
                            onClick = { showMenu = true },
                            shadowOffset = 3.dp,
                            fillColor = menuFill,
                            contentColor = menuContent,
                        ) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = s.more)
                        }
                    }
                }

                // Info card: dish class, title, description, author, meta — overlaps the
                // banner's bottom edge by 30dp, per the mockup's `margin:-30px 18px 0`.
                StickerCard(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 250.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(CookncoGreen)
                                    .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = dishClassLabel(recipe.dishClass).uppercase(),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CookncoWhite,
                                )
                            }
                        }
                        Text(
                            text = recipe.title,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                            lineHeight = 35.sp,
                        )
                        if (recipe.description.isNotBlank()) {
                            Text(
                                text = recipe.description,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = CookncoNavy.copy(alpha = 0.72f),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AuthorChip(owner = recipe.owner, onClick = { onNavigateToUser(recipe.owner.id) })
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(CookncoOrange)
                                    .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                LikeCount(count = recipe.likesCount, color = CookncoWhite)
                            }
                        }
                        val hasMeta = recipe.preparationTime != null || recipe.cookingTime != null || recipe.cookingTemperature != null
                        if (hasMeta) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(CookncoNavy.copy(alpha = 0.14f)),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    recipe.preparationTime?.let { MetaStat(label = s.prepCaps, value = s.minutes(it), modifier = Modifier.weight(1f)) }
                                    recipe.cookingTime?.let { MetaStat(label = s.cookCaps, value = s.minutes(it), modifier = Modifier.weight(1f)) }
                                    recipe.cookingTemperature?.let { MetaStat(label = s.ovenCaps, value = s.degrees(it), modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showMenu) {
            item {
                RecipeActionSheet(
                    recipe = recipe,
                    isOwner = isOwner,
                    onShare = onShare,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onDismissRequest = { showMenu = false },
                )
            }
        }

        // ── Tabs ───────────────────────────────────────────────────────────
        item {
            StickerSegmentedControl(
                options = RecipeTab.entries,
                selected = selectedTab,
                onSelect = { selectedTab = it },
                label = { it.label(s) },
                spacing = 2.dp,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp),
            )
        }

        when (selectedTab) {
            RecipeTab.INGREDIENTS -> ingredientsTab(
                recipe = recipe,
                uiState = uiState,
                recipeYield = recipeYield,
                s = s,
                onYieldMinus = onYieldMinus,
                onYieldPlus = onYieldPlus,
            )
            RecipeTab.STEPS -> stepsTab(recipe = recipe, s = s)
            RecipeTab.NOTES -> item {
                NotesCard(
                    tips = recipe.tips,
                    authorName = recipe.owner.username,
                    notes = uiState.notes,
                    remoteNotes = uiState.remoteNotes,
                    isEditing = uiState.isEditingNotes,
                    onStartEdit = onStartEditNotes,
                    onNotesChange = onNotesChange,
                    onSave = onSaveNotes,
                    onCancel = onCancelNoteEdit,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }

        // ── Bottom actions ───────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Cook mode walks through the steps one at a time, so with no steps
                // written there is nothing for it to walk through. Share then takes the
                // width the button was using, rather than being left a stranded square.
                val canCook = recipe.steps.isNotEmpty()
                StickerCard(
                    modifier = if (canCook) {
                        Modifier.size(width = 58.dp, height = 56.dp)
                    } else {
                        Modifier.weight(1f).height(56.dp)
                    },
                    shape = RoundedCornerShape(16.dp),
                    shadowOffset = 4.dp,
                    onClick = onShare,
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = s.share, tint = CookncoNavy)
                        if (!canCook) {
                            Text(s.share, color = CookncoNavy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                if (canCook) {
                    StickerCard(
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        fillColor = CookncoOrange,
                        shadowOffset = 4.dp,
                        onClick = onStartCooking,
                    ) {
                        Text(
                            s.startCooking,
                            color = CookncoWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }

        if (uiState.error != null) {
            item {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun RecipeTab.label(s: Strings): String = when (this) {
    RecipeTab.INGREDIENTS -> s.ingredients
    RecipeTab.STEPS -> s.steps
    RecipeTab.NOTES -> s.notes
}

private fun dishClassLabel(dishClass: String): String = dishClass
    .lowercase()
    .split('_')
    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

private fun LazyListScope.ingredientsTab(
    recipe: RecipeInfo,
    uiState: RecipeUiState,
    recipeYield: Int,
    // Passed in rather than read: a LazyListScope builder is not a composable, so there is
    // no LocalStrings to reach from here.
    s: Strings,
    onYieldMinus: () -> Unit,
    onYieldPlus: () -> Unit,
) {
    if (recipe.yield != null) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.scaledFor, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CookncoNavy, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(CookncoBackground)
                        .border(3.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onYieldMinus),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Remove, contentDescription = s.decrease, tint = CookncoNavy) }
                    Text(
                        s.portions(uiState.selectedYield),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CookncoNavy,
                        // A floor, not a width: "12 parts" does not fit 54dp and was
                        // wrapping onto a second line inside the pill. The pill has the
                        // room to grow — the label beside it carries the weight — so the
                        // count takes what it needs and keeps to one line.
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = 54.dp).padding(horizontal = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CookncoOrange)
                            .clickable(onClick = onYieldPlus),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Add, contentDescription = s.increase, tint = CookncoWhite) }
                }
            }
        }
    }

    if (recipe.ingredients.isNotEmpty()) {
        item {
            StickerCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column {
                    recipe.ingredients.forEachIndexed { index, ingredient ->
                        IngredientRow(
                            ingredient = ingredient,
                            selectedYield = uiState.selectedYield,
                            recipeYield = recipeYield,
                            showDivider = index < recipe.ingredients.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.stepsTab(recipe: RecipeInfo, s: Strings) {
    if (recipe.steps.isEmpty()) {
        item {
            Text(
                s.noStepsYet,
                color = CookncoWhite.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            )
        }
        return
    }
    item {
        val totalMinutes = listOfNotNull(recipe.preparationTime, recipe.cookingTime).sum()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val stepsLabel = s.stepsCount(recipe.steps.size) +
                if (totalMinutes > 0) " · " + s.aboutMinutesInTotal(totalMinutes) else ""
            Text(stepsLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, modifier = Modifier.weight(1f))
            recipe.cookingTemperature?.let { temperature ->
                StickerPill(
                    height = 36.dp,
                    fillColor = CookncoGold,
                    borderWidth = 2.dp,
                    shadowOffset = 0.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(s.ovenAt(temperature), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                }
            }
        }
    }
    itemsIndexed(recipe.steps) { index, step ->
        StepRow(index = index, step = step, modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp))
    }
}

// ── Reusable sub-composables ──────────────────────────────────────────────────

@Composable
private fun MetaStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

@Composable
private fun AuthorChip(owner: RecipeOwner, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(CookncoWhite)
            .border(2.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(start = 3.dp, top = 3.dp, end = 10.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UserAvatar(
            userId = owner.id,
            version = owner.version,
            contentDescription = null,
            modifier = Modifier.size(24.dp).clip(CircleShape),
        )
        Text(text = owner.username, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

@Composable
private fun IngredientRow(
    ingredient: RecipeIngredientInfo,
    selectedYield: Int,
    recipeYield: Int,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val amountStr = scaleAmount(ingredient.amount, selectedYield, recipeYield)
    val unitStr = unitLabel(ingredient.unit)
    val amountLabel = buildString {
        if (amountStr.isNotEmpty()) append(amountStr)
        if (unitStr.isNotEmpty()) append(if (amountStr.isEmpty()) unitStr else " $unitStr")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CookncoGreenLight)
                    .border(2.dp, CookncoNavy, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (ingredient.type != null) {
                    AsyncImage(
                        model = "${ApiClient.IMAGE_URL}/ingredients/${ingredient.type}.webp",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Text("🍽", fontSize = 15.sp)
                }
            }
            Text(ingredient.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = CookncoNavy, modifier = Modifier.weight(1f))
            if (amountLabel.isNotBlank()) {
                Text(amountLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CookncoNavy)
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(2.dp)
                    .background(CookncoNavy.copy(alpha = 0.1f)),
            )
        }
    }
}

@Composable
private fun StepRow(index: Int, step: String, modifier: Modifier = Modifier) {
    val s = strings()
    StickerCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        shadowOffset = 5.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(
                text = s.stepNumber(index + 1),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoGreenDark,
                letterSpacing = 1.sp,
            )
            Text(
                text = step,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                color = CookncoNavy,
            )
        }
    }
}

@Composable
private fun NotesCard(
    notes: String,
    tips: String,
    authorName: String,
    remoteNotes: String?,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Column(modifier = modifier.fillMaxWidth()) {
        // The author's own tip for the recipe, if they left one — read-only, everyone sees it.
        if (tips.isNotBlank()) {
            Text(
                text = s.fromTheAuthor(authorName),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), fillColor = CookncoGold) {
                Text(
                    text = tips,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = CookncoNavy,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (tips.isNotBlank()) 22.dp else 0.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.myNotesOnlyYouSeeThese,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            if (!isEditing) {
                StickerPill(shadowOffset = 3.dp, onClick = onStartEdit) {
                    Text(s.edit, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                }
            }
        }

        // The signed-in user's private note — editing, saved and empty states.
        if (isEditing) {
            // The note is edited in the very card it will be read in — the mockup draws no
            // editing state of its own, and a filled Material field inside the cream card
            // was the one place on this screen that did not look like the rest of it.
            StickerCard(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                StickerTextArea(
                    value = notes,
                    onValueChange = onNotesChange,
                    placeholder = s.writeNotesHere,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    placeholderColor = CookncoNavy.copy(alpha = 0.4f),
                    modifier = Modifier.padding(16.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StickerPill(height = 48.dp, shadowOffset = 4.dp, onClick = onCancel) {
                    Text(s.cancel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                }
                StickerPill(
                    height = 48.dp,
                    shadowOffset = 4.dp,
                    fillColor = CookncoOrange,
                    contentColor = CookncoWhite,
                    onClick = onSave,
                ) {
                    Text(s.saveCaps, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        } else if (remoteNotes != null) {
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Text(
                    text = notes,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = CookncoNavy,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedNavyBorder(radius = 20.dp)
                    .clickable(onClick = onStartEdit)
                    .padding(16.dp),
            ) {
                Text(
                    text = s.writeANoteHere,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CookncoNavy,
                )
            }
        }
    }
}

/** A dashed navy rounded-rect outline — the notes tab's empty-state prompt (no filled equivalent
 * exists in [com.xavierclavel.cooknco.ui.theme.Sticker], so it is drawn directly here). */
private fun Modifier.dashedNavyBorder(radius: Dp): Modifier = drawWithContent {
    drawContent()
    val strokeWidthPx = 3.dp.toPx()
    drawRoundRect(
        color = CookncoNavy,
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
        ),
        cornerRadius = CornerRadius(radius.toPx()),
        topLeft = androidx.compose.ui.geometry.Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
        size = androidx.compose.ui.geometry.Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
    )
}

/**
 * The recipe's own "···" sheet: which actions it offers, on top of [StickerActionSheet].
 *
 * "Add to a cookbook" appears in the mockup too, but this screen has no callback for it
 * (no such navigation exists yet), so it is left out rather than wired to nothing.
 */
@Composable
private fun RecipeActionSheet(
    recipe: RecipeInfo,
    isOwner: Boolean,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    StickerActionSheet(
        title = recipe.title,
        subtitle = if (isOwner) {
            s.yourRecipe + " · " + publishedLabel(recipe.creationDate, s)
        } else {
            publishedLabel(recipe.creationDate, s).replaceFirstChar(Char::uppercase)
        },
        actions = buildList {
            add(SheetAction(label = s.shareLink, onClick = onShare))
            if (isOwner) {
                add(SheetAction(label = s.editRecipe, onClick = onEdit))
                add(SheetAction(label = s.deleteRecipe, onClick = onDelete, destructive = true))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

// ── Preview ──────────────────────────────────────────────────────────────────

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Aya Amayri")
private val previewRecipe = RecipeInfo(
    id = 1L, version = 1L,
    title = "Harcha",
    dishClass = "MAIN_DISH",
    owner = previewOwner,
    description = "Petits pains marocain à la semoule que l'on mange traditionnellement avec du thé à la menthe et du miel.",
    yield = 8,
    preparationTime = 5,
    cookingTime = 35,
    cookingTemperature = 100,
    ingredients = listOf(
        RecipeIngredientInfo(id = 3L, name = "Semoule moyen", amount = 400f, unit = "GRAM", complement = null, type = "GRAIN", allowedTypes = listOf("NONE", "WEIGHT")),
        RecipeIngredientInfo(id = null, name = "Beurre végétal", amount = 100f, unit = "GRAM", complement = null, allowedTypes = listOf("NONE", "AMOUNT", "WEIGHT", "VOLUME")),
    ),
    steps = listOf(
        "Faire fondre le beurre végétal",
        "Ajouter tous les ingrédients dans un saladier, mélanger à la main jusqu'à la formation d'une pâte homogène",
        "Laisser reposer la pâte 30 mn",
    ),
    tips = "",
    creationDate = 1742601600000L,
    likesCount = 8,
)

@Preview(showBackground = true)
@Composable
fun RecipeScreenPreview() {
    CookncoTheme {
        Surface(color = CookncoGreen) {
            RecipeContent(
                recipe = previewRecipe,
                uiState = RecipeUiState(recipe = previewRecipe, selectedYield = 8, isLoading = false),
                isOwner = true,
                onToggleLike = {},
                onShare = {},
                onEdit = {},
                onAddToCookbook = {},
                onDelete = {},
                onYieldMinus = {},
                onYieldPlus = {},
                onStartEditNotes = {},
                onNotesChange = {},
                onSaveNotes = {},
                onCancelNoteEdit = {},
                onStartCooking = {},
            )
        }
    }
}
