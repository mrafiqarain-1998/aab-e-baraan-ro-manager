package com.example.ui.screens.stockcost

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import com.example.ui.components.CompactDropdownField
import com.example.ui.components.MobileFormDialog
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottleStockSubTab(
    viewModel: WaterPlantViewModel,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val isViewOnly = !viewModel.hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.ADD) &&
        !viewModel.hasPermission(AppModule.BOTTLE_ISSUE, PermissionAction.ADD) &&
        !viewModel.hasPermission(AppModule.REFILL_BATCHES, PermissionAction.ADD)
    val bottleSizes by viewModel.bottleSizes.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val customerAllocations by viewModel.customerAllocations.collectAsState()
    val movements by viewModel.bottleMovements.collectAsState()

    var showAddStockDialog by remember { mutableStateOf(false) }
    var showIssueBottleDialog by remember { mutableStateOf(false) }
    var showRefillBatchDialog by remember { mutableStateOf(false) }
    var showLossDamageDialog by remember { mutableStateOf(false) }

    var showAddSizeDialog by remember { mutableStateOf(false) }
    var sizeToEdit by remember { mutableStateOf<BottleSize?>(null) }
    var sizeToDelete by remember { mutableStateOf<BottleSize?>(null) }

    val activeSizes = remember(bottleSizes) { bottleSizes.filter { it.active } }
    val singleUseSizes = remember(activeSizes) {
        activeSizes.filter {
            it.usageType == BottleUsageType.ONE_TIME ||
                (it.usageType == BottleUsageType.CUSTOM && !it.isReturnableBottle)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("bottle_stock_sub_tab"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Bar: 4 Properly Aligned Iconic Buttons in One Row
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val canAddBottleStock = viewModel.hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.ADD)
                        val canIssueBottle = viewModel.hasPermission(AppModule.BOTTLE_ISSUE, PermissionAction.ADD)
                        val canRefillBatch = viewModel.hasPermission(AppModule.REFILL_BATCHES, PermissionAction.ADD)
                        val canEditBottleStock = viewModel.hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.EDIT) || canAddBottleStock
                        BottleActionTile(
                            label = "Add Stock",
                            icon = Icons.Default.AddShoppingCart,
                            containerColor = WaterSkyLight,
                            contentColor = WaterNavyPrimary,
                            enabled = canAddBottleStock && activeSizes.isNotEmpty(),
                            onClick = { showAddStockDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_top_add_stock")
                        )
                        BottleActionTile(
                            label = "Issue Bottle",
                            icon = Icons.Default.Upload,
                            containerColor = WaterNavyPrimary,
                            contentColor = Color.White,
                            enabled = canIssueBottle && activeSizes.isNotEmpty(),
                            onClick = { showIssueBottleDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_top_issue_bottle")
                        )
                        BottleActionTile(
                            label = "Refill Batch",
                            icon = Icons.Default.Autorenew,
                            containerColor = SuccessGreenLight,
                            contentColor = SuccessGreen,
                            enabled = canRefillBatch && singleUseSizes.isNotEmpty(),
                            onClick = { showRefillBatchDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_top_refill_batch")
                        )
                        BottleActionTile(
                            label = "Loss & Damage",
                            icon = Icons.Default.BrokenImage,
                            containerColor = ErrorRedLight,
                            contentColor = ErrorRed,
                            enabled = canEditBottleStock && activeSizes.isNotEmpty(),
                            onClick = { showLossDamageDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_top_loss_damage")
                        )
                    }
                }
            }
        }

        // 2. Unified Bottle Sizes Section Header + Add New Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bottle Stock & Sizes (${activeSizes.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = WaterNavyPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "بوتل سائز، پلانٹ اسٹاک، گاہک استعمال اور نقصان",
                        fontSize = 9.5.sp,
                        color = TextSecondaryDark,
                        maxLines = 1
                    )
                }

                if (!isViewOnly) {
                    Button(
                        onClick = { showAddSizeDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WaterNavyPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 38.dp)
                            .testTag("btn_add_bottle_size")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add New",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 3. Unified Bottle Sizes Display (Single Unified List)
        if (activeSizes.isEmpty()) {
            item {
                EmptyStateCard(
                    message = "No bottle sizes registered yet.",
                    urduMessage = "نیا بوتل سائز شامل کرنے کے لیے Add New دبائیں"
                )
            }
        } else {
            items(activeSizes, key = { it.id }) { size ->
                val inv = inventory.find { it.bottleSizeId == size.id } ?: BottleInventory(
                    bottleSizeId = size.id,
                    sizeName = size.name
                )
                val heldCustomers = customerAllocations
                    .filter { it.bottleSizeId == size.id }
                    .sumOf { it.emptyHeld }

                UnifiedBottleSizeCard(
                    size = size,
                    inventory = inv,
                    inUseCount = inv.inUse,
                    canEditOrDelete = isAdmin,
                    onEditClick = { sizeToEdit = size },
                    onDeleteClick = { sizeToDelete = size }
                )
            }
        }

        // 4. Recent Stock Movements Audit Log
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = TextPrimaryDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            "Recent Stock Movements (${movements.size})",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = WaterNavyPrimary
                        )
                        Text("بوتل کی نقل و حرکت کا ریکارڈ", fontSize = 9.5.sp, color = TextSecondaryDark)
                    }
                }

                if (isAdmin && movements.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            viewModel.clearAllBottleMovements()
                            Toast.makeText(context, "All movements cleared", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = ErrorRed, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Clear", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val recentMovements = movements.take(15)
        if (recentMovements.isEmpty()) {
            item {
                EmptyStateCard(
                    message = "No stock movements recorded yet.",
                    urduMessage = "ابھی تک کوئی لاگ ریکارڈ نہیں ہوا"
                )
            }
        } else {
            items(recentMovements, key = { it.id }) { mov ->
                MovementAuditRow(
                    movement = mov,
                    isAdmin = isAdmin,
                    onDeleteClick = {
                        viewModel.deleteBottleMovement(mov.id)
                        Toast.makeText(context, "Movement record deleted", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // --- DIALOGS ---

    // A. Add Stock Form
    if (showAddStockDialog) {
        AddBottleStockFormDialog(
            bottleSizes = activeSizes,
            onDismiss = { showAddStockDialog = false },
            onConfirmPurchase = { sizeId, qty, rate, supplier, paymentSource, paymentMode, purchaseDate ->
                viewModel.refillBottleStock(
                    bottleSizeId = sizeId,
                    quantity = qty,
                    purchaseDate = purchaseDate,
                    supplierName = supplier,
                    unitPurchaseRate = rate,
                    notes = "Stock added via ${paymentSource.label} (${paymentMode.label})",
                    paymentSource = paymentSource,
                    paymentMode = paymentMode
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showAddStockDialog = false
                }
            }
        )
    }

    // B. Issue Bottle Form
    if (showIssueBottleDialog) {
        IssueBottleFormDialog(
            bottleSizes = activeSizes,
            customers = customers.filter { it.active },
            inventory = inventory,
            onDismiss = { showIssueBottleDialog = false },
            onConfirmIssue = { target, customerId, sizeId, qty, note ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                viewModel.issueBottleStock(
                    bottleSizeId = sizeId,
                    target = target,
                    customerId = customerId,
                    quantity = qty,
                    issueDate = today,
                    deposit = 0.0,
                    notes = note
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showIssueBottleDialog = false
                }
            }
        )
    }

    // C. Refill Batch Form (Single Time Use Bottles Strictly)
    if (showRefillBatchDialog) {
        RefillBatchSingleUseDialog(
            singleUseSizes = singleUseSizes,
            inventory = inventory,
            onDismiss = { showRefillBatchDialog = false },
            onConfirmRefill = { sizeId, qty, packetSize, damagedQty, date, notes, containerType, returnLooseToInUse ->
                viewModel.refillSingleUseBatchWithConversion(
                    bottleSizeId = sizeId,
                    refillQuantity = qty,
                    packetSize = packetSize,
                    damagedQuantity = damagedQty,
                    date = date,
                    notes = notes,
                    containerType = containerType,
                    returnLooseToInUse = returnLooseToInUse
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showRefillBatchDialog = false
                }
            }
        )
    }

    // D. Loss & Damage Form
    if (showLossDamageDialog) {
        BottleLossDamageFormDialog(
            bottleSizes = activeSizes,
            customers = customers.filter { it.active },
            inventory = inventory,
            customerAllocations = customerAllocations,
            onDismiss = { showLossDamageDialog = false },
            onConfirmLoss = { source, customerId, sizeId, qty, remarks ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                viewModel.recordBottleLossOrDamage(
                    source = source,
                    bottleSizeId = sizeId,
                    quantity = qty,
                    date = today,
                    remarks = remarks,
                    customerId = customerId
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showLossDamageDialog = false
                }
            }
        )
    }

    // E. Add / Edit Bottle Size Form
    if (showAddSizeDialog || sizeToEdit != null) {
        val editing = sizeToEdit
        BottleSizeFormDialog(
            existingSize = editing,
            onDismiss = {
                showAddSizeDialog = false
                sizeToEdit = null
            },
            onSave = { name, capacity, usageType, refillPrice, purchaseCost, deposit, uom ->
                if (editing == null) {
                    viewModel.addNewBottleSize(
                        name = name,
                        purchaseRate = purchaseCost,
                        packQty = 1,
                        uom = uom,
                        openingStock = 0,
                        usageType = usageType,
                        defaultRefillPrice = refillPrice,
                        salePrice = refillPrice,
                        capacityLitres = capacity,
                        depositAmount = deposit
                    ) {
                        showAddSizeDialog = false
                        Toast.makeText(context, "Bottle size added", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.updateBottleSizeDetails(
                        bottleSizeId = editing.id,
                        name = name,
                        capacityLitres = capacity,
                        usageType = usageType,
                        defaultRefillPrice = refillPrice,
                        purchaseCost = purchaseCost,
                        depositAmount = deposit,
                        uom = uom
                    ) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) sizeToEdit = null
                    }
                }
            }
        )
    }

    // F. Delete Bottle Size Confirmation Dialog
    sizeToDelete?.let { size ->
        val inv = inventory.find { it.bottleSizeId == size.id }
        val plantStock = (inv?.atPlantFull ?: 0) + (inv?.atPlantEmpty ?: 0) + (inv?.inUse ?: 0)
        val heldCount = customerAllocations.filter { it.bottleSizeId == size.id }.sumOf { it.emptyHeld }

        AlertDialog(
            onDismissRequest = { sizeToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            title = {
                Text("Delete ${size.name}?", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = ErrorRed)
            },
            text = {
                if (heldCount > 0 || plantStock > 0) {
                    Text(
                        "Active stock: $plantStock at plant, $heldCount with customers. Clear stock first or use Admin Force Delete.",
                        color = ErrorRed,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        "Delete or archive '${size.name}'? Historical orders remain safe.",
                        fontSize = 11.sp,
                        color = TextPrimaryDark
                    )
                }
            },
            confirmButton = {
                if (isAdmin && (plantStock > 0 || heldCount > 0)) {
                    Button(
                        onClick = {
                            viewModel.forceDeleteBottleSize(size.id) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) sizeToDelete = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                    ) {
                        Text("Force Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        enabled = plantStock == 0 && heldCount == 0,
                        onClick = {
                            viewModel.deleteOrArchiveBottleSize(size.id) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) sizeToDelete = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                    ) {
                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { sizeToDelete = null }) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
private fun BottleActionTile(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) containerColor else Color(0xFFF1F5F9),
        modifier = modifier.height(60.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) contentColor else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (enabled) contentColor else TextMuted,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun UnifiedBottleSizeCard(
    size: BottleSize,
    inventory: BottleInventory,
    inUseCount: Int,
    canEditOrDelete: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isSingleUse = size.usageType == BottleUsageType.ONE_TIME ||
        (size.usageType == BottleUsageType.CUSTOM && !size.isReturnableBottle)
    val badgeBg = if (isSingleUse) SuccessGreenLight else WaterSkyLight
    val badgeFg = if (isSingleUse) SuccessGreen else WaterBlueDeep

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Card Header: Bottle Name + Usage Type Badge + Edit & Delete Icon Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSingleUse) Icons.Default.LocalDrink else Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = size.name,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeBg
                            ) {
                                Text(
                                    text = if (isSingleUse) "Single Time" else "Multiple Time",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = badgeFg,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${size.capacityLitres}L • ${size.uom} • Refill Rs.${size.defaultRefillPrice.toInt()} • Cost Rs.${size.latestPurchaseRate.toInt()}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (canEditOrDelete) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("btn_edit_size_${size.id}")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Size",
                                tint = WaterBlueDeep,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("btn_delete_size_${size.id}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Size",
                                tint = ErrorRed,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Small Square Compact Number Boards: Plant Full, Plant Empty, In Use, Damage
            val totalPacketsFull = if (inventory.packVariants.isNotEmpty()) {
                inventory.packVariants.sumOf { it.packetCount }
            } else {
                inventory.fullPackets
            }
            val plantFullCount = if (isSingleUse) totalPacketsFull else inventory.atPlantFull
            val plantFullSubtitle = if (isSingleUse) {
                if (inventory.fullLooseBottles > 0) "${inventory.atPlantFull} Btl (+${inventory.fullLooseBottles} loose)"
                else "${inventory.atPlantFull} Bottles"
            } else null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SquareNumberBoard(
                    label = if (isSingleUse) "Plant Full (Pkt)" else "Plant Full",
                    count = plantFullCount,
                    color = SuccessGreen,
                    bg = SuccessGreenLight,
                    subtitle = plantFullSubtitle,
                    modifier = Modifier.weight(1f)
                )
                SquareNumberBoard(
                    label = "Plant Empty",
                    count = inventory.atPlantEmpty,
                    color = WaterBlueDeep,
                    bg = WaterSkyLight,
                    modifier = Modifier.weight(1f)
                )
                SquareNumberBoard(
                    label = "In Use",
                    count = inUseCount,
                    color = WarningOrange,
                    bg = WarningOrangeLight,
                    modifier = Modifier.weight(1f)
                )
                SquareNumberBoard(
                    label = "Damage",
                    count = inventory.damaged,
                    color = ErrorRed,
                    bg = ErrorRedLight,
                    modifier = Modifier.weight(1f)
                )
            }

            if (isSingleUse && inventory.packVariants.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(0.5.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Packet / Carton Breakdown (Plant Full)",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondaryDark
                        )
                        inventory.packVariants.forEach { variant ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = variant.displayName,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WaterNavyPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = if (variant.packetCount > 0) SuccessGreenLight else Color(0xFFE2E8F0)
                                ) {
                                    Text(
                                        text = if (variant.packSize <= 1) "${variant.packetCount} Btl" else "${variant.packetCount} ${variant.containerType.shortLabel}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (variant.packetCount > 0) SuccessGreen else TextSecondaryDark,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

@Composable
private fun SquareNumberBoard(
    label: String,
    count: Int,
    color: Color,
    bg: Color,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.2f)),
        modifier = modifier.height(66.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$count",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = color,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.9f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color.copy(alpha = 0.8f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// -------------------------------------------------------------
// A. ADD STOCK FORM DIALOG
// 1. Bottle Size -> 2. Quantity -> 3. Rate -> 4. Supplier Name
// -> 5. Payment Source -> 6. Payment Mode -> 7. Confirm Purchase
// -------------------------------------------------------------
@Composable
fun AddBottleStockFormDialog(
    bottleSizes: List<BottleSize>,
    onDismiss: () -> Unit,
    onConfirmPurchase: (
        bottleSizeId: String,
        quantity: Int,
        rate: Double,
        supplierName: String,
        paymentSource: PaymentSource,
        paymentMode: StockPaymentMode,
        purchaseDate: String
    ) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedSize by remember { mutableStateOf(bottleSizes.firstOrNull()) }
    var quantity by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(selectedSize?.latestPurchaseRate?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var supplierName by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf(dateFormat.format(Date())) }
    var paymentSource by remember { mutableStateOf(PaymentSource.PAY_FROM_SALE) }
    var paymentMode by remember { mutableStateOf(StockPaymentMode.CASH) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val qtyInt = quantity.toIntOrNull() ?: 0
    val rateVal = rate.toDoubleOrNull() ?: 0.0
    val totalAmount = qtyInt * rateVal
    val hasUnsaved = quantity.isNotBlank() || supplierName.isNotBlank()

    MobileFormDialog(
        title = "Add Stock",
        subtitle = "اسٹاک کی خریداری درج کریں",
        hasUnsavedChanges = hasUnsaved,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val size = selectedSize
                    when {
                        size == null -> errorMsg = "Please select a bottle size."
                        qtyInt <= 0 -> errorMsg = "Enter a valid quantity (> 0)."
                        rateVal < 0 -> errorMsg = "Enter a valid rate."
                        else -> onConfirmPurchase(
                            size.id,
                            qtyInt,
                            rateVal,
                            supplierName.trim(),
                            paymentSource,
                            paymentMode,
                            purchaseDate.ifBlank { dateFormat.format(Date()) }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirm_add_stock")
            ) {
                Text("Confirm Purchase", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        // 1. Bottle Size Dropdown
        CompactDropdownField(
            label = "1. Bottle Size*",
            selectedText = selectedSize?.name ?: "",
            options = bottleSizes,
            optionLabel = { "${it.name} (${it.usageType.shortLabel})" },
            onOptionSelected = { size ->
                selectedSize = size
                if (size.latestPurchaseRate > 0) {
                    rate = size.latestPurchaseRate.toInt().toString()
                }
                errorMsg = null
            }
        )

        // Purchase Date with Calendar Icon (defaults to today)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = purchaseDate,
                onValueChange = { purchaseDate = it },
                label = { Text("Purchase Date", fontSize = 10.5.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WaterSkyLight,
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        val cal = Calendar.getInstance()
                        try {
                            dateFormat.parse(purchaseDate)?.let { cal.time = it }
                        } catch (_: Exception) {}
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val picked = Calendar.getInstance().apply { set(y, m, d) }
                                purchaseDate = dateFormat.format(picked.time)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .testTag("btn_pick_bottle_stock_date")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = "Select Date",
                        tint = WaterNavyPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 2. Quantity
        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it.filter { ch -> ch.isDigit() }; errorMsg = null },
            label = { Text("2. Quantity*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 3. Rate
        OutlinedTextField(
            value = rate,
            onValueChange = { rate = it; errorMsg = null },
            label = { Text("3. Rate (Rs. per unit)*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 4. Supplier Name
        OutlinedTextField(
            value = supplierName,
            onValueChange = { supplierName = it },
            label = { Text("4. Supplier Name", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 5. Payment Source
        Text("5. Payment Source*", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentSource.values().forEach { src ->
                val selected = paymentSource == src
                Surface(
                    onClick = { paymentSource = src },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) WaterSkyLight else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (selected) WaterNavyPrimary else BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { paymentSource = src }, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(src.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                    }
                }
            }
        }

        // 6. Payment Mode
        Text("6. Payment Mode*", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StockPaymentMode.values().forEach { mode ->
                val selected = paymentMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { paymentMode = mode },
                    colors = waterFilterChipColors(),
                    label = { Text(mode.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (totalAmount > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SuccessGreenLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Purchase:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    Text("Rs. ${totalAmount.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                }
            }
        }

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// B. ISSUE BOTTLE FORM DIALOG
// Tabs: Customer (multiple-use only) | Issue to RO Plant (all sizes)
// Always deducts from Plant Empty
// -------------------------------------------------------------
@Composable
fun IssueBottleFormDialog(
    bottleSizes: List<BottleSize>,
    customers: List<Customer>,
    inventory: List<BottleInventory>,
    onDismiss: () -> Unit,
    onConfirmIssue: (
        target: BottleIssueTarget,
        customerId: String?,
        bottleSizeId: String,
        quantity: Int,
        note: String
    ) -> Unit
) {
    var target by remember { mutableStateOf(BottleIssueTarget.CUSTOMER) }
    val availableSizes = remember(target, bottleSizes) {
        if (target == BottleIssueTarget.CUSTOMER) {
            bottleSizes.filter {
                it.usageType == BottleUsageType.MULTIPLE_TIME ||
                    (it.usageType == BottleUsageType.CUSTOM && it.isReturnableBottle)
            }
        } else {
            bottleSizes
        }
    }
    var selectedCustomer by remember { mutableStateOf(customers.firstOrNull()) }
    var selectedSize by remember { mutableStateOf(availableSizes.firstOrNull()) }
    var quantity by remember { mutableStateOf("") }
    var issueNote by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(target, availableSizes) {
        if (selectedSize == null || availableSizes.none { it.id == selectedSize?.id }) {
            selectedSize = availableSizes.firstOrNull()
        }
    }

    val availablePlantEmpty = inventory.find { it.bottleSizeId == selectedSize?.id }?.atPlantEmpty ?: 0
    val qtyInt = quantity.toIntOrNull() ?: 0
    val hasUnsaved = quantity.isNotBlank() || issueNote.isNotBlank()

    MobileFormDialog(
        title = "Issue Bottle",
        subtitle = "گاہک (Multiple-Use) یا آر او پلانٹ کو خالی بوتلیں جاری کریں",
        hasUnsavedChanges = hasUnsaved,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val size = selectedSize
                    when {
                        size == null -> errorMsg = "Select a bottle size."
                        target == BottleIssueTarget.CUSTOMER && selectedCustomer == null -> errorMsg = "Choose a customer."
                        qtyInt <= 0 -> errorMsg = "Enter quantity > 0."
                        qtyInt > availablePlantEmpty -> errorMsg = "Insufficient Plant Empty stock ($availablePlantEmpty available)."
                        else -> onConfirmIssue(
                            target,
                            if (target == BottleIssueTarget.CUSTOMER) selectedCustomer?.id else null,
                            size.id,
                            qtyInt,
                            issueNote.trim()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirm_issue_bottle")
            ) {
                Text("Confirm Issue", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Text("Issue To*", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = target == BottleIssueTarget.CUSTOMER,
                onClick = { target = BottleIssueTarget.CUSTOMER; errorMsg = null },
                colors = waterFilterChipColors(),
                label = { Text("Customer", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                modifier = Modifier.weight(1f).testTag("tab_issue_customer")
            )
            FilterChip(
                selected = target == BottleIssueTarget.INTERNAL_USE,
                onClick = { target = BottleIssueTarget.INTERNAL_USE; errorMsg = null },
                colors = waterFilterChipColors(),
                label = { Text("Issue to RO Plant", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                modifier = Modifier.weight(1f).testTag("tab_issue_ro_plant")
            )
        }

        if (target == BottleIssueTarget.CUSTOMER) {
            CompactDropdownField(
                label = "Choose Customer*",
                selectedText = selectedCustomer?.let { "${it.name} (${it.area})" } ?: "",
                options = customers,
                optionLabel = { "${it.name} • ${it.area}" },
                onOptionSelected = { selectedCustomer = it; errorMsg = null },
                placeholder = "Select Customer"
            )
        }

        CompactDropdownField(
            label = if (target == BottleIssueTarget.CUSTOMER) "Bottle Size (Multiple-Use Only)*" else "Bottle Size (All Sizes)*",
            selectedText = selectedSize?.name ?: "",
            options = availableSizes,
            optionLabel = { size ->
                val avail = inventory.find { it.bottleSizeId == size.id }?.atPlantEmpty ?: 0
                "${size.name} (Empty: $avail)"
            },
            onOptionSelected = { selectedSize = it; errorMsg = null },
            placeholder = if (target == BottleIssueTarget.CUSTOMER) "No multiple-use bottle sizes found" else "No bottle sizes found"
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = WaterSkyLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Plant Empty Available: $availablePlantEmpty bottles",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }

        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it.filter { ch -> ch.isDigit() }; errorMsg = null },
            label = { Text("Quantity*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = qtyInt > availablePlantEmpty,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_issue_quantity")
        )

        OutlinedTextField(
            value = issueNote,
            onValueChange = { issueNote = it },
            label = { Text("Issue Note", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// C. REFILL BATCH & PACKET/CARTON CONVERSION DIALOG (Single Time Use Strictly)
// Shows only single-use sizes with In Use > 0
// Step 1: Size (In Use > 0), Qty, Date, Note -> "Save and Convert Into Packet/Carton"
// Step 2: Packet Size (1, 6, 12, 24) + Damaged/Lost Qty + Live Preview -> Confirm
// -------------------------------------------------------------
@Composable
fun RefillBatchSingleUseDialog(
    singleUseSizes: List<BottleSize>,
    inventory: List<BottleInventory>,
    onDismiss: () -> Unit,
    onConfirmRefill: (
        bottleSizeId: String,
        refillQuantity: Int,
        packetSize: Int,
        damagedQuantity: Int,
        date: String,
        notes: String,
        containerType: PackContainerType,
        returnLooseToInUse: Boolean
    ) -> Unit
) {
    val eligibleSizes = remember(singleUseSizes, inventory) {
        singleUseSizes.filter { size ->
            (inventory.find { it.bottleSizeId == size.id }?.inUse ?: 0) > 0
        }
    }

    var selectedSize by remember { mutableStateOf(eligibleSizes.firstOrNull()) }
    LaunchedEffect(eligibleSizes) {
        if (selectedSize == null || eligibleSizes.none { it.id == selectedSize?.id }) {
            selectedSize = eligibleSizes.firstOrNull()
        }
    }

    var quantity by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var notes by remember { mutableStateOf("") }

    // Step 2: Packet/Carton conversion state
    var showConversionStep by remember { mutableStateOf(false) }
    var selectedPacketSize by remember { mutableStateOf(12) }
    var selectedContainerType by remember { mutableStateOf(PackContainerType.PACKET) }
    var returnLooseToInUse by remember { mutableStateOf(true) }
    var damagedQtyText by remember { mutableStateOf("0") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val availableInUse = inventory.find { it.bottleSizeId == selectedSize?.id }?.inUse ?: 0
    val qtyInt = quantity.toIntOrNull() ?: 0
    val damagedInt = damagedQtyText.toIntOrNull() ?: 0
    val isDamagedValid = damagedQtyText.isEmpty() || (damagedQtyText.all { it.isDigit() } && damagedInt in 0..qtyInt)

    val usableBottles = (qtyInt - damagedInt).coerceAtLeast(0)
    val completePackets = if (selectedPacketSize > 0) usableBottles / selectedPacketSize else 0
    val packedBottles = completePackets * selectedPacketSize
    val remainingLooseBottles = if (selectedPacketSize > 0) usableBottles % selectedPacketSize else 0
    val variantPreviewName = selectedSize?.let {
        formatPackVariantName(it.name, selectedPacketSize, selectedContainerType)
    } ?: ""

    MobileFormDialog(
        title = if (!showConversionStep) "Refill Batch (Single Time Use)" else "Packet / Carton Conversion",
        subtitle = if (!showConversionStep) "سنگل ٹائم یوز بوتل فلنگ بیچ (In Use سے پلانٹ فل)" else "پیکٹ/کارٹن تبدیلی اور خراب بوتلوں کا حساب",
        accentColor = SuccessGreen,
        hasUnsavedChanges = quantity.isNotBlank() || notes.isNotBlank(),
        onDismiss = onDismiss,
        footerContent = {
            if (!showConversionStep) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
                Button(
                    onClick = {
                        val size = selectedSize
                        when {
                            size == null -> errorMsg = "No single-use size available in In Use stock."
                            qtyInt <= 0 -> errorMsg = "Enter a valid refill quantity (> 0)."
                            qtyInt > availableInUse -> errorMsg = "Refill quantity ($qtyInt) exceeds In Use stock ($availableInUse)."
                            else -> {
                                errorMsg = null
                                selectedPacketSize = size.packQuantity.takeIf { it in listOf(1, 6, 12, 24) } ?: 12
                                showConversionStep = true
                            }
                        }
                    },
                    enabled = eligibleSizes.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_save_convert_packet")
                ) {
                    Text("Save and Convert Into Packet/Carton", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = { showConversionStep = false; errorMsg = null }) {
                    Text("Back", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
                Button(
                    onClick = {
                        val size = selectedSize ?: return@Button
                        when {
                            !isDamagedValid || damagedInt < 0 -> errorMsg = "Invalid damaged bottle quantity."
                            damagedInt > qtyInt -> errorMsg = "Damaged quantity ($damagedInt) cannot exceed refill quantity ($qtyInt)."
                            else -> onConfirmRefill(
                                size.id,
                                qtyInt,
                                selectedPacketSize,
                                damagedInt,
                                date.trim(),
                                notes.trim(),
                                selectedContainerType,
                                returnLooseToInUse
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_confirm_refill_batch")
                ) {
                    Text("Confirm Conversion", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        if (eligibleSizes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WarningOrangeLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No single-use bottles currently have stock in In Use. Use 'Issue Bottle -> Issue to RO Plant' first.",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WaterNavyPrimary
                    )
                }
            }
        } else if (!showConversionStep) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SuccessGreenLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Showing single-use sizes with In Use stock only (In Use -> Plant Full).",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WaterNavyPrimary
                    )
                }
            }

            CompactDropdownField(
                label = "Single Time Bottle Size*",
                selectedText = selectedSize?.name ?: "",
                options = eligibleSizes,
                optionLabel = { size ->
                    val inUseAvail = inventory.find { it.bottleSizeId == size.id }?.inUse ?: 0
                    "${size.name} (In Use: $inUseAvail)"
                },
                onOptionSelected = { selectedSize = it; errorMsg = null },
                placeholder = "Select bottle size"
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WarningOrangeLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Available In Use Quantity: $availableInUse bottles",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningOrange,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter { ch -> ch.isDigit() }; errorMsg = null },
                label = { Text("Refill Quantity*", fontSize = 10.5.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = qtyInt > availableInUse,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_refill_batch_qty")
            )

            val dialogContext = LocalContext.current
            val batchDateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Batch Date", fontSize = 10.5.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WaterSkyLight,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            val cal = Calendar.getInstance()
                            try {
                                batchDateFmt.parse(date)?.let { cal.time = it }
                            } catch (_: Exception) {}
                            DatePickerDialog(
                                dialogContext,
                                { _, y, m, d ->
                                    val picked = Calendar.getInstance().apply { set(y, m, d) }
                                    date = batchDateFmt.format(picked.time)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Select Batch Date",
                            tint = WaterNavyPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Batch Note / Operator", fontSize = 10.5.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // STEP 2: PACKET / CARTON CONVERSION
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WaterSkyLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        text = "${selectedSize?.name}: Converting $qtyInt bottles from In Use",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WaterNavyPrimary
                    )
                    if (variantPreviewName.isNotBlank()) {
                        Text(
                            text = "Pack Name: $variantPreviewName",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterBlueDeep
                        )
                    }
                }
            }

            // Packet vs Carton Checkboxes
            Text("Convert Into*", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PackContainerType.values().forEach { type ->
                    val checked = selectedContainerType == type
                    Surface(
                        onClick = { selectedContainerType = type },
                        shape = RoundedCornerShape(8.dp),
                        color = if (checked) WaterSkyLight else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (checked) WaterNavyPrimary else BorderLight),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("checkbox_container_${type.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selectedContainerType = type },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = type.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Text("Select ${selectedContainerType.label} Size*", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
            val packetOptions = listOf(1 to "1 Bottle", 6 to "6 Bottles", 12 to "12 Bottles", 24 to "24 Bottles")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                packetOptions.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { (packSize, label) ->
                            val selected = selectedPacketSize == packSize
                            FilterChip(
                                selected = selected,
                                onClick = { selectedPacketSize = packSize; errorMsg = null },
                                colors = waterFilterChipColors(),
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_packet_$packSize")
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = damagedQtyText,
                onValueChange = { damagedQtyText = it.filter { ch -> ch.isDigit() }; errorMsg = null },
                label = { Text("Damaged / Lost Bottle Quantity", fontSize = 10.5.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = !isDamagedValid || damagedInt > qtyInt,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_refill_damaged_qty")
            )

            // Return Loose Bottles back to In Use Checkbox
            Surface(
                onClick = { returnLooseToInUse = !returnLooseToInUse },
                shape = RoundedCornerShape(8.dp),
                color = if (returnLooseToInUse) WaterSkyLight else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, if (returnLooseToInUse) WaterAqua else BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("checkbox_return_loose_inuse")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = returnLooseToInUse,
                        onCheckedChange = { returnLooseToInUse = it },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Return Loose Bottles to In Use Stock",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyPrimary
                        )
                        Text(
                            text = if (returnLooseToInUse) {
                                "$remainingLooseBottles loose bottle(s) will return to In Use; only complete ${selectedContainerType.label}s go to Plant Full."
                            } else {
                                "$remainingLooseBottles loose bottle(s) will stay in Plant Full separately."
                            },
                            fontSize = 9.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }

            // Live Calculation Preview Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SuccessGreenLight,
                border = BorderStroke(0.5.dp, SuccessGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Conversion Calculation Preview", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterNavyPrimary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Usable Filled Bottles:", fontSize = 10.sp, color = TextSecondaryDark)
                        Text("$usableBottles bottles", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (selectedPacketSize <= 1) "Single Bottles (1/unit):" else "Complete ${selectedContainerType.label}s ($selectedPacketSize/pack):",
                            fontSize = 10.sp,
                            color = TextSecondaryDark
                        )
                        Text(
                            text = if (selectedPacketSize <= 1) "$completePackets Btl" else "$completePackets ${selectedContainerType.shortLabel} ($packedBottles Btl)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SuccessGreen
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (returnLooseToInUse) "Loose -> Back to In Use:" else "Remaining Loose -> Full:",
                            fontSize = 10.sp,
                            color = TextSecondaryDark
                        )
                        Text("$remainingLooseBottles bottles", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterBlueDeep)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Damaged / Lost Bottles:", fontSize = 10.sp, color = TextSecondaryDark)
                        Text("$damagedInt bottles", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (damagedInt > 0) ErrorRed else TextSecondaryDark)
                    }
                }
            }
        }

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// D. LOSS & DAMAGE FORM DIALOG
// -------------------------------------------------------------
@Composable
fun BottleLossDamageFormDialog(
    bottleSizes: List<BottleSize>,
    customers: List<Customer>,
    inventory: List<BottleInventory>,
    customerAllocations: List<CustomerBottleAllocation>,
    onDismiss: () -> Unit,
    onConfirmLoss: (
        source: BottleLossSource,
        customerId: String?,
        bottleSizeId: String,
        quantity: Int,
        remarks: String
    ) -> Unit
) {
    var source by remember { mutableStateOf(BottleLossSource.PLANT_FULL) }
    var selectedSize by remember { mutableStateOf(bottleSizes.firstOrNull()) }
    var selectedCustomer by remember { mutableStateOf(customers.firstOrNull()) }
    var quantity by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val sizeInv = inventory.find { it.bottleSizeId == selectedSize?.id }
    val customerHeldCount = if (selectedCustomer != null && selectedSize != null) {
        customerAllocations
            .filter { it.customerId == selectedCustomer!!.id && it.bottleSizeId == selectedSize!!.id }
            .sumOf { it.emptyHeld }
    } else 0

    val maxAvailable = when (source) {
        BottleLossSource.PLANT_FULL -> sizeInv?.atPlantFull ?: 0
        BottleLossSource.PLANT_EMPTY -> sizeInv?.atPlantEmpty ?: 0
        BottleLossSource.CUSTOMER_HELD -> customerHeldCount
    }

    val qtyInt = quantity.toIntOrNull() ?: 0

    MobileFormDialog(
        title = "Bottle Loss & Damage",
        subtitle = "بوتل ٹوٹ پھوٹ / نقصان کا ریکارڈ اور اسٹاک ایڈجسٹمنٹ",
        accentColor = ErrorRed,
        hasUnsavedChanges = quantity.isNotBlank() || remarks.isNotBlank(),
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val size = selectedSize
                    when {
                        size == null -> errorMsg = "Select bottle size."
                        source == BottleLossSource.CUSTOMER_HELD && selectedCustomer == null -> errorMsg = "Select customer."
                        qtyInt <= 0 -> errorMsg = "Enter quantity > 0."
                        qtyInt > maxAvailable -> errorMsg = "Exceeds available stock ($maxAvailable max)."
                        else -> onConfirmLoss(
                            source,
                            if (source == BottleLossSource.CUSTOMER_HELD) selectedCustomer?.id else null,
                            size.id,
                            qtyInt,
                            remarks.trim()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirm_loss_damage")
            ) {
                Text("Confirm Adjustment", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        CompactDropdownField(
            label = "Bottle Size*",
            selectedText = selectedSize?.name ?: "",
            options = bottleSizes,
            optionLabel = { it.name },
            onOptionSelected = { selectedSize = it; errorMsg = null }
        )

        CompactDropdownField(
            label = "Stock Source*",
            selectedText = source.label,
            options = BottleLossSource.values().toList(),
            optionLabel = { it.label },
            onOptionSelected = { source = it; errorMsg = null }
        )

        if (source == BottleLossSource.CUSTOMER_HELD) {
            CompactDropdownField(
                label = "Customer*",
                selectedText = selectedCustomer?.name ?: "",
                options = customers,
                optionLabel = { cust ->
                    val held = customerAllocations
                        .filter { it.customerId == cust.id && it.bottleSizeId == selectedSize?.id }
                        .sumOf { it.emptyHeld }
                    "${cust.name} (Held: $held)"
                },
                onOptionSelected = { selectedCustomer = it; errorMsg = null }
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ErrorRedLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Available in ${source.label}: $maxAvailable bottles",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = ErrorRed,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }

        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it.filter { ch -> ch.isDigit() }; errorMsg = null },
            label = { Text("Damaged / Lost Quantity*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = qtyInt > maxAvailable,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = { Text("Reason / Remarks", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// E. ADD / EDIT BOTTLE SIZE FORM DIALOG
// Equal-width properly aligned fields:
// 1. Bottle Name
// 2. Capacity
// 3. Usage Type (Multiple Time / Single Time)
// 4. Filling / Refilling Price
// 5. Purchase Cost
// 6. Security Deposit
// 7. UOM (default "Bottle")
// -------------------------------------------------------------
@Composable
fun BottleSizeFormDialog(
    existingSize: BottleSize?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        capacity: Double,
        usageType: BottleUsageType,
        refillPrice: Double,
        purchaseCost: Double,
        securityDeposit: Double,
        uom: String
    ) -> Unit
) {
    val isEditing = existingSize != null
    var bottleName by remember { mutableStateOf(existingSize?.name ?: "") }
    var capacity by remember { mutableStateOf(existingSize?.capacityLitres?.toString() ?: "") }
    var usageType by remember {
        mutableStateOf(
            if (existingSize?.usageType == BottleUsageType.ONE_TIME) BottleUsageType.ONE_TIME
            else BottleUsageType.MULTIPLE_TIME
        )
    }
    var fillingPrice by remember { mutableStateOf(existingSize?.defaultRefillPrice?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var purchaseCost by remember { mutableStateOf(existingSize?.latestPurchaseRate?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var securityDeposit by remember { mutableStateOf(existingSize?.depositAmount?.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var uom by remember { mutableStateOf(existingSize?.uom?.ifBlank { "Bottle" } ?: "Bottle") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val usageOptions = listOf(BottleUsageType.MULTIPLE_TIME, BottleUsageType.ONE_TIME)
    val hasUnsaved = if (isEditing) {
        bottleName != existingSize?.name ||
            capacity != existingSize?.capacityLitres?.toString() ||
            uom != existingSize?.uom
    } else {
        bottleName.isNotBlank() || capacity.isNotBlank() || fillingPrice.isNotBlank() || purchaseCost.isNotBlank()
    }

    MobileFormDialog(
        title = if (isEditing) "Edit Bottle Size" else "Bottle Size Registration",
        subtitle = if (isEditing) existingSize?.name else "نیا بوتل سائز رجسٹر کریں",
        hasUnsavedChanges = hasUnsaved,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val cleanName = bottleName.trim()
                    val capVal = capacity.toDoubleOrNull() ?: 0.0
                    val fillVal = fillingPrice.toDoubleOrNull() ?: 0.0
                    val purVal = purchaseCost.toDoubleOrNull() ?: 0.0
                    val depVal = securityDeposit.toDoubleOrNull() ?: 0.0
                    val cleanUom = uom.trim().ifBlank { "Bottle" }

                    when {
                        cleanName.isEmpty() -> errorMsg = "Bottle Name is required."
                        capVal <= 0.0 -> errorMsg = "Enter valid Capacity in Litres."
                        else -> onSave(cleanName, capVal, usageType, fillVal, purVal, depVal, cleanUom)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_save_bottle_size")
            ) {
                Text(if (isEditing) "Save Size" else "Add Size", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        OutlinedTextField(
            value = bottleName,
            onValueChange = { bottleName = it; errorMsg = null },
            label = { Text("1. Bottle Name*", fontSize = 10.5.sp) },
            placeholder = { Text("e.g. 19 Litre Bottle", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_bottle_name")
        )

        OutlinedTextField(
            value = capacity,
            onValueChange = { capacity = it; errorMsg = null },
            label = { Text("2. Capacity (Litres)*", fontSize = 10.5.sp) },
            placeholder = { Text("e.g. 19", fontSize = 11.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        CompactDropdownField(
            label = "3. Usage Type*",
            selectedText = usageType.shortLabel,
            options = usageOptions,
            optionLabel = { it.shortLabel },
            onOptionSelected = { usageType = it }
        )

        OutlinedTextField(
            value = fillingPrice,
            onValueChange = { fillingPrice = it },
            label = { Text("4. Filling / Refilling Price (Rs.)", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = purchaseCost,
            onValueChange = { purchaseCost = it },
            label = { Text("5. Purchase Cost (Rs.)", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = securityDeposit,
            onValueChange = { securityDeposit = it },
            label = { Text("6. Security Deposit (Rs.)", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uom,
            onValueChange = { uom = it },
            label = { Text("7. UOM*", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// SHARED HELPER UI COMPONENTS
// -------------------------------------------------------------
@Composable
fun MovementAuditRow(
    movement: BottleMovement,
    isAdmin: Boolean = false,
    onDeleteClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val (icon, color) = when (movement.movementType) {
                    BottleMovementType.PURCHASE_NEW -> Icons.Default.AddShoppingCart to SuccessGreen
                    BottleMovementType.DELIVERY_OUT -> Icons.Default.Upload to WaterNavyPrimary
                    BottleMovementType.RETURN_IN -> Icons.Default.Download to WaterAqua
                    BottleMovementType.CUSTOMER_DAMAGE, BottleMovementType.DAMAGED -> Icons.Default.BrokenImage to ErrorRed
                    else -> Icons.Default.Sync to WaterBlueDeep
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${movement.sizeName} • ${movement.movementType.label}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = WaterNavyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${movement.date} • ${movement.notes}",
                        fontSize = 9.5.sp,
                        color = TextSecondaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${movement.quantity} pcs",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = WaterNavyPrimary
                )

                if (isAdmin) {
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Record",
                            tint = ErrorRed,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(message: String, urduMessage: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Inventory2, contentDescription = null, tint = TextMuted, modifier = Modifier.size(30.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(message, fontSize = 11.sp, color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            Text(urduMessage, fontSize = 9.5.sp, color = TextMuted)
        }
    }
}
