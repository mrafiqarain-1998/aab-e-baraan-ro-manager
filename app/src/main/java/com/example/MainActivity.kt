package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.util.TransactionNotificationHelper
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.credentials.CredentialManager
import com.example.auth.GoogleSignInHelper
import com.example.data.AppUpdateChecker
import com.example.model.Customer
import com.example.ui.components.AabLogoWatermarkOverlay
import com.example.ui.components.AabTopAppBar
import com.example.ui.screens.*
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenLight
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.WaterAqua
import com.example.ui.theme.WaterBlueDeep
import com.example.ui.theme.WaterMint
import com.example.ui.theme.WaterNavyPrimary
import com.example.ui.theme.WaterSkySurface
import com.example.viewmodel.WaterPlantViewModel
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

enum class AppScreen(val title: String, val urduTitle: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", "ڈیش بورڈ", Icons.Default.Dashboard),
    DELIVERIES("Deliveries", "ترسیلات", Icons.Default.LocalShipping),
    CUSTOMERS("Customers", "گاہک", Icons.Default.People),
    STOCK("Stock & Cost", "اسٹاک و لاگت", Icons.Default.Inventory2),
    CASH_CLOSING("Cash Closing", "کیش بندش", Icons.Default.PointOfSale),
    EXPENSES("Expenses", "اخراجات", Icons.Default.ReceiptLong),
    REPORTS("Reports", "رپورٹ", Icons.Default.Assessment),
    NEW_DELIVERY("New Delivery", "نئی ڈیلیوری", Icons.Default.AddShoppingCart),
    RECEIVE_PAYMENT("Receive Payment", "وصولی", Icons.Default.Payments),
    CUSTOMER_DETAIL("Customer Details", "گاہک کھاتہ", Icons.Default.Person),
    BOTTLE_SIZES("Bottle Sizes & Rates", "سائز و ریٹ", Icons.Default.Tune),
    USER_MANAGEMENT("Staff Login Accounts", "عملہ لاگ ان", Icons.Default.AdminPanelSettings),
    BACKUP_RESTORE("Backup & Restore", "بیک اپ اور بحالی", Icons.Default.CloudSync),
    AUDIT_HISTORY("Audit Trail", "آڈٹ ریکارڈ", Icons.Default.History),
    SYNC_CENTER("Sync & Branches", "سنک و برانچز", Icons.Default.CloudDone),
    APPROVALS("Approvals", "منظوریاں", Icons.Default.FactCheck)
}

class MainActivity : ComponentActivity() {

    private val viewModel: WaterPlantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TransactionNotificationHelper.createNotificationChannel(applicationContext)
        TransactionNotificationHelper.scheduleBackgroundSyncWorker(applicationContext)

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val credentialManager = remember { CredentialManager.create(context) }

