package com.xavierclavel.cooknco.ui.cookbook

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.network.dto.UserSummary
import com.xavierclavel.cooknco.ui.components.UserAvatar

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

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            val id = uiState.cookbookId
            if (id != null) onSaved(id)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (cookbookId == null) "New Cookbook" else "Edit Cookbook",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp)
            }
            return@Scaffold
        }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Title
        item {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.error?.contains("Title") == true,
            )
        }

        // Description
        item {
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        }

        // Visibility
        item {
            Text(
                text = "Visibility",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PRIVATE" to "Private", "PROTECTED" to "Protected", "PUBLIC" to "Public").forEach { (value, label) ->
                    FilterChip(
                        selected = uiState.visibility == value,
                        onClick = { viewModel.updateVisibility(value) },
                        label = { Text(label) },
                    )
                }
            }
        }

        // Members header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Members",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { viewModel.addMember() }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add member")
                }
            }
        }

        // Member rows
        val members = uiState.members
        members.forEachIndexed { index, member ->
            item(key = "member_$index") {
                MemberEditRow(
                    index = index,
                    member = member,
                    onQueryChange = { viewModel.updateMemberQuery(index, it) },
                    onSelect = { viewModel.selectMember(index, it) },
                    onDismiss = { viewModel.dismissMemberDropdown(index) },
                    onRoleChange = { viewModel.updateMemberRole(index, it) },
                    onRemove = { viewModel.removeMember(index) },
                )
            }
        }

        // Error
        if (uiState.error != null) {
            item {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSaving,
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(18.dp)
                                .width(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberEditRow(
    index: Int,
    member: EditMember,
    onQueryChange: (String) -> Unit,
    onSelect: (UserSummary) -> Unit,
    onDismiss: () -> Unit,
    onRoleChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserAvatar(
                userId = member.userId,
                version = 1L,
                contentDescription = member.username,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )

            // Username search field with autocomplete
            ExposedDropdownMenuBox(
                expanded = member.showDropdown,
                onExpandedChange = { if (!it) onDismiss() },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = member.searchQuery,
                    onValueChange = onQueryChange,
                    label = { Text("Username") },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                        .fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = member.showDropdown)
                    },
                )
                ExposedDropdownMenu(
                    expanded = member.showDropdown,
                    onDismissRequest = onDismiss,
                ) {
                    member.searchResults.forEach { result ->
                        DropdownMenuItem(
                            text = { Text(result.username) },
                            onClick = { onSelect(result) },
                        )
                    }
                }
            }

            // Remove button
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove member",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Admin toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Admin",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = member.isAdmin,
                onCheckedChange = onRoleChange,
            )
        }
    }
}
