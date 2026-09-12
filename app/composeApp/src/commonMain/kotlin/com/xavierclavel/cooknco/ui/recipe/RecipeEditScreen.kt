package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.UnitInfo
import com.xavierclavel.cooknco.network.dto.displayName
import com.xavierclavel.cooknco.platform.PickedImage
import com.xavierclavel.cooknco.platform.rememberCameraCapture
import com.xavierclavel.cooknco.platform.rememberImagePicker
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoOrangeDark
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.swallowTaps
import com.xavierclavel.cooknco.ui.theme.sheetScrim
import com.xavierclavel.cooknco.ui.theme.StickerDropdownMenu
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerTextArea
import com.xavierclavel.cooknco.ui.theme.stickerSwitchSpec
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState

/** The values the API stores; what each is called comes from the catalogue. */
private val dishClasses = listOf("ENTREE", "MAIN_DISH", "DESERT", "SALTY_SNACK", "SUGARY_SNACK", "DRINK", "OTHER")

// Custom rows carry no capability data, so they accept the whole catalog.
private fun unitsForIngredient(allowedTypes: List<String>, units: List<UnitInfo>): List<UnitInfo> =
    if (allowedTypes.isEmpty()) units else units.filter { it.type in allowedTypes }

/**
 * The heading a unit sits under in the picker. Driven by `UnitInfo.type` rather than the
 * three headings the mockup happens to draw, so a unit type added to the catalogue lands in
 * a section of its own instead of silently joining the last one.
 */
private fun unitSectionLabel(type: String, s: Strings): String? = when (type) {
    "WEIGHT" -> s.sectionWeight
    "VOLUME" -> s.sectionVolume
    "AMOUNT" -> s.sectionCount
    "NONE" -> null
    else -> type.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * What the note line says before anything has been written on it.
 *
 * It used to name the catalogue — "catalogue", "catalogue · grain" — which told the cook
 * where the app had looked the ingredient up rather than anything about the ingredient.
 * What is left is the kind, for the entries that have one, and nothing at all for the rest.
 */
private fun EditIngredient.originLabel(s: Strings): String? = when {
    ingredientId == null -> s.custom
    type.isNotEmpty() -> s.ingredientTypeName(type)
    else -> null
}

private enum class EditorStep {
    BASICS,
    INGREDIENTS,
    STEPS,
    PHOTO,
    ;

    fun label(s: Strings) = when (this) {
        BASICS -> s.stepBasics
        INGREDIENTS -> s.stepIngredients
        STEPS -> s.stepSteps
        PHOTO -> s.stepPhoto
    }
}

// ── Shared styling helpers ────────────────────────────────────────────────────

/** The small caps caption above a field — "TITLE *", "DESCRIPTION". */
@Composable
private fun FieldCaption(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = CookncoGreenDark,
        letterSpacing = 0.7.sp,
        modifier = Modifier.padding(bottom = 7.dp),
    )
}

/**
 * The editor's white, navy-outlined field, with its caption above it rather than floating
 * inside it. The outline turns coral while focused — the mockup draws the field being
 * typed into that way, and it is the only focus signal there is without Material's
 * label-and-indicator machinery.
 */
@Composable
private fun StickerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 22.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    minHeight: Dp = 50.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) CookncoOrange else CookncoNavy,
        animationSpec = stickerSwitchSpec(),
        label = "field_border",
    )
    val textStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        color = CookncoNavy,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(CookncoWhite)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = textStyle.copy(color = CookncoNavy.copy(alpha = 0.35f)))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(CookncoOrange),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}

@Composable
private fun StepHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, lineHeight = 31.sp)
        Text(subtitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CookncoNavy, modifier = Modifier.padding(top = 4.dp))
    }
}

// A small uppercase caption over a group of fields — "DISH CLASS", "TIMES & YIELD" —
// matching the mockup's section labels (navy, not the muted green used for field captions).
//
// The top padding is larger than the mockup's gap on purpose: a sticker card's hard shadow
// is painted outside its layout bounds, so a card above this label eats the first 6dp of
// it. What the mockup measures as clear space has to be spent twice here.
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = CookncoNavy,
        letterSpacing = 0.7.sp,
        modifier = modifier.padding(top = 28.dp, bottom = 10.dp),
    )
}

