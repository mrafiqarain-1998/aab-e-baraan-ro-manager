package com.example.ui.screens.stockcost

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Unified Profit & Loss + Financial Report Dashboard (Section 1 of Costing Module).
 * Supports Day / Week / Month / Year filters with detailed financial breakdown below.
 * Note: Sold Bottle Breakdown has been intentionally removed per specification.
 */
@Composable
fun CostingProfitLossDashboard(
    selectedPeriod: CostingReportPeriod,
    onPeriodSelected: (CostingReportPeriod) -> Unit,
    referenceDate: String,
    onReferenceDateChanged: (String) -> Unit,
    report: CostingProfitLossReport,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var showOpBreakdown by remember { mutableStateOf(true) }

    fun shiftDate(step: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = try {
            sdf.parse(referenceDate) ?: Date()
        } catch (_: Exception) {
            Date()
        }
        val cal = Calendar.getInstance().apply { time = parsed }
        when (selectedPeriod) {
            CostingReportPeriod.DAILY -> cal.add(Calendar.DAY_OF_YEAR, step)
            CostingReportPeriod.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, step * 7)
            CostingReportPeriod.MONTHLY -> cal.add(Calendar.MONTH, step)
            CostingReportPeriod.YEARLY -> cal.add(Calendar.YEAR, step)
        }
        onReferenceDateChanged(sdf.format(cal.time))
    }

    val isProfit = report.netProfitOrLoss >= 0.0
    val netAccent = if (isProfit) SuccessGreen else ErrorRed
    val netBg = if (isProfit) SuccessGreenLight else ErrorRedLight
    val staffAndOpTotal = if (report.totalOperationalAndStaffCost > 0.0 || report.activeStaffCount > 0 || report.monthlyOperationalCostsTotal > 0.0) {
        report.totalOperationalAndStaffCost
    } else {
        report.opexExpenses + report.proratedMonthlyFixedCost
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("costing_pnl_dashboard")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Profit & Loss Financial Dashboard",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = WaterNavyPrimary
                    )
                    Text(
                        text = "روزانہ، ہفتہ وار، ماہانہ اور سالانہ مالیاتی رپورٹ و نفع نقصان",
                        fontSize = 10.sp,
                        color = TextSecondaryDark
                    )
                }
                if (referenceDate != todayStr) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WaterNavyPrimary.copy(alpha = 0.08f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onReferenceDateChanged(todayStr) }
                            .testTag("pnl_reset_today_btn")
                    ) {
                        Text(
                            text = "Today",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Segmented Period Selector (Day / Week / Month / Year)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CostingReportPeriod.values().forEach { period ->
                    val selected = period == selectedPeriod
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) WaterNavyPrimary else Color(0xFFF1F5F9),
                        border = if (selected) null else BorderStroke(1.dp, BorderLight),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("pnl_period_${period.name.lowercase(Locale.ENGLISH)}")
                            .clickable { onPeriodSelected(period) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = period.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else WaterNavyPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = period.urduLabel,
                                fontSize = 8.5.sp,
                                color = if (selected) WaterCyanBright else TextSecondaryDark,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Date / Period Navigator
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WaterSkySurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { shiftDate(-1) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("pnl_prev_period_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Period",
                            tint = WaterNavyPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = report.periodLabel,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyPrimary
                        )
                        Text(
                            text = "${report.totalDeliveries} deliveries • ${report.totalUnitsSold} units • ${report.daysInPeriod}d",
                            fontSize = 9.5.sp,
                            color = TextSecondaryDark
                        )
                    }
                    IconButton(
                        onClick = { shiftDate(1) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("pnl_next_period_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Period",
                            tint = WaterNavyPrimary
                        )
                    }
                }
            }

            // Hero Final Net Profit / Loss Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = netBg,
                border = BorderStroke(1.dp, netAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isProfit) "FINAL NET PROFIT (خالص منافع)" else "FINAL NET LOSS (خالص نقصان)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = netAccent
                            )
                            Text(
                                text = "Sales Rs. ${formatPnlAmount(report.totalSalesRevenue)} − Total Cost Rs. ${formatPnlAmount(report.totalCost)}",
                                fontSize = 9.5.sp,
                                color = TextSecondaryDark
                            )
                        }
                        Text(
                            text = "${if (isProfit) "+" else "-"}Rs. ${formatPnlAmount(abs(report.netProfitOrLoss))}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = netAccent
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PnlRuleChip("Auto Staff: ${report.activeStaffCount}")
                        PnlRuleChip("Actual > Budget")
                        PnlRuleChip("Daily Share ÷ ${report.daysInReferenceMonth}d")
                    }
                }
            }

            // 4 KPI Summary Cards (2x2 Grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialKpiCard(
                    title = "Sales Revenue",
                    urdu = "وصول: Rs. ${formatPnlAmount(report.receivedPayments)}",
                    amount = "Rs. ${formatPnlAmount(report.totalSalesRevenue)}",
                    color = WaterBlueDeep,
                    bg = WaterSkyLight,
                    modifier = Modifier.weight(1f)
                )
                FinancialKpiCard(
                    title = "Bottle Material Cost",
                    urdu = "پیکٹ / بوتل لاگت",
                    amount = "Rs. ${formatPnlAmount(report.totalBottleAndPackingCost)}",
                    color = WarningOrange,
                    bg = WarningAmberLight,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val preOpPositive = report.profitBeforeOperationalCosts >= 0.0
                FinancialKpiCard(
                    title = "Profit Before OpEx",
                    urdu = "مٹیریل لاگت کے بعد منافع",
                    amount = "${if (preOpPositive) "" else "-"}Rs. ${formatPnlAmount(abs(report.profitBeforeOperationalCosts))}",
                    color = if (preOpPositive) SuccessGreen else ErrorRed,
                    bg = if (preOpPositive) SuccessGreenLight else ErrorRedLight,
                    modifier = Modifier.weight(1f)
                )
                FinancialKpiCard(
                    title = "Staff + Op Costs",
                    urdu = "تنخواہیں + ماہانہ اخراجات",
                    amount = "Rs. ${formatPnlAmount(staffAndOpTotal)}",
                    color = WaterNavyPrimary,
                    bg = Color(0xFFEFF6FF),
                    modifier = Modifier.weight(1f)
                )
            }

            // Detailed Financial Breakdown Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = "Detailed Financial Breakdown (${selectedPeriod.label})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.5.sp,
                        color = WaterNavyPrimary
                    )

                    // Sales & Collections Block
                    ReportRow(
                        label = "Sales Revenue (${report.totalDeliveries} deliveries)",
                        value = "Rs. ${formatPnlAmount(report.totalSalesRevenue)}",
                        isPositive = true
                    )
                    ReportRow(
                        label = "Received Payments (Cash & Online)",
                        value = "Rs. ${formatPnlAmount(report.receivedPayments)}"
                    )
                    if (report.outstandingSalesAmount > 0.0) {
                        ReportRow(
                            label = "Pending Receivable Balance",
                            value = "Rs. ${formatPnlAmount(report.outstandingSalesAmount)}",
                            isAlert = true
                        )
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Step 1: Bottle Material Cost -> Profit Before OpEx
                    ReportRow(
                        label = "Bottle & Packaging Material Cost",
                        value = "- Rs. ${formatPnlAmount(report.totalBottleAndPackingCost)}"
                    )
                    ReportRow(
                        label = "Profit Before Operational Expenses",
                        value = "${if (report.profitBeforeOperationalCosts >= 0.0) "" else "-"}Rs. ${formatPnlAmount(abs(report.profitBeforeOperationalCosts))}",
                        isBold = true,
                        isPositive = report.profitBeforeOperationalCosts >= 0.0,
                        isAlert = report.profitBeforeOperationalCosts < 0.0
                    )

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Step 2: Allocated Staff Salaries & Monthly Operational Costs
                    ReportRow(
                        label = "Auto Staff Salaries (${report.activeStaffCount} staff • Rs. ${formatPnlAmount(report.dailyStaffSalaryRate)}/d)",
                        value = "- Rs. ${formatPnlAmount(report.allocatedStaffSalaryCost)}"
                    )
                    ReportRow(
                        label = "Monthly Operational Costs (Rs. ${formatPnlAmount(report.dailyOperationalCostRate)}/d)",
                        value = "- Rs. ${formatPnlAmount(report.allocatedOperationalCost)}"
                    )

                    // 6-Item Operational Cost Breakdown (Actual vs Budget indicator)
                    if (report.operationalCostBreakdown.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showOpBreakdown = !showOpBreakdown }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Operational Cost Details (${report.operationalCostBreakdown.size} items)",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterBlueDeep
                            )
                            Icon(
                                imageVector = if (showOpBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle operational cost details",
                                tint = WaterBlueDeep,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (showOpBreakdown) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                report.operationalCostBreakdown.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "• ${item.label}",
                                                fontSize = 9.5.sp,
                                                color = TextSecondaryDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (item.effectiveMonthlyAmount > 0.0) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (item.isUsingActual) SuccessGreenLight else Color(0xFFE2E8F0)
                                                ) {
                                                    Text(
                                                        text = if (item.isUsingActual) "Actual" else "Budget",
                                                        fontSize = 7.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (item.isUsingActual) SuccessGreen else TextSecondaryDark,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Rs. ${formatPnlAmount(item.allocatedPeriodAmount)}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimaryDark
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    ReportRow(
                        label = "Total Period Cost",
                        value = "Rs. ${formatPnlAmount(report.totalCost)}",
                        isBold = true
                    )
                    ReportRow(
                        label = if (isProfit) "Final Net Profit (${selectedPeriod.label})" else "Final Net Loss (${selectedPeriod.label})",
                        value = "${if (isProfit) "+" else "-"}Rs. ${formatPnlAmount(abs(report.netProfitOrLoss))}",
                        isBold = true,
                        isPositive = isProfit,
                        isAlert = !isProfit
                    )
                }
            }

            // Share Unified Profit & Loss Report
            Button(
                onClick = {
                    val shareText = buildString {
                        appendLine("💧 *Aab-e-Baraan RO Plant - ${selectedPeriod.label} Profit & Loss* 💧")
                        appendLine("📅 *Period:* ${report.periodLabel} (${report.daysInPeriod}d)")
                        appendLine("────────────────────────")
                        appendLine("💰 *Sales Revenue:* Rs. ${formatPnlAmount(report.totalSalesRevenue)} (${report.totalDeliveries} deliveries)")
                        appendLine("💵 *Received Payments:* Rs. ${formatPnlAmount(report.receivedPayments)}")
                        if (report.outstandingSalesAmount > 0.0) {
                            appendLine("⏳ *Pending Receivable:* Rs. ${formatPnlAmount(report.outstandingSalesAmount)}")
                        }
                        appendLine("────────────────────────")
                        appendLine("📦 *Bottle & Packaging Cost:* -Rs. ${formatPnlAmount(report.totalBottleAndPackingCost)}")
                        appendLine("📊 *Profit Before OpEx:* Rs. ${formatPnlAmount(report.profitBeforeOperationalCosts)}")
                        appendLine("👷 *Staff Salaries Share (${report.activeStaffCount} staff):* -Rs. ${formatPnlAmount(report.allocatedStaffSalaryCost)}")
                        appendLine("🏢 *Monthly Operational Share:* -Rs. ${formatPnlAmount(report.allocatedOperationalCost)}")
                        appendLine("────────────────────────")
                        appendLine("📉 *Total Period Cost:* Rs. ${formatPnlAmount(report.totalCost)}")
                        appendLine(
                            if (isProfit) "📈 *FINAL NET PROFIT:* +Rs. ${formatPnlAmount(report.netProfitOrLoss)}"
                            else "📉 *FINAL NET LOSS:* -Rs. ${formatPnlAmount(abs(report.netProfitOrLoss))}"
                        )
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share ${selectedPeriod.label} Profit & Loss"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 46.dp)
                    .testTag("share_pnl_report_btn")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Share ${selectedPeriod.label} Financial Report",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PnlRuleChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.85f)
    ) {
        Text(
            text = text,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = WaterNavyPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

internal fun formatPnlAmount(value: Double): String {
    return if (abs(value - value.toLong().toDouble()) < 0.01) {
        String.format(Locale.ENGLISH, "%,d", value.toLong())
    } else {
        String.format(Locale.ENGLISH, "%,.2f", value)
    }
}
