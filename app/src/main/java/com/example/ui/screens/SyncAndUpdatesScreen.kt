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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.AppUpdateChecker
import com.example.model.*
import com.example.ui.components.MobileFormDialog
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel

@Composable
fun SyncAndUpdatesScreen(viewModel: WaterPlantViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isCloudConnected by viewModel.isCloudConnected.collectAsState()
    val isDeviceOnline by viewModel.isDeviceOnline.collectAsState()
    val isSyncFlushing by viewModel.isSyncFlushing.collectAsState()
    val syncQueueItems by viewModel.syncQueueItems.collectAsState()
    val syncConflicts by viewModel.syncConflicts.collectAsState()
    val plantBranches by viewModel.plantBranches.collectAsState()
    val activePlantId by viewModel.activePlantId.collectAsState()
    val appReleaseInfo by viewModel.appReleaseInfo.collectAsState()
    val availableAppUpdate by viewModel.availableAppUpdate.collectAsState()
    val isCheckingAppUpdate by viewModel.isCheckingAppUpdate.collectAsState()
    val appUpdateStatusMessage by viewModel.appUpdateStatusMessage.collectAsState()
    val context = LocalContext.current

    val canManageSettings = viewModel.hasPermission(AppModule.SETTINGS, PermissionAction.MANAGE)
    val canSwitchBranch = currentUser.isSuperAdmin || currentUser.role == UserRole.ADMIN || currentUser.assignedBranchIds.size > 1

    var queueFilter by remember { mutableStateOf<SyncQueueStatus?>(null) }
    var showAddBranchDialog by remember { mutableStateOf(false) }
    var showUpdateSettings by remember { mutableStateOf(false) }
    var showManualLinkFields by remember { mutableStateOf(false) }
    var githubRepoInput by remember(appReleaseInfo?.githubRepo) {
        mutableStateOf(
            appReleaseInfo?.githubRepo?.ifBlank { AppUpdateChecker.defaultConfiguredGithubRepo() }
                ?: AppUpdateChecker.defaultConfiguredGithubRepo()
        )
    }
    var releaseVersionNameInput by remember(appReleaseInfo?.versionName) {
        mutableStateOf(appReleaseInfo?.versionName?.ifBlank { BuildConfig.VERSION_NAME } ?: BuildConfig.VERSION_NAME)
    }
    var releaseVersionCodeInput by remember(appReleaseInfo?.versionCode) {
        mutableStateOf((appReleaseInfo?.versionCode?.takeIf { it > 0 } ?: (BuildConfig.VERSION_CODE + 1)).toString())
    }
    var releaseUrlInput by remember(appReleaseInfo?.downloadUrl) { mutableStateOf(appReleaseInfo?.downloadUrl.orEmpty()) }
    var releaseNotesInput by remember(appReleaseInfo?.releaseNotes) { mutableStateOf(appReleaseInfo?.releaseNotes.orEmpty()) }
    var releaseMandatory by remember(appReleaseInfo?.mandatory) { mutableStateOf(appReleaseInfo?.mandatory ?: false) }
    var isPublishingRelease by remember { mutableStateOf(false) }

    val unsyncedActionGroups = remember(syncQueueItems) {
        syncQueueItems
            .filter { it.status != SyncQueueStatus.SYNCED }
            .groupBy { it.actionGroupId.ifBlank { it.id } }
            .map { (groupId, groupItems) ->
                val groupStatus = when {
                    groupItems.any { it.status == SyncQueueStatus.FAILED } -> SyncQueueStatus.FAILED
                    groupItems.any { it.status == SyncQueueStatus.SYNCING } -> SyncQueueStatus.SYNCING
                    else -> SyncQueueStatus.PENDING
                }
                val title = groupItems
                    .firstOrNull { it.actionTitle.isNotBlank() }
                    ?.actionTitle
                    ?: groupItems.first().collection
                val failedInGroup = groupItems.count { it.status == SyncQueueStatus.FAILED }
                val firstError = groupItems.firstOrNull { it.lastError.isNotBlank() }?.lastError.orEmpty()
                SyncActionGroup(
                    groupId = groupId,
                    title = title,
                    status = groupStatus,
                    itemCount = groupItems.size,
                    failedCount = failedInGroup,
                    attemptCount = groupItems.maxOfOrNull { it.attemptCount } ?: 0,
                    lastError = firstError,
                    updatedAtMillis = groupItems.maxOfOrNull { it.updatedAtMillis } ?: 0L,
                    itemIds = groupItems.map { it.id }
                )
            }
            .sortedByDescending { it.updatedAtMillis }
    }

    val pendingActionCount = unsyncedActionGroups.count { it.status == SyncQueueStatus.PENDING || it.status == SyncQueueStatus.SYNCING }
    val failedActionCount = unsyncedActionGroups.count { it.status == SyncQueueStatus.FAILED }
    val unresolvedConflicts = syncConflicts.filter { !it.resolved }

    LaunchedEffect(unsyncedActionGroups.isEmpty()) {
        if (unsyncedActionGroups.isEmpty()) {
            queueFilter = null
        }
    }

    val filteredGroups = remember(unsyncedActionGroups, queueFilter) {
        when (queueFilter) {
            null -> unsyncedActionGroups
            SyncQueueStatus.PENDING -> unsyncedActionGroups.filter { it.status == SyncQueueStatus.PENDING || it.status == SyncQueueStatus.SYNCING }
            SyncQueueStatus.FAILED -> unsyncedActionGroups.filter { it.status == SyncQueueStatus.FAILED }
            else -> unsyncedActionGroups
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface)
            .testTag("sync_and_updates_screen"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Live Connectivity & Sync Health Header
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud Sync & Plant Branches",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = syncStatus,
                                color = WaterSkyLight,
                                fontSize = 10.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDeviceOnline && isCloudConnected) SuccessGreenLight else if (isDeviceOnline) WarningOrangeLight else ErrorRedLight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isDeviceOnline && isCloudConnected) SuccessGreen else if (isDeviceOnline) WarningOrange else ErrorRed)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = when {
                                        !isDeviceOnline -> "Offline"
                                        isCloudConnected && unsyncedActionGroups.isEmpty() -> "All Synced"
                                        isCloudConnected -> "Cloud Live"
                                        else -> "Standby"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WaterNavyDark
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QueueMetricPill("Pending", pendingActionCount, WarningOrange, Modifier.weight(1f))
                        QueueMetricPill("Failed", failedActionCount, ErrorRed, Modifier.weight(1f))
                        QueueMetricPill("Conflicts", unresolvedConflicts.size, WaterAqua, Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.flushOfflineSyncQueueNow() },
                            enabled = !isSyncFlushing && isDeviceOnline,
                            colors = ButtonDefaults.buttonColors(containerColor = WaterAqua, contentColor = WaterNavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("flush_sync_queue_btn")
                        ) {
                            if (isSyncFlushing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WaterNavyDark)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (unsyncedActionGroups.isEmpty()) "Sync Now" else "Sync Pending (${unsyncedActionGroups.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.refreshDataFromCloud() },
                            border = BorderStroke(1.dp, WaterCyanBright),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("pull_cloud_data_btn")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pull Cloud", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    Text(
                        text = "Firebase Realtime DB Active • Unlimited daily reads/writes (10 GB/mo free)",
                        color = WaterSkyLight.copy(alpha = 0.85f),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 1B. Free One-Click App Updates (OTA without USB Debugging)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(
                    width = if (availableAppUpdate != null) 1.5.dp else 1.dp,
                    color = if (availableAppUpdate != null) SuccessGreen else BorderLight
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_updates_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Updates (Free One-Click Install)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WaterNavyDark
                            )
                            Text(
                                text = "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }
                        StatusBadge(
                            text = if (availableAppUpdate != null) "UPDATE READY" else "UP TO DATE",
                            backgroundColor = if (availableAppUpdate != null) SuccessGreenLight else WaterSkyLight,
                            textColor = if (availableAppUpdate != null) SuccessGreen else WaterNavyPrimary
                        )
                    }

                    val pendingUpdate = availableAppUpdate
                    if (pendingUpdate != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SuccessGreenLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "New Version Available: v${pendingUpdate.versionName.ifBlank { pendingUpdate.versionCode.toString() }}" +
                                        if (pendingUpdate.versionCode > 0) " (Build ${pendingUpdate.versionCode})" else "",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WaterNavyDark
                                )
                                if (pendingUpdate.releaseNotes.isNotBlank()) {
                                    Text(
                                        text = pendingUpdate.releaseNotes,
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Button(
                                    onClick = { AppUpdateChecker.openUpdateUrl(context, pendingUpdate.downloadUrl) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("install_app_update_btn")
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download & Install Update", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    if (!appUpdateStatusMessage.isNullOrBlank()) {
                        Text(
                            text = appUpdateStatusMessage!!,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (availableAppUpdate != null) SuccessGreen else TextSecondaryDark
                        )
                    }

                    val activeRepo = appReleaseInfo?.githubRepo?.takeIf { it.isNotBlank() }
                        ?: AppUpdateChecker.defaultConfiguredGithubRepo()
                    if (activeRepo.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WaterSkyLight.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Auto-Update Connected: $activeRepo",
                                    fontSize = 10.sp,
                                    color = WaterNavyDark,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else if (currentUser.isSuperAdmin) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WarningOrangeLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "One-time setup: Tap 'Settings' below and paste your GitHub repo link once. After that, every push auto-notifies all phones!",
                                fontSize = 10.sp,
                                color = WarningOrange,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = !isCheckingAppUpdate && isDeviceOnline,
                            onClick = { viewModel.checkForAppUpdate(force = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("check_app_update_btn")
                        ) {
                            if (isCheckingAppUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isCheckingAppUpdate) "Checking..." else "Check Update", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        if (currentUser.isSuperAdmin) {
                            OutlinedButton(
                                onClick = { showUpdateSettings = !showUpdateSettings },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, WaterNavyPrimary.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .height(46.dp)
                                    .testTag("toggle_publish_update_btn")
                            ) {
                                Icon(
                                    if (showUpdateSettings) Icons.Default.Close else Icons.Default.Settings,
                                    contentDescription = "Update Settings",
                                    tint = WaterNavyPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (showUpdateSettings) "Close" else "Settings",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WaterNavyPrimary
                                )
                            }
                        }
                    }

                    if (currentUser.isSuperAdmin && showUpdateSettings) {
                        HorizontalDivider(color = BorderLight)
                        Text(
                            text = "One-Time GitHub Auto-Update Setup",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyDark
                        )
                        Text(
                            text = "Paste your PUBLIC GitHub repo link once (e.g. username/aab-e-baraan). Every time GitHub Actions finishes, all phones will automatically see the new update.",
                            fontSize = 10.sp,
                            color = TextSecondaryDark
                        )
                        OutlinedTextField(
                            value = githubRepoInput,
                            onValueChange = { githubRepoInput = it },
                            label = { Text("GitHub Repo Link (username/repo)", fontSize = 10.5.sp) },
                            placeholder = { Text("https://github.com/username/repo-name", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_github_repo_input")
                        )

                        TextButton(
                            onClick = { showManualLinkFields = !showManualLinkFields },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                if (showManualLinkFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showManualLinkFields) "Hide Manual APK Link Option" else "Or paste a direct APK link manually (Optional)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (showManualLinkFields) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = releaseVersionNameInput,
                                    onValueChange = { releaseVersionNameInput = it },
                                    label = { Text("Version (e.g. 1.0.2)", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = releaseVersionCodeInput,
                                    onValueChange = { releaseVersionCodeInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Build Code", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = releaseUrlInput,
                                onValueChange = { releaseUrlInput = it },
                                label = { Text("Direct HTTPS APK Link", fontSize = 10.sp) },
                                placeholder = { Text("https://.../Aab-e-Baraan.apk", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_download_url_input")
                            )
                            OutlinedTextField(
                                value = releaseNotesInput,
                                onValueChange = { releaseNotesInput = it },
                                label = { Text("What's New (Optional)", fontSize = 10.sp) },
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Keep Update Banner Pinned", fontSize = 10.5.sp, color = WaterNavyDark)
                                Switch(
                                    checked = releaseMandatory,
                                    onCheckedChange = { releaseMandatory = it }
                                )
                            }
                        }

                        Button(
                            enabled = !isPublishingRelease,
                            onClick = {
                                isPublishingRelease = true
                                val manualUrl = if (showManualLinkFields) releaseUrlInput else ""
                                val manualCode = if (showManualLinkFields) (releaseVersionCodeInput.toIntOrNull() ?: 0) else 0
                                val manualVer = if (showManualLinkFields) releaseVersionNameInput else ""
                                val manualNotes = if (showManualLinkFields) releaseNotesInput else ""
                                viewModel.publishAppRelease(
                                    versionCode = manualCode,
                                    versionName = manualVer,
                                    downloadUrl = manualUrl,
                                    githubRepo = githubRepoInput,
                                    releaseNotes = manualNotes,
                                    mandatory = if (showManualLinkFields) releaseMandatory else false
                                ) { ok, _ ->
                                    isPublishingRelease = false
                                    if (ok) showUpdateSettings = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("save_publish_update_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPublishingRelease) "Saving..." else "Save & Check for Update",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Multi-Branch Plant Selector & Management
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Centralized Plant Branches (${plantBranches.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WaterNavyDark
                            )
                            Text(
                                text = "Active Branch ID: $activePlantId",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }
                        if (canManageSettings) {
                            FilledTonalButton(
                                onClick = { showAddBranchDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(38.dp).testTag("add_plant_branch_btn")
                            ) {
                                Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Branch", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        plantBranches.forEach { branch ->
                            val selected = branch.id == activePlantId
                            FilterChip(
                                selected = selected,
                                enabled = canSwitchBranch || selected,
                                onClick = {
                                    if (!selected) {
                                        viewModel.switchActivePlantBranch(branch.id)
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (branch.city.isNotBlank()) "${branch.name} (${branch.city})" else branch.name,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                } else null,
                                colors = waterFilterChipColors()
                            )
                        }
                    }
                }
            }
        }

        // 4. Sync Conflicts Alert Section
        if (unresolvedConflicts.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningOrangeLight),
                    border = BorderStroke(1.dp, WarningOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sync Conflicts Detected (${unresolvedConflicts.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningOrange
                        )
                        unresolvedConflicts.forEach { conflict ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${conflict.collection} / ${conflict.docId}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WaterNavyDark
                                    )
                                    Text(
                                        text = "${conflict.recordLabel.ifBlank { conflict.collection }} • Local: ${conflict.localSummary} | Cloud: ${conflict.remoteSummary}",
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = { viewModel.resolveSyncConflict(conflict.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Mark Resolved", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Offline Sync Queue — shown ONLY when unsynced actions exist; 1 grouped card per complete action
        if (unsyncedActionGroups.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_sync_queue_header"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Offline Sync Queue (${filteredGroups.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WaterNavyDark
                    )
                    if (failedActionCount > 0) {
                        TextButton(onClick = { viewModel.retryAllFailedSyncItems() }) {
                            Text("Retry Failed", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                        }
                    }
                }
                if (pendingActionCount > 0 && failedActionCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = queueFilter == null,
                            onClick = { queueFilter = null },
                            label = { Text("All (${unsyncedActionGroups.size})", fontSize = 10.sp) },
                            colors = waterFilterChipColors()
                        )
                        FilterChip(
                            selected = queueFilter == SyncQueueStatus.PENDING,
                            onClick = { queueFilter = if (queueFilter == SyncQueueStatus.PENDING) null else SyncQueueStatus.PENDING },
                            label = { Text("Pending ($pendingActionCount)", fontSize = 10.sp) },
                            colors = waterFilterChipColors()
                        )
                        FilterChip(
                            selected = queueFilter == SyncQueueStatus.FAILED,
                            onClick = { queueFilter = if (queueFilter == SyncQueueStatus.FAILED) null else SyncQueueStatus.FAILED },
                            label = { Text("Failed ($failedActionCount)", fontSize = 10.sp) },
                            colors = waterFilterChipColors()
                        )
                    }
                }
            }

            items(filteredGroups, key = { it.groupId }) { group ->
                SyncActionGroupCard(
                    group = group,
                    onRetry = { viewModel.retrySyncActionGroup(group.groupId) }
                )
            }
        }
    }

    if (showAddBranchDialog) {
        var bName by remember { mutableStateOf("") }
        var bCity by remember { mutableStateOf("") }
        var bCode by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        MobileFormDialog(
            title = "Add Plant Branch",
            subtitle = "Create shared plant branch under centralized database",
            hasUnsavedChanges = bName.isNotBlank() || bCity.isNotBlank(),
            onDismiss = { showAddBranchDialog = false },
            footerContent = {
                TextButton(onClick = { showAddBranchDialog = false }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        if (bName.isBlank()) {
                            err = "Branch name is required."
                            return@Button
                        }
                        val ok = viewModel.addOrUpdatePlantBranch(
                            PlantBranch(
                                id = bCode.trim(),
                                name = bName.trim(),
                                city = bCity.trim(),
                                isPrimary = false,
                                active = true
                            )
                        )
                        if (ok) showAddBranchDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White)
                ) {
                    Text("Save Branch", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            OutlinedTextField(
                value = bName,
                onValueChange = { bName = it; err = null },
                label = { Text("Branch Name*") },
                placeholder = { Text("e.g. Aab e Baraan Lahore Branch") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bCity,
                onValueChange = { bCity = it },
                label = { Text("City / Area") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bCode,
                onValueChange = { bCode = it },
                label = { Text("Custom Plant ID (Optional)") },
                placeholder = { Text("Auto-generated if blank") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (err != null) {
                Text(err!!, color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QueueMetricPill(label: String, count: Int, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = WaterNavyDark.copy(alpha = 0.72f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                color = accent,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                color = WaterSkyLight,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SyncActionGroupCard(
    group: SyncActionGroup,
    onRetry: () -> Unit
) {
    val (bg, fg) = when (group.status) {
        SyncQueueStatus.SYNCED -> SuccessGreenLight to SuccessGreen
        SyncQueueStatus.FAILED -> ErrorRedLight to ErrorRed
        SyncQueueStatus.SYNCING -> WaterSkyLight to WaterBlueMedium
        SyncQueueStatus.PENDING -> WarningOrangeLight to WarningOrange
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_action_group_${group.groupId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusBadge(text = group.status.name, backgroundColor = bg, textColor = fg)
                    Text(
                        text = group.title,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val subtext = buildString {
                    append(if (group.itemCount == 1) "1 record pending" else "${group.itemCount} records in action")
                    if (group.failedCount > 0) append(" • ${group.failedCount} failed")
                    if (group.attemptCount > 0) append(" • Retries: ${group.attemptCount}")
                }
                Text(
                    text = subtext,
                    fontSize = 9.5.sp,
                    color = TextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (group.lastError.isNotBlank()) {
                    Text(
                        text = group.lastError,
                        fontSize = 9.5.sp,
                        color = ErrorRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onRetry,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("retry_sync_group_${group.groupId}")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry Sync Action",
                    tint = WaterNavyPrimary
                )
            }
        }
    }
}
