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
import com.example.ui.components.CompactDropdownField
import com.example.ui.components.CompactFormTextField
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

    val periodExpenses = remember(expenses, selectedPeriod) {
        expenses.filter { exp ->
            when (selectedPeriod) {
                ExpensePeriod.ALL -> true
                ExpensePeriod.TODAY -> exp.date == todayStr
                ExpensePeriod.YESTERDAY -> exp.date == yesterdayStr
                ExpensePeriod.LAST_7_DAYS -> exp.date >= weekStartStr
                ExpensePeriod.THIS_MONTH -> exp.date >= monthStartStr
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

                // Single-Line Aligned Period Tabs: Today | Yesterday | Last 7 Days | This Month | All Expense
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_period_tabs"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabConfigs = listOf(
                        Triple(ExpensePeriod.TODAY, "Today", 0.78f),
                        Triple(ExpensePeriod.YESTERDAY, "Yesterday", 1.05f),
                        Triple(ExpensePeriod.LAST_7_DAYS, "Last 7 Days", 1.12f),
                        Triple(ExpensePeriod.THIS_MONTH, "This Month", 1.10f),
                        Triple(ExpensePeriod.ALL, "All Expense", 1.12f)
                    )

                    tabConfigs.forEach { (period, label, weightVal) ->
                        val isSelected = selectedPeriod == period
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isSelected) WaterSkyButton else WaterSkyLight.copy(alpha = 0.75f),
                            modifier = Modifier
                                .weight(weightVal)
                                .height(29.dp)
                                .clickable { selectedPeriod = period }
                                .testTag("expense_tab_${period.name.lowercase()}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 8.5.sp,
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

    // Add Expense Dialog
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
            onAddCategory = { name, urdu, type ->
                viewModel.addExpenseCategory(name, urdu, type)
            }
        )
    }

    if (showNewCategoryDialog) {
        ManageExpenseCategoriesDialog(
            categories = categories,
            onAddCategory = { name, urdu, type -> viewModel.addExpenseCategory(name, urdu, type) },
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

    // Admin Edit Expense Dialog
    if (expenseToEdit != null) {
        EditExpenseDialog(
            expense = expenseToEdit!!,
            categories = categories,
            onDismiss = { expenseToEdit = null },
            onSave = { updated ->
                viewModel.updateExpense(updated)
                expenseToEdit = null
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

                    if (expense.linkedInventoryType.isNotBlank() && expense.linkedInventoryQty > 0.0) {
                        val qtyLabel = if (expense.linkedInventoryQty % 1.0 == 0.0) {
                            expense.linkedInventoryQty.toInt().toString()
                        } else {
                            String.format(Locale.ENGLISH, "%.1f", expense.linkedInventoryQty)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SuccessGreenLight
                        ) {
                            Text(
                                text = if (expense.linkedInventoryType == "BOTTLE") "Bottle +$qtyLabel" else "Stock +$qtyLabel",
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
                ExpenseDetailRow("Expense Type", expense.expenseType.name)
                ExpenseDetailRow("Category", expense.categoryName)
                if (expense.staffMemberName.isNotBlank()) {
                    ExpenseDetailRow("Staff Member", expense.staffMemberName)
                }
                if (expense.linkedInventoryType.isNotBlank() && expense.linkedInventoryItemName.isNotBlank()) {
                    val qtyText = if (expense.linkedInventoryQty % 1.0 == 0.0) {
                        expense.linkedInventoryQty.toInt().toString()
                    } else {
                        String.format(Locale.ENGLISH, "%.2f", expense.linkedInventoryQty)
                    }
                    val stockKind = if (expense.linkedInventoryType == "BOTTLE") "Bottle Stock" else "Material Stock"
                    ExpenseDetailRow("Linked Stock", "${expense.linkedInventoryItemName} (+$qtyText • $stockKind)")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    categories: List<ExpenseCategory>,
    isAdmin: Boolean,
    factoryMaterials: List<FactoryMaterial> = emptyList(),
    bottleSizes: List<BottleSize> = emptyList(),
    plantWorkers: List<PlantWorker> = emptyList(),
    onGetWorkerSalarySummary: ((workerId: String, month: String) -> WorkerSalarySummary)? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, PaymentMethod, String, String, ExpenseType, String) -> Unit,
    onSaveWithSource: ((String, String, String, Double, PaymentMethod, String, String, ExpenseType, String, PaymentSource) -> Unit)? = null,
    onSaveWithSourceAndStaff: ((
        catId: String,
        catName: String,
        desc: String,
        amt: Double,
        method: PaymentMethod,
        payee: String,
        notes: String,
        type: ExpenseType,
        date: String,
        source: PaymentSource,
        invoiceNo: String,
        staffMemberId: String,
        staffMemberName: String
    ) -> Unit)? = null,
    onSaveWithMaterialRefill: ((
        materialId: String,
        qty: Double,
        unitCost: Double,
        catId: String,
        catName: String,
        expType: ExpenseType,
        desc: String,
        supplier: String,
        invoiceNo: String,
        notes: String,
        date: String,
        source: PaymentSource,
        method: PaymentMethod
    ) -> Unit)? = null,
    onSaveWithBottlePurchase: ((
        bottleSizeId: String,
        qty: Int,
        unitCost: Double,
        catId: String,
        catName: String,
        expType: ExpenseType,
        desc: String,
        supplier: String,
        invoiceNo: String,
        notes: String,
        date: String,
        source: PaymentSource,
        method: PaymentMethod
    ) -> Unit)? = null,
    onAddCategory: (String, String, ExpenseType) -> Unit = { _, _, _ -> }
) {
    val allCategories = remember(categories) {
        (STANDARD_EXPENSE_CATEGORIES + categories)
            .distinctBy { it.name.trim().lowercase() }
    }
    val activeMaterials = remember(factoryMaterials) { factoryMaterials.filter { it.active } }
    val activeBottleSizes = remember(bottleSizes) { bottleSizes.filter { it.active } }
    val activeWorkers = remember(plantWorkers) { plantWorkers.filter { it.active } }

    var selectedType by remember { mutableStateOf(ExpenseType.OPEX) }
    var selectedCategory by remember {
        mutableStateOf(
            allCategories.firstOrNull { it.type == ExpenseType.OPEX } ?: allCategories.first()
        )
    }

    var expenseDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var paymentSource by remember { mutableStateOf(PaymentSource.PAY_FROM_SALE) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Inventory Purchase Mode State (Other Material OR Bottle Stock)
    var isInventoryPurchase by remember { mutableStateOf(false) }
    var isBottleStockMode by remember { mutableStateOf(false) }
    var selectedMaterial by remember(activeMaterials) { mutableStateOf(activeMaterials.firstOrNull()) }
    var selectedBottleSize by remember(activeBottleSizes) { mutableStateOf(activeBottleSizes.firstOrNull()) }
    var invQtyText by remember { mutableStateOf("") }
    var invUnitRateText by remember { mutableStateOf("") }

    // Staff Salary Selection State
    val isSalaryCategory = selectedCategory.id == "cat_salary" ||
        selectedCategory.name.contains("salary", ignoreCase = true)
    var selectedWorker by remember(activeWorkers) { mutableStateOf<PlantWorker?>(null) }
    val expenseMonth = remember(expenseDate) {
        if (expenseDate.length >= 7) expenseDate.substring(0, 7)
        else SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    }
    val workerSalarySummary = remember(selectedWorker, expenseMonth) {
        selectedWorker?.let { w -> onGetWorkerSalarySummary?.invoke(w.id, expenseMonth) }
    }

    // Auto-calculate total amount when Inventory Purchase checkbox is enabled
    val invQtyVal = invQtyText.toDoubleOrNull() ?: 0.0
    val invRateVal = invUnitRateText.toDoubleOrNull() ?: 0.0
    val invComputedTotal = invQtyVal * invRateVal

    val hasChanges = description.isNotBlank() || amountText.isNotBlank() || vendor.isNotBlank() ||
        notes.isNotBlank() || invQtyText.isNotBlank() || selectedWorker != null
    val context = LocalContext.current

    fun applySelectedMaterialDefaults(mat: FactoryMaterial) {
        selectedMaterial = mat
        invUnitRateText = if (mat.purchaseRate > 0.0) {
            if (mat.purchaseRate % 1.0 == 0.0) mat.purchaseRate.toInt().toString()
            else String.format(Locale.ENGLISH, "%.2f", mat.purchaseRate)
        } else ""
        if (mat.supplierName.isNotBlank()) vendor = mat.supplierName
        if (description.isBlank() || description.startsWith("Stock Purchase:") || description.startsWith("Bottle Purchase:")) {
            description = "Stock Purchase: ${mat.name}"
        }
        val matchingCat = allCategories.firstOrNull { it.name.equals(mat.itemCategory, ignoreCase = true) }
        if (matchingCat != null) {
            selectedCategory = matchingCat
            selectedType = mat.costType
        }
    }

    fun applySelectedBottleDefaults(size: BottleSize) {
        selectedBottleSize = size
        invUnitRateText = if (size.latestPurchaseRate > 0.0) {
            if (size.latestPurchaseRate % 1.0 == 0.0) size.latestPurchaseRate.toInt().toString()
            else String.format(Locale.ENGLISH, "%.2f", size.latestPurchaseRate)
        } else ""
        if (description.isBlank() || description.startsWith("Stock Purchase:") || description.startsWith("Bottle Purchase:")) {
            description = "Bottle Purchase: ${size.name}"
        }
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        try {
            val parts = expenseDate.split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        } catch (_: Exception) {}
        android.app.DatePickerDialog(
            context,
            { _, y, m, d ->
                expenseDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    MobileFormDialog(
        title = "Add Expense Voucher",
        subtitle = "Date: $expenseDate",
        hasUnsavedChanges = hasChanges,
        onDismiss = onDismiss,
        accentColor = ErrorRed,
        footerContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = WaterSkyLight,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { openDatePicker() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = WaterNavyPrimary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(expenseDate, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary, maxLines = 1, softWrap = false)
                    }
                }

                Button(
                    onClick = {
                        if (isInventoryPurchase && !isSalaryCategory) {
                            if (isBottleStockMode) {
                                val size = selectedBottleSize
                                val qtyInt = invQtyText.trim().toIntOrNull() ?: 0
                                val unitRate = invUnitRateText.trim().toDoubleOrNull() ?: 0.0
                                when {
                                    size == null -> {
                                        errorMessage = "Select an existing Bottle Size first"
                                        return@Button
                                    }
                                    qtyInt <= 0 -> {
                                        errorMessage = "Enter bottle quantity > 0"
                                        return@Button
                                    }
                                    unitRate < 0.0 -> {
                                        errorMessage = "Enter valid unit rate"
                                        return@Button
                                    }
                                    onSaveWithBottlePurchase != null -> {
                                        onSaveWithBottlePurchase(
                                            size.id,
                                            qtyInt,
                                            unitRate,
                                            selectedCategory.id,
                                            selectedCategory.name,
                                            selectedType,
                                            description.trim().ifBlank { "Bottle Purchase: $qtyInt x ${size.name}" },
                                            vendor.trim(),
                                            invoiceNumber.trim(),
                                            notes.trim(),
                                            expenseDate.trim(),
                                            paymentSource,
                                            paymentMethod
                                        )
                                        onDismiss()
                                        return@Button
                                    }
                                }
                            } else {
                                val mat = selectedMaterial
                                val qtyD = invQtyText.trim().toDoubleOrNull() ?: 0.0
                                val unitRate = invUnitRateText.trim().toDoubleOrNull() ?: 0.0
                                when {
                                    mat == null -> {
                                        errorMessage = "Select an existing Inventory Item (add new items in Stock & Cost → Materials first)"
                                        return@Button
                                    }
                                    qtyD <= 0.0 -> {
                                        errorMessage = "Enter quantity > 0"
                                        return@Button
                                    }
                                    unitRate < 0.0 -> {
                                        errorMessage = "Enter valid unit rate"
                                        return@Button
                                    }
                                    onSaveWithMaterialRefill != null -> {
                                        onSaveWithMaterialRefill(
                                            mat.id,
                                            qtyD,
                                            unitRate,
                                            selectedCategory.id,
                                            selectedCategory.name,
                                            selectedType,
                                            description.trim().ifBlank { "Stock Purchase: ${mat.name}" },
                                            vendor.trim(),
                                            invoiceNumber.trim(),
                                            notes.trim(),
                                            expenseDate.trim(),
                                            paymentSource,
                                            paymentMethod
                                        )
                                        onDismiss()
                                        return@Button
                                    }
                                }
                            }
                        }

                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        val finalDesc = description.trim().ifBlank {
                            if (isSalaryCategory && selectedWorker != null) {
                                "Salary / Advance - ${selectedWorker!!.name} ($expenseMonth)"
                            } else ""
                        }
                        if (finalDesc.isBlank()) {
                            errorMessage = "Please enter Description"
                            return@Button
                        }
                        if (amt <= 0.0) {
                            errorMessage = "Please enter valid Amount"
                            return@Button
                        }
                        val staffId = if (isSalaryCategory) selectedWorker?.id.orEmpty() else ""
                        val staffName = if (isSalaryCategory) selectedWorker?.name.orEmpty() else ""
                        val finalPayee = vendor.trim().ifBlank { staffName }

                        if (onSaveWithSourceAndStaff != null) {
                            onSaveWithSourceAndStaff(
                                selectedCategory.id,
                                selectedCategory.name,
                                finalDesc,
                                amt,
                                paymentMethod,
                                finalPayee,
                                notes.trim(),
                                selectedType,
                                expenseDate.trim(),
                                paymentSource,
                                invoiceNumber.trim(),
                                staffId,
                                staffName
                            )
                        } else if (onSaveWithSource != null) {
                            onSaveWithSource(
                                selectedCategory.id,
                                selectedCategory.name,
                                finalDesc,
                                amt,
                                paymentMethod,
                                finalPayee,
                                notes.trim(),
                                selectedType,
                                expenseDate.trim(),
                                paymentSource
                            )
                        } else {
                            onSave(
                                selectedCategory.id,
                                selectedCategory.name,
                                finalDesc,
                                amt,
                                paymentMethod,
                                finalPayee,
                                notes.trim(),
                                selectedType,
                                expenseDate.trim()
                            )
                        }
                        onDismiss()
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("save_expense_voucher_btn")
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Save Voucher", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    ) {
        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ErrorRedLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage!!,
                    color = ErrorRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }

        // 1. Expense Type - Dropdown (OPEX, CAPEX)
        CompactDropdownField(
            label = "Expense Type*",
            selectedText = if (selectedType == ExpenseType.OPEX) "OPEX" else "CAPEX",
            options = listOf(ExpenseType.OPEX, ExpenseType.CAPEX),
            optionLabel = { if (it == ExpenseType.OPEX) "OPEX (Operating)" else "CAPEX (Asset / Capital)" },
            onOptionSelected = { newType ->
                selectedType = newType
                if (newType == ExpenseType.CAPEX) {
                    selectedCategory = allCategories.firstOrNull { it.type == ExpenseType.CAPEX } ?: selectedCategory
                } else if (selectedCategory.type == ExpenseType.CAPEX) {
                    selectedCategory = allCategories.firstOrNull { it.type == ExpenseType.OPEX } ?: selectedCategory
                }
            },
            modifier = Modifier.testTag("expense_type_dropdown")
        )

        // 2. Category - Dropdown (All standard + custom categories)
        CompactDropdownField(
            label = "Category*",
            selectedText = selectedCategory.name,
            options = allCategories,
            optionLabel = { it.name },
            onOptionSelected = { cat ->
                selectedCategory = cat
                errorMessage = null
                if (cat.id == "cat_salary" || cat.name.contains("salary", ignoreCase = true)) {
                    isInventoryPurchase = false
                } else if (cat.name.contains("bottle", ignoreCase = true)) {
                    isBottleStockMode = true
                    if (isInventoryPurchase) {
                        selectedBottleSize?.let { applySelectedBottleDefaults(it) }
                    }
                }
            },
            modifier = Modifier.testTag("expense_category_dropdown")
        )

        // 2B. STAFF SALARY SMART SELECTOR (Shown BEFORE Description when Staff Salary selected)
        if (isSalaryCategory) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = WaterSkyLight.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, WaterAqua.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_staff_salary_section")
            ) {
                Column(
                    modifier = Modifier.padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Select Staff Member (عملہ منتخب کریں)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WaterNavyPrimary
                    )
                    if (activeWorkers.isEmpty()) {
                        Text(
                            text = "No workers registered yet. Add workers in Stock & Cost → Staff tab to track monthly salary & advance deduction automatically.",
                            fontSize = 9.5.sp,
                            color = TextSecondaryDark
                        )
                    } else {
                        CompactDropdownField(
                            label = "Staff Name (عملے کا نام)*",
                            selectedText = selectedWorker?.let { "${it.name} (Rs. ${it.monthlySalary.toInt()})" } ?: "Select Staff...",
                            options = activeWorkers,
                            optionLabel = { w ->
                                val sum = onGetWorkerSalarySummary?.invoke(w.id, expenseMonth)
                                if (sum != null && sum.paidOrAdvanceThisMonth > 0.0) {
                                    "${w.name} • Rem: Rs. ${sum.remainingSalary.toInt()} / ${w.monthlySalary.toInt()}"
                                } else {
                                    "${w.name} • Salary: Rs. ${w.monthlySalary.toInt()}"
                                }
                            },
                            onOptionSelected = { worker ->
                                selectedWorker = worker
                                vendor = worker.name
                                val sum = onGetWorkerSalarySummary?.invoke(worker.id, expenseMonth)
                                val suggested = sum?.remainingSalary ?: worker.monthlySalary
                                amountText = if (suggested > 0.0) suggested.toInt().toString() else ""
                                description = if (sum != null && sum.paidOrAdvanceThisMonth > 0.0) {
                                    "Salary Balance - ${worker.name} ($expenseMonth)"
                                } else {
                                    "Salary / Advance - ${worker.name} ($expenseMonth)"
                                }
                                errorMessage = null
                            },
                            modifier = Modifier.testTag("expense_staff_dropdown")
                        )

                        workerSalarySummary?.let { summary ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Salary", fontSize = 8.sp, color = TextSecondaryDark, fontWeight = FontWeight.Bold)
                                        Text("Rs. ${summary.monthlySalary.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = WaterNavyPrimary, maxLines = 1)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (summary.paidOrAdvanceThisMonth > 0.0) WarningOrangeLight else Color.White,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Advance Paid", fontSize = 8.sp, color = WarningOrange, fontWeight = FontWeight.Bold)
                                        Text("Rs. ${summary.paidOrAdvanceThisMonth.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = WarningOrange, maxLines = 1)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (summary.remainingSalary > 0.0) SuccessGreenLight else Color.White,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Remaining", fontSize = 8.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                        Text("Rs. ${summary.remainingSalary.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen, maxLines = 1)
                                    }
                                }
                            }
                            Text(
                                text = "Month: $expenseMonth • Any amount paid deducts automatically as advance/salary.",
                                fontSize = 8.5.sp,
                                color = WaterBlueDeep
                            )
                        }
                    }
                }
            }
        } else {
            // 2C. INVENTORY ITEM / BOTTLE STOCK PURCHASE CHECKBOX (Two-Way Sync)
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (isInventoryPurchase) WaterSkyLight.copy(alpha = 0.85f) else Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isInventoryPurchase) WaterBlueMedium else BorderLight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !isInventoryPurchase
                        isInventoryPurchase = next
                        errorMessage = null
                        if (next) {
                            if (selectedCategory.name.contains("bottle", ignoreCase = true)) {
                                isBottleStockMode = true
                                selectedBottleSize?.let { applySelectedBottleDefaults(it) }
                            } else {
                                isBottleStockMode = false
                                selectedMaterial?.let { applySelectedMaterialDefaults(it) }
                            }
                        }
                    }
                    .testTag("expense_inventory_checkbox_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isInventoryPurchase,
                        onCheckedChange = { checked ->
                            isInventoryPurchase = checked
                            errorMessage = null
                            if (checked) {
                                if (selectedCategory.name.contains("bottle", ignoreCase = true)) {
                                    isBottleStockMode = true
                                    selectedBottleSize?.let { applySelectedBottleDefaults(it) }
                                } else {
                                    isBottleStockMode = false
                                    selectedMaterial?.let { applySelectedMaterialDefaults(it) }
                                }
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = WaterBlueDeep),
                        modifier = Modifier
                            .size(22.dp)
                            .testTag("expense_inventory_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Inventory Item / Bottle Stock Purchase",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WaterNavyDark
                        )
                        Text(
                            text = "اسٹاک آئٹم خریداری — ٹک کریں تو خرچہ اور اسٹاک دونوں اپڈیٹ ہوں گے",
                            fontSize = 8.5.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }

            if (isInventoryPurchase) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WaterAqua.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        // Stock Type Toggle: Other Material vs Bottle Stock
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                false to "Other Material (${activeMaterials.size})",
                                true to "Bottle Stock (${activeBottleSizes.size})"
                            ).forEach { (isBottle, label) ->
                                val selected = isBottleStockMode == isBottle
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = if (selected) WaterSkyButton else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) WaterBlueMedium else BorderLight
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clickable {
                                            isBottleStockMode = isBottle
                                            errorMessage = null
                                            if (isBottle) {
                                                selectedBottleSize?.let { applySelectedBottleDefaults(it) }
                                            } else {
                                                selectedMaterial?.let { applySelectedMaterialDefaults(it) }
                                            }
                                        }
                                        .testTag(if (isBottle) "exp_inv_mode_bottle" else "exp_inv_mode_material")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) WaterSkyButtonContent else WaterNavyDark,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        if (!isBottleStockMode) {
                            if (activeMaterials.isEmpty()) {
                                Text(
                                    text = "No Other Material items found. Add new items in Stock & Cost → Materials first.",
                                    fontSize = 9.5.sp,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                CompactDropdownField(
                                    label = "Select Inventory Item*",
                                    selectedText = selectedMaterial?.let { "${it.name} (${it.itemCategory} • ${it.uom})" } ?: "",
                                    options = activeMaterials,
                                    optionLabel = { "${it.name} • ${it.itemCategory} (${it.uom})" },
                                    onOptionSelected = { mat ->
                                        applySelectedMaterialDefaults(mat)
                                        errorMessage = null
                                    },
                                    modifier = Modifier.testTag("exp_inv_material_dropdown")
                                )

                                selectedMaterial?.let { mat ->
                                    val curStockStr = if (mat.currentStock % 1.0 == 0.0) {
                                        mat.currentStock.toInt().toString()
                                    } else {
                                        String.format(Locale.ENGLISH, "%.2f", mat.currentStock)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = WaterSkyLight,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Category: ${mat.itemCategory}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WaterNavyDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "UOM: ${mat.uom} • Stock: $curStockStr",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = WaterBlueDeep,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "New items must first be added in Stock & Cost → Materials.",
                                fontSize = 8.5.sp,
                                color = TextSecondaryDark
                            )
                        } else {
                            if (activeBottleSizes.isEmpty()) {
                                Text(
                                    text = "No active Bottle Sizes found. Add bottle sizes in Stock & Cost → Sizes first.",
                                    fontSize = 9.5.sp,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                CompactDropdownField(
                                    label = "Select Bottle Size*",
                                    selectedText = selectedBottleSize?.name ?: "",
                                    options = activeBottleSizes,
                                    optionLabel = { "${it.name} • Rs. ${it.latestPurchaseRate.toInt()}" },
                                    onOptionSelected = { size ->
                                        applySelectedBottleDefaults(size)
                                        errorMessage = null
                                    },
                                    modifier = Modifier.testTag("exp_inv_bottle_dropdown")
                                )
                            }
                        }

                        // Refill Quantity & Unit Rate Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompactFormTextField(
                                value = invQtyText,
                                onValueChange = { raw ->
                                    invQtyText = if (isBottleStockMode) {
                                        raw.filter { it.isDigit() }
                                    } else {
                                        raw.filter { it.isDigit() || it == '.' }
                                    }
                                    errorMessage = null
                                },
                                label = if (isBottleStockMode) "Quantity (Bottles)*" else "Quantity (${selectedMaterial?.uom ?: "Unit"})*",
                                placeholder = "0",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (isBottleStockMode) KeyboardType.Number else KeyboardType.Decimal
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("exp_inv_qty_input")
                            )

                            CompactFormTextField(
                                value = invUnitRateText,
                                onValueChange = { raw ->
                                    invUnitRateText = raw.filter { it.isDigit() || it == '.' }
                                    errorMessage = null
                                },
                                label = "Unit Rate (Rs.)*",
                                placeholder = "0",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("exp_inv_rate_input")
                            )
                        }

                        CompactFormTextField(
                            value = invoiceNumber,
                            onValueChange = { invoiceNumber = it },
                            label = "Invoice / Bill No. (Optional)",
                            placeholder = "e.g. INV-104",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("exp_inv_invoice_input")
                        )

                        if (invComputedTotal > 0.0) {
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = SuccessGreenLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 9.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total Purchase & Expense:",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                    Text(
                                        text = "Rs. ${invComputedTotal.toInt()}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SuccessGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Description
        CompactFormTextField(
            value = description,
            onValueChange = {
                description = it
                errorMessage = null
            },
            label = "Description*",
            placeholder = "Enter expense description...",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_description_input")
        )

        // 4. Amount (Hidden in Inventory mode since Total = Qty * Unit Rate)
        if (!isInventoryPurchase || isSalaryCategory) {
            CompactFormTextField(
                value = amountText,
                onValueChange = {
                    amountText = it.filter { ch -> ch.isDigit() || ch == '.' }
                    errorMessage = null
                },
                label = "Amount (Rs.)*",
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_amount_input")
            )
        }

        // 5. Vendor / Supplier
        CompactFormTextField(
            value = vendor,
            onValueChange = { vendor = it },
            label = if (isInventoryPurchase) "Supplier" else "Vendor",
            placeholder = "Supplier / Vendor name",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_vendor_input")
        )

        // 6. Payment Source (From Sale, Investment)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Payment Source",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyDark
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    PaymentSource.PAY_FROM_SALE to "From Sale",
                    PaymentSource.PAY_FROM_INVESTMENT to "Investment"
                ).forEach { (src, label) ->
                    val selected = paymentSource == src
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (selected) WaterSkyButton else WaterSkyLight.copy(alpha = 0.7f),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clickable { paymentSource = src }
                            .testTag("expense_source_${src.name.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) WaterSkyButtonContent else WaterNavyDark,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // 7. Payment Mode (Cash, Online)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Payment Mode",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyDark
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    PaymentMethod.CASH to "Cash",
                    PaymentMethod.BANK_TRANSFER to "Online"
                ).forEach { (mode, label) ->
                    val selected = paymentMethod == mode
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (selected) WaterSkyButton else WaterSkyLight.copy(alpha = 0.7f),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clickable { paymentMethod = mode }
                            .testTag("expense_mode_${mode.name.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) WaterSkyButtonContent else WaterNavyDark,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // 8. Note
        CompactFormTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Note",
            placeholder = "Optional note...",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_note_input")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: Expense,
    categories: List<ExpenseCategory>,
    isAdmin: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    val allCategories = remember(categories) {
        (STANDARD_EXPENSE_CATEGORIES + categories)
            .distinctBy { it.name.trim().lowercase() }
    }

    var selectedType by remember { mutableStateOf(expense.expenseType) }
    var selectedCategory by remember {
        mutableStateOf(
            allCategories.find { it.id == expense.categoryId || it.name.equals(expense.categoryName, ignoreCase = true) }
                ?: allCategories.first()
        )
    }
    var description by remember { mutableStateOf(expense.description) }
    var amountText by remember { mutableStateOf(expense.amount.toInt().toString()) }
    var payee by remember { mutableStateOf(expense.payee) }
    var paymentSource by remember { mutableStateOf(expense.paymentSource) }
    var paymentMethod by remember { mutableStateOf(if (expense.paymentMethod.isCash) PaymentMethod.CASH else PaymentMethod.BANK_TRANSFER) }
    var notes by remember { mutableStateOf(expense.notes) }
    var pendingSaveConfirm by remember { mutableStateOf<Expense?>(null) }

    if (pendingSaveConfirm != null) {
        val candidate = pendingSaveConfirm!!
        AlertDialog(
            onDismissRequest = { pendingSaveConfirm = null },
            containerColor = Color.White,
            icon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(30.dp)) },
            title = { Text("Confirm Edit Expense?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WaterNavyPrimary) },
            text = {
                Text(
                    text = "Save changes to Voucher #${candidate.voucherNumber} (${candidate.categoryName} • Rs. ${candidate.amount.toInt()})?\n\nکیا آپ واقعی اس خرچے میں تبدیلی محفوظ کرنا چاہتے ہیں؟",
                    fontSize = 11.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingSaveConfirm = null
                        onSave(candidate)
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_edit_expense_dialog_btn")
                ) {
                    Text("Yes, Save Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSaveConfirm = null }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }

    MobileFormDialog(
        title = "Edit Voucher #${expense.voucherNumber}",
        subtitle = "Date: ${expense.date}",
        onDismiss = onDismiss,
        accentColor = WaterNavyPrimary,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondaryDark, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: expense.amount
                    val updated = expense.copy(
                        categoryId = selectedCategory.id,
                        categoryName = selectedCategory.name,
                        description = description.ifBlank { expense.description },
                        amount = amt,
                        paymentMethod = paymentMethod,
                        paymentSource = paymentSource,
                        payee = payee.trim(),
                        notes = notes.trim(),
                        expenseType = selectedType
                    )
                    pendingSaveConfirm = updated
                },
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("save_edit_expense_btn")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
            }
        }
    ) {
        CompactDropdownField(
            label = "Expense Type*",
            selectedText = selectedType.name,
            options = listOf(ExpenseType.OPEX, ExpenseType.CAPEX),
            optionLabel = { if (it == ExpenseType.OPEX) "OPEX (Operating)" else "CAPEX (Asset / Capital)" },
            onOptionSelected = { selectedType = it }
        )

        CompactDropdownField(
            label = "Category*",
            selectedText = selectedCategory.name,
            options = allCategories,
            optionLabel = { it.name },
            onOptionSelected = { selectedCategory = it }
        )

        CompactFormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Description*",
            modifier = Modifier.fillMaxWidth().testTag("edit_expense_desc")
        )

        CompactFormTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = "Amount (Rs.)*",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("edit_expense_amount")
        )

        CompactFormTextField(
            value = payee,
            onValueChange = { payee = it },
            label = "Vendor",
            modifier = Modifier.fillMaxWidth().testTag("edit_expense_payee")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                PaymentSource.PAY_FROM_SALE to "From Sale",
                PaymentSource.PAY_FROM_INVESTMENT to "Investment"
            ).forEach { (src, label) ->
                val selected = paymentSource == src
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (selected) WaterSkyButton else WaterSkyLight.copy(alpha = 0.7f),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clickable { paymentSource = src }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) WaterSkyButtonContent else WaterNavyDark, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                PaymentMethod.CASH to "Cash",
                PaymentMethod.BANK_TRANSFER to "Online"
            ).forEach { (mode, label) ->
                val selected = paymentMethod == mode
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (selected) WaterSkyButton else WaterSkyLight.copy(alpha = 0.7f),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clickable { paymentMethod = mode }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) WaterSkyButtonContent else WaterNavyDark, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        CompactFormTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Note",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ManageExpenseCategoriesDialog(
    categories: List<ExpenseCategory>,
    onAddCategory: (name: String, urdu: String, type: ExpenseType) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newCatName by remember { mutableStateOf("") }
    var newCatUrdu by remember { mutableStateOf("") }
    var newCatType by remember { mutableStateOf(ExpenseType.OPEX) }

    MobileFormDialog(
        title = "Expense Categories",
        subtitle = "Manage custom expense categories",
        onDismiss = onDismiss,
        accentColor = WaterNavyPrimary,
        footerContent = {
            Button(
                onClick = onDismiss,
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFF8FAFC),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add New Category:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyDark)
                CompactFormTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = "Category Name*",
                    modifier = Modifier.fillMaxWidth()
                )
                CompactFormTextField(
                    value = newCatUrdu,
                    onValueChange = { newCatUrdu = it },
                    label = "Urdu Name (Optional)",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(ExpenseType.OPEX, ExpenseType.CAPEX).forEach { t ->
                        val isSel = newCatType == t
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isSel) WaterSkyButton else WaterSkyLight,
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clickable { newCatType = t }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(t.name, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (isSel) WaterSkyButtonContent else WaterNavyDark)
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        if (newCatName.isNotBlank()) {
                            onAddCategory(newCatName.trim(), newCatUrdu.trim(), newCatType)
                            newCatName = ""
                            newCatUrdu = ""
                        }
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Category", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("Existing Categories (${categories.size}):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)

        categories.forEach { cat ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cat.name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyDark)
                        if (cat.urduName.isNotBlank()) {
                            Text(cat.urduName, fontSize = 9.sp, color = TextSecondaryDark)
                        }
                    }

                    IconButton(
                        onClick = { onDeleteCategory(cat.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
