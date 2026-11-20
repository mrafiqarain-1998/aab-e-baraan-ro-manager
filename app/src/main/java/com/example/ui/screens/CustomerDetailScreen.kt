package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppModule
import com.example.model.BottleUsageType
import com.example.model.Customer
import com.example.model.Invoice
import com.example.model.PermissionAction
import com.example.model.UserRole
import com.example.ui.components.BlinkingOrangeDot
import com.example.ui.components.PdfPreviewDialog
import com.example.ui.components.PdfPreviewRequest
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import com.example.viewmodel.WaterPlantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customer: Customer,
    viewModel: WaterPlantViewModel,
    onBack: () -> Unit,
    onDeliver: (Customer) -> Unit,
    onReceivePayment: (Customer) -> Unit
) {
    val context = LocalContext.current
    val allCustomers by viewModel.customers.collectAsState()
    val allInvoices by viewModel.invoices.collectAsState()
    val allDeliveries by viewModel.deliveries.collectAsState()
    val bottleSizes by viewModel.bottleSizes.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val allCustomerAllocations by viewModel.customerAllocations.collectAsState()
    val rateDisplayOptions = remember(bottleSizes, inventory) {
        buildDeliverySelectableOptions(bottleSizes, inventory)
    }

    val currentCust = allCustomers.find { it.id == customer.id } ?: customer
    LaunchedEffect(currentCust.id) {
        viewModel.markRecordSeen("customer", currentCust.id)
    }
    val customerInvoices = allInvoices.filter { it.customerId == currentCust.id }
    val customerDeliveries = allDeliveries.filter { it.customerId == currentCust.id }
    val thisCustAllocations = allCustomerAllocations.filter { it.customerId == currentCust.id }
    val currentUser by viewModel.currentUser.collectAsState()
    val canEditCustomer = viewModel.hasPermission(AppModule.CUSTOMERS, PermissionAction.EDIT)
    val canDeleteCustomer = viewModel.hasPermission(AppModule.CUSTOMERS, PermissionAction.DELETE)
    val canManageCustomer = viewModel.hasPermission(AppModule.CUSTOMERS, PermissionAction.MANAGE)
    val canAddDelivery = viewModel.hasPermission(AppModule.DELIVERIES, PermissionAction.ADD)
    val canReceivePayment = viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.ADD)
    val canEditCustomerRates = viewModel.hasPermission(AppModule.CUSTOMER_RATES, PermissionAction.EDIT)
    val isAdmin = canDeleteCustomer || canManageCustomer
    val isViewOnly = !canEditCustomer && !canAddDelivery && !canReceivePayment

    // Only check if customer has pending debt/balance or unreturned empty bottles
    val hasPendingObligations = currentCust.currentBalance > 0.0 || currentCust.emptyBottlesOwed > 0

    var showEditDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    var showDamageDialog by remember { mutableStateOf(false) }
    var showIssueDialog by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showUnsafeDeleteWarningDialog by remember { mutableStateOf(false) }
    var selectedInvoiceForDetail by remember { mutableStateOf<Invoice?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentCust.name, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (canEditCustomer) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        val statement = viewModel.generateCustomerStatement(currentCust)
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, statement)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Send Statement"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WaterNavyPrimary)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(WaterSkySurface),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Customer Overview Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentCust.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WaterNavyDark
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    StatusBadge(
                                        text = if (currentCust.active) "Active (فعال)" else "Inactive (غیر فعال)",
                                        backgroundColor = if (currentCust.active) SuccessGreenLight else WarningOrangeLight,
                                        textColor = if (currentCust.active) SuccessGreen else WarningOrange
                                    )
                                }
                                Text(
                                    text = "${currentCust.customerCode} • ${currentCust.type.label}",
                                    fontSize = 10.5.sp,
                                    color = TextSecondaryDark
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    currentCust.currentBalance > 0 -> {
                                        StatusBadge(
                                            text = "Due: Rs. ${currentCust.currentBalance.toInt()}",
                                            backgroundColor = ErrorRedLight,
                                            textColor = ErrorRed
                                        )
                                    }
                                    currentCust.currentBalance < 0 -> {
                                        StatusBadge(
                                            text = "Adv: Rs. ${kotlin.math.abs(currentCust.currentBalance).toInt()}",
                                            backgroundColor = SuccessGreenLight,
                                            textColor = SuccessGreen
                                        )
                                    }
                                    else -> {
                                        StatusBadge(
                                            text = "Clear",
                                            backgroundColor = WaterSkyLight,
                                            textColor = WaterNavyPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Address: ${currentCust.address.ifBlank { "—" }} (${currentCust.area.ifBlank { "—" }})", fontSize = 11.5.sp, color = TextPrimaryDark, fontWeight = FontWeight.Medium)
                        Text("Payment Cycle: ${currentCust.paymentCycle.label} • Phone: ${currentCust.phone.ifBlank { "—" }}", fontSize = 10.5.sp, color = TextSecondaryDark)
                        Text("Bottles with Customer: ${currentCust.emptyBottlesOwed} bottles", fontSize = 10.5.sp, color = WaterBlueDeep, fontWeight = FontWeight.Bold)

                        val missingFields = remember(currentCust.name, currentCust.phone, currentCust.whatsapp, currentCust.area, currentCust.address) {
                            currentCust.missingDetailLabels()
                        }
                        if (missingFields.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = WarningOrangeLight,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("customer_detail_missing_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        BlinkingOrangeDot(dotSize = 9.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Detail Missing: ${missingFields.joinToString(", ")}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningOrange,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (canEditCustomer) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        TextButton(
                                            onClick = { showEditDialog = true },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text(
                                                text = "Fill Now",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = WarningOrange,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Single-Line Compact Action Buttons: Deliver, Pay, WhatsApp, Edit, Call, Inactive, Delete
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_detail_actions_row"),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (canAddDelivery) {
                                CustomerActionIconButton(
                                    icon = Icons.Default.LocalShipping,
                                    label = "Deliver",
                                    contentDescription = "New Delivery for ${currentCust.name}",
                                    containerColor = WaterSkyButton,
                                    contentColor = WaterSkyButtonContent,
                                    onClick = { onDeliver(currentCust) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "customer_deliver_btn"
                                )
                            }

                            if (canReceivePayment) {
                                CustomerActionIconButton(
                                    icon = Icons.Default.Payments,
                                    label = "Pay",
                                    contentDescription = "Receive Payment from ${currentCust.name}",
                                    containerColor = SuccessGreen,
                                    contentColor = Color.White,
                                    onClick = { onReceivePayment(currentCust) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "customer_pay_btn"
                                )
                            }

                            CustomerActionIconButton(
                                icon = Icons.Default.Chat,
                                label = "WhatsApp",
                                contentDescription = "WhatsApp message to ${currentCust.name}",
                                containerColor = SuccessGreenLight,
                                contentColor = SuccessGreen,
                                onClick = {
                                    val cleanNum = (if (currentCust.whatsapp.isNotBlank()) currentCust.whatsapp else currentCust.phone)
                                        .replace("-", "").replace(" ", "").replace("+", "")
                                    val text = viewModel.generateCustomerStatement(currentCust)
                                    val uri = Uri.parse("https://wa.me/$cleanNum?text=" + Uri.encode(text))
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "customer_whatsapp_btn"
                            )

                            if (canEditCustomer) {
                                CustomerActionIconButton(
                                    icon = Icons.Default.Edit,
                                    label = "Edit",
                                    contentDescription = "Edit Customer ${currentCust.name}",
                                    containerColor = WaterSkyLight,
                                    contentColor = WaterNavyPrimary,
                                    onClick = { showEditDialog = true },
                                    modifier = Modifier.weight(1f),
                                    testTag = "customer_edit_btn"
                                )
                            }

                            CustomerActionIconButton(
                                icon = Icons.Default.Call,
                                label = "Call",
                                contentDescription = "Call ${currentCust.name}",
                                containerColor = Color(0xFFE2E8F0),
                                contentColor = WaterNavyDark,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentCust.phone}"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "customer_call_btn"
                            )

                            if (canManageCustomer) {
                                CustomerActionIconButton(
                                    icon = if (currentCust.active) Icons.Default.Block else Icons.Default.CheckCircle,
                                    label = if (currentCust.active) "Inactive" else "Active",
                                    contentDescription = if (currentCust.active) "Mark Customer Inactive" else "Mark Customer Active",
                                    containerColor = if (currentCust.active) WarningOrangeLight else SuccessGreenLight,
                                    contentColor = if (currentCust.active) WarningOrange else SuccessGreen,
                                    onClick = { viewModel.toggleCustomerActive(currentCust.id) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "customer_toggle_active_btn"
                                )
                            }

                            if (canDeleteCustomer) {
                                CustomerActionIconButton(
                                    icon = Icons.Default.Delete,
                                    label = "Delete",
                                    contentDescription = "Delete Customer ${currentCust.name}",
                                    containerColor = ErrorRedLight,
                                    contentColor = ErrorRed,
                                    onClick = {
                                        if (hasPendingObligations) {
                                            showUnsafeDeleteWarningDialog = true
                                        } else {
                                            showDeleteConfirmDialog = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    testTag = "customer_delete_btn"
                                )
                            }
                        }
                    }
                }
            }

            // 2. Customer Specific Bottle Allocation & Damage Number Board (All aligned on single lines)
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
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = WaterAqua,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Bottle Allocation & Damage",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = WaterNavyPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "بوتل اجرا، واپسی اور نقصان کا حساب",
                                        fontSize = 9.5.sp,
                                        color = TextSecondaryDark,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Top Iconic Action Buttons: Issue, Damage, Return
                            if (!isViewOnly) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { showIssueDialog = true },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = WaterSkyLight,
                                            contentColor = WaterNavyPrimary
                                        ),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("customer_bottle_issue_icon")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircleOutline,
                                            contentDescription = "Issue Bottle (بوتل جاری کریں)",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    FilledTonalIconButton(
                                        onClick = { showDamageDialog = true },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = ErrorRedLight,
                                            contentColor = ErrorRed
                                        ),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("customer_bottle_damage_icon")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BrokenImage,
                                            contentDescription = "Record Damage (ٹوٹ پھوٹ ریکارڈ کریں)",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    FilledTonalIconButton(
                                        onClick = { showReturnDialog = true },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = SuccessGreenLight,
                                            contentColor = SuccessGreen
                                        ),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("customer_bottle_return_icon")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AssignmentReturn,
                                            contentDescription = "Return Bottle (بوتل واپسی)",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val totalIssued = thisCustAllocations.sumOf { it.totalIssued }.let { sum ->
                            if (sum > 0) sum else currentCust.emptyBottlesOwed
                        }
                        val normalHeld = currentCust.emptyBottlesOwed
                        val damagedCount = thisCustAllocations.sumOf { it.damagedCount }
                        val returnedCount = thisCustAllocations.sumOf { it.returnedCount }

                        // Single-Line Overall Number Board (Issued | Held | Damaged | Returned)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_bottle_number_board"),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = WaterSkyLight,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$totalIssued", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterNavyPrimary, maxLines = 1, softWrap = false)
                                    Text("Issued", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary, maxLines = 1, softWrap = false)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = WaterAqua.copy(alpha = 0.14f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$normalHeld", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterAqua, maxLines = 1, softWrap = false)
                                    Text("Held", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyDark, maxLines = 1, softWrap = false)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = ErrorRedLight,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$damagedCount", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = ErrorRed, maxLines = 1, softWrap = false)
                                    Text("Damaged", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = ErrorRed, maxLines = 1, softWrap = false)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = SuccessGreenLight,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$returnedCount", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen, maxLines = 1, softWrap = false)
                                    Text("Returned", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = SuccessGreen, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        // Show multiple-use bottle sizes only in Customer Details (hide single-use sizes)
                        val returnableSizes = bottleSizes.filter {
                            it.usageType == BottleUsageType.MULTIPLE_TIME ||
                                (it.usageType == BottleUsageType.CUSTOM && it.isReturnableBottle)
                        }
                        if (returnableSizes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            returnableSizes.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    pair.forEach { size ->
                                        val alloc = thisCustAllocations.find { it.bottleSizeId == size.id }
                                        val sIssued = alloc?.totalIssued ?: 0
                                        val sHeld = alloc?.emptyHeld ?: 0
                                        val sDamaged = alloc?.damagedCount ?: 0
                                        val sReturned = alloc?.returnedCount ?: 0
                                        val shortName = shortBottleLabel(size)

                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = WaterSkySurface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = shortName,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 11.sp,
                                                        color = WaterNavyPrimary,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (sHeld > 0) WarningOrangeLight else SuccessGreenLight
                                                    ) {
                                                        Text(
                                                            text = "Held: $sHeld",
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (sHeld > 0) WarningOrange else SuccessGreen,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Iss:$sIssued", fontSize = 8.5.sp, color = TextSecondaryDark, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                    Text("Ret:$sReturned", fontSize = 8.5.sp, color = SuccessGreen, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                    Text("Dmg:$sDamaged", fontSize = 8.5.sp, color = ErrorRed, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                }
                                            }
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Custom Bottle Pricing for this Customer (Admin Fixes Rate)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Agreed Bottle Rates",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (!isViewOnly && canEditCustomerRates) {
                                TextButton(
                                    onClick = { showRateDialog = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = WaterAqua)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Set Custom Rate", fontSize = 9.5.sp, color = WaterAqua, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        rateDisplayOptions.forEach { opt ->
                            val directPrice = viewModel.getCustomerPrice(currentCust.id, opt.rateKey)
                            val fallbackPrice = if (directPrice <= 0.0 && opt.rateKey != opt.bottleSizeId) {
                                viewModel.getCustomerPrice(currentCust.id, opt.bottleSizeId)
                            } else 0.0
                            val currentPrice = if (directPrice > 0.0) directPrice else fallbackPrice
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = opt.shortName,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WaterNavyDark
                                )
                                Text(
                                    text = if (currentPrice > 0.0) "Rs. ${currentPrice.toInt()} (Agreed)" else "Rs. 0 (Not Set)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentPrice > 0.0) WaterAqua else TextSecondaryDark
                                )
                            }
                        }
                    }
                }
            }

            // 4. Invoices List
            item {
                SectionHeader(
                    title = "Invoices & Ledger History",
                    urduTitle = "انوائسز اور بلز"
                )
            }

            if (customerInvoices.isEmpty()) {
                item {
                    Text("No invoices generated yet.", fontSize = 10.5.sp, color = TextMuted)
                }
            } else {
                items(customerInvoices) { inv ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedInvoiceForDetail = inv }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = inv.invoiceNumber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = WaterNavyPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${inv.date}",
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = inv.itemsSummary.ifEmpty { "Water Supply" },
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Rs. ${inv.totalAmount.toInt()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = TextPrimaryDark
                                    )
                                    Text(
                                        text = if (inv.balanceDue <= 0) "Paid" else "Due: Rs. ${inv.balanceDue.toInt()}",
                                        fontSize = 10.sp,
                                        color = if (inv.balanceDue > 0) ErrorRed else SuccessGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Quick WhatsApp Share Button
                                if (!isViewOnly) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SuccessGreenLight,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable {
                                                val shareText = """
                                                    *AAB E BARAAN RO PLANT*
                                                    📄 Invoice: ${inv.invoiceNumber}
                                                    👤 Customer: ${currentCust.name}
                                                    📅 Date: ${inv.date}
                                                    
                                                    📦 Summary: ${inv.itemsSummary.ifEmpty { "Water Bottles Supply" }}
                                                    💰 Total Bill: Rs. ${inv.totalAmount.toInt()}
                                                    💵 Amount Paid: Rs. ${inv.paidAmount.toInt()}
                                                    ⚠️ Balance Due: Rs. ${inv.balanceDue.toInt()}
                                                """.trimIndent()
                                                val targetNum = (if (currentCust.whatsapp.isNotBlank()) currentCust.whatsapp else currentCust.phone)
                                                    .replace("-", "").replace(" ", "").replace("+", "")
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$targetNum?text=${Uri.encode(shareText)}"))
                                                try { context.startActivity(intent) } catch (e: Exception) {}
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Share, contentDescription = "Share Invoice", tint = SuccessGreen, modifier = Modifier.size(16.dp))
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

    val multiTimeBottleSizes = remember(bottleSizes) {
        bottleSizes.filter { it.active && (it.usageType == BottleUsageType.MULTIPLE_TIME || it.isReturnableBottle) }
            .ifEmpty { bottleSizes.filter { it.isReturnableBottle } }
    }

    // Record Broken Bottle Dialog (Multiple Time Bottles Only — Size Mandatory)
    if (showDamageDialog) {
        var brokenCountText by remember { mutableStateOf("1") }
        var reasonText by remember { mutableStateOf("Cracked / Leaking at home") }
        var selectedDamageSizeId by remember {
            mutableStateOf(if (multiTimeBottleSizes.size == 1) multiTimeBottleSizes.first().id else "")
        }

        AlertDialog(
            onDismissRequest = { showDamageDialog = false },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            title = {
                Text(
                    text = "Record Broken / Damaged Bottle (ٹوٹی بوتل)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = ErrorRed
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Record bottles damaged while in ${currentCust.name}'s possession:",
                        fontSize = 10.5.sp,
                        color = TextPrimaryDark
                    )

                    Text(
                        "Select Multiple-Time Bottle Size* (بوتل کا سائز لازمی منتخب کریں):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        multiTimeBottleSizes.forEach { size ->
                            val held = thisCustAllocations.find { it.bottleSizeId == size.id }?.emptyHeld ?: 0
                            FilterChip(
                                selected = selectedDamageSizeId == size.id,
                                onClick = { selectedDamageSizeId = size.id },
                                colors = waterFilterChipColors(),
                                label = {
                                    Text(
                                        "${shortBottleLabel(size)} (Held: $held)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                        }
                    }
                    if (selectedDamageSizeId.isBlank()) {
                        Text(
                            text = "Please choose bottle size to continue.",
                            fontSize = 9.5.sp,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = brokenCountText,
                        onValueChange = { brokenCountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Number of Broken Bottles*") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Reason / Condition (وجہ)*") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val countVal = brokenCountText.toIntOrNull() ?: 0
                Button(
                    enabled = selectedDamageSizeId.isNotBlank() && countVal > 0,
                    onClick = {
                        viewModel.recordCustomerDamage(currentCust.id, selectedDamageSizeId, countVal, reasonText)
                        showDamageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Record Damage", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDamageDialog = false }) {
                    Text("Cancel", color = WaterNavyPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Issue Bottles Dialog (Multiple Time Bottles Only — Size Mandatory)
    if (showIssueDialog) {
        var issueCountText by remember { mutableStateOf("2") }
        var depositText by remember { mutableStateOf("2000") }
        var selectedIssueSizeId by remember {
            mutableStateOf(if (multiTimeBottleSizes.size == 1) multiTimeBottleSizes.first().id else "")
        }

        AlertDialog(
            onDismissRequest = { showIssueDialog = false },
            containerColor = Color.White,
            titleContentColor = WaterNavyPrimary,
            textContentColor = TextPrimaryDark,
            title = {
                Text(
                    text = "Issue Bottles to Customer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = WaterNavyPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Allocate bottles to ${currentCust.name}:", fontSize = 10.5.sp, color = TextPrimaryDark)

                    Text(
                        "Select Multiple-Time Bottle Size* (بوتل کا سائز لازمی منتخب کریں):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        multiTimeBottleSizes.forEach { size ->
                            FilterChip(
                                selected = selectedIssueSizeId == size.id,
                                onClick = { selectedIssueSizeId = size.id },
                                colors = waterFilterChipColors(),
                                label = {
                                    Text(
                                        "${shortBottleLabel(size)} (${size.name})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                        }
                    }
                    if (selectedIssueSizeId.isBlank()) {
                        Text(
                            text = "Please choose bottle size to continue.",
                            fontSize = 9.5.sp,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = issueCountText,
                        onValueChange = { issueCountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Number of Bottles Issued*") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = depositText,
                        onValueChange = { depositText = it },
                        label = { Text("Deposit Collected (سیکیورٹی رقم)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val countVal = issueCountText.toIntOrNull() ?: 0
                Button(
                    enabled = selectedIssueSizeId.isNotBlank() && countVal > 0,
                    onClick = {
                        val dep = depositText.toDoubleOrNull() ?: 0.0
                        viewModel.issueBottlesToCustomer(currentCust.id, selectedIssueSizeId, countVal, dep)
                        showIssueDialog = false
                    },
                    colors = waterPrimaryButtonColors()
                ) {
                    Text("Issue Bottles", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueDialog = false }) {
                    Text("Cancel", color = WaterNavyPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Return Bottle Dialog (Multiple Time Bottles Only — Size Mandatory)
    if (showReturnDialog) {
        var returnCountText by remember { mutableStateOf("1") }
        var selectedSizeId by remember {
            mutableStateOf(if (multiTimeBottleSizes.size == 1) multiTimeBottleSizes.first().id else "")
        }
        var isFullBottle by remember { mutableStateOf(false) }
        var returnReasonText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showReturnDialog = false },
            containerColor = Color.White,
            titleContentColor = WaterNavyPrimary,
            textContentColor = TextPrimaryDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بوتل واپسی (Return Bottle)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = WaterNavyPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "گاہک: ${currentCust.name} (کل زیر ذمہ: ${currentCust.emptyBottlesOwed} بوتلیں)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WaterNavyDark
                    )

                    // Mandatory Multiple-Time Bottle size selection chips
                    Text(
                        "Multiple-Time Bottle Size* (بوتل کا سائز لازمی منتخب کریں):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        multiTimeBottleSizes.forEach { size ->
                            val held = thisCustAllocations.find { it.bottleSizeId == size.id }?.emptyHeld ?: 0
                            FilterChip(
                                selected = selectedSizeId == size.id,
                                onClick = { selectedSizeId = size.id },
                                colors = waterFilterChipColors(),
                                label = {
                                    Text(
                                        "${shortBottleLabel(size)} (Held: $held)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                        }
                    }
                    if (selectedSizeId.isBlank()) {
                        Text(
                            text = "Please choose bottle size to continue.",
                            fontSize = 9.5.sp,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = returnCountText,
                        onValueChange = { returnCountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("واپسی بوتلوں کی تعداد (Quantity)*") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bottle condition: Empty vs Full
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isFullBottle,
                            onClick = { isFullBottle = false },
                            colors = waterFilterChipColors(),
                            label = { Text("خالی بوتل (Empty)", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isFullBottle,
                            onClick = { isFullBottle = true },
                            colors = waterFilterChipColors(),
                            label = { Text("بھری بوتل (Full)", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = returnReasonText,
                        onValueChange = { returnReasonText = it },
                        label = { Text("واپسی کی وجہ (Reason / Notes)") },
                        placeholder = { Text("مثلاً: اضافی بوتل واپس، سیکیورٹی واپسی") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Clear Banner showing Add to Inventory and deduction
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Add to Inventory (اسٹاک میں شامل کریں)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                                Text(
                                    text = "یہ بوتل پلانٹ کے اسٹاک میں جمع ہو جائے گی اور گاہک کے کھاتے سے کم ہو جائے گی۔",
                                    fontSize = 9.5.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val countVal = returnCountText.toIntOrNull() ?: 0
                Button(
                    enabled = selectedSizeId.isNotBlank() && countVal > 0,
                    onClick = {
                        val r = returnReasonText.ifBlank { "Customer returned bottles" }
                        viewModel.returnBottlesFromCustomer(
                            customerId = currentCust.id,
                            bottleSizeId = selectedSizeId,
                            count = countVal,
                            isFull = isFullBottle,
                            reason = r
                        )
                        showReturnDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White)
                ) {
                    Text("واپسی درج کریں (Save Return)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReturnDialog = false }) {
                    Text("منسوخ", color = WaterNavyPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Custom Bottle Sizes & Rates Dialog
    if (showRateDialog) {
        CustomerBottleRateDialog(
            customer = currentCust,
            viewModel = viewModel,
            onDismiss = { showRateDialog = false }
        )
    }

    if (showEditDialog) {
        val currentAgreedRate = viewModel.getCustomerPrice(currentCust.id, "size_19l")
        CustomerFormDialog(
            customer = currentCust,
            initialAgreedRate = currentAgreedRate.coerceAtLeast(0.0),
            onDismiss = { showEditDialog = false },
            onConfirmWithRate = { updatedCust, agreedRate ->
                viewModel.updateCustomer(updatedCust)
                if (agreedRate > 0.0) {
                    viewModel.setCustomerPrice(updatedCust.id, "size_19l", agreedRate)
                }
                showEditDialog = false
            }
        )
    }

    if (showUnsafeDeleteWarningDialog) {
        AlertDialog(
            onDismissRequest = { showUnsafeDeleteWarningDialog = false },
            containerColor = Color.White,
            titleContentColor = WaterNavyPrimary,
            textContentColor = TextPrimaryDark,
            icon = {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = WarningOrange,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Deletion Protected (حفاظتی پابندی)",
                    fontWeight = FontWeight.Bold,
                    color = WaterNavyPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "گاہک '${currentCust.name}' کو ڈیلیٹ نہیں کیا جا سکتا کیونکہ اس کے پاس بقایا رقم یا خالی بوتلیں واجب الادا ہیں:",
                        fontSize = 11.5.sp,
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• بقایا واجب الادا رقم: Rs. ${currentCust.currentBalance.toInt()}\n• پاس موجود بوتلیں: ${currentCust.emptyBottlesOwed}",
                        fontSize = 10.5.sp,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "برائے مہربانی پہلے رقم وصول کریں یا بوتلیں واپس لیں، یا گاہک کو 'غیر فعال (Inactive)' کریں۔",
                        fontSize = 10.5.sp,
                        color = WaterBlueDeep
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentCust.active) {
                            viewModel.toggleCustomerActive(currentCust.id)
                        }
                        showUnsafeDeleteWarningDialog = false
                    },
                    colors = waterPrimaryButtonColors()
                ) {
                    Text(if (currentCust.active) "Mark Inactive (غیر فعال کریں)" else "Understood (سمجھ گئے)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsafeDeleteWarningDialog = false }) {
                    Text("Close (بند کریں)", color = WaterNavyPrimary)
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
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
                    text = "Delete Customer (گاہک ڈیلیٹ کریں)",
                    fontWeight = FontWeight.Bold,
                    color = ErrorRed
                )
            },
            text = {
                Text(
                    text = "کیا آپ واقعی '${currentCust.name}' کو مستقل ڈیلیٹ کرنا چاہتے ہیں؟ اس عمل کو واپس نہیں لایا جا سکتا۔",
                    fontSize = 11.5.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(currentCust.id)
                        showDeleteConfirmDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Yes, Delete (ڈیلیٹ کریں)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel (منسوخ)", color = WaterNavyPrimary)
                }
            }
        )
    }

    // Invoice Detail Dialog
    if (selectedInvoiceForDetail != null) {
        InvoiceDetailDialog(
            invoice = selectedInvoiceForDetail!!,
            customer = currentCust,
            onDismiss = { selectedInvoiceForDetail = null },
            isViewOnly = isViewOnly
        )
    }
}

@Composable
fun CustomerActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 3.dp, horizontal = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 7.sp,
                letterSpacing = (-0.2).sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun InvoiceDetailDialog(
    invoice: Invoice,
    customer: Customer,
    onDismiss: () -> Unit,
    isViewOnly: Boolean = false
) {
    val context = LocalContext.current
    var previewPdfRequest by remember { mutableStateOf<PdfPreviewRequest?>(null) }
    val shareText = """
        *AAB E BARAAN RO PLANT*
        📄 Customer Invoice: ${invoice.invoiceNumber}
        👤 Customer: ${customer.name}
        📅 Date: ${invoice.date}
        
        📦 Summary: ${invoice.itemsSummary.ifEmpty { "Water Supply & Refill" }}
        
        💰 Total Bill: Rs. ${invoice.totalAmount.toInt()}
        💵 Amount Paid: Rs. ${invoice.paidAmount.toInt()}
        ⚠️ Balance Due: Rs. ${invoice.balanceDue.toInt()}
        
        Status: ${invoice.status.name}
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = WaterNavyPrimary,
        textContentColor = TextPrimaryDark,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = WaterNavyPrimary)
                    Text("Date: ${invoice.date}", fontSize = 10.sp, color = TextSecondaryDark)
                }
                StatusBadge(
                    text = if (invoice.balanceDue <= 0.0) "Paid (مکمل)" else if (invoice.paidAmount > 0) "Partial (جزوی)" else "Unpaid (باقی)",
                    backgroundColor = if (invoice.balanceDue <= 0.0) SuccessGreenLight else if (invoice.paidAmount > 0) WarningOrangeLight else WarningAmberLight,
                    textColor = if (invoice.balanceDue <= 0.0) SuccessGreen else if (invoice.paidAmount > 0) WarningOrange else WarningAmber
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WaterSkyLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = WaterNavyPrimary)
                        Text("Contact: ${customer.phone.ifEmpty { "N/A" }}", fontSize = 10.sp, color = TextPrimaryDark)
                        Text("Area: ${customer.area}", fontSize = 10.sp, color = TextSecondaryDark)
                    }
                }

                Text("Delivered Items & Summary (تفصیل):", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = WaterNavyDark)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = invoice.itemsSummary.ifEmpty { "Standard Water Supply & Bottle Refill" },
                        fontSize = 10.5.sp,
                        color = TextPrimaryDark,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                HorizontalDivider(color = DividerColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Invoice Bill (کل رقم):", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = WaterNavyPrimary)
                    Text("Rs. ${invoice.totalAmount.toInt()}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WaterNavyPrimary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Amount Paid (ادا شدہ):", fontSize = 10.5.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                    Text("Rs. ${invoice.paidAmount.toInt()}", fontSize = 10.5.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                }

                if (invoice.balanceDue > 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Balance Due (بقایا رقم):", fontSize = 10.5.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                        Text("Rs. ${invoice.balanceDue.toInt()}", fontSize = 11.5.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            if (!isViewOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // WhatsApp Share Button
                    Button(
                        onClick = {
                            val targetNum = (if (customer.whatsapp.isNotBlank()) customer.whatsapp else customer.phone)
                                .replace("-", "").replace(" ", "").replace("+", "")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$targetNum?text=${Uri.encode(shareText)}"))
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // PDF Preview & Share Button
                    OutlinedButton(
                        onClick = {
                            try {
                                val pdfFile = PdfGenerator.generateInvoicePdf(context, invoice, customer)
                                previewPdfRequest = PdfPreviewRequest(
                                    file = pdfFile,
                                    title = "Invoice #${invoice.invoiceNumber}",
                                    chooserTitle = "Share Invoice #${invoice.invoiceNumber}",
                                    whatsAppCaption = shareText
                                )
                            } catch (e: Exception) {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Invoice Voucher"))
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF Bill", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close (بند کریں)", color = TextSecondaryDark)
                }
            }
        },
        dismissButton = {
            if (!isViewOnly) {
                TextButton(onClick = onDismiss) {
                    Text("Close (بند کریں)", color = TextSecondaryDark)
                }
            }
        }
    )

    previewPdfRequest?.let { req ->
        PdfPreviewDialog(
            request = req,
            onDismiss = { previewPdfRequest = null }
        )
    }
}
