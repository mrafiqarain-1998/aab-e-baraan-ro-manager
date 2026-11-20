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
    var showBreakdown by remember { mutableStateOf(true) }

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
        }
        onReferenceDateChanged(sdf.format(cal.time))
    }

    val isProfit = report.netProfitOrLoss >= 0.0
    val netAccent = if (isProfit) SuccessGreen else ErrorRed
    val netBg = if (isProfit) SuccessGreenLight else ErrorRedLight

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
                        text = "Profit & Loss Dashboard",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = WaterNavyPrimary
                    )
                    Text(
                        text = "روزانہ، ہفتہ وار اور ماہانہ نفع و نقصان کا مکمل حساب",
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

            // Segmented Period Selector (Daily / Weekly / Monthly)
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
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = period.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else WaterNavyPrimary
                            )
                            Text(
                                text = period.urduLabel,
                                fontSize = 9.sp,
                                color = if (selected) WaterCyanBright else TextSecondaryDark
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
                        modifier = Modifier.size(38.dp)
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyPrimary
                        )
                        Text(
                            text = "${report.totalDeliveries} deliveries • ${report.totalUnitsSold} units sold",
                            fontSize = 9.5.sp,
                            color = TextSecondaryDark
                        )
                    }
                    IconButton(
                        onClick = { shiftDate(1) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Period",
                            tint = WaterNavyPrimary
                        )
                    }
                }
            }

            // Hero Net Profit / Loss Banner
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
                        Column {
                            Text(
                                text = if (isProfit) "NET PROFIT (خالص منافع)" else "NET LOSS (خالص نقصان)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = netAccent
                            )
                            Text(
                                text = "Sales Rs. ${formatPnlAmount(report.totalSalesRevenue)} − Cost Rs. ${formatPnlAmount(report.totalCost)}",
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

                    // Active Accounting Rules Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PnlRuleChip("Multi-Use Purchase: Rs. 0")
                        PnlRuleChip("CAPEX Excluded")
                        PnlRuleChip("Fixed ÷ ${report.daysInReferenceMonth}d")
                    }
                }
            }

            // 4 KPI Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialKpiCard(
                    title = "Sales Revenue",
                    urdu = "کل فروخت",
                    amount = "Rs. ${formatPnlAmount(report.totalSalesRevenue)}",
                    color = WaterBlueDeep,
                    bg = WaterSkyLight,
                    modifier = Modifier.weight(1f)
                )
                FinancialKpiCard(
                    title = "Bottle + Packing",
                    urdu = "بوتل، فلنگ و پیکنگ",
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
                FinancialKpiCard(
                    title = "OPEX Expenses",
                    urdu = "روزمرہ اخراجات (CAPEX نکال کر)",
                    amount = "Rs. ${formatPnlAmount(report.opexExpenses)}",
                    color = ErrorRed,
                    bg = ErrorRedLight,
                    modifier = Modifier.weight(1f)
                )
                FinancialKpiCard(
                    title = "Monthly Fixed Share",
                    urdu = "Rs. ${formatPnlAmount(report.dailyFixedCostRate)}/دن × ${report.daysInPeriod} دن",
                    amount = "Rs. ${formatPnlAmount(report.proratedMonthlyFixedCost)}",
                    color = WaterNavyPrimary,
                    bg = Color(0xFFEFF6FF),
                    modifier = Modifier.weight(1f)
                )
            }

            // Full Transparent Cost Formula Breakdown
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Cost & Profit Calculation Breakdown",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = WaterNavyPrimary
                    )
                    ReportRow(
                        label = "Sales Revenue (${report.totalDeliveries} deliveries)",
                        value = "Rs. ${formatPnlAmount(report.totalSalesRevenue)}",
                        isPositive = true
                    )
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    ReportRow(
                        label = "Single-Use Bottle Purchase (Multi-Use = Rs. 0)",
                        value = "- Rs. ${formatPnlAmount(report.singleUsePurchaseCost)}"
                    )
                    ReportRow(
                        label = "Bottle Filling Cost",
                        value = "- Rs. ${formatPnlAmount(report.fillingCost)}"
                    )
                    ReportRow(
                        label = "Packing Cost (Single / Packet / Carton)",
                        value = "- Rs. ${formatPnlAmount(report.packingCost)}"
                    )
                    if (report.otherBottleUnitCost > 0.0) {
                        ReportRow(
                            label = "Extra Bottle Costs (Cap/Label/Labour/Delivery)",
                            value = "- Rs. ${formatPnlAmount(report.otherBottleUnitCost)}"
                        )
                    }
                    ReportRow(
                        label = "OPEX Expenses (Salary, Maintenance, Fuel, etc.)",
                        value = "- Rs. ${formatPnlAmount(report.opexExpenses)}"
                    )
                    ReportRow(
                        label = "Monthly Fixed Share (Rs. ${formatPnlAmount(report.monthlyRecurringTotal)} ÷ ${report.daysInReferenceMonth} × ${report.daysInPeriod}d)",
                        value = "- Rs. ${formatPnlAmount(report.proratedMonthlyFixedCost)}"
                    )
                    if (report.excludedCapexExpenses > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CAPEX Expenses (Excluded from P&L)",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "Rs. ${formatPnlAmount(report.excludedCapexExpenses)} (Excluded)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    ReportRow(
                        label = "Total Period Cost",
                        value = "Rs. ${formatPnlAmount(report.totalCost)}",
                        isBold = true
                    )
                    ReportRow(
                        label = if (isProfit) "Net Profit (${selectedPeriod.label})" else "Net Loss (${selectedPeriod.label})",
                        value = "${if (isProfit) "+" else "-"}Rs. ${formatPnlAmount(abs(report.netProfitOrLoss))}",
                        isBold = true,
                        isPositive = isProfit,
                        isAlert = !isProfit
                    )
                }
            }

            // Sold Bottles Cost Breakdown
            if (report.bottleBreakdown.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBreakdown = !showBreakdown },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sold Bottles Cost Breakdown (${report.bottleBreakdown.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = WaterNavyPrimary
                    )
                    Icon(
                        imageVector = if (showBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Breakdown",
                        tint = WaterNavyPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showBreakdown) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        report.bottleBreakdown.forEach { line ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = line.bottleName,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WaterNavyPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = WaterNavyPrimary.copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    text = line.packingType.shortLabel,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = WaterNavyPrimary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                            if (line.isMultiTimeUse) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = SuccessGreenLight
                                                ) {
                                                    Text(
                                                        text = "Multi-Use",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = SuccessGreen,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${line.unitsSold} sold",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WaterBlueDeep
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Sale: Rs. ${formatPnlAmount(line.revenue)}",
                                            fontSize = 9.5.sp,
                                            color = TextSecondaryDark
                                        )
                                        Text(
                                            text = "Cost: Rs. ${formatPnlAmount(line.totalProductionCost)}",
                                            fontSize = 9.5.sp,
                                            color = ErrorRed
                                        )
                                        Text(
                                            text = "Margin: Rs. ${formatPnlAmount(line.grossMargin)}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (line.grossMargin >= 0) SuccessGreen else ErrorRed
                                        )
                                    }

                                    if (!line.hasConfiguredCosting) {
                                        Text(
                                            text = "Tip: Add costing rate below for exact ${line.packingType.label.lowercase()} filling & packing cost.",
                                            fontSize = 8.5.sp,
                                            color = WarningOrange
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No deliveries recorded in ${report.periodLabel}. Fixed & OPEX costs are still reflected above.",
                        fontSize = 10.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Share P&L Report
            OutlinedButton(
                onClick = {
                    val shareText = buildString {
                        appendLine("💧 *Aab-e-Baraan RO Plant - ${selectedPeriod.label} Profit & Loss* 💧")
                        appendLine("📅 *Period:* ${report.periodLabel}")
                        appendLine("────────────────────────")
                        appendLine("💰 *Sales Revenue:* Rs. ${formatPnlAmount(report.totalSalesRevenue)} (${report.totalDeliveries} deliveries)")
                        appendLine("🧴 *Single-Use Bottle Purchase:* Rs. ${formatPnlAmount(report.singleUsePurchaseCost)} (Multi-Use Excluded)")
                        appendLine("🚰 *Filling Cost:* Rs. ${formatPnlAmount(report.fillingCost)}")
                        appendLine("📦 *Packing Cost:* Rs. ${formatPnlAmount(report.packingCost)}")
                        if (report.otherBottleUnitCost > 0.0) {
                            appendLine("🏷️ *Other Bottle Costs:* Rs. ${formatPnlAmount(report.otherBottleUnitCost)}")
                        }
                        appendLine("⚙️ *OPEX Expenses:* Rs. ${formatPnlAmount(report.opexExpenses)} (CAPEX Excluded)")
                        appendLine("🏢 *Monthly Fixed Share:* Rs. ${formatPnlAmount(report.proratedMonthlyFixedCost)} (Rs. ${formatPnlAmount(report.dailyFixedCostRate)}/day × ${report.daysInPeriod}d)")
                        appendLine("────────────────────────")
                        appendLine("📉 *Total Cost:* Rs. ${formatPnlAmount(report.totalCost)}")
                        appendLine(
                            if (isProfit) "📈 *NET PROFIT:* +Rs. ${formatPnlAmount(report.netProfitOrLoss)}"
                            else "📉 *NET LOSS:* -Rs. ${formatPnlAmount(abs(report.netProfitOrLoss))}"
                        )
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share ${selectedPeriod.label} P&L Report"))
                },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WaterNavyPrimary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Share ${selectedPeriod.label} Profit & Loss Report",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaterNavyPrimary
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

private fun formatPnlAmount(value: Double): String {
    return if (abs(value - value.toLong().toDouble()) < 0.01) {
        String.format(Locale.ENGLISH, "%,d", value.toLong())
    } else {
        String.format(Locale.ENGLISH, "%,.2f", value)
    }
}
