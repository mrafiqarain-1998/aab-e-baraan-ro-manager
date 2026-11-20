package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CashClosing
import com.example.model.CashClosingSummary
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DailyCashClosingScreen(
    viewModel: WaterPlantViewModel,
    onClosingSubmitted: () -> Unit
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val paymentsList by viewModel.payments.collectAsState()
    val expensesList by viewModel.expenses.collectAsState()
    val cashSummary: CashClosingSummary = remember(today, paymentsList, expensesList) {
        viewModel.getDailyCashSummary(today)
    }
    val previousClosings by viewModel.cashClosings.collectAsState()

    var actualCashText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val actualCash = actualCashText.toDoubleOrNull() ?: 0.0
    val difference = actualCash - cashSummary.expectedClosingCash
    val isBalanced = kotlin.math.abs(difference) < 0.01
    val isShortage = difference < -0.01
    val isSurplus = difference > 0.01

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header Card with Formula Explanation
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily Cash Closing Drawer",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "روزانہ کیش گنتی اور حساب بندش",
                                color = WaterCyanBright,
                                fontSize = 10.5.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WaterBlueDeep
                        ) {
                            Text(
                                text = today,
                                color = WaterSkyLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Formula: Daily Net = Daily Received − OPEX Expenses (Pay from Sale only)",
                        color = WaterSkyLight.copy(alpha = 0.9f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 1.5 Daily Received vs OPEX (From Sale) Net Summary Card
        item {
            val isDailyPositive = cashSummary.dailyNetReceivedAfterOpex >= 0.0
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily Received vs OPEX (From Sale)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = WaterNavyPrimary
                            )
                            Text(
                                text = "روزانہ وصولی منفی صرف آپریٹنگ (سیل سے) اخراجات",
                                fontSize = 9.5.sp,
                                color = TextSecondaryDark
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WaterSkyLight
                        ) {
                            Text(
                                text = "OPEX • SALE ONLY",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    CalculationRow(
                        label = "Daily Received Amount (آج کی کل وصولی)",
                        amount = cashSummary.totalReceivedAmount,
                        isPositive = true,
                        icon = Icons.Default.Payments,
                        color = SuccessGreen
                    )

                    if (cashSummary.digitalCollected > 0.0) {
                        Text(
                            text = "   Cash: Rs. ${cashSummary.customerCashCollected.toInt()}  •  Online/Bank: Rs. ${cashSummary.digitalCollected.toInt()}",
                            fontSize = 9.5.sp,
                            color = TextMuted
                        )
                    }

                    CalculationRow(
                        label = "- Daily Expenses (OPEX • From Sale Only)",
                        amount = cashSummary.opexFromSaleExpenses,
                        isPositive = false,
                        icon = Icons.Default.RemoveCircle,
                        color = ErrorRed
                    )

                    if (cashSummary.excludedCapexExpenses > 0.0 || cashSummary.excludedInvestmentExpenses > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val parts = buildList {
                                if (cashSummary.excludedCapexExpenses > 0.0) {
                                    add("CAPEX: Rs. ${cashSummary.excludedCapexExpenses.toInt()}")
                                }
                                if (cashSummary.excludedInvestmentExpenses > 0.0) {
                                    add("Investment: Rs. ${cashSummary.excludedInvestmentExpenses.toInt()}")
                                }
                            }.joinToString(" • ")
                            Text(
                                text = "Excluded from Daily Closing: $parts",
                                fontSize = 9.5.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Net Daily Balance (Received − Expense)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = WaterNavyPrimary
                            )
                            Text(
                                text = "آج کی خالص بچت (وصولی منفی OPEX سیل خرچ)",
                                fontSize = 9.5.sp,
                                color = TextSecondaryDark
                            )
                        }
                        Text(
                            text = "${if (isDailyPositive) "" else "-"}Rs. ${kotlin.math.abs(cashSummary.dailyNetReceivedAfterOpex).toInt()}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp,
                            color = if (isDailyPositive) SuccessGreen else ErrorRed
                        )
                    }
                }
            }
        }

        // 2. The Cash Breakdown Ledger Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Cash Drawer Closing (دراز کیش حساب)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = WaterNavyPrimary
                    )

                    CalculationRow(
                        label = "Opening Drawer Cash (صبح کا کیش)",
                        amount = cashSummary.openingCash,
                        isPositive = true,
                        icon = Icons.Default.LockClock,
                        color = WaterNavyPrimary
                    )

                    CalculationRow(
                        label = "+ Cash Collections (گاہکوں سے نقد)",
                        amount = cashSummary.customerCashCollected,
                        isPositive = true,
                        icon = Icons.Default.AddCircle,
                        color = SuccessGreen
                    )

                    CalculationRow(
                        label = "- Cash OPEX Expenses (From Sale Only)",
                        amount = cashSummary.cashExpenses,
                        isPositive = false,
                        icon = Icons.Default.RemoveCircle,
                        color = ErrorRed
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Expected Drawer Cash",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = WaterNavyPrimary
                            )
                            Text(
                                text = "حساب کے مطابق دراز میں کیش ہونا چاہیے",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }

                        Text(
                            text = "Rs. ${cashSummary.expectedClosingCash.toInt()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = WaterNavyPrimary
                        )
                    }
                }
            }
        }

        // 3. Counted Cash Entry & Difference Indicator
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Physical Cash Count (گن کر لکھا گیا کیش)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = WaterNavyPrimary
                    )

                    OutlinedTextField(
                        value = actualCashText,
                        onValueChange = { actualCashText = it },
                        label = { Text("Actual Cash in Hand (دراز میں موجود کل رقم)*") },
                        placeholder = { Text("Enter physical notes counted") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = WaterNavyPrimary,
                            unfocusedBorderColor = BorderLight
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { actualCashText = cashSummary.expectedClosingCash.toInt().toString() }) {
                                Text("Matches Expected", fontSize = 9.5.sp, color = WaterAqua)
                            }
                        }
                    )

                    // Difference Banner
                    if (actualCashText.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isBalanced -> SuccessGreenLight
                                isShortage -> ErrorRedLight
                                else -> WarningAmberLight
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when {
                                            isBalanced -> Icons.Default.CheckCircle
                                            isShortage -> Icons.Default.Warning
                                            else -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            isBalanced -> SuccessGreen
                                            isShortage -> ErrorRed
                                            else -> WarningAmber
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = when {
                                                isBalanced -> "Balanced! Cash drawer matches 100%"
                                                isShortage -> "Shortage (کیش میں کمی ہے!)"
                                                else -> "Surplus (کیش میں زیادتی ہے)"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = when {
                                                isBalanced -> SuccessGreen
                                                isShortage -> ErrorRed
                                                else -> WarningAmber
                                            }
                                        )
                                        Text(
                                            text = when {
                                                isBalanced -> "حساب بالکل برابر ہے"
                                                isShortage -> "Check if any expense or refund was unrecorded"
                                                else -> "Check if any payment was recorded incorrectly"
                                            },
                                            fontSize = 10.sp,
                                            color = TextSecondaryDark
                                        )
                                    }
                                }

                                Text(
                                    text = if (isBalanced) "Rs. 0" else "Rs. ${difference.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = when {
                                        isBalanced -> SuccessGreen
                                        isShortage -> ErrorRed
                                        else -> WarningAmber
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Closing Notes / Reason for Difference (نوٹس)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = WaterNavyPrimary,
                            unfocusedBorderColor = BorderLight
                        )
                    )

                    Button(
                        onClick = {
                            if (actualCashText.isNotEmpty()) {
                                viewModel.submitCashClosing(cashSummary, actualCash, notes)
                                onClosingSubmitted()
                            }
                        },
                        colors = waterPrimaryButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = actualCashText.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Submit Cash Closing & Lock Day",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 4. Past Closing Records
        item {
            SectionHeader(
                title = "Past Closing Records",
                urduTitle = "گزشتہ دنوں کی بندش"
            )
        }

        if (previousClosings.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No previous closing records yet. Today's submission will appear here.",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        } else {
            items(previousClosings) { close ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = close.date,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = WaterNavyPrimary
                            )
                            Text(
                                text = "Closed by: ${close.closedBy}",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Rs. ${close.actualClosingCash.toInt()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextPrimaryDark
                            )
                            StatusBadge(
                                text = if (close.isBalanced) "Balanced" else "Diff: ${close.difference.toInt()}",
                                backgroundColor = if (close.isBalanced) SuccessGreenLight else ErrorRedLight,
                                textColor = if (close.isBalanced) SuccessGreen else ErrorRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculationRow(
    label: String,
    amount: Double,
    isPositive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 10.5.sp, color = TextSecondaryDark)
        }

        Text(
            text = "Rs. ${amount.toInt()}",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
