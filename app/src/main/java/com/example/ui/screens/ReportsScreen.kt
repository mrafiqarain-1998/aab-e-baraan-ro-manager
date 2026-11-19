package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PdfPreviewDialog
import com.example.ui.components.PdfPreviewRequest
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel

enum class ReportPeriod(val label: String, val urduLabel: String) {
    DAILY("Daily Report", "روزانہ رپورٹ"),
    WEEKLY("Weekly Report", "ہفتہ وار رپورٹ"),
    MONTHLY("Monthly Report", "ماہانہ رپورٹ")
}

@Composable
fun ReportsScreen(
    viewModel: WaterPlantViewModel
) {
    val context = LocalContext.current
    val stats by viewModel.dashboardStats.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isViewOnly = !viewModel.hasPermission(com.example.model.AppModule.REPORTS, com.example.model.PermissionAction.VIEW) ||
        (currentUser.role == com.example.model.UserRole.VIEW_ONLY &&
            !viewModel.hasPermission(com.example.model.AppModule.REPORTS, com.example.model.PermissionAction.MANAGE) &&
            !viewModel.hasPermission(com.example.model.AppModule.REPORTS, com.example.model.PermissionAction.ADD))

    var selectedPeriod by remember { mutableStateOf(ReportPeriod.DAILY) }
    var previewPdfRequest by remember { mutableStateOf<PdfPreviewRequest?>(null) }

    fun shareReport(text: String, title: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title)
        context.startActivity(shareIntent)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Period Selector Tabs
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Business Reports & WhatsApp Sharing",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "روزانہ، ہفتہ وار اور ماہانہ تفصیلی رپورٹ واٹس ایپ پر شیئر کریں",
                        color = WaterCyanBright,
                        fontSize = 10.5.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportPeriod.entries.forEach { period ->
                            val isSelected = selectedPeriod == period
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) WaterCyanBright else WaterBlueDeep,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedPeriod = period }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = period.label.split(" ")[0],
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = period.urduLabel,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 9.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // WhatsApp Share Banner for Selected Period
        item {
            val reportText = when (selectedPeriod) {
                ReportPeriod.DAILY -> viewModel.generateDailyReportSummary()
                ReportPeriod.WEEKLY -> viewModel.generateWeeklyReportSummary()
                ReportPeriod.MONTHLY -> viewModel.generateMonthlyReportSummary()
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Column {
                        Text(
                            text = selectedPeriod.label + " Preview (رپورٹ کا جائزہ)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = WaterNavyPrimary
                        )
                        Text(
                            text = "Customer-wise deliveries, income & expenses",
                            fontSize = 10.5.sp,
                            color = TextSecondaryDark
                        )
                    }

                    if (!isViewOnly) {
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { shareReport(reportText, "Share ${selectedPeriod.label}") },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("WhatsApp", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val pdfFile = com.example.util.PdfGenerator.generateSalesReportPdf(
                                        context = context,
                                        title = "Aab-e-Baraan ${selectedPeriod.label} Executive Report",
                                        periodLabel = selectedPeriod.label,
                                        stats = stats
                                    )
                                    previewPdfRequest = PdfPreviewRequest(
                                        file = pdfFile,
                                        title = "${selectedPeriod.label} PDF",
                                        chooserTitle = "Share PDF Report (${selectedPeriod.label})",
                                        whatsAppCaption = "Aab-e-Baraan ${selectedPeriod.label} Executive Report"
                                    )
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = WaterNavyPrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF رپورٹ", color = WaterNavyPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Textual Preview Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WaterSkyLight.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = reportText,
                            fontSize = 10.5.sp,
                            color = TextPrimaryDark,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Financial Snapshot
        item {
            SectionHeader(
                title = "Financial Overview",
                urduTitle = "آمدن اور اخراجات کا تقابل"
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportLine(label = "Billed Deliveries (سیلز)", amount = stats.todaySales, isPositive = true, color = WaterNavyPrimary)
                    ReportLine(label = "Cash In Drawer (نقد وصولی)", amount = stats.todayCashCollected, isPositive = true, color = SuccessGreen)
                    ReportLine(label = "Online / Bank Income (آن لائن)", amount = stats.todayDigitalCollected, isPositive = true, color = WaterAqua)
                    ReportLine(label = "Operating Expenses (اخراجات)", amount = stats.todayExpenses, isPositive = false, color = ErrorRed)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = DividerColor)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Net Operating Profit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = WaterNavyPrimary
                            )
                            Text(
                                text = "خالص بچت",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }

                        Text(
                            text = "Rs. ${stats.estimatedProfit.toInt()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (stats.estimatedProfit >= 0) SuccessGreen else ErrorRed
                        )
                    }
                }
            }
        }

        // Outstanding Receivables List
        item {
            SectionHeader(
                title = "Market Receivables (ادھار تفصیل)",
                urduTitle = "کس گاہک پر کتنا ادھار ہے"
            )
        }

        val debtCustomers = customers.filter { it.currentBalance > 0 }.sortedByDescending { it.currentBalance }
        items(debtCustomers.size) { index ->
            val cust = debtCustomers[index]
            Card(
                shape = RoundedCornerShape(10.dp),
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
                            text = cust.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = WaterNavyPrimary
                        )
                        Text(
                            text = "${cust.area} • Empties: ${cust.emptyBottlesOwed}",
                            fontSize = 10.sp,
                            color = TextSecondaryDark
                        )
                    }

                    Text(
                        text = "Rs. ${cust.currentBalance.toInt()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ErrorRed
                    )
                }
            }
        }
    }

    previewPdfRequest?.let { req ->
        PdfPreviewDialog(
            request = req,
            onDismiss = { previewPdfRequest = null }
        )
    }
}

@Composable
fun ReportLine(label: String, amount: Double, isPositive: Boolean, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.5.sp, color = TextSecondaryDark)
        Text(
            text = (if (isPositive) "" else "- ") + "Rs. ${amount.toInt()}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
