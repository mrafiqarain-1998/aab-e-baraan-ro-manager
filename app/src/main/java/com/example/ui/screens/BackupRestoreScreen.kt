package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.*
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import java.io.File

@Composable
fun BackupRestoreScreen(viewModel: WaterPlantViewModel) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val backupHistory by viewModel.backupHistory.collectAsState()
    val isAutoBackupEnabled by viewModel.isAutoBackupEnabled.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val isBackupInProgress by viewModel.isBackupInProgress.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isCloudConnected by viewModel.isCloudConnected.collectAsState()
    val cloudUserEmail by viewModel.cloudUserEmail.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Backup & Export, 1: Restore, 2: History
    var showBackupNotesDialog by remember { mutableStateOf(false) }
    var backupNotesInput by remember { mutableStateOf("") }

    // Dialog state for Safe Restore Confirmation
    var pendingVerificationReport by remember { mutableStateOf<BackupVerificationReport?>(null) }
    var showSafeRestoreConfirmDialog by remember { mutableStateOf(false) }
    var isRestoringInProgress by remember { mutableStateOf(false) }

    // Cloud backups list state
    var cloudBackupsList by remember { mutableStateOf<List<BackupMetadata>>(emptyList()) }
    var isLoadingCloudBackups by remember { mutableStateOf(false) }

    // Helper for sharing files via FileProvider
    fun shareFile(file: File, mimeType: String, title: String) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "Error exporting file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // File picker for restoring local JSON file
    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonString = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val report = viewModel.verifyBackupJson(jsonString)
                if (report.isValid && report.backupData != null) {
                    pendingVerificationReport = report
                    showSafeRestoreConfirmDialog = true
                } else {
                    val errMsg = report.errors.joinToString("\n")
                    Toast.makeText(context, "Invalid backup file:\n$errMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Permission check: Settings / Backup & Restore access required
    if (!viewModel.hasPermission(AppModule.SETTINGS, PermissionAction.VIEW)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ErrorRed.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "Administrator Authorization Required",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyDark,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "صرف ایڈمنسٹریٹر کو مکمل ڈیٹا بیس بیک اپ اور ڈیٹا بحالی (Restore) کی اجازت ہے۔",
                        fontSize = 11.5.sp,
                        color = TextSecondaryDark,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Current Account: ${currentUser.name} (${currentUser.role.displayName})",
                        fontSize = 10.5.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Overview & Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(WaterCyanBright.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = WaterCyanBright,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "System Backup & Restore",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ڈیٹا بیک اپ اور محفوظ بحالی نظام",
                                    color = WaterSkyLight,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.25f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "SECURED",
                                    color = SuccessGreen,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                    // Last Backup Time & Protection Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Last Successful Backup:",
                                color = WaterSkyLight.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                            Text(
                                text = lastBackupTime ?: "No backup recorded yet",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud Sync Status:",
                                color = WaterSkyLight.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                            Text(
                                text = cloudUserEmail?.let { "$it • $syncStatus" } ?: syncStatus,
                                color = if (isCloudConnected) WaterCyanBright else WarningAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }

                    // Auto-Backup Switch
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WaterNavyDark.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Update,
                                    contentDescription = null,
                                    tint = WaterAqua,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Daily Automatic Backup",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "روزانہ رات خودکار محفوظ بیک اپ",
                                        color = WaterSkyLight,
                                        fontSize = 9.5.sp
                                    )
                                }
                            }

                            Switch(
                                checked = isAutoBackupEnabled,
                                onCheckedChange = { viewModel.toggleAutoBackup(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = WaterNavyPrimary,
                                    checkedTrackColor = WaterCyanBright,
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        }
                    }

                    // Primary Action: BACKUP NOW BUTTON
                    Button(
                        onClick = { showBackupNotesDialog = true },
                        enabled = !isBackupInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = WaterCyanBright, contentColor = WaterNavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("backup_now_button")
                    ) {
                        if (isBackupInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = WaterNavyPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Securing Backup...",
                                color = WaterNavyPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        } else {
                            Icon(
                                Icons.Default.Backup,
                                contentDescription = null,
                                tint = WaterNavyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Backup Now (ابھی مکمل بیک اپ بنائیں)",
                                color = WaterNavyPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. Navigation Tabs (Backup & Export | Safe Restore | History)
        item {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.White,
                contentColor = WaterNavyPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Text(
                            text = "Export & CSV",
                            fontSize = 10.5.sp,
                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    icon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = {
                        activeTab = 1
                        isLoadingCloudBackups = true
                        viewModel.fetchCloudBackups { list ->
                            cloudBackupsList = list
                            isLoadingCloudBackups = false
                        }
                    },
                    text = {
                        Text(
                            text = "Safe Restore",
                            fontSize = 10.5.sp,
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = {
                        Text(
                            text = "History (${backupHistory.size})",
                            fontSize = 10.5.sp,
                            fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        // TAB CONTENT
        when (activeTab) {
            0 -> {
                // TAB 0: Export & CSV Files
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(
                                title = "Export Restorable JSON Backup",
                                urduTitle = "مکمل محفوظ فائل ڈاؤن لوڈ یا شیئر کریں"
                            )
                            Text(
                                text = "Exports a complete restorable JSON file containing all 19 collections (Customers, Deliveries, Payments, Expenses, Inventory, Bottle Allocations, Staff Accounts, etc.) with SHA-256 verification hash.",
                                fontSize = 10.5.sp,
                                color = TextSecondaryDark
                            )

                            Button(
                                onClick = {
                                    val file = viewModel.exportBackupFile("aab_ebaraan_backup")
                                    if (file != null) {
                                        shareFile(file, "application/json", "Aab e Baraan System Backup JSON")
                                    } else {
                                        Toast.makeText(context, "Failed to create export file", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export & Share System Backup (.JSON)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(
                                title = "Export Records as Excel / CSV",
                                urduTitle = "مائیکروسافٹ ایکسل یا گوگل شیٹس کے لیے سی ایس وی"
                            )
                            Text(
                                text = "Download individual table records as formatted CSV spreadsheets for financial auditing, reports, or offline record keeping.",
                                fontSize = 10.5.sp,
                                color = TextSecondaryDark
                            )

                            val csvTypes = listOf(
                                Triple("CUSTOMERS", "Customers Directory (گاہکوں کی فہرست)", Icons.Default.People),
                                Triple("DELIVERIES", "Delivery Dispatches (ترسیلات کا ریکارڈ)", Icons.Default.LocalShipping),
                                Triple("PAYMENTS", "Payment Receipts (وصولیوں کی رسیدیں)", Icons.Default.Payments),
                                Triple("EXPENSES", "Operating Expenses (اخراجات کا ریکارڈ)", Icons.Default.ReceiptLong),
                                Triple("INVENTORY", "Bottle Stock & Damage (بوتلوں کا اسٹاک)", Icons.Default.Inventory2),
                                Triple("CASH_CLOSING", "Cash Closings (شام بندش کا حساب)", Icons.Default.PointOfSale)
                            )

                            csvTypes.forEach { (typeKey, label, icon) ->
                                OutlinedButton(
                                    onClick = {
                                        val file = viewModel.generateCsvExport(typeKey)
                                        if (file != null) {
                                            shareFile(file, "text/csv", "Aab e Baraan $typeKey CSV")
                                        } else {
                                            Toast.makeText(context, "Error generating CSV", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WaterNavyPrimary)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = WaterNavyPrimary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Icon(Icons.Default.Download, contentDescription = "Download CSV", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // TAB 1: Safe Restore (File Picker & Cloud Firestore)
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningOrangeLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Safe Restore Guard Active",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange
                                )
                                Text(
                                    text = "Before any restore operation executes, the app automatically takes an immutable safety backup of current live data. No customer or financial records can be accidentally lost.",
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(
                                title = "Option A: Restore from Phone File (.json)",
                                urduTitle = "موبائل میں محفوظ کردہ فائل سے بحالی"
                            )
                            Text(
                                text = "Select any previously exported .json backup file from your phone storage, Google Drive, or downloads folder.",
                                fontSize = 10.5.sp,
                                color = TextSecondaryDark
                            )

                            Button(
                                onClick = { jsonPickerLauncher.launch("application/json") },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Browse & Select Backup File (.JSON)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionHeader(
                                    title = "Option B: Cloud Backups (Firestore)",
                                    urduTitle = "کلاؤڈ میں محفوظ شدہ بیک اپس"
                                )

                                IconButton(
                                    onClick = {
                                        isLoadingCloudBackups = true
                                        viewModel.fetchCloudBackups { list ->
                                            cloudBackupsList = list
                                            isLoadingCloudBackups = false
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Cloud Backups", tint = WaterNavyPrimary)
                                }
                            }

                            if (isLoadingCloudBackups) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = WaterNavyPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                }
                            } else if (cloudBackupsList.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = WaterSkyLight.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No cloud backups found on Firestore. Tap 'Backup Now' to create one.",
                                        fontSize = 10.5.sp,
                                        color = TextMuted,
                                        modifier = Modifier.padding(12.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    cloudBackupsList.forEach { cloudMeta ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = WaterSkyLight.copy(alpha = 0.4f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = cloudMeta.createdAt,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.5.sp,
                                                            color = WaterNavyDark
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = WaterNavyPrimary.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = cloudMeta.backupType.name,
                                                                fontSize = 9.sp,
                                                                color = WaterNavyPrimary,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "${cloudMeta.totalRecords} records secured • By ${cloudMeta.creatorName}",
                                                        fontSize = 10.sp,
                                                        color = TextSecondaryDark
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.fetchCloudBackupData(cloudMeta.backupId) { data ->
                                                            if (data != null) {
                                                                val rep = viewModel.verifyBackupJson(
                                                                    com.example.data.PlantDataStorage(context).serializeBackupData(data)
                                                                )
                                                                pendingVerificationReport = rep
                                                                showSafeRestoreConfirmDialog = true
                                                            } else {
                                                                Toast.makeText(context, "Could not download cloud backup data.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Restore", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // TAB 2: Backup History Log
                if (backupHistory.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.HistoryToggleOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No backup history recorded yet.", color = TextMuted, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                } else {
                    items(backupHistory) { item ->
                        BackupHistoryCard(
                            record = item,
                            onShare = {
                                val file = viewModel.exportBackupFile(item.id)
                                if (file != null) {
                                    shareFile(file, "application/json", "Aab e Baraan Backup ${item.id}")
                                } else {
                                    Toast.makeText(context, "File not available for sharing", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRestore = {
                                val file = viewModel.exportBackupFile(item.id)
                                if (file != null && file.exists()) {
                                    val json = file.readText()
                                    val rep = viewModel.verifyBackupJson(json)
                                    if (rep.isValid && rep.backupData != null) {
                                        pendingVerificationReport = rep
                                        showSafeRestoreConfirmDialog = true
                                    } else {
                                        Toast.makeText(context, "Backup integrity error: ${rep.errors.firstOrNull()}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Local file not found for this backup", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal: Backup Notes Dialog before creating Manual Backup
    if (showBackupNotesDialog) {
        AlertDialog(
            onDismissRequest = { showBackupNotesDialog = false },
            containerColor = Color.White,
            titleContentColor = WaterNavyDark,
            textContentColor = TextPrimaryDark,
            icon = {
                Icon(Icons.Default.Backup, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    text = "Create Database Backup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = WaterNavyDark,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This will capture an exact snapshot of all 19 collections (Customers, Deliveries, Payments, Expenses, Inventory, Rates, Staff) and sync to Cloud Firestore and local storage.",
                        fontSize = 10.5.sp,
                        color = TextSecondaryDark
                    )

                    OutlinedTextField(
                        value = backupNotesInput,
                        onValueChange = { backupNotesInput = it },
                        label = { Text("Backup Notes (Optional)") },
                        placeholder = { Text("e.g. End of Day Closing / Pre-Audit") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val notes = backupNotesInput.trim()
                        showBackupNotesDialog = false
                        backupNotesInput = ""
                        viewModel.backupNow(notes) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White)
                ) {
                    Text("Start Backup Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupNotesDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }

    // Modal: SAFE RESTORE CONFIRMATION & COMPARISON DIALOG
    if (showSafeRestoreConfirmDialog && pendingVerificationReport != null) {
        val report = pendingVerificationReport!!
        val currentCustCount by viewModel.customers.collectAsState()
        val currentDelCount by viewModel.deliveries.collectAsState()
        val currentPayCount by viewModel.payments.collectAsState()
        val currentExpCount by viewModel.expenses.collectAsState()

        AlertDialog(
            onDismissRequest = {
                if (!isRestoringInProgress) {
                    showSafeRestoreConfirmDialog = false
                    pendingVerificationReport = null
                }
            },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            icon = {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Safe Database Restore",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = ErrorRed,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "آگاہی: یہ عمل منتخب شدہ بیک اپ سے ڈیٹا بحال کرے گا۔",
                        fontSize = 10.5.sp,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pre-restore safety snapshot will be auto-created first.",
                                fontSize = 10.sp,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Comparison Table: Current Live vs Backup File
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = WaterSkyLight)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Entity", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                                Text("Current Live", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                                Text("In Backup", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                            }
                            HorizontalDivider()

                            val bCust = report.recordCounts["customers"] ?: report.backupData?.customers?.size ?: 0
                            val bDel = report.recordCounts["deliveries"] ?: report.backupData?.deliveries?.size ?: 0
                            val bPay = report.recordCounts["payments"] ?: report.backupData?.payments?.size ?: 0
                            val bExp = report.recordCounts["expenses"] ?: report.backupData?.expenses?.size ?: 0

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Customers", fontSize = 10.sp)
                                Text("${currentCustCount.size}", fontSize = 10.sp)
                                Text("$bCust", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Deliveries", fontSize = 10.sp)
                                Text("${currentDelCount.size}", fontSize = 10.sp)
                                Text("$bDel", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Payments", fontSize = 10.sp)
                                Text("${currentPayCount.size}", fontSize = 10.sp)
                                Text("$bPay", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Expenses", fontSize = 10.sp)
                                Text("${currentExpCount.size}", fontSize = 10.sp)
                                Text("$bExp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Records", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${currentCustCount.size + currentDelCount.size + currentPayCount.size + currentExpCount.size}", fontSize = 10.sp)
                                Text("${report.totalRecords}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                            }
                        }
                    }

                    Text(
                        text = "Backup ID: ${report.backupId}\nDate: ${report.createdAt}\nType: ${report.backupType.displayName}",
                        fontSize = 9.5.sp,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (report.backupData != null) {
                            isRestoringInProgress = true
                            viewModel.restoreFromVerifiedBackup(report.backupData, requireSafety = true) { success, msg ->
                                isRestoringInProgress = false
                                showSafeRestoreConfirmDialog = false
                                pendingVerificationReport = null
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isRestoringInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    if (isRestoringInProgress) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restoring...")
                    } else {
                        Text("Confirm & Restore Database")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSafeRestoreConfirmDialog = false
                        pendingVerificationReport = null
                    },
                    enabled = !isRestoringInProgress
                ) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
fun BackupHistoryCard(
    record: BackupHistoryRecord,
    onShare: () -> Unit,
    onRestore: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (record.type) {
                            BackupType.MANUAL -> Icons.Default.Backup
                            BackupType.AUTOMATIC -> Icons.Default.Update
                            BackupType.PRE_RESTORE_SAFETY -> Icons.Default.Shield
                        },
                        contentDescription = null,
                        tint = when (record.type) {
                            BackupType.MANUAL -> WaterNavyPrimary
                            BackupType.AUTOMATIC -> WaterAqua
                            BackupType.PRE_RESTORE_SAFETY -> WarningOrange
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = record.timestamp,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyDark
                        )
                        Text(
                            text = "${record.type.displayName} (${record.type.urduLabel})",
                            fontSize = 9.5.sp,
                            color = TextSecondaryDark
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (record.status == "SUCCESS") SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = record.status,
                        color = if (record.status == "SUCCESS") SuccessGreen else ErrorRed,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = record.recordCountSummary,
                fontSize = 10.sp,
                color = WaterNavyPrimary,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dest: ${record.destination}",
                    fontSize = 9.5.sp,
                    color = TextMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onShare,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 9.5.sp)
                    }

                    Button(
                        onClick = onRestore,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", fontSize = 9.5.sp)
                    }
                }
            }
        }
    }
}
