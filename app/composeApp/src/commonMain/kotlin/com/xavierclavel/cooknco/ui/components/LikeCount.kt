package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.ui.theme.CookncoNavy

/**
 * A recipe's like count: a heart, then the number, both in [color].
 *
 * The heart is the vector icon and **not** the "♥" character it used to be. U+2665 has an
 * emoji presentation, so Android's font fallback paints it from the color emoji font — a
 * red heart, whatever color the text is given, which on the coral badges read as red on
 * coral. A vector honours [color] on every platform.
 */
@Composable
fun LikeCount(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = CookncoNavy,
    fontSize: TextUnit = 12.5.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    iconSize: Dp = 13.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize),
        )
        Text(count.toString(), fontSize = fontSize, fontWeight = fontWeight, color = color)
    }
}
