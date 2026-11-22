package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.UserRole
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileFormDialog(
    title: String,
    subtitle: String? = null,
    hasUnsavedChanges: Boolean = false,
    onDismiss: () -> Unit,
    accentColor: Color = WaterNavyPrimary,
    footerContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var showDiscardPrompt by remember { mutableStateOf(false) }

    val attemptClose = {
        if (hasUnsavedChanges) {
            showDiscardPrompt = true
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = attemptClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 460.dp)
                .heightIn(max = 700.dp)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with Title + Close (X)
                Surface(
                    color = accentColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = attemptClose,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("dialog_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )

                if (footerContent != null) {
                    HorizontalDivider(color = BorderLight)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = footerContent
                    )
                }
            }
        }
    }

    if (showDiscardPrompt) {
        AlertDialog(
            onDismissRequest = { showDiscardPrompt = false },
            containerColor = Color.White,
            titleContentColor = WaterNavyPrimary,
            textContentColor = TextSecondaryDark,
            title = {
                Text(
                    "Discard Unsaved Changes?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = WaterNavyPrimary
                )
            },
            text = {
                Text(
                    "You have unsaved entries in this form. Closing now will discard them.\nکیا آپ غیر محفوظ تبدیلیاں ختم کرنا چاہتے ہیں؟",
                    fontSize = 11.sp,
                    color = TextSecondaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardPrompt = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Discard", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardPrompt = false }) {
                    Text("Keep Editing", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> CompactDropdownField(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select...",
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedText.ifBlank { placeholder },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
            },
            textStyle = LocalTextStyle.current.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selectedText.isBlank()) TextMuted else TextPrimaryDark
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimaryDark,
                unfocusedTextColor = TextPrimaryDark,
                focusedBorderColor = WaterNavyPrimary,
                unfocusedBorderColor = BorderLight,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No options available", fontSize = 11.sp, color = TextMuted) },
                    onClick = { expanded = false }
                )
            } else {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel(opt),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryDark
                            )
                        },
                        onClick = {
                            onOptionSelected(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    height: Dp = 40.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimaryDark,
        unfocusedTextColor = TextPrimaryDark,
        focusedBorderColor = WaterNavyPrimary,
        unfocusedBorderColor = BorderLight,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(height),
        readOnly = readOnly,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryDark
        ),
        cursorBrush = SolidColor(WaterNavyPrimary),
        interactionSource = interactionSource
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = singleLine,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            label = label?.let {
                {
                    Text(
                        text = it,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        fontSize = 9.5.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = colors,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            container = {
                OutlinedTextFieldDefaults.ContainerBox(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = colors,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AabTopAppBar(
    title: String,
    subtitle: String? = null,
    currentRole: UserRole,
    currentUserName: String = "",
    syncStatus: String = "Online",
    isCloudConnected: Boolean = false,
    cloudUserEmail: String? = null,
    onConnectGoogleCloud: (() -> Unit)? = null,
    isDarkMode: Boolean = false,
    onToggleDarkMode: (() -> Unit)? = null,
    onSwitchRole: (UserRole) -> Unit = {},
    onVerifyAdminPassword: (String) -> Boolean = { true },
    onVerifyRolePassword: (UserRole, String) -> Boolean = { role, pwd -> onVerifyAdminPassword(pwd) },
    onSwitchUserCredentials: ((username: String, password: String, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    onRefreshCloud: (() -> Unit)? = null,
    onNavigateToUserManagement: (() -> Unit)? = null,
    onNavigateToBackupRestore: (() -> Unit)? = null,
    onNavigateToAuditHistory: (() -> Unit)? = null,
    onNavigateToSyncCenter: (() -> Unit)? = null,
    canManageStaff: Boolean = false,
    canManageBackup: Boolean = false,
    canViewAudit: Boolean = false,
    pendingSyncCount: Int = 0,
    isDeviceOnline: Boolean = true,
    activeBranchName: String = "",
    notificationsEnabled: Boolean = true,
    onToggleNotifications: ((Boolean) -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var showRoleMenu by remember { mutableStateOf(false) }
    var showSwitchUserDialog by remember { mutableStateOf(false) }
    var switchUsername by remember { mutableStateOf("") }
    var switchPassword by remember { mutableStateOf("") }
    var switchPasswordVisible by remember { mutableStateOf(false) }
    var switchError by remember { mutableStateOf<String?>(null) }
    var isSwitching by remember { mutableStateOf(false) }

    if (showSwitchUserDialog) {
        val hasEdits = switchUsername.isNotBlank() || switchPassword.isNotBlank()
        MobileFormDialog(
            title = "Switch User (صارف تبدیل کریں)",
            subtitle = "Enter Username & Password to sign into another staff ID",
            hasUnsavedChanges = hasEdits,
            onDismiss = {
                showSwitchUserDialog = false
                switchUsername = ""
                switchPassword = ""
                switchError = null
                isSwitching = false
            },
            footerContent = {
                TextButton(
                    onClick = {
                        showSwitchUserDialog = false
                        switchUsername = ""
                        switchPassword = ""
                        switchError = null
                        isSwitching = false
                    }
                ) {
                    Text("Cancel", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
                Button(
                    enabled = !isSwitching,
                    onClick = {
                        val cleanUser = switchUsername.trim()
                        if (cleanUser.isEmpty() || switchPassword.isEmpty()) {
                            switchError = "Username and Password are both required (یوزر نیم اور پاس ورڈ دونوں ضروری ہیں)"
                            return@Button
                        }
                        if (onSwitchUserCredentials != null) {
                            isSwitching = true
                            switchError = null
                            onSwitchUserCredentials(cleanUser, switchPassword) { success, msg ->
                                isSwitching = false
                                if (success) {
                                    showSwitchUserDialog = false
                                    switchUsername = ""
                                    switchPassword = ""
                                    switchError = null
                                } else {
                                    switchError = msg
                                }
                            }
                        } else {
                            switchError = "Account switcher unavailable."
                        }
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_switch_user_btn")
                ) {
                    if (isSwitching) {
                        CircularProgressIndicator(
                            color = WaterSkyButtonContent,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Switch Account", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            OutlinedTextField(
                value = switchUsername,
                onValueChange = {
                    switchUsername = it
                    switchError = null
                },
                label = { Text("Username / Login ID*", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                placeholder = { Text("e.g. admin or rashid1", fontSize = 11.sp, color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    focusedBorderColor = WaterNavyPrimary,
                    unfocusedBorderColor = BorderLight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("switch_username_input")
            )

            OutlinedTextField(
                value = switchPassword,
                onValueChange = {
                    switchPassword = it
                    switchError = null
                },
                label = { Text("Password*", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    IconButton(onClick = { switchPasswordVisible = !switchPasswordVisible }) {
                        Icon(
                            imageVector = if (switchPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (switchPasswordVisible) "Hide password" else "Show password",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                visualTransformation = if (switchPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    focusedBorderColor = WaterNavyPrimary,
                    unfocusedBorderColor = BorderLight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("switch_password_input")
            )

            if (switchError != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ErrorRedLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = switchError!!,
                        color = ErrorRed,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }

    Surface(
        color = WaterNavyPrimary,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.padding(end = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.aab_e_baraan_logo),
                                    contentDescription = "Aab e Baraan Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                color = WaterSkyLight.copy(alpha = 0.85f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = navigationIcon ?: {},
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text("Switch User / Account") } },
                                state = rememberTooltipState()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when (currentRole) {
                                        UserRole.ADMIN -> WaterCyanBright
                                        UserRole.INCHARGE -> WaterAqua
                                        UserRole.VIEW_ONLY -> Color(0xFFBBDEFB)
                                    },
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clickable { showRoleMenu = true }
                                        .testTag("account_menu_chip")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (currentRole) {
                                                UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                                                UserRole.INCHARGE -> Icons.Default.Engineering
                                                UserRole.VIEW_ONLY -> Icons.Default.Visibility
                                            },
                                            contentDescription = "Switch User",
                                            tint = WaterNavyDark,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = currentRole.displayName.split(" ")[0],
                                            color = WaterNavyDark,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Account Options",
                                            tint = WaterNavyDark,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showRoleMenu,
                                onDismissRequest = { showRoleMenu = false }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(
                                        text = currentUserName.ifBlank { currentRole.displayName },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WaterNavyPrimary
                                    )
                                    Text(
                                        text = currentRole.displayName,
                                        fontSize = 9.5.sp,
                                        color = TextSecondaryDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "Switch User",
                                                fontWeight = FontWeight.Bold,
                                                color = WaterNavyPrimary,
                                                fontSize = 11.5.sp
                                            )
                                            Text(
                                                text = "یوزر نیم اور پاس ورڈ سے اکاؤنٹ بدلیں",
                                                fontSize = 9.5.sp,
                                                color = TextSecondaryDark
                                            )
                                        }
                                    },
                                    onClick = {
                                        showRoleMenu = false
                                        switchUsername = ""
                                        switchPassword = ""
                                        switchError = null
                                        showSwitchUserDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SwitchAccount,
                                            contentDescription = "Switch User",
                                            tint = WaterNavyPrimary
                                        )
                                    }
                                )

                                if (canManageStaff && onNavigateToUserManagement != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Staff ID & Permissions",
                                                    fontWeight = FontWeight.Bold,
                                                    color = WaterNavyPrimary,
                                                    fontSize = 11.5.sp
                                                )
                                                Text(
                                                    text = "عملہ اکاؤنٹس، پاس ورڈ اور اختیارات",
                                                    fontSize = 9.5.sp,
                                                    color = TextSecondaryDark
                                                )
                                            }
                                        },
                                        onClick = {
                                            showRoleMenu = false
                                            onNavigateToUserManagement()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = WaterNavyPrimary
                                            )
                                        }
                                    )
                                }

                                if (onToggleNotifications != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                                    Text(
                                                        text = if (notificationsEnabled) "Notifications: ON" else "Notifications: OFF",
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (notificationsEnabled) WaterNavyPrimary else ErrorRed,
                                                        fontSize = 11.5.sp
                                                    )
                                                    Text(
                                                        text = if (notificationsEnabled) "نوٹیفکیشنز آن ہیں (بند کرنے کیلئے کلک کریں)" else "نوٹیفکیشنز بند ہیں (آن کرنے کیلئے کلک کریں)",
                                                        fontSize = 9.sp,
                                                        color = TextSecondaryDark
                                                    )
                                                }
                                                Switch(
                                                    checked = notificationsEnabled,
                                                    onCheckedChange = { onToggleNotifications(it) },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = SuccessGreen,
                                                        uncheckedThumbColor = Color.White,
                                                        uncheckedTrackColor = ErrorRed.copy(alpha = 0.55f)
                                                    )
                                                )
                                            }
                                        },
                                        onClick = { onToggleNotifications(!notificationsEnabled) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                                contentDescription = "Toggle Notifications",
                                                tint = if (notificationsEnabled) SuccessGreen else ErrorRed
                                            )
                                        },
                                        modifier = Modifier.testTag("menu_toggle_notifications")
                                    )
                                }

                                if (onNavigateToSyncCenter != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = if (pendingSyncCount > 0) "Sync & Branches ($pendingSyncCount)" else "Sync & Branches",
                                                    fontWeight = FontWeight.Bold,
                                                    color = WaterNavyPrimary,
                                                    fontSize = 11.5.sp
                                                )
                                                Text(
                                                    text = "آف لائن سنک اور برانچز",
                                                    fontSize = 9.5.sp,
                                                    color = TextSecondaryDark
                                                )
                                            }
                                        },
                                        onClick = {
                                            showRoleMenu = false
                                            onNavigateToSyncCenter()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.CloudDone,
                                                contentDescription = null,
                                                tint = WaterNavyPrimary
                                            )
                                        }
                                    )
                                }

                                if (canViewAudit && onNavigateToAuditHistory != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Audit Trail History",
                                                    fontWeight = FontWeight.Bold,
                                                    color = WaterNavyPrimary,
                                                    fontSize = 11.5.sp
                                                )
                                                Text(
                                                    text = "تمام تبدیلیوں اور لاگ ان کا ریکارڈ",
                                                    fontSize = 9.5.sp,
                                                    color = TextSecondaryDark
                                                )
                                            }
                                        },
                                        onClick = {
                                            showRoleMenu = false
                                            onNavigateToAuditHistory()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = WaterNavyPrimary
                                            )
                                        }
                                    )
                                }

                                if (canManageBackup && onNavigateToBackupRestore != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Backup & Restore Database",
                                                    fontWeight = FontWeight.Bold,
                                                    color = WaterNavyPrimary,
                                                    fontSize = 11.5.sp
                                                )
                                                Text(
                                                    text = "ڈیٹا بیک اپ اور محفوظ بحالی نظام",
                                                    fontSize = 9.5.sp,
                                                    color = TextSecondaryDark
                                                )
                                            }
                                        },
                                        onClick = {
                                            showRoleMenu = false
                                            onNavigateToBackupRestore()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Backup,
                                                contentDescription = null,
                                                tint = WaterNavyPrimary
                                            )
                                        }
                                    )
                                }

                                if (onLogout != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Logout (لاگ آؤٹ کریں)",
                                                fontWeight = FontWeight.Bold,
                                                color = ErrorRed,
                                                fontSize = 11.5.sp
                                            )
                                        },
                                        onClick = {
                                            showRoleMenu = false
                                            onLogout()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Logout,
                                                contentDescription = "Logout",
                                                tint = ErrorRed
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if (onRefreshCloud != null) {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text("Refresh Cloud Data") } },
                                state = rememberTooltipState()
                            ) {
                                IconButton(
                                    onClick = onRefreshCloud,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("topbar_refresh_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Refresh Cloud Data",
                                        tint = WaterCyanBright,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        actions()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WaterNavyPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )

            // Micro Cloud Sync Ribbon
            val isPermissionOrDbError = syncStatus.contains("Denied", ignoreCase = true) ||
                syncStatus.contains("Not Found", ignoreCase = true) ||
                syncStatus.contains("Quota", ignoreCase = true)
            val statusDotColor = when {
                !isDeviceOnline -> ErrorRed
                isCloudConnected && pendingSyncCount == 0 -> SuccessGreen
                isPermissionOrDbError -> ErrorRed
                else -> WarningAmber
            }
            val ribbonLabel = buildString {
                if (activeBranchName.isNotBlank()) append("$activeBranchName • ")
                if (!isDeviceOnline) {
                    append("Offline")
                } else if (cloudUserEmail != null) {
                    append("Cloud ($cloudUserEmail)")
                } else {
                    append("Cloud")
                }
                append(" • $syncStatus")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WaterNavyDark)
                    .clickable {
                        if (onNavigateToSyncCenter != null) {
                            onNavigateToSyncCenter()
                        } else if (cloudUserEmail != null) {
                            onRefreshCloud?.invoke()
                        } else {
                            onConnectGoogleCloud?.invoke()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = ribbonLabel,
                        color = WaterSkyLight.copy(alpha = 0.88f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (pendingSyncCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WarningAmber.copy(alpha = 0.28f),
                        modifier = Modifier
                            .clickable { onNavigateToSyncCenter?.invoke() }
                            .testTag("pending_sync_pill")
                    ) {
                        Text(
                            text = "Pending: $pendingSyncCount",
                            color = WarningAmber,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (cloudUserEmail == null && onConnectGoogleCloud != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WaterCyanBright.copy(alpha = 0.22f),
                        modifier = Modifier
                            .clickable { onConnectGoogleCloud() }
                            .testTag("connect_google_cloud_chip")
                    ) {
                        Text(
                            text = "Connect Google",
                            color = WaterCyanBright,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricStatCard(
    title: String,
    urduTitle: String,
    value: String,
    icon: ImageVector,
    iconTint: Color = WaterAqua,
    cardBackground: Color = Color.White,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconTint.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WaterSkyLight
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WaterNavyPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimaryDark
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = title,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark
            )

            Text(
                text = urduTitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted
            )
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    urduText: String? = null,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = modifier.heightIn(min = 24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (urduText != null) {
                Text(
                    text = " • $urduText",
                    color = textColor.copy(alpha = 0.85f),
                    fontSize = 9.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    urduTitle: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = WaterNavyPrimary
            )
            Text(
                text = urduTitle,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondaryDark
            )
        }

        if (actionText != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = actionText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WaterAqua
                )
            }
        }
    }
}

@Composable
fun AabLogoWatermarkOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.aab_e_baraan_logo),
            contentDescription = null,
            alpha = 0.055f,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(250.dp)
                .padding(16.dp)
        )
    }
}

/**
 * Compact clickable "NEW" badge shown to OTHER users on newly added
 * Customers, Deliveries, Expenses, and Payments until opened or tapped.
 */
@Composable
fun NewItemBadge(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFE11D48),
        shadowElevation = 1.dp,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "New item" }
    ) {
        Text(
            text = "NEW",
            color = Color.White,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

/**
 * Smoothly blinking orange indicator dot shown on Customers whose profile details
 * are incomplete — continues blinking until all required customer details are filled.
 */
@Composable
fun BlinkingOrangeDot(
    modifier: Modifier = Modifier,
    dotSize: Dp = 9.dp,
    description: String = "Customer details missing"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "missing_detail_orange_blink")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orange_dot_alpha"
    )
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orange_dot_halo_scale"
    )
    val vividOrange = Color(0xFFF97316)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(dotSize + 6.dp)
            .semantics { contentDescription = description }
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(haloScale)
                .alpha(pulseAlpha * 0.38f)
                .clip(CircleShape)
                .background(vividOrange)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(vividOrange)
        )
    }
}