            MyApplicationTheme(darkTheme = false) {
                var isAuthenticated by remember { mutableStateOf(viewModel.isUserLoggedIn()) }

                if (!isAuthenticated) {
                    AuthScreen(
                        viewModel = viewModel,
                        onAuthSuccess = { isAuthenticated = true }
                    )
                } else {
                    MainAppScaffold(
                        viewModel = viewModel,
                        onLogout = {
                            GoogleSignInHelper.clearAndSignOut(
                                credentialManager = credentialManager,
                                scope = coroutineScope,
                                onComplete = {
                                    viewModel.logout()
                                    isAuthenticated = false
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: WaterPlantViewModel,
    onLogout: () -> Unit = {}
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isCloudConnected by viewModel.isCloudConnected.collectAsState()
    val cloudUserEmail by viewModel.cloudUserEmail.collectAsState()
    val feedbackMessage by viewModel.userFeedbackMessage.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val syncQueueItems by viewModel.syncQueueItems.collectAsState()
    val isDeviceOnline by viewModel.isDeviceOnline.collectAsState()
    val plantBranches by viewModel.plantBranches.collectAsState()
    val activePlantId by viewModel.activePlantId.collectAsState()
    val availableAppUpdate by viewModel.availableAppUpdate.collectAsState()
    var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }

    val pendingSyncCount = remember(syncQueueItems) {
        syncQueueItems
            .filter { it.status != com.example.model.SyncQueueStatus.SYNCED }
            .map { it.actionGroupId.ifBlank { it.id } }
            .distinct()
            .size
    }
    val activeBranchName = remember(plantBranches, activePlantId) {
        plantBranches.find { it.id == activePlantId }?.name.orEmpty()
    }
    val canViewDashboard = viewModel.hasPermission(com.example.model.AppModule.DASHBOARD, com.example.model.PermissionAction.VIEW)
    val canViewDeliveries = viewModel.hasPermission(com.example.model.AppModule.DELIVERIES, com.example.model.PermissionAction.VIEW)
    val canViewCustomers = viewModel.hasPermission(com.example.model.AppModule.CUSTOMERS, com.example.model.PermissionAction.VIEW)
    val canViewExpenses = viewModel.hasPermission(com.example.model.AppModule.EXPENSES, com.example.model.PermissionAction.VIEW)
    val canViewStock = viewModel.hasPermission(com.example.model.AppModule.BOTTLE_STOCK, com.example.model.PermissionAction.VIEW) ||
        viewModel.hasPermission(com.example.model.AppModule.FACTORY_MATERIALS, com.example.model.PermissionAction.VIEW) ||
        viewModel.hasPermission(com.example.model.AppModule.BOTTLE_ISSUE, com.example.model.PermissionAction.VIEW) ||
        viewModel.hasPermission(com.example.model.AppModule.REFILL_BATCHES, com.example.model.PermissionAction.VIEW)
    val canViewReports = viewModel.hasPermission(com.example.model.AppModule.REPORTS, com.example.model.PermissionAction.VIEW)
    val canAddDelivery = viewModel.hasPermission(com.example.model.AppModule.DELIVERIES, com.example.model.PermissionAction.ADD)
    val canViewPayments = viewModel.hasPermission(com.example.model.AppModule.PAYMENTS_BALANCES, com.example.model.PermissionAction.VIEW)
    val canAddPayment = viewModel.hasPermission(com.example.model.AppModule.PAYMENTS_BALANCES, com.example.model.PermissionAction.ADD)
    val canManageSizes = viewModel.hasPermission(com.example.model.AppModule.BOTTLE_STOCK, com.example.model.PermissionAction.MANAGE)
    val canManageStaff = viewModel.hasPermission(com.example.model.AppModule.STAFF_MANAGEMENT, com.example.model.PermissionAction.VIEW)
    val canManageBackup = viewModel.hasPermission(com.example.model.AppModule.SETTINGS, com.example.model.PermissionAction.VIEW)
    val canViewAudit = viewModel.hasPermission(com.example.model.AppModule.AUDIT_HISTORY, com.example.model.PermissionAction.VIEW)
    val canAccessApprovals = currentUser.role == com.example.model.UserRole.ADMIN ||
        viewModel.hasPermission(com.example.model.AppModule.EXPENSES, com.example.model.PermissionAction.APPROVE) ||
        viewModel.hasPermission(com.example.model.AppModule.PAYMENTS_BALANCES, com.example.model.PermissionAction.APPROVE) ||
        viewModel.hasPermission(com.example.model.AppModule.STAFF_MANAGEMENT, com.example.model.PermissionAction.MANAGE)
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()

    val permittedNavItems = remember(
        currentUser,
        canViewDashboard,
        canViewDeliveries,
        canViewExpenses,
        canViewCustomers,
        canViewStock
    ) {
        buildList {
            if (canViewDashboard) add(AppScreen.DASHBOARD)
            if (canViewDeliveries) add(AppScreen.DELIVERIES)
            if (canViewExpenses) add(AppScreen.EXPENSES)
            if (canViewCustomers) add(AppScreen.CUSTOMERS)
            if (canViewStock) add(AppScreen.STOCK)
        }
    }

    fun isScreenPermitted(screen: AppScreen): Boolean = when (screen) {
        AppScreen.DASHBOARD -> canViewDashboard
        AppScreen.DELIVERIES -> canViewDeliveries
        AppScreen.CUSTOMERS, AppScreen.CUSTOMER_DETAIL -> canViewCustomers
        AppScreen.EXPENSES -> canViewExpenses
        AppScreen.STOCK -> canViewStock
        AppScreen.REPORTS -> canViewReports
        AppScreen.NEW_DELIVERY -> canAddDelivery
        AppScreen.RECEIVE_PAYMENT -> canAddPayment
        AppScreen.CASH_CLOSING -> canViewPayments || canAddPayment
        AppScreen.BOTTLE_SIZES -> canManageSizes
        AppScreen.USER_MANAGEMENT -> canManageStaff
        AppScreen.BACKUP_RESTORE -> canManageBackup
        AppScreen.AUDIT_HISTORY -> canViewAudit
        AppScreen.SYNC_CENTER -> true
        AppScreen.APPROVALS -> canAccessApprovals
    }

    val initialTab = permittedNavItems.firstOrNull() ?: AppScreen.SYNC_CENTER
    var currentScreen by remember { mutableStateOf(initialTab) }
    var screenStack by remember { mutableStateOf(listOf(initialTab)) }
    var activeCustomerForAction by remember { mutableStateOf<Customer?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            snackbarHostState.showSnackbar(feedbackMessage!!)
            viewModel.clearFeedback()
        }
    }

    fun navigateTo(screen: AppScreen) {
        if (!isScreenPermitted(screen)) return
        if (currentScreen != screen) {
            screenStack = screenStack + screen
            currentScreen = screen
        }
    }

    fun navigateBack() {
        if (screenStack.size > 1) {
            val newStack = screenStack.dropLast(1)
            val target = newStack.last()
            if (isScreenPermitted(target)) {
                screenStack = newStack
                currentScreen = target
                return
            }
        }
        val fallback = permittedNavItems.firstOrNull() ?: AppScreen.SYNC_CENTER
        screenStack = listOf(fallback)
        currentScreen = fallback
    }

    BackHandler(enabled = screenStack.size > 1) {
        navigateBack()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted && notificationsEnabled) {
                TransactionNotificationHelper.scheduleBackgroundSyncWorker(context.applicationContext)
            }
        }
    )

    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !TransactionNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(currentUser.id, activePlantId) {
        viewModel.syncNotificationUserContext()
        val hasEmail = currentUser.email.contains("@")
        val firebaseSignedIn = try {
            com.google.firebase.Firebase.auth.currentUser != null
        } catch (_: Exception) {
            false
        }
        if (hasEmail && !firebaseSignedIn) {
            GoogleSignInHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { uid, email, displayName ->
                    viewModel.connectGoogleCloudSession(uid, email, displayName)
                },
                onUnauthenticated = {},
                scope = coroutineScope
            )
        }
    }

    LaunchedEffect(currentUser, currentScreen, permittedNavItems) {
        if (!isScreenPermitted(currentScreen)) {
            val fallback = permittedNavItems.firstOrNull() ?: AppScreen.SYNC_CENTER
            screenStack = listOf(fallback)
            currentScreen = fallback
        }
    }

    val isSubScreen = currentScreen in listOf(
        AppScreen.NEW_DELIVERY,
        AppScreen.RECEIVE_PAYMENT,
        AppScreen.CUSTOMER_DETAIL,
        AppScreen.CASH_CLOSING,
        AppScreen.REPORTS,
        AppScreen.BOTTLE_SIZES,
        AppScreen.USER_MANAGEMENT,
        AppScreen.BACKUP_RESTORE,
        AppScreen.AUDIT_HISTORY,
        AppScreen.SYNC_CENTER,
        AppScreen.APPROVALS
    )

    Scaffold(
        topBar = {
            if (currentScreen != AppScreen.CUSTOMER_DETAIL) {
                AabTopAppBar(
                    title = currentScreen.title,
                    subtitle = currentScreen.urduTitle,
                    currentRole = currentUser.role,
                    currentUserName = currentUser.name,
                    syncStatus = syncStatus,
                    isCloudConnected = isCloudConnected,
                    cloudUserEmail = cloudUserEmail,
                    onConnectGoogleCloud = {
                        GoogleSignInHelper.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = { uid, email, displayName ->
                                viewModel.connectGoogleCloudSession(uid, email, displayName)
                            },
                            onAuthError = { err ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Google Cloud link error: $err")
                                }
                            },
                            scope = coroutineScope
                        )
                    },
                    onSwitchRole = { newRole -> viewModel.switchRole(newRole) },
                    onVerifyAdminPassword = { pwd -> viewModel.verifyAdminPassword(pwd) },
                    onVerifyRolePassword = { role, pwd -> viewModel.verifyRolePassword(role, pwd) },
                    onSwitchUserCredentials = { u, p, onRes -> viewModel.switchUserWithCredentials(u, p, onRes) },
                    onRefreshCloud = { viewModel.refreshDataFromCloud() },
                    onNavigateToUserManagement = { navigateTo(AppScreen.USER_MANAGEMENT) },
                    onNavigateToBackupRestore = { navigateTo(AppScreen.BACKUP_RESTORE) },
                    onNavigateToAuditHistory = { navigateTo(AppScreen.AUDIT_HISTORY) },
                    onNavigateToSyncCenter = { navigateTo(AppScreen.SYNC_CENTER) },
                    canManageStaff = canManageStaff,
                    canManageBackup = canManageBackup,
                    canViewAudit = canViewAudit,
                    pendingSyncCount = pendingSyncCount,
                    isDeviceOnline = isDeviceOnline,
                    activeBranchName = activeBranchName,
                    notificationsEnabled = notificationsEnabled,
                    onToggleNotifications = { viewModel.setNotificationsEnabled(it) },
                    onLogout = onLogout,
                    navigationIcon = if (isSubScreen) {
                        {
                            IconButton(onClick = { navigateBack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                    } else null,
                    actions = {
                        if (canViewReports) {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text("Share Report / Analytics") } },
                                state = rememberTooltipState()
                            ) {
                                IconButton(
                                    onClick = { navigateTo(AppScreen.REPORTS) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("topbar_share_report_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Assessment,
                                        contentDescription = "Share Report",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isSubScreen && permittedNavItems.isNotEmpty()) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 6.dp
                ) {
                    permittedNavItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            alwaysShowLabel = true,
                            onClick = {
                                screenStack = listOf(screen)
                                currentScreen = screen
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = screen.title,
                                        maxLines = 1,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = screen.urduTitle,
                                        maxLines = 1,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Medium,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = WaterNavyPrimary,
                                selectedTextColor = WaterNavyPrimary,
                                unselectedIconColor = TextSecondaryDark,
                                unselectedTextColor = TextSecondaryDark,
                                indicatorColor = WaterAqua.copy(alpha = 0.18f)
                            )
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WaterSkySurface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val updateInfo = availableAppUpdate
            val updateKey = updateInfo?.let { "${it.versionCode}_${it.versionName}" }
            if (updateInfo != null && (updateInfo.mandatory || dismissedUpdateVersion != updateKey)) {
                Surface(
                    color = SuccessGreenLight,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_update_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "App Update Available",
                            tint = SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New Update v${updateInfo.versionName.ifBlank { updateInfo.versionCode.toString() }} Available",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = updateInfo.releaseNotes.ifBlank { "Tap Update to download & install the latest APK" },
                                fontSize = 9.5.sp,
                                color = TextSecondaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                val opened = AppUpdateChecker.openUpdateUrl(context, updateInfo.downloadUrl)
                                if (!opened) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Unable to open update link. Check URL in Sync & Branches.")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("banner_update_now_btn")
                        ) {
                            Text("Update", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (!updateInfo.mandatory) {
                            IconButton(
                                onClick = { dismissedUpdateVersion = updateKey },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss update banner",
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
            when (currentScreen) {
                AppScreen.DASHBOARD -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToNewDelivery = {
                            activeCustomerForAction = null
                            navigateTo(AppScreen.NEW_DELIVERY)
                        },
                        onNavigateToCustomers = { navigateTo(AppScreen.CUSTOMERS) },
                        onNavigateToReceivePayment = {
                            activeCustomerForAction = null
                            navigateTo(AppScreen.RECEIVE_PAYMENT)
                        },
                        onNavigateToExpenses = { navigateTo(AppScreen.EXPENSES) },
                        onNavigateToCashClosing = { navigateTo(AppScreen.CASH_CLOSING) },
                        onNavigateToInventory = { navigateTo(AppScreen.STOCK) },
                        onNavigateToSizes = { navigateTo(AppScreen.BOTTLE_SIZES) },
                        onNavigateToUserManagement = { navigateTo(AppScreen.USER_MANAGEMENT) },
                        onNavigateToReports = { navigateTo(AppScreen.REPORTS) },
                        onNavigateToBackupRestore = { navigateTo(AppScreen.BACKUP_RESTORE) },
                        onNavigateToApprovals = { navigateTo(AppScreen.APPROVALS) }
                    )
                }

                AppScreen.DELIVERIES -> {
                    DeliveriesListScreen(
                        viewModel = viewModel,
                        onNewDeliveryClick = {
                            activeCustomerForAction = null
                            navigateTo(AppScreen.NEW_DELIVERY)
                        }
                    )
                }

                AppScreen.CUSTOMERS -> {
                    CustomerListScreen(
                        viewModel = viewModel,
                        onCustomerClick = { customer ->
                            activeCustomerForAction = customer
                            navigateTo(AppScreen.CUSTOMER_DETAIL)
                        },
                        onNavigateToNewDelivery = { customer ->
                            activeCustomerForAction = customer
                            navigateTo(AppScreen.NEW_DELIVERY)
                        },
                        onNavigateToReceivePayment = { customer ->
                            activeCustomerForAction = customer
                            navigateTo(AppScreen.RECEIVE_PAYMENT)
                        }
                    )
                }

                AppScreen.CUSTOMER_DETAIL -> {
                    if (activeCustomerForAction != null) {
                        CustomerDetailScreen(
                            customer = activeCustomerForAction!!,
                            viewModel = viewModel,
                            onBack = { navigateBack() },
                            onDeliver = { cust ->
                                activeCustomerForAction = cust
                                navigateTo(AppScreen.NEW_DELIVERY)
                            },
                            onReceivePayment = { cust ->
                                activeCustomerForAction = cust
                                navigateTo(AppScreen.RECEIVE_PAYMENT)
                            }
                        )
                    } else {
                        navigateBack()
                    }
                }

                AppScreen.NEW_DELIVERY -> {
                    NewDeliveryScreen(
                        viewModel = viewModel,
                        preSelectedCustomer = activeCustomerForAction,
                        onDeliverySaved = {
                            navigateBack()
                        }
                    )
                }

                AppScreen.RECEIVE_PAYMENT -> {
                    ReceivePaymentScreen(
                        viewModel = viewModel,
                        preSelectedCustomer = activeCustomerForAction,
                        onPaymentCompleted = {
                            navigateBack()
                        }
                    )
                }

                AppScreen.STOCK -> {
                    InventoryScreen(
                        viewModel = viewModel,
                        onManageSizesClick = { navigateTo(AppScreen.BOTTLE_SIZES) }
                    )
                }

                AppScreen.BOTTLE_SIZES -> {
                    BottleSizesScreen(viewModel = viewModel)
                }

                AppScreen.USER_MANAGEMENT -> {
                    UserManagementScreen(viewModel = viewModel)
                }

                AppScreen.CASH_CLOSING -> {
                    DailyCashClosingScreen(
                        viewModel = viewModel,
                        onClosingSubmitted = {
                            navigateBack()
                        }
                    )
                }

                AppScreen.EXPENSES -> {
                    ExpensesScreen(viewModel = viewModel)
                }

                AppScreen.REPORTS -> {
                    ReportsScreen(viewModel = viewModel)
                }

                AppScreen.BACKUP_RESTORE -> {
                    BackupRestoreScreen(viewModel = viewModel)
                }

                AppScreen.AUDIT_HISTORY -> {
                    AuditHistoryScreen(viewModel = viewModel)
                }

                AppScreen.SYNC_CENTER -> {
                    SyncAndUpdatesScreen(viewModel = viewModel)
                }

                AppScreen.APPROVALS -> {
                    AdminApprovalsScreen(viewModel = viewModel)
                }
            }
            AabLogoWatermarkOverlay()
        }
        }
    }
}
