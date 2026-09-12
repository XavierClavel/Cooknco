package com.xavierclavel.cooknco.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.resources.Res
import com.xavierclavel.cooknco.resources.logo
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLogin: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onForgotPassword: () -> Unit,
    onLoginWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Surface(
        modifier = modifier.fillMaxSize(),
        color = CookncoGreen,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = "Cook'n'Co",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(140.dp)
                    .padding(bottom = 22.dp),
            )

            AuthCard {
                Text(
                    text = s.logIn,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                )

                GoogleButton(text = s.continueWithGoogle, onClick = onLoginWithGoogle)

                OrDivider()

                AuthTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    placeholder = s.emailAddress,
                    leadingIcon = Icons.Outlined.Email,
                    isError = state.error != null,
                )

                AuthTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    placeholder = s.password,
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    isPasswordVisible = state.isPasswordVisible,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                    isError = state.error != null,
                )

                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AuthButton(text = s.logIn, onClick = onLogin, isLoading = state.isLoading)

                Text(
                    text = s.forgottenPassword,
                    color = CookncoNavy.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onForgotPassword),
                )
            }

            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = s.newHere + " ", color = CookncoNavy, fontSize = 14.sp)
                Text(
                    text = s.createAnAccount,
                    color = CookncoNavy,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onNavigateToSignup),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    CookncoTheme {
        LoginScreen(
            state = LoginUiState(),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onLogin = {},
            onNavigateToSignup = {},
            onForgotPassword = {},
            onLoginWithGoogle = {},
        )
    }
}

@Preview(showBackground = true, name = "Login - Error")
@Composable
fun LoginScreenErrorPreview() {
    CookncoTheme {
        LoginScreen(
            state = LoginUiState(
                email = "user@example.com",
                password = "wrong",
                error = "Invalid email or password",
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onLogin = {},
            onNavigateToSignup = {},
            onForgotPassword = {},
            onLoginWithGoogle = {},
        )
    }
}

@Preview(showBackground = true, name = "Login - Loading")
@Composable
fun LoginScreenLoadingPreview() {
    CookncoTheme {
        LoginScreen(
            state = LoginUiState(
                email = "user@example.com",
                password = "mypassword",
                isLoading = true,
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onLogin = {},
            onNavigateToSignup = {},
            onForgotPassword = {},
            onLoginWithGoogle = {},
        )
    }
}
