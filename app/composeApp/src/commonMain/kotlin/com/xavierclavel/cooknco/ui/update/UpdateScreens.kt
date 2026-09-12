package com.xavierclavel.cooknco.ui.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xavierclavel.cooknco.ui.auth.AuthButton
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange

/**
 * The end of the road for a build the backend will not let run.
 *
 * A screen rather than a dialog, and with no way past it: it replaces the app entirely, so
 * there is nothing behind it to dismiss back to. That is the whole point of the feature and
 * also its danger — see `AppVersionRepository`, which reaches this state only on an explicit
 * answer from the server and never on a failure to get one.
 */
@Composable
fun UpdateRequiredScreen(
    latestVersion: String,
    onOpenStore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CookncoGreen),
                border = BorderStroke(2.dp, CookncoNavy),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = CookncoOrange,
                    )

                    Text(
                        text = s.timeToUpdate,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = s.updateRequiredMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = CookncoBackground,
                    )

                    if (latestVersion.isNotBlank()) {
                        Text(
                            text = s.latestVersion(latestVersion),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = CookncoBackground,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Hidden rather than disabled when there is nowhere to send them: a
                    // button that does nothing reads as a broken app rather than as a
                    // missing link.
                    if (onOpenStore != null) {
                        AuthButton(
                            text = s.updateNow,
                            onClick = onOpenStore,
                            leadingIcon = Icons.Outlined.SystemUpdate,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The softer half: a newer build exists, and the user may carry on regardless.
 *
 * Said once per launch — [onDismiss] is what makes that true — because an update prompt
 * that returns on every screen is one people learn to tap away without reading.
 */
@Composable
fun UpdateAvailableDialog(
    latestVersion: String,
    onOpenStore: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val s = strings()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = CookncoOrange,
            )
        },
        title = { Text(s.updateAvailable, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (latestVersion.isNotBlank()) "Cook&Co $latestVersion is out. Get the newest recipes, fixes and features."
                else "A newer version of Cook&Co is out. Get the newest recipes, fixes and features.",
            )
        },
        confirmButton = {
            if (onOpenStore != null) {
                TextButton(onClick = { onOpenStore(); onDismiss() }) { Text(s.update) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.notNow) }
        },
    )
}
