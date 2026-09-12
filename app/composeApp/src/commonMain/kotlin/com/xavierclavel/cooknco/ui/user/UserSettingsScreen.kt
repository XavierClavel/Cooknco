package com.xavierclavel.cooknco.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.platform.appVersion
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

/**
 * Matches the mockup's "Settings" artboard (`Cooknco Mobile.dc.html`, turn 5 / option `5a`,
 * lines 1221-1283): a PRIVACY card backed by [UserSettingsViewModel], an ACCOUNT card, and a
 * standalone log-out row. The mockup has no Save button — each toggle applies immediately —
 * and no confirmation-modal artwork for log out, so that dialog stays a plain
 * [AlertDialog] rather than moving to [com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog]
 * (that shared component's mockup reference is "Delete recipe — confirmation", a different
 * screen; nothing here shows the same treatment for logging out).
 *
 * The mockup's LANGUAGE, NOTIFICATIONS, and ACCOUNT "Change password" / "MCP access" rows are
 * not reproduced: [UserSettingsViewModel] carries no state for them and none of those
 * destinations exist yet elsewhere in the app, so rendering them here would be either a dead
 * control (a language switch or notification toggle nothing backs) or a dead link. "App
 * version" is included since it reads a real value ([appVersion]).
 */
@Composable
fun UserSettingsScreen(
    viewModel: UserSettingsViewModel,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    isLoggingOut: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }

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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp, start = 18.dp, end = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // ── Privacy ──────────────────────────────────────────────────────
                Column {
                    SettingsSectionLabel("PRIVACY")
                    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsToggleRow(
                                title = "Public account",
                                description = "Anyone can see your recipes",
                                checked = uiState.isAccountPublic,
                                onCheckedChange = {
                                    viewModel.toggleAccountPublic()
                                    viewModel.save()
                                },
                                modifier = Modifier.padding(14.dp),
                            )
                            HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
                            SettingsToggleRow(
                                title = "Auto-accept follow requests",
                                description = if (uiState.isAccountPublic) {
                                    "Always on while your account is public"
                                } else {
                                    "Anyone can follow you without asking"
                                },
                                checked = uiState.isAccountPublic || uiState.autoAcceptFollowRequests,
                                enabled = !uiState.isAccountPublic,
                                onCheckedChange = {
                                    viewModel.toggleAutoAccept()
                                    viewModel.save()
                                },
                                modifier = Modifier.padding(14.dp),
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Account ──────────────────────────────────────────────────────
                Column {
                    SettingsSectionLabel("ACCOUNT")
                    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "App version",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = CookncoNavy,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = appVersion.ifBlank { "—" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = CookncoGreenDark,
                            )
                        }
                    }
                }

                // ── Log out ──────────────────────────────────────────────────────
                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    shadowOffset = 4.dp,
                    onClick = { showLogoutConfirm = true },
                ) {
                    Text(
                        text = "Log out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showLogoutConfirm) {
        // Nothing dismisses this once the sign-out is in flight, same reasoning as the
        // dialog this replaced in MainScreen: the session is already being torn down.
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

/** An 11sp bold, letter-spaced caps label above a settings card — "PRIVACY", "ACCOUNT". */
@Composable
private fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = CookncoNavy,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 2.dp, bottom = 10.dp),
    )
}

@Composable
private fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = CookncoWhite,
    checkedTrackColor = CookncoOrange,
    checkedBorderColor = CookncoNavy,
    checkedIconColor = CookncoOrange,
    uncheckedThumbColor = CookncoNavy,
    uncheckedTrackColor = CookncoBackground,
    uncheckedBorderColor = CookncoNavy.copy(alpha = 0.5f),
    uncheckedIconColor = CookncoBackground,
    // The "forced on, locked" look auto-accept takes on while the account is public — a
    // faded track/border rather than the normal orange, matching the mockup's greyed switch.
    disabledCheckedThumbColor = CookncoBackground,
    disabledCheckedTrackColor = CookncoNavy.copy(alpha = 0.12f),
    disabledCheckedBorderColor = CookncoNavy.copy(alpha = 0.4f),
    disabledCheckedIconColor = CookncoNavy.copy(alpha = 0.4f),
    disabledUncheckedThumbColor = CookncoNavy.copy(alpha = 0.4f),
    disabledUncheckedTrackColor = CookncoBackground,
    disabledUncheckedBorderColor = CookncoNavy.copy(alpha = 0.3f),
    disabledUncheckedIconColor = CookncoBackground,
)

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val textColor = if (enabled) CookncoNavy else CookncoNavy.copy(alpha = 0.5f)
    val descriptionColor = if (enabled) CookncoGreenDark else CookncoNavy.copy(alpha = 0.5f)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(
                text = description,
                fontSize = 12.sp,
                color = descriptionColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = settingsSwitchColors(),
        )
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun UserSettingsScreenPreview() {
    CookncoTheme {
        Column(modifier = Modifier.fillMaxSize().background(CookncoGreen)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StickerIconButton(onClick = {}, shadowOffset = 3.dp) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 18.dp, end = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column {
                    SettingsSectionLabel("PRIVACY")
                    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsToggleRow(
                                title = "Public account",
                                description = "Anyone can see your recipes",
                                checked = true,
                                onCheckedChange = {},
                                modifier = Modifier.padding(14.dp),
                            )
                            HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
                            SettingsToggleRow(
                                title = "Auto-accept follow requests",
                                description = "Always on while your account is public",
                                checked = true,
                                enabled = false,
                                onCheckedChange = {},
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
                Column {
                    SettingsSectionLabel("ACCOUNT")
                    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("App version", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, modifier = Modifier.weight(1f))
                            Text("1.4.0", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CookncoGreenDark)
                        }
                    }
                }
                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    shadowOffset = 4.dp,
                ) {
                    Text(
                        text = "Log out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}
