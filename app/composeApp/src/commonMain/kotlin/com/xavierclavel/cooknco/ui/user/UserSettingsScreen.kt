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
import androidx.compose.material.icons.automirrored.outlined.Logout
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
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

/**
 * The account preferences [UserSettingsViewModel] already loads/saves, plus the log-out
 * action that used to sit directly behind [UserProfileScreen]'s gear icon (see
 * `MainScreen`'s former `showLogoutConfirm`). The confirm dialog now lives here instead,
 * local to this screen, since logging out is a settings action rather than a bare tap.
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
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
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
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Spacer(Modifier.height(2.dp))

                // ── Account preferences ─────────────────────────────────────────
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        SettingsToggleRow(
                            title = "Automatically accept follow requests",
                            description = "Anyone can follow you without asking",
                            checked = uiState.autoAcceptFollowRequests,
                            onCheckedChange = { viewModel.toggleAutoAccept() },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = CookncoNavy.copy(alpha = 0.12f),
                        )
                        SettingsToggleRow(
                            title = "Public account",
                            description = "Your profile and recipes are visible to everyone",
                            checked = uiState.isAccountPublic,
                            onCheckedChange = { viewModel.toggleAccountPublic() },
                        )
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

                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    fillColor = CookncoOrange,
                    shadowOffset = 4.dp,
                    onClick = viewModel::save,
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).align(Alignment.Center),
                            strokeWidth = 2.dp,
                            color = CookncoWhite,
                        )
                    } else {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CookncoWhite,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                // ── Log out ──────────────────────────────────────────────────────
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = CookncoNavy.copy(alpha = 0.15f))
                Spacer(Modifier.height(2.dp))

                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    shadowOffset = 4.dp,
                    onClick = { showLogoutConfirm = true },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = CookncoNavy)
                        Text("Log out", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CookncoNavy)
                    }
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
)

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            Text(
                text = description,
                fontSize = 12.5.sp,
                color = CookncoNavy.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = settingsSwitchColors())
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
                Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        SettingsToggleRow(
                            title = "Automatically accept follow requests",
                            description = "Anyone can follow you without asking",
                            checked = true,
                            onCheckedChange = {},
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = CookncoNavy.copy(alpha = 0.12f))
                        SettingsToggleRow(
                            title = "Public account",
                            description = "Your profile and recipes are visible to everyone",
                            checked = false,
                            onCheckedChange = {},
                        )
                    }
                }
            }
        }
    }
}
