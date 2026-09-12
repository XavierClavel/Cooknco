package com.xavierclavel.cooknco.ui.cookbook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.dto.UserSummary
import com.xavierclavel.cooknco.platform.rememberImagePicker
import com.xavierclavel.cooknco.ui.components.CookbookImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.stickerShadow

// ── Shared styling helpers (mirrors RecipeEditScreen's private equivalents) ──────

private val fieldShape = RoundedCornerShape(12.dp)

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CookncoWhite,
    unfocusedContainerColor = CookncoWhite,
    focusedBorderColor = CookncoOrange,
    unfocusedBorderColor = CookncoNavy,
    focusedTextColor = CookncoNavy,
    unfocusedTextColor = CookncoNavy,
    focusedLabelColor = CookncoOrange,
    unfocusedLabelColor = CookncoNavy.copy(alpha = 0.6f),
    cursorColor = CookncoOrange,
)

private val visibilityOptions = listOf("PRIVATE" to "Private", "PROTECTED" to "Protected", "PUBLIC" to "Public")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookbookEditScreen(
    cookbookId: Long?,
    currentUserId: Long,
    currentUsername: String,
    onNavigateBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: CookbookEditViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val imagePicker = rememberImagePicker(onPicked = viewModel::setPendingImage)

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            val id = uiState.cookbookId
            if (id != null) onSaved(id)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        // ── Top bar ────────────────────────────────────────────────────────────
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
            Text(
                text = if (cookbookId == null) "New cookbook" else "Edit cookbook",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                modifier = Modifier.weight(1f),
            )
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }
            return@Column
        }

        val pickedImage = uiState.pendingImage
        val pickedBitmap = pickedImage?.let { picked ->
            remember(picked) { runCatching { picked.bytes.decodeToImageBitmap() }.getOrNull() }
        }
        // A version of 0 means the cookbook is still on the backend's default placeholder —
        // CookbookInfo.version doubles as the image version (see ImageController on the backend).
        val hasExistingPhoto = uiState.cookbookId != null && (uiState.cookbookVersion ?: 0) > 0

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ── Photo ─────────────────────────────────────────────────────────
            item {
                val hasAnyPhoto = pickedBitmap != null || hasExistingPhoto
                val photoShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .stickerShadow(photoShape)
                        .clip(photoShape)
                        .background(CookncoGreenLight)
                        .border(3.dp, CookncoNavy, photoShape)
                        .clickable { imagePicker.launch() },
                ) {
                    when {
                        pickedBitmap != null -> Image(
                            bitmap = pickedBitmap,
                            contentDescription = "Cookbook photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        hasExistingPhoto -> CookbookImage(
                            cookbookId = uiState.cookbookId!!,
                            version = uiState.cookbookVersion!!,
                            contentDescription = "Cookbook photo",
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            tint = CookncoNavy.copy(alpha = 0.4f),
                            modifier = Modifier.padding(start = 12.dp).size(24.dp).align(Alignment.CenterStart),
                        )
                    }
                    StickerPill(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                        onClick = { imagePicker.launch() },
                        height = 44.dp,
                        shadowOffset = 0.dp,
                    ) {
                        Text(
                            text = if (hasAnyPhoto) "Change cover" else "Add cover",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                        )
                    }
                }
            }
            if (pickedImage != null) {
                item {
                    Text(
                        text = "Uploaded when you save.",
                        color = CookncoNavy.copy(alpha = 0.6f),
                        fontSize = 11.5.sp,
                    )
                }
            }

            // ── Basics ────────────────────────────────────────────────────────
            item {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column {
                            FieldLabel("TITLE *", modifier = Modifier.padding(bottom = 7.dp))
                            OutlinedTextField(
                                value = uiState.title,
                                onValueChange = viewModel::updateTitle,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = uiState.error?.contains("Title") == true,
                                colors = editFieldColors(),
                                shape = fieldShape,
                            )
                        }
                        Column {
                            FieldLabel("DESCRIPTION", modifier = Modifier.padding(bottom = 7.dp))
                            OutlinedTextField(
                                value = uiState.description,
                                onValueChange = viewModel::updateDescription,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                colors = editFieldColors(),
                                shape = fieldShape,
                            )
                        }
                    }
                }
            }

            // ── Visibility ────────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("VISIBILITY", color = CookncoNavy)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .stickerShadow(RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(CookncoBackground)
                            .border(3.dp, CookncoNavy, RoundedCornerShape(20.dp))
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        visibilityOptions.forEach { (value, label) ->
                            VisibilitySegment(
                                label = label,
                                selected = uiState.visibility == value,
                                onClick = { viewModel.updateVisibility(value) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── Members ───────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldLabel("MEMBERS", modifier = Modifier.weight(1f), color = CookncoNavy)
                    StickerPill(
                        onClick = viewModel::addMember,
                        height = 44.dp,
                        fillColor = CookncoGold,
                        shadowOffset = 3.dp,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Add member",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            uiState.members.forEachIndexed { index, member ->
                item(key = "member_$index") {
                    MemberEditRow(
                        member = member,
                        onQueryChange = { viewModel.updateMemberQuery(index, it) },
                        onSelect = { viewModel.selectMember(index, it) },
                        onDismiss = { viewModel.dismissMemberDropdown(index) },
                        onRoleChange = { viewModel.updateMemberRole(index, it) },
                        onRemove = { viewModel.removeMember(index) },
                    )
                }
            }

            // ── Error ─────────────────────────────────────────────────────────
            if (uiState.error != null) {
                item {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
            }

            // ── Bottom actions ───────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StickerCard(
                        modifier = Modifier.size(width = 100.dp, height = 56.dp),
                        shape = RoundedCornerShape(16.dp),
                        shadowOffset = 4.dp,
                        onClick = onNavigateBack,
                    ) {
                        Text(
                            text = "Cancel",
                            color = CookncoNavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    StickerCard(
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        fillColor = CookncoOrange,
                        shadowOffset = 4.dp,
                        onClick = { viewModel.save() },
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).align(Alignment.Center),
                                strokeWidth = 2.dp,
                                color = CookncoWhite,
                            )
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
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier, color: Color = CookncoGreenDark) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 0.7.sp,
        modifier = modifier,
    )
}

@Composable
private fun VisibilitySegment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) CookncoOrange else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) CookncoWhite else CookncoNavy,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.5.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberEditRow(
    member: EditMember,
    onQueryChange: (String) -> Unit,
    onSelect: (UserSummary) -> Unit,
    onDismiss: () -> Unit,
    onRoleChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), shadowOffset = 5.dp) {
        Column(
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp, start = 12.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UserAvatar(
                    userId = member.userId,
                    version = 1L,
                    contentDescription = member.username,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )

                ExposedDropdownMenuBox(
                    expanded = member.showDropdown,
                    onExpandedChange = { if (!it) onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = member.searchQuery,
                        onValueChange = onQueryChange,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                    DropdownMenu(expanded = member.showDropdown, onDismissRequest = onDismiss) {
                        member.searchResults.forEach { result ->
                            DropdownMenuItem(text = { Text(result.username) }, onClick = { onSelect(result) })
                        }
                    }
                }

                Box(
                    modifier = Modifier.size(44.dp).clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove member",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Admin",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = CookncoNavy,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = member.isAdmin,
                    onCheckedChange = onRoleChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CookncoWhite,
                        checkedTrackColor = CookncoOrange,
                        checkedBorderColor = CookncoNavy,
                        uncheckedThumbColor = CookncoNavy,
                        uncheckedTrackColor = CookncoBackground,
                        uncheckedBorderColor = CookncoNavy,
                    ),
                )
            }
        }
    }
}
