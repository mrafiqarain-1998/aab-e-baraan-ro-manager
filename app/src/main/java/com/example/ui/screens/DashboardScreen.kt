package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppModule
import com.example.model.Delivery
import com.example.model.PermissionAction
import com.example.model.SyncQueueStatus
import com.example.model.UserRole
import com.example.ui.components.MetricStatCard
import com.example.ui.components.NewItemBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.viewmodel.DashboardStats
import com.example.viewmodel.WaterPlantViewModel

enum class DashboardPeriod(val label: String, val urduLabel: String) {
    TODAY("Today", "آج"),
    YESTERDAY("Yesterday", "گزشتہ کل"),
    WEEKLY("Weekly", "ہفتہ وار"),
    MONTHLY("Monthly", "ماہانہ"),
    CUSTOM("Custom Date", "منتخب تاریخ")
}

@Composable
fun DashboardScreen(
    viewModel: WaterPlantViewModel,
    onNavigateToNewDelivery: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToReceivePayment: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToCashClosing: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToSizes: () -> Unit,
    onNavigateToUserManagement: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToBackupRestore: () -> Unit = {}
) {
    val user by viewModel.currentUser.collectAsState()
    val stats by viewModel.dashboardStats.collectAsState()
    val deliveries by viewModel.deliveries.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val passwordResetRequests by viewModel.passwordResetRequests.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val seenRecordKeys by viewModel.seenRecordKeys.collectAsState()
    val isDeviceOnline by viewModel.isDeviceOnline.collectAsState()
    val isCloudConnected by viewModel.isCloudConnected.collectAsState()
    val isSyncFlushing by viewModel.isSyncFlushing.collectAsState()
    val syncQueueItems by viewModel.syncQueueItems.collectAsState()
    val factoryMaterials by viewModel.factoryMaterials.collectAsState()
    val bottleSizes by viewModel.bottleSizes.collectAsState()
    val plantWorkers by viewModel.plantWorkers.collectAsState()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showPasswordResetRequestsDialog by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf(DashboardPeriod.TODAY) }
    var showPeriodDropdown by remember { mutableStateOf(false) }

    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    val todayCal = remember { java.util.Calendar.getInstance() }
    val todayStr = remember { sdf.format(todayCal.time) }

    var customStartDate by remember { mutableStateOf(todayStr) }
    var customEndDate by remember { mutableStateOf(todayStr) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val yesterdayStr = remember {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, -1)
        sdf.format(c.time)
    }

    val weekStartStr = remember {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, -7)
        sdf.format(c.time)
    }

    val monthStartStr = remember {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.DAY_OF_MONTH, 1)
        sdf.format(c.time)
    }

    val sdfDisplay = remember { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()) }
    val todayDisplay = remember { sdfDisplay.format(todayCal.time) }
    val yesterdayDisplay = remember {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, -1)
        sdfDisplay.format(c.time)
    }
    val weekStartDisplay = remember {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, -7)
        sdfDisplay.format(c.time)
    }
    val monthStartDisplay = remember {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.DAY_OF_MONTH, 1)
        sdfDisplay.format(c.time)
    }

    // Filter items dynamically based on selected period
    val periodDeliveries = remember(deliveries, selectedPeriod, customStartDate, customEndDate) {
        when (selectedPeriod) {
            DashboardPeriod.TODAY -> deliveries.filter { it.date == todayStr }
            DashboardPeriod.YESTERDAY -> deliveries.filter { it.date == yesterdayStr }
            DashboardPeriod.WEEKLY -> deliveries.filter { it.date >= weekStartStr }
            DashboardPeriod.MONTHLY -> deliveries.filter { it.date >= monthStartStr }
            DashboardPeriod.CUSTOM -> {
                val s = minOf(customStartDate, customEndDate)
                val e = maxOf(customStartDate, customEndDate)
                deliveries.filter { it.date in s..e }
            }
        }
    }

    val periodExpenses = remember(expenses, selectedPeriod, customStartDate, customEndDate) {
        when (selectedPeriod) {
            DashboardPeriod.TODAY -> expenses.filter { it.date == todayStr }
            DashboardPeriod.YESTERDAY -> expenses.filter { it.date == yesterdayStr }
            DashboardPeriod.WEEKLY -> expenses.filter { it.date >= weekStartStr }
            DashboardPeriod.MONTHLY -> expenses.filter { it.date >= monthStartStr }
            DashboardPeriod.CUSTOM -> {
                val s = minOf(customStartDate, customEndDate)
                val e = maxOf(customStartDate, customEndDate)
                expenses.filter { it.date in s..e }
            }
        }
    }

    val periodPayments = remember(payments, selectedPeriod, customStartDate, customEndDate) {
        when (selectedPeriod) {
            DashboardPeriod.TODAY -> payments.filter { it.date == todayStr }
            DashboardPeriod.YESTERDAY -> payments.filter { it.date == yesterdayStr }
            DashboardPeriod.WEEKLY -> payments.filter { it.date >= weekStartStr }
            DashboardPeriod.MONTHLY -> payments.filter { it.date >= monthStartStr }
            DashboardPeriod.CUSTOM -> {
                val s = minOf(customStartDate, customEndDate)
                val e = maxOf(customStartDate, customEndDate)
                payments.filter { it.date in s..e }
            }
        }
    }

    val periodSales = remember(periodDeliveries) { periodDeliveries.sumOf { it.totalAmount } }
    val periodCashCollected = remember(periodPayments, periodDeliveries) {
        val directPaymentCash = periodPayments.filter { it.paymentMethod.isCash }.sumOf { it.amount }
        val nonDuplicatedDeliveryCash = periodDeliveries
            .filter { del ->
                del.paymentMethod == "Cash" && del.paymentCollected > 0 &&
                        periodPayments.none { p ->
                            p.notes.contains(del.deliveryNumber) ||
                                    (del.invoiceId.isNotEmpty() && p.allocations.any { it.invoiceId == del.invoiceId })
                        }
            }
            .sumOf { it.paymentCollected }
        directPaymentCash + nonDuplicatedDeliveryCash
    }
    val periodExpenseTotal = remember(periodExpenses) { periodExpenses.sumOf { it.amount } }
    val periodNetProfit = periodSales - periodExpenseTotal
    val periodDeliveryCount = periodDeliveries.size

    val count19L = remember(periodDeliveries) {
        periodDeliveries.flatMap { it.items }
            .filter { it.bottleSizeId.contains("19", ignoreCase = true) || it.bottleName.contains("19", ignoreCase = true) }
            .sumOf { it.quantity }
    }
    val countOther = remember(periodDeliveries) {
        periodDeliveries.flatMap { it.items }
            .filter { !it.bottleSizeId.contains("19", ignoreCase = true) && !it.bottleName.contains("19", ignoreCase = true) }
            .sumOf { it.quantity }
    }
    val emptyCollected = remember(periodDeliveries) {
        periodDeliveries.sumOf { it.totalEmptyCollected }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Plant Status Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.water_hero_banner_1791537621996),
                        contentDescription = "RO Plant Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )

                    // Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        WaterNavyPrimary.copy(alpha = 0.82f),
                                        WaterNavyDark.copy(alpha = 0.94f)
                                    )
                                )
                            )
                    )

                    // Content over banner
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.aab_e_baraan_logo),
                                        contentDescription = "Aab e Baraan Logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Aab e Baraan RO Plant",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val pendingSyncCount = remember(syncQueueItems) {
                                syncQueueItems.count { it.status == SyncQueueStatus.PENDING || it.status == SyncQueueStatus.SYNCING }
                            }
                            val failedSyncCount = remember(syncQueueItems) {
                                syncQueueItems.count { it.status == SyncQueueStatus.FAILED }
                            }
                            val (syncLabel, syncColor) = when {
                                !isDeviceOnline -> "OFFLINE" to ErrorRed
                                failedSyncCount > 0 -> "SYNC ERROR" to ErrorRed
                                isSyncFlushing || pendingSyncCount > 0 ->
                                    (if (pendingSyncCount > 0) "SYNCING ($pendingSyncCount)" else "SYNCING") to WarningAmber
                                isCloudConnected -> "LIVE SYNC" to SuccessGreen
                                else -> "LOCAL ONLY" to WarningAmber
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = syncColor.copy(alpha = 0.22f),
                                modifier = Modifier.testTag("dashboard_live_sync_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(syncColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = syncLabel,
                                        color = syncColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Logged In As",
                                    color = WaterSkyLight.copy(alpha = 0.75f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = user.name,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Role",
                                    color = WaterSkyLight.copy(alpha = 0.75f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = user.role.displayName,
                                    color = WaterMint,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Admin Pending Acknowledgments Alert Banner (Payments, Expenses, Password Resets)
        if (
            viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.APPROVE) ||
            viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE) ||
            viewModel.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)
        ) {
            val pendingPayments = payments.count { !it.isAcknowledged }
            val pendingExpenses = expenses.count { !it.isAcknowledged }
            val pendingPasswordResets = passwordResetRequests.count { !it.isResolved }
            val totalPending = pendingPayments + pendingExpenses + pendingPasswordResets

            if (totalPending > 0) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningOrangeLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.PendingActions,
                                        contentDescription = null,
                                        tint = WarningOrange,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Verification Required (تصدیق طلب کارروائی)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningOrange
                                        )
                                        Text(
                                            text = buildString {
                                                val parts = mutableListOf<String>()
                                                if (pendingPayments > 0) parts.add("$pendingPayments Payment(s)")
                                                if (pendingExpenses > 0) parts.add("$pendingExpenses Expense(s)")
                                                if (pendingPasswordResets > 0) parts.add("$pendingPasswordResets Password Reset(s)")
                                                append(parts.joinToString(" • "))
                                                append(" awaiting Admin approval")
                                            },
                                            fontSize = 10.sp,
                                            color = TextSecondaryDark
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (pendingPayments > 0) {
                                    Button(
                                        onClick = onNavigateToReceivePayment,
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("Payments ($pendingPayments)", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }

                                if (pendingExpenses > 0) {
                                    Button(
                                        onClick = onNavigateToExpenses,
                                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("Expenses ($pendingExpenses)", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }

                                if (pendingPasswordResets > 0) {
                                    Button(
                                        onClick = { showPasswordResetRequestsDialog = true },
                                        colors = waterPrimaryButtonColors(),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("Password ($pendingPasswordResets)", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Primary Financial Cards with Corner Period Filter Button (Today, Yesterday, Weekly, Monthly)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${selectedPeriod.label}'s Business Summary",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyPrimary
                    )
                    Text(
                        text = "${selectedPeriod.urduLabel} کا ریکارڈ اور کارکردگی",
                        fontSize = 10.sp,
                        color = TextSecondaryDark
                    )
                }

                // Corner Period Selector Button + Date Range below
                Column(horizontalAlignment = Alignment.End) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = WaterNavyPrimary,
                            modifier = Modifier
                                .clickable { showPeriodDropdown = true }
                                .testTag("dashboard_period_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = WaterSkyLight,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${selectedPeriod.label} (${selectedPeriod.urduLabel})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showPeriodDropdown,
                            onDismissRequest = { showPeriodDropdown = false }
                        ) {
                            DashboardPeriod.values().forEach { period ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = period.label,
                                                fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selectedPeriod == period) WaterNavyPrimary else TextPrimaryDark
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Text(
                                                text = period.urduLabel,
                                                fontSize = 10.5.sp,
                                                color = if (selectedPeriod == period) WaterAqua else TextMuted
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        if (selectedPeriod == period) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = WaterNavyPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedPeriod = period
                                        showPeriodDropdown = false
                                        if (period == DashboardPeriod.CUSTOM) {
                                            showCustomDateDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Date range subtitle directly under button: kb sy kb tk ka count hy
                    val dateRangeSubtitle = when (selectedPeriod) {
                        DashboardPeriod.WEEKLY -> "$weekStartDisplay — $todayDisplay"
                        DashboardPeriod.MONTHLY -> "$monthStartDisplay — $todayDisplay"
                        DashboardPeriod.YESTERDAY -> yesterdayDisplay
                        DashboardPeriod.TODAY -> todayDisplay
                        DashboardPeriod.CUSTOM -> {
                            if (customStartDate == customEndDate) customStartDate else "$customStartDate — $customEndDate"
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = selectedPeriod == DashboardPeriod.CUSTOM) {
                                showCustomDateDialog = true
                            }
                            .padding(top = 2.dp, end = 2.dp)
                    ) {
                        if (selectedPeriod == DashboardPeriod.CUSTOM) {
                            Icon(
                                Icons.Default.EditCalendar,
                                contentDescription = "Edit Date",
                                tint = WaterNavyPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = dateRangeSubtitle,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedPeriod == DashboardPeriod.CUSTOM) WaterNavyPrimary else if (selectedPeriod == DashboardPeriod.WEEKLY || selectedPeriod == DashboardPeriod.MONTHLY) WaterBlueMedium else TextSecondaryDark
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    title = "Total Sales",
                    urduTitle = "کل سیلز / بل",
                    value = "Rs. ${periodSales.toInt()}",
                    icon = Icons.Default.TrendingUp,
                    iconTint = WaterCyanBright,
                    badgeText = "$periodDeliveryCount Orders",
                    modifier = Modifier.weight(1f),
                    onClick = null
                )

                MetricStatCard(
                    title = "Cash Collected",
                    urduTitle = "وصول شدہ کیش",
                    value = "Rs. ${periodCashCollected.toInt()}",
                    icon = Icons.Default.Payments,
                    iconTint = SuccessGreen,
                    badgeText = "Naqd",
                    modifier = Modifier.weight(1f),
                    onClick = null
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    title = "Total Outstanding",
                    urduTitle = "مارکیٹ ادھار",
                    value = "Rs. ${stats.totalReceivables.toInt()}",
                    icon = Icons.Default.AccountBalanceWallet,
                    iconTint = WarningAmber,
                    badgeText = "Udhaar",
                    modifier = Modifier.weight(1f),
                    onClick = null
                )

                MetricStatCard(
                    title = "Expenses",
                    urduTitle = "اخراجات",
                    value = "Rs. ${periodExpenseTotal.toInt()}",
                    icon = Icons.Default.ReceiptLong,
                    iconTint = ErrorRed,
                    badgeText = "Net: Rs. ${periodNetProfit.toInt()}",
                    modifier = Modifier.weight(1f),
                    onClick = null
                )
            }
        }

        // Bottle Delivery Total by Size for Selected Period (e.g. 19L: 50 bottles, 6L: 50 bottles, 500ml: 10 packets)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Delivered Bottles by Size (${selectedPeriod.label})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyDark
                            )
                            Text(
                                text = "سائز کے حساب سے کل ترسیل",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }

                        val totalDeliveredPeriod = remember(periodDeliveries) {
                            periodDeliveries.sumOf { it.totalFullDelivered }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WaterSkyLight
                        ) {
                            Text(
                                text = "$totalDeliveredPeriod Total Bottles",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val bottleBreakdown = remember(periodDeliveries) {
                        val map = linkedMapOf<String, Int>()
                        periodDeliveries.forEach { del ->
                            del.items.forEach { item ->
                                val name = item.bottleName.ifEmpty { "19L Refill" }
                                map[name] = (map[name] ?: 0) + item.quantity
                            }
                        }
                        map
                    }

                    if (bottleBreakdown.isEmpty()) {
                        Text(
                            text = "No bottles delivered in this period (اس دورانیہ میں ترسیل موجود نہیں)",
                            fontSize = 10.5.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            bottleBreakdown.forEach { (sizeName, qty) ->
                                val unitLabel = if (sizeName.contains("500", ignoreCase = true)) "Packets (پیکٹ)" else "Bottles (بوتلیں)"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = WaterSkyLight.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(WaterNavyPrimary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.WaterDrop,
                                                    contentDescription = null,
                                                    tint = WaterNavyPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = sizeName,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WaterNavyDark
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = WaterNavyPrimary
                                        ) {
                                            Text(
                                                text = "$qty $unitLabel",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Quick Action Grid (Filtered by RBAC permissions)
        val canQuickDelivery = viewModel.hasPermission(AppModule.DELIVERIES, PermissionAction.ADD)
        val canQuickPayment = viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.ADD)
        val canQuickExpense = viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.ADD)
        val canQuickClosing = viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.MANAGE) || canQuickPayment
        if (canQuickDelivery || canQuickPayment || canQuickExpense || canQuickClosing) {
            item {
                SectionHeader(
                    title = "Quick Actions (فوری ایکشن)",
                    urduTitle = "کم وقت میں اندراج کریں"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (canQuickDelivery) {
                        QuickActionButton(
                            icon = Icons.Default.LocalShipping,
                            title = "New Delivery",
                            urdu = "نئی ڈیلیوری",
                            color = WaterNavyPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToNewDelivery
                        )
                    }
                    if (canQuickPayment) {
                        QuickActionButton(
                            icon = Icons.Default.PriceCheck,
                            title = "Receive Cash",
                            urdu = "پیمنٹ وصولی",
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToReceivePayment
                        )
                    }
                    if (canQuickExpense) {
                        QuickActionButton(
                            icon = Icons.Default.ReceiptLong,
                            title = "Add Expense",
                            urdu = "خرچ درج کریں",
                            color = ErrorRed,
                            modifier = Modifier.weight(1f),
                            onClick = { showAddExpenseDialog = true }
                        )
                    }
                    if (canQuickClosing) {
                        QuickActionButton(
                            icon = Icons.Default.PointOfSale,
                            title = "Cash Closing",
                            urdu = "شام بندش",
                            color = WaterAqua,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToCashClosing
                        )
                    }
                }
            }
        }


        // 4. Period Deliveries List (Auto Today or Selected Period)
        item {
            SectionHeader(
                title = if (selectedPeriod == DashboardPeriod.TODAY) "Today's Deliveries (آج کی ترسیلات)" else "${selectedPeriod.label}'s Deliveries (${periodDeliveries.size})",
                urduTitle = "${selectedPeriod.urduLabel} کے آرڈرز اور ترسیل"
            )
        }

        if (periodDeliveries.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No deliveries recorded for ${selectedPeriod.label.lowercase()}. Tap 'New Delivery' to record.",
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        } else {
            items(periodDeliveries, key = { it.id }) { delivery ->
                val isDeliveryNew = viewModel.isRecordNewForCurrentUser(
                    type = "delivery",
                    recordId = delivery.id,
                    createdByUid = delivery.createdByUid,
                    createdByName = delivery.createdBy,
                    createdAt = delivery.createdAt,
                    seenKeys = seenRecordKeys,
                    user = user
                )
                DeliveryItemCard(
                    delivery = delivery,
                    isNew = isDeliveryNew,
                    onClick = { viewModel.markRecordSeen("delivery", delivery.id) }
                )
            }
        }
    }

    if (showCustomDateDialog) {
        DashboardDateRangeDialog(
            initialStartDate = customStartDate,
            initialEndDate = customEndDate,
            onDismiss = { showCustomDateDialog = false },
            onConfirm = { s, e ->
                customStartDate = s
                customEndDate = e
                selectedPeriod = DashboardPeriod.CUSTOM
                showCustomDateDialog = false
            }
        )
    }

    // Password Reset Requests Dialog for Admin
    if (showPasswordResetRequestsDialog) {
        val unresolvedRequests = passwordResetRequests.filter { !it.isResolved }
        var selectedReqForReset by remember { mutableStateOf<com.example.model.PasswordResetRequest?>(null) }
        var newPasswordInput by remember { mutableStateOf("") }
        var resetSuccessMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPasswordResetRequestsDialog = false },
            containerColor = Color.White,
            titleContentColor = WaterNavyDark,
            textContentColor = TextPrimaryDark,
            icon = {
                Icon(
                    Icons.Default.LockReset,
                    contentDescription = null,
                    tint = WaterNavyPrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Password Reset Requests (پاس ورڈ درخواستیں)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = WaterNavyDark,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (unresolvedRequests.isEmpty()) {
                        Text(
                            text = "کوئی زیر التواء پاس ورڈ درخواست نہیں ہے۔ تمام حل شدہ ہیں۔",
                            color = SuccessGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Text(
                            text = "سٹاف نے لاگ ان پاس ورڈ ری سیٹ کرنے کی درخواست کی ہے:",
                            fontSize = 10.5.sp,
                            color = TextSecondaryDark
                        )

                        unresolvedRequests.forEach { req ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = WaterSkyLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${req.fullName} (@${req.username})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = WaterNavyPrimary
                                            )
                                            Text(
                                                text = "${req.role.displayName} • ${req.requestTime}",
                                                fontSize = 10.sp,
                                                color = TextSecondaryDark
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = WarningOrange.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "Pending",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WarningOrange,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (selectedReqForReset?.id == req.id) {
                                        OutlinedTextField(
                                            value = newPasswordInput,
                                            onValueChange = { newPasswordInput = it },
                                            label = { Text("Set New Password (نیا پاس ورڈ)") },
                                            placeholder = { Text("e.g. 123456") },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = TextPrimaryDark,
                                                unfocusedTextColor = TextPrimaryDark,
                                                focusedBorderColor = WaterNavyPrimary,
                                                unfocusedBorderColor = BorderLight
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = { selectedReqForReset = null }) {
                                                Text("Cancel", fontSize = 10.5.sp)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = {
                                                    val pass = if (newPasswordInput.isBlank()) "123456" else newPasswordInput.trim()
                                                    viewModel.resolvePasswordReset(req.id, pass)
                                                    resetSuccessMsg = "پاس ورڈ '$pass' سیٹ کر دیا گیا!"
                                                    selectedReqForReset = null
                                                    newPasswordInput = ""
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Confirm & Set (منظور)", fontSize = 10.5.sp, color = Color.White)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                selectedReqForReset = req
                                                newPasswordInput = "123456"
                                            },
                                            colors = waterPrimaryButtonColors(),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Reset & Approve Password", fontSize = 10.5.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (resetSuccessMsg != null) {
                        Text(
                            text = resetSuccessMsg!!,
                            color = SuccessGreen,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPasswordResetRequestsDialog = false },
                    colors = waterPrimaryButtonColors()
                ) {
                    Text("Close (بند کریں)")
                }
            }
        )
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            categories = expenseCategories,
            isAdmin = viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.APPROVE),
            factoryMaterials = factoryMaterials,
            bottleSizes = bottleSizes,
            plantWorkers = plantWorkers,
            onGetWorkerSalarySummary = { workerId, month ->
                viewModel.getWorkerMonthlySalarySummary(workerId, month)
            },
            onDismiss = { showAddExpenseDialog = false },
            onSave = { catId, catName, desc, amt, method, payee, notes, type, date ->
                viewModel.addExpense(catId, catName, desc, amt, method, payee, notes, type, date)
                showAddExpenseDialog = false
            },
            onSaveWithSource = { catId, catName, desc, amt, method, payee, notes, type, date, source ->
                viewModel.addExpense(catId, catName, desc, amt, method, payee, notes, type, date, source)
                showAddExpenseDialog = false
            },
            onSaveWithSourceAndStaff = { catId, catName, desc, amt, method, payee, notes, type, date, source, invoiceNo, staffId, staffName ->
                viewModel.addExpense(
                    categoryId = catId,
                    categoryName = catName,
                    description = desc,
                    amount = amt,
                    method = method,
                    payee = payee,
                    notes = notes,
                    type = type,
                    expenseDate = date,
                    paymentSource = source,
                    invoiceNumber = invoiceNo,
                    staffMemberId = staffId,
                    staffMemberName = staffName
                )
                showAddExpenseDialog = false
            },
            onSaveWithMaterialRefill = { matId, qty, unitCost, catId, catName, expType, desc, supplier, invoiceNo, notes, date, source, method ->
                viewModel.recordExpenseWithMaterialRefill(
                    materialId = matId,
                    quantity = qty,
                    unitCost = unitCost,
                    categoryId = catId,
                    categoryName = catName,
                    expenseType = expType,
                    description = desc,
                    supplierName = supplier,
                    invoiceNo = invoiceNo,
                    notes = notes,
                    expenseDate = date,
                    paymentSource = source,
                    paymentMethod = method
                )
                showAddExpenseDialog = false
            },
            onSaveWithBottlePurchase = { sizeId, qty, unitCost, catId, catName, expType, desc, supplier, invoiceNo, notes, date, source, method ->
                viewModel.recordExpenseWithBottlePurchase(
                    bottleSizeId = sizeId,
                    quantity = qty,
                    unitCost = unitCost,
                    categoryId = catId,
                    categoryName = catName,
                    expenseType = expType,
                    description = desc,
                    supplierName = supplier,
                    invoiceNo = invoiceNo,
                    notes = notes,
                    expenseDate = date,
                    paymentSource = source,
                    paymentMethod = method
                )
                showAddExpenseDialog = false
            },
            onAddCategory = { name, urdu, type ->
                viewModel.addExpenseCategory(name, urdu, type)
            }
        )
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    title: String,
    urdu: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyPrimary,
                maxLines = 1
            )
            Text(
                text = urdu,
                fontSize = 9.5.sp,
                color = TextSecondaryDark,
                maxLines = 1
            )
        }
    }
}

@Composable
fun BottleStatBadge(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = WaterSkyLight.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = TextSecondaryDark,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DeliveryItemCard(
    delivery: Delivery,
    isNew: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(WaterSkyLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = WaterCyanBright,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = delivery.customerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = WaterNavyPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isNew) {
                            NewItemBadge(onClick = onClick)
                        }
                    }
                    Text(
                        text = "${delivery.customerArea} • ${delivery.time}",
                        fontSize = 10.sp,
                        color = TextSecondaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Delivered: ${delivery.totalFullDelivered} | Empty: ${delivery.totalEmptyCollected}",
                        fontSize = 10.sp,
                        color = WaterAqua,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Rs. ${delivery.totalAmount.toInt()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextPrimaryDark
                )

                StatusBadge(
                    text = if (delivery.paymentCollected > 0) "Paid" else "Unpaid",
                    backgroundColor = if (delivery.paymentCollected > 0) SuccessGreenLight else WarningAmberLight,
                    textColor = if (delivery.paymentCollected > 0) SuccessGreen else WarningAmber
                )
            }
        }
    }
}

@Composable
fun DashboardDateRangeDialog(
    initialStartDate: String,
    initialEndDate: String,
    onDismiss: () -> Unit,
    onConfirm: (startDate: String, endDate: String) -> Unit
) {
    var isSingleDateMode by remember { mutableStateOf(initialStartDate == initialEndDate) }
    var startDateText by remember { mutableStateOf(initialStartDate) }
    var endDateText by remember { mutableStateOf(initialEndDate) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val cal = remember { java.util.Calendar.getInstance() }
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }

    fun showPicker(isStart: Boolean) {
        val curDateStr = if (isStart) startDateText else endDateText
        try {
            val d = sdf.parse(curDateStr)
            if (d != null) cal.time = d
        } catch (_: Exception) {}

        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = java.util.Calendar.getInstance()
                chosen.set(year, month, dayOfMonth)
                val formatted = sdf.format(chosen.time)
                if (isStart) {
                    startDateText = formatted
                    if (isSingleDateMode) endDateText = formatted
                } else {
                    endDateText = formatted
                }
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = WaterNavyPrimary,
        textContentColor = TextPrimaryDark,
        title = {
            Text(
                text = "تاریخ منتخب کریں (Select Date / Range)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = WaterNavyPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Mode selector chips: Single Day vs Date Range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isSingleDateMode,
                        onClick = {
                            isSingleDateMode = true
                            endDateText = startDateText
                        },
                        colors = waterFilterChipColors(),
                        label = { Text("ایک دن (Single Date)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isSingleDateMode,
                        onClick = { isSingleDateMode = false },
                        colors = waterFilterChipColors(),
                        label = { Text("تاریخ کی حد (Date Range)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Start Date / Single Date field with calendar button
                OutlinedTextField(
                    value = startDateText,
                    onValueChange = {
                        startDateText = it
                        if (isSingleDateMode) endDateText = it
                    },
                    label = { Text(if (isSingleDateMode) "منتخب تاریخ (Date: YYYY-MM-DD)" else "شروع کی تاریخ (Start Date)") },
                    trailingIcon = {
                        IconButton(onClick = { showPicker(true) }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = WaterNavyPrimary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (!isSingleDateMode) {
                    OutlinedTextField(
                        value = endDateText,
                        onValueChange = { endDateText = it },
                        label = { Text("اختتامی تاریخ (End Date: YYYY-MM-DD)") },
                        trailingIcon = {
                            IconButton(onClick = { showPicker(false) }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick End Date", tint = WaterNavyPrimary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val todayStr = remember { sdf.format(java.util.Date()) }
                    val yesterdayStr = remember {
                        val c = java.util.Calendar.getInstance()
                        c.add(java.util.Calendar.DAY_OF_YEAR, -1)
                        sdf.format(c.time)
                    }
                    SuggestionChip(
                        onClick = {
                            startDateText = todayStr
                            endDateText = todayStr
                        },
                        label = { Text("آج", fontSize = 9.5.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            startDateText = yesterdayStr
                            endDateText = yesterdayStr
                        },
                        label = { Text("گزشتہ کل", fontSize = 9.5.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            val c = java.util.Calendar.getInstance()
                            c.add(java.util.Calendar.DAY_OF_YEAR, -7)
                            startDateText = sdf.format(c.time)
                            endDateText = todayStr
                            isSingleDateMode = false
                        },
                        label = { Text("پچھلے 7 دن", fontSize = 9.5.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = if (startDateText.isNotBlank()) startDateText.trim() else initialStartDate
                    val e = if (isSingleDateMode) s else if (endDateText.isNotBlank()) endDateText.trim() else s
                    onConfirm(s, e)
                },
                colors = waterPrimaryButtonColors()
            ) {
                Text("لاگو کریں (Apply Filter)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("منسوخ", color = TextSecondaryDark) }
        }
    )
}
