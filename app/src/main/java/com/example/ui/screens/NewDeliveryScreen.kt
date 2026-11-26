package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.components.CompactFormTextField
import com.example.ui.components.MobileFormDialog
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeliverySelectableOption(
    val rateKey: String,        // variantId if packed variant, else bottleSizeId
    val bottleSizeId: String,
    val variantId: String,
    val packSize: Int,
    val displayName: String,
    val shortName: String,
    val isReturnable: Boolean,
    val stdRefillPrice: Double
)

fun buildDeliverySelectableOptions(
    bottleSizes: List<BottleSize>,
    inventory: List<BottleInventory>
): List<DeliverySelectableOption> {
    val result = mutableListOf<DeliverySelectableOption>()

    // Order standard sizes cleanly: 19L, 12L, 6L, 500ml (Carton & Packet), 1.5L (Carton & Packet), then custom sizes
    val activeSizes = bottleSizes.filter { it.active }.sortedBy { size ->
        when (size.id) {
            "size_19l" -> 0
            "size_12l" -> 1
            "size_6l" -> 2
            "size_500ml" -> 3
            "size_1_5l" -> 4
            else -> 10
        }
    }

    activeSizes.forEach { size ->
        val isRet = size.usageType == BottleUsageType.MULTIPLE_TIME ||
            (size.usageType == BottleUsageType.CUSTOM && size.isReturnableBottle)
        val inv = inventory.find { it.bottleSizeId == size.id }
        val variants = inv?.packVariants.orEmpty()
        val baseShort = shortBottleLabel(size)
        val cleanBaseName = normalizeLegacyBottleName(size.name)

        val isMultiPackStandard = size.id == "size_500ml" || size.id == "size_1_5l" ||
            baseShort.equals("500ml", ignoreCase = true) || baseShort.equals("1.5L", ignoreCase = true)

        if (!isRet && isMultiPackStandard) {
            val defaultCartonPackSize = if (baseShort.equals("500ml", ignoreCase = true)) 24 else 12
            val defaultPacketPackSize = if (baseShort.equals("500ml", ignoreCase = true)) 12 else 6

            val cartonVariant = variants.firstOrNull { it.packSize > 1 && it.containerType == PackContainerType.CARTON }
            val packetVariant = variants.firstOrNull { it.packSize > 1 && it.containerType == PackContainerType.PACKET }

            val cartonLabel = "$baseShort Carton"
            val packetLabel = "$baseShort Packet"

            result.add(
                DeliverySelectableOption(
                    rateKey = cartonVariant?.variantId ?: "${size.id}_carton_$defaultCartonPackSize",
                    bottleSizeId = size.id,
                    variantId = cartonVariant?.variantId ?: "${size.id}_carton_$defaultCartonPackSize",
                    packSize = cartonVariant?.packSize ?: defaultCartonPackSize,
                    displayName = cartonLabel,
                    shortName = cartonLabel,
                    isReturnable = false,
                    stdRefillPrice = size.defaultRefillPrice
                )
            )
            result.add(
                DeliverySelectableOption(
                    rateKey = packetVariant?.variantId ?: "${size.id}_packet_$defaultPacketPackSize",
                    bottleSizeId = size.id,
                    variantId = packetVariant?.variantId ?: "${size.id}_packet_$defaultPacketPackSize",
                    packSize = packetVariant?.packSize ?: defaultPacketPackSize,
                    displayName = packetLabel,
                    shortName = packetLabel,
                    isReturnable = false,
                    stdRefillPrice = size.defaultRefillPrice
                )
            )
        } else if (!isRet && variants.any { it.packSize > 1 }) {
            // Other single-use sizes with multi-bottle pack variants: show clean Carton / Packet tabs
            val groupedByContainer = variants.filter { it.packSize > 1 }.distinctBy { it.containerType }
            groupedByContainer.forEach { v ->
                val cleanLabel = formatPackVariantShortLabel(cleanBaseName, size.capacityLitres, v.packSize, v.containerType)
                result.add(
                    DeliverySelectableOption(
                        rateKey = v.variantId,
                        bottleSizeId = size.id,
                        variantId = v.variantId,
                        packSize = v.packSize.coerceAtLeast(1),
                        displayName = cleanLabel,
                        shortName = cleanLabel,
                        isReturnable = false,
                        stdRefillPrice = size.defaultRefillPrice
                    )
                )
            }
        } else {
            // Returnable (19L, 12L) or single-bottle (6L, etc.) — clean short label, NEVER "1 Bottle" or "(1B)"
            val singleVariant = if (!isRet) variants.firstOrNull { it.packSize <= 1 } else null
            result.add(
                DeliverySelectableOption(
                    rateKey = singleVariant?.variantId ?: size.id,
                    bottleSizeId = size.id,
                    variantId = singleVariant?.variantId ?: "",
                    packSize = 1,
                    displayName = baseShort,
                    shortName = baseShort,
                    isReturnable = isRet,
                    stdRefillPrice = size.defaultRefillPrice
                )
            )
        }
    }
    return result
}

