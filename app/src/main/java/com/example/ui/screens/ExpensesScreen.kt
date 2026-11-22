package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.ui.components.CompactDropdownField
import com.example.ui.components.CompactFormTextField
import com.example.ui.components.EditMetadataBadge
import com.example.ui.components.EditableDateField
import com.example.ui.components.FromToDateRangeDialog
import com.example.ui.components.MobileFormDialog
import com.example.ui.components.NewItemBadge
import com.example.ui.components.PdfPreviewDialog
import com.example.ui.components.PdfPreviewRequest
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import com.example.viewmodel.WaterPlantViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExpensePeriod(val label: String, val urduLabel: String) {
    TODAY("Today", "آج"),
    YESTERDAY("Yesterday", "گزشتہ کل"),
    LAST_7_DAYS("Last 7 Days", "آخری 7 دن"),
    THIS_MONTH("This Month", "اس ماہ"),
    CUSTOM("Custom Date", "مخصوص تاریخ"),
    ALL("All Expense", "تمام اخراجات")
}

enum class ExpenseStatusFilter(val label: String, val urduLabel: String) {
    ALL("All Status", "تمام"),
    VERIFIED("Verified", "تصدیق شدہ"),
    PENDING("Pending", "زیرِ تصدیق")
}

private val STANDARD_EXPENSE_CATEGORIES: List<ExpenseCategory> = listOf(
    ExpenseCategory("cat_asset_machinery", "Asset / Machinery", "اثاثہ جات / مشینری", ExpenseType.CAPEX),
    ExpenseCategory("cat_bottles_opex", "Bottle Purchase", "بوتل کی خریداری", ExpenseType.OPEX),
    ExpenseCategory("cat_entertainment", "Entertainment", "مہمان نوازی", ExpenseType.OPEX),
    ExpenseCategory("cat_fuel", "Fuel / Rent", "ایندھن / کرایہ", ExpenseType.OPEX),
    ExpenseCategory("cat_electricity", "Electricity Bill", "بجلی کا بل", ExpenseType.OPEX),
    ExpenseCategory("cat_maint", "Plant Maintenance", "پلانٹ مینٹیننس", ExpenseType.OPEX),
    ExpenseCategory("cat_vehicle_maint", "Vehicle Maintenance", "گاڑی کی مرمت", ExpenseType.OPEX),
    ExpenseCategory("cat_filters", "Filter and Cartridge", "فلٹر اور کارٹریج", ExpenseType.OPEX),
    ExpenseCategory("cat_packing", "Packing Material", "پیکنگ مٹیریل", ExpenseType.OPEX),
    ExpenseCategory("cat_minerals", "Chemical and Mineral", "کیمیکلز اور منرلز", ExpenseType.OPEX),
    ExpenseCategory("cat_misc", "General", "عمومی اخراجات", ExpenseType.OPEX),
    ExpenseCategory("cat_salary", "Staff Salary", "عملے کی تنخواہ", ExpenseType.OPEX),
    ExpenseCategory("cat_raw_material", "Raw Material", "خام مال", ExpenseType.OPEX),
    ExpenseCategory("cat_others", "Others", "دیگر اخراجات", ExpenseType.OPEX)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: WaterPlantViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val seenRecordKeys by viewModel.seenRecordKeys.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val categories by viewModel.expenseCategories.collectAsState()
    val factoryMaterials by viewModel.factoryMaterials.collectAsState()
    val bottleSizes by viewModel.bottleSizes.collectAsState()
    val plantWorkers by viewModel.plantWorkers.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var selectedExpenseForDetail by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var previewPdfRequest by remember { mutableStateOf<PdfPreviewRequest?>(null) }

    // Period Filter State — defaults to Today automatically
    var selectedPeriod by remember { mutableStateOf(ExpensePeriod.TODAY) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val sdfDisplay = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val todayCal = remember { Calendar.getInstance() }
    val todayStr = remember { sdf.format(todayCal.time) }
    val todayDisplay = remember { sdfDisplay.format(todayCal.time) }
    var customStartDate by remember { mutableStateOf(todayStr) }
    var customEndDate by remember { mutableStateOf(todayStr) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val yesterdayStr = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -1)
        sdf.format(c.time)
    }
    val yesterdayDisplay = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -1)
        sdfDisplay.format(c.time)
    }

    val weekStartStr = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -6)
        sdf.format(c.time)
    }
    val weekStartDisplay = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -6)
        sdfDisplay.format(c.time)
    }

    val monthStartStr = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        sdf.format(c.time)
    }
    val monthStartDisplay = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        sdfDisplay.format(c.time)
    }

    // Type Filter: null (All), OPEX, or CAPEX
    var selectedTypeFilter by remember { mutableStateOf<ExpenseType?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedStatusFilter by remember { mutableStateOf(ExpenseStatusFilter.ALL) }
    var selectedPaymentSourceFilter by remember { mutableStateOf<PaymentSource?>(null) }

    val periodExpenses = remember(expenses, selectedPeriod, customStartDate, customEndDate) {
        expenses.filter { exp ->
            when (selectedPeriod) {
                ExpensePeriod.ALL -> true
                ExpensePeriod.TODAY -> exp.date == todayStr
                ExpensePeriod.YESTERDAY -> exp.date == yesterdayStr
                ExpensePeriod.LAST_7_DAYS -> exp.date >= weekStartStr
                ExpensePeriod.THIS_MONTH -> exp.date >= monthStartStr
                ExpensePeriod.CUSTOM -> exp.date in customStartDate..customEndDate
            }
        }
    }

    val filteredExpenses = periodExpenses.filter { exp ->
        val matchesType = selectedTypeFilter == null || exp.expenseType == selectedTypeFilter
        val matchesCategory = selectedCategoryFilter == null || exp.categoryId == selectedCategoryFilter
        val matchesSource = selectedPaymentSourceFilter == null || exp.paymentSource == selectedPaymentSourceFilter
        val matchesStatus = when (selectedStatusFilter) {
            ExpenseStatusFilter.ALL -> true
            ExpenseStatusFilter.VERIFIED -> exp.isAcknowledged
            ExpenseStatusFilter.PENDING -> !exp.isAcknowledged
        }
        matchesType && matchesCategory && matchesSource && matchesStatus
    }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt }.thenByDescending { it.id })

    val totalExpenses = filteredExpenses.sumOf { it.amount }
    val totalOpex = filteredExpenses.filter { it.expenseType == ExpenseType.OPEX }.sumOf { it.amount }
    val totalCapex = filteredExpenses.filter { it.expenseType == ExpenseType.CAPEX }.sumOf { it.amount }
    val totalFromSale = periodExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_SALE }.sumOf { it.amount }
    val totalFromInvestment = periodExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_INVESTMENT }.sumOf { it.amount }

    val canAddExpense = viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.ADD)
    val canEditExpense = viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.EDIT)
    val canDeleteExpense = viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.DELETE)
    val canApproveExpense = viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.APPROVE)
    val isAdmin = canApproveExpense || canDeleteExpense
    val isViewOnly = !canAddExpense
    val canAddNewItems = viewModel.hasPermission(AppModule.NEW_ITEMS, PermissionAction.ADD)

    fun shareExpensePdf(exp: Expense) {
        try {
            val file = PdfGenerator.generateExpensePdf(context, exp)
            previewPdfRequest = PdfPreviewRequest(
                file = file,
                title = "Expense ${exp.voucherNumber}",
                chooserTitle = "Share Expense Voucher ${exp.voucherNumber}",
                whatsAppCaption = "Expense Voucher ${exp.voucherNumber} (${exp.categoryName}) - Rs. ${exp.amount.toInt()}"
            )
        } catch (_: Exception) {
            val text = PdfGenerator.buildExpenseShareSummary(exp)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Expense ${exp.voucherNumber}"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface)
    ) {
        // Compact Header + Period Tabs + Compact Number Board
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                val dateSubtitle = when (selectedPeriod) {
                    ExpensePeriod.ALL -> "All History"
                    ExpensePeriod.TODAY -> todayDisplay
                    ExpensePeriod.YESTERDAY -> yesterdayDisplay
                    ExpensePeriod.LAST_7_DAYS -> "$weekStartDisplay — $todayDisplay"
                    ExpensePeriod.THIS_MONTH -> "$monthStartDisplay — $todayDisplay"
                    ExpensePeriod.CUSTOM -> if (customStartDate == customEndDate) customStartDate else "$customStartDate — $customEndDate"
                }

                // Title & Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            text = "Plant Expenses",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyDark,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateSubtitle,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WaterAqua,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isViewOnly) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (canAddNewItems) {
                                OutlinedButton(
                                    onClick = { showNewCategoryDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Category, contentDescription = "Manage Categories", modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Categories",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("add_expense_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "+ Add Expense",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Single-Line Scrollable Period Tabs including Custom Date
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag("expense_period_tabs"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabConfigs = listOf(
                        ExpensePeriod.TODAY to "Today",
                        ExpensePeriod.YESTERDAY to "Yesterday",
                        ExpensePeriod.LAST_7_DAYS to "Last 7 Days",
                        ExpensePeriod.THIS_MONTH to "This Month",
                        ExpensePeriod.CUSTOM to if (selectedPeriod == ExpensePeriod.CUSTOM) {
                            if (customStartDate == customEndDate) customStartDate else "$customStartDate..$customEndDate"
                        } else "Custom Date",
                        ExpensePeriod.ALL to "All Expense"
                    )

                    tabConfigs.forEach { (period, label) ->
                        val isSelected = selectedPeriod == period
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isSelected) WaterSkyButton else WaterSkyLight.copy(alpha = 0.75f),
                            modifier = Modifier
                                .height(30.dp)
                                .clickable {
                                    if (period == ExpensePeriod.CUSTOM) {
                                        showCustomDateDialog = true
                                    } else {
                                        selectedPeriod = period
                                    }
                                }
                                .testTag("expense_tab_${period.name.lowercase()}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) WaterSkyButtonContent else WaterNavyDark,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))

                // Compact Number Board: Total, OPEX, CAPEX
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WaterNavyPrimary.copy(alpha = 0.08f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Rs. ${totalExpenses.toInt()}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "Total Expense",
                                fontSize = 8.5.sp,
                                color = TextSecondaryDark,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WaterAqua.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Rs. ${totalOpex.toInt()}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WaterNavyDark,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "OPEX",
                                fontSize = 8.5.sp,
                                color = WaterNavyDark,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3E5F5),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Rs. ${totalCapex.toInt()}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF6A1B9A),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "CAPEX",
                                fontSize = 8.5.sp,
                                color = Color(0xFF6A1B9A),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Payment Source Breakdown & Quick Filter Strip (Both From Sale & Investment Included)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_source_summary_row"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sourceChips = listOf(
                        Triple(null, "All Sources (${periodExpenses.size})", periodExpenses.sumOf { it.amount }),
                        Triple(PaymentSource.PAY_FROM_SALE, "From Sale", totalFromSale),
                        Triple(PaymentSource.PAY_FROM_INVESTMENT, "Investment", totalFromInvestment)
                    )
                    sourceChips.forEach { (src, title, amt) ->
                        val isSel = selectedPaymentSourceFilter == src
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isSel) WaterSkyButton else WaterSkyLight.copy(alpha = 0.55f),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clickable { selectedPaymentSourceFilter = src }
                                .testTag("expense_source_filter_${src?.name?.lowercase() ?: "all"}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (src == null) title else "$title: Rs.${amt.toInt()}",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) WaterSkyButtonContent else WaterNavyDark,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Compact Filter Button & Count Row
                var showFilterDialog by remember { mutableStateOf(false) }
                val activeFiltersCount = (if (selectedTypeFilter != null) 1 else 0) +
                    (if (selectedCategoryFilter != null) 1 else 0) +
                    (if (selectedStatusFilter != ExpenseStatusFilter.ALL) 1 else 0) +
                    (if (selectedPaymentSourceFilter != null) 1 else 0)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (activeFiltersCount > 0) WaterSkyButton else WaterSkyLight,
                        modifier = Modifier
                            .height(28.dp)
                            .clickable { showFilterDialog = true }
                            .testTag("expense_filter_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter Expenses",
                                tint = if (activeFiltersCount > 0) WaterSkyButtonContent else WaterNavyDark,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (activeFiltersCount > 0) "Filter ($activeFiltersCount Active)" else "Filter",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeFiltersCount > 0) WaterSkyButtonContent else WaterNavyDark,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activeFiltersCount > 0) {
                            Text(
                                text = "Reset",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clickable {
                                    selectedTypeFilter = null
                                    selectedCategoryFilter = null
                                    selectedStatusFilter = ExpenseStatusFilter.ALL
                                    selectedPaymentSourceFilter = null
                                }
                            )
                        }
                        Text(
                            text = "${filteredExpenses.size} Vouchers",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryDark,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Filter Dialog
                if (showFilterDialog) {
                    var tempType by remember(showFilterDialog) { mutableStateOf(selectedTypeFilter) }
                    var tempCategory by remember(showFilterDialog) { mutableStateOf(selectedCategoryFilter) }
                    var tempStatus by remember(showFilterDialog) { mutableStateOf(selectedStatusFilter) }
                    var tempSource by remember(showFilterDialog) { mutableStateOf(selectedPaymentSourceFilter) }

                    val filterCategories = remember(categories, tempType) {
                        val combined = (STANDARD_EXPENSE_CATEGORIES + categories)
                            .distinctBy { it.name.trim().lowercase() }
                        combined.filter { cat -> tempType == null || cat.type == tempType }
                    }

                    AlertDialog(
                        onDismissRequest = { showFilterDialog = false },
                        containerColor = Color.White,
                        titleContentColor = WaterNavyDark,
                        textContentColor = TextPrimaryDark,
                        modifier = Modifier.fillMaxWidth(0.94f),
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filter Expenses",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = WaterNavyDark
                                )
                                TextButton(
                                    onClick = {
                                        tempType = null
                                        tempCategory = null
                                        tempStatus = ExpenseStatusFilter.ALL
                                        tempSource = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Clear All", fontSize = 10.sp, color = ErrorRed, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Expense Type:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            Triple(null, "All (${periodExpenses.size})", periodExpenses.size),
                                            Triple(ExpenseType.OPEX, "OPEX (${periodExpenses.count { it.expenseType == ExpenseType.OPEX }})", 0),
                                            Triple(ExpenseType.CAPEX, "CAPEX (${periodExpenses.count { it.expenseType == ExpenseType.CAPEX }})", 0)
                                        ).forEach { (t, label, _) ->
                                            val isSel = (tempType == t)
                                            Surface(
                                                shape = RoundedCornerShape(7.dp),
                                                color = if (isSel) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.65f),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(30.dp)
                                                    .clickable { tempType = t }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSel) Color.White else WaterNavyDark,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Category:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        item {
                                            val isAllCatSel = (tempCategory == null)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isAllCatSel) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .height(28.dp)
                                                    .clickable { tempCategory = null }
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                    Text(
                                                        text = "All",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isAllCatSel) Color.White else WaterNavyDark,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                        items(filterCategories) { cat ->
                                            val isCatSel = (tempCategory == cat.id)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isCatSel) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .height(28.dp)
                                                    .clickable {
                                                        tempCategory = if (isCatSel) null else cat.id
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                    Text(
                                                        text = cat.name,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isCatSel) Color.White else WaterNavyDark,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Verification Status:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val verifiedCount = periodExpenses.count { it.isAcknowledged }
                                        val unverifiedCount = periodExpenses.count { !it.isAcknowledged }
                                        listOf(
                                            Pair(ExpenseStatusFilter.ALL, "All"),
                                            Pair(ExpenseStatusFilter.VERIFIED, "Verified ($verifiedCount)"),
                                            Pair(ExpenseStatusFilter.PENDING, "Pending ($unverifiedCount)")
                                        ).forEach { (st, label) ->
                                            val isStSel = (tempStatus == st)
                                            val activeCol = when (st) {
                                                ExpenseStatusFilter.ALL -> WaterNavyPrimary
                                                ExpenseStatusFilter.VERIFIED -> SuccessGreen
                                                ExpenseStatusFilter.PENDING -> WarningOrange
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(7.dp),
                                                color = if (isStSel) activeCol else WaterSkyLight.copy(alpha = 0.65f),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(30.dp)
                                                    .clickable { tempStatus = st }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isStSel) Color.White else WaterNavyDark,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Payment Source:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            Pair(null, "All (${periodExpenses.size})"),
                                            Pair(PaymentSource.PAY_FROM_SALE, "From Sale"),
                                            Pair(PaymentSource.PAY_FROM_INVESTMENT, "Investment")
                                        ).forEach { (src, label) ->
                                            val isSrcSel = (tempSource == src)
                                            Surface(
                                                shape = RoundedCornerShape(7.dp),
                                                color = if (isSrcSel) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.65f),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(30.dp)
                                                    .clickable { tempSource = src }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSrcSel) Color.White else WaterNavyDark,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    selectedTypeFilter = tempType
                                    selectedCategoryFilter = tempCategory
                                    selectedStatusFilter = tempStatus
                                    selectedPaymentSourceFilter = tempSource
                                    showFilterDialog = false
                                },
                                colors = waterPrimaryButtonColors()
                            ) {
                                Text("Apply", fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showFilterDialog = false }) {
                                Text("Cancel", fontSize = 10.5.sp, color = TextSecondaryDark)
                            }
                        }
                    )
                }
            }
        }

        // Expense Items List (Compact high-density list-wise view)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (filteredExpenses.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No expenses found for selected period.",
                                    color = TextSecondaryDark,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                items(filteredExpenses, key = { it.id }) { exp ->
                    val isNewExpense = viewModel.isRecordNewForCurrentUser(
                        type = "expense",
                        recordId = exp.id,
                        createdByUid = exp.createdByUid,
                        createdByName = exp.enteredBy,
                        createdAt = exp.createdAt,
                        seenKeys = seenRecordKeys,
                        user = currentUser
                    )
                    ExpenseItemCard(
                        expense = exp,
                        isAdmin = isAdmin,
                        isNew = isNewExpense,
                        onDismissNew = { viewModel.markRecordSeen("expense", exp.id) },
                        onOpenDetail = {
                            viewModel.markRecordSeen("expense", exp.id)
                            selectedExpenseForDetail = exp
                        },
                        onSharePdf = {
                            viewModel.markRecordSeen("expense", exp.id)
                            shareExpensePdf(exp)
                        },
                        onAcknowledge = {
                            viewModel.markRecordSeen("expense", exp.id)
                            viewModel.acknowledgeExpense(exp.id)
                        },
                        onEdit = {
                            viewModel.markRecordSeen("expense", exp.id)
                            expenseToEdit = exp
                        },
                        onDelete = {
                            viewModel.markRecordSeen("expense", exp.id)
                            expenseToDelete = exp
                        }
                    )
                }
            }
        }
    }

    // Expense Detail Dialog (with Granular Edit/Delete/Verify Actions & Share PDF)
    if (selectedExpenseForDetail != null) {
        val currentExp = expenses.find { it.id == selectedExpenseForDetail!!.id } ?: selectedExpenseForDetail!!
        ExpenseDetailDialog(
            expense = currentExp,
            canEdit = canEditExpense,
            canDelete = canDeleteExpense,
            canApprove = canApproveExpense,
            onDismiss = { selectedExpenseForDetail = null },
            onSharePdf = { shareExpensePdf(currentExp) },
            onEdit = {
                selectedExpenseForDetail = null
                expenseToEdit = currentExp
            },
            onDelete = {
                selectedExpenseForDetail = null
                expenseToDelete = currentExp
            },
            onAcknowledge = {
                viewModel.acknowledgeExpense(currentExp.id)
            }
        )
    }

    previewPdfRequest?.let { req ->
        PdfPreviewDialog(
            request = req,
            onDismiss = { previewPdfRequest = null }
        )
    }

    // Add Expense Dialog (Choice -> Inventory Multi-Item OR Non-Inventory)
    if (showAddDialog) {
        AddExpenseDialog(
            categories = categories,
            isAdmin = isAdmin,
            factoryMaterials = factoryMaterials,
            bottleSizes = bottleSizes,
            plantWorkers = plantWorkers,
            onGetWorkerSalarySummary = { workerId, month ->
                viewModel.getWorkerMonthlySalarySummary(workerId, month)
            },
            onDismiss = { showAddDialog = false },
            onSave = { catId, catName, desc, amt, method, payee, notes, type, date ->
                viewModel.addExpense(catId, catName, desc, amt, method, payee, notes, type, date)
                showAddDialog = false
            },
            onSaveWithSource = { catId, catName, desc, amt, method, payee, notes, type, date, source ->
                viewModel.addExpense(catId, catName, desc, amt, method, payee, notes, type, date, source)
                showAddDialog = false
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
                showAddDialog = false
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
                showAddDialog = false
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
                showAddDialog = false
            },
            onSaveMultiItemInventory = { items, expType, supplier, invoiceNo, notes, date, source, method ->
                viewModel.recordMultiItemInventoryExpense(
                    items = items,
                    expenseType = expType,
                    supplierName = supplier,
                    invoiceNo = invoiceNo,
                    notes = notes,
                    expenseDate = date,
                    paymentSource = source,
                    paymentMethod = method
                )
                showAddDialog = false
            },
            onAddCategory = { name, urdu, type ->
                viewModel.addExpenseCategory(name, urdu, type, false)
            },
            onAddCategoryWithType = { name, urdu, type, isInv ->
                viewModel.addExpenseCategory(name, urdu, type, isInv)
            },
            onAddNewMaterial = { mat ->
                viewModel.addFactoryMaterial(mat)
            },
            onAddNewBottleSize = { name, cap, usage, refill, cost, uom, catName ->
                viewModel.addNewBottleSize(
                    name = name,
                    purchaseRate = cost,
                    packQty = 1,
                    uom = uom,
                    openingStock = 0,
                    usageType = usage,
                    defaultRefillPrice = refill,
                    salePrice = refill,
                    capacityLitres = cap,
                    depositAmount = 0.0,
                    categoryName = catName
                )
            }
        )
    }

    if (showNewCategoryDialog) {
        ManageExpenseCategoriesDialog(
            categories = categories,
            onAddCategory = { name, urdu, type, isInv ->
                viewModel.addExpenseCategory(name, urdu, type, isInv)
            },
            onDeleteCategory = { catId -> viewModel.deleteExpenseCategory(catId) },
            onDismiss = { showNewCategoryDialog = false }
        )
    }

    // Admin Confirmation Dialog for Deleting Expense
    if (expenseToDelete != null) {
        val target = expenseToDelete!!
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Expense Voucher?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = ErrorRed
                )
            },
            text = {
                Text(
                    text = "Delete voucher #${target.voucherNumber} (${target.categoryName} - Rs. ${target.amount.toInt()})?",
                    fontSize = 11.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(target.id)
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel", color = TextSecondaryDark, fontSize = 10.5.sp)
                }
            }
        )
    }

    // Full Edit Expense Dialog (Both Inventory & Non-Inventory)
    if (expenseToEdit != null) {
        EditExpenseDialog(
            expense = expenseToEdit!!,
            categories = categories,
            isAdmin = isAdmin,
            factoryMaterials = factoryMaterials,
            bottleSizes = bottleSizes,
            plantWorkers = plantWorkers,
            onGetWorkerSalarySummary = { workerId, month ->
                viewModel.getWorkerMonthlySalarySummary(workerId, month)
            },
            onAddCategoryWithType = { name, urdu, type, isInv ->
                viewModel.addExpenseCategory(name, urdu, type, isInv)
            },
            onAddNewMaterial = { mat ->
                viewModel.addFactoryMaterial(mat)
            },
            onAddNewBottleSize = { name, cap, usage, refill, cost, uom, catName ->
                viewModel.addNewBottleSize(
                    name = name,
                    purchaseRate = cost,
                    packQty = 1,
                    uom = uom,
                    openingStock = 0,
                    usageType = usage,
                    defaultRefillPrice = refill,
                    salePrice = refill,
                    capacityLitres = cap,
                    depositAmount = 0.0,
                    categoryName = catName
                )
            },
            onDismiss = { expenseToEdit = null },
            onSave = { updated ->
                viewModel.updateExpense(updated)
                expenseToEdit = null
            }
        )
    }

    if (showCustomDateDialog) {
        FromToDateRangeDialog(
            initialFromDate = customStartDate,
            initialToDate = customEndDate,
            title = "Expense Custom Date (تاریخ منتخب کریں)",
            onDismiss = { showCustomDateDialog = false },
            onConfirm = { from, to ->
                customStartDate = from
                customEndDate = to
                selectedPeriod = ExpensePeriod.CUSTOM
                showCustomDateDialog = false
            }
        )
    }
}