/**
 * Parses a "yyyy-MM-dd" string into epoch millis at local midnight. Returns null if invalid.
 */
fun parseIsoDateToMillis(isoDate: String): Long? {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        sdf.parse(isoDate.trim())?.time
    } catch (_: Throwable) {
        null
    }
}

/**
 * Formats an epoch timestamp into readable audit date-time text.
 */
fun formatEditTimestamp(epochMillis: Long): String {
    return try {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(epochMillis))
    } catch (_: Throwable) {
        ""
    }
}

/**
 * Reusable Edit Metadata strip shown ONLY inside internal Detail views
 * (Delivery Detail, Expense Detail, Customer Detail, Payment Detail) — NEVER on invoices or PDFs.
 */
@Composable
fun EditMetadataBadge(
    lastEditedAt: Long,
    lastEditedBy: String,
    lastEditNote: String = "",
    modifier: Modifier = Modifier
) {
    if (lastEditedAt <= 0L && lastEditedBy.isBlank() && lastEditNote.isBlank()) return
    val formattedTime = if (lastEditedAt > 0L) formatEditTimestamp(lastEditedAt) else ""
    val editor = lastEditedBy.ifBlank { "Staff" }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFF7ED),
        border = BorderStroke(1.dp, Color(0xFFFED7AA)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("edit_metadata_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = "Edit History",
                tint = WarningAmber,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Edited by $editor",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9A3412)
                )
                if (formattedTime.isNotBlank()) {
                    Text(
                        text = "Last Edited: $formattedTime",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFC2410C)
                    )
                }
                if (lastEditNote.isNotBlank()) {
                    Text(
                        text = "Edit Note: $lastEditNote",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF7C2D12),
                        lineHeight = 13.sp,
                        modifier = Modifier.testTag("edit_metadata_note_text")
                    )
                }
            }
        }
    }
}

