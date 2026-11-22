package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.CompactFormTextField
import com.example.ui.components.EditMetadataBadge
import com.example.ui.components.EditableDateField
import com.example.ui.components.MobileFormDialog
import com.example.ui.components.NewItemBadge
import com.example.ui.components.PdfPreviewDialog
import com.example.ui.components.PdfPreviewRequest
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import com.example.viewmodel.WaterPlantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivePaymentScreen(
    viewModel: WaterPlantViewModel,
    preSelectedCustomer: Customer? = null,
    onPaymentCompleted: () -> Unit
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val seenRecordKeys by viewModel.seenRecordKeys.collectAsState()
    val allPayments by viewModel.payments.collectAsState()
    val isAdmin = viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE) ||
        viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.DELETE)

    // Start with null if no customer was passed so user can select from pending dues list
    var selectedCustomer by remember { mutableStateOf(preSelectedCustomer) }
    var customerSearchQuery by remember { mutableStateOf("") }
    var showOnlyDueFilter by remember { mutableStateOf(true) }

    var paymentAmountText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var notes by remember { mutableStateOf("") }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }
    var selectedPaymentForDetail by remember { mutableStateOf<Payment?>(null) }
    var paymentToEdit by remember { mutableStateOf<Payment?>(null) }
    val canEditPayment = viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.EDIT)
    val canDeletePayment = viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.DELETE)

    // Dialog state for viewing/sharing a receipt slip
    var receiptToShare by remember { mutableStateOf<Payment?>(null) }
    var previewPdfRequest by remember { mutableStateOf<PdfPreviewRequest?>(null) }

    // Map invoiceId -> allocated amount
    val allocations = remember { mutableStateMapOf<String, Double>() }
    // Checked pending invoices for multi-invoice selection
    var selectedInvoiceIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Fetch unpaid invoices for selected customer
    val unpaidInvoices = remember(selectedCustomer, viewModel.invoices.collectAsState().value, viewModel.deliveries.collectAsState().value) {
        if (selectedCustomer != null) {
            val invs = viewModel.getUnpaidInvoicesForCustomer(selectedCustomer!!.id).toMutableList()
            val existingDelIds = invs.map { it.deliveryId }.toSet()
            val unpaidDels = viewModel.deliveries.value.filter {
                it.customerId == selectedCustomer!!.id &&
                (it.paymentMethod == "Unpaid" || it.paymentCollected < it.totalAmount) &&
                it.id !in existingDelIds
            }
            for (del in unpaidDels) {
                val due = del.totalAmount - del.paymentCollected
                if (due > 0) {
                    invs.add(
                        Invoice(
                            id = del.invoiceId.ifEmpty { "inv_${del.id}" },
                            invoiceNumber = del.deliveryNumber.replace("DEL-", "INV-"),
                            customerId = del.customerId,
                            customerName = del.customerName,
                            deliveryId = del.id,
                            date = del.date,
                            dueDate = del.date,
                            totalAmount = del.totalAmount,
                            paidAmount = del.paymentCollected,
                            balanceDue = due,
                            status = InvoiceStatus.UNPAID,
                            itemsSummary = del.items.joinToString(", ") { "${it.quantity}x ${it.bottleName}" }
                        )
                    )
                }
            }
            invs.sortedBy { it.date }
        } else emptyList()
    }

    // Allocate amount across checked invoices (or FIFO across all unpaid invoices if none checked)
    fun distributeAmountAcrossInvoices(totalAmount: Double, checkedIds: Set<String>) {
        allocations.clear()
        val targets = if (checkedIds.isNotEmpty()) {
            unpaidInvoices.filter { it.id in checkedIds }
        } else {
            unpaidInvoices
        }
        var remaining = totalAmount
        for (inv in targets) {
            if (remaining <= 0) break
            val toPay = kotlin.math.min(remaining, inv.balanceDue)
            if (toPay > 0.0) {
                allocations[inv.id] = toPay
                remaining -= toPay
            }
        }
    }

    // Apply checkbox selection: auto-sums checked pending invoices and updates amount + allocations
    fun applyCheckedInvoices(newChecked: Set<String>) {
        selectedInvoiceIds = newChecked
        allocations.clear()
        val checkedInvs = unpaidInvoices.filter { it.id in newChecked }
        val checkedSum = checkedInvs.sumOf { it.balanceDue }
        checkedInvs.forEach { inv ->
            if (inv.balanceDue > 0.0) allocations[inv.id] = inv.balanceDue
        }
        paymentAmountText = if (checkedSum > 0.0) checkedSum.toInt().toString() else ""
    }

    // Helper to calculate total due (taking into account customer balance, unpaid invoices, and unpaid deliveries)
    fun getCustomerDue(cust: Customer): Double {
        return viewModel.getCustomerTotalDue(cust)
    }

    // Initialize amount / checkboxes on customer change
    LaunchedEffect(selectedCustomer, unpaidInvoices.size) {
        if (selectedCustomer != null) {
            when {
                unpaidInvoices.size == 1 -> {
                    val onlyInv = unpaidInvoices.first()
                    selectedInvoiceIds = setOf(onlyInv.id)
                    allocations.clear()
                    allocations[onlyInv.id] = onlyInv.balanceDue
                    paymentAmountText = onlyInv.balanceDue.toInt().toString()
                }
                unpaidInvoices.size > 1 -> {
                    // Multi-invoice pending: let user check boxes to dynamically total selected invoices
                    selectedInvoiceIds = emptySet()
                    allocations.clear()
                    paymentAmountText = ""
                }
                else -> {
                    selectedInvoiceIds = emptySet()
                    allocations.clear()
                    val balance = getCustomerDue(selectedCustomer!!)
                    paymentAmountText = if (balance > 0) balance.toInt().toString() else ""
                }
            }
        } else {
            selectedInvoiceIds = emptySet()
            allocations.clear()
            paymentAmountText = ""
        }
    }

    val totalEnteredAmount = paymentAmountText.toDoubleOrNull() ?: 0.0
    val totalAllocated = allocations.values.sum()
    val selectedInvoicesTotal = unpaidInvoices.filter { it.id in selectedInvoiceIds }.sumOf { it.balanceDue }
    val unallocatedExcess = (totalEnteredAmount - totalAllocated).coerceAtLeast(0.0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // -------------------------------------------------------------
        // 1. CUSTOMER SELECTION / PENDING DUES LIST
        // -------------------------------------------------------------
        if (selectedCustomer == null) {
            item {
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
                            Column {
                                Text(
                                    text = "Receive Cash (پیمنٹ وصولی)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WaterNavyDark
                                )
                                Text(
                                    text = "جس گاہک سے رقم وصول کرنی ہے اسے منتخب کریں",
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                            }

                            val pendingDueCustomers = customers.filter { getCustomerDue(it) > 0 }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ErrorRedLight
                            ) {
                                Text(
                                    text = "${pendingDueCustomers.size} Pending Dues",
                                    color = ErrorRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Customer Search Bar
                        OutlinedTextField(
                            value = customerSearchQuery,
                            onValueChange = { customerSearchQuery = it },
                            placeholder = { Text("Search name, phone, area (گاہک تلاش کریں)...", fontSize = 10.5.sp, color = TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (customerSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { customerSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondaryDark, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("payment_customer_search"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark,
                                focusedBorderColor = WaterNavyPrimary,
                                unfocusedBorderColor = BorderLight
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Filter Row (Pending Dues vs All Customers)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val dueCount = customers.count { getCustomerDue(it) > 0 }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (showOnlyDueFilter) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clickable { showOnlyDueFilter = true }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "Pending Dues ($dueCount)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showOnlyDueFilter) Color.White else WaterNavyDark
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (!showOnlyDueFilter) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clickable { showOnlyDueFilter = false }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "All Customers (${customers.size})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!showOnlyDueFilter) Color.White else WaterNavyDark
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Customer List for Selection
            val matchingCustomers = customers.filter { cust ->
                val matchesQuery = customerSearchQuery.isEmpty() ||
                        cust.name.contains(customerSearchQuery, ignoreCase = true) ||
                        cust.phone.contains(customerSearchQuery) ||
                        cust.area.contains(customerSearchQuery, ignoreCase = true) ||
                        cust.customerCode.contains(customerSearchQuery, ignoreCase = true)
                val matchesDue = !showOnlyDueFilter || getCustomerDue(cust) > 0
                matchesQuery && matchesDue
            }

            if (matchingCustomers.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (showOnlyDueFilter) "No customers with pending dues found! (کوئی واجب الادا رقم نہیں)"
                                else "No matching customers found.",
                                color = TextMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            } else {
                items(matchingCustomers) { cust ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCustomer = cust }
                            .testTag("select_pay_cust_${cust.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (cust.currentBalance > 0) WarningOrangeLight else SuccessGreenLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (cust.currentBalance > 0) WarningOrange else SuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = cust.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = WaterNavyDark
                                    )
                                    Text(
                                        text = "${cust.area} • ${cust.phone}",
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                val due = getCustomerDue(cust)
                                if (due > 0) {
                                    Text(
                                        text = "Due: Rs. ${due.toInt()}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ErrorRed
                                    )
                                } else {
                                    Text(
                                        text = "Clear (0)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = WaterNavyPrimary,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "Receive >",
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // -------------------------------------------------------------
            // SELECTED CUSTOMER HEADER & PAYMENT FORM
            // -------------------------------------------------------------
            val cust = selectedCustomer!!
            item {
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
                            Text(
                                text = "Customer Account (منتخب گاہک)",
                                fontSize = 10.5.sp,
                                color = TextSecondaryDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = { selectedCustomer = null },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp), tint = WaterNavyPrimary)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Change (تبدیل)", fontSize = 10.sp, color = WaterNavyPrimary, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = WaterSkyLight.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(WaterNavyPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cust.name,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WaterNavyDark
                                    )
                                    Text(
                                        text = "${cust.customerCode} • ${cust.phone} • ${cust.area}",
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark
                                    )
                                    val due = getCustomerDue(cust)
                                    Text(
                                        text = "Current Due: Rs. ${due.toInt()}",
                                        fontSize = 11.5.sp,
                                        color = if (due > 0) ErrorRed else SuccessGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Amount and Payment Method
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Payment Amount & Mode (وصولی کی رقم و طریقہ)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = WaterNavyPrimary
                        )

                        OutlinedTextField(
                            value = paymentAmountText,
                            onValueChange = {
                                paymentAmountText = it
                                val amt = it.toDoubleOrNull() ?: 0.0
                                distributeAmountAcrossInvoices(amt, selectedInvoiceIds)
                            },
                            label = { Text("Payment Received (وصول شدہ رقم)") },
                            placeholder = { Text("Tick invoices below or enter amount in Rs.") },
                            leadingIcon = {
                                Text("Rs.", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WaterNavyPrimary, modifier = Modifier.padding(start = 12.dp, end = 4.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark,
                                focusedBorderColor = WaterNavyPrimary,
                                unfocusedBorderColor = BorderLight
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("payment_amount_input")
                        )

                        // Quick Fill Buttons
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val totalDueForCust = getCustomerDue(cust)
                            if (totalDueForCust > 0) {
                                OutlinedButton(
                                    onClick = {
                                        if (unpaidInvoices.isNotEmpty()) {
                                            applyCheckedInvoices(unpaidInvoices.map { it.id }.toSet())
                                            if (totalDueForCust > unpaidInvoices.sumOf { it.balanceDue }) {
                                                paymentAmountText = totalDueForCust.toInt().toString()
                                            }
                                        } else {
                                            paymentAmountText = totalDueForCust.toInt().toString()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Text("Full Balance (Rs. ${totalDueForCust.toInt()})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedInvoiceIds = emptySet()
                                    paymentAmountText = ""
                                    allocations.clear()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(0.5f).height(34.dp)
                            ) {
                                Text("Clear", fontSize = 10.sp)
                            }
                        }

                        // Payment Methods
                        Text("Payment Mode (طریقہ کار):", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                PaymentMethod.CASH,
                                PaymentMethod.BANK_TRANSFER,
                                PaymentMethod.JAZZCASH,
                                PaymentMethod.EASYPAISA
                            ).forEach { method ->
                                val isSelected = paymentMethod == method
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clickable { paymentMethod = method }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = when (method) {
                                                PaymentMethod.CASH -> "Cash (نقد)"
                                                PaymentMethod.BANK_TRANSFER -> "Online"
                                                PaymentMethod.JAZZCASH -> "JazzCash"
                                                PaymentMethod.EASYPAISA -> "Easypaisa"
                                                else -> method.label
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else WaterNavyDark
                                        )
                                    }
                                }
                            }
                        }

                        // Notes Field
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Payment Notes / Reference (اختیاری نوٹ)", fontSize = 10.sp) },
                            placeholder = { Text("e.g. Paid via mobile app or cash receipt", fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark,
                                focusedBorderColor = WaterNavyPrimary,
                                unfocusedBorderColor = BorderLight
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Pending Invoices with Multi-Select Checkboxes & Dynamic Total
            if (unpaidInvoices.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = WaterSkyLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WaterAqua.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Pending Invoices (${unpaidInvoices.size})",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = WaterNavyPrimary
                                    )
                                    Text(
                                        text = "جن انوائسز کی رقم وصول ہوئی ہے ان پر ٹک (✓) لگائیں",
                                        fontSize = 9.5.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val allChecked = selectedInvoiceIds.size == unpaidInvoices.size
                                    OutlinedButton(
                                        onClick = {
                                            if (allChecked) {
                                                applyCheckedInvoices(emptySet())
                                            } else {
                                                applyCheckedInvoices(unpaidInvoices.map { it.id }.toSet())
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(30.dp)
                                            .testTag("toggle_all_invoices_btn")
                                    ) {
                                        Text(
                                            text = if (allChecked) "Uncheck All" else "Select All",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WaterNavyPrimary
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedInvoiceIds.isNotEmpty()) SuccessGreenLight else Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("selected_invoices_total_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Selected (${selectedInvoiceIds.size}/${unpaidInvoices.size}):",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedInvoiceIds.isNotEmpty()) SuccessGreen else TextSecondaryDark
                                    )
                                    Text(
                                        text = "Rs. ${selectedInvoicesTotal.toInt()}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (selectedInvoiceIds.isNotEmpty()) SuccessGreen else WaterNavyDark
                                    )
                                }
                            }
                        }
                    }
                }

                items(unpaidInvoices, key = { it.id }) { inv ->
                    val isChecked = inv.id in selectedInvoiceIds
                    val allocated = allocations[inv.id] ?: 0.0
                    val remainingInvDue = (inv.balanceDue - allocated).coerceAtLeast(0.0)

                    fun toggleInvoiceCheck() {
                        val updated = if (isChecked) {
                            selectedInvoiceIds - inv.id
                        } else {
                            selectedInvoiceIds + inv.id
                        }
                        applyCheckedInvoices(updated)
                    }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) Color(0xFFF0FDF4) else Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isChecked) 1.5.dp else 1.dp,
                            color = if (isChecked) SuccessGreen else BorderLight
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleInvoiceCheck() }
                            .testTag("pending_invoice_card_${inv.id}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { toggleInvoiceCheck() },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SuccessGreen,
                                        uncheckedColor = WaterNavyPrimary
                                    ),
                                    modifier = Modifier.testTag("invoice_checkbox_${inv.id}")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = inv.invoiceNumber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = WaterNavyPrimary
                                    )
                                    Text(
                                        text = "Date: ${inv.date} • Bill: Rs. ${inv.totalAmount.toInt()}",
                                        fontSize = 9.5.sp,
                                        color = TextSecondaryDark
                                    )
                                    if (inv.itemsSummary.isNotBlank()) {
                                        Text(
                                            text = inv.itemsSummary,
                                            fontSize = 9.sp,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Due: Rs. ${inv.balanceDue.toInt()}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp,
                                        color = ErrorRed
                                    )
                                    if (allocated > 0) {
                                        Text(
                                            text = if (remainingInvDue == 0.0) "Full Pay ✓" else "Pay: Rs. ${allocated.toInt()}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (remainingInvDue == 0.0) SuccessGreen else WarningOrange
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Summary & Save + Issue Slip Buttons (At the very bottom of the page)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Payment Received:", color = WaterSkyLight, fontSize = 11.5.sp)
                            Text("Rs. ${totalEnteredAmount.toInt()}", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Allocated to Invoices:", color = WaterSkyLight, fontSize = 10.5.sp)
                            Text("Rs. ${totalAllocated.toInt()}", color = WaterCyanBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        if (unallocatedExcess > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Excess (Advance Credited):", color = WaterMint, fontSize = 10.5.sp)
                                Text("Rs. ${unallocatedExcess.toInt()}", color = WaterMint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        var isSubmitting by remember { mutableStateOf(false) }
                        // null = no dialog, false = Save Only, true = Save & Issue Slip
                        var pendingConfirmWithSlip by remember { mutableStateOf<Boolean?>(null) }

                        if (pendingConfirmWithSlip != null) {
                            val withSlip = pendingConfirmWithSlip!!
                            val targetCustomer = selectedCustomer
                            AlertDialog(
                                onDismissRequest = { pendingConfirmWithSlip = null },
                                containerColor = Color.White,
                                icon = {
                                    Icon(
                                        Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(30.dp)
                                    )
                                },
                                title = {
                                    Text(
                                        text = "Confirm Payment?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = SuccessGreen
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Record payment of Rs. ${totalEnteredAmount.toInt()} (${paymentMethod.label}) from ${targetCustomer?.name.orEmpty()}?\n\nکیا آپ واقعی Rs. ${totalEnteredAmount.toInt()} کی وصولی محفوظ کرنا چاہتے ہیں؟",
                                        fontSize = 11.sp,
                                        color = TextPrimaryDark
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val c = targetCustomer
                                            pendingConfirmWithSlip = null
                                            if (!isSubmitting && c != null && totalEnteredAmount > 0) {
                                                isSubmitting = true
                                                val payment = viewModel.recordPayment(
                                                    customerId = c.id,
                                                    amount = totalEnteredAmount,
                                                    method = paymentMethod,
                                                    allocations = allocations.toMap(),
                                                    notes = notes
                                                )
                                                if (withSlip) {
                                                    receiptToShare = payment
                                                    isSubmitting = false
                                                } else {
                                                    onPaymentCompleted()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("confirm_receive_payment_dialog_btn")
                                    ) {
                                        Text("Yes, Confirm Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingConfirmWithSlip = null }) {
                                        Text("Cancel", color = TextSecondaryDark)
                                    }
                                }
                            )
                        }

                        // Smart Action Buttons (Save vs Save & Issue Slip)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Button 1: Save Payment Only
                            OutlinedButton(
                                onClick = {
                                    if (!isSubmitting && selectedCustomer != null && totalEnteredAmount > 0) {
                                        pendingConfirmWithSlip = false
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isSubmitting && totalEnteredAmount > 0 && selectedCustomer != null,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("save_payment_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save Only", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }

                            // Button 2: Save & Issue Slip (Receipt slip to share on WhatsApp)
                            Button(
                                onClick = {
                                    if (!isSubmitting && selectedCustomer != null && totalEnteredAmount > 0) {
                                        pendingConfirmWithSlip = true
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isSubmitting && totalEnteredAmount > 0 && selectedCustomer != null,
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(46.dp)
                                    .testTag("save_and_issue_receipt_btn")
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save & Issue Slip (رسید)", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 5. RECENT RECEIPTS HISTORY & VERIFICATION (Shown on main pending dues screen)
        // -------------------------------------------------------------
        if (selectedCustomer == null) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(
                    title = "Recent Payment Receipts (حالیہ وصولیاں)",
                    urduTitle = "وصولیاں اور ایڈمن تصدیق"
                )
            }

        if (allPayments.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No payment receipts recorded yet.", fontSize = 10.5.sp, color = TextSecondaryDark)
                    }
                }
            }
        } else {
            items(allPayments, key = { it.id }) { pay ->
                val isNewPayment = viewModel.isRecordNewForCurrentUser(
                    type = "payment",
                    recordId = pay.id,
                    createdByUid = pay.createdByUid,
                    createdByName = pay.receivedBy,
                    createdAt = pay.createdAt,
                    seenKeys = seenRecordKeys,
                    user = currentUser
                )
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.markRecordSeen("payment", pay.id)
                            selectedPaymentForDetail = pay
                        }
                        .testTag("payment_card_${pay.id}")
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
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = pay.customerName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WaterNavyDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isNewPayment) {
                                        NewItemBadge(
                                            onClick = { viewModel.markRecordSeen("payment", pay.id) },
                                            modifier = Modifier.testTag("new_payment_badge_${pay.id}")
                                        )
                                    }
                                }
                                Text(
                                    text = "${pay.receiptNumber} • ${pay.date} • ${pay.paymentMethod.label.split(" ")[0]}",
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                            }
                            Text(
                                text = "Rs. ${pay.amount.toInt()}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Received by: ${pay.receivedBy}",
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                                if (pay.isAcknowledged && pay.acknowledgedBy.isNotEmpty()) {
                                    Text(
                                        text = "Verified by: ${pay.acknowledgedBy}",
                                        fontSize = 9.5.sp,
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Open Payment Receipt PDF Preview before sharing
                                IconButton(
                                    onClick = {
                                        viewModel.markRecordSeen("payment", pay.id)
                                        val cust = customers.firstOrNull { it.id == pay.customerId }
                                        val pdfFile = PdfGenerator.generatePaymentReceiptPdf(context, pay, cust)
                                        previewPdfRequest = PdfPreviewRequest(
                                            file = pdfFile,
                                            title = "Payment Receipt ${pay.receiptNumber}",
                                            chooserTitle = "Share Payment Receipt PDF",
                                            whatsAppCaption = "Aab e Baraan Payment Receipt ${pay.receiptNumber} (${pay.customerName}) - Rs. ${pay.amount.toInt()}"
                                        )
                                    },
                                    modifier = Modifier.size(32.dp).testTag("share_payment_pdf_${pay.id}")
                                ) {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = "Share Receipt PDF",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                // Share Slip Dialog / WhatsApp Text
                                IconButton(
                                    onClick = {
                                        viewModel.markRecordSeen("payment", pay.id)
                                        receiptToShare = pay
                                    },
                                    modifier = Modifier.size(32.dp).testTag("share_payment_whatsapp_${pay.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Share Slip",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                if (isAdmin) {
                                    IconButton(
                                        onClick = {
                                            viewModel.markRecordSeen("payment", pay.id)
                                            paymentToDelete = pay
                                        },
                                        modifier = Modifier.size(30.dp).testTag("delete_payment_${pay.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Payment",
                                            tint = ErrorRed,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                if (pay.isAcknowledged) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SuccessGreenLight,
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Clear",
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                fontWeight = FontWeight.Bold,
                                                color = SuccessGreen
                                            )
                                        }
                                    }
                                } else {
                                    if (isAdmin) {
                                        Button(
                                            onClick = {
                                                viewModel.markRecordSeen("payment", pay.id)
                                                viewModel.acknowledgePayment(pay.id)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Mark Clear",
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = WarningOrangeLight,
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.HourglassTop,
                                                    contentDescription = "Unsettle",
                                                    tint = WarningOrange,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Unsettle",
                                                    fontSize = 9.5.sp,
                                                    maxLines = 1,
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

    // -----------------------------------------------------------------
    // RECEIPT SLIP DIALOG WITH WHATSAPP SHARE
    // -----------------------------------------------------------------
    if (receiptToShare != null) {
        val slip = receiptToShare!!
        val customerObj = customers.firstOrNull { it.id == slip.customerId }

        val shareText = buildString {
            appendLine("💧 *Aab e Baraan RO Water Plant*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🧾 *PAYMENT RECEIPT (ادائیگی رسید)*")
            appendLine("📌 *Receipt #:* ${slip.receiptNumber}")
            appendLine("📅 *Date:* ${slip.date}")
            appendLine("👤 *Customer:* ${slip.customerName}")
            if (customerObj != null) {
                appendLine("📍 *Area:* ${customerObj.area} • Phone: ${customerObj.phone}")
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("💰 *Amount Received:* Rs. ${slip.amount.toInt()}")
            appendLine("💳 *Payment Mode:* ${slip.paymentMethod.label}")
            if (customerObj != null) {
                appendLine("⚖️ *Remaining Due:* Rs. ${customerObj.currentBalance.toInt()}")
            }
            if (slip.notes.isNotEmpty()) {
                appendLine("📝 *Note:* ${slip.notes}")
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("👨‍💼 *Received By:* ${slip.receivedBy}")
            appendLine("✅ _Payment verified & credited to account._")
            appendLine("_شکریہ! آب باراں منرل واٹر پلانٹ_")
        }

        AlertDialog(
            onDismissRequest = {
                receiptToShare = null
                onPaymentCompleted()
            },
            icon = {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Payment Receipt Issued (رسید جاری ہو گئی)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = WaterNavyDark,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WaterSkyLight.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Receipt #:", fontSize = 10.sp, color = TextSecondaryDark)
                                Text(slip.receiptNumber, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Customer:", fontSize = 10.sp, color = TextSecondaryDark)
                                Text(slip.customerName, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyDark)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Amount Received:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Text("Rs. ${slip.amount.toInt()}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Payment Mode:", fontSize = 10.sp, color = TextSecondaryDark)
                                Text(slip.paymentMethod.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Date & Time:", fontSize = 10.sp, color = TextSecondaryDark)
                                Text(slip.date, fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }

                    Text(
                        text = "یہ رسید پی ڈی ایف یا واٹس ایپ میسج کے ذریعے گاہک کو شیئر کریں:",
                        fontSize = 10.sp,
                        color = TextSecondaryDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val pdfFile = PdfGenerator.generatePaymentReceiptPdf(context, slip, customerObj)
                                previewPdfRequest = PdfPreviewRequest(
                                    file = pdfFile,
                                    title = "Payment Receipt ${slip.receiptNumber}",
                                    chooserTitle = "Share Payment Receipt PDF",
                                    whatsAppCaption = "Aab e Baraan Payment Receipt ${slip.receiptNumber} - Rs. ${slip.amount.toInt()}"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("dialog_share_payment_pdf_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "PDF Share",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Receipt Slip via WhatsApp"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("dialog_share_payment_whatsapp_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "WhatsApp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        receiptToShare = null
                        onPaymentCompleted()
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done / Close", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // Admin Confirmation Dialog for Deleting Payment
    if (paymentToDelete != null) {
        val target = paymentToDelete!!
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Payment Receipt? (وصولی حذف کریں)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = ErrorRed
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Are you sure you want to delete payment #${target.receiptNumber} (Rs. ${target.amount.toInt()}) for ${target.customerName}?",
                        fontSize = 11.5.sp,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "⚠️ یہ رقم گاہک کے واجب الادا ادھار میں واپس شامل ہو جائے گی۔",
                        fontSize = 10.5.sp,
                        color = TextSecondaryDark
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(target.id)
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Yes, Delete (ہاں ڈیلیٹ کریں)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }

    // Payment Detail Dialog (shows internal Edit Metadata — never on PDFs/invoices)
    if (selectedPaymentForDetail != null) {
        val livePayment = allPayments.find { it.id == selectedPaymentForDetail!!.id } ?: selectedPaymentForDetail!!
        MobileFormDialog(
            title = "Payment ${livePayment.receiptNumber}",
            subtitle = "${livePayment.date} • ${livePayment.customerName}",
            onDismiss = { selectedPaymentForDetail = null },
            footerContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canEditPayment) {
                        OutlinedButton(
                            onClick = {
                                selectedPaymentForDetail = null
                                paymentToEdit = livePayment
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (canDeletePayment) {
                        OutlinedButton(
                            onClick = {
                                selectedPaymentForDetail = null
                                paymentToDelete = livePayment
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { selectedPaymentForDetail = null },
                        colors = waterPrimaryButtonColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("Close", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SuccessGreenLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Amount Received", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    Text("Rs. ${livePayment.amount.toInt()}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Customer: ${livePayment.customerName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WaterNavyDark)
                    Text("Date: ${livePayment.date} • Mode: ${livePayment.paymentMethod.label}", fontSize = 10.sp, color = TextSecondaryDark)
                    Text("Received By: ${livePayment.receivedBy}", fontSize = 10.sp, color = TextSecondaryDark)
                    if (livePayment.notes.isNotBlank()) {
                        Text("Notes: ${livePayment.notes}", fontSize = 10.sp, color = TextPrimaryDark)
                    }
                    if (livePayment.allocations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Allocated Invoices:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                        livePayment.allocations.forEach { a ->
                            Text("• ${a.invoiceNumber}: Rs. ${a.allocatedAmount.toInt()}", fontSize = 9.5.sp, color = TextPrimaryDark)
                        }
                    }
                }
            }
            EditMetadataBadge(
                lastEditedAt = livePayment.lastEditedAt,
                lastEditedBy = livePayment.lastEditedBy
            )
        }
    }

    // Edit Payment Dialog (with Confirmation + Audit Metadata)
    if (paymentToEdit != null) {
        val target = paymentToEdit!!
        var editDate by remember(target.id) { mutableStateOf(target.date) }
        var editAmount by remember(target.id) { mutableStateOf(target.amount.toInt().toString()) }
        var editMethod by remember(target.id) { mutableStateOf(target.paymentMethod) }
        var editNotes by remember(target.id) { mutableStateOf(target.notes) }
        var confirmEditPayment by remember { mutableStateOf<Payment?>(null) }

        if (confirmEditPayment != null) {
            val candidate = confirmEditPayment!!
            AlertDialog(
                onDismissRequest = { confirmEditPayment = null },
                containerColor = Color.White,
                title = { Text("Confirm Edit Payment?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WaterNavyPrimary) },
                text = {
                    Text(
                        "Save changes to ${candidate.receiptNumber} (Rs. ${candidate.amount.toInt()} on ${candidate.date})?",
                        fontSize = 11.sp,
                        color = TextPrimaryDark
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updatePayment(candidate)
                            confirmEditPayment = null
                            paymentToEdit = null
                        },
                        colors = waterPrimaryButtonColors()
                    ) {
                        Text("Yes, Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmEditPayment = null }) {
                        Text("Cancel", color = TextSecondaryDark)
                    }
                }
            )
        }

        MobileFormDialog(
            title = "Edit Payment ${target.receiptNumber}",
            subtitle = target.customerName,
            onDismiss = { paymentToEdit = null },
            footerContent = {
                TextButton(onClick = { paymentToEdit = null }) {
                    Text("Cancel", color = TextSecondaryDark, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        val amt = editAmount.toDoubleOrNull() ?: target.amount
                        if (amt > 0.0) {
                            confirmEditPayment = target.copy(
                                date = editDate.ifBlank { target.date },
                                amount = amt,
                                paymentMethod = editMethod,
                                notes = editNotes.trim()
                            )
                        }
                    },
                    colors = waterPrimaryButtonColors()
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            EditableDateField(
                label = "Payment Date (تاریخ)*",
                dateValue = editDate,
                onDateSelected = { editDate = it }
            )
            CompactFormTextField(
                value = editAmount,
                onValueChange = { editAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = "Amount (Rs.)*",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER, PaymentMethod.JAZZCASH, PaymentMethod.EASYPAISA).forEach { m ->
                    val sel = editMethod == m
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (sel) WaterSkyButton else WaterSkyLight,
                        modifier = Modifier.weight(1f).height(32.dp).clickable { editMethod = m }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = when (m) {
                                    PaymentMethod.CASH -> "Cash"
                                    PaymentMethod.BANK_TRANSFER -> "Online"
                                    PaymentMethod.JAZZCASH -> "JazzCash"
                                    PaymentMethod.EASYPAISA -> "Easypaisa"
                                    else -> m.label
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) WaterSkyButtonContent else WaterNavyDark
                            )
                        }
                    }
                }
            }
            CompactFormTextField(
                value = editNotes,
                onValueChange = { editNotes = it },
                label = "Notes",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
