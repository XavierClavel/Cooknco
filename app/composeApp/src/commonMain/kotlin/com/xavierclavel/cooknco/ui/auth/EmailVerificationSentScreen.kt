package com.xavierclavel.cooknco.ui.auth

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme

@Composable
fun EmailVerificationSentScreen(
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = CookncoGreen,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AuthCard {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MarkEmailRead,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = CookncoOrange,
                    )

                    Text(
                        text = "Check your email",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = "We've sent a verification link to your address. Click it to activate your account and start cooking!",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = CookncoNavy.copy(alpha = 0.75f),
                    )

                    Spacer(Modifier.height(4.dp))

                    AuthButton(
                        text = "Back to Login",
                        onClick = onBackToLogin,
                        leadingIcon = Icons.Outlined.Login,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EmailVerificationSentScreenPreview() {
    CookncoTheme {
        EmailVerificationSentScreen(onBackToLogin = {})
    }
}
