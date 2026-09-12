package com.xavierclavel.cooknco.ui.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.stickerShadow

// ── Shared styling helpers (mirrors CookbookEditScreen's private equivalents) ───

private val fieldShape = RoundedCornerShape(12.dp)

@Composable
private fun editColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CookncoWhite,
    unfocusedContainerColor = CookncoWhite,
    focusedBorderColor = CookncoOrange,
    unfocusedBorderColor = CookncoNavy,
    focusedTextColor = CookncoNavy,
    unfocusedTextColor = CookncoNavy,
    cursorColor = CookncoOrange,
)

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark, letterSpacing = 0.7.sp, modifier = modifier)
}

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

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        // ── Top bar: back + title only — saving happens from the bottom SAVE button ──
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerIconButton(onClick = onNavigateBack, shadowOffset = 3.dp) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Edit profile", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, modifier = Modifier.weight(1f))
        }

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }

            else -> UserEditContent(
                uiState = uiState,
                onUsernameChange = viewModel::updateUsername,
                onBioChange = { viewModel.updateBio(it.take(255)) },
                onImageSelected = { uri -> viewModel.setPendingImage(uri) },
                onSave = viewModel::save,
                onCancel = onNavigateBack,
                modifier = Modifier.weight(1f),
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
    val avatarShape = CircleShape

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Avatar picker ────────────────────────────────────────────────────
        Box(
            modifier = Modifier.size(130.dp).clickable { imagePicker.launch() },
            contentAlignment = Alignment.Center,
        ) {
            val pickedBitmap = uiState.pendingImage?.let { picked ->
                remember(picked) { runCatching { picked.bytes.decodeToImageBitmap() }.getOrNull() }
            }

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .stickerShadow(avatarShape, offsetX = 5.dp, offsetY = 5.dp)
                    .clip(avatarShape)
                    .border(3.dp, CookncoNavy, avatarShape)
                    .background(CookncoGreenLight),
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
                    .align(Alignment.BottomEnd)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CookncoGold)
                    .border(3.dp, CookncoNavy, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = "Change photo",
                    tint = CookncoNavy,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Text(
            text = "Tap to change photo",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = CookncoNavy,
            modifier = Modifier.padding(top = 14.dp),
        )

        // ── Fields card ──────────────────────────────────────────────────────
        StickerCard(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    FieldLabel("USERNAME", modifier = Modifier.padding(bottom = 7.dp))
                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = uiState.error?.contains("username", ignoreCase = true) == true,
                        colors = editColors(),
                        shape = fieldShape,
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                }
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp), verticalAlignment = Alignment.Bottom) {
                        FieldLabel("BIO", modifier = Modifier.weight(1f))
                        Text("${uiState.bio.length}/255", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CookncoGreenDark)
                    }
                    OutlinedTextField(
                        value = uiState.bio,
                        onValueChange = onBioChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        colors = editColors(),
                        shape = fieldShape,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // ── Error ────────────────────────────────────────────────────────────
        if (uiState.error != null) {
            Text(
                text = uiState.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        // ── Bottom actions ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerCard(
                modifier = Modifier.size(width = 100.dp, height = 56.dp),
                shape = RoundedCornerShape(16.dp),
                shadowOffset = 4.dp,
                onClick = onCancel,
            ) {
                Text("Cancel", color = CookncoNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
            }
            StickerCard(
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                fillColor = CookncoOrange,
                shadowOffset = 4.dp,
                onClick = if (!uiState.isSaving) onSave else null,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp).align(Alignment.Center), strokeWidth = 2.dp, color = CookncoWhite)
                } else {
                    Text(
                        text = "SAVE",
                        color = CookncoWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
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
        Box(modifier = Modifier.background(CookncoGreen)) {
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
        Box(modifier = Modifier.background(CookncoGreen)) {
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
