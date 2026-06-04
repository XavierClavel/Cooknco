package com.xavierclavel.cooknco.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite

@Composable
fun SignupScreen(
    state: SignupUiState,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onSignup: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onSignupWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(40.dp))

            Text(
                text = "Cook'n'Co",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = CookncoOrange,
            )

            Spacer(Modifier.height(24.dp))

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
                        text = "Sign up",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        textAlign = TextAlign.Center,
                    )

                    // Google button
                    Button(
                        onClick = onSignupWithGoogle,
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
                        value = state.username,
                        onValueChange = onUsernameChange,
                        placeholder = "Username",
                        leadingIcon = Icons.Outlined.AccountCircle,
                        isError = state.error != null,
                    )

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
                        placeholder = "Password (min. 8 characters)",
                        leadingIcon = Icons.Outlined.Lock,
                        isPassword = true,
                        isPasswordVisible = state.isPasswordVisible,
                        onTogglePasswordVisibility = onTogglePasswordVisibility,
                        isError = state.error != null,
                    )

                    AuthTextField(
                        value = state.confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        placeholder = "Confirm password",
                        leadingIcon = Icons.Outlined.LockOpen,
                        isPassword = true,
                        isPasswordVisible = state.isConfirmPasswordVisible,
                        onTogglePasswordVisibility = onToggleConfirmPasswordVisibility,
                        isError = state.error != null,
                    )

                    if (state.error != null) {
                        Text(
                            text = state.error,
                            color = CookncoWhite,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Back to login
                    AuthButton(
                        text = "Log in",
                        onClick = onNavigateToLogin,
                        leadingIcon = Icons.Outlined.ArrowBack,
                    )

                    // Sign up action
                    AuthButton(
                        text = "Sign up",
                        onClick = onSignup,
                        leadingIcon = Icons.Outlined.HowToReg,
                        isLoading = state.isLoading,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SignupScreenPreview() {
    CookncoTheme {
        SignupScreen(
            state = SignupUiState(),
            onUsernameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onTogglePasswordVisibility = {},
            onToggleConfirmPasswordVisibility = {},
            onSignup = {},
            onNavigateToLogin = {},
            onSignupWithGoogle = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Signup - Error")
@Composable
fun SignupScreenErrorPreview() {
    CookncoTheme {
        SignupScreen(
            state = SignupUiState(
                username = "xavier",
                email = "xavier@example.com",
                password = "short",
                confirmPassword = "short",
                error = "Password must be at least 8 characters",
            ),
            onUsernameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onTogglePasswordVisibility = {},
            onToggleConfirmPasswordVisibility = {},
            onSignup = {},
            onNavigateToLogin = {},
            onSignupWithGoogle = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Signup - Loading")
@Composable
fun SignupScreenLoadingPreview() {
    CookncoTheme {
        SignupScreen(
            state = SignupUiState(
                username = "xavier",
                email = "xavier@example.com",
                password = "mypassword8",
                confirmPassword = "mypassword8",
                isLoading = true,
            ),
            onUsernameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onTogglePasswordVisibility = {},
            onToggleConfirmPasswordVisibility = {},
            onSignup = {},
            onNavigateToLogin = {},
            onSignupWithGoogle = {},
        )
    }
}