@Composable
fun ExpenseDateSectionHeader(
    title: String,
    count: Int,
    totalAmount: Double,
    badgeColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.1f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$title • $count Vouchers",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
            Text(
                text = "Rs. ${totalAmount.toInt()}",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
    }
}

@Composable
fun ExpenseItemCard(
    expense: Expense,
    isAdmin: Boolean,
    isNew: Boolean = false,
    onDismissNew: () -> Unit = {},
    onOpenDetail: () -> Unit = {},
    onSharePdf: () -> Unit = {},
    onAcknowledge: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val sourceLabel = if (expense.paymentSource == PaymentSource.PAY_FROM_INVESTMENT) "Investment" else "From Sale"
    val modeLabel = if (expense.paymentMethod.isCash) "Cash" else "Online"

    Card(
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() }
            .testTag("expense_card_${expense.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Row 1: NEW Badge (for other users) + Type + Category + Source + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isNew) {
                        NewItemBadge(
                            onClick = onDismissNew,
                            modifier = Modifier.testTag("new_expense_badge_${expense.id}")
                        )
                    }

                    val isCapex = expense.expenseType == ExpenseType.CAPEX
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isCapex) Color(0xFFF3E5F5) else WaterNavyPrimary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = if (isCapex) "CAPEX" else "OPEX",
                            color = if (isCapex) Color(0xFF6A1B9A) else WaterNavyPrimary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ErrorRedLight
                    ) {
                        Text(
                            text = expense.categoryName,
                            color = ErrorRed,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = WaterSkyLight
                    ) {
                        Text(
                            text = "$sourceLabel/$modeLabel",
                            color = WaterBlueDeep,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    val invLines = expense.effectiveInventoryItems
                    if (invLines.isNotEmpty()) {
                        val badgeLabel = if (invLines.size > 1) {
                            val totalUnits = invLines.sumOf { it.quantity }
                            val uStr = if (totalUnits % 1.0 == 0.0) totalUnits.toInt().toString()
                            else String.format(Locale.ENGLISH, "%.1f", totalUnits)
                            "${invLines.size} Items • +$uStr"
                        } else {
                            val single = invLines.first()
                            val qtyLabel = if (single.quantity % 1.0 == 0.0) single.quantity.toInt().toString()
                            else String.format(Locale.ENGLISH, "%.1f", single.quantity)
                            if (single.itemType == "BOTTLE") "Bottle +$qtyLabel" else "Stock +$qtyLabel"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SuccessGreenLight
                        ) {
                            Text(
                                text = badgeLabel,
                                color = SuccessGreen,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (expense.staffMemberName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = WarningOrangeLight
                        ) {
                            Text(
                                text = "Staff: ${expense.staffMemberName}",
                                color = WarningOrange,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Rs. ${expense.amount.toInt()}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ErrorRed,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Row 2: Description + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = expense.description.ifBlank { expense.categoryName },
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaterNavyDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )
                Text(
                    text = "${expense.voucherNumber} • ${expense.date}",
                    fontSize = 8.5.sp,
                    color = TextMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Row 3: Compact Footer — Vendor / Entered By + Action Icons + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${expense.payee.ifEmpty { "General" }} • By: ${expense.enteredBy}",
                    fontSize = 8.5.sp,
                    color = TextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (expense.isAcknowledged) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = SuccessGreenLight,
                            modifier = Modifier
                                .height(22.dp)
                                .testTag("badge_expense_verified_${expense.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Verified",
                                    fontSize = 8.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = WarningOrangeLight,
                            modifier = Modifier
                                .height(22.dp)
                                .testTag("badge_expense_unsettle_${expense.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = "Unsettle",
                                    tint = WarningOrange,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Unsettle",
                                    fontSize = 8.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseDetailDialog(
    expense: Expense,
    canEdit: Boolean,
    canDelete: Boolean,
    canApprove: Boolean,
    onDismiss: () -> Unit,
    onSharePdf: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAcknowledge: () -> Unit
) {
    val sourceLabel = if (expense.paymentSource == PaymentSource.PAY_FROM_INVESTMENT) "Investment Account" else "From Sale"
    val modeLabel = if (expense.paymentMethod.isCash) "Cash" else "Online"

    MobileFormDialog(
        title = "Voucher #${expense.voucherNumber}",
        subtitle = "${expense.date} • ${expense.expenseType.name}",
        onDismiss = onDismiss,
        accentColor = WaterNavyPrimary,
        footerContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSharePdf,
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("expense_detail_share_pdf_btn")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Share PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                if (canEdit) {
                    FilledTonalIconButton(
                        onClick = onEdit,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = WaterSkyLight,
                            contentColor = WaterNavyPrimary
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("expense_detail_edit_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Expense", modifier = Modifier.size(18.dp))
                    }
                }

                if (canDelete) {
                    FilledTonalIconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = ErrorRedLight,
                            contentColor = ErrorRed
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("expense_detail_delete_btn")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Expense", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    ) {
        // Amount Highlight
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = ErrorRedLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Amount", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = ErrorRed)
                    Text("Rs. ${expense.amount.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ErrorRed)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (expense.isAcknowledged) SuccessGreenLight else WarningOrangeLight
                ) {
                    Text(
                        text = if (expense.isAcknowledged) "Verified" else "Pending",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (expense.isAcknowledged) SuccessGreen else WarningOrange,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Item-wise Breakdown Card for Inventory Expenses
        val invItems = expense.effectiveInventoryItems
        if (invItems.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SuccessGreenLight.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_detail_items_breakdown")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Purchased Inventory Items (${invItems.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SuccessGreen
                        )
                        Text(
                            text = "اسٹاک آئٹمز کی تفصیل",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }
                    HorizontalDivider(color = SuccessGreen.copy(alpha = 0.25f))
                    invItems.forEachIndexed { idx, item ->
                        val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString()
                        else String.format(Locale.ENGLISH, "%.2f", item.quantity)
                        val rateStr = if (item.unitRate % 1.0 == 0.0) item.unitRate.toInt().toString()
                        else String.format(Locale.ENGLISH, "%.2f", item.unitRate)
                        val lineTotalVal = if (item.lineTotal > 0.0) item.lineTotal else item.quantity * item.unitRate
                        val catLabel = item.categoryName.ifBlank { "Uncategorized" }
                        val kindLabel = if (item.itemType == "BOTTLE") "Bottle" else "Material"

                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                    Text(
                                        text = "${idx + 1}. ${item.itemName}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterNavyDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$kindLabel • Cat: $catLabel • $qtyStr ${item.uom.ifBlank { "pcs" }} × Rs. $rateStr",
                                        fontSize = 8.5.sp,
                                        color = TextSecondaryDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "Rs. ${lineTotalVal.toInt()}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WaterNavyPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detail Rows Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFF8FAFC),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ExpenseDetailRow("Expense Type", "${expense.expenseType.name} (${if (expense.hasInventoryItems) "Inventory" else "Non-Inventory"})")
                ExpenseDetailRow("Category", expense.categoryName.ifBlank { "Uncategorized" })
                if (expense.staffMemberName.isNotBlank()) {
                    ExpenseDetailRow("Staff Member", expense.staffMemberName)
                }
                if (expense.invoiceNumber.isNotBlank()) {
                    ExpenseDetailRow("Invoice / Bill #", expense.invoiceNumber)
                }
                ExpenseDetailRow("Description", expense.description)
                ExpenseDetailRow("Vendor", expense.payee.ifBlank { "General" })
                ExpenseDetailRow("Payment Source", sourceLabel)
                ExpenseDetailRow("Payment Mode", modeLabel)
                ExpenseDetailRow("Note", expense.notes.ifBlank { "-" })
                ExpenseDetailRow("Entered By", expense.enteredBy.ifBlank { "-" })
                if (expense.isAcknowledged && expense.acknowledgedBy.isNotBlank()) {
                    ExpenseDetailRow("Verified By", expense.acknowledgedBy)
                }
            }
        }

        EditMetadataBadge(
            lastEditedAt = expense.lastEditedAt,
            lastEditedBy = expense.lastEditedBy
        )

        if (!expense.isAcknowledged && canApprove) {
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Verify Voucher Now", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun ExpenseDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryDark,
            modifier = Modifier.width(105.dp)
        )
        Text(
            text = value,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = WaterNavyDark,
            modifier = Modifier.weight(1f)
        )
    }
}

// AddExpenseDialog, EditExpenseDialog, and ManageExpenseCategoriesDialog
// are modularized in ExpenseFormDialogs.kt (same package com.example.ui.screens).

