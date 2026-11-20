package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.components.BlinkingOrangeDot
import com.example.ui.components.CompactFormTextField
import com.example.ui.components.NewItemBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    viewModel: WaterPlantViewModel,
    onCustomerClick: (Customer) -> Unit,
    onNavigateToNewDelivery: (Customer) -> Unit,
    onNavigateToReceivePayment: (Customer) -> Unit
) {
    val customers by viewModel.customers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val seenRecordKeys by viewModel.seenRecordKeys.collectAsState()
    val isAdmin = viewModel.hasPermission(AppModule.CUSTOMERS, PermissionAction.DELETE) ||
        viewModel.hasPermission(AppModule.CUSTOMERS, PermissionAction.MANAGE)
    val isViewOnly = !viewModel.hasPermission(AppModule.CUSTOMERS, PermissionAction.ADD) &&
        !viewModel.hasPermission(AppModule.CUSTOMERS, PermissionAction.EDIT)

    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }

    var selectedTypeFilter by remember { mutableStateOf<CustomerType?>(null) }
    var showOnlyDebt by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<Boolean?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var customerForBottleRate by remember { mutableStateOf<Customer?>(null) }
    var customerForSecurityDeposit by remember { mutableStateOf<Customer?>(null) }

    val filteredCustomers = remember(customers, searchQuery, selectedTypeFilter, showOnlyDebt, statusFilter) {
        val q = searchQuery.trim().lowercase()
        val list = customers.filter { cust ->
            val matchesSearch = q.isEmpty() ||
                    cust.name.lowercase().contains(q) ||
                    cust.phone.replace("-", "").contains(q) ||
                    cust.phone.contains(q) ||
                    cust.customerCode.lowercase().contains(q) ||
                    cust.area.lowercase().contains(q) ||
                    cust.address.lowercase().contains(q)

            val matchesType = selectedTypeFilter == null || cust.type == selectedTypeFilter
            val matchesDebt = !showOnlyDebt || viewModel.getCustomerTotalDue(cust) > 0
            val matchesStatus = statusFilter == null || cust.active == statusFilter

            matchesSearch && matchesType && matchesDebt && matchesStatus
        }
        list.reversed()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(WaterSkySurface)
        ) {
            // Header Bar with Compact Corner Search Icon
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Customers",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyDark,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${filteredCustomers.size} Total • Active: ${customers.count { it.active }}",
                                fontSize = 9.5.sp,
                                color = TextSecondaryDark,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Smart Search Icon Button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSearchOpen || searchQuery.isNotEmpty()) WaterNavyPrimary else WaterSkyLight,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        isSearchOpen = !isSearchOpen
                                        if (!isSearchOpen) searchQuery = ""
                                    }
                                    .testTag("search_customer_toggle_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSearchOpen || searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Search,
                                        contentDescription = "Search Customer",
                                        tint = if (isSearchOpen || searchQuery.isNotEmpty()) Color.White else WaterNavyPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (!isViewOnly) {
                                Button(
                                    onClick = { showAddDialog = true },
                                    colors = waterPrimaryButtonColors(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp).testTag("add_customer_top_btn")
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = "Add New Customer", modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "+ Add Customer",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Smart Responsive Expandable Search Field
                    AnimatedVisibility(
                        visible = isSearchOpen || searchQuery.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            CompactFormTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("customer_search_input"),
                                placeholder = "Search name, mobile, ID, area...",
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = WaterNavyPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchQuery = "" },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    tint = TextSecondaryDark,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                isSearchOpen = false
                                                searchQuery = ""
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Close Search",
                                                tint = TextSecondaryDark,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                },
                                height = 40.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter Chips Row (Single-line compact tabs)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (selectedTypeFilter == null && statusFilter == null) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.7f),
                            modifier = Modifier
                                .height(29.dp)
                                .weight(1f)
                                .clickable {
                                    selectedTypeFilter = null
                                    showOnlyDebt = false
                                    statusFilter = null
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                                Text(
                                    text = "All (${customers.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (selectedTypeFilter == null && statusFilter == null) Color.White else WaterNavyDark
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (statusFilter == true) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.7f),
                            modifier = Modifier
                                .height(29.dp)
                                .weight(1f)
                                .clickable {
                                    showOnlyDebt = false
                                    statusFilter = if (statusFilter == true) null else true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                                Text(
                                    text = "Active (${customers.count { it.active }})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (statusFilter == true) Color.White else WaterNavyDark
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (statusFilter == false) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.7f),
                            modifier = Modifier
                                .height(29.dp)
                                .weight(1f)
                                .clickable {
                                    showOnlyDebt = false
                                    statusFilter = if (statusFilter == false) null else false
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                                Text(
                                    text = "Inactive (${customers.count { !it.active }})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (statusFilter == false) Color.White else WaterNavyDark
                                )
                            }
                        }
                    }
                }
            }

            // Customer List (Slightly narrower centered cards)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(0.94f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredCustomers.size} Customers Shown",
                            fontSize = 10.5.sp,
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        val totalDebt = filteredCustomers.sumOf { cust ->
                            val due = viewModel.getCustomerTotalDue(cust)
                            if (due > 0.0) due else 0.0
                        }
                        Text(
                            text = "Market Due: Rs. ${totalDebt.toInt()}",
                            fontSize = 10.sp,
                            color = WarningAmber,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (filteredCustomers.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth(0.94f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.PersonSearch,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "No customers matched your search query",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredCustomers, key = { it.id }) { customer ->
                        val isNewCustomer = viewModel.isRecordNewForCurrentUser(
                            type = "customer",
                            recordId = customer.id,
                            createdByUid = customer.createdByUid,
                            createdByName = customer.createdBy,
                            createdAt = customer.createdAt,
                            seenKeys = seenRecordKeys,
                            user = currentUser
                        )
                        CustomerCard(
                            customer = customer,
                            effectiveDue = viewModel.getCustomerTotalDue(customer),
                            isNew = isNewCustomer,
                            onDismissNew = { viewModel.markRecordSeen("customer", customer.id) },
                            onClick = {
                                viewModel.markRecordSeen("customer", customer.id)
                                onCustomerClick(customer)
                            },
                            onEdit = {
                                viewModel.markRecordSeen("customer", customer.id)
                                customerToEdit = customer
                            },
                            onOpenBottleRates = {
                                viewModel.markRecordSeen("customer", customer.id)
                                customerForBottleRate = customer
                            },
                            onOpenSecurityDeposit = {
                                viewModel.markRecordSeen("customer", customer.id)
                                customerForSecurityDeposit = customer
                            },
                            onDeliver = {
                                viewModel.markRecordSeen("customer", customer.id)
                                onNavigateToNewDelivery(customer)
                            },
                            onReceivePayment = {
                                viewModel.markRecordSeen("customer", customer.id)
                                onNavigateToReceivePayment(customer)
                            },
                            isViewOnly = isViewOnly
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CustomerFormDialog(
            customer = null,
            onDismiss = { showAddDialog = false },
            onConfirmWithRate = { newCust, agreedRate ->
                val newId = viewModel.addCustomer(newCust)
                if (agreedRate > 0) {
                    viewModel.setCustomerPrice(newId, "size_19l", agreedRate)
                }
                showAddDialog = false
            }
        )
    }

    if (customerToEdit != null) {
        val currentRate = viewModel.getCustomerPrice(customerToEdit!!.id, "size_19l")
        CustomerFormDialog(
            customer = customerToEdit,
            initialAgreedRate = currentRate,
            onDismiss = { customerToEdit = null },
            onConfirmWithRate = { updatedCust, agreedRate ->
                viewModel.updateCustomer(updatedCust)
                if (agreedRate > 0) {
                    viewModel.setCustomerPrice(updatedCust.id, "size_19l", agreedRate)
                }
                customerToEdit = null
            }
        )
    }

    if (customerForBottleRate != null) {
        CustomerBottleRateDialog(
            customer = customerForBottleRate!!,
            viewModel = viewModel,
            onDismiss = { customerForBottleRate = null }
        )
    }

    if (customerForSecurityDeposit != null) {
        CustomerSecurityDepositDialog(
            customer = customerForSecurityDeposit!!,
            viewModel = viewModel,
            onDismiss = { customerForSecurityDeposit = null }
        )
    }
}

@Composable
fun CustomerCard(
    customer: Customer,
    effectiveDue: Double = customer.currentBalance,
    isNew: Boolean = false,
    onDismissNew: () -> Unit = {},
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onOpenBottleRates: () -> Unit,
    onOpenSecurityDeposit: () -> Unit,
    onDeliver: () -> Unit,
    onReceivePayment: () -> Unit,
    isViewOnly: Boolean = false
) {
    val context = LocalContext.current
    val missingDetails = remember(customer.name, customer.phone, customer.whatsapp, customer.area, customer.address) {
        customer.missingDetailLabels()
    }
    val hasMissingDetails = missingDetails.isNotEmpty()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .clickable { onClick() }
            .testTag("customer_card_${customer.id}")
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            // Row 1: Customer Number & Customer Name + Blinking Orange Missing-Detail Dot + NEW Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = WaterSkyLight
                ) {
                    Text(
                        text = customer.customerCode,
                        color = WaterNavyPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = customer.name,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WaterNavyDark,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (hasMissingDetails) {
                    Spacer(modifier = Modifier.width(4.dp))
                    BlinkingOrangeDot(
                        dotSize = 9.dp,
                        description = "Missing details: ${missingDetails.joinToString(", ")}",
                        modifier = Modifier.testTag("customer_missing_dot_${customer.id}")
                    )
                }

                if (isNew) {
                    Spacer(modifier = Modifier.width(5.dp))
                    NewItemBadge(
                        onClick = onDismissNew,
                        modifier = Modifier.testTag("new_customer_badge_${customer.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Mobile Number & Creator (or Missing Detail hint)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Mobile Number",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = customer.phone,
                        fontSize = 10.sp,
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (hasMissingDetails) {
                    Text(
                        text = "Missing: ${missingDetails.joinToString(", ")}",
                        fontSize = 8.5.sp,
                        color = WarningOrange,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (customer.createdBy.isNotBlank()) {
                    Text(
                        text = "By: ${customer.createdBy}",
                        fontSize = 9.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 7.dp), color = DividerColor)

            // Compact Single-Line Actions: WhatsApp, Call, Edit, Pay, Deliver
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = SuccessGreenLight,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable {
                                val targetNum = (if (customer.whatsapp.isNotBlank()) customer.whatsapp else customer.phone)
                                    .replace("-", "").replace(" ", "").replace("+", "")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$targetNum"))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            }
                            .testTag("customer_whatsapp_${customer.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = SuccessGreen, modifier = Modifier.size(15.dp))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = WaterSkyLight,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable {
                                val phoneNum = customer.phone.replace("-", "").replace(" ", "")
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNum"))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            }
                            .testTag("customer_call_${customer.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Call, contentDescription = "Call Customer", tint = WaterNavyPrimary, modifier = Modifier.size(15.dp))
                        }
                    }

                    if (!isViewOnly) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = WaterSkySurface,
                            modifier = Modifier
                                .size(30.dp)
                                .clickable(onClick = onEdit)
                                .testTag("edit_customer_${customer.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = WaterNavyPrimary, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }

                if (!isViewOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = onReceivePayment,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SuccessGreenLight,
                                contentColor = SuccessGreen
                            ),
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.height(30.dp).testTag("customer_pay_${customer.id}")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Pay", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        Button(
                            onClick = onDeliver,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = waterPrimaryButtonColors(),
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.height(30.dp).testTag("customer_deliver_${customer.id}")
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Deliver", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Customer Bottle & Rate Management Dialog
 * Handles Predefined 5 bottle sizes:
 * 19 Liter, 12.5 Liter, 6 Liter, 1.5 Liter, 500 ml.
 * Allows user to:
 * - Assign/deactivate bottle sizes
 * - Enter custom rate
 * - Specify "Applied From Date"
 * - View rate history
 * - Reactivate previously deactivated size
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerBottleRateDialog(
    customer: Customer,
    viewModel: WaterPlantViewModel,
    onDismiss: () -> Unit
) {
    val predefinedSizes by viewModel.bottleSizes.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val allCustomerRates by viewModel.customerRates.collectAsState()
    val canEditCustomerRates = viewModel.hasPermission(AppModule.CUSTOMER_RATES, PermissionAction.EDIT)
    val rateOptions = remember(predefinedSizes, inventory) {
        buildDeliverySelectableOptions(predefinedSizes, inventory)
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { dateFormat.format(Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = WaterNavyDark,
        textContentColor = TextPrimaryDark,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Column {
                Text(
                    text = "Bottle Sizes & Rates (${customer.name})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = WaterNavyDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "گاہک کے لیے مخصوص ریٹ اور بوتل سائز مقرر کریں",
                    fontSize = 10.sp,
                    color = TextSecondaryDark
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rateOptions, key = { it.rateKey }) { opt ->
                    val customRate = allCustomerRates.find {
                        it.customerId == customer.id && it.bottleSizeId == opt.rateKey
                    }
                    val isAssigned = customRate?.isAssigned == true

                    var inputPrice by remember(customRate) {
                        mutableStateOf(customRate?.customPrice?.toInt()?.toString() ?: "0")
                    }
                    var appliedDate by remember(customRate) {
                        mutableStateOf(customRate?.appliedDate?.takeIf { it.isNotEmpty() } ?: todayStr)
                    }
                    var showHistory by remember { mutableStateOf(false) }
                    var showCalendarPicker by remember { mutableStateOf(false) }

                    if (showCalendarPicker) {
                        val initialMillis = remember(appliedDate) {
                            try {
                                dateFormat.parse(appliedDate)?.time ?: System.currentTimeMillis()
                            } catch (_: Exception) {
                                System.currentTimeMillis()
                            }
                        }
                        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                        DatePickerDialog(
                            onDismissRequest = { showCalendarPicker = false },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        pickerState.selectedDateMillis?.let { millis ->
                                            appliedDate = dateFormat.format(Date(millis))
                                        }
                                        showCalendarPicker = false
                                    }
                                ) {
                                    Text("OK", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCalendarPicker = false }) {
                                    Text("Cancel")
                                }
                            }
                        ) {
                            DatePicker(state = pickerState)
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAssigned) WaterSkyLight else Color(0xFFF8FAFC)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAssigned) WaterAqua.copy(alpha = 0.5f) else BorderLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = opt.shortName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = WaterNavyDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Std Refill: Rs. ${opt.stdRefillPrice.toInt()}",
                                        fontSize = 9.5.sp,
                                        color = TextSecondaryDark,
                                        maxLines = 1
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isAssigned) SuccessGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (isAssigned) "Active" else "Not Set",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAssigned) SuccessGreen else WarningOrange,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Rate and Applied Date Inputs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompactFormTextField(
                                    value = inputPrice,
                                    onValueChange = { inputPrice = it },
                                    label = "Rate (PKR)*",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(0.95f)
                                )

                                CompactFormTextField(
                                    value = appliedDate,
                                    onValueChange = { appliedDate = it },
                                    label = "Effective Date*",
                                    placeholder = "YYYY-MM-DD",
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { showCalendarPicker = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CalendarToday,
                                                contentDescription = "Select Date",
                                                tint = WaterNavyPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1.35f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showHistory = !showHistory },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showHistory) Icons.Default.ExpandLess else Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (showHistory) "Hide" else "History (${customRate?.rateHistory?.size ?: 0})",
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (canEditCustomerRates) {
                                        if (isAssigned) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.removeCustomerBottleSize(customer.id, opt.rateKey)
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text(
                                                    "Deactivate",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                val p = inputPrice.toDoubleOrNull() ?: opt.stdRefillPrice
                                                viewModel.assignCustomerBottleRate(
                                                    customerId = customer.id,
                                                    bottleSizeId = opt.rateKey,
                                                    bottleSizeName = opt.shortName,
                                                    rate = p,
                                                    appliedDate = appliedDate
                                                )
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            colors = waterPrimaryButtonColors(),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(
                                                if (isAssigned) "Update Rate" else "Fix Rate",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            // Rate History Section
                            if (showHistory) {
                                val history = customRate?.rateHistory ?: emptyList()
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Rate History (سابقہ ریٹس ریکارڈ):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                                        if (history.isEmpty()) {
                                            Text("No past rate changes logged yet.", fontSize = 9.5.sp, color = TextMuted)
                                        } else {
                                            history.reversed().forEach { h ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Rs. ${h.rate.toInt()} (From: ${h.appliedFromDate})", fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
                                                    Text("by ${h.updatedBy}", fontSize = 9.5.sp, color = TextMuted)
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
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = waterPrimaryButtonColors()
            ) {
                Text("Done (مکمل)", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    )
}

/**
 * Customer Security Deposit Management Dialog
 * Tracks initial deposits, additional deposits, partial/full refunds, adjustments.
 */
@Composable
fun CustomerSecurityDepositDialog(
    customer: Customer,
    viewModel: WaterPlantViewModel,
    onDismiss: () -> Unit
) {
    val allDeposits by viewModel.securityDeposits.collectAsState()
    val thisCustDeposits = allDeposits.filter { it.customerId == customer.id }
    val currentBalance = viewModel.getCustomerSecurityDepositBalance(customer.id)

    var showAddTx by remember { mutableStateOf(false) }
    var txAmount by remember { mutableStateOf("") }
    var txType by remember { mutableStateOf(SecurityDepositTransactionType.ADDITIONAL_DEPOSIT) }
    var txMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var txRef by remember { mutableStateOf("") }
    var txNotes by remember { mutableStateOf("") }
    var showRefundConfirm by remember { mutableStateOf(false) }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = WaterNavyDark,
        textContentColor = TextPrimaryDark,
        title = {
            Column {
                Text(
                    text = "Security Deposit Ledger (${customer.name})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = WaterNavyDark
                )
                Text(
                    text = "سیکیورٹی ڈپازٹ اور واپسی کا کھاتہ",
                    fontSize = 10.sp,
                    color = TextSecondaryDark
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Balance Banner
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SuccessGreenLight),
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
                            Text("Current Deposit Balance:", fontSize = 10.sp, color = TextSecondaryDark)
                            Text("Rs. ${currentBalance.toInt()}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }

                        Button(
                            onClick = { showAddTx = !showAddTx },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(if (showAddTx) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showAddTx) "Close Form" else "+ Record Entry", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Add Transaction Form
                if (showAddTx) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Record Deposit / Refund Transaction:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)

                            // Type Selection Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    SecurityDepositTransactionType.ADDITIONAL_DEPOSIT,
                                    SecurityDepositTransactionType.PARTIAL_REFUND,
                                    SecurityDepositTransactionType.FULL_REFUND
                                ).forEach { type ->
                                    FilterChip(
                                        selected = txType == type,
                                        onClick = { txType = type },
                                        colors = waterFilterChipColors(),
                                        label = { Text(type.label.split(" ")[0], fontSize = 9.5.sp) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = txAmount,
                                    onValueChange = { txAmount = it },
                                    label = { Text("Amount (PKR)*", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(50.dp)
                                )

                                OutlinedTextField(
                                    value = txRef,
                                    onValueChange = { txRef = it },
                                    label = { Text("Ref / Receipt #", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(50.dp)
                                )
                            }

                            OutlinedTextField(
                                value = txNotes,
                                onValueChange = { txNotes = it },
                                label = { Text("Notes / Reason", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            )

                            Button(
                                onClick = {
                                    val amt = txAmount.toDoubleOrNull() ?: 0.0
                                    if (amt > 0) {
                                        if (txType == SecurityDepositTransactionType.PARTIAL_REFUND || txType == SecurityDepositTransactionType.FULL_REFUND) {
                                            showRefundConfirm = true
                                        } else {
                                            viewModel.recordSecurityDepositTransaction(
                                                SecurityDepositTransaction(
                                                    id = "sec_${System.currentTimeMillis()}",
                                                    customerId = customer.id,
                                                    customerName = customer.name,
                                                    amount = amt,
                                                    type = txType,
                                                    date = todayStr,
                                                    paymentMethod = txMethod,
                                                    reference = txRef,
                                                    notes = txNotes
                                                )
                                            )
                                            txAmount = ""
                                            txRef = ""
                                            txNotes = ""
                                            showAddTx = false
                                        }
                                    }
                                },
                                colors = waterPrimaryButtonColors(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("Submit Transaction", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Ledger Transaction List
                Text("Transaction Ledger (کھاتہ تفصیل):", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)

                if (thisCustDeposits.isEmpty()) {
                    Text("No deposit records logged yet.", fontSize = 10.sp, color = TextMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(thisCustDeposits.reversed()) { tx ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(tx.type.label, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = WaterNavyDark)
                                        Text("${tx.date} • ${tx.paymentMethod.label}" + if (tx.reference.isNotEmpty()) " • Ref: ${tx.reference}" else "", fontSize = 9.5.sp, color = TextSecondaryDark)
                                        if (tx.notes.isNotEmpty()) {
                                            Text(tx.notes, fontSize = 9.sp, color = TextMuted)
                                        }
                                    }

                                    val isDeposit = tx.type == SecurityDepositTransactionType.INITIAL_DEPOSIT || tx.type == SecurityDepositTransactionType.ADDITIONAL_DEPOSIT
                                    Text(
                                        text = (if (isDeposit) "+ Rs. " else "- Rs. ") + tx.amount.toInt(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp,
                                        color = if (isDeposit) SuccessGreen else ErrorRed
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showRefundConfirm) {
                AlertDialog(
                    onDismissRequest = { showRefundConfirm = false },
                    containerColor = Color.White,
                    titleContentColor = WaterNavyDark,
                    textContentColor = TextPrimaryDark,
                    title = { Text("Confirm Refund", fontWeight = FontWeight.Bold, color = WaterNavyDark) },
                    text = { Text("Are you sure you want to refund Rs. $txAmount to ${customer.name}?", color = TextPrimaryDark) },
                    confirmButton = {
                        Button(
                            onClick = {
                                val amt = txAmount.toDoubleOrNull() ?: 0.0
                                viewModel.recordSecurityDepositTransaction(
                                    SecurityDepositTransaction(
                                        id = "sec_${System.currentTimeMillis()}",
                                        customerId = customer.id,
                                        customerName = customer.name,
                                        amount = amt,
                                        type = txType,
                                        date = todayStr,
                                        paymentMethod = txMethod,
                                        reference = txRef,
                                        notes = txNotes
                                    )
                                )
                                txAmount = ""
                                txRef = ""
                                txNotes = ""
                                showAddTx = false
                                showRefundConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                        ) {
                            Text("Confirm Refund")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRefundConfirm = false }) { Text("Cancel", color = TextSecondaryDark) }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = waterPrimaryButtonColors()) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Compact No-Scroll Universal Dialog for Adding and Editing Customers
 */
@Composable
fun CustomerFormDialog(
    customer: Customer? = null,
    initialAgreedRate: Double = 0.0,
    onDismiss: () -> Unit,
    onConfirm: (Customer) -> Unit = {},
    onConfirmWithRate: ((Customer, Double) -> Unit)? = null
) {
    val isEditMode = customer != null
    val context = LocalContext.current

    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var whatsapp by remember { mutableStateOf(customer?.whatsapp ?: (customer?.phone ?: "")) }
    var address by remember { mutableStateOf(customer?.address ?: "") }
    var area by remember { mutableStateOf(customer?.area ?: "") }
    var selectedType by remember { mutableStateOf(customer?.type ?: CustomerType.HOME) }
    var selectedCycle by remember { mutableStateOf(customer?.paymentCycle ?: PaymentCycle.PAY_ON_DELIVERY) }
    var securityDeposit by remember {
        mutableStateOf(
            if (isEditMode && (customer?.securityDeposit ?: 0.0) > 0.0) customer!!.securityDeposit.toInt().toString()
            else if (isEditMode && (customer?.bottleDeposit ?: 0.0) > 0.0) customer!!.bottleDeposit.toInt().toString()
            else ""
        )
    }
    var openingBalance by remember {
        mutableStateOf(
            if (isEditMode && (customer?.currentBalance ?: 0.0) != 0.0) customer!!.currentBalance.toInt().toString()
            else ""
        )
    }
    var initialBottleBalance by remember {
        mutableStateOf(
            if (isEditMode && (customer?.emptyBottlesOwed ?: 0) > 0) customer!!.emptyBottlesOwed.toString()
            else ""
        )
    }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { contactUri ->
            try {
                val cursor = context.contentResolver.query(contactUri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                        val hasPhoneIndex = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (name.isBlank() && nameIndex >= 0) {
                            val pickedName = c.getString(nameIndex)
                            if (!pickedName.isNullOrBlank()) {
                                name = pickedName
                            }
                        }
                        if (idIndex >= 0 && hasPhoneIndex >= 0) {
                            val contactId = c.getString(idIndex)
                            val hasPhone = c.getString(hasPhoneIndex)
                            if (hasPhone == "1") {
                                val phonesCursor = context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    null,
                                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                    arrayOf(contactId),
                                    null
                                )
                                phonesCursor?.use { pc ->
                                    if (pc.moveToFirst()) {
                                        val numberIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        if (numberIndex >= 0) {
                                            val rawNum = pc.getString(numberIndex) ?: ""
                                            val cleanNum = rawNum.replace(" ", "").replace("-", "")
                                            phone = cleanNum
                                            if (whatsapp.isBlank() || whatsapp == phone) {
                                                whatsapp = cleanNum
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CustomerForm", "Failed to pick contact", e)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 390.dp)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Compact Header with (X) Close Button
                Surface(
                    color = WaterNavyPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEditMode) "Edit Customer Profile" else "Add New Customer",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("dialog_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // Compact Body — Fits ENTIRELY without scrolling on standard phones
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ErrorRedLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = ErrorRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 1. Customer Name
                    CompactFormTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        label = "Customer Name*",
                        placeholder = "e.g. Tariq Mehmood",
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(14.dp)) },
                        height = 38.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_form_name")
                    )

                    // 2. Mobile Number + Contact Picker
                    CompactFormTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            if (whatsapp.isEmpty() || whatsapp == phone) {
                                whatsapp = it
                            }
                            errorMessage = null
                        },
                        label = "Mobile Number*",
                        placeholder = "0300-1234567",
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(14.dp)) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                        contactPickerLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Contacts,
                                    contentDescription = "Pick Contact",
                                    tint = WaterNavyPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        height = 38.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_form_phone")
                    )

                    // 3. WhatsApp & Area in compact side-by-side row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactFormTextField(
                            value = whatsapp,
                            onValueChange = { whatsapp = it },
                            label = "WhatsApp",
                            placeholder = "0300-1234567",
                            leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            height = 38.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_form_whatsapp")
                        )

                        CompactFormTextField(
                            value = area,
                            onValueChange = { area = it },
                            label = "Area / Sector*",
                            placeholder = "e.g. G-10/2",
                            leadingIcon = { Icon(Icons.Default.Map, contentDescription = null, tint = WaterBlueDeep, modifier = Modifier.size(13.dp)) },
                            height = 38.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_form_area")
                        )
                    }

                    // 4. Complete Address
                    CompactFormTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "Complete Address*",
                        placeholder = "House / Shop #, Street #",
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = WaterBlueDeep, modifier = Modifier.size(14.dp)) },
                        height = 38.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_form_address")
                    )

                    // 5. Security Deposit & Opening Due
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactFormTextField(
                            value = securityDeposit,
                            onValueChange = { securityDeposit = it.filter { ch -> ch.isDigit() } },
                            label = "Security Deposit",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            height = 38.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_form_security_deposit")
                        )

                        CompactFormTextField(
                            value = openingBalance,
                            onValueChange = { openingBalance = it.filter { ch -> ch.isDigit() || ch == '-' } },
                            label = "Opening Due",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            height = 38.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_form_balance")
                        )
                    }

                    // 6. Empty Held Bottles (Watermark inside field) & Notes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactFormTextField(
                            value = initialBottleBalance,
                            onValueChange = { initialBottleBalance = it.filter { ch -> ch.isDigit() } },
                            label = null,
                            placeholder = "Empty Held Bottles",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            height = 38.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_form_bottles")
                        )

                        CompactFormTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = "Notes",
                            placeholder = "Instructions...",
                            height = 38.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 7. Customer Type (Single-line non-wrapping pills)
                    Text(
                        text = "Customer Type:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyDark,
                        maxLines = 1,
                        softWrap = false
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            CustomerType.HOME to "Home",
                            CustomerType.SHOP to "Shop",
                            CustomerType.OFFICE to "Office",
                            CustomerType.RESTAURANT to "Hotel"
                        ).forEach { (type, chipLabel) ->
                            val isSel = selectedType == type
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(27.dp)
                                    .clickable { selectedType = type }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = chipLabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else WaterNavyDark,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // 8. Payment Cycle (Single-line non-wrapping pills: COD, Daily, Weekly, Monthly)
                    Text(
                        text = "Payment Cycle:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyDark,
                        maxLines = 1,
                        softWrap = false
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            PaymentCycle.PAY_ON_DELIVERY to "COD",
                            PaymentCycle.DAILY to "Daily",
                            PaymentCycle.WEEKLY to "Weekly",
                            PaymentCycle.MONTHLY to "Monthly"
                        ).forEach { (cycle, cycleLabel) ->
                            val isSel = selectedCycle == cycle
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) WaterNavyPrimary else WaterSkyLight.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(27.dp)
                                    .clickable { selectedCycle = cycle }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = cycleLabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else WaterNavyDark,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                // Compact Footer
                HorizontalDivider(color = BorderLight)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = TextSecondaryDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = {
                            val cleanName = name.trim()
                            val cleanPhone = phone.trim()
                            if (cleanName.isEmpty()) {
                                errorMessage = "Please enter customer name"
                                return@Button
                            }
                            if (cleanPhone.isEmpty()) {
                                errorMessage = "Please enter mobile number"
                                return@Button
                            }

                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            val depositAmount = securityDeposit.toDoubleOrNull() ?: 0.0
                            val rate = initialAgreedRate
                            val updatedOrNew = Customer(
                                id = customer?.id ?: "",
                                customerCode = customer?.customerCode ?: "",
                                name = cleanName,
                                phone = cleanPhone,
                                whatsapp = if (whatsapp.isNotEmpty()) whatsapp.trim() else cleanPhone,
                                address = address.trim(),
                                area = area.trim(),
                                type = selectedType,
                                paymentCycle = selectedCycle,
                                registrationDate = customer?.registrationDate?.takeIf { it.isNotEmpty() } ?: todayStr,
                                openingBalance = openingBalance.toDoubleOrNull() ?: 0.0,
                                currentBalance = openingBalance.toDoubleOrNull() ?: (customer?.currentBalance ?: 0.0),
                                emptyBottlesOwed = initialBottleBalance.toIntOrNull() ?: (customer?.emptyBottlesOwed ?: 0),
                                creditLimit = customer?.creditLimit ?: 5000.0,
                                securityDeposit = depositAmount,
                                bottleDeposit = depositAmount,
                                notes = notes.trim(),
                                active = customer?.active ?: true,
                                createdBy = customer?.createdBy ?: "",
                                createdByUid = customer?.createdByUid ?: "",
                                createdAt = customer?.createdAt ?: 0L
                            )
                            onConfirm(updatedOrNew)
                            onConfirmWithRate?.invoke(updatedOrNew, rate)
                        },
                        colors = waterPrimaryButtonColors(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("save_customer_submit_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEditMode) "Save Changes" else "Add Customer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