/**
 * Reusable clickable Date Picker field for editing Delivery / Expense dates inside dialogs.
 */
@Composable
fun EditableDateField(
    label: String,
    dateValue: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    fun openCalendar() {
        val cal = Calendar.getInstance()
        val parsed = parseIsoDateToMillis(dateValue)
        if (parsed != null) cal.timeInMillis = parsed
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(formatted)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, BorderLight),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable { openCalendar() }
            .testTag("editable_date_field")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryDark
                )
                Text(
                    text = dateValue.ifBlank { "Select Date" },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WaterNavyPrimary
                )
            }
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Choose Date",
                tint = WaterAqua,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Reusable Custom Date Range Dialog with 2 Calendar Icon selectors ("From" and "To").
 * - Supports selecting the SAME date for single-day filtering (From == To)
 * - Supports selecting DIFFERENT dates for multi-day ranges
 * - Strictly prevents "From" date from ever being after "To" date!
 */
@Composable
fun FromToDateRangeDialog(
    initialFromDate: String,
    initialToDate: String,
    title: String = "Custom Date Range (تاریخ منتخب کریں)",
    onDismiss: () -> Unit,
    onConfirm: (fromDate: String, toDate: String) -> Unit
) {
    val context = LocalContext.current
    val today = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var fromDate by remember(initialFromDate) {
        mutableStateOf(initialFromDate.ifBlank { today })
    }
    var toDate by remember(initialToDate) {
        mutableStateOf(initialToDate.ifBlank { fromDate })
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Ensure initial consistency (From <= To)
    LaunchedEffect(Unit) {
        if (fromDate > toDate) {
            toDate = fromDate
        }
    }

    fun openFromCalendar() {
        val cal = Calendar.getInstance()
        parseIsoDateToMillis(fromDate)?.let { cal.timeInMillis = it }
        val picker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                if (picked > toDate) {
                    errorMessage = "From date cannot be after To date (شروع کی تاریخ آخری تاریخ کے بعد نہیں ہو سکتی)"
                } else {
                    fromDate = picked
                    errorMessage = null
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        // Enforce From <= To right inside the Android Calendar picker too!
        parseIsoDateToMillis(toDate)?.let { maxMs ->
            picker.datePicker.maxDate = maxMs + (24L * 60L * 60L * 1000L - 1000L)
        }
        picker.show()
    }

    fun openToCalendar() {
        val cal = Calendar.getInstance()
        parseIsoDateToMillis(toDate)?.let { cal.timeInMillis = it }
        val picker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                if (picked < fromDate) {
                    errorMessage = "To date cannot be before From date (آخری تاریخ شروع کی تاریخ سے پہلے نہیں ہو سکتی)"
                } else {
                    toDate = picked
                    errorMessage = null
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        // Enforce To >= From right inside the Android Calendar picker!
        parseIsoDateToMillis(fromDate)?.let { minMs ->
            picker.datePicker.minDate = minMs
        }
        picker.show()
    }

    MobileFormDialog(
        title = title,
        subtitle = "Tap From & To calendars (Same date = single day)",
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    if (fromDate > toDate) {
                        errorMessage = "From date cannot be after To date."
                    } else {
                        onConfirm(fromDate, toDate)
                    }
                },
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("apply_from_to_date_btn")
            ) {
                Text("Apply Dates", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Text(
            text = "For a single day report, select the same date in both From & To. For a range, pick different dates.",
            fontSize = 10.sp,
            color = TextSecondaryDark,
            lineHeight = 14.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // FROM Calendar Selector
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WaterSkyLight,
                border = BorderStroke(1.2.dp, WaterAqua),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 68.dp)
                    .clickable { openFromCalendar() }
                    .testTag("custom_date_from_btn")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "From Date Calendar",
                            tint = WaterAqua,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "FROM (از)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WaterNavyPrimary
                        )
                    }
                    Text(
                        text = fromDate,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimaryDark
                    )
                }
            }

            // TO Calendar Selector
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WaterSkyLight,
                border = BorderStroke(1.2.dp, WaterAqua),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 68.dp)
                    .clickable { openToCalendar() }
                    .testTag("custom_date_to_btn")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "To Date Calendar",
                            tint = WaterAqua,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "TO (تا)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WaterNavyPrimary
                        )
                    }
                    Text(
                        text = toDate,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimaryDark
                    )
                }
            }
        }

        // Quick helper shortcut to set single day or Today
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    toDate = fromDate
                    errorMessage = null
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("Same Single Day", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
            }
            OutlinedButton(
                onClick = {
                    fromDate = today
                    toDate = today
                    errorMessage = null
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("Reset to Today", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
            }
        }

        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ErrorRedLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage!!,
                    color = ErrorRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}



