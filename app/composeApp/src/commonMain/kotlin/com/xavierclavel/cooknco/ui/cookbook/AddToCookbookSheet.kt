package com.xavierclavel.cooknco.ui.cookbook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.recipe.CookbookPickerState
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.stickerSwitchSpec

/**
 * Picks which cookbooks a recipe belongs to.
 *
 * A list of toggles rather than a one-shot "add to…" picker, because the question a cook
 * actually has is "is this in my Weeknights book?" — and the backend answers exactly that
 * in one request (`GET /cookbook/recipeStatus`), so showing the current state costs
 * nothing and leaving it out would mean adding a recipe twice to find out.
 *
 * The sheet stays open after a tap. Sorting a recipe into two or three books is one visit,
 * and there is nothing to confirm: each toggle is already saved.
 */
@Composable
fun AddToCookbookSheet(
    state: CookbookPickerState,
    onToggle: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.55f))
                .clickable(onClick = onDismissRequest),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 26.dp),
            ) {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 12.dp)) {
                            Text(s.addToCookbook, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                            Text(
                                text = s.addToCookbookHint,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CookncoGreenDark,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Divider()

                        when {
                            state.isLoading -> Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp) }

                            state.cookbooks.isEmpty() -> Text(
                                text = s.noCookbooksYet,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CookncoGreenDark,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 26.dp),
                            )

                            else -> LazyColumn(
                                // Capped so a cook with thirty cookbooks gets a scrolling
                                // sheet rather than one that runs off the top of the screen.
                                modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                            ) {
                                items(state.cookbooks, key = { it.id }) { cookbook ->
                                    CookbookToggleRow(
                                        title = cookbook.title,
                                        checked = cookbook.hasRecipe,
                                        busy = cookbook.id in state.busy,
                                        onClick = { onToggle(cookbook.id) },
                                    )
                                    Divider()
                                }
                            }
                        }

                        if (state.error != null) {
                            Text(
                                text = state.error,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    shadowOffset = 4.dp,
                    onClick = onDismissRequest,
                ) {
                    Text(
                        // Not "Cancel": every toggle is already saved, so there is nothing
                        // here to back out of.
                        text = s.done,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun CookbookToggleRow(
    title: String,
    checked: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val s = strings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // The row dims rather than showing a spinner of its own: the tick has already moved,
        // and a spinner would say the opposite.
        val rowAlpha by animateFloatAsState(if (busy) 0.5f else 1f, stickerSwitchSpec())
        Box(
            modifier = Modifier
                .size(26.dp)
                .alpha(rowAlpha)
                .background(
                    color = if (checked) CookncoOrange else CookncoBackground,
                    shape = RoundedCornerShape(8.dp),
                )
                .border(2.dp, CookncoNavy, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = s.inThisCookbook,
                    tint = CookncoWhite,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(CookncoNavy.copy(alpha = 0.12f)),
    )
}
