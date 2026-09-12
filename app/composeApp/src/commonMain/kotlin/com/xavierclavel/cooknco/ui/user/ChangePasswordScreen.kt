package com.xavierclavel.cooknco.ui.user

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoOrangeDark
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.stickerSwitchSpec

/**
 * The settings screen's "Change password" destination (`PUT /user/password`).
 *
 * The current password is asked for because the backend checks it — a session alone is not
 * enough to change it, which is what stops a borrowed unlocked phone from locking the owner
 * out. The new one is typed twice here rather than server-side, since the backend has no
 * idea what the second field said.
 *
 * A successful change clears the session on the backend (`call.sessions.clear`), so the
 * screen says so rather than pretending nothing happened.
 */
@Composable
fun ChangePasswordScreen(
    viewModel: ChangePasswordViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val uiState by viewModel.uiState.collectAsState()
    var showPasswords by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.changed) { if (uiState.changed) onNavigateBack() }

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerIconButton(onClick = onNavigateBack, shadowOffset = 3.dp) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back)
            }
            Text(s.changePassword, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowOffset = 6.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PasswordField(
                        caption = s.currentPasswordCaps,
                        value = uiState.current,
                        onValueChange = viewModel::updateCurrent,
                        visible = showPasswords,
                    )
                    PasswordField(
                        caption = s.newPasswordCaps,
                        value = uiState.new,
                        onValueChange = viewModel::updateNew,
                        visible = showPasswords,
                    )
                    PasswordField(
                        caption = s.newPasswordAgainCaps,
                        value = uiState.confirm,
                        onValueChange = viewModel::updateConfirm,
                        visible = showPasswords,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        StickerPill(
                            height = 40.dp,
                            borderWidth = 2.dp,
                            shadowOffset = 0.dp,
                            onClick = { showPasswords = !showPasswords },
                        ) {
                            Text(
                                text = if (showPasswords) s.hide else s.show,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = CookncoNavy,
                            )
                        }
                        Text(
                            text = s.atLeastEightCharacters,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = CookncoGreenDark,
                        )
                    }
                }
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = CookncoOrangeDark,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }

            StickerCard(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                fillColor = if (uiState.canSubmit) CookncoOrange else CookncoOrange.copy(alpha = 0.5f),
                shadowOffset = 4.dp,
                onClick = { if (uiState.canSubmit) viewModel.submit() },
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).align(Alignment.Center),
                        strokeWidth = 2.dp,
                        color = CookncoWhite,
                    )
                } else {
                    Text(
                        text = s.savePasswordCaps,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = CookncoWhite,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Text(
                text = s.changingPasswordSignsOut,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = CookncoGreenDark,
                modifier = Modifier.padding(start = 2.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun PasswordField(
    caption: String,
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) CookncoOrange else CookncoNavy,
        animationSpec = stickerSwitchSpec(),
        label = "password_border",
    )
    Column {
        Text(
            text = caption,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoGreenDark,
            letterSpacing = 0.7.sp,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .background(CookncoWhite, RoundedCornerShape(12.dp))
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CookncoNavy),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                cursorBrush = SolidColor(CookncoOrange),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
    }
}
