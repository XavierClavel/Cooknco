package com.xavierclavel.cooknco.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite

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
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(40.dp))

            // App name above the card
            Text(
                text = "Cook'n'Co",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = CookncoOrange,
            )

            Spacer(Modifier.height(24.dp))

            // Main green card — matches the website's green card style
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CookncoGreen),
                border = BorderStroke(2.dp, CookncoNavy),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Login",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        textAlign = TextAlign.Center,
                    )

                    // Google button
                    Button(
                        onClick = onLoginWithGoogle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CookncoWhite,
                            contentColor = CookncoNavy,
                        ),
                        border = BorderStroke(1.5.dp, CookncoNavy),
                    ) {
                        Text(
                            text = "G  Continue with Google",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }

                    OrDivider()

                    AuthTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        placeholder = "Email address",
                        leadingIcon = Icons.Outlined.Email,
                        isError = state.error != null,
                    )

                    AuthTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        placeholder = "Password",
                        leadingIcon = Icons.Outlined.Lock,
                        isPassword = true,
                        isPasswordVisible = state.isPasswordVisible,
                        onTogglePasswordVisibility = onTogglePasswordVisibility,
                        isError = state.error != null,
                    )

                    if (state.error != null) {
                        Text(
                            text = state.error,
                            color = CookncoBackground,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Forgot password link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Forgotten password?",
                            color = CookncoNavy,
                            fontSize = 14.sp,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }

                    // Sign up button (navigate away)
                    AuthButton(
                        text = "Sign up",
                        onClick = onNavigateToSignup,
                        leadingIcon = Icons.Outlined.PersonAdd,
                    )

                    // Log in button (primary action)
                    AuthButton(
                        text = "Log in",
                        onClick = onLogin,
                        leadingIcon = Icons.Outlined.Login,
                        isLoading = state.isLoading,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
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
