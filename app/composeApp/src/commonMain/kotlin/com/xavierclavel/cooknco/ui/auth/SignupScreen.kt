package com.xavierclavel.cooknco.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
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
                    // Sized on its width, not its height: the mark is a wide badge, so
                    // 140.dp of height left it covering barely half the screen. The cap
                    // stops it becoming a billboard on a tablet.
                    .fillMaxWidth(0.78f)
                    .widthIn(max = 340.dp)
                    .padding(bottom = 24.dp),
            )

            AuthCard {
                Text(
                    text = s.signUp,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                )

                GoogleButton(text = s.continueWithGoogle, onClick = onSignupWithGoogle)

                OrDivider()

                AuthTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    placeholder = s.username,
                    leadingIcon = Icons.Outlined.AccountCircle,
                    isError = state.error != null,
                )

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
                    placeholder = s.passwordMinEightPlaceholder,
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    isPasswordVisible = state.isPasswordVisible,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                    isError = state.error != null,
                )

                AuthTextField(
                    value = state.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    placeholder = s.confirmPassword,
                    leadingIcon = Icons.Outlined.LockOpen,
                    isPassword = true,
                    isPasswordVisible = state.isConfirmPasswordVisible,
                    onTogglePasswordVisibility = onToggleConfirmPasswordVisibility,
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

                AuthButton(text = s.signUp, onClick = onSignup, isLoading = state.isLoading)
            }

            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = s.alreadyHaveAnAccount + " ", color = CookncoNavy, fontSize = 14.sp)
                Text(
                    text = s.logIn,
                    color = CookncoNavy,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onNavigateToLogin),
                )
            }
        }
    }
}

@Preview(showBackground = true)
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

@Preview(showBackground = true, name = "Signup - Error")
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

@Preview(showBackground = true, name = "Signup - Loading")
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
