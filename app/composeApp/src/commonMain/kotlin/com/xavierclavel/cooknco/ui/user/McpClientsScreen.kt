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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.dto.McpClientInfo
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrangeDark
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

/**
 * The settings screen's "MCP access": the clients this account has let into its recipes
 * through `/mcp`, and the way to take that back.
 *
 * A client's name is its own claim — it chose it when it registered — so the URI its codes
 * are sent to is shown underneath, exactly as the consent page does. That address is the one
 * thing about a client that cannot be a lie, and it is what tells two "Claude"s apart.
 */
@Composable
fun McpClientsScreen(
    viewModel: McpClientsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingRevoke by remember { mutableStateOf<McpClientInfo?>(null) }

    // Closes once the client is actually gone, not on tapping Revoke — the dialog's own
    // isConfirming lock keeps it up while the request is in flight.
    LaunchedEffect(uiState.clients) {
        val stillThere = pendingRevoke?.let { p -> uiState.clients.any { it.clientId == p.clientId } } ?: true
        if (!stillThere) pendingRevoke = null
    }

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
            Text("MCP access", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 10.dp),
        ) {
            Text(
                text = "Apps you have connected to your recipes. Each can read and write them as you, " +
                    "until you revoke it here.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = CookncoNavy,
                modifier = Modifier.padding(bottom = 14.dp, start = 2.dp),
            )

            when {
                uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }

                uiState.clients.isEmpty() -> StickerCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = "Nothing connected. Adding this account to an MCP client sends you to a " +
                            "consent page first — whatever you allow there shows up here.",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = CookncoNavy.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp),
                    )
                }

                else -> StickerCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    shadowOffset = 6.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        uiState.clients.forEachIndexed { index, client ->
                            McpClientRow(
                                client = client,
                                isRevoking = uiState.revokingClientId == client.clientId,
                                onRevoke = { pendingRevoke = client },
                            )
                            if (index != uiState.clients.lastIndex) {
                                HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = CookncoOrangeDark,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, start = 2.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    pendingRevoke?.let { client ->
        StickerConfirmDialog(
            icon = Icons.Outlined.LinkOff,
            title = "Revoke ${client.clientName}?",
            message = "It loses access to your recipes straight away, even if it is connected right now. " +
                "You can connect it again from the app itself.",
            confirmText = "Revoke",
            isConfirming = uiState.revokingClientId == client.clientId,
            onConfirm = { viewModel.revoke(client.clientId) },
            onDismissRequest = { pendingRevoke = null },
        )
    }
}

@Composable
private fun McpClientRow(client: McpClientInfo, isRevoking: Boolean, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = client.clientName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            client.redirectUris.firstOrNull()?.let { uri ->
                Text(
                    text = uri,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = CookncoGreenDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = followedSinceLabel("Connected", client.grantedAt) +
                    " · " + followedSinceLabel("used", client.lastUsedAt).lowercase(),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = CookncoNavy.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (isRevoking) {
            CircularProgressIndicator(color = CookncoNavy, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            FollowRowTextAction(text = "Revoke", onClick = onRevoke)
        }
    }
}
