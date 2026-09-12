package com.xavierclavel.cooknco.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoBlueDark
import com.xavierclavel.cooknco.ui.theme.CookncoBlueLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrangeDark
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerSegmentedControl
import com.xavierclavel.cooknco.ui.theme.StickerToggle

/**
 * Matches the mockup's "Settings" artboard (`Cooknco Mobile.dc.html`, turn 5 / option `5a`,
 * lines 1221-1283): a PRIVACY card backed by [UserSettingsViewModel], an ACCOUNT card, and a
 * standalone log-out row. The mockup has no Save button — each toggle applies immediately.
 * Its toggles are the theme's own pill ([StickerToggle]), not Material's switch, and the
 * log-out confirmation is the theme's modal: the mockup draws no confirmation for logging
 * out, but every other one in the app is a [StickerConfirmDialog], and Material's dialog
 * chrome in the middle of this screen is the thing that reads as out of place.
 *
 * Every row here writes something real: LANGUAGE and "Email notifications" are
 * `UserSettingsDTO.locale` / `mailNotificationsEnabled`, "Push on this device" registers or
 * detaches this handset, "Change password" is `PUT /user/password`, and "MCP access" lists
 * the clients this account has approved — the grants an
 * `/user/mcp-clients`, and it is the count of those that the badge shows.
 */
@Composable
fun UserSettingsScreen(
    viewModel: UserSettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPassword: () -> Unit,
    onNavigateToMcpClients: () -> Unit,
    onLogout: () -> Unit,
    isLoggingOut: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val s = strings()
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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back)
            }
            Text(s.settings, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
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
                // ── Language ─────────────────────────────────────────────────────
                Column {
                    SettingsSectionLabel(s.language)
                    StickerSegmentedControl(
                        options = AccountLocale.entries,
                        selected = uiState.locale,
                        onSelect = viewModel::selectLocale,
                        label = { it.label },
                        shape = RoundedCornerShape(20.dp),
                        segmentShape = RoundedCornerShape(15.dp),
                        spacing = 5.dp,
                        shadowOffset = 6.dp,
                    )
                    Text(
                        text = s.languageNote,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = CookncoGreenDark,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                    )
                }

                // ── Privacy ──────────────────────────────────────────────────────
                Column {
                    SettingsSectionLabel(s.privacy)
                    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsToggleRow(
                                title = s.publicAccount,
                                description = s.publicAccountNote,
                                checked = uiState.isAccountPublic,
                                onCheckedChange = { viewModel.toggleAccountPublic() },
                                modifier = Modifier.padding(14.dp),
                            )
                            HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
                            SettingsToggleRow(
                                title = s.autoAcceptFollows,
                                description = if (uiState.isAccountPublic) {
                                    s.autoAcceptAlwaysOn
                                } else {
                                    s.autoAcceptAnyone
                                },
                                checked = uiState.isAccountPublic || uiState.autoAcceptFollowRequests,
                                enabled = !uiState.isAccountPublic,
                                onCheckedChange = { viewModel.toggleAutoAccept() },
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }

                // ── Notifications ────────────────────────────────────────────────
                Column {
                    SettingsSectionLabel(s.notifications)
                    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsToggleRow(
                                title = s.pushOnThisDevice,
                                description = s.pushNote,
                                checked = uiState.pushEnabled,
                                onCheckedChange = { viewModel.togglePush() },
                                modifier = Modifier.padding(14.dp),
                            )
                            HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
                            SettingsToggleRow(
                                title = s.emailNotifications,
                                description = s.emailNotificationsNote,
                                checked = uiState.mailNotificationsEnabled,
                                onCheckedChange = { viewModel.toggleMailNotifications() },
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = CookncoOrangeDark,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Account ──────────────────────────────────────────────────────
                Column {
                    SettingsSectionLabel(s.account)
                    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onNavigateToPassword)
                                    .padding(horizontal = 14.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = s.changePassword,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CookncoNavy,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
                            }
                            HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onNavigateToMcpClients)
                                    .padding(horizontal = 14.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = s.mcpAccess,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CookncoNavy,
                                    modifier = Modifier.weight(1f),
                                )
                                // Only shown once there is something to count: an empty badge
                                // would say "none connected" in the loudest way on the screen.
                                if (uiState.mcpClientCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .background(CookncoBlueLight, RoundedCornerShape(percent = 50))
                                            .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                                            .padding(horizontal = 9.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = s.clientCount(uiState.mcpClientCount),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CookncoBlueDark,
                                        )
                                    }
                                }
                                Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
                            }
                            HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = s.appVersion,
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
                }

                // ── Log out ──────────────────────────────────────────────────────
                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    shadowOffset = 4.dp,
                    onClick = { showLogoutConfirm = true },
                ) {
                    Text(
                        text = s.logOut,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CookncoOrangeDark,
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
        StickerConfirmDialog(
            icon = Icons.AutoMirrored.Outlined.Logout,
            title = s.logOut,
            message = s.logOutMessage,
            confirmText = s.logOut,
            isConfirming = isLoggingOut,
            onConfirm = onLogout,
            onDismissRequest = { showLogoutConfirm = false },
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
        StickerToggle(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
                        color = CookncoOrangeDark,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}
