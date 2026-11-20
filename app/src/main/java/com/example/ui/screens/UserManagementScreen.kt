package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.model.AppModule
import com.example.model.PasswordResetRequest
import com.example.model.PermissionAction
import com.example.model.PermissionDefaults
import com.example.model.PlantBranch
import com.example.model.UserAccount
import com.example.model.UserRole
import com.example.ui.components.MobileFormDialog
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    viewModel: WaterPlantViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val userAccounts by viewModel.userAccounts.collectAsState()
    val passwordResetRequests by viewModel.passwordResetRequests.collectAsState()
    val plantBranches by viewModel.plantBranches.collectAsState()
    val activePlantId by viewModel.activePlantId.collectAsState()

    val canManageStaff = viewModel.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE) ||
        viewModel.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.ADD)

    var showCreateDialog by remember { mutableStateOf(false) }
    var showChangeOwnPwdDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<UserAccount?>(null) }
    var accountToChangePassword by remember { mutableStateOf<UserAccount?>(null) }
    var accountToDelete by remember { mutableStateOf<UserAccount?>(null) }
    var requestToResolve by remember { mutableStateOf<PasswordResetRequest?>(null) }

    val activeCount = userAccounts.count { it.active }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Admin Header Banner + Change My Password button
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Staff ID & Password Control",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "عملے کے اکاؤنٹس، پاس ورڈ تبدیلی، ایکٹو/ان ایکٹو اور حذف کنٹرول",
                                color = WaterSkyLight,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = WaterCyanBright,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WaterNavyDark.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Signed in: ${currentUser.name}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@${currentUser.username} • ${currentUser.role.displayName}",
                                    color = WaterCyanBright,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            FilledTonalButton(
                                onClick = { showChangeOwnPwdDialog = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = WaterAqua,
                                    contentColor = WaterNavyPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .testTag("change_own_password_btn")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "My Password",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pending Password Reset Requests
        val pendingRequests = passwordResetRequests.filter { !it.isResolved }
        if (pendingRequests.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningOrangeLight),
                    border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LockReset,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pending Password Resets (${pendingRequests.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = WarningOrange
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = WarningOrange.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Action Needed",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        pendingRequests.forEach { req ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${req.fullName} (@${req.username})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = WaterNavyPrimary
                                        )
                                        Text(
                                            text = "${req.role.displayName} • ${req.requestTime}",
                                            fontSize = 9.5.sp,
                                            color = TextSecondaryDark
                                        )
                                    }

                                    Button(
                                        onClick = { requestToResolve = req },
                                        colors = waterPrimaryButtonColors(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Reset", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Single Header Add Staff Row (No Duplicate FAB)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Staff Accounts (${userAccounts.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        color = WaterNavyDark
                    )
                    Text(
                        text = "$activeCount Active • ${userAccounts.size - activeCount} Inactive",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryDark
                    )
                }

                if (canManageStaff) {
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = waterPrimaryButtonColors(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("new_staff_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New Staff",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Staff Cards
        items(userAccounts, key = { it.id }) { acc ->
            val isProtectedAdmin = acc.isSuperAdmin ||
                acc.id == "user_admin_01" ||
                acc.username.equals("admin", ignoreCase = true)
            val isPrimaryOrSelf = isProtectedAdmin || acc.id == currentUser.id

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (acc.active) Color.White else Color(0xFFF8FAFC)
                ),
                border = if (!acc.active) BorderStroke(1.dp, ErrorRed.copy(alpha = 0.25f)) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !acc.active -> ErrorRedLight
                                            acc.role == UserRole.ADMIN -> WaterSkyLight
                                            acc.role == UserRole.INCHARGE -> WaterCyanBright.copy(alpha = 0.15f)
                                            else -> Color(0xFFE3F2FD)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (acc.role) {
                                        UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                                        UserRole.INCHARGE -> Icons.Default.Engineering
                                        UserRole.VIEW_ONLY -> Icons.Default.Visibility
                                    },
                                    contentDescription = null,
                                    tint = if (!acc.active) ErrorRed else when (acc.role) {
                                        UserRole.ADMIN -> WaterNavyPrimary
                                        UserRole.INCHARGE -> WaterBlueMedium
                                        UserRole.VIEW_ONLY -> Color(0xFF1976D2)
                                    },
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = acc.name,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = if (acc.active) WaterNavyDark else TextSecondaryDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    StatusBadge(
                                        text = if (acc.active) "Active" else "Inactive",
                                        backgroundColor = if (acc.active) SuccessGreenLight else ErrorRedLight,
                                        textColor = if (acc.active) SuccessGreen else ErrorRed
                                    )
                                    if (isProtectedAdmin) {
                                        StatusBadge(
                                            text = "Super Admin",
                                            backgroundColor = WaterSkyLight,
                                            textColor = WaterNavyPrimary
                                        )
                                    }
                                }
                                Text(
                                    text = "ID: @${acc.username} • ${acc.role.displayName}",
                                    fontSize = 10.5.sp,
                                    color = TextPrimaryDark,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Phone: ${if (acc.phone.isNotEmpty()) acc.phone else "Not set"} • Modules: ${PermissionDefaults.normalizePermissions(acc.permissions, acc.role, acc.isSuperAdmin).size}",
                                    fontSize = 9.5.sp,
                                    color = TextSecondaryDark
                                )
                                Text(
                                    text = if (acc.googleEmail.isNotBlank()) "Sync Gmail: ${acc.googleEmail}" else "Direct Staff ID Login (No Gmail needed)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WaterBlueMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val effPerms = PermissionDefaults.normalizePermissions(acc.permissions, acc.role, acc.isSuperAdmin)
                                val allowRates = acc.isSuperAdmin || PermissionAction.EDIT.name in effPerms[AppModule.CUSTOMER_RATES.name].orEmpty()
                                val allowAddStock = acc.isSuperAdmin || PermissionAction.ADD.name in effPerms[AppModule.BOTTLE_STOCK.name].orEmpty()
                                val allowNewItems = acc.isSuperAdmin || PermissionAction.ADD.name in effPerms[AppModule.NEW_ITEMS.name].orEmpty()
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    StatusBadge(
                                        text = if (allowRates) "Rates ✓" else "Rates ✕",
                                        backgroundColor = if (allowRates) SuccessGreenLight else ErrorRedLight,
                                        textColor = if (allowRates) SuccessGreen else ErrorRed
                                    )
                                    StatusBadge(
                                        text = if (allowAddStock) "Stock ✓" else "Stock ✕",
                                        backgroundColor = if (allowAddStock) SuccessGreenLight else ErrorRedLight,
                                        textColor = if (allowAddStock) SuccessGreen else ErrorRed
                                    )
                                    StatusBadge(
                                        text = if (allowNewItems) "New Items ✓" else "New Items ✕",
                                        backgroundColor = if (allowNewItems) SuccessGreenLight else ErrorRedLight,
                                        textColor = if (allowNewItems) SuccessGreen else ErrorRed
                                    )
                                }
                                if (acc.restrictToOwnDataOnly || acc.restrictToTodayOnly) {
                                    Row(
                                        modifier = Modifier.padding(top = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (acc.restrictToOwnDataOnly) {
                                            StatusBadge(
                                                text = "Own Data Only",
                                                backgroundColor = WarningOrangeLight,
                                                textColor = WarningOrange
                                            )
                                        }
                                        if (acc.restrictToTodayOnly) {
                                            StatusBadge(
                                                text = "Today Only",
                                                backgroundColor = WarningOrangeLight,
                                                textColor = WarningOrange
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderLight)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Row: Edit, Change Password, Active/Inactive Toggle, Delete User
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { accountToEdit = acc },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WaterSkyLight)
                                .testTag("edit_user_btn_${acc.username}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Staff",
                                tint = WaterNavyPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = { accountToChangePassword = acc },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WaterNavyPrimary),
                            border = BorderStroke(1.dp, WaterNavyPrimary.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(36.dp)
                                .testTag("reset_password_btn_${acc.username}")
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Password",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        FilledTonalButton(
                            enabled = !isPrimaryOrSelf,
                            onClick = {
                                viewModel.toggleUserAccountActive(acc.id) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (acc.active) WarningOrangeLight else SuccessGreenLight,
                                contentColor = if (acc.active) WarningOrange else SuccessGreen
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("toggle_active_btn_${acc.username}")
                        ) {
                            Icon(
                                imageVector = if (acc.active) Icons.Default.PauseCircle else Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (acc.active) "Deactivate" else "Activate",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        IconButton(
                            enabled = !isPrimaryOrSelf,
                            onClick = { accountToDelete = acc },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isPrimaryOrSelf) ErrorRedLight else Color(0xFFF1F5F9))
                                .testTag("delete_user_btn_${acc.username}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete User",
                                tint = if (!isPrimaryOrSelf) ErrorRed else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. Create Staff Account Dialog
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var googleEmail by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf(UserRole.INCHARGE) }
        var customPerms by remember { mutableStateOf(PermissionDefaults.defaultPermissionsForRole(UserRole.INCHARGE)) }
        var selectedBranches by remember { mutableStateOf(setOf(activePlantId.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID })) }
        var restrictToOwnDataOnly by remember { mutableStateOf(false) }
        var restrictToTodayOnly by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val hasUnsaved = name.isNotBlank() || username.isNotBlank() || password.isNotBlank() || phone.isNotBlank() || googleEmail.isNotBlank()

        MobileFormDialog(
            title = "Create Staff Account",
            subtitle = "عملے کی نئی آئی ڈی، پاس ورڈ اور اختیارات سیٹ کریں",
            hasUnsavedChanges = hasUnsaved,
            onDismiss = { showCreateDialog = false },
            footerContent = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
                Button(
                    onClick = {
                        val cleanName = name.trim()
                        val cleanUser = username.trim().removePrefix("@").trim().lowercase()
                        val cleanPass = password.trim()
                        val cleanEmail = googleEmail.trim().lowercase()
                        if (cleanName.isEmpty() || cleanUser.isEmpty() || cleanPass.isEmpty()) {
                            errorMsg = "Name, Username, and Password are required."
                            return@Button
                        }
                        if (userAccounts.any { it.username.trim().removePrefix("@").trim().equals(cleanUser, ignoreCase = true) }) {
                            errorMsg = "Username '@$cleanUser' already exists."
                            return@Button
                        }
                        val branchList = selectedBranches.toList().ifEmpty { listOf(activePlantId.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }) }
                        viewModel.createUserAccount(
                            UserAccount(
                                name = cleanName,
                                username = cleanUser,
                                password = cleanPass,
                                phone = phone.trim(),
                                googleEmail = cleanEmail,
                                role = selectedRole,
                                permissions = customPerms,
                                hasExplicitPermissions = true,
                                assignedBranchIds = branchList,
                                defaultBranchId = branchList.first(),
                                active = true,
                                restrictToOwnDataOnly = restrictToOwnDataOnly,
                                restrictToTodayOnly = restrictToTodayOnly
                            )
                        )
                        showCreateDialog = false
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("create_staff_submit_btn")
                ) {
                    Text("Create Account", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMsg = null },
                label = { Text("Staff Full Name*", fontSize = 10.5.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMsg = null },
                label = { Text("Username / Staff Login ID*", fontSize = 10.5.sp) },
                placeholder = { Text("e.g. rashid1 (no Gmail required)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = null },
                label = { Text("Set Password*", fontSize = 10.5.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Mobile Phone Number", fontSize = 10.5.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = googleEmail,
                onValueChange = { googleEmail = it },
                label = { Text("Staff Gmail (Optional Fallback)", fontSize = 10.5.sp) },
                placeholder = { Text("Optional — Staff ID works without Gmail", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "عملہ بغیر جی میل کے صرف یوزر نیم اور پاس ورڈ سے لاگ ان کر سکتا ہے۔",
                fontSize = 9.5.sp,
                color = WaterBlueMedium
            )
            Text(
                text = "Role Preset (عہدہ):",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedRole == UserRole.INCHARGE,
                    onClick = {
                        selectedRole = UserRole.INCHARGE
                        customPerms = PermissionDefaults.defaultPermissionsForRole(UserRole.INCHARGE)
                    },
                    colors = waterFilterChipColors(),
                    label = { Text("Supervisor", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedRole == UserRole.ADMIN,
                    onClick = {
                        selectedRole = UserRole.ADMIN
                        customPerms = PermissionDefaults.defaultPermissionsForRole(UserRole.ADMIN)
                    },
                    colors = waterFilterChipColors(),
                    label = { Text("Admin", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedRole == UserRole.VIEW_ONLY,
                    onClick = {
                        selectedRole = UserRole.VIEW_ONLY
                        customPerms = PermissionDefaults.defaultPermissionsForRole(UserRole.VIEW_ONLY)
                    },
                    colors = waterFilterChipColors(),
                    label = { Text("View-Only", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (plantBranches.size > 1) {
                BranchAssignmentSelector(
                    branches = plantBranches,
                    selectedBranchIds = selectedBranches,
                    onToggleBranch = { bId ->
                        selectedBranches = if (bId in selectedBranches) {
                            if (selectedBranches.size > 1) selectedBranches - bId else selectedBranches
                        } else {
                            selectedBranches + bId
                        }
                    }
                )
            }

            AdminSpecialControlsCard(
                permissions = customPerms,
                onPermissionsChanged = { customPerms = it },
                restrictToOwnDataOnly = restrictToOwnDataOnly,
                onRestrictOwnDataChanged = { restrictToOwnDataOnly = it },
                restrictToTodayOnly = restrictToTodayOnly,
                onRestrictTodayOnlyChanged = { restrictToTodayOnly = it }
            )

            PermissionMatrixEditor(
                permissions = customPerms,
                onPermissionsChanged = { customPerms = it }
            )

            if (errorMsg != null) {
                Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // 2. Admin Change Password for Any Staff Account (including Admin accounts)
    accountToChangePassword?.let { targetAcc ->
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var showPwd by remember { mutableStateOf(false) }
        var resetError by remember { mutableStateOf<String?>(null) }

        MobileFormDialog(
            title = "Change Password",
            subtitle = "${targetAcc.name} (@${targetAcc.username})",
            hasUnsavedChanges = newPassword.isNotBlank() || confirmPassword.isNotBlank(),
            onDismiss = { accountToChangePassword = null },
            footerContent = {
                TextButton(onClick = { accountToChangePassword = null }) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
                Button(
                    onClick = {
                        val cleanNew = newPassword.trim()
                        val cleanConfirm = confirmPassword.trim()
                        when {
                            cleanNew.isBlank() -> resetError = "Please enter a new password"
                            cleanNew != cleanConfirm -> resetError = "Passwords do not match"
                            else -> {
                                viewModel.resetUserPassword(targetAcc.id, cleanNew)
                                Toast.makeText(context, "Password changed for @${targetAcc.username}", Toast.LENGTH_SHORT).show()
                                accountToChangePassword = null
                            }
                        }
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Password", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Text(
                text = "Role: ${targetAcc.role.displayName}",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondaryDark
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; resetError = null },
                label = { Text("New Password*", fontSize = 10.5.sp) },
                trailingIcon = {
                    IconButton(onClick = { showPwd = !showPwd }) {
                        Icon(if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle visibility")
                    }
                },
                visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; resetError = null },
                label = { Text("Confirm New Password*", fontSize = 10.5.sp) },
                visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (resetError != null) {
                Text(resetError!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // 3. Admin Change Own Password Dialog
    if (showChangeOwnPwdDialog) {
        var currentPwd by remember { mutableStateOf("") }
        var newPwd by remember { mutableStateOf("") }
        var confirmPwd by remember { mutableStateOf("") }
        var showPwd by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        MobileFormDialog(
            title = "Change My Password",
            subtitle = "ایڈمن اپنا موجودہ پاس ورڈ تبدیل کریں",
            hasUnsavedChanges = currentPwd.isNotBlank() || newPwd.isNotBlank() || confirmPwd.isNotBlank(),
            onDismiss = { showChangeOwnPwdDialog = false },
            footerContent = {
                TextButton(onClick = { showChangeOwnPwdDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
                Button(
                    onClick = {
                        when {
                            currentPwd.isBlank() || newPwd.isBlank() -> errorMsg = "All fields are required"
                            newPwd != confirmPwd -> errorMsg = "New passwords do not match"
                            else -> {
                                viewModel.changeOwnPassword(currentPwd, newPwd) { success, msg ->
                                    if (success) {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        showChangeOwnPwdDialog = false
                                    } else {
                                        errorMsg = msg
                                    }
                                }
                            }
                        }
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Update Password", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            OutlinedTextField(
                value = currentPwd,
                onValueChange = { currentPwd = it; errorMsg = null },
                label = { Text("Current Password*", fontSize = 10.5.sp) },
                visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPwd,
                onValueChange = { newPwd = it; errorMsg = null },
                label = { Text("New Password*", fontSize = 10.5.sp) },
                trailingIcon = {
                    IconButton(onClick = { showPwd = !showPwd }) {
                        Icon(if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle visibility")
                    }
                },
                visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPwd,
                onValueChange = { confirmPwd = it; errorMsg = null },
                label = { Text("Confirm New Password*", fontSize = 10.5.sp) },
                visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (errorMsg != null) {
                Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // 4. Confirm Delete Staff Account
    accountToDelete?.let { targetAcc ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            title = {
                Text("Delete Staff Account?", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = ErrorRed)
            },
            text = {
                Text(
                    "Remove '${targetAcc.name}' (@${targetAcc.username})? They will no longer be able to sign in.\nکیا آپ یہ اسٹاف اکاؤنٹ حذف کرنا چاہتے ہیں؟",
                    fontSize = 11.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUserAccount(targetAcc.id) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
            }
        )
    }

    // 5. Resolve Pending Request Dialog
    requestToResolve?.let { req ->
        var inputPass by remember { mutableStateOf("123456") }

        MobileFormDialog(
            title = "Approve Password Reset",
            subtitle = "${req.fullName} (@${req.username})",
            hasUnsavedChanges = inputPass != "123456",
            onDismiss = { requestToResolve = null },
            footerContent = {
                TextButton(onClick = { requestToResolve = null }) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
                Button(
                    onClick = {
                        val pass = if (inputPass.isBlank()) "123456" else inputPass.trim()
                        viewModel.resolvePasswordReset(req.id, pass)
                        requestToResolve = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Approve & Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Text(
                text = "Request Time: ${req.requestTime}",
                fontSize = 10.sp,
                color = TextSecondaryDark
            )
            OutlinedTextField(
                value = inputPass,
                onValueChange = { inputPass = it },
                label = { Text("Set New Password (نیا پاس ورڈ)*", fontSize = 10.5.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 6. Edit Staff Account Dialog (Name, Phone, Staff Gmail, Role, Permissions, Branches)
    accountToEdit?.let { targetAcc ->
        val isPrimaryAdmin = targetAcc.isSuperAdmin || targetAcc.id == "user_admin_01" || targetAcc.username.equals("admin", ignoreCase = true)
        var name by remember(targetAcc.id) { mutableStateOf(targetAcc.name) }
        var phone by remember(targetAcc.id) { mutableStateOf(targetAcc.phone) }
        var googleEmail by remember(targetAcc.id) { mutableStateOf(targetAcc.googleEmail) }
        var selectedRole by remember(targetAcc.id) { mutableStateOf(targetAcc.role) }
        var customPerms by remember(targetAcc.id) {
            mutableStateOf(PermissionDefaults.normalizePermissions(targetAcc.permissions, targetAcc.role, isPrimaryAdmin))
        }
        var selectedBranches by remember(targetAcc.id) {
            mutableStateOf(targetAcc.assignedBranchIds.ifEmpty { listOf(activePlantId.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }) }.toSet())
        }
        var restrictToOwnDataOnly by remember(targetAcc.id) { mutableStateOf(targetAcc.restrictToOwnDataOnly) }
        var restrictToTodayOnly by remember(targetAcc.id) { mutableStateOf(targetAcc.restrictToTodayOnly) }
        var editError by remember { mutableStateOf<String?>(null) }

        val hasUnsaved = name != targetAcc.name || phone != targetAcc.phone ||
            googleEmail != targetAcc.googleEmail || selectedRole != targetAcc.role ||
            customPerms != targetAcc.permissions ||
            restrictToOwnDataOnly != targetAcc.restrictToOwnDataOnly ||
            restrictToTodayOnly != targetAcc.restrictToTodayOnly

        MobileFormDialog(
            title = "Edit Staff Account",
            subtitle = "Login ID: @${targetAcc.username}",
            hasUnsavedChanges = hasUnsaved,
            onDismiss = { accountToEdit = null },
            footerContent = {
                TextButton(onClick = { accountToEdit = null }) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
                Button(
                    onClick = {
                        val cleanName = name.trim()
                        if (cleanName.isEmpty()) {
                            editError = "Staff Full Name is required."
                            return@Button
                        }
                        val branchList = selectedBranches.toList().ifEmpty { listOf(activePlantId.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }) }
                        val finalRole = if (isPrimaryAdmin) UserRole.ADMIN else selectedRole
                        val finalPerms = if (isPrimaryAdmin) PermissionDefaults.fullAdminPermissions() else customPerms
                        viewModel.updateUserAccount(
                            targetAcc.copy(
                                name = cleanName,
                                phone = phone.trim(),
                                googleEmail = googleEmail.trim().lowercase(),
                                role = finalRole,
                                isSuperAdmin = isPrimaryAdmin,
                                permissions = finalPerms,
                                hasExplicitPermissions = true,
                                assignedBranchIds = branchList,
                                defaultBranchId = branchList.first(),
                                restrictToOwnDataOnly = if (isPrimaryAdmin) false else restrictToOwnDataOnly,
                                restrictToTodayOnly = if (isPrimaryAdmin) false else restrictToTodayOnly
                            )
                        )
                        Toast.makeText(context, "Updated @${targetAcc.username}", Toast.LENGTH_SHORT).show()
                        accountToEdit = null
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("save_edit_staff_btn")
                ) {
                    Text("Save Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; editError = null },
                label = { Text("Staff Full Name*", fontSize = 10.5.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Mobile Phone Number", fontSize = 10.5.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = googleEmail,
                onValueChange = { googleEmail = it },
                label = { Text("Staff Gmail (Optional Fallback)", fontSize = 10.5.sp) },
                placeholder = { Text("Optional — Staff ID works without Gmail", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (isPrimaryAdmin) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WaterSkyLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Super Admin protection enabled: role and full permissions cannot be downgraded.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                Text(
                    text = "Role Preset (عہدہ):",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaterNavyPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedRole == UserRole.INCHARGE,
                        onClick = {
                            selectedRole = UserRole.INCHARGE
                            customPerms = PermissionDefaults.defaultPermissionsForRole(UserRole.INCHARGE)
                        },
                        colors = waterFilterChipColors(),
                        label = { Text("Supervisor", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedRole == UserRole.ADMIN,
                        onClick = {
                            selectedRole = UserRole.ADMIN
                            customPerms = PermissionDefaults.defaultPermissionsForRole(UserRole.ADMIN)
                        },
                        colors = waterFilterChipColors(),
                        label = { Text("Admin", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedRole == UserRole.VIEW_ONLY,
                        onClick = {
                            selectedRole = UserRole.VIEW_ONLY
                            customPerms = PermissionDefaults.defaultPermissionsForRole(UserRole.VIEW_ONLY)
                        },
                        colors = waterFilterChipColors(),
                        label = { Text("View-Only", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (plantBranches.size > 1) {
                    BranchAssignmentSelector(
                        branches = plantBranches,
                        selectedBranchIds = selectedBranches,
                        onToggleBranch = { bId ->
                            selectedBranches = if (bId in selectedBranches) {
                                if (selectedBranches.size > 1) selectedBranches - bId else selectedBranches
                            } else {
                                selectedBranches + bId
                            }
                        }
                    )
                }

                AdminSpecialControlsCard(
                    permissions = customPerms,
                    onPermissionsChanged = { customPerms = it },
                    restrictToOwnDataOnly = restrictToOwnDataOnly,
                    onRestrictOwnDataChanged = { restrictToOwnDataOnly = it },
                    restrictToTodayOnly = restrictToTodayOnly,
                    onRestrictTodayOnlyChanged = { restrictToTodayOnly = it }
                )

                PermissionMatrixEditor(
                    permissions = customPerms,
                    onPermissionsChanged = { customPerms = it }
                )
            }
            if (editError != null) {
                Text(editError!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BranchAssignmentSelector(
    branches: List<PlantBranch>,
    selectedBranchIds: Set<String>,
    onToggleBranch: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Assigned Plant Branches:",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = WaterNavyPrimary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            branches.forEach { b ->
                val selected = b.id in selectedBranchIds
                FilterChip(
                    selected = selected,
                    onClick = { onToggleBranch(b.id) },
                    label = { Text(b.name, fontSize = 9.5.sp, maxLines = 1) },
                    colors = waterFilterChipColors()
                )
            }
        }
    }
}

@Composable
private fun AdminSpecialControlsCard(
    permissions: Map<String, List<String>>,
    onPermissionsChanged: (Map<String, List<String>>) -> Unit,
    restrictToOwnDataOnly: Boolean = false,
    onRestrictOwnDataChanged: (Boolean) -> Unit = {},
    restrictToTodayOnly: Boolean = false,
    onRestrictTodayOnlyChanged: (Boolean) -> Unit = {}
) {
    val canChangeRates = PermissionAction.EDIT.name in permissions[AppModule.CUSTOMER_RATES.name].orEmpty()
    val canAddStock = PermissionAction.ADD.name in permissions[AppModule.BOTTLE_STOCK.name].orEmpty() ||
        PermissionAction.ADD.name in permissions[AppModule.FACTORY_MATERIALS.name].orEmpty()
    val canAddNewItems = PermissionAction.ADD.name in permissions[AppModule.NEW_ITEMS.name].orEmpty()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = WaterSkyLight.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, WaterAqua.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_special_controls_card")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Admin Special Controls (ایڈمن خصوصی اختیارات)",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WaterNavyPrimary
            )
            Text(
                text = "Allow or block staff from changing customer rates, adding stock, or adding new items/sizes.",
                fontSize = 9.sp,
                color = TextSecondaryDark
            )

            AdminControlToggleRow(
                title = "Change Customer Rates",
                subtitle = "گاہک کے بوتل ریٹ سیٹ یا تبدیل کرنے کی اجازت",
                checked = canChangeRates,
                tag = "toggle_ctrl_customer_rates",
                onCheckedChange = { allowed ->
                    val next = permissions.toMutableMap()
                    next[AppModule.CUSTOMER_RATES.name] = if (allowed) {
                        listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name, PermissionAction.EDIT.name)
                    } else {
                        listOf(PermissionAction.VIEW.name)
                    }
                    onPermissionsChanged(next)
                }
            )

            HorizontalDivider(color = BorderLight.copy(alpha = 0.6f))

            AdminControlToggleRow(
                title = "Add Stock (Bottles & Materials)",
                subtitle = "نیا بوتل یا فیکٹری میٹریل اسٹاک شامل کرنے کی اجازت",
                checked = canAddStock,
                tag = "toggle_ctrl_add_stock",
                onCheckedChange = { allowed ->
                    val next = permissions.toMutableMap()
                    val bottleActions = next[AppModule.BOTTLE_STOCK.name].orEmpty().toMutableSet()
                    val matActions = next[AppModule.FACTORY_MATERIALS.name].orEmpty().toMutableSet()
                    if (allowed) {
                        bottleActions.add(PermissionAction.VIEW.name)
                        bottleActions.add(PermissionAction.ADD.name)
                        matActions.add(PermissionAction.VIEW.name)
                        matActions.add(PermissionAction.ADD.name)
                    } else {
                        bottleActions.remove(PermissionAction.ADD.name)
                        matActions.remove(PermissionAction.ADD.name)
                    }
                    next[AppModule.BOTTLE_STOCK.name] = bottleActions.toList()
                    next[AppModule.FACTORY_MATERIALS.name] = matActions.toList()
                    onPermissionsChanged(next)
                }
            )

            HorizontalDivider(color = BorderLight.copy(alpha = 0.6f))

            AdminControlToggleRow(
                title = "Add New Items & Sizes",
                subtitle = "نیا بوتل سائز، آئٹم، میٹریل یا خرچہ کیٹیگری بنانے کی اجازت",
                checked = canAddNewItems,
                tag = "toggle_ctrl_new_items",
                onCheckedChange = { allowed ->
                    val next = permissions.toMutableMap()
                    next[AppModule.NEW_ITEMS.name] = if (allowed) {
                        listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name, PermissionAction.EDIT.name)
                    } else {
                        emptyList()
                    }
                    onPermissionsChanged(next)
                }
            )

            HorizontalDivider(color = BorderLight.copy(alpha = 0.6f))

            Text(
                text = "Data Visibility Controls (ڈیٹا چھپانے کا کنٹرول)",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WarningOrange,
                modifier = Modifier.padding(top = 2.dp)
            )

            AdminControlToggleRow(
                title = "Hide Other Users' Data (Own Data Only)",
                subtitle = "صرف اس یوزر کا اپنا ڈیٹا نظر آئے — دوسرے عملے کی انٹریز چھپ جائیں",
                checked = restrictToOwnDataOnly,
                tag = "toggle_ctrl_own_data_only",
                onCheckedChange = onRestrictOwnDataChanged
            )

            HorizontalDivider(color = BorderLight.copy(alpha = 0.6f))

            AdminControlToggleRow(
                title = "Hide Old / Past Data (Today Only)",
                subtitle = "صرف آج کا ڈیٹا نظر آئے — گزشتہ دنوں کا پرانا ریکارڈ چھپ جائے",
                checked = restrictToTodayOnly,
                tag = "toggle_ctrl_today_only",
                onCheckedChange = onRestrictTodayOnlyChanged
            )
        }
    }
}

@Composable
private fun AdminControlToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyDark
            )
            Text(
                text = subtitle,
                fontSize = 8.5.sp,
                color = TextSecondaryDark
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SuccessGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = ErrorRed.copy(alpha = 0.45f)
            )
        )
    }
}

@Composable
private fun PermissionMatrixEditor(
    permissions: Map<String, List<String>>,
    onPermissionsChanged: (Map<String, List<String>>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Granular Module Permissions (تفصیلی اختیارات)",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyPrimary
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide" else "Customize", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (expanded) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppModule.entries.forEach { module ->
                        val currentActions = permissions[module.name].orEmpty().toSet()
                        val isHidden = currentActions.isEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${module.title} (${module.urduTitle})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHidden) ErrorRed else WaterNavyDark
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isHidden) ErrorRedLight else SuccessGreenLight
                                ) {
                                    Text(
                                        text = if (isHidden) "Hidden (بند)" else "Visible (${currentActions.size})",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHidden) ErrorRed else SuccessGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                PermissionAction.entries.forEach { action ->
                                    val checked = action.name in currentActions
                                    FilterChip(
                                        selected = checked,
                                        onClick = {
                                            val nextSet = when {
                                                // Unchecking VIEW hides the entire module completely (clears all actions)
                                                checked && action == PermissionAction.VIEW -> emptySet()
                                                checked -> currentActions - action.name
                                                // Checking any action automatically grants VIEW too
                                                else -> currentActions + action.name + PermissionAction.VIEW.name
                                            }
                                            val updatedMap = permissions.toMutableMap()
                                            updatedMap[module.name] = nextSet.toList()
                                            onPermissionsChanged(updatedMap)
                                        },
                                        label = {
                                            Text(
                                                text = action.name.lowercase().replaceFirstChar { it.uppercase() },
                                                fontSize = 8.5.sp,
                                                maxLines = 1
                                            )
                                        },
                                        colors = waterFilterChipColors()
                                    )
                                }
                            }
                        }
                        if (module != AppModule.entries.last()) {
                            HorizontalDivider(color = BorderLight.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
