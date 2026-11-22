package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.components.FromToDateRangeDialog
import com.example.ui.components.MobileFormDialog
import com.example.ui.components.NewItemBadge
import com.example.ui.components.PdfPreviewDialog
import com.example.ui.components.PdfPreviewRequest
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import com.example.viewmodel.WaterPlantViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DeliveryDateFilter(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    CUSTOM("Custom Date"),
    ALL("All Deliveries")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveriesListScreen(
    viewModel: WaterPlantViewModel,
    onNewDeliveryClick: () -> Unit
) {
    val deliveries by viewModel.deliveries.collectAsState()
    val bottleSizes by viewModel.bottleSizes.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val seenRecordKeys by viewModel.seenRecordKeys.collectAsState()
    val canAddDelivery = viewModel.hasPermission(AppModule.DELIVERIES, PermissionAction.ADD)
    val canEditDelivery = viewModel.hasPermission(AppModule.DELIVERIES, PermissionAction.EDIT)
    val canDeleteDelivery = viewModel.hasPermission(AppModule.DELIVERIES, PermissionAction.DELETE)
    val canReceivePayment = viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.ADD)
    val canApprovePayment = viewModel.hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE)
    val isViewOnly = !canAddDelivery && !canEditDelivery
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedDeliveryId by remember { mutableStateOf<String?>(null) }
    var deliveryToDelete by remember { mutableStateOf<Delivery?>(null) }
    var deliveryToEdit by remember { mutableStateOf<Delivery?>(null) }
    var deliveryForDirectPayment by remember { mutableStateOf<Delivery?>(null) }
    var previewPdfRequest by remember { mutableStateOf<PdfPreviewRequest?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { dateFormat.format(Date()) }
    val yesterdayStr = remember {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.let { dateFormat.format(it.time) }
    }
    val last7StartStr = remember {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }.let { dateFormat.format(it.time) }
    }
    val last30StartStr = remember {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -29) }.let { dateFormat.format(it.time) }
    }

    var selectedFilter by remember { mutableStateOf(DeliveryDateFilter.TODAY) }
    var customStartDate by remember { mutableStateOf(todayStr) }
    var customEndDate by remember { mutableStateOf(todayStr) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val (filterStart, filterEnd) = remember(selectedFilter, todayStr, yesterdayStr, last7StartStr, last30StartStr, customStartDate, customEndDate) {
        when (selectedFilter) {
            DeliveryDateFilter.TODAY -> todayStr to todayStr
            DeliveryDateFilter.YESTERDAY -> yesterdayStr to yesterdayStr
            DeliveryDateFilter.LAST_7_DAYS -> last7StartStr to todayStr
            DeliveryDateFilter.LAST_30_DAYS -> last30StartStr to todayStr
            DeliveryDateFilter.CUSTOM -> customStartDate to customEndDate
            DeliveryDateFilter.ALL -> "" to "9999-12-31"
        }
    }

    val activeFilterTitle = remember(selectedFilter, customStartDate, customEndDate) {
        if (selectedFilter == DeliveryDateFilter.CUSTOM) {
            if (customStartDate == customEndDate) customStartDate else "$customStartDate → $customEndDate"
        } else {
            selectedFilter.label
        }
    }

    // Period-filtered deliveries for both the Number Board and the List below
    val periodDeliveries = remember(deliveries, selectedFilter, filterStart, filterEnd) {
        val base = if (selectedFilter == DeliveryDateFilter.ALL) {
            deliveries
        } else {
            deliveries.filter { it.date in filterStart..filterEnd }
        }
        base.sortedWith(compareByDescending<Delivery> { it.date }.thenByDescending { it.createdAt }.thenByDescending { it.id })
    }

    val filteredDeliveries = remember(periodDeliveries, searchQuery) {
        if (searchQuery.isBlank()) periodDeliveries
        else {
            val q = searchQuery.trim().lowercase()
            periodDeliveries.filter { del ->
                del.customerName.lowercase().contains(q) ||
                    del.deliveryNumber.lowercase().contains(q) ||
                    del.customerArea.lowercase().contains(q) ||
                    del.customerPhone.lowercase().contains(q)
            }
        }
    }

    val activeSizes = remember(bottleSizes) { bottleSizes.filter { it.active } }
    val totalFullDeliveredInSummary = remember(periodDeliveries) { periodDeliveries.sumOf { it.totalFullDelivered } }
    val totalEmptyCollectedInSummary = remember(periodDeliveries) { periodDeliveries.sumOf { it.totalEmptyCollected } }
    val totalAmountInSummary = remember(periodDeliveries) { periodDeliveries.sumOf { it.totalAmount } }
    val creditCountInSummary = remember(periodDeliveries) {
        periodDeliveries.count { it.totalAmount > 0 && it.paymentCollected < it.totalAmount }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. TOP SINGLE-LINE FILTER TABS (Today, Yesterday, Last 7 Days, Last 30 Days, All Deliveries)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp)
                    .testTag("delivery_period_tabs"),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DeliveryDateFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) WaterNavyPrimary else Color.Transparent,
                        modifier = Modifier
                            .height(32.dp)
                            .clickable {
                                if (filter == DeliveryDateFilter.CUSTOM) {
                                    showCustomDateDialog = true
                                } else {
                                    selectedFilter = filter
                                }
                            }
                            .testTag("filter_${filter.name.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = if (filter == DeliveryDateFilter.CUSTOM && isSelected) {
                                    if (customStartDate == customEndDate) customStartDate else "$customStartDate..$customEndDate"
                                } else {
                                    filter.label
                                },
                                color = if (isSelected) Color.White else WaterNavyDark,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // 2. COMPACT BOTTLE-WISE NUMBER BOARD
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delivery_summary_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = WaterCyanBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$activeFilterTitle Summary",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WaterAqua.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "${periodDeliveries.size} Orders • Rs. ${totalAmountInSummary.toInt()}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterMint,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Full: $totalFullDeliveredInSummary  •  Empty Ret: $totalEmptyCollectedInSummary",
                            color = WaterSkyLight.copy(alpha = 0.85f),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (creditCountInSummary > 0) {
                            Text(
                                text = "$creditCountInSummary Credit",
                                color = WarningAmber,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottle-Wise Number Board (single-line alignment; scrollable if > 5 custom sizes added)
                    val badgeColors = listOf(WaterCyanBright, WaterMint, WarningAmber, Color(0xFF81D4FA), Color(0xFFA7FFEB))
                    val fitsSingleRow = activeSizes.size <= 5
                    Row(
                        modifier = if (fitsSingleRow) {
                            Modifier
                                .fillMaxWidth()
                                .testTag("delivery_bottle_number_boards")
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("delivery_bottle_number_boards")
                        },
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeSizes.forEachIndexed { idx, size ->
                            val qtyForSize = periodDeliveries.sumOf { del ->
                                del.items.filter { it.bottleSizeId == size.id }.sumOf { it.quantity }
                            }
                            SmartSizeBadge(
                                label = shortBottleLabel(size),
                                value = "$qtyForSize",
                                accentColor = badgeColors[idx % badgeColors.size],
                                modifier = if (fitsSingleRow) Modifier.weight(1f) else Modifier.width(62.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. + ADD NEW DELIVERY BUTTON & CORNER SEARCH ICON
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canAddDelivery) {
                        Button(
                            onClick = onNewDeliveryClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WaterAqua,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("new_delivery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddShoppingCart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ Add New Delivery",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSearchOpen || searchQuery.isNotEmpty()) WaterNavyPrimary else Color.White,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                isSearchOpen = !isSearchOpen
                                if (!isSearchOpen) searchQuery = ""
                            }
                            .testTag("delivery_search_toggle_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSearchOpen || searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Search,
                                contentDescription = "Search Delivery",
                                tint = if (isSearchOpen || searchQuery.isNotEmpty()) Color.White else WaterNavyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isSearchOpen || searchQuery.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    CompactFormTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search customer, voucher #, area...",
                        height = 38.dp,
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delivery_search_input")
                    )
                }
            }
        }

        // 4. FILTERED DELIVERIES LIST (Slightly narrower aligned cards, Credit marked cleanly)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$activeFilterTitle Deliveries (${filteredDeliveries.size})",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaterNavyPrimary,
                    maxLines = 1,
                    softWrap = false
                )
                if (creditCountInSummary > 0) {
                    Text(
                        text = "Credit Marked",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarningOrange,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (filteredDeliveries.isEmpty()) {
            item {
                EmptyDeliverySectionCard(
                    message = "No deliveries found for ${selectedFilter.label} (اس دوران کی کوئی ڈیلیوری نہیں)"
                )
            }
        } else {
            items(filteredDeliveries, key = { it.id }) { delivery ->
                val cardPayments = PdfGenerator.findRelatedPaymentsForDelivery(delivery, payments)
                val isNewDelivery = viewModel.isRecordNewForCurrentUser(
                    type = "delivery",
                    recordId = delivery.id,
                    createdByUid = delivery.createdByUid,
                    createdByName = delivery.createdBy,
                    createdAt = delivery.createdAt,
                    seenKeys = seenRecordKeys,
                    user = currentUser
                )
                DeliveryCardItem(
                    delivery = delivery,
                    bottleSizes = bottleSizes,
                    relatedPayments = cardPayments,
                    isAdmin = canApprovePayment,
                    isViewOnly = isViewOnly,
                    isNew = isNewDelivery,
                    onDismissNew = { viewModel.markRecordSeen("delivery", delivery.id) },
                    onClick = {
                        viewModel.markRecordSeen("delivery", delivery.id)
                        selectedDeliveryId = delivery.id
                    },
                    onSharePdf = {
                        viewModel.markRecordSeen("delivery", delivery.id)
                        try {
                            val pdfFile = PdfGenerator.generateDeliveryVoucherPdf(
                                context = context,
                                delivery = delivery,
                                payments = cardPayments,
                                bottleSizes = bottleSizes
                            )
                            previewPdfRequest = PdfPreviewRequest(
                                file = pdfFile,
                                title = "Delivery Slip #${delivery.deliveryNumber}",
                                chooserTitle = "Share Delivery Slip #${delivery.deliveryNumber}",
                                whatsAppCaption = "Delivery Voucher #${delivery.deliveryNumber} - ${delivery.customerName}"
                            )
                        } catch (t: Throwable) {
                            Toast.makeText(context, "Unable to open PDF: ${t.message ?: "Error"}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMarkClear = {
                        viewModel.markRecordSeen("delivery", delivery.id)
                        val unsettledIds = cardPayments.filter { !it.isAcknowledged }.map { it.id }
                        if (unsettledIds.isNotEmpty()) {
                            viewModel.acknowledgePayments(unsettledIds)
                        }
                    },
                    onReceivePayment = {
                        viewModel.markRecordSeen("delivery", delivery.id)
                        deliveryForDirectPayment = it
                    },
                    onEdit = {
                        viewModel.markRecordSeen("delivery", delivery.id)
                        deliveryToEdit = it
                    },
                    onDelete = {
                        viewModel.markRecordSeen("delivery", delivery.id)
                        deliveryToDelete = it
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // LIVE DELIVERY DETAIL DIALOG (Auto-reflects edits & installment payments)
    val selectedDelivery = remember(selectedDeliveryId, deliveries) {
        selectedDeliveryId?.let { id -> deliveries.find { it.id == id } }
    }
    if (selectedDelivery != null) {
        val del = selectedDelivery
        val relatedPayments = remember(del, payments) {
            PdfGenerator.findRelatedPaymentsForDelivery(del, payments)
        }
        val allocatedTotal = relatedPayments.sumOf { p ->
            val matched = p.allocations.filter {
                (del.invoiceId.isNotBlank() && it.invoiceId == del.invoiceId) ||
                    it.invoiceId == del.id ||
                    it.invoiceId == "inv_${del.id}"
            }.sumOf { it.allocatedAmount }
            if (matched > 0.0) matched else p.amount
        }
        val totalPaid = maxOf(del.paymentCollected, allocatedTotal).coerceAtMost(del.totalAmount)
        val outstandingBal = (del.totalAmount - totalPaid).coerceAtLeast(0.0)

        MobileFormDialog(
            title = "Delivery ${del.deliveryNumber}",
            subtitle = "${del.date} • ${del.time}",
            hasUnsavedChanges = false,
            onDismiss = { selectedDeliveryId = null },
            footerContent = {
                // Aligned Iconic Action Bar inside Delivery Detail
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DetailIconActionButton(
                            icon = Icons.Default.Chat,
                            label = "WhatsApp",
                            containerColor = SuccessGreen,
                            contentColor = Color.White,
                            onClick = {
                                val itemsStr = del.items.joinToString("\n") { item ->
                                    val size = bottleSizes.find { it.id == item.bottleSizeId }
                                    val isRet = size?.isReturnableBottle ?: (item.bottleSizeId in listOf("size_19l", "size_12l"))
                                    val retTxt = if (isRet) " (Empty Ret: ${item.emptyCollected})" else ""
                                    "• ${item.bottleName}: ${item.quantity} x Rs.${item.unitPrice.toInt()} = Rs.${item.lineTotal.toInt()}$retTxt"
                                }
                                val payStr = if (relatedPayments.isNotEmpty()) {
                                    relatedPayments.mapIndexed { idx, p ->
                                        val matched = p.allocations.find {
                                            (del.invoiceId.isNotBlank() && it.invoiceId == del.invoiceId) ||
                                                it.invoiceId == del.id ||
                                                it.invoiceId == "inv_${del.id}"
                                        }?.allocatedAmount ?: p.amount
                                        "${idx + 1}. ${p.date}: Rs.${matched.toInt()} (${p.paymentMethod.label})"
                                    }.joinToString("\n")
                                } else if (totalPaid > 0) {
                                    "Paid Rs.${totalPaid.toInt()} (${del.paymentMethod})"
                                } else {
                                    "Credit / Unpaid"
                                }
                                val msg = """
                                    *AAB E BARAAN RO PLANT*
                                    Voucher: ${del.deliveryNumber}
                                    Customer: ${del.customerName}
                                    Date: ${del.date} ${del.time}
                                    
                                    *Items:*
                                    $itemsStr
                                    
                                    Total Bill: Rs. ${del.totalAmount.toInt()}
                                    Paid: Rs. ${totalPaid.toInt()}
                                    Due: Rs. ${outstandingBal.toInt()}
                                    
                                    *Payments:*
                                    $payStr
                                """.trimIndent()
                                val cleanNum = del.customerPhone.replace("-", "").replace(" ", "").replace("+", "")
                                if (cleanNum.isNotBlank()) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNum?text=${Uri.encode(msg)}")))
                                    } catch (_: Exception) {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, msg)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share via WhatsApp"))
                                    }
                                } else {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, msg)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share via WhatsApp"))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("whatsapp_share_delivery_btn")
                        )

                        DetailIconActionButton(
                            icon = Icons.Default.PictureAsPdf,
                            label = "PDF",
                            containerColor = WaterSkyLight,
                            contentColor = WaterNavyPrimary,
                            onClick = {
                                try {
                                    val pdfFile = PdfGenerator.generateDeliveryVoucherPdf(
                                        context = context,
                                        delivery = del,
                                        payments = relatedPayments,
                                        bottleSizes = bottleSizes
                                    )
                                    previewPdfRequest = PdfPreviewRequest(
                                        file = pdfFile,
                                        title = "Delivery Slip #${del.deliveryNumber}",
                                        chooserTitle = "Share Delivery PDF #${del.deliveryNumber}",
                                        whatsAppCaption = "Delivery Voucher #${del.deliveryNumber} - ${del.customerName}"
                                    )
                                } catch (t: Throwable) {
                                    Toast.makeText(context, "Unable to open PDF: ${t.message ?: "Error"}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pdf_share_delivery_btn")
                        )

                        if (outstandingBal > 0.0 && canReceivePayment) {
                            DetailIconActionButton(
                                icon = Icons.Default.Payments,
                                label = "Pay Now",
                                containerColor = WaterSkyButton,
                                contentColor = WaterSkyButtonContent,
                                onClick = { deliveryForDirectPayment = del },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("direct_receive_payment_btn")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (canEditDelivery) {
                            DetailIconActionButton(
                                icon = Icons.Default.Edit,
                                label = "Edit",
                                containerColor = WaterSkyLight,
                                contentColor = WaterNavyPrimary,
                                onClick = {
                                    deliveryToEdit = del
                                    selectedDeliveryId = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_edit_delivery_btn")
                            )
                        }

                        if (canDeleteDelivery) {
                            DetailIconActionButton(
                                icon = Icons.Default.Delete,
                                label = "Delete",
                                containerColor = ErrorRedLight,
                                contentColor = ErrorRed,
                                onClick = {
                                    deliveryToDelete = del
                                    selectedDeliveryId = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_delete_delivery_btn")
                            )
                        }

                        DetailIconActionButton(
                            icon = Icons.Default.Close,
                            label = "Close",
                            containerColor = Color(0xFFE2E8F0),
                            contentColor = TextPrimaryDark,
                            onClick = { selectedDeliveryId = null },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_close_btn")
                        )
                    }
                }
            }
        ) {
            // Status Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = del.customerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = WaterNavyPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(
                    text = when {
                        del.totalAmount <= 0 -> "N/A"
                        outstandingBal <= 0 -> "Paid"
                        totalPaid > 0 -> "Partial Credit"
                        else -> "Credit"
                    },
                    backgroundColor = when {
                        del.totalAmount <= 0 -> Color(0xFFF1F5F9)
                        outstandingBal <= 0 -> SuccessGreenLight
                        totalPaid > 0 -> WarningOrangeLight
                        else -> ErrorRedLight
                    },
                    textColor = when {
                        del.totalAmount <= 0 -> TextMuted
                        outstandingBal <= 0 -> SuccessGreen
                        totalPaid > 0 -> WarningOrange
                        else -> ErrorRed
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WaterSkyLight.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Area: ${del.customerArea} • Phone: ${del.customerPhone.ifBlank { "N/A" }}",
                        fontSize = 10.sp,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "Staff: ${del.staffName.ifBlank { "Staff" }} • Mode: ${del.paymentMethod}",
                        fontSize = 10.sp,
                        color = TextSecondaryDark
                    )
                }
            }

            Text("Bottles Detail:", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = WaterNavyDark)
            del.items.forEach { item ->
                val size = bottleSizes.find { it.id == item.bottleSizeId }
                val isRet = size?.isReturnableBottle ?: (item.bottleSizeId in listOf("size_19l", "size_12l"))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.bottleName} x${item.quantity}" + if (isRet) " (Ret ${item.emptyCollected})" else " (No Ret)",
                        fontSize = 9.8.sp,
                        color = TextPrimaryDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Rs. ${item.lineTotal.toInt()}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            HorizontalDivider(color = DividerColor)

            if (del.deliveryCharges > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Delivery Charges:", fontSize = 10.sp, color = TextSecondaryDark)
                    Text("Rs. ${del.deliveryCharges.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Bill:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = WaterNavyPrimary)
                Text("Rs. ${del.totalAmount.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = WaterNavyPrimary)
            }

            HorizontalDivider(color = DividerColor)

            // Payment Installments History (Full & Partial payments recorded later)
            val unsettledRelated = relatedPayments.filter { !it.isAcknowledged }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Payment Records (وصولی تفصیل):", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = WaterNavyDark)
                if (unsettledRelated.isNotEmpty() && canApprovePayment) {
                    Surface(
                        onClick = { viewModel.acknowledgePayments(unsettledRelated.map { it.id }) },
                        shape = RoundedCornerShape(6.dp),
                        color = SuccessGreen,
                        modifier = Modifier.testTag("detail_mark_clear_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Mark Clear", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
            if (relatedPayments.isNotEmpty()) {
                relatedPayments.forEachIndexed { idx, p ->
                    val allocAmt = p.allocations.find {
                        (del.invoiceId.isNotBlank() && it.invoiceId == del.invoiceId) ||
                            it.invoiceId == del.id ||
                            it.invoiceId == "inv_${del.id}"
                    }?.allocatedAmount ?: p.amount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. ${p.receiptNumber} (${p.date})",
                            fontSize = 9.5.sp,
                            color = TextSecondaryDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Rs. ${allocAmt.toInt()} (${p.paymentMethod.label.split(" ")[0]})",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (canApprovePayment) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (p.isAcknowledged) SuccessGreenLight else WarningOrangeLight
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (p.isAcknowledged) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                                            contentDescription = if (p.isAcknowledged) "Clear" else "Unsettle",
                                            tint = if (p.isAcknowledged) SuccessGreen else WarningOrange,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = if (p.isAcknowledged) "Clear" else "Unsettle",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (p.isAcknowledged) SuccessGreen else WarningOrange
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (totalPaid > 0.0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("On Delivery (${del.date}):", fontSize = 9.5.sp, color = TextSecondaryDark)
                    Text("Rs. ${totalPaid.toInt()} (${del.paymentMethod})", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                }
            } else {
                Text("No payment received yet (ادھار)", fontSize = 10.sp, color = ErrorRed, fontWeight = FontWeight.SemiBold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Paid:", fontSize = 10.5.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                Text("Rs. ${totalPaid.toInt()}", fontSize = 10.5.sp, color = SuccessGreen, fontWeight = FontWeight.ExtraBold)
            }
            if (outstandingBal > 0.0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Remaining Due:", fontSize = 10.5.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                    Text("Rs. ${outstandingBal.toInt()}", fontSize = 11.sp, color = ErrorRed, fontWeight = FontWeight.ExtraBold)
                }
            }

            if (del.notes.isNotBlank()) {
                Text("Note: ${del.notes}", fontSize = 10.sp, color = TextSecondaryDark)
            }

            EditMetadataBadge(
                lastEditedAt = del.lastEditedAt,
                lastEditedBy = del.lastEditedBy
            )
        }
    }

    // Admin Delete Confirmation
    if (deliveryToDelete != null) {
        val target = deliveryToDelete!!
        AlertDialog(
            onDismissRequest = { deliveryToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(34.dp))
            },
            title = {
                Text("Delete Delivery #${target.deliveryNumber}?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ErrorRed)
            },
            text = {
                Text(
                    "Delete delivery for ${target.customerName}? Stock, invoice, and customer dues will be reverted.",
                    fontSize = 11.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDelivery(target.id)
                        deliveryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deliveryToDelete = null }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }

    // Admin Full Edit Delivery Dialog (edits slip, date, bottle lines, charges, payment, notes)
    if (deliveryToEdit != null) {
        EditDeliveryDialog(
            delivery = deliveryToEdit!!,
            bottleSizes = bottleSizes,
            viewModel = viewModel,
            onDismiss = { deliveryToEdit = null },
            onSave = { updated ->
                viewModel.updateDelivery(updated)
                deliveryToEdit = null
            }
        )
    }

    // Pay Now Dialog for Credit / Partial Credit Deliveries (supports partial installments or full payment)
    if (deliveryForDirectPayment != null) {
        val targetDel = deliveryForDirectPayment!!
        DirectReceivePaymentDialog(
            delivery = targetDel,
            onDismiss = { deliveryForDirectPayment = null },
            onConfirm = { amount, method ->
                viewModel.markDeliveryAsPaid(targetDel.id, amount, method)
                deliveryForDirectPayment = null
            }
        )
    }

    previewPdfRequest?.let { req ->
        PdfPreviewDialog(
            request = req,
            onDismiss = { previewPdfRequest = null }
        )
    }

    if (showCustomDateDialog) {
        FromToDateRangeDialog(
            initialFromDate = customStartDate,
            initialToDate = customEndDate,
            title = "Delivery Custom Date (تاریخ منتخب کریں)",
            onDismiss = { showCustomDateDialog = false },
            onConfirm = { from, to ->
                customStartDate = from
                customEndDate = to
                selectedFilter = DeliveryDateFilter.CUSTOM
                showCustomDateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeliveryDialog(
    delivery: Delivery,
    bottleSizes: List<BottleSize>,
    viewModel: WaterPlantViewModel,
    onDismiss: () -> Unit,
    onSave: (Delivery) -> Unit
) {
    var editDate by remember { mutableStateOf(delivery.date) }
    var showDatePicker by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(delivery.items) }
    val inventory by viewModel.inventory.collectAsState()
    val selectableOptions = remember(bottleSizes, inventory) {
        buildDeliverySelectableOptions(bottleSizes, inventory)
    }
    var deliveryChargesText by remember { mutableStateOf(delivery.deliveryCharges.toInt().toString()) }
    var paymentCollectedText by remember { mutableStateOf(delivery.paymentCollected.toInt().toString()) }
    var isOnlineMode by remember {
        mutableStateOf(
            delivery.paymentMethod.contains("Online", ignoreCase = true) ||
                delivery.paymentMethod.contains("Bank", ignoreCase = true)
        )
    }
    var notesText by remember { mutableStateOf(delivery.notes) }
    var showAddSizeDropdown by remember { mutableStateOf(false) }
    var pendingSaveConfirm by remember { mutableStateOf<Delivery?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val subtotal = remember(items) { items.sumOf { it.lineTotal } }
    val currentTotal = remember(subtotal, deliveryChargesText) {
        subtotal + (deliveryChargesText.toDoubleOrNull() ?: 0.0)
    }

    if (pendingSaveConfirm != null) {
        val candidate = pendingSaveConfirm!!
        AlertDialog(
            onDismissRequest = { pendingSaveConfirm = null },
            containerColor = Color.White,
            icon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(30.dp)) },
            title = { Text("Confirm Edit Changes?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WaterNavyPrimary) },
            text = {
                Text(
                    text = "Save edited Delivery ${candidate.deliveryNumber} for ${candidate.customerName} (Total: Rs. ${candidate.totalAmount.toInt()}, Paid: Rs. ${candidate.paymentCollected.toInt()})?\n\nکیا آپ واقعی اس ڈیلیوری میں تبدیلی محفوظ کرنا چاہتے ہیں؟",
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
                    modifier = Modifier.testTag("confirm_edit_delivery_dialog_btn")
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
        title = "Edit Delivery ${delivery.deliveryNumber}",
        subtitle = "${delivery.customerName} • Updates slip & detail",
        hasUnsavedChanges = true,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val charges = deliveryChargesText.toDoubleOrNull() ?: 0.0
                    val paid = (paymentCollectedText.toDoubleOrNull() ?: 0.0).coerceAtMost(currentTotal)
                    val totalDelivered = items.sumOf { it.quantity }
                    val totalEmpty = items.sumOf { item ->
                        val s = bottleSizes.find { it.id == item.bottleSizeId }
                        val isRet = if (item.variantId.isNotBlank()) {
                            false
                        } else {
                            s?.isReturnableBottle ?: (item.bottleSizeId in listOf("size_19l", "size_12l"))
                        }
                        if (isRet) item.emptyCollected else 0
                    }
                    val modeLabel = if (isOnlineMode) "Online" else "Cash"
                    val methodStr = when {
                        paid <= 0.0 -> "Unpaid"
                        paid >= currentTotal && currentTotal > 0 -> modeLabel
                        else -> "Partial ($modeLabel)"
                    }
                    val updated = delivery.copy(
                        date = editDate.ifBlank { delivery.date },
                        items = items.filter { it.quantity > 0 || it.emptyCollected > 0 },
                        totalFullDelivered = totalDelivered,
                        totalEmptyCollected = totalEmpty,
                        deliveryCharges = charges,
                        subtotal = subtotal,
                        totalAmount = currentTotal,
                        paymentCollected = paid,
                        paymentMethod = methodStr,
                        notes = notesText
                    )
                    pendingSaveConfirm = updated
                },
                enabled = items.any { it.quantity > 0 },
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_edit_delivery_btn")
            ) {
                Text("Save Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        // Date Row with Calendar Picker
        EditableDateField(
            label = "Delivery Date (ڈیلیوری کی تاریخ)*",
            dateValue = editDate,
            onDateSelected = { editDate = it }
        )

        // Bottle Items + Add Extra Size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bottles & Returns:", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = WaterNavyDark)

            val remainingOptions = selectableOptions.filter { opt ->
                items.none { it.bottleSizeId == opt.bottleSizeId && it.variantId == opt.variantId }
            }
            if (remainingOptions.isNotEmpty()) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = WaterSkyLight,
                        modifier = Modifier
                            .height(26.dp)
                            .clickable { showAddSizeDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Add Size", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                        }
                    }
                    DropdownMenu(
                        expanded = showAddSizeDropdown,
                        onDismissRequest = { showAddSizeDropdown = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        remainingOptions.forEach { opt ->
                            val rate = viewModel.getCustomerRateForDate(delivery.customerId, opt.rateKey, editDate)
                            DropdownMenuItem(
                                text = { Text("${opt.displayName} — Rs.${rate.toInt()}", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    items = items + DeliveryItem(
                                        bottleSizeId = opt.bottleSizeId,
                                        variantId = opt.variantId,
                                        packSize = opt.packSize,
                                        bottleName = opt.displayName,
                                        quantity = 0,
                                        unitPrice = rate,
                                        emptyCollected = 0,
                                        lineTotal = 0.0
                                    )
                                    showAddSizeDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        items.forEachIndexed { index, item ->
            val sizeObj = bottleSizes.find { it.id == item.bottleSizeId }
            val isRet = if (item.variantId.isNotBlank()) {
                false
            } else {
                sizeObj?.let {
                    it.usageType == BottleUsageType.MULTIPLE_TIME ||
                        (it.usageType == BottleUsageType.CUSTOM && it.isReturnableBottle)
                } ?: (item.bottleSizeId in listOf("size_19l", "size_12l"))
            }
            Surface(
                color = WaterSkyLight.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.bottleName} (Rs.${item.unitPrice.toInt()})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = WaterNavyDark,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Rs. ${item.lineTotal.toInt()}", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = WaterNavyPrimary)
                            if (items.size > 1) {
                                Surface(
                                    shape = CircleShape,
                                    color = ErrorRedLight,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { items = items.filterIndexed { i, _ -> i != index } }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactFormTextField(
                            value = item.quantity.toString(),
                            onValueChange = { newQtyStr ->
                                val normalized = normalizeQtyDigits(newQtyStr)
                                val qty = normalized.toIntOrNull() ?: 0
                                items = items.toMutableList().also { list ->
                                    list[index] = item.copy(
                                        quantity = qty,
                                        lineTotal = qty * item.unitPrice,
                                        emptyCollected = if (isRet) item.emptyCollected else 0
                                    )
                                }
                            },
                            label = "Qty",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            height = 36.dp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isRet) {
                            CompactFormTextField(
                                value = item.emptyCollected.toString(),
                                onValueChange = { newEmptyStr ->
                                    val normalized = normalizeQtyDigits(newEmptyStr)
                                    val empty = normalized.toIntOrNull() ?: 0
                                    items = items.toMutableList().also { list ->
                                        list[index] = item.copy(emptyCollected = empty)
                                    }
                                },
                                label = "Empty Ret",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                height = 36.dp,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = TextMuted, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("0 (Locked)", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactFormTextField(
                value = deliveryChargesText,
                onValueChange = { deliveryChargesText = it.filter { ch -> ch.isDigit() } },
                label = "Delivery Charges",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                height = 38.dp,
                modifier = Modifier.weight(1f)
            )
            CompactFormTextField(
                value = paymentCollectedText,
                onValueChange = { paymentCollectedText = it.filter { ch -> ch.isDigit() } },
                label = "Paid Amount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                height = 38.dp,
                modifier = Modifier.weight(1f)
            )
        }

        // Payment Mode Toggle (Cash / Online)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (!isOnlineMode) WaterNavyPrimary else WaterSkyLight,
                modifier = Modifier.weight(1f).height(32.dp).clickable { isOnlineMode = false }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Cash", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (!isOnlineMode) Color.White else WaterNavyDark)
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isOnlineMode) WaterNavyPrimary else WaterSkyLight,
                modifier = Modifier.weight(1f).height(32.dp).clickable { isOnlineMode = true }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Online", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isOnlineMode) Color.White else WaterNavyDark)
                }
            }
        }

        CompactFormTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = "Delivery Notes",
            height = 38.dp,
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            color = WaterNavyDark,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Updated Total Bill:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Rs. ${currentTotal.toInt()}", color = WaterCyanBright, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
            }
        }
    }

    if (showDatePicker) {
        val initMillis = remember(editDate) {
            try {
                dateFormat.parse(editDate)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { editDate = dateFormat.format(Date(it)) }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun SmartSizeBadge(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = label,
                fontSize = 8.5.sp,
                color = WaterSkyLight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyDeliverySectionCard(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth(0.95f)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                color = TextSecondaryDark,
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun DeliveryCardItem(
    delivery: Delivery,
    bottleSizes: List<BottleSize>,
    relatedPayments: List<Payment> = emptyList(),
    isAdmin: Boolean,
    isViewOnly: Boolean,
    isNew: Boolean = false,
    onDismissNew: () -> Unit = {},
    onClick: () -> Unit,
    onSharePdf: () -> Unit,
    onMarkClear: () -> Unit = {},
    onReceivePayment: (Delivery) -> Unit,
    onEdit: (Delivery) -> Unit,
    onDelete: (Delivery) -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingDue = (delivery.totalAmount - delivery.paymentCollected).coerceAtLeast(0.0)
    val isCredit = delivery.totalAmount > 0 && remainingDue > 0.0
    val isFullCredit = delivery.totalAmount > 0 && delivery.paymentCollected <= 0.0
    val hasUnsettledPayment = relatedPayments.any { !it.isAcknowledged }
    val allPaymentsCleared = relatedPayments.isNotEmpty() && relatedPayments.all { it.isAcknowledged }

    val itemsCompact = remember(delivery.items, bottleSizes) {
        delivery.items.joinToString(" • ") { item ->
            if (item.variantId.isNotBlank()) {
                "${item.quantity}x ${item.bottleName}"
            } else {
                val size = bottleSizes.find { it.id == item.bottleSizeId }
                val short = if (size != null) shortBottleLabel(size) else item.bottleName.take(6)
                "${item.quantity}x $short"
            }
        }
    }

    Card(
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = if (isCredit) androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.45f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .clickable { onClick() }
            .testTag("delivery_card_${delivery.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Row 1: Voucher # + NEW Badge (for other users) + Customer Name + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = WaterSkyLight
                    ) {
                        Text(
                            text = delivery.deliveryNumber,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyPrimary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (isNew) {
                        Spacer(modifier = Modifier.width(4.dp))
                        NewItemBadge(
                            onClick = onDismissNew,
                            modifier = Modifier.testTag("new_delivery_badge_${delivery.id}")
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = delivery.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = WaterNavyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Rs. ${delivery.totalAmount.toInt()}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.5.sp,
                    color = WaterNavyPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Row 2: Items + Empties + Area/Date | Status + Due
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$itemsCompact • Ret:${delivery.totalEmptyCollected} • ${delivery.date}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isCredit) {
                        Text(
                            text = "Due ${remainingDue.toInt()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    val (statusLabel, statusBg, statusFg) = when {
                        delivery.totalAmount <= 0.0 -> Triple("N/A", Color(0xFFF1F5F9), TextMuted)
                        remainingDue <= 0.0 -> Triple("Paid", SuccessGreenLight, SuccessGreen)
                        isFullCredit -> Triple("Credit", ErrorRedLight, ErrorRed)
                        else -> Triple("Partial", WarningOrangeLight, WarningOrange)
                    }
                    StatusBadge(
                        text = statusLabel,
                        backgroundColor = statusBg,
                        textColor = statusFg
                    )
                }
            }

            // Row 3: Unsettle / Clear Badges ONLY visible to Admin / Payment Approver
            val showSettlementRow = isAdmin && (hasUnsettledPayment || allPaymentsCleared)
            if (showSettlementRow || isCredit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAdmin && hasUnsettledPayment) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = WarningOrangeLight,
                            modifier = Modifier.testTag("badge_delivery_unsettle_${delivery.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = "Unsettle",
                                    tint = WarningOrange,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Unsettle",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else if (isAdmin && allPaymentsCleared) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SuccessGreenLight,
                            modifier = Modifier.testTag("badge_delivery_clear_${delivery.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Clear",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Clear",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Tap for details ›",
                        fontSize = 8.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CardSmallIconButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        modifier = modifier
            .height(26.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(13.dp))
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DetailIconActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 9.5.sp,
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
fun DirectReceivePaymentDialog(
    delivery: Delivery,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: PaymentMethod) -> Unit
) {
    val remainingDue = (delivery.totalAmount - delivery.paymentCollected).coerceAtLeast(0.0)
    var amountText by remember { mutableStateOf(remainingDue.toInt().toString()) }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var pendingPayConfirm by remember { mutableStateOf<Pair<Double, PaymentMethod>?>(null) }

    if (pendingPayConfirm != null) {
        val (confirmAmt, confirmMethod) = pendingPayConfirm!!
        AlertDialog(
            onDismissRequest = { pendingPayConfirm = null },
            containerColor = Color.White,
            icon = { Icon(Icons.Default.Payments, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(30.dp)) },
            title = { Text("Confirm Payment?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SuccessGreen) },
            text = {
                Text(
                    text = "Receive Rs. ${confirmAmt.toInt()} (${if (confirmMethod == PaymentMethod.CASH) "Cash" else "Online"}) from ${delivery.customerName} for Delivery ${delivery.deliveryNumber}?\n\nکیا آپ واقعی Rs. ${confirmAmt.toInt()} وصولی کی تصدیق کرتے ہیں؟",
                    fontSize = 11.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isSubmitting) {
                            isSubmitting = true
                            pendingPayConfirm = null
                            onConfirm(confirmAmt, confirmMethod)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_pay_now_dialog_btn")
                ) {
                    Text("Yes, Confirm Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPayConfirm = null }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        )
    }

    MobileFormDialog(
        title = "Pay Now (وصولی)",
        subtitle = "${delivery.customerName} • ${delivery.deliveryNumber}",
        hasUnsavedChanges = false,
        onDismiss = onDismiss,
        accentColor = SuccessGreen,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) {
                        errorMsg = "Enter amount greater than 0"
                    } else if (amt > remainingDue) {
                        errorMsg = "Amount cannot exceed due Rs. ${remainingDue.toInt()}"
                    } else {
                        pendingPayConfirm = amt to selectedMethod
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_direct_payment_btn")
            ) {
                Text("Confirm Payment", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    ) {
        Surface(
            color = WaterSkySurface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total: Rs.${delivery.totalAmount.toInt()}", fontSize = 9.5.sp, color = TextPrimaryDark)
                Text("Paid: Rs.${delivery.paymentCollected.toInt()}", fontSize = 9.5.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                Text("Due: Rs.${remainingDue.toInt()}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
            }
        }

        CompactFormTextField(
            value = amountText,
            onValueChange = {
                amountText = it.filter { ch -> ch.isDigit() }
                errorMsg = null
            },
            label = "Amount Received (Partial or Full)*",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            height = 40.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("direct_payment_amount_input")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = WaterSkyLight,
                modifier = Modifier.weight(1f).height(28.dp).clickable { amountText = remainingDue.toInt().toString() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Full", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                }
            }
            if (remainingDue >= 2) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = WaterSkyLight,
                    modifier = Modifier.weight(1f).height(28.dp).clickable { amountText = (remainingDue / 2).toInt().toString() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("50%", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                    }
                }
            }
        }

        Text("Payment Mode:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isCash = selectedMethod == PaymentMethod.CASH
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isCash) WaterNavyPrimary else WaterSkyLight,
                modifier = Modifier.weight(1f).height(34.dp).clickable { selectedMethod = PaymentMethod.CASH }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Cash", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isCash) Color.White else WaterNavyDark, maxLines = 1, softWrap = false)
                }
            }
            val isOnline = selectedMethod != PaymentMethod.CASH
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isOnline) WaterNavyPrimary else WaterSkyLight,
                modifier = Modifier.weight(1f).height(34.dp).clickable { selectedMethod = PaymentMethod.BANK_TRANSFER }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Online", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isOnline) Color.White else WaterNavyDark, maxLines = 1, softWrap = false)
                }
            }
        }

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
