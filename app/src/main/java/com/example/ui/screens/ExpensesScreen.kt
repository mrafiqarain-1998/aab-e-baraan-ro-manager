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

    var showAddDialog by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var selectedExpenseForDetail by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var previewPdfRequest by remember { mutableStateOf<PdfPreviewRequest?>(null) }

    // Period Filter State
    var selectedPeriod by remember { mutableStateOf(ExpensePeriod.ALL) }

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

    val isAdmin = viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.APPROVE) ||
        viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.DELETE)
    val isViewOnly = !viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.ADD)
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
                            color = if (isSelected) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.75f),
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
                                    color = if (isSelected) Color.White else WaterNavyDark,
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
                            color = if (isSel) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.55f),
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
                                    color = if (isSel) Color.White else WaterNavyDark,
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
                        color = if (activeFiltersCount > 0) WaterNavyPrimary else WaterSkyLight,
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
                                tint = if (activeFiltersCount > 0) Color.White else WaterNavyDark,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (activeFiltersCount > 0) "Filter ($activeFiltersCount Active)" else "Filter",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeFiltersCount > 0) Color.White else WaterNavyDark,
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
                                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White)
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

    // Expense Detail Dialog (with Admin Edit/Delete Icons & Share PDF)
    if (selectedExpenseForDetail != null) {
        val currentExp = expenses.find { it.id == selectedExpenseForDetail!!.id } ?: selectedExpenseForDetail!!
        ExpenseDetailDialog(
            expense = currentExp,
            isAdmin = isAdmin,
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
            onDismiss = { showAddDialog = false },
            onSave = { catId, catName, desc, amt, method, payee, notes, type, date ->
                viewModel.addExpense(catId, catName, desc, amt, method, payee, notes, type, date)
                showAddDialog = false
            },
            onSaveWithSource = { catId, catName, desc, amt, method, payee, notes, type, date, source ->
                viewModel.addExpense(catId, catName, desc, amt, method, payee, notes, type, date, source)
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
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = WaterSkyLight,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onSharePdf)
                            .testTag("share_expense_pdf_${expense.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = "Share Expense PDF",
                                tint = WaterNavyPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    if (isAdmin) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(24.dp).testTag("edit_expense_${expense.id}")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Expense",
                                tint = WaterNavyPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(24.dp).testTag("delete_expense_${expense.id}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Expense",
                                tint = ErrorRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (expense.isAcknowledged) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = SuccessGreenLight,
                            modifier = Modifier.height(22.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
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
                    } else if (isAdmin) {
                        Button(
                            onClick = onAcknowledge,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(5.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                            modifier = Modifier.height(22.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Verify",
                                fontSize = 8.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = WarningOrangeLight,
                            modifier = Modifier.height(22.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Pending",
                                    fontSize = 9.sp,
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
    isAdmin: Boolean,
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
                    colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
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

                if (isAdmin) {
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

        if (!expense.isAcknowledged && isAdmin) {
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
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, PaymentMethod, String, String, ExpenseType, String) -> Unit,
    onSaveWithSource: ((String, String, String, Double, PaymentMethod, String, String, ExpenseType, String, PaymentSource) -> Unit)? = null,
    onAddCategory: (String, String, ExpenseType) -> Unit = { _, _, _ -> }
) {
    val allCategories = remember(categories) {
        (STANDARD_EXPENSE_CATEGORIES + categories)
            .distinctBy { it.name.trim().lowercase() }
    }

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
    var paymentSource by remember { mutableStateOf(PaymentSource.PAY_FROM_SALE) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val hasChanges = description.isNotBlank() || amountText.isNotBlank() || vendor.isNotBlank() || notes.isNotBlank()
    val context = LocalContext.current

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
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (description.isBlank()) {
                            errorMessage = "Please enter Description"
                            return@Button
                        }
                        if (amt <= 0.0) {
                            errorMessage = "Please enter valid Amount"
                            return@Button
                        }
                        if (onSaveWithSource != null) {
                            onSaveWithSource(
                                selectedCategory.id,
                                selectedCategory.name,
                                description.trim(),
                                amt,
                                paymentMethod,
                                vendor.trim(),
                                notes.trim(),
                                selectedType,
                                expenseDate.trim(),
                                paymentSource
                            )
                        } else {
                            onSave(
                                selectedCategory.id,
                                selectedCategory.name,
                                description.trim(),
                                amt,
                                paymentMethod,
                                vendor.trim(),
                                notes.trim(),
                                selectedType,
                                expenseDate.trim()
                            )
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
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

        // 2. Category - Dropdown (All 13 requested options + custom)
        CompactDropdownField(
            label = "Category*",
            selectedText = selectedCategory.name,
            options = allCategories,
            optionLabel = { it.name },
            onOptionSelected = { selectedCategory = it },
            modifier = Modifier.testTag("expense_category_dropdown")
        )

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

        // 4. Amount
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

        // 5. Vendor
        CompactFormTextField(
            value = vendor,
            onValueChange = { vendor = it },
            label = "Vendor",
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
                        color = if (selected) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.7f),
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
                                color = if (selected) Color.White else WaterNavyDark,
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
                        color = if (selected) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.7f),
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
                                color = if (selected) Color.White else WaterNavyDark,
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
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
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
                    color = if (selected) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.7f),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clickable { paymentSource = src }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else WaterNavyDark, maxLines = 1, softWrap = false)
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
                    color = if (selected) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.7f),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clickable { paymentMethod = mode }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else WaterNavyDark, maxLines = 1, softWrap = false)
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
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
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
                            color = if (isSel) WaterNavyPrimary else WaterSkyLight,
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clickable { newCatType = t }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(t.name, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else WaterNavyDark)
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
                    colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
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
