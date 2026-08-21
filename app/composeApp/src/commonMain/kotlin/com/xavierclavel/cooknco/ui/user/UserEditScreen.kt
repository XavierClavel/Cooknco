package com.xavierclavel.cooknco.ui.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.platform.PickedImage
import com.xavierclavel.cooknco.platform.rememberImagePicker
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite

private val fieldShape = RoundedCornerShape(12.dp)

@Composable
private fun editColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CookncoWhite,
    unfocusedContainerColor = CookncoWhite,
    focusedBorderColor = CookncoOrange,
    unfocusedBorderColor = CookncoNavy.copy(alpha = 0.5f),
    focusedTextColor = CookncoNavy,
    unfocusedTextColor = CookncoNavy,
    focusedLabelColor = CookncoOrange,
    unfocusedLabelColor = CookncoNavy.copy(alpha = 0.6f),
    cursorColor = CookncoOrange,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditScreen(
    viewModel: UserEditViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onNavigateBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::save,
                        enabled = !uiState.isSaving,
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CookncoNavy,
                            )
                        } else {
                            Icon(Icons.Outlined.Check, contentDescription = "Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CookncoGreen,
                    titleContentColor = CookncoNavy,
                    navigationIconContentColor = CookncoNavy,
                    actionIconContentColor = CookncoNavy,
                ),
            )
        },
        containerColor = CookncoBackground,
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp) }

            else -> UserEditContent(
                uiState = uiState,
                onUsernameChange = viewModel::updateUsername,
                onBioChange = viewModel::updateBio,
                onImageSelected = { uri -> viewModel.setPendingImage(uri) },
                onSave = viewModel::save,
                onCancel = onNavigateBack,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun UserEditContent(
    uiState: UserEditUiState,
    onUsernameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onImageSelected: (PickedImage) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val user = uiState.user

    val imagePicker = rememberImagePicker(onPicked = onImageSelected)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Avatar picker ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(120.dp)
                .clickable { imagePicker.launch() },
            contentAlignment = Alignment.BottomEnd,
        ) {
            // Show the freshly picked image if there is one, otherwise the remote avatar
            val pickedBitmap = uiState.pendingImage?.let { picked ->
                remember(picked) { runCatching { picked.bytes.decodeToImageBitmap() }.getOrNull() }
            }

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(2.5.dp, CookncoNavy, CircleShape)
                    .background(CookncoGreenLight)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(72.dp),
                )
                if (pickedBitmap != null) {
                    Image(
                        bitmap = pickedBitmap,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = user?.let { "${ApiClient.IMAGE_URL}/users/${it.id}-v${it.version}.webp" },
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Camera badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CookncoOrange)
                    .border(2.dp, CookncoNavy, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = "Change photo",
                    tint = CookncoWhite,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Text(
            text = "Tap to change photo",
            style = MaterialTheme.typography.bodySmall,
            color = CookncoNavy.copy(alpha = 0.5f),
        )

        // ── Username ─────────────────────────────────────────────────────────
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = uiState.error?.contains("Username") == true || uiState.error?.contains("username") == true,
            colors = editColors(),
            shape = fieldShape,
        )

        // ── Bio ──────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = uiState.bio,
            onValueChange = onBioChange,
            label = { Text("Bio") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            colors = editColors(),
            shape = fieldShape,
            supportingText = { Text("${uiState.bio.length}/255", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
        )

        // ── Error ────────────────────────────────────────────────────────────
        if (uiState.error != null) {
            Text(
                text = uiState.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Save button ──────────────────────────────────────────────────────
        Button(
            onClick = onSave,
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CookncoOrange,
                contentColor = CookncoWhite,
            ),
            border = BorderStroke(1.5.dp, CookncoNavy),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CookncoWhite)
            } else {
                Text("SAVE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        // ── Cancel button ────────────────────────────────────────────────────
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CookncoNavy.copy(alpha = 0.06f),
                contentColor = CookncoNavy,
            ),
            border = BorderStroke(1.5.dp, CookncoNavy.copy(alpha = 0.4f)),
        ) {
            Text("Cancel", fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────

private val previewEditState = UserEditUiState(
    user = UserInfo(
        id = 1L, version = 2L, username = "Xavier Clavel",
        role = "USER", joinDate = 0L,
        bio = "Passionate about food and cooking since forever 🍳",
        recipesCount = 12, likesCount = 45, cookbooksCount = 3,
        followersCount = 28, followsCount = 7,
    ),
    username = "Xavier Clavel",
    bio = "Passionate about food and cooking since forever 🍳",
    isLoading = false,
)

@Preview(showBackground = true)
@Composable
fun UserEditScreenPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            UserEditContent(
                uiState = previewEditState,
                onUsernameChange = {},
                onBioChange = {},
                onImageSelected = {},
                onSave = {},
                onCancel = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Edit - Error")
@Composable
fun UserEditErrorPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            UserEditContent(
                uiState = previewEditState.copy(error = "Username already taken"),
                onUsernameChange = {},
                onBioChange = {},
                onImageSelected = {},
                onSave = {},
                onCancel = {},
            )
        }
    }
}