fun normalizeQtyDigits(incoming: String): String {
    val digits = incoming.filter { it.isDigit() }
    return if (digits.length > 1) digits.trimStart('0').ifEmpty { "0" } else digits
}

data class DeliveryLineState(
    val bottleSizeId: String,
    val variantId: String = "",
    val packSize: Int = 1,
    val rateKey: String = bottleSizeId,
    val bottleName: String,
    val shortName: String,
    val isReturnable: Boolean,
    val unitPrice: Double,
    var deliveredQtyText: String = "0",
    var emptyCollectedText: String = "0"
) {
    val deliveredQty: Int get() = deliveredQtyText.toIntOrNull() ?: 0
    val emptyQty: Int get() = if (isReturnable) (emptyCollectedText.toIntOrNull() ?: 0) else 0
    val lineTotal: Double get() = deliveredQty * unitPrice
}

fun shortBottleLabel(size: BottleSize): String {
    val n = normalizeLegacyBottleName(size.name).trim()
    return when {
        size.id == "size_19l" || n.contains("19", ignoreCase = true) -> "19L"
        size.id == "size_12l" || n.contains("12.5", ignoreCase = true) || n.contains("12", ignoreCase = true) -> "12L"
        size.id == "size_6l" || n.startsWith("6 ", ignoreCase = true) || n.contains("6 Lit", ignoreCase = true) || n.equals("6L", ignoreCase = true) -> "6L"
        size.id == "size_1_5l" || n.contains("1.5", ignoreCase = true) -> "1.5L"
        size.id == "size_500ml" || n.contains("500", ignoreCase = true) -> "500ml"
        size.capacityLitres > 0 && n.contains("ml", ignoreCase = true) -> {
            val match = Regex("(\\d+(?:\\.\\d+)?)\\s*ml", RegexOption.IGNORE_CASE).find(n)
            match?.let { "${it.groupValues[1]}ml" } ?: n.take(7)
        }
        n.length <= 7 -> n
        else -> {
            val cap = if (size.capacityLitres == 12.5) 12.0 else size.capacityLitres
            if (cap > 0) {
                if (cap % 1.0 == 0.0) "${cap.toInt()}L" else "${cap}L"
            } else n.take(7)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDeliveryScreen(
    viewModel: WaterPlantViewModel,
    preSelectedCustomer: Customer? = null,
    onDeliverySaved: () -> Unit
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val bottleSizes by viewModel.bottleSizes.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val allCustomerRates by viewModel.customerRates.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val canEditCustomerRates = viewModel.hasPermission(AppModule.CUSTOMER_RATES, PermissionAction.EDIT)

    val selectableOptions = remember(bottleSizes, inventory) {
        buildDeliverySelectableOptions(bottleSizes, inventory)
    }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { dateFormat.format(Date()) }

    var deliveryDate by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }

    var selectedCustomer by remember { mutableStateOf(preSelectedCustomer) }
    var customerDropdownExpanded by remember { mutableStateOf(false) }
    var customerFilterText by remember { mutableStateOf("") }
    var showQuickAddCustomerDialog by remember { mutableStateOf(false) }

    // Multi-bottle line items (added via short-name size/variant buttons; no duplicates allowed)
    var deliveryLines by remember { mutableStateOf<List<DeliveryLineState>>(emptyList()) }

    // Step progression: 1 = Customer & Bottle Sizes, 2 = Payment & Submit
    var currentStep by remember { mutableIntStateOf(1) }

    // Rate-fix dialog state when user selects an option with no fixed rate for selectedCustomer
    var pendingRateOption by remember { mutableStateOf<DeliverySelectableOption?>(null) }

    // Payment state (Step 2)
    var deliveryChargesText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("") }
    var isPayNowTab by remember { mutableStateOf(true) } // true = Pay Now, false = Credit
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) } // CASH or BANK_TRANSFER (Online)
    var immediatePaymentText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // Refresh unit prices automatically whenever deliveryDate changes
    LaunchedEffect(selectedCustomer?.id, deliveryDate, allCustomerRates) {
        val cust = selectedCustomer ?: return@LaunchedEffect
        deliveryLines = deliveryLines.map { line ->
            val updatedRate = viewModel.getCustomerRateForDate(cust.id, line.rateKey, deliveryDate)
            line.copy(unitPrice = updatedRate)
        }
    }

    val charges = deliveryChargesText.toDoubleOrNull() ?: 0.0
    val subtotal = deliveryLines.sumOf { it.lineTotal }
    val rawDiscount = discountText.toDoubleOrNull() ?: 0.0
    val discountVal = rawDiscount.coerceIn(0.0, subtotal + charges)
    val grandTotal = (subtotal + charges - discountVal).coerceAtLeast(0.0)
    val totalFullDelivered = deliveryLines.sumOf { it.deliveredQty }
    val totalEmptyCollected = deliveryLines.sumOf { it.emptyQty }
    val paidAmount = if (!isPayNowTab) 0.0 else (immediatePaymentText.toDoubleOrNull() ?: grandTotal).coerceAtMost(grandTotal)

    fun hasFixedRateForCustomer(customerId: String, rateKey: String): Boolean {
        return viewModel.findCustomerRate(customerId, rateKey, assignedOnly = true) != null
    }

    fun addBottleLineForOption(cust: Customer, option: DeliverySelectableOption, explicitRate: Double? = null) {
        if (deliveryLines.any { it.rateKey == option.rateKey }) return
        val rate = explicitRate ?: viewModel.getCustomerRateForDate(cust.id, option.rateKey, deliveryDate)
        deliveryLines = deliveryLines + DeliveryLineState(
            bottleSizeId = option.bottleSizeId,
            variantId = option.variantId,
            packSize = option.packSize,
            rateKey = option.rateKey,
            bottleName = option.displayName,
            shortName = option.shortName,
            isReturnable = option.isReturnable,
            unitPrice = rate,
            deliveredQtyText = "0",
            emptyCollectedText = "0"
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // -------------------------------------------------------------
        // TOP HEADER BAR WITH CALENDAR ICON (Default Today's Date)
        // -------------------------------------------------------------
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (currentStep == 1) "Add New Delivery" else "Delivery Payment",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = if (deliveryDate == todayStr) "Date: $deliveryDate (Today)" else "Date: $deliveryDate",
                            color = WaterCyanBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Top Calendar Icon Button to change delivery date
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .clickable { showDatePicker = true }
                            .testTag("change_delivery_date_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Select Delivery Date",
                                tint = WaterCyanBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = deliveryDate,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        if (currentStep == 1) {
            // -------------------------------------------------------------
            // STEP 1A: CUSTOMER DROPDOWN SELECTION
            // -------------------------------------------------------------
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
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
                            Text(
                                text = "Select Customer (گاہک منتخب کریں)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                softWrap = false
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = WaterSkyLight,
                                modifier = Modifier
                                    .height(26.dp)
                                    .clickable { showQuickAddCustomerDialog = true }
                                    .testTag("new_delivery_add_customer_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("+ Add", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        // Clickable Customer Picker Field -> opens Focusable Search Dialog so soft keyboard ALWAYS opens
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (customerDropdownExpanded) WaterNavyPrimary else BorderLight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 50.dp)
                                .clickable { customerDropdownExpanded = true }
                                .testTag("delivery_customer_dropdown")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = WaterNavyPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = selectedCustomer?.let { "${it.name} (${it.area})" }
                                            ?: "Choose added customer (Search name, phone)...",
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selectedCustomer != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedCustomer != null) TextPrimaryDark else TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Choose Customer",
                                    tint = WaterNavyPrimary
                                )
                            }
                        }

                        if (customerDropdownExpanded) {
                            val searchFocusRequester = remember { FocusRequester() }
                            val keyboardController = LocalSoftwareKeyboardController.current
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(120)
                                try {
                                    searchFocusRequester.requestFocus()
                                    keyboardController?.show()
                                } catch (_: Exception) {}
                            }

                            Dialog(
                                onDismissRequest = { customerDropdownExpanded = false },
                                properties = DialogProperties(
                                    usePlatformDefaultWidth = false,
                                    decorFitsSystemWindows = true
                                )
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth(0.94f)
                                        .heightIn(max = 520.dp)
                                        .imePadding()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Select Customer (گاہک تلاش کریں)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WaterNavyPrimary
                                            )
                                            IconButton(
                                                onClick = { customerDropdownExpanded = false },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondaryDark)
                                            }
                                        }

                                        OutlinedTextField(
                                            value = customerFilterText,
                                            onValueChange = { customerFilterText = it },
                                            placeholder = { Text("Search name, phone, area...", fontSize = 11.sp) },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = WaterNavyPrimary) },
                                            trailingIcon = {
                                                if (customerFilterText.isNotEmpty()) {
                                                    IconButton(onClick = { customerFilterText = "" }) {
                                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = TextPrimaryDark,
                                                unfocusedTextColor = TextPrimaryDark,
                                                focusedBorderColor = WaterNavyPrimary,
                                                unfocusedBorderColor = BorderLight
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(searchFocusRequester)
                                                .testTag("delivery_customer_search")
                                        )

                                        val filteredCusts = remember(customers, customerFilterText) {
                                            customers.filter {
                                                customerFilterText.isBlank() ||
                                                    it.name.contains(customerFilterText, ignoreCase = true) ||
                                                    it.phone.contains(customerFilterText) ||
                                                    it.area.contains(customerFilterText, ignoreCase = true)
                                            }
                                        }

                                        if (filteredCusts.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("No customers found", fontSize = 11.sp, color = TextMuted)
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f, fill = false),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                items(filteredCusts, key = { it.id }) { cust ->
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (selectedCustomer?.id == cust.id) WaterSkyLight else Color(0xFFF8FAFC),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                keyboardController?.hide()
                                                                selectedCustomer = cust
                                                                deliveryLines = emptyList()
                                                                customerFilterText = ""
                                                                customerDropdownExpanded = false
                                                            }
                                                            .testTag("select_cust_${cust.id}")
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = cust.name + if (!cust.active) " (Inactive)" else "",
                                                                    fontSize = 11.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (cust.active) WaterNavyDark else ErrorRed,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = "${cust.area} • ${cust.phone}",
                                                                    fontSize = 9.5.sp,
                                                                    color = TextSecondaryDark,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                            Text(
                                                                text = if (cust.currentBalance > 0) "Due Rs.${cust.currentBalance.toInt()}" else "Clear",
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (cust.currentBalance > 0) ErrorRed else SuccessGreen,
                                                                maxLines = 1,
                                                                softWrap = false
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

                        // Compact Selected Customer Summary Strip
                        selectedCustomer?.let { cust ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (cust.active) WaterSkyLight.copy(alpha = 0.6f) else ErrorRedLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${cust.name} • ${cust.phone}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (cust.active) WaterNavyPrimary else ErrorRed,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (cust.active) "Due: Rs. ${cust.currentBalance.toInt()} • Empty Held: ${cust.emptyBottlesOwed}" else "Inactive Customer — Activate first before delivery",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (cust.active) TextSecondaryDark else ErrorRed,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // STEP 1B: SHORT-NAME BOTTLE SIZE BUTTONS & QTY / RETURN ROWS
            // -------------------------------------------------------------
            if (selectedCustomer != null && selectedCustomer!!.active) {
                val cust = selectedCustomer!!

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Select Bottle Size (بوتل سائز منتخب کریں)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                softWrap = false
                            )

                            // Short-name Size / Packet Variant Select Buttons Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectableOptions.forEach { option ->
                                    val isAlreadyAdded = deliveryLines.any { it.rateKey == option.rateKey }
                                    val hasRate = hasFixedRateForCustomer(cust.id, option.rateKey)

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isAlreadyAdded -> SuccessGreen
                                            hasRate -> WaterNavyPrimary
                                            else -> WaterSkyLight
                                        },
                                        border = if (!isAlreadyAdded && !hasRate) {
                                            androidx.compose.foundation.BorderStroke(1.dp, WaterAqua)
                                        } else null,
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clickable(enabled = !isAlreadyAdded) {
                                                if (!hasFixedRateForCustomer(cust.id, option.rateKey)) {
                                                    if (canEditCustomerRates) {
                                                        // Rate not fixed for this customer -> open Rate Fix Window first
                                                        pendingRateOption = option
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Rate not set for ${option.shortName}. Admin has restricted rate changes.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } else {
                                                    addBottleLineForOption(cust, option)
                                                }
                                            }
                                            .testTag("size_btn_${option.rateKey}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isAlreadyAdded) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Added",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            } else if (!hasRate) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Fix Rate & Add",
                                                    tint = WaterNavyPrimary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            Text(
                                                text = option.shortName,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAlreadyAdded || hasRate) Color.White else WaterNavyPrimary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            if (deliveryLines.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = WaterSkySurface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Tap any bottle size button above (e.g. 19L, 1.5L) to enter quantity.",
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                }
                            } else {
                                HorizontalDivider(color = DividerColor)

                                deliveryLines.forEachIndexed { index, line ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = WaterSkyLight.copy(alpha = 0.55f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("delivery_line_${line.rateKey}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Header row: Short Size + Rate + Line Total + Small "X" remove button
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
                                                    Surface(
                                                        shape = RoundedCornerShape(5.dp),
                                                        color = WaterNavyPrimary
                                                    ) {
                                                        Text(
                                                            text = line.shortName,
                                                            color = Color.White,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "${line.bottleName} • Rs.${line.unitPrice.toInt()}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = WaterNavyDark,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Rs. ${line.lineTotal.toInt()}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = WaterNavyPrimary,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                    // Small "X" button to remove added bottle size
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = ErrorRedLight,
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clickable {
                                                                deliveryLines = deliveryLines.filterIndexed { i, _ -> i != index }
                                                            }
                                                            .testTag("remove_size_${line.rateKey}")
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Remove ${line.shortName}",
                                                                tint = ErrorRed,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Qty and Empty Return Side-by-Side
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Qty Field (Defaults to 0)
                                                CompactFormTextField(
                                                    value = line.deliveredQtyText,
                                                    onValueChange = { txt ->
                                                        val normalized = normalizeQtyDigits(txt)
                                                        deliveryLines = deliveryLines.toMutableList().also {
                                                            it[index] = it[index].copy(deliveredQtyText = normalized)
                                                        }
                                                    },
                                                    label = "Qty (تعداد)",
                                                    placeholder = "0",
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    height = 38.dp,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("delivered_qty_input_${index}")
                                                )

                                                // Empty Return Field (Editable for Multiple-Time, Locked for One-Time)
                                                if (line.isReturnable) {
                                                    CompactFormTextField(
                                                        value = line.emptyCollectedText,
                                                        onValueChange = { txt ->
                                                            val normalized = normalizeQtyDigits(txt)
                                                            deliveryLines = deliveryLines.toMutableList().also {
                                                                it[index] = it[index].copy(emptyCollectedText = normalized)
                                                            }
                                                        },
                                                        label = "Return Empty (واپسی)",
                                                        placeholder = "0",
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        height = 38.dp,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .testTag("empty_collected_input_${index}")
                                                    )
                                                } else {
                                                    // One-Time Use Bottle: Empty Return Locked to 0 (read-only)
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFFF1F5F9),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(38.dp)
                                                            .testTag("empty_locked_${index}")
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(horizontal = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = "Locked at 0",
                                                                tint = TextMuted,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Empty: 0 (Locked)",
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = TextMuted,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Subtotal Strip & NEXT Button
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = WaterNavyDark,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Bottles: $totalFullDelivered • Return: $totalEmptyCollected",
                                            color = WaterSkyLight,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "Total: Rs. ${subtotal.toInt()}",
                                            color = WaterCyanBright,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        currentStep = 2
                                        if (immediatePaymentText.isEmpty()) {
                                            immediatePaymentText = grandTotal.toInt().toString()
                                        }
                                    },
                                    enabled = totalFullDelivered > 0 || totalEmptyCollected > 0,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WaterAqua,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("bottles_ok_button")
                                ) {
                                    Text(
                                        text = "Next (ادائیگی پر جائیں)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // -------------------------------------------------------------
            // STEP 2: PAYMENTS & BILLING (After clicking Next)
            // -------------------------------------------------------------
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedCustomer?.name ?: "Customer",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            TextButton(
                                onClick = { currentStep = 1 },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = WaterAqua, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Edit Sizes", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterAqua, maxLines = 1, softWrap = false)
                            }
                        }

                        // Total Payments (Bottles Subtotal)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WaterSkyLight.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Payments ($totalFullDelivered Bottles)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimaryDark,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "Rs. ${subtotal.toInt()}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WaterNavyPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Delivery Charges Field
                        CompactFormTextField(
                            value = deliveryChargesText,
                            onValueChange = { txt ->
                                deliveryChargesText = txt.filter { it.isDigit() }
                                if (isPayNowTab) {
                                    val newCharges = deliveryChargesText.toDoubleOrNull() ?: 0.0
                                    val newDisc = (discountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal + newCharges)
                                    val newGrand = (subtotal + newCharges - newDisc).coerceAtLeast(0.0)
                                    immediatePaymentText = newGrand.toInt().toString()
                                }
                            },
                            label = "Delivery Charges (اگر کوئی ہو)",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            height = 40.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delivery_charges_input")
                        )

                        // Discount Field (immediately after Delivery Charges)
                        CompactFormTextField(
                            value = discountText,
                            onValueChange = { txt ->
                                discountText = txt.filter { it.isDigit() }
                                if (isPayNowTab) {
                                    val newCharges = deliveryChargesText.toDoubleOrNull() ?: 0.0
                                    val newDisc = (discountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal + newCharges)
                                    val newGrand = (subtotal + newCharges - newDisc).coerceAtLeast(0.0)
                                    immediatePaymentText = newGrand.toInt().toString()
                                }
                            },
                            label = "Discount (ڈسکاؤنٹ Rs.)",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            height = 40.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delivery_discount_input")
                        )

                        // Total Bill Banner
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = WaterNavyPrimary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Bill (کل بل)",
                                    color = WaterSkyLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "Rs. ${grandTotal.toInt()}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // 2 Tabs: Pay Now | Credit
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(WaterSkySurface, RoundedCornerShape(10.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isPayNowTab) SuccessGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clickable {
                                        isPayNowTab = true
                                        immediatePaymentText = grandTotal.toInt().toString()
                                    }
                                    .testTag("payment_pay_now_btn")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isPayNowTab) Color.White else SuccessGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Pay Now",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPayNowTab) Color.White else WaterNavyDark,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (!isPayNowTab) WarningOrange else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clickable {
                                        isPayNowTab = false
                                        immediatePaymentText = "0"
                                    }
                                    .testTag("payment_udhar_btn")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = if (!isPayNowTab) Color.White else WarningOrange,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Credit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isPayNowTab) Color.White else WaterNavyDark,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        if (isPayNowTab) {
                            // Payment Mode: Cash vs Online + Amount Received
                            Text(
                                text = "Payment Mode (طریقہ ادائیگی)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondaryDark
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isCash = paymentMethod == PaymentMethod.CASH
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCash) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clickable { paymentMethod = PaymentMethod.CASH }
                                        .testTag("pay_method_cash")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, tint = if (isCash) Color.White else WaterNavyDark, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Cash", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (isCash) Color.White else WaterNavyDark, maxLines = 1, softWrap = false)
                                    }
                                }

                                val isOnline = paymentMethod == PaymentMethod.BANK_TRANSFER || paymentMethod == PaymentMethod.JAZZCASH
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isOnline) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clickable { paymentMethod = PaymentMethod.BANK_TRANSFER }
                                        .testTag("pay_method_online")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = if (isOnline) Color.White else WaterNavyDark, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Online", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (isOnline) Color.White else WaterNavyDark, maxLines = 1, softWrap = false)
                                    }
                                }
                            }

                            CompactFormTextField(
                                value = immediatePaymentText,
                                onValueChange = { txt -> immediatePaymentText = txt.filter { it.isDigit() } },
                                label = "Amount Paid Now (Rs.)",
                                placeholder = grandTotal.toInt().toString(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                height = 38.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("payment_amount_input")
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = WarningAmberLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Marked as Credit (ادھار): Rs. ${grandTotal.toInt()} will be recorded as due.",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Delivery Note
                        CompactFormTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = "Delivery Note (اختیاری نوٹ)",
                            placeholder = "e.g. Gate par rakh di",
                            height = 38.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delivery_note_input")
                        )

                        val canSubmit = selectedCustomer != null &&
                            selectedCustomer!!.active &&
                            (totalFullDelivered > 0 || totalEmptyCollected > 0) &&
                            !isSubmitting

                        Button(
                            onClick = {
                                if (canSubmit && selectedCustomer != null) {
                                    isSubmitting = true
                                    val activeItems = deliveryLines
                                        .filter { it.deliveredQty > 0 || it.emptyQty > 0 }
                                        .map { line ->
                                            DeliveryItem(
                                                bottleSizeId = line.bottleSizeId,
                                                variantId = line.variantId,
                                                packSize = line.packSize,
                                                bottleName = line.bottleName,
                                                quantity = line.deliveredQty,
                                                unitPrice = line.unitPrice,
                                                emptyCollected = line.emptyQty,
                                                lineTotal = line.lineTotal
                                            )
                                        }
                                    if (activeItems.isNotEmpty()) {
                                        viewModel.recordDelivery(
                                            customerId = selectedCustomer!!.id,
                                            items = activeItems,
                                            assignedStaffName = currentUser.name.ifEmpty { "Supervisor" },
                                            notes = notes,
                                            deliveryCharges = charges,
                                            immediatePaymentAmount = paidAmount,
                                            paymentMethod = paymentMethod,
                                            deliveryDate = deliveryDate,
                                            discount = discountVal
                                        )
                                        onDeliverySaved()
                                    } else {
                                        isSubmitting = false
                                    }
                                }
                            },
                            enabled = canSubmit,
                            colors = waterPrimaryButtonColors(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("save_delivery_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WaterSkyButtonContent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Submit Delivery",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }

    // Calendar Material 3 DatePickerDialog
    if (showDatePicker) {
        val initialMillis = remember(deliveryDate) {
            try {
                dateFormat.parse(deliveryDate)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            deliveryDate = dateFormat.format(Date(millis))
                        }
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
            DatePicker(state = datePickerState)
        }
    }

    // Quick Add Customer Dialog
    if (showQuickAddCustomerDialog) {
        CustomerFormDialog(
            customer = null,
            onDismiss = { showQuickAddCustomerDialog = false },
            onConfirmWithRate = { newCust, agreedRate ->
                val id = viewModel.addCustomer(newCust)
                if (agreedRate > 0.0) {
                    viewModel.setCustomerPrice(id, "size_19l", agreedRate)
                }
                selectedCustomer = newCust.copy(id = id)
                deliveryLines = emptyList()
                showQuickAddCustomerDialog = false
            }
        )
    }

    // Rate Fix Window when customer has no fixed rate for selected bottle size / pack variant
    if (pendingRateOption != null && selectedCustomer != null) {
        val cust = selectedCustomer!!
        FixCustomerRateDialog(
            customer = cust,
            options = selectableOptions,
            initialOption = pendingRateOption!!,
            initialEffectiveDate = deliveryDate,
            onDismiss = { pendingRateOption = null },
            onConfirm = { chosenOption, fixedRate, effectiveDate ->
                viewModel.assignCustomerBottleRate(
                    customerId = cust.id,
                    bottleSizeId = chosenOption.rateKey,
                    bottleSizeName = chosenOption.displayName,
                    rate = fixedRate,
                    appliedDate = effectiveDate
                )
                addBottleLineForOption(cust, chosenOption, explicitRate = fixedRate)
                pendingRateOption = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixCustomerRateDialog(
    customer: Customer,
    options: List<DeliverySelectableOption>,
    initialOption: DeliverySelectableOption,
    initialEffectiveDate: String,
    onDismiss: () -> Unit,
    onConfirm: (option: DeliverySelectableOption, rate: Double, effectiveDate: String) -> Unit
) {
    var selectedOption by remember { mutableStateOf(initialOption) }
    var sizeMenuExpanded by remember { mutableStateOf(false) }
    var rateText by remember(selectedOption.rateKey) {
        mutableStateOf("0")
    }
    var effectiveDate by remember { mutableStateOf(initialEffectiveDate) }
    var showEffectiveDatePicker by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    MobileFormDialog(
        title = "Fix Customer Rate (ریٹ مقرر کریں)",
        subtitle = "Customer: ${customer.name} • Default rate: Rs. 0",
        hasUnsavedChanges = false,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val rate = rateText.toDoubleOrNull()
                    if (rate == null || rate < 0.0) {
                        errorText = "Please enter valid rate (0 or greater)"
                    } else if (effectiveDate.isBlank()) {
                        errorText = "Please select effective date"
                    } else {
                        onConfirm(selectedOption, rate, effectiveDate.trim())
                    }
                },
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_fix_rate_btn")
            ) {
                Text("OK", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Text(
            text = "Rate is not fixed for this size. Fix rate & effective date to add directly to delivery:",
            fontSize = 10.sp,
            color = TextSecondaryDark
        )

        // Size / Pack Variant Dropdown
        ExposedDropdownMenuBox(
            expanded = sizeMenuExpanded,
            onExpandedChange = { sizeMenuExpanded = !sizeMenuExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedOption.displayName,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Bottle Size / Pack*", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sizeMenuExpanded) },
                textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    focusedBorderColor = WaterNavyPrimary,
                    unfocusedBorderColor = BorderLight
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = sizeMenuExpanded,
                onDismissRequest = { sizeMenuExpanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${opt.displayName} (Std Rs.${opt.stdRefillPrice.toInt()})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        onClick = {
                            selectedOption = opt
                            sizeMenuExpanded = false
                        }
                    )
                }
            }
        }

        CompactFormTextField(
            value = rateText,
            onValueChange = {
                rateText = it.filter { ch -> ch.isDigit() }
                errorText = null
            },
            label = "Agreed Rate (PKR)*",
            placeholder = "e.g. 120",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            height = 40.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("fix_rate_amount_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactFormTextField(
                value = effectiveDate,
                onValueChange = {
                    effectiveDate = it
                    errorText = null
                },
                label = "Effective Date (YYYY-MM-DD)*",
                placeholder = "YYYY-MM-DD",
                height = 40.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("fix_rate_date_input")
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WaterSkyLight,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { showEffectiveDatePicker = true }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Effective Date", tint = WaterNavyPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (errorText != null) {
            Text(errorText!!, color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showEffectiveDatePicker) {
        val initMillis = remember(effectiveDate) {
            try {
                dateFormat.parse(effectiveDate)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showEffectiveDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { effectiveDate = dateFormat.format(Date(it)) }
                        showEffectiveDatePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEffectiveDatePicker = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
