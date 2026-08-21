package com.xavierclavel.cooknco.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = CookncoNavy.copy(alpha = 0.45f),
                fontSize = 15.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = CookncoNavy.copy(alpha = 0.6f),
            )
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { onTogglePasswordVisibility?.invoke() }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = CookncoNavy.copy(alpha = 0.6f),
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CookncoBackground,
            unfocusedContainerColor = CookncoBackground,
            focusedBorderColor = CookncoOrange,
            unfocusedBorderColor = CookncoNavy.copy(alpha = 0.6f),
            focusedTextColor = CookncoNavy,
            unfocusedTextColor = CookncoNavy,
            cursorColor = CookncoOrange,
            errorContainerColor = CookncoBackground,
            errorBorderColor = Color(0xFFB3261E),
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        isError = isError,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = CookncoNavy.copy(alpha = 0.4f),
        )
        Text(
            text = "  or  ",
            color = CookncoNavy.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = CookncoNavy.copy(alpha = 0.4f),
        )
    }
}

@Composable
fun AuthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = CookncoOrange,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = CookncoWhite,
            disabledContainerColor = containerColor.copy(alpha = 0.6f),
            disabledContentColor = CookncoWhite.copy(alpha = 0.7f),
        ),
        border = BorderStroke(1.5.dp, CookncoNavy),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = CookncoWhite,
                strokeWidth = 2.dp,
            )
        } else {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            val textModifier = if (leadingIcon != null) Modifier.padding(start = 8.dp) else Modifier
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.5.sp,
                modifier = textModifier,
            )
        }
    }
}