// A dashed sticker-navy outline, used for the "+ Add step" affordance — Modifier.border()
// has no dashed variant, so this paints the stroke itself with a dash path effect.
private fun Modifier.dashedBorder(
    color: Color,
    shape: Shape,
    strokeWidth: Dp = 3.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 6.dp,
): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f),
    )
    drawPath(path = path, color = color, style = stroke)
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditScreen(
    recipeId: Long?,
    currentUserId: Long,
    onNavigateBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: RecipeEditViewModel,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val uiState by viewModel.uiState.collectAsState()
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val steps = EditorStep.entries

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            val id = uiState.recipeId ?: return@LaunchedEffect
            onSaved(id)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        // ── Top bar ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerIconButton(
                onClick = { if (currentStep > 0) currentStep-- else onNavigateBack() },
                shadowOffset = 3.dp,
            ) {
                // Step 0 has nowhere to go "back" to inside the wizard, so the icon reads
                // as a close (cancel) rather than a back arrow — steps 1-3 step back instead.
                if (currentStep == 0) {
                    Icon(Icons.Outlined.Close, contentDescription = s.cancel)
                } else {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back)
                }
            }
            Text(
                text = if (recipeId == null) s.newRecipe else s.editRecipeTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                modifier = Modifier.weight(1f),
            )
            // The mockup's "Save draft" pill is not here: nothing saves a half-filled
            // recipe yet, and a button that publishes what it calls a draft is worse than
            // no button. PUBLISH at the end of the wizard is the only save.
        }

        // ── Progress segments ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            steps.forEachIndexed { index, step ->
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (index <= currentStep) CookncoGold else CookncoBackground)
                            .border(1.5.dp, CookncoNavy, RoundedCornerShape(3.dp)),
                    )
                    Text(
                        text = step.label(s).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (index <= currentStep) CookncoNavy else CookncoGreenDark,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }
            return@Column
        }

        Box(modifier = Modifier.weight(1f)) {
            when (steps[currentStep]) {
                EditorStep.BASICS -> BasicsStep(uiState = uiState, viewModel = viewModel)
                EditorStep.INGREDIENTS -> IngredientsStep(uiState = uiState, viewModel = viewModel)
                EditorStep.STEPS -> StepsStep(uiState = uiState, viewModel = viewModel)
                EditorStep.PHOTO -> PhotoStep(
                    uiState = uiState,
                    viewModel = viewModel,
                    pickedImage = uiState.pendingImage,
                    onImagePicked = viewModel::setPendingImage,
                )
            }
        }

        // ── Bottom navigation ─────────────────────────────────────────────────
        // Step 0 has no "Cancel" box beside the primary button — the close icon in the
        // top bar already covers that, so only the full-width primary action shows.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (currentStep > 0) {
                StickerCard(
                    modifier = Modifier.size(width = 100.dp, height = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    shadowOffset = 4.dp,
                    onClick = { currentStep-- },
                ) {
                    Text(
                        text = s.back,
                        color = CookncoNavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            StickerCard(
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                fillColor = CookncoOrange,
                shadowOffset = 4.dp,
                onClick = {
                    if (currentStep < steps.lastIndex) currentStep++ else viewModel.save()
                },
            ) {
                if (uiState.isSaving && currentStep == steps.lastIndex) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).align(Alignment.Center),
                        strokeWidth = 2.dp,
                        color = CookncoWhite,
                    )
                } else {
                    Text(
                        text = if (currentStep < steps.lastIndex) s.nextStepLabel(steps[currentStep + 1].label(s).lowercase()) else s.publishCaps,
                        color = CookncoWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = if (currentStep == steps.lastIndex) 0.6.sp else 0.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
    }
}

// ── Step 1: Basics ─────────────────────────────────────────────────────────────

@Composable
private fun BasicsStep(uiState: RecipeEditUiState, viewModel: RecipeEditViewModel, modifier: Modifier = Modifier) {
    val s = strings()
    var picking by remember { mutableStateOf<RecipeNumber?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            StepHeading(title = s.theBasics, subtitle = s.theBasicsSubtitle)
        }
        item {
            StickerCard(modifier = Modifier.fillMaxWidth(), shadowOffset = 6.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        FieldCaption(s.titleRequiredCaps)
                        StickerField(
                            value = uiState.title,
                            onValueChange = viewModel::updateTitle,
                            placeholder = s.nameYourRecipe,
                            singleLine = true,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            minHeight = 50.dp,
                        )
                    }
                    Column {
                        FieldCaption(s.descriptionCaps)
                        StickerField(
                            value = uiState.description,
                            onValueChange = viewModel::updateDescription,
                            placeholder = s.aLineAboutTheDish,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            minHeight = 52.dp,
                        )
                    }
                }
            }
        }
        item { SectionLabel(s.timesAndYield) }
        item {
            StickerCard(modifier = Modifier.fillMaxWidth(), shadowOffset = 6.dp) {
                // Every row but the first is preceded by a 2dp rule and the 5dp of padding
                // under it, so the first row — yield — was sitting 7dp tighter to the card's
                // edge than its neighbours are to theirs. This is that 7dp.
                Column(modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
                    RecipeNumber.entries.forEach { number ->
                        NumberStepperRow(
                            number = number,
                            value = number.valueOf(uiState),
                            onValueChange = { number.update(viewModel, it) },
                            onTypeIt = { picking = number },
                        )
                        HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
                    }
                    Text(
                        text = totalTimeLabel(uiState, s),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CookncoGreenDark,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
            }
        }
        item { SectionLabel(s.dishClass) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dishClasses.forEach { value ->
                    DishClassChip(
                        label = s.dishClassName(value),
                        selected = uiState.dishClass == value,
                        onClick = { viewModel.updateDishClass(value) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    picking?.let { number ->
        NumberPickerSheet(
            number = number,
            value = number.valueOf(uiState),
            totalLabel = totalTimeLabel(uiState, s),
            onConfirm = { number.update(viewModel, it); picking = null },
            onDismissRequest = { picking = null },
        )
    }
}

/**
 * The four numbers on the basics step, in the order the mockup's TIMES & YIELD card lists
 * them. Each knows how far one tap of −/+ moves it and what the picker offers as presets,
 * because "5 minutes" is a sensible nudge for a time and a useless one for an oven.
 */
private enum class RecipeNumber(
    val step: Int,
    val max: Int,
    val presets: List<Int>,
) {
    YIELD(1, 999, listOf(1, 2, 4, 6, 8, 12)),
    PREP(5, 1440, listOf(5, 10, 15, 30, 60)),
    COOK(5, 1440, listOf(10, 20, 35, 60, 90)),
    TEMP(10, 300, listOf(150, 180, 200, 220, 240));

    /**
     * Minutes and degrees are symbols; portions are a word, and words differ per language —
     * which is why this comes from the catalogue rather than from a constructor argument.
     */
    fun unit(s: Strings) = when (this) {
        YIELD -> s.portionsUnit
        PREP, COOK -> s.minutesUnit
        TEMP -> s.degreesUnit
    }

    fun title(s: Strings) = when (this) {
        YIELD -> s.yieldLabel
        PREP -> s.prepTime
        COOK -> s.cookTime
        TEMP -> s.ovenTemp
    }

    fun hint(s: Strings) = when (this) {
        YIELD -> s.yieldHint
        PREP -> s.prepHint
        COOK -> s.cookHint
        TEMP -> s.ovenHint
    }

    fun valueOf(state: RecipeEditUiState): Int = when (this) {
        YIELD -> state.yield
        PREP -> state.prepTime
        COOK -> state.cookTime
        TEMP -> state.cookTemp
    }.toIntOrNull() ?: 0

    fun update(viewModel: RecipeEditViewModel, value: Int) {
        // Zero is "not set" everywhere in the editor, and these fields are strings because
        // that is what the recipe DTO carries — so it clears rather than writing "0".
        val text = if (value <= 0) "" else value.toString()
        when (this) {
            YIELD -> viewModel.updateYield(text)
            PREP -> viewModel.updatePrepTime(text)
            COOK -> viewModel.updateCookTime(text)
            TEMP -> viewModel.updateCookTemp(text)
        }
    }

    /** "1 h 30" reads better than "90 min" on a preset chip; the row itself stays in minutes. */
    fun labelFor(value: Int, s: Strings): String = when {
        this != PREP && this != COOK -> "$value ${unit(s)}"
        value >= 60 && value % 60 == 0 -> "${value / 60} h"
        value >= 60 -> "${value / 60} h ${value % 60}"
        else -> "$value min"
    }
}

private fun totalTimeLabel(state: RecipeEditUiState, s: Strings): String {
    val total = (state.prepTime.toIntOrNull() ?: 0) + (state.cookTime.toIntOrNull() ?: 0)
    return if (total > 0) s.totalMinutes(total) else s.noTimesYet
}

/** One row of the TIMES & YIELD card: name and hint, then a −/value/+ pill. */
@Composable
private fun NumberStepperRow(
    number: RecipeNumber,
    value: Int,
    onValueChange: (Int) -> Unit,
    onTypeIt: () -> Unit,
) {
    val s = strings()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 9.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(number.title(s), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            Text(number.hint(s), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = CookncoGreenDark)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(CookncoWhite)
                .border(2.5.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                // Border width plus the artboard's 2px inset. `border` paints over the
                // outermost 2.5dp and `padding` measures from the same edge, so the 2dp the
                // mockup asks for leaves the coral + sitting under the outline rather than
                // inside it — CSS puts that padding inside the border, Compose does not.
                .padding(4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(
                symbol = "−",
                enabled = value > 0,
                onClick = { onValueChange((value - number.step).coerceAtLeast(0)) },
            )
            Text(
                text = if (value > 0) "$value ${number.unit(s)}" else "—",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (value > 0) CookncoNavy else CookncoNavy.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTypeIt),
            )
            StepperButton(
                symbol = "+",
                filled = true,
                onClick = { onValueChange((value + number.step).coerceAtMost(number.max)) },
            )
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (filled) CookncoOrange else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                filled -> CookncoWhite
                enabled -> CookncoNavy
                else -> CookncoNavy.copy(alpha = 0.3f)
            },
        )
    }
}

/**
 * The "Editor — yield & time picker" artboard: the same number, big enough to nudge with a
 * thumb, the usual answers one tap away, and the keyboard still there for the unusual one —
 * tapping the number itself starts typing, with nothing on screen having to say so.
 * Nothing is written until "Set", so the row behind it does not move while you decide.
 */
@Composable
private fun NumberPickerSheet(
    number: RecipeNumber,
    value: Int,
    totalLabel: String,
    onConfirm: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    var draft by remember { mutableIntStateOf(value) }
    var typing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.55f))
                // Nothing is written until "Set", so tapping away is simply cancelling.
                .sheetScrim(onDismissRequest),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .swallowTaps()
                    .padding(start = 14.dp, end = 14.dp, bottom = 26.dp),
            ) {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadowOffset = 6.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
                            Text(number.title(s), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                            Text(
                                text = number.hint(s),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CookncoGreenDark,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.12f))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StickerCard(
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(18.dp),
                                fillColor = CookncoWhite,
                                shadowOffset = 0.dp,
                                onClick = { draft = (draft - number.step).coerceAtLeast(0) },
                            ) {
                                Text(
                                    "−",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CookncoNavy,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .widthIn(min = 118.dp)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(CookncoWhite)
                                    .border(3.dp, CookncoOrange, RoundedCornerShape(18.dp))
                                    .clickable { typing = true }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            ) {
                                if (typing) {
                                    BasicTextField(
                                        value = if (draft > 0) draft.toString() else "",
                                        onValueChange = { text ->
                                            draft = text.filter { it.isDigit() }
                                                .take(4)
                                                .toIntOrNull()
                                                ?.coerceAtMost(number.max) ?: 0
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = CookncoNavy),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        cursorBrush = SolidColor(CookncoOrange),
                                        modifier = Modifier.widthIn(min = 40.dp, max = 86.dp),
                                    )
                                } else {
                                    Text(
                                        text = draft.toString(),
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CookncoNavy,
                                    )
                                }
                                Text(number.unit(s), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
                            }
                            StickerCard(
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(18.dp),
                                fillColor = CookncoOrange,
                                shadowOffset = 0.dp,
                                onClick = { draft = (draft + number.step).coerceAtMost(number.max) },
                            ) {
                                Text(
                                    "+",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CookncoWhite,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            number.presets.forEach { preset ->
                                PresetChip(
                                    label = number.labelFor(preset, s),
                                    selected = draft == preset,
                                    onClick = { draft = preset; typing = false },
                                )
                            }
                        }

                        HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.12f))
                        Text(
                            text = totalLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CookncoGreenDark,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StickerCard(
                        modifier = Modifier.size(width = 110.dp, height = 56.dp),
                        shape = RoundedCornerShape(18.dp),
                        shadowOffset = 4.dp,
                        onClick = onDismissRequest,
                    ) {
                        Text(
                            s.cancel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    StickerCard(
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        fillColor = CookncoOrange,
                        shadowOffset = 4.dp,
                        onClick = { onConfirm(draft) },
                    ) {
                        Text(
                            text = if (draft > 0) s.setValue(number.labelFor(draft, s)) else s.clearValue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoWhite,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(
        targetValue = if (selected) CookncoNavy else CookncoWhite,
        animationSpec = stickerSwitchSpec(),
        label = "preset_fill",
    )
    val content by animateColorAsState(
        targetValue = if (selected) CookncoWhite else CookncoNavy,
        animationSpec = stickerSwitchSpec(),
        label = "preset_content",
    )
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .border(2.5.dp, CookncoNavy, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

@Composable
private fun DishClassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(
        targetValue = if (selected) CookncoNavy else CookncoBackground,
        animationSpec = stickerSwitchSpec(),
        label = "dish_class_fill",
    )
    val content by animateColorAsState(
        targetValue = if (selected) CookncoWhite else CookncoNavy,
        animationSpec = stickerSwitchSpec(),
        label = "dish_class_content",
    )
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(fill)
            .border(2.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = content,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

// ── Step 2: Ingredients ─────────────────────────────────────────────────────────

/**
 * One search field at the top with its results directly under it, then the ingredients
 * already added — the "Editor — step 2 of 4" artboard.
 *
 * The view model keeps one row per ingredient, and a row that has not resolved to a
 * catalogue entry or a custom name yet *is* the search box. So the first unresolved row is
 * rendered as the field at the top and the resolved ones as the list below it, and a fresh
 * unresolved row is appended as soon as one resolves — which is what keeps a search field
 * on screen without a separate "add" button to press first.
 */
@Composable
private fun IngredientsStep(uiState: RecipeEditUiState, viewModel: RecipeEditViewModel, modifier: Modifier = Modifier) {
    val s = strings()
    val searchIndex = uiState.ingredients.indexOfFirst { !it.isResolved }
    LaunchedEffect(searchIndex) { if (searchIndex < 0) viewModel.addIngredient() }
    val search = uiState.ingredients.getOrNull(searchIndex)
    val added = uiState.ingredients.withIndex().filter { it.value.isResolved }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StepHeading(title = s.stepIngredients, subtitle = s.ingredientsSubtitle)
        }

        if (search != null) {
            item {
                IngredientSearchField(
                    query = search.query,
                    onQueryChange = { viewModel.updateIngredientQuery(searchIndex, it) },
                )
            }
            if (search.query.isNotBlank()) {
                item {
                    IngredientSearchResults(
                        results = search.searchResults,
                        query = search.query,
                        onSelect = { viewModel.selectIngredient(searchIndex, it) },
                        onSelectCustom = { viewModel.selectCustomIngredient(searchIndex) },
                    )
                }
            }
        }

        item {
            Text(
                text = s.addedCount(added.size),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }

        if (added.isEmpty()) {
            item {
                Text(
                    text = s.nothingYetSearchAbove,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = CookncoNavy.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }

        added.forEach { (index, ingredient) ->
            item(key = "ingredient_$index") {
                AddedIngredientCard(
                    ingredient = ingredient,
                    units = uiState.units,
                    onUnitChange = { viewModel.updateIngredientUnit(index, it) },
                    onAmountChange = { viewModel.updateIngredientAmount(index, it) },
                    onComplementChange = { viewModel.updateIngredientComplement(index, it) },
                    onRemove = { viewModel.removeIngredient(index) },
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** A row points at a catalogue ingredient, or has been confirmed as a custom one. */
private val EditIngredient.isResolved get() = ingredientId != null || customName != null

@Composable
private fun IngredientSearchField(query: String, onQueryChange: (String) -> Unit) {
    val s = strings()
    StickerCard(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        shadowOffset = 5.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(17.dp).border(2.5.dp, CookncoNavy, CircleShape))
            Box(modifier = Modifier.weight(1f)) {
                val textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = CookncoNavy)
                if (query.isEmpty()) {
                    Text(s.searchAnIngredient, style = textStyle.copy(color = CookncoNavy.copy(alpha = 0.4f)))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(CookncoOrange),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The results, listed in place rather than in a dropdown over the page: a dropdown covers
 * the very list you are adding to, and the artboard draws them as a card of their own.
 */
@Composable
private fun IngredientSearchResults(
    results: List<IngredientSummary>,
    query: String,
    onSelect: (IngredientSummary) -> Unit,
    onSelectCustom: () -> Unit,
) {
    val s = strings()
    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), shadowOffset = 5.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            results.forEach { result ->
                val name = result.displayName()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(result) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IngredientThumb(type = result.type, size = 30.dp, radius = 8.dp)
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CookncoNavy, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelectCustom)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.size(30.dp).dashedBorder(CookncoNavy, RoundedCornerShape(8.dp), strokeWidth = 2.dp))
                Text(
                    text = s.addAsCustom(query.trim()),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = CookncoNavy,
                )
            }
        }
    }
}

/** The catalogue picture for an ingredient type, or an empty tile for a custom one. */
@Composable
private fun IngredientThumb(type: String, size: Dp, radius: Dp) {
    val shape = RoundedCornerShape(radius)
    if (type.isNotEmpty()) {
        AsyncImage(
            model = "${ApiClient.IMAGE_URL}/ingredients/$type.webp",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape).background(CookncoGreenLight).border(2.dp, CookncoNavy, shape),
        )
    } else {
        Box(modifier = Modifier.size(size).clip(shape).background(CookncoGreenLight).border(2.dp, CookncoNavy, shape))
    }
}

/**
 * One ingredient already on the recipe: picture, name and note, then the amount and the
 * unit it is counted in. Both of those are white boxes on the cream card rather than
 * Material fields — the note under the name is editable in place, the way the artboard
 * shows it written.
 */
@Composable
private fun AddedIngredientCard(
    ingredient: EditIngredient,
    units: List<UnitInfo>,
    onUnitChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onComplementChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val s = strings()
    val availableUnits = unitsForIngredient(ingredient.allowedTypes, units)
    val showAmount = ingredient.unit != "NONE"
    var unitExpanded by remember { mutableStateOf(false) }

    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), shadowOffset = 5.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IngredientThumb(type = ingredient.type, size = 38.dp, radius = 10.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ingredient.ingredientName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CookncoNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    val noteStyle = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = CookncoGreenDark)
                    ingredient.originLabel(s)?.takeIf { ingredient.complement.isEmpty() }?.let { origin ->
                        Text(origin, style = noteStyle.copy(color = CookncoGreenDark.copy(alpha = 0.75f)))
                    }
                    BasicTextField(
                        value = ingredient.complement,
                        onValueChange = onComplementChange,
                        singleLine = true,
                        textStyle = noteStyle,
                        cursorBrush = SolidColor(CookncoOrange),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (showAmount) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CookncoWhite)
                        .border(2.dp, CookncoNavy, RoundedCornerShape(12.dp))
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicTextField(
                        value = ingredient.amount?.let { amount ->
                            if (amount % 1f == 0f) amount.toInt().toString() else amount.toString()
                        } ?: "",
                        onValueChange = onAmountChange,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CookncoNavy),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(CookncoOrange),
                        modifier = Modifier.widthIn(min = 26.dp, max = 46.dp),
                    )
                }
            }

            // The unit is the one control on the row that opens something, so the mockup
            // fills it gold — the same "this is a thing to press" gold as the Create button.
            StickerDropdownMenu(
                expanded = unitExpanded,
                onDismissRequest = { unitExpanded = false },
                items = availableUnits,
                label = { s.unitName(it.name) },
                selected = { it.name == ingredient.unit },
                sectionOf = { unitSectionLabel(it.type, s) },
                onSelect = { onUnitChange(it.name); unitExpanded = false },
                alignEnd = true,
                width = 196.dp,
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CookncoGold)
                        .border(2.5.dp, CookncoNavy, RoundedCornerShape(12.dp))
                        .clickable { unitExpanded = !unitExpanded }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(s.unitName(ingredient.unit), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                    Text(if (unitExpanded) "\u25b4" else "\u25be", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                }
            }

            Box(
                modifier = Modifier.size(28.dp).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = s.removeNamed(ingredient.ingredientName),
                    tint = CookncoOrangeDark,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── Step 3: Steps ───────────────────────────────────────────────────────────────

@Composable
private fun StepsStep(uiState: RecipeEditUiState, viewModel: RecipeEditViewModel, modifier: Modifier = Modifier) {
    val s = strings()
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyColumnState(lazyListState) { from, to ->
        val steps = uiState.steps
        val fromIdx = steps.indexOfFirst { it.id == from.key }
        val toIdx = steps.indexOfFirst { it.id == to.key }
        if (fromIdx != -1 && toIdx != -1) viewModel.reorderStep(fromIdx, toIdx)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StepHeading(title = s.stepSteps, subtitle = s.stepsSubtitle)
        }

        uiState.steps.forEachIndexed { index, step ->
            item(key = step.id) {
                ReorderableItem(reorderState, key = step.id) { isDragging ->
                    val elevation by animateDpAsState(targetValue = if (isDragging) 6.dp else 0.dp, label = "step_elevation")
                    val haptic = LocalHapticFeedback.current
                    StepEditCard(
                        index = index,
                        step = step,
                        elevation = elevation,
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        ),
                        onStepChange = { viewModel.updateStep(step.id, it) },
                        onRemove = { viewModel.removeStep(step.id) },
                    )
                }
            }
        }
        item {
            // The dashed "+ Add step" affordance replaces the old top-right circle button —
            // the mockup only ever shows this one way to add a step.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .dashedBorder(CookncoNavy, RoundedCornerShape(18.dp))
                    .clickable(onClick = viewModel::addStep),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                Text(s.addStep, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            }
        }
        item {
            SectionLabel(s.tipsOptional)
        }
        item {
            StickerCard(
                modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp),
                shape = RoundedCornerShape(16.dp),
                shadowOffset = 5.dp,
            ) {
                StickerTextArea(
                    value = uiState.tips,
                    onValueChange = viewModel::updateTips,
                    placeholder = s.tipsPlaceholder,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// The step card as drawn in option 5a: the number is a small caps label above the text
// inside the card, and drag + delete stack in a column on the right. (Turn 7 offered four
// other treatments for the number — this screen shipped 7b's gold column for a while; 5a
// is what the rest of the app is measured against, so it is what this follows.)
@Composable
private fun StepEditCard(
    index: Int,
    step: StepItem,
    elevation: Dp = 0.dp,
    dragHandleModifier: Modifier = Modifier,
    onStepChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val s = strings()
    StickerCard(
        modifier = Modifier.fillMaxWidth().shadow(elevation, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        shadowOffset = 5.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.stepNumber(index + 1),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoGreenDark,
                    letterSpacing = 1.4.sp,
                )
                StickerTextArea(
                    value = step.text,
                    onValueChange = onStepChange,
                    placeholder = s.describeThisStep,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(44.dp).then(dragHandleModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragIndicator,
                        contentDescription = s.dragToReorder,
                        tint = CookncoNavy,
                    )
                }
                Box(
                    modifier = Modifier.size(44.dp).clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = s.removeStep,
                        tint = CookncoOrangeDark,
                    )
                }
            }
        }
    }
}

// ── Step 4: Photo ───────────────────────────────────────────────────────────────

@Composable
private fun PhotoStep(
    uiState: RecipeEditUiState,
    viewModel: RecipeEditViewModel,
    pickedImage: PickedImage?,
    onImagePicked: (PickedImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val imagePicker = rememberImagePicker(onPicked = onImagePicked)
    val cameraCapture = rememberCameraCapture(onPicked = onImagePicked)
    val pickedBitmap = pickedImage?.let { picked ->
        remember(picked) { runCatching { picked.bytes.decodeToImageBitmap() }.getOrNull() }
    }
    // A version of 0 means the recipe is still on the backend's default placeholder —
    // RecipeInfo.version doubles as the image version (see ImageController on the backend).
    val hasExistingPhoto = uiState.recipeId != null && (uiState.recipeVersion ?: 0) > 0

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StepHeading(title = s.photoAndPublish, subtitle = s.photoSubtitle)
        }
        item {
            StickerCard(modifier = Modifier.fillMaxWidth(), shadowOffset = 6.dp) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(CookncoGreenLight)
                            .clickable { imagePicker.launch() },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            pickedBitmap != null -> Image(
                                bitmap = pickedBitmap,
                                contentDescription = s.recipePhoto,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            hasExistingPhoto -> RecipeImage(
                                recipeId = uiState.recipeId!!,
                                version = uiState.recipeVersion!!,
                                contentDescription = s.recipePhoto,
                                thumbnail = false,
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = CookncoNavy.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                                Text(s.tapToAddPhoto, color = CookncoNavy.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(CookncoNavy))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CookncoBackground)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PhotoActionButton(text = s.takeAPhoto, onClick = { cameraCapture.launch() }, modifier = Modifier.weight(1f))
                        PhotoActionButton(text = s.chooseAnother, onClick = { imagePicker.launch() }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (pickedImage != null) {
            item {
                Text(
                    text = s.uploadedWhenYouSave,
                    color = CookncoNavy.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                )
            }
        }
        item {
            Column {
                SectionLabel(s.readyToPublish)
                PublishChecklist(uiState = uiState)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PhotoActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CookncoWhite)
            .border(2.dp, CookncoNavy, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

// A read-only recap of what's been filled in so far, matching the mockup's "ready to
// publish" summary — it reads the same state the other three steps already collected,
// nothing new is computed or validated here.
@Composable
private fun PublishChecklist(uiState: RecipeEditUiState, modifier: Modifier = Modifier) {
    val s = strings()
    val dishClassLabel = s.dishClassName(uiState.dishClass)
    val ingredientCount = uiState.ingredients.count { it.ingredientId != null || it.customName != null }
    val catalogCount = uiState.ingredients.count { it.ingredientId != null }
    val ingredientsLine = when {
        ingredientCount == 0 -> s.noIngredientsYet
        catalogCount == ingredientCount -> s.ingredientsAllFromCatalogue(ingredientCount)
        else -> s.ingredientsAdded(ingredientCount)
    }
    val stepCount = uiState.steps.count { it.text.isNotBlank() }
    val stepsLine = if (uiState.cookTime.isNotBlank()) {
        s.stepsCount(stepCount) + " · " + s.cookMinutes(uiState.cookTime)
    } else {
        s.stepsCount(stepCount)
    }

    StickerCard(modifier = modifier.fillMaxWidth(), shadowOffset = 6.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ChecklistRow(
                title = if (uiState.title.isNotBlank()) "${uiState.title} · ${dishClassLabel.lowercase()}" else s.untitledRecipe,
                trailing = s.stepBasics + " ✓",
                showDivider = true,
            )
            ChecklistRow(title = ingredientsLine, trailing = "✓", showDivider = true)
            ChecklistRow(title = stepsLine, trailing = "✓", showDivider = false)
        }
    }
}

@Composable
private fun ChecklistRow(title: String, trailing: String, showDivider: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = CookncoNavy, modifier = Modifier.weight(1f))
            Text(trailing, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(CookncoNavy.copy(alpha = 0.1f)))
        }
    }
}
