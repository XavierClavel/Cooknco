package com.xavierclavel.cooknco.ui.cookbook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
            StickerPill(onClick = { viewModel.save() }, height = 44.dp, shadowOffset = 3.dp) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CookncoNavy)
                } else {
                    Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                }
            }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CookncoGreenLight)
                        .border(3.dp, CookncoNavy, RoundedCornerShape(18.dp))
                        .clickable { imagePicker.launch() },
                    contentAlignment = Alignment.Center,
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
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = CookncoNavy.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                            Text("Tap to add a photo", color = CookncoNavy.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        }
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
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.error?.contains("Title") == true,
                    colors = editFieldColors(),
                    shape = fieldShape,
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = editFieldColors(),
                    shape = fieldShape,
                )
            }

            // ── Visibility ────────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Visibility", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CookncoNavy.copy(alpha = 0.75f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        visibilityOptions.forEach { (value, label) ->
                            VisibilityChip(label = label, selected = uiState.visibility == value, onClick = { viewModel.updateVisibility(value) })
                        }
                    }
                }
            }

            // ── Members ───────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "MEMBERS · ${uiState.members.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CookncoGreen)
                            .border(1.5.dp, CookncoNavy, CircleShape)
                            .clickable(onClick = viewModel::addMember),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add member", tint = CookncoNavy, modifier = Modifier.size(20.dp))
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
                                text = "Save cookbook",
                                color = CookncoWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
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
private fun VisibilityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) CookncoOrange else CookncoWhite)
            .border(1.5.dp, if (selected) CookncoNavy else CookncoNavy.copy(alpha = 0.35f), RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) CookncoWhite else CookncoNavy, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
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
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        label = { Text("Username") },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = member.showDropdown) },
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                    DropdownMenu(expanded = member.showDropdown, onDismissRequest = onDismiss) {
                        member.searchResults.forEach { result ->
                            DropdownMenuItem(text = { Text(result.username) }, onClick = { onSelect(result) })
                        }
                    }
                }

                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove member",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp).clickable(onClick = onRemove),
                )
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
