package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import kotlin.math.abs

private enum class ApprovalFilter(val label: String) {
    ALL("All"),
    EXPENSES("Expenses"),
    PAYMENTS("Payments"),
    CASH_CLOSING("Cash Diff"),
    PASSWORDS("Passwords"),
    OTHER("Other")
}

@Composable
fun AdminApprovalsScreen(
    viewModel: WaterPlantViewModel
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val cashClosings by viewModel.cashClosings.collectAsState()
    val passwordResetRequests by viewModel.passwordResetRequests.collectAsState()
    val deliveries by viewModel.deliveries.collectAsState()
    val syncConflicts by viewModel.syncConflicts.collectAsState()

    val isAdmin = currentUser.role == UserRole.ADMIN
    val canApproveExpense = isAdmin || viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.APPROVE)
    val canApprovePayment = isAdmin || viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE)
    val canManageStaff = isAdmin || viewModel.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)

    val pendingExpenses = remember(expenses, canApproveExpense) {
        if (canApproveExpense) expenses.filter { !it.isAcknowledged } else emptyList()
    }
    val pendingPayments = remember(payments, canApprovePayment) {
        if (canApprovePayment) payments.filter { !it.isAcknowledged } else emptyList()
    }
    val pendingClosings = remember(cashClosings, canApprovePayment) {
        if (canApprovePayment) cashClosings.filter { it.isDifferencePendingApproval } else emptyList()
    }
    val pendingPasswords = remember(passwordResetRequests, canManageStaff) {
        if (canManageStaff) passwordResetRequests.filter { !it.isResolved } else emptyList()
    }
    val pendingDeliveries = remember(deliveries) {
        deliveries.filter { it.status == DeliveryStatus.PENDING || it.status == DeliveryStatus.ASSIGNED }
    }
    val unresolvedConflicts = remember(syncConflicts) {
        syncConflicts.filter { !it.resolved }
    }
    val otherCount = pendingDeliveries.size + unresolvedConflicts.size
    val totalPending = pendingExpenses.size + pendingPayments.size + pendingClosings.size + pendingPasswords.size + otherCount

    var selectedFilter by remember { mutableStateOf(ApprovalFilter.ALL) }

    // Confirmation dialog states
    var expenseToApprove by remember { mutableStateOf<Expense?>(null) }
    var paymentToApprove by remember { mutableStateOf<Payment?>(null) }
    var closingToApprove by remember { mutableStateOf<CashClosing?>(null) }
    var closingToReject by remember { mutableStateOf<CashClosing?>(null) }
    var passwordReqToResolve by remember { mutableStateOf<PasswordResetRequest?>(null) }
    var newPasswordInput by remember { mutableStateOf("123456") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface)
            .testTag("admin_approvals_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Summary Banner
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Admin Approval Center",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "اخراجات، پیمنٹس، کیش فرق، پاس ورڈ اور دیگر منظوریاں",
                                color = WaterCyanBright,
                                fontSize = 10.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (totalPending > 0) WarningOrange else SuccessGreen
                        ) {
                            Text(
                                text = "$totalPending Pending",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Filter Chips Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ApprovalFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        ApprovalFilter.ALL -> totalPending
                        ApprovalFilter.EXPENSES -> pendingExpenses.size
                        ApprovalFilter.PAYMENTS -> pendingPayments.size
                        ApprovalFilter.CASH_CLOSING -> pendingClosings.size
                        ApprovalFilter.PASSWORDS -> pendingPasswords.size
                        ApprovalFilter.OTHER -> otherCount
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        colors = waterFilterChipColors(),
                        label = {
                            Text(
                                text = "${filter.label} ($count)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("approval_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        if (totalPending == 0) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "All Clear",
                            tint = SuccessGreen,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "All Caught Up! (کوئی منظوری زیر التواء نہیں)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = WaterNavyPrimary
                        )
                        Text(
                            text = "All expenses, payments, cash closing differences, and password requests have been reviewed.",
                            fontSize = 10.5.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }

        // 1. Cash Closing Differences Section
        if ((selectedFilter == ApprovalFilter.ALL || selectedFilter == ApprovalFilter.CASH_CLOSING) && pendingClosings.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Cash Closing Differences (${pendingClosings.size})",
                    urduTitle = "کیش کلوزنگ کے فرق کی منظوری"
                )
            }
            items(pendingClosings, key = { "closing_${it.id}" }) { closing ->
                val isShortage = closing.difference < 0
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("approval_closing_${closing.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cash Closing • ${closing.date}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = WaterNavyPrimary
                                )
                                Text(
                                    text = "Closed by: ${closing.closedBy}",
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                            }
                            StatusBadge(
                                text = if (isShortage) "Shortage Rs.${abs(closing.difference).toInt()}" else "Surplus +Rs.${closing.difference.toInt()}",
                                backgroundColor = if (isShortage) ErrorRedLight else WarningAmberLight,
                                textColor = if (isShortage) ErrorRed else WarningAmber
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WaterSkyLight.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Expected: Rs. ${closing.expectedClosingCash.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WaterNavyDark)
                                Text("Actual Drawer: Rs. ${closing.actualClosingCash.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                            }
                        }

                        if (closing.notes.isNotBlank()) {
                            Text(
                                text = "Note: ${closing.notes}",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }

                        Text(
                            text = "Approve -> Next day opens with Drawer Cash (Rs. ${closing.actualClosingCash.toInt()}). Reject -> Next day opens with Expected Cash (Rs. ${closing.expectedClosingCash.toInt()}).",
                            fontSize = 9.5.sp,
                            color = TextMuted
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { closingToReject = closing },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                border = BorderStroke(1.dp, ErrorRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("btn_reject_closing_${closing.id}")
                            ) {
                                Text("Reject (مسترد)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Button(
                                onClick = { closingToApprove = closing },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("btn_approve_closing_${closing.id}")
                            ) {
                                Text("Approve (منظور)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // 2. Pending Expenses Section
        if ((selectedFilter == ApprovalFilter.ALL || selectedFilter == ApprovalFilter.EXPENSES) && pendingExpenses.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Pending Expenses (${pendingExpenses.size})",
                    urduTitle = "اخراجات کی تصدیق"
                )
            }
            items(pendingExpenses, key = { "exp_${it.id}" }) { exp ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("approval_expense_${exp.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${exp.categoryName} • ${exp.voucherNumber}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = exp.description.ifBlank { exp.payee.ifBlank { "Plant Expense" } },
                                fontSize = 10.sp,
                                color = TextSecondaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${exp.date} • By ${exp.enteredBy} • ${exp.expenseType.name}",
                                fontSize = 9.5.sp,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Rs. ${exp.amount.toInt()}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = ErrorRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { expenseToApprove = exp },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_approve_expense_${exp.id}")
                            ) {
                                Text("Verify", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Pending Payments Section
        if ((selectedFilter == ApprovalFilter.ALL || selectedFilter == ApprovalFilter.PAYMENTS) && pendingPayments.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Pending Payments (${pendingPayments.size})",
                    urduTitle = "وصولی کی تصدیق (Clear)"
                )
            }
            items(pendingPayments, key = { "pay_${it.id}" }) { pay ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("approval_payment_${pay.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pay.customerName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${pay.receiptNumber} • ${pay.paymentMethod.label}",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                            Text(
                                text = "${pay.date} • By ${pay.receivedBy}",
                                fontSize = 9.5.sp,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Rs. ${pay.amount.toInt()}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = SuccessGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { paymentToApprove = pay },
                                colors = waterPrimaryButtonColors(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_approve_payment_${pay.id}")
                            ) {
                                Text("Clear", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 4. Staff Password Reset Requests
        if ((selectedFilter == ApprovalFilter.ALL || selectedFilter == ApprovalFilter.PASSWORDS) && pendingPasswords.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Staff Password Resets (${pendingPasswords.size})",
                    urduTitle = "پاس ورڈ ری سیٹ کی درخواستیں"
                )
            }
            items(pendingPasswords, key = { "pwd_${it.id}" }) { req ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("approval_password_${req.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                        Button(
                            onClick = {
                                newPasswordInput = "123456"
                                passwordReqToResolve = req
                            },
                            colors = waterPrimaryButtonColors(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_reset_pwd_${req.id}")
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 5. Other Pending Approvals (Pending Deliveries & Sync Conflicts)
        if ((selectedFilter == ApprovalFilter.ALL || selectedFilter == ApprovalFilter.OTHER) && otherCount > 0) {
            item {
                SectionHeader(
                    title = "Other Pending Items ($otherCount)",
                    urduTitle = "دیگر زیر التواء کارروائیاں"
                )
            }
            items(pendingDeliveries, key = { "del_${it.id}" }) { del ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${del.deliveryNumber} • ${del.customerName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = WaterNavyPrimary
                            )
                            Text(
                                text = "${del.date} • Rs. ${del.totalAmount.toInt()} • Status: ${del.status.name}",
                                fontSize = 10.sp,
                                color = TextSecondaryDark
                            )
                        }
                        StatusBadge(
                            text = del.status.name,
                            backgroundColor = WarningAmberLight,
                            textColor = WarningAmber
                        )
                    }
                }
            }
            items(unresolvedConflicts, key = { "conf_${it.id}" }) { conflict ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sync Conflict: ${conflict.recordLabel.ifBlank { conflict.localSummary }}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ErrorRed
                            )
                            Text(
                                text = "${conflict.collection} • ${conflict.resolution}",
                                fontSize = 9.5.sp,
                                color = TextSecondaryDark
                            )
                        }
                        Button(
                            onClick = { viewModel.resolveSyncConflict(conflict.id) },
                            colors = waterPrimaryButtonColors(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Resolve", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialogs
    expenseToApprove?.let { exp ->
        AlertDialog(
            onDismissRequest = { expenseToApprove = null },
            containerColor = Color.White,
            title = { Text("Verify Expense?", fontWeight = FontWeight.Bold, color = WaterNavyPrimary) },
            text = {
                Text(
                    "Confirm verifying expense ${exp.voucherNumber} (${exp.categoryName}) for Rs. ${exp.amount.toInt()}?",
                    fontSize = 11.5.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.acknowledgeExpense(exp.id)
                        expenseToApprove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White)
                ) {
                    Text("Verify (منظور)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToApprove = null }) { Text("Cancel") }
            }
        )
    }

    paymentToApprove?.let { pay ->
        AlertDialog(
            onDismissRequest = { paymentToApprove = null },
            containerColor = Color.White,
            title = { Text("Mark Payment Clear?", fontWeight = FontWeight.Bold, color = WaterNavyPrimary) },
            text = {
                Text(
                    "Confirm marking payment ${pay.receiptNumber} from ${pay.customerName} (Rs. ${pay.amount.toInt()}) as Clear?",
                    fontSize = 11.5.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.acknowledgePayment(pay.id)
                        paymentToApprove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White)
                ) {
                    Text("Mark Clear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToApprove = null }) { Text("Cancel") }
            }
        )
    }

    closingToApprove?.let { closing ->
        AlertDialog(
            onDismissRequest = { closingToApprove = null },
            containerColor = Color.White,
            title = { Text("Approve Cash Difference?", fontWeight = FontWeight.Bold, color = WaterNavyPrimary) },
            text = {
                Text(
                    "Approving this difference (Rs. ${closing.difference.toInt()}) will settle the discrepancy and open the next day with Actual Drawer Cash: Rs. ${closing.actualClosingCash.toInt()}.",
                    fontSize = 11.5.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approveCashClosingDifference(closing.id)
                        closingToApprove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White)
                ) {
                    Text("Approve (منظور)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { closingToApprove = null }) { Text("Cancel") }
            }
        )
    }

    closingToReject?.let { closing ->
        AlertDialog(
            onDismissRequest = { closingToReject = null },
            containerColor = Color.White,
            title = { Text("Reject Cash Difference?", fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = {
                Text(
                    "Rejecting this difference keeps accountability on the system ledger. Next day will open with Expected Cash: Rs. ${closing.expectedClosingCash.toInt()} (past drawer + sales − OPEX).",
                    fontSize = 11.5.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rejectCashClosingDifference(closing.id)
                        closingToReject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Reject (مسترد)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { closingToReject = null }) { Text("Cancel") }
            }
        )
    }

    passwordReqToResolve?.let { req ->
        AlertDialog(
            onDismissRequest = { passwordReqToResolve = null },
            containerColor = Color.White,
            title = { Text("Reset Password for ${req.fullName}", fontWeight = FontWeight.Bold, color = WaterNavyPrimary, fontSize = 13.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Username: @${req.username} (${req.role.displayName})", fontSize = 11.sp, color = TextSecondaryDark)
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New Password (نیا پاس ورڈ)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pwd = newPasswordInput.trim().ifBlank { "123456" }
                        viewModel.resolvePasswordReset(req.id, pwd)
                        passwordReqToResolve = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White)
                ) {
                    Text("Confirm & Reset", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordReqToResolve = null }) { Text("Cancel") }
            }
        )
    }
}
