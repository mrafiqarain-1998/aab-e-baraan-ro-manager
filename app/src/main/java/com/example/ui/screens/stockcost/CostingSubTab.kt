package com.example.ui.screens.stockcost

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.CompactDropdownField
import com.example.ui.components.MobileFormDialog
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostingSubTab(
    viewModel: WaterPlantViewModel,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Strict Admin Access Gate
    if (!isAdmin) {
        AccessDeniedCard()
        return
    }

    val bottleSizes by viewModel.bottleSizes.collectAsState()
    val bottleCostings by viewModel.bottleCostings.collectAsState()
    val monthlyRecurringExpenses by viewModel.monthlyRecurringExpenses.collectAsState()
    val deliveries by viewModel.deliveries.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val payments by viewModel.payments.collectAsState()

    var showAddCostingDialog by remember { mutableStateOf(false) }
    var costingToEdit by remember { mutableStateOf<BottleCostingRecord?>(null) }
    var showAddRecurringCostDialog by remember { mutableStateOf(false) }
    var costingToDelete by remember { mutableStateOf<BottleCostingRecord?>(null) }
    var recurringToDelete by remember { mutableStateOf<MonthlyRecurringExpense?>(null) }

    // Daily / Weekly / Monthly Profit & Loss Dashboard State
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedPnlPeriod by remember { mutableStateOf(CostingReportPeriod.DAILY) }
    var pnlReferenceDate by remember { mutableStateOf(todayDate) }

    val pnlReport = remember(
        selectedPnlPeriod,
        pnlReferenceDate,
        deliveries,
        expenses,
        bottleSizes,
        bottleCostings,
        monthlyRecurringExpenses
    ) {
        viewModel.getCostingProfitLossReport(selectedPnlPeriod, pnlReferenceDate)
    }

    // Month Selector for Financial Report & Recurring Costs
    val currentMonth = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }
    var selectedMonth by remember { mutableStateOf(currentMonth) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }

    // Financial summary for selected month
    val financialSummary = remember(selectedMonth, deliveries, payments, expenses, monthlyRecurringExpenses) {
        viewModel.getMonthlyFinancialSummary(selectedMonth)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("costing_sub_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Section Header Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Costing & Profit Matrix",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ErrorRed
                            ) {
                                Text(
                                    "ADMIN ONLY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "مکمل بوتل لاگت کا تخمینہ، ماہانہ اخراجات و خالص منافع",
                            color = WaterSkyLight.copy(alpha = 0.85f),
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }

        // =========================================================
        // DAILY / WEEKLY / MONTHLY PROFIT & LOSS DASHBOARD
        // =========================================================
        item {
            CostingProfitLossDashboard(
                selectedPeriod = selectedPnlPeriod,
                onPeriodSelected = { selectedPnlPeriod = it },
                referenceDate = pnlReferenceDate,
                onReferenceDateChanged = { newDate ->
                    pnlReferenceDate = newDate
                    if (newDate.length >= 7) {
                        selectedMonth = newDate.take(7)
                    }
                },
                report = pnlReport
            )
        }

        // =========================================================
        // SECTION 1: BOTTLE COSTING (SINGLE / PACKET / CARTON)
        // =========================================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Section 1: Bottle Costing Rates", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = WaterNavyPrimary)
                    Text("خرید، فلنگ، پیکنگ (سنگل/پیکٹ/کارٹن) اور فروخت ریٹ", fontSize = 10.sp, color = TextSecondaryDark)
                }

                Button(
                    onClick = {
                        costingToEdit = null
                        showAddCostingDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("add_bottle_costing_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Rate", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (bottleCostings.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No bottle costing rates added yet. Tap 'Add Rate' to set Purchase, Filling, Single/Packet/Carton Packing, and Sale rates for each bottle.",
                        fontSize = 10.5.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(bottleCostings, key = { it.id }) { costing ->
                val matchedSize = bottleSizes.find { it.id == costing.bottleSizeId }
                val isMultiUse = matchedSize?.let {
                    it.usageType == BottleUsageType.MULTIPLE_TIME || it.isReturnableBottle
                } ?: costing.isMultiTimeUse
                BottleCostingCard(
                    costing = costing,
                    isMultiTimeUse = isMultiUse,
                    onEditClick = {
                        costingToEdit = costing
                        showAddCostingDialog = true
                    },
                    onDeleteClick = { costingToDelete = costing }
                )
            }
        }

        // =========================================================
        // SECTION 2: OTHER MONTHLY COSTS (RECURRING)
        // =========================================================
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Section 2: Other Monthly Costs", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = WaterNavyPrimary)
                    Text("تنخواہیں، آر او کرایہ و مستقل ماہانہ فیکٹری اخراجات", fontSize = 10.sp, color = TextSecondaryDark)
                }

                Button(
                    onClick = { showAddRecurringCostDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningOrange, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Monthly Cost", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        val filteredMonthlyCosts = monthlyRecurringExpenses.filter {
            it.month == selectedMonth || it.date.startsWith(selectedMonth)
        }

        if (filteredMonthlyCosts.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No recurring monthly expenses recorded for $selectedMonth.",
                        fontSize = 10.5.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredMonthlyCosts, key = { it.id }) { cost ->
                RecurringCostRow(
                    cost = cost,
                    onDeleteClick = { recurringToDelete = cost }
                )
            }
        }

        // =========================================================
        // SECTION 3: MONTHLY SALES, EXPENSE & PROFIT REPORT
        // =========================================================
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Title and Month Picker Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Section 3: Monthly Financial Report", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = WaterNavyPrimary)
                            Text("سیلز، وصولیاں، کل اخراجات اور نیٹ منافع", fontSize = 10.sp, color = TextSecondaryDark)
                        }

                        // Compact Calendar Month Picker Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WaterNavyPrimary.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WaterNavyPrimary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showMonthPickerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Select Month",
                                    tint = WaterNavyPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = selectedMonth,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WaterNavyPrimary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = WaterNavyPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Main KPI Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinancialKpiCard(
                            title = "Total Sales",
                            urdu = "کل بلنگ سیلز",
                            amount = "Rs. ${financialSummary.totalSalesAmount.toInt()}",
                            color = WaterBlueDeep,
                            bg = WaterSkyLight,
                            modifier = Modifier.weight(1f)
                        )
                        FinancialKpiCard(
                            title = "Received Cash",
                            urdu = "موصولہ رقم",
                            amount = "Rs. ${financialSummary.receivedPayments.toInt()}",
                            color = SuccessGreen,
                            bg = SuccessGreenLight,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinancialKpiCard(
                            title = "Total Expenses",
                            urdu = "کل فیکٹری اخراجات",
                            amount = "Rs. ${financialSummary.totalExpenses.toInt()}",
                            color = ErrorRed,
                            bg = ErrorRedLight,
                            modifier = Modifier.weight(1f)
                        )
                        FinancialKpiCard(
                            title = "Net Profit (Cash)",
                            urdu = "خالص منافع",
                            amount = "Rs. ${financialSummary.netProfitCash.toInt()}",
                            color = if (financialSummary.netProfitCash >= 0) SuccessGreen else ErrorRed,
                            bg = if (financialSummary.netProfitCash >= 0) SuccessGreenLight else ErrorRedLight,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Detailed Breakdown Table
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Monthly Reconciliation Breakdown:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = WaterNavyPrimary)

                            ReportRow(label = "Total Deliveries Completed", value = "${financialSummary.totalDeliveries} orders")
                            ReportRow(label = "Total Invoiced Sales Revenue", value = "Rs. ${financialSummary.totalSalesAmount.toInt()}")
                            ReportRow(label = "Cash / Online Received Amount", value = "Rs. ${financialSummary.receivedPayments.toInt()}", isPositive = true)
                            ReportRow(label = "Pending Customer Outstanding", value = "Rs. ${financialSummary.outstandingSalesAmount.toInt()}", isAlert = true)
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            ReportRow(label = "Plant Operational Expenses (Daily OPEX)", value = "Rs. ${financialSummary.operationalExpenses.toInt()}")
                            ReportRow(label = "Other Monthly Fixed Costs (Rent/Salaries)", value = "Rs. ${financialSummary.otherMonthlyCosts.toInt()}")
                            ReportRow(label = "Total Factory Outflows (All Expenses)", value = "Rs. ${financialSummary.totalExpenses.toInt()}", isBold = true)
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            ReportRow(
                                label = "Net Accrual Profit (Sales - Expenses)",
                                value = "Rs. ${financialSummary.netProfitAccrual.toInt()}",
                                isBold = true,
                                isPositive = financialSummary.netProfitAccrual >= 0
                            )
                            ReportRow(
                                label = "Net Cash Profit (Received - Expenses)",
                                value = "Rs. ${financialSummary.netProfitCash.toInt()}",
                                isBold = true,
                                isPositive = financialSummary.netProfitCash >= 0
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // WhatsApp Share Report Button
                    Button(
                        onClick = {
                            val reportText = buildString {
                                appendLine("💧 *Aab-e-Baraan RO Water Plant - Financial Report* 💧")
                                appendLine("📅 *Month:* $selectedMonth")
                                appendLine("────────────────────────")
                                appendLine("📦 *Total Deliveries:* ${financialSummary.totalDeliveries}")
                                appendLine("💰 *Total Invoiced Sales:* Rs. ${financialSummary.totalSalesAmount.toInt()}")
                                appendLine("💵 *Received Payments:* Rs. ${financialSummary.receivedPayments.toInt()}")
                                appendLine("⏳ *Outstanding Balance:* Rs. ${financialSummary.outstandingSalesAmount.toInt()}")
                                appendLine("────────────────────────")
                                appendLine("⚙️ *Operational Expenses:* Rs. ${financialSummary.operationalExpenses.toInt()}")
                                appendLine("🏢 *Monthly Fixed Costs:* Rs. ${financialSummary.otherMonthlyCosts.toInt()}")
                                appendLine("📉 *Total Expenses:* Rs. ${financialSummary.totalExpenses.toInt()}")
                                appendLine("────────────────────────")
                                appendLine("📈 *Net Cash Profit:* Rs. ${financialSummary.netProfitCash.toInt()}")
                                appendLine("📊 *Net Accrual Profit:* Rs. ${financialSummary.netProfitAccrual.toInt()}")
                                appendLine("────────────────────────")
                                appendLine("Generated via Plant Admin System")
                            }

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, reportText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Monthly Financial Summary"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 46.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Monthly Summary via WhatsApp", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // Add / Edit Bottle Costing Dialog
    if (showAddCostingDialog) {
        AddBottleCostingDialog(
            bottleSizes = bottleSizes.filter { it.active },
            existingRecord = costingToEdit,
            onDismiss = {
                showAddCostingDialog = false
                costingToEdit = null
            },
            onSave = { record ->
                viewModel.saveBottleCosting(record) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    if (success) {
                        showAddCostingDialog = false
                        costingToEdit = null
                    }
                }
            }
        )
    }

    // Add Recurring Monthly Cost Dialog
    if (showAddRecurringCostDialog) {
        AddRecurringCostDialog(
            currentMonth = selectedMonth,
            onDismiss = { showAddRecurringCostDialog = false },
            onSave = { cost ->
                viewModel.addMonthlyRecurringExpense(cost) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showAddRecurringCostDialog = false
                }
            }
        )
    }

    // Delete Costing Record Dialog
    costingToDelete?.let { costing ->
        AlertDialog(
            onDismissRequest = { costingToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            title = { Text("Delete Costing Record", fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = { Text("Are you sure you want to delete costing matrix for ${costing.bottleSizeName}?", color = TextPrimaryDark) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBottleCosting(costing.id) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            costingToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { costingToDelete = null }) { Text("Cancel", color = TextSecondaryDark) }
            }
        )
    }

    // Delete Recurring Monthly Cost Dialog
    recurringToDelete?.let { cost ->
        AlertDialog(
            onDismissRequest = { recurringToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            title = { Text("Delete Monthly Cost", fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = { Text("Delete '${cost.description}' (Rs. ${cost.amount.toInt()})?", color = TextPrimaryDark) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMonthlyRecurringExpense(cost.id) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            recurringToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { recurringToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Month Picker Modal Dialog
    if (showMonthPickerDialog) {
        MonthPickerDialogComposable(
            initialMonth = selectedMonth,
            onDismiss = { showMonthPickerDialog = false },
            onMonthSelected = { newMonth ->
                selectedMonth = newMonth
                showMonthPickerDialog = false
            }
        )
    }
}

// -------------------------------------------------------------
// BOTTLE COSTING CARD
// -------------------------------------------------------------
@Composable
fun BottleCostingCard(
    costing: BottleCostingRecord,
    isMultiTimeUse: Boolean = costing.isMultiTimeUse,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit
) {
    val effectiveCost = costing.effectiveUnitCostForPnl(isMultiTimeUse)
    val effectiveProfit = costing.salePrice - effectiveCost

    Card(
        shape = RoundedCornerShape(14.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(costing.bottleSizeName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WaterNavyPrimary)
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = WaterNavyPrimary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = if (costing.packQty > 1) "${costing.packingType.shortLabel} (${costing.packQty})" else costing.packingType.label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isMultiTimeUse) {
                        Text(
                            text = "Multi-Time Reusable • Bottle Purchase Excluded in P&L",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessGreen
                        )
                    } else {
                        Text("Updated: ${costing.date} • By: ${costing.updatedBy}", fontSize = 9.5.sp, color = TextSecondaryDark)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Costing", tint = WaterBlueDeep, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cost vs Profit Summary Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WaterSkyLight,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("P&L Cost", fontSize = 9.5.sp, color = TextMuted)
                        Text("Rs. ${String.format(Locale.ENGLISH, "%.2f", effectiveCost)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WaterNavyPrimary)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sale Rate", fontSize = 9.5.sp, color = TextMuted)
                        Text("Rs. ${String.format(Locale.ENGLISH, "%.2f", costing.salePrice)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimaryDark)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (effectiveProfit >= 0) SuccessGreenLight else ErrorRedLight,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Unit Margin", fontSize = 9.5.sp, color = if (effectiveProfit >= 0) SuccessGreen else ErrorRed)
                        Text("Rs. ${String.format(Locale.ENGLISH, "%.2f", effectiveProfit)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (effectiveProfit >= 0) SuccessGreen else ErrorRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Component Cost Details Grid
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isMultiTimeUse) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bottle Purchase Rate (Reusable)", fontSize = 10.sp, color = TextMuted)
                            Text(
                                text = if (costing.bottlePurchaseRate > 0.0) "Rs. ${String.format(Locale.ENGLISH, "%.0f", costing.bottlePurchaseRate)} (Excluded)" else "Rs. 0.00 (Excluded)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen
                            )
                        }
                    } else {
                        ComponentCostText("Bottle Purchase Rate", costing.bottlePurchaseRate)
                    }
                    ComponentCostText("Filling Rate", costing.fillingRate)
                    ComponentCostText("${costing.packingType.label} Packing Rate", costing.packingRate)
                    ComponentCostText("Label & Shrink Seal", costing.labelSealCalculatedCost)
                    ComponentCostText("Carton / Polythene Wrap", costing.cartonPolytheneCost)
                    ComponentCostText("Bottle Cap", costing.capCost)
                    ComponentCostText("Water Filtration & Raw Water", costing.waterCost)
                    ComponentCostText("Labour Allocation", costing.labourCost)
                    ComponentCostText("Electricity & Power", costing.electricityCost)
                    ComponentCostText("Delivery & Fuel Transport", costing.deliveryCost)
                }
            }
        }
    }
}

@Composable
fun ComponentCostText(name: String, cost: Double) {
    if (cost > 0.0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, fontSize = 10.sp, color = TextSecondaryDark)
            Text("Rs. ${String.format(Locale.ENGLISH, "%.2f", cost)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
        }
    }
}

// -------------------------------------------------------------
// ADD / EDIT BOTTLE COSTING DIALOG (SINGLE / PACKET / CARTON)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBottleCostingDialog(
    bottleSizes: List<BottleSize>,
    existingRecord: BottleCostingRecord? = null,
    onDismiss: () -> Unit,
    onSave: (BottleCostingRecord) -> Unit
) {
    var selectedSize by remember {
        mutableStateOf(
            existingRecord?.let { rec -> bottleSizes.find { it.id == rec.bottleSizeId } }
                ?: bottleSizes.firstOrNull()
        )
    }

    fun defaultPackingForSize(size: BottleSize?): CostingPackingType {
        if (size == null) return CostingPackingType.SINGLE
        return if (size.packQuantity > 1) CostingPackingType.PACKET else CostingPackingType.SINGLE
    }

    fun defaultPackQty(size: BottleSize?, mode: CostingPackingType): Int {
        if (mode == CostingPackingType.SINGLE) return 1
        val cap = size?.capacityLitres ?: 0.0
        return when {
            mode == CostingPackingType.CARTON && cap in 0.4..0.6 -> 24
            mode == CostingPackingType.PACKET && cap in 0.4..0.6 -> 12
            mode == CostingPackingType.CARTON && cap in 1.4..1.6 -> 12
            mode == CostingPackingType.PACKET && cap in 1.4..1.6 -> 6
            else -> (size?.packQuantity ?: 1).coerceAtLeast(1)
        }
    }

    var packingType by remember {
        mutableStateOf(existingRecord?.packingType ?: defaultPackingForSize(selectedSize))
    }
    var bottlePurchaseRate by remember {
        mutableStateOf(
            existingRecord?.bottlePurchaseRate?.toString()
                ?: (selectedSize?.latestPurchaseRate?.toString() ?: "0")
        )
    }
    var fillingRate by remember {
        mutableStateOf(
            existingRecord?.fillingRate?.toString()
                ?: (selectedSize?.refillCost?.takeIf { it > 0.0 }?.toString() ?: "0")
        )
    }
    var packingRate by remember {
        mutableStateOf(existingRecord?.packingRate?.toString() ?: "0")
    }
    var packQty by remember {
        mutableStateOf(
            existingRecord?.packQty?.toString()
                ?: defaultPackQty(selectedSize, packingType).toString()
        )
    }
    var salePrice by remember {
        mutableStateOf(
            existingRecord?.salePrice?.toString()
                ?: (selectedSize?.defaultRefillPrice?.toString() ?: "0")
        )
    }
    var notes by remember { mutableStateOf(existingRecord?.notes ?: "") }

    // Optional extra components (collapsed by default for clean, fast entry)
    var showOptionalExtras by remember {
        mutableStateOf(existingRecord != null && existingRecord.extraComponentCost > 0.0)
    }
    var labelPacketPrice by remember { mutableStateOf(existingRecord?.labelSealPacketPrice?.toString() ?: "0") }
    var labelTotalPieces by remember { mutableStateOf((existingRecord?.labelSealTotalPieces ?: 1).coerceAtLeast(1).toString()) }
    var labelPiecesUsed by remember { mutableStateOf((existingRecord?.labelSealPiecesUsedPerPack ?: 0).toString()) }
    var cartonPolytheneCost by remember { mutableStateOf(existingRecord?.cartonPolytheneCost?.toString() ?: "0") }
    var capCost by remember { mutableStateOf(existingRecord?.capCost?.toString() ?: "0") }
    var waterCost by remember { mutableStateOf(existingRecord?.waterCost?.toString() ?: "0") }
    var labourCost by remember { mutableStateOf(existingRecord?.labourCost?.toString() ?: "0") }
    var electricityCost by remember { mutableStateOf(existingRecord?.electricityCost?.toString() ?: "0") }
    var deliveryCost by remember { mutableStateOf(existingRecord?.deliveryCost?.toString() ?: "0") }

    val isMultiUse = selectedSize?.let {
        it.usageType == BottleUsageType.MULTIPLE_TIME || it.isReturnableBottle
    } ?: false

    val packetPriceVal = labelPacketPrice.toDoubleOrNull() ?: 0.0
    val totalPiecesVal = (labelTotalPieces.toIntOrNull() ?: 1).coerceAtLeast(1)
    val piecesUsedVal = labelPiecesUsed.toIntOrNull() ?: 0
    val calculatedLabelSealCost = if (packetPriceVal > 0.0 && piecesUsedVal > 0) {
        (packetPriceVal / totalPiecesVal) * piecesUsedVal
    } else 0.0

    val bRate = bottlePurchaseRate.toDoubleOrNull() ?: 0.0
    val effectivePurchaseInPnl = if (isMultiUse) 0.0 else bRate
    val fillVal = fillingRate.toDoubleOrNull() ?: 0.0
    val packVal = packingRate.toDoubleOrNull() ?: 0.0
    val cartonCost = cartonPolytheneCost.toDoubleOrNull() ?: 0.0
    val cap = capCost.toDoubleOrNull() ?: 0.0
    val water = waterCost.toDoubleOrNull() ?: 0.0
    val labour = labourCost.toDoubleOrNull() ?: 0.0
    val electric = electricityCost.toDoubleOrNull() ?: 0.0
    val delivery = deliveryCost.toDoubleOrNull() ?: 0.0
    val sPrice = salePrice.toDoubleOrNull() ?: 0.0

    val calculatedTotalCost = effectivePurchaseInPnl + fillVal + packVal + calculatedLabelSealCost + cartonCost + cap + water + labour + electric + delivery
    val calculatedProfit = sPrice - calculatedTotalCost

    val unitSuffix = when (packingType) {
        CostingPackingType.SINGLE -> "Per Bottle"
        CostingPackingType.PACKET -> "Per Packet"
        CostingPackingType.CARTON -> "Per Carton"
    }

    MobileFormDialog(
        title = if (existingRecord == null) "Add Bottle Costing Rate" else "Edit Bottle Costing Rate",
        subtitle = "خرید، فلنگ، پیکنگ (سنگل / پیکٹ / کارٹن) اور فروخت ریٹ",
        hasUnsavedChanges = notes.isNotBlank() || fillVal > 0.0 || packVal > 0.0,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val size = selectedSize ?: return@Button
                    val record = BottleCostingRecord(
                        id = existingRecord?.id ?: "",
                        bottleSizeId = size.id,
                        bottleSizeName = size.name,
                        packingType = packingType,
                        isMultiTimeUse = isMultiUse,
                        bottlePurchaseRate = bRate,
                        fillingRate = fillVal,
                        packingRate = packVal,
                        packQty = (packQty.toIntOrNull() ?: 1).coerceAtLeast(1),
                        labelSealPacketPrice = packetPriceVal,
                        labelSealTotalPieces = totalPiecesVal,
                        labelSealPiecesUsedPerPack = piecesUsedVal,
                        labelSealCalculatedCost = calculatedLabelSealCost,
                        cartonPolytheneCost = cartonCost,
                        capCost = cap,
                        waterCost = water,
                        labourCost = labour,
                        electricityCost = electric,
                        deliveryCost = delivery,
                        totalCostPerPackOrBottle = calculatedTotalCost,
                        salePrice = sPrice,
                        profitPerPackOrBottle = calculatedProfit,
                        date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                        notes = notes.trim()
                    )
                    onSave(record)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                enabled = selectedSize != null && sPrice > 0.0,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (existingRecord == null) "Save Rate" else "Update Rate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        CompactDropdownField(
            label = "Select Bottle Size*",
            selectedText = selectedSize?.name ?: "",
            options = bottleSizes,
            optionLabel = { size ->
                val tag = if (size.usageType == BottleUsageType.MULTIPLE_TIME || size.isReturnableBottle) "Multi-Use" else "Single-Use"
                "${size.name} ($tag)"
            },
            onOptionSelected = { size ->
                selectedSize = size
                val newMode = defaultPackingForSize(size)
                packingType = newMode
                packQty = defaultPackQty(size, newMode).toString()
                bottlePurchaseRate = size.latestPurchaseRate.toString()
                fillingRate = size.refillCost.takeIf { it > 0.0 }?.toString() ?: "0"
                salePrice = size.defaultRefillPrice.toString()
            }
        )

        // Packing Mode Selector: Single Bottle / Packet / Carton
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Packing Type (پیکنگ کی قسم)*",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CostingPackingType.values().forEach { mode ->
                    val isSel = mode == packingType
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) WaterNavyPrimary else Color(0xFFF1F5F9),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                packingType = mode
                                packQty = defaultPackQty(selectedSize, mode).toString()
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = mode.shortLabel,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else WaterNavyPrimary
                            )
                            Text(
                                text = mode.urduLabel,
                                fontSize = 8.5.sp,
                                color = if (isSel) WaterCyanBright else TextSecondaryDark
                            )
                        }
                    }
                }
            }
        }

        if (isMultiUse) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SuccessGreenLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Autorenew, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Multi-Time Reusable Bottle: Purchase rate is NOT counted in Daily/Weekly/Monthly P&L (بار بار استعمال ہونے والی بوتل کی خرید لاگت نفع/نقصان میں شامل نہیں ہوگی)",
                        fontSize = 9.5.sp,
                        color = SuccessGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Core 4 Rates Grid: Purchase, Filling, Packing (Single/Packet/Carton), Sale
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = bottlePurchaseRate,
                onValueChange = { bottlePurchaseRate = it },
                label = { Text(if (isMultiUse) "Purchase (Ref Only)" else "Purchase ($unitSuffix)", fontSize = 9.5.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = fillingRate,
                onValueChange = { fillingRate = it },
                label = { Text("Filling Rate ($unitSuffix)*", fontSize = 9.5.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = packingRate,
                onValueChange = { packingRate = it },
                label = { Text("${packingType.shortLabel} Packing Rate*", fontSize = 9.5.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = salePrice,
                onValueChange = { salePrice = it },
                label = { Text("Sale Rate ($unitSuffix)*", fontSize = 9.5.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        if (packingType != CostingPackingType.SINGLE) {
            OutlinedTextField(
                value = packQty,
                onValueChange = { packQty = it },
                label = { Text("Bottles inside 1 ${packingType.label} (بوتلوں کی تعداد)", fontSize = 10.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Collapsible Optional Extra Components
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF8FAFC),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showOptionalExtras = !showOptionalExtras }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Optional Extra Costs (Label, Cap, Labour, Delivery)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WaterNavyPrimary
                )
                Icon(
                    imageVector = if (showOptionalExtras) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = WaterNavyPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (showOptionalExtras) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Label & Seal Formula: (Pkt Price / Total Pcs) × Used",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = labelPacketPrice,
                            onValueChange = { labelPacketPrice = it },
                            label = { Text("Pkt Rs", fontSize = 9.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = labelTotalPieces,
                            onValueChange = { labelTotalPieces = it },
                            label = { Text("Total Pcs", fontSize = 9.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = labelPiecesUsed,
                            onValueChange = { labelPiecesUsed = it },
                            label = { Text("Used", fontSize = 9.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cartonPolytheneCost,
                            onValueChange = { cartonPolytheneCost = it },
                            label = { Text("Extra Wrap", fontSize = 9.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = capCost,
                            onValueChange = { capCost = it },
                            label = { Text("Cap Cost", fontSize = 9.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = waterCost,
                            onValueChange = { waterCost = it },
                            label = { Text("Water", fontSize = 9.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = labourCost,
                            onValueChange = { labourCost = it },
                            label = { Text("Labour", fontSize = 9.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = electricityCost,
                            onValueChange = { electricityCost = it },
                            label = { Text("Electricity", fontSize = 9.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = deliveryCost,
                            onValueChange = { deliveryCost = it },
                            label = { Text("Delivery", fontSize = 9.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Live P&L Cost & Margin Preview
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = WaterSkyLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Cost in P&L ($unitSuffix):", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                    Text("Rs. ${String.format(Locale.ENGLISH, "%.2f", calculatedTotalCost)}", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterNavyPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Expected Unit Margin:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (calculatedProfit >= 0) SuccessGreen else ErrorRed)
                    Text("Rs. ${String.format(Locale.ENGLISH, "%.2f", calculatedProfit)}", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = if (calculatedProfit >= 0) SuccessGreen else ErrorRed)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ADD RECURRING COST DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringCostDialog(
    currentMonth: String,
    onDismiss: () -> Unit,
    onSave: (MonthlyRecurringExpense) -> Unit
) {
    var category by remember { mutableStateOf(MonthlyCostCategory.STAFF_SALARIES) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var month by remember { mutableStateOf(currentMonth) }
    var notes by remember { mutableStateOf("") }

    MobileFormDialog(
        title = "Add Monthly Cost",
        subtitle = "ماہانہ تنخواہیں، آر او کرایہ و مرمت",
        accentColor = WarningOrange,
        hasUnsavedChanges = description.isNotBlank() || amount.isNotBlank(),
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0 && description.isNotBlank()) {
                        onSave(
                            MonthlyRecurringExpense(
                                category = category,
                                description = description.trim(),
                                amount = amt,
                                month = month.trim(),
                                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                                notes = notes.trim()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0.0 && description.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Cost", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        CompactDropdownField(
            label = "Expense Category*",
            selectedText = category.label,
            options = MonthlyCostCategory.values().toList(),
            optionLabel = { it.label },
            onOptionSelected = { category = it }
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description / Details*", fontSize = 10.5.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (Rs.)*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = month,
            onValueChange = { month = it },
            label = { Text("Month (YYYY-MM)*", fontSize = 10.5.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes / Payment Reference", fontSize = 10.5.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

// -------------------------------------------------------------
// RECURRING COST ROW
// -------------------------------------------------------------
@Composable
fun RecurringCostRow(
    cost: MonthlyRecurringExpense,
    onDeleteClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cost.category.label, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = WaterNavyPrimary)
                Text(cost.description, fontSize = 10.5.sp, color = TextPrimaryDark)
                Text("${cost.date} • Month: ${cost.month}", fontSize = 9.5.sp, color = TextMuted)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Rs. ${cost.amount.toInt()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = ErrorRed
                )
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// FINANCIAL KPI CARD
// -------------------------------------------------------------
@Composable
fun FinancialKpiCard(
    title: String,
    urdu: String,
    amount: String,
    color: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(amount, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = color)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = color)
            Text(urdu, fontSize = 9.sp, color = TextMuted)
        }
    }
}

@Composable
fun ReportRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    isPositive: Boolean = false,
    isAlert: Boolean = false
) {
    val color = when {
        isAlert -> ErrorRed
        isPositive -> SuccessGreen
        isBold -> WaterNavyPrimary
        else -> TextPrimaryDark
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = if (isBold) 12.sp else 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = if (isBold) WaterNavyPrimary else TextSecondaryDark
        )
        Text(
            value,
            fontSize = if (isBold) 13.sp else 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = color
        )
    }
}

// -------------------------------------------------------------
// ACCESS DENIED VIEW
// -------------------------------------------------------------
@Composable
fun AccessDeniedCard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Access Denied (صرف ایڈمن کی رسائی)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = ErrorRed)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "The Costing and Financial Profit module is strictly restricted to the Admin role. Please switch to an authorized Admin profile to access factory costing and net profit calculations.",
                    fontSize = 10.5.sp,
                    color = TextPrimaryDark,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MonthPickerDialogComposable(
    initialMonth: String,
    onDismiss: () -> Unit,
    onMonthSelected: (String) -> Unit
) {
    val parts = initialMonth.split("-")
    var selectedYear by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 2026) }
    val currentSelectedMonthNum = parts.getOrNull(1)?.toIntOrNull() ?: 1

    val months = listOf(
        1 to "Jan", 2 to "Feb", 3 to "Mar", 4 to "Apr",
        5 to "May", 6 to "Jun", 7 to "Jul", 8 to "Aug",
        9 to "Sep", 10 to "Oct", 11 to "Nov", 12 to "Dec"
    )

    MobileFormDialog(
        title = "Select Report Month",
        subtitle = "ماہانہ مالیاتی رپورٹ کا مہینہ منتخب کریں",
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { selectedYear-- }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year", tint = WaterNavyPrimary)
            }
            Text(
                text = "$selectedYear",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = WaterNavyPrimary
            )
            IconButton(onClick = { selectedYear++ }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Year", tint = WaterNavyPrimary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (chunk in months.chunked(3)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunk.forEach { (mNum, mName) ->
                        val isCurrentSelected = (selectedYear == (parts.getOrNull(0)?.toIntOrNull() ?: 2026) && mNum == currentSelectedMonthNum)
                        Button(
                            onClick = {
                                val formatted = String.format(Locale.US, "%04d-%02d", selectedYear, mNum)
                                onMonthSelected(formatted)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = if (isCurrentSelected) {
                                ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White)
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = WaterNavyPrimary.copy(alpha = 0.08f),
                                    contentColor = WaterNavyPrimary
                                )
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = mName,
                                fontSize = 10.5.sp,
                                fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
