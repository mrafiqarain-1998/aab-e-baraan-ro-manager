package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppModule
import com.example.model.AuditActionType
import com.example.model.AuditLogEntry
import com.example.model.PermissionAction
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel

@Composable
fun AuditHistoryScreen(viewModel: WaterPlantViewModel) {
    val auditLogs by viewModel.auditLogs.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val canViewAudit = viewModel.hasPermission(AppModule.AUDIT_HISTORY, PermissionAction.VIEW)

    var searchQuery by remember { mutableStateOf("") }
    var selectedModule by remember { mutableStateOf<AppModule?>(null) }
    var selectedAction by remember { mutableStateOf<AuditActionType?>(null) }

    if (!canViewAudit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WaterSkySurface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Access Restricted",
                        tint = ErrorRed,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Audit History Restricted",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyDark
                    )
                    Text(
                        text = "Only Admin or staff granted AUDIT_LOGS View permission can inspect security & activity logs.",
                        fontSize = 11.5.sp,
                        color = TextSecondaryDark
                    )
                }
            }
        }
        return
    }

    val filteredLogs = remember(auditLogs, searchQuery, selectedModule, selectedAction) {
        val q = searchQuery.trim().lowercase()
        auditLogs.filter { entry ->
            val matchesModule = selectedModule == null || entry.module == selectedModule!!.name
            val matchesAction = selectedAction == null || entry.action == selectedAction!!.name
            val matchesQuery = q.isEmpty() ||
                entry.actorName.lowercase().contains(q) ||
                entry.actorUsername.lowercase().contains(q) ||
                entry.recordLabel.lowercase().contains(q) ||
                entry.previousValue.lowercase().contains(q) ||
                entry.updatedValue.lowercase().contains(q) ||
                entry.module.lowercase().contains(q) ||
                entry.action.lowercase().contains(q)
            matchesModule && matchesAction && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface)
            .testTag("audit_history_list"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Immutable Audit Trail (${filteredLogs.size})",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "لاگ ان، عملے کی تبدیلیاں، اسٹاک اور بلنگ ریکارڈ (Append-Only Security Log)",
                            color = WaterSkyLight,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Viewer: ${currentUser.name} (${currentUser.role.displayName})",
                            color = WaterCyanBright,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Audit Trail",
                        tint = WaterCyanBright,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by staff, customer, invoice, or action...", fontSize = 11.5.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Logs", tint = WaterNavyPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = WaterNavyPrimary,
                    unfocusedBorderColor = BorderLight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audit_search_input")
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Filter by Module (ماڈیول):",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaterNavyDark
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedModule == null,
                        onClick = { selectedModule = null },
                        label = { Text("All", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = waterFilterChipColors()
                    )
                    AppModule.entries.forEach { mod ->
                        FilterChip(
                            selected = selectedModule == mod,
                            onClick = { selectedModule = if (selectedModule == mod) null else mod },
                            label = { Text(mod.title, fontSize = 10.sp, maxLines = 1) },
                            colors = waterFilterChipColors()
                        )
                    }
                }

                Text(
                    text = "Filter by Action Type:",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaterNavyDark
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedAction == null,
                        onClick = { selectedAction = null },
                        label = { Text("All Actions", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = waterFilterChipColors()
                    )
                    AuditActionType.entries.forEach { act ->
                        FilterChip(
                            selected = selectedAction == act,
                            onClick = { selectedAction = if (selectedAction == act) null else act },
                            label = { Text(act.name, fontSize = 9.5.sp, maxLines = 1) },
                            colors = waterFilterChipColors()
                        )
                    }
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No matching audit records yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = WaterNavyDark
                        )
                        Text(
                            text = "New logins, staff updates, deliveries, payments, stock edits, and cloud sync events appear here automatically.",
                            fontSize = 10.5.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { entry ->
                AuditLogCard(entry = entry)
            }
        }
    }
}

@Composable
private fun AuditLogCard(entry: AuditLogEntry) {
    val actionColor = when (entry.action) {
        AuditActionType.DELETE.name -> ErrorRed
        AuditActionType.UPDATE.name, AuditActionType.PASSWORD_RESET.name -> WarningOrange
        AuditActionType.CREATE.name, AuditActionType.LOGIN.name -> SuccessGreen
        AuditActionType.SYNC.name -> WaterBlueMedium
        else -> WaterNavyPrimary
    }
    val actionBg = when (entry.action) {
        AuditActionType.DELETE.name -> ErrorRedLight
        AuditActionType.UPDATE.name, AuditActionType.PASSWORD_RESET.name -> WarningOrangeLight
        AuditActionType.CREATE.name, AuditActionType.LOGIN.name -> SuccessGreenLight
        else -> WaterSkyLight
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(actionColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entry.recordLabel.ifBlank { entry.module },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                StatusBadge(
                    text = entry.action,
                    backgroundColor = actionBg,
                    textColor = actionColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "By: ${entry.actorName.ifBlank { entry.actorUsername }} (${entry.actorRole})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryDark
                )
                Text(
                    text = entry.dateTimeDisplay,
                    fontSize = 9.5.sp,
                    color = TextSecondaryDark
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = WaterSkyLight) {
                    Text(
                        text = entry.module,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (entry.deviceInfo.isNotBlank()) {
                    Text(
                        text = "• ${entry.deviceInfo}",
                        fontSize = 9.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (entry.previousValue.isNotBlank() || entry.updatedValue.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (entry.previousValue.isNotBlank()) {
                            Text(
                                text = "Before: ${entry.previousValue}",
                                fontSize = 10.sp,
                                color = ErrorRed
                            )
                        }
                        if (entry.updatedValue.isNotBlank()) {
                            Text(
                                text = "After: ${entry.updatedValue}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }
    }
}
