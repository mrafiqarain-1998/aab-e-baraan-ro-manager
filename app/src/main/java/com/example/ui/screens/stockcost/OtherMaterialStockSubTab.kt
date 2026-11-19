package com.example.ui.screens.stockcost

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
fun OtherMaterialStockSubTab(
    viewModel: WaterPlantViewModel,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val isViewOnly = !viewModel.hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.ADD) &&
        !viewModel.hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.EDIT)
    val materials by viewModel.factoryMaterials.collectAsState()
    val transactions by viewModel.materialTransactions.collectAsState()

    var showAddItemsDialog by remember { mutableStateOf(false) }
    var showIssueMaterialDialog by remember { mutableStateOf(false) }
    var showRefillStockDialog by remember { mutableStateOf(false) }
    var showMarkDamageDialog by remember { mutableStateOf(false) }
    var showMalfunctionDialog by remember { mutableStateOf(false) }

    var preselectedMaterialForEdit by remember { mutableStateOf<FactoryMaterial?>(null) }
    var materialForHistory by remember { mutableStateOf<FactoryMaterial?>(null) }
    var materialToDelete by remember { mutableStateOf<FactoryMaterial?>(null) }

    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    val activeMaterials = remember(materials) { materials.filter { it.active } }
    val displayedMaterials = remember(activeMaterials, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) activeMaterials
        else activeMaterials.filter { it.itemCategory == selectedCategoryFilter }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("other_material_stock_sub_tab"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Bar: 5 Aligned Iconic Buttons in One Row
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val canAddNewItems = viewModel.hasPermission(AppModule.NEW_ITEMS, PermissionAction.ADD)
                    val canAddMaterialStock = viewModel.hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.ADD)
                    val canEditMaterials = viewModel.hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.EDIT) || canAddMaterialStock
                    MaterialActionTile(
                        label = "Add Items",
                        icon = Icons.Default.AddBox,
                        containerColor = WaterSkyLight,
                        contentColor = WaterNavyPrimary,
                        enabled = canAddNewItems,
                        onClick = { showAddItemsDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_mat_add_items")
                    )
                    MaterialActionTile(
                        label = "Issue Material",
                        icon = Icons.Default.Output,
                        containerColor = WaterNavyPrimary,
                        contentColor = Color.White,
                        enabled = canEditMaterials && activeMaterials.isNotEmpty(),
                        onClick = { showIssueMaterialDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_mat_issue")
                    )
                    MaterialActionTile(
                        label = "Add Stock",
                        icon = Icons.Default.AddShoppingCart,
                        containerColor = SuccessGreenLight,
                        contentColor = SuccessGreen,
                        enabled = canAddMaterialStock && activeMaterials.isNotEmpty(),
                        onClick = { showRefillStockDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_mat_refill")
                    )
                    MaterialActionTile(
                        label = "Mark Damage",
                        icon = Icons.Default.BrokenImage,
                        containerColor = WarningOrangeLight,
                        contentColor = WarningOrange,
                        enabled = canEditMaterials && activeMaterials.isNotEmpty(),
                        onClick = { showMarkDamageDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_mat_damage")
                    )
                    MaterialActionTile(
                        label = "Malfunction",
                        icon = Icons.Default.ReportProblem,
                        containerColor = ErrorRedLight,
                        contentColor = ErrorRed,
                        enabled = !isViewOnly && activeMaterials.isNotEmpty(),
                        onClick = { showMalfunctionDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_mat_malfunction")
                    )
                }
            }
        }

        // 2. Category Filter Bar
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        colors = waterFilterChipColors(),
                        label = { Text("All (${activeMaterials.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    )
                }
                items(StockMasterOptions.MATERIAL_CATEGORIES) { cat ->
                    val count = activeMaterials.count { it.itemCategory == cat }
                    if (count > 0) {
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                            colors = waterFilterChipColors(),
                            label = { Text("$cat ($count)", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                        )
                    }
                }
            }
        }

        // 3. Material Inventory List Cards
        item {
            Column {
                Text(
                    text = "Material Inventory (${displayedMaterials.size})",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = WaterNavyPrimary
                )
                Text(
                    text = "فیکٹری میٹریل، قیمت اور دستیاب اسٹاک",
                    fontSize = 9.5.sp,
                    color = TextSecondaryDark
                )
            }
        }

        if (displayedMaterials.isEmpty()) {
            item {
                EmptyStateCard(
                    message = "No materials registered yet.",
                    urduMessage = "نیا سامان شامل کرنے کے لیے Add Items دبائیں"
                )
            }
        } else {
            items(displayedMaterials, key = { it.id }) { material ->
                CompactMaterialCard(
                    material = material,
                    canEditOrDelete = isAdmin,
                    onEditClick = { preselectedMaterialForEdit = material },
                    onHistoryClick = { materialForHistory = material },
                    onDeleteClick = { materialToDelete = material }
                )
            }
        }

        // 4. Recent Material Transaction Log
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Recent Material Activity (${transactions.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = WaterNavyPrimary
                    )
                    Text("سامان کے اجرا، خریداری اور نقصان کا ریکارڈ", fontSize = 9.5.sp, color = TextSecondaryDark)
                }
                if (isAdmin && transactions.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            viewModel.clearAllMaterialTransactions()
                            Toast.makeText(context, "Transactions cleared", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Clear", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val recentTx = transactions.take(15)
        if (recentTx.isEmpty()) {
            item {
                EmptyStateCard(
                    message = "No material transactions logged yet.",
                    urduMessage = "ابھی تک کوئی ٹرانزیکشن درج نہیں ہوئی"
                )
            }
        } else {
            items(recentTx, key = { it.id }) { tx ->
                MaterialTransactionRow(tx = tx)
            }
        }
    }

    // --- DIALOGS ---

    // A. Add Items Form
    if (showAddItemsDialog) {
        AddMaterialItemDialog(
            onDismiss = { showAddItemsDialog = false },
            onSave = { newMat ->
                viewModel.addFactoryMaterial(newMat) {
                    showAddItemsDialog = false
                    Toast.makeText(context, "Item '${newMat.name}' added", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // B. Issue Material Form
    if (showIssueMaterialDialog) {
        IssueMaterialFormDialog(
            materials = activeMaterials,
            onDismiss = { showIssueMaterialDialog = false },
            onConfirmIssue = { matId, qty, issueFor, remarks ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                viewModel.issueFactoryMaterial(
                    materialId = matId,
                    category = issueFor,
                    quantity = qty,
                    date = today,
                    responsiblePerson = currentUser.name,
                    remarks = remarks
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showIssueMaterialDialog = false
                }
            }
        )
    }

    // C. Add Stock Form
    if (showRefillStockDialog) {
        RefillMaterialStockDialog(
            materials = activeMaterials,
            onDismiss = { showRefillStockDialog = false },
            onConfirmRefill = { matId, qty, unitCost, supplier, paymentSource, paymentMode, purchaseDate ->
                viewModel.refillFactoryMaterial(
                    materialId = matId,
                    quantity = qty,
                    purchaseRate = unitCost,
                    date = purchaseDate,
                    supplierName = supplier,
                    invoiceNo = "",
                    notes = "Added via ${paymentSource.label} (${paymentMode.label})",
                    paymentSource = paymentSource,
                    paymentMode = paymentMode
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showRefillStockDialog = false
                }
            }
        )
    }

    // D. Mark Damage Form
    if (showMarkDamageDialog) {
        MaterialLossActionDialog(
            title = "Mark Damage",
            subtitle = "خراب یا ٹوٹے ہوئے سامان کا اندراج",
            confirmLabel = "Confirm Damage",
            accentColor = WarningOrange,
            materials = activeMaterials,
            onDismiss = { showMarkDamageDialog = false },
            onConfirm = { matId, qty, remarks ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                viewModel.recordMaterialDamageOrMalfunction(
                    materialId = matId,
                    actionType = MaterialActionType.DAMAGE,
                    quantity = qty,
                    date = today,
                    remarks = remarks
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showMarkDamageDialog = false
                }
            }
        )
    }

    // E. Malfunction Form
    if (showMalfunctionDialog) {
        MaterialLossActionDialog(
            title = "Malfunction",
            subtitle = "ناکارہ یا خراب پرزہ جات کی رپورٹ",
            confirmLabel = "Confirm Malfunction",
            accentColor = ErrorRed,
            materials = activeMaterials,
            onDismiss = { showMalfunctionDialog = false },
            onConfirm = { matId, qty, remarks ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                viewModel.recordMaterialDamageOrMalfunction(
                    materialId = matId,
                    actionType = MaterialActionType.MALFUNCTION,
                    quantity = qty,
                    date = today,
                    remarks = remarks
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showMalfunctionDialog = false
                }
            }
        )
    }

    // F. Edit Material Form
    preselectedMaterialForEdit?.let { mat ->
        EditMaterialDialog(
            material = mat,
            onDismiss = { preselectedMaterialForEdit = null },
            onSave = { updated ->
                viewModel.updateFactoryMaterial(updated) {
                    preselectedMaterialForEdit = null
                    Toast.makeText(context, "Item updated", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // G. Material History Dialog
    materialForHistory?.let { mat ->
        val matHistory = transactions.filter { it.materialId == mat.id }
        MaterialHistoryDialog(
            material = mat,
            transactions = matHistory,
            onDismiss = { materialForHistory = null }
        )
    }

    // H. Delete Material Dialog
    materialToDelete?.let { mat ->
        AlertDialog(
            onDismissRequest = { materialToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            title = {
                Text("Delete ${mat.name}?", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = ErrorRed)
            },
            text = {
                if (mat.currentStock > 0.0) {
                    Text(
                        "Cannot delete: ${mat.currentStock} ${mat.uom} still in stock. Reduce stock to 0 first.",
                        color = ErrorRed,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        "Delete or archive '${mat.name}'? Transaction history remains intact.",
                        fontSize = 11.sp,
                        color = TextPrimaryDark
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = mat.currentStock <= 0.0,
                    onClick = {
                        viewModel.deleteOrArchiveFactoryMaterial(mat.id) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) materialToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { materialToDelete = null }) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
private fun MaterialActionTile(
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
        shape = RoundedCornerShape(9.dp),
        color = if (enabled) containerColor else Color(0xFFF1F5F9),
        modifier = modifier.height(62.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) contentColor else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 8.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (enabled) contentColor else TextMuted,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CompactMaterialCard(
    material: FactoryMaterial,
    canEditOrDelete: Boolean,
    onEditClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val minLevel = material.minStockLevel.coerceAtLeast(0.0)
    val isCritical = material.currentStock <= 0.0 || (minLevel > 0.0 && material.currentStock <= minLevel)
    val isNearMin = !isCritical && (
        (minLevel > 0.0 && material.currentStock <= minLevel * 1.25) ||
            (minLevel <= 0.0 && material.currentStock <= 5.0)
        )
    val stockBg = when {
        isCritical -> ErrorRedLight
        isNearMin -> WarningOrangeLight
        else -> SuccessGreenLight
    }
    val stockFg = when {
        isCritical -> ErrorRed
        isNearMin -> WarningOrange
        else -> SuccessGreen
    }
    val cardContainerColor = when {
        isCritical -> ErrorRedLight.copy(alpha = 0.32f)
        isNearMin -> WarningOrangeLight.copy(alpha = 0.25f)
        else -> Color.White
    }
    val cardBorder = when {
        isCritical -> BorderStroke(1.2.dp, ErrorRed.copy(alpha = 0.45f))
        isNearMin -> BorderStroke(1.dp, WarningOrange.copy(alpha = 0.4f))
        else -> BorderStroke(0.5.dp, BorderLight)
    }

    Card(
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHistoryClick() }
            .testTag("material_card_${material.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = material.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp,
                            color = WaterNavyPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WaterSkyLight
                        ) {
                            Text(
                                text = material.itemCategory,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WaterBlueDeep,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (material.costType == ExpenseType.CAPEX) WarningOrangeLight else SuccessGreenLight
                        ) {
                            Text(
                                text = material.costType.name,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (material.costType == ExpenseType.CAPEX) WarningOrange else SuccessGreen,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (material.supplierName.isNotBlank()) {
                            Text(
                                text = "Supplier: ${material.supplierName}",
                                fontSize = 9.5.sp,
                                color = TextSecondaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (minLevel > 0.0) {
                            Text(
                                text = "Min: ${formatMaterialQty(minLevel)} ${material.uom}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCritical) ErrorRed else TextSecondaryDark,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onHistoryClick, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.History, contentDescription = "Item Details & History", tint = WaterNavyPrimary, modifier = Modifier.size(16.dp))
                    }
                    if (canEditOrDelete) {
                        IconButton(onClick = onEditClick, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Item", tint = WaterBlueDeep, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDeleteClick, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Item", tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(0.5.dp, BorderLight),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("Unit Price", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryDark)
                        Text(
                            text = "Rs. ${material.purchaseRate.toInt()} / ${material.uom}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WaterNavyPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = stockBg,
                    border = BorderStroke(0.8.dp, stockFg.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = when {
                                isCritical -> "Low Stock (Alert)"
                                isNearMin -> "Near Minimum"
                                else -> "Available Stock"
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = stockFg
                        )
                        Text(
                            text = "${formatMaterialQty(material.currentStock)} ${material.uom}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = stockFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// A. ADD ITEMS FORM DIALOG
// -------------------------------------------------------------
@Composable
fun AddMaterialItemDialog(
    onDismiss: () -> Unit,
    onSave: (FactoryMaterial) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var itemCategory by remember { mutableStateOf(StockMasterOptions.MATERIAL_CATEGORIES.first()) }
    var costType by remember { mutableStateOf(ExpenseType.OPEX) }
    var uom by remember { mutableStateOf(StockMasterOptions.MATERIAL_UOMS.first()) }
    var currentStock by remember { mutableStateOf("") }
    var minStockLevel by remember { mutableStateOf("") }
    var purchaseRate by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val hasUnsaved = itemName.isNotBlank() || currentStock.isNotBlank() || minStockLevel.isNotBlank() || purchaseRate.isNotBlank() || supplier.isNotBlank()

    MobileFormDialog(
        title = "Add Items",
        subtitle = "نیا میٹریل یا اسپیئر پارٹ شامل کریں",
        hasUnsavedChanges = hasUnsaved,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val cleanName = itemName.trim()
                    val stockVal = currentStock.toDoubleOrNull() ?: 0.0
                    val minVal = (minStockLevel.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    val rateVal = purchaseRate.toDoubleOrNull() ?: 0.0
                    when {
                        cleanName.isEmpty() -> errorMsg = "Item Name is required."
                        stockVal < 0.0 || rateVal < 0.0 -> errorMsg = "Values cannot be negative."
                        else -> {
                            val usage = if (itemCategory in listOf("Packing Material", "Bottle & Cap")) {
                                MaterialUsageType.PRODUCTION_CONSUMABLE
                            } else {
                                MaterialUsageType.MAINTENANCE_MATERIAL
                            }
                            onSave(
                                FactoryMaterial(
                                    name = cleanName,
                                    itemCategory = itemCategory,
                                    costType = costType,
                                    uom = uom,
                                    openingStock = stockVal,
                                    currentStock = stockVal,
                                    minStockLevel = minVal,
                                    purchaseRate = rateVal,
                                    supplierName = supplier.trim(),
                                    usageType = usage,
                                    defaultIssueCategory = MaterialIssueCategory.PRODUCTION
                                )
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_save_material_item")
            ) {
                Text("Save Items", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        OutlinedTextField(
            value = itemName,
            onValueChange = { itemName = it; errorMsg = null },
            label = { Text("Item Name*", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        CompactDropdownField(
            label = "Item Category*",
            selectedText = itemCategory,
            options = StockMasterOptions.MATERIAL_CATEGORIES,
            optionLabel = { it },
            onOptionSelected = { itemCategory = it }
        )

        Text("Cost Category (لاگت کی قسم)*", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(ExpenseType.OPEX to "OPEX (Operating)", ExpenseType.CAPEX to "CAPEX (Asset)").forEach { (type, label) ->
                val selected = costType == type
                Surface(
                    onClick = { costType = type },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) WaterNavyPrimary else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (selected) WaterNavyPrimary else BorderLight),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("mat_cost_type_${type.name.lowercase()}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else WaterNavyPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        CompactDropdownField(
            label = "UOM*",
            selectedText = uom,
            options = StockMasterOptions.MATERIAL_UOMS,
            optionLabel = { it },
            onOptionSelected = { uom = it }
        )

        OutlinedTextField(
            value = currentStock,
            onValueChange = { currentStock = it; errorMsg = null },
            label = { Text("Current Stock", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = minStockLevel,
            onValueChange = { minStockLevel = it; errorMsg = null },
            label = { Text("Minimum Stock Level (کم از کم حد)", fontSize = 10.5.sp) },
            placeholder = { Text("e.g. 10 (color highlight alert)", fontSize = 10.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_min_stock_level")
        )

        OutlinedTextField(
            value = purchaseRate,
            onValueChange = { purchaseRate = it; errorMsg = null },
            label = { Text("Purchase Rate (Rs.)", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = supplier,
            onValueChange = { supplier = it },
            label = { Text("Supplier", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// B. ISSUE MATERIAL FORM DIALOG
// -------------------------------------------------------------
@Composable
fun IssueMaterialFormDialog(
    materials: List<FactoryMaterial>,
    onDismiss: () -> Unit,
    onConfirmIssue: (materialId: String, qty: Double, issueFor: MaterialIssueCategory, remarks: String) -> Unit
) {
    var selectedMaterial by remember { mutableStateOf(materials.firstOrNull()) }
    var quantity by remember { mutableStateOf("") }
    val issueForOptions = remember {
        listOf(
            MaterialIssueCategory.PRODUCTION,
            MaterialIssueCategory.DRIVER,
            MaterialIssueCategory.REPAIRING,
            MaterialIssueCategory.REPLACEMENT
        )
    }
    var issueFor by remember { mutableStateOf(issueForOptions.first()) }
    var remarks by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val avail = selectedMaterial?.currentStock ?: 0.0
    val qtyVal = quantity.toDoubleOrNull() ?: 0.0

    MobileFormDialog(
        title = "Issue Material",
        subtitle = "پیداوار، ڈرائیور، مرمت یا تبدیلی کے لیے سامان جاری کریں",
        hasUnsavedChanges = quantity.isNotBlank() || remarks.isNotBlank(),
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val mat = selectedMaterial
                    when {
                        mat == null -> errorMsg = "Select an item."
                        qtyVal <= 0.0 -> errorMsg = "Enter quantity > 0."
                        qtyVal > mat.currentStock -> errorMsg = "Exceeds available stock (${formatMaterialQty(mat.currentStock)} ${mat.uom})."
                        else -> onConfirmIssue(mat.id, qtyVal, issueFor, remarks.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Issue", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        CompactDropdownField(
            label = "Select Items*",
            selectedText = selectedMaterial?.let { "${it.name} (${formatMaterialQty(it.currentStock)} ${it.uom})" } ?: "",
            options = materials,
            optionLabel = { "${it.name} • Avail: ${formatMaterialQty(it.currentStock)} ${it.uom}" },
            onOptionSelected = { selectedMaterial = it; errorMsg = null }
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = WaterSkyLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Available: ${formatMaterialQty(avail)} ${selectedMaterial?.uom ?: ""}",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }

        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it; errorMsg = null },
            label = { Text("Quantity*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = qtyVal > avail,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        CompactDropdownField(
            label = "Issue For*",
            selectedText = issueFor.label,
            options = issueForOptions,
            optionLabel = { it.label },
            onOptionSelected = { issueFor = it }
        )

        OutlinedTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = { Text("Remarks", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// C. REFILL STOCK FORM DIALOG
// -------------------------------------------------------------
@Composable
fun RefillMaterialStockDialog(
    materials: List<FactoryMaterial>,
    onDismiss: () -> Unit,
    onConfirmRefill: (
        materialId: String,
        qty: Double,
        unitCost: Double,
        supplier: String,
        paymentSource: PaymentSource,
        paymentMode: StockPaymentMode,
        purchaseDate: String
    ) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedMaterial by remember { mutableStateOf(materials.firstOrNull()) }
    var quantity by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf(selectedMaterial?.purchaseRate?.toInt()?.toString() ?: "") }
    var supplier by remember { mutableStateOf(selectedMaterial?.supplierName ?: "") }
    var purchaseDate by remember { mutableStateOf(dateFormat.format(Date())) }
    var paymentSource by remember { mutableStateOf(PaymentSource.PAY_FROM_SALE) }
    var paymentMode by remember { mutableStateOf(StockPaymentMode.CASH) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val qtyVal = quantity.toDoubleOrNull() ?: 0.0
    val costVal = unitCost.toDoubleOrNull() ?: 0.0
    val total = qtyVal * costVal

    MobileFormDialog(
        title = "Add Stock",
        subtitle = "میٹریل اسٹاک کی خریداری اور اضافہ",
        accentColor = SuccessGreen,
        hasUnsavedChanges = quantity.isNotBlank(),
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val mat = selectedMaterial
                    when {
                        mat == null -> errorMsg = "Select an item."
                        qtyVal <= 0.0 -> errorMsg = "Enter quantity > 0."
                        costVal < 0.0 -> errorMsg = "Enter valid unit cost."
                        else -> onConfirmRefill(
                            mat.id,
                            qtyVal,
                            costVal,
                            supplier.trim(),
                            paymentSource,
                            paymentMode,
                            purchaseDate.ifBlank { dateFormat.format(Date()) }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        CompactDropdownField(
            label = "Select Items*",
            selectedText = selectedMaterial?.name ?: "",
            options = materials,
            optionLabel = { "${it.name} (${formatMaterialQty(it.currentStock)} ${it.uom})" },
            onOptionSelected = { mat ->
                selectedMaterial = mat
                unitCost = if (mat.purchaseRate > 0) mat.purchaseRate.toInt().toString() else ""
                supplier = mat.supplierName
                errorMsg = null
            }
        )

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
                    .testTag("btn_pick_material_stock_date")
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

        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it; errorMsg = null },
            label = { Text("Quantity*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = unitCost,
            onValueChange = { unitCost = it; errorMsg = null },
            label = { Text("Unit Cost (Rs.)*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = supplier,
            onValueChange = { supplier = it },
            label = { Text("Supplier", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Payment Source*", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
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

        Text("Payment Mode*", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StockPaymentMode.values().forEach { mode ->
                FilterChip(
                    selected = paymentMode == mode,
                    onClick = { paymentMode = mode },
                    colors = waterFilterChipColors(),
                    label = { Text(mode.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (total > 0) {
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
                    Text("Rs. ${total.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                }
            }
        }

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// D & E. MARK DAMAGE / MALFUNCTION FORM DIALOG
// -------------------------------------------------------------
@Composable
fun MaterialLossActionDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    accentColor: Color,
    materials: List<FactoryMaterial>,
    onDismiss: () -> Unit,
    onConfirm: (materialId: String, qty: Double, remarks: String) -> Unit
) {
    var selectedMaterial by remember { mutableStateOf(materials.firstOrNull()) }
    var quantity by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val avail = selectedMaterial?.currentStock ?: 0.0
    val qtyVal = quantity.toDoubleOrNull() ?: 0.0

    MobileFormDialog(
        title = title,
        subtitle = subtitle,
        accentColor = accentColor,
        hasUnsavedChanges = quantity.isNotBlank() || remarks.isNotBlank(),
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val mat = selectedMaterial
                    when {
                        mat == null -> errorMsg = "Select an item."
                        qtyVal <= 0.0 -> errorMsg = "Enter quantity > 0."
                        qtyVal > mat.currentStock -> errorMsg = "Exceeds available stock (${formatMaterialQty(mat.currentStock)} ${mat.uom})."
                        else -> onConfirm(mat.id, qtyVal, remarks.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(confirmLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        CompactDropdownField(
            label = "Select Items*",
            selectedText = selectedMaterial?.let { "${it.name} (${formatMaterialQty(it.currentStock)} ${it.uom})" } ?: "",
            options = materials,
            optionLabel = { "${it.name} • Avail: ${formatMaterialQty(it.currentStock)} ${it.uom}" },
            onOptionSelected = { selectedMaterial = it; errorMsg = null }
        )

        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it; errorMsg = null },
            label = { Text("Quantity*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = qtyVal > avail,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = { Text("Remarks", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// F. EDIT MATERIAL DIALOG
// -------------------------------------------------------------
@Composable
fun EditMaterialDialog(
    material: FactoryMaterial,
    onDismiss: () -> Unit,
    onSave: (FactoryMaterial) -> Unit
) {
    var name by remember { mutableStateOf(material.name) }
    var itemCategory by remember { mutableStateOf(material.itemCategory.ifBlank { StockMasterOptions.MATERIAL_CATEGORIES.first() }) }
    var costType by remember { mutableStateOf(material.costType) }
    var uom by remember { mutableStateOf(material.uom.ifBlank { StockMasterOptions.MATERIAL_UOMS.first() }) }
    var purchaseRate by remember { mutableStateOf(material.purchaseRate.toInt().toString()) }
    var minStockLevel by remember {
        mutableStateOf(
            if (material.minStockLevel > 0.0) formatMaterialQty(material.minStockLevel) else ""
        )
    }
    var supplier by remember { mutableStateOf(material.supplierName) }

    val hasUnsaved = name != material.name ||
        itemCategory != material.itemCategory ||
        costType != material.costType ||
        uom != material.uom ||
        supplier != material.supplierName ||
        (minStockLevel.toDoubleOrNull() ?: 0.0) != material.minStockLevel

    MobileFormDialog(
        title = "Edit Item",
        subtitle = material.name,
        hasUnsavedChanges = hasUnsaved,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val rate = purchaseRate.toDoubleOrNull() ?: material.purchaseRate
                    val minVal = (minStockLevel.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    if (name.isNotBlank()) {
                        onSave(
                            material.copy(
                                name = name.trim(),
                                itemCategory = itemCategory,
                                costType = costType,
                                uom = uom,
                                purchaseRate = rate,
                                minStockLevel = minVal,
                                supplierName = supplier.trim()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Update Item", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item Name*", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        CompactDropdownField(
            label = "Item Category*",
            selectedText = itemCategory,
            options = StockMasterOptions.MATERIAL_CATEGORIES,
            optionLabel = { it },
            onOptionSelected = { itemCategory = it }
        )
        Text("Cost Category (لاگت کی قسم)*", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(ExpenseType.OPEX to "OPEX (Operating)", ExpenseType.CAPEX to "CAPEX (Asset)").forEach { (type, label) ->
                val selected = costType == type
                Surface(
                    onClick = { costType = type },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) WaterNavyPrimary else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (selected) WaterNavyPrimary else BorderLight),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else WaterNavyPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        CompactDropdownField(
            label = "UOM*",
            selectedText = uom,
            options = StockMasterOptions.MATERIAL_UOMS,
            optionLabel = { it },
            onOptionSelected = { uom = it }
        )
        OutlinedTextField(
            value = purchaseRate,
            onValueChange = { purchaseRate = it },
            label = { Text("Purchase Rate (Rs.)", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = minStockLevel,
            onValueChange = { minStockLevel = it },
            label = { Text("Minimum Stock Level (کم از کم حد)", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = supplier,
            onValueChange = { supplier = it },
            label = { Text("Supplier", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// -------------------------------------------------------------
// MATERIAL ITEM DETAIL & PURCHASE / ISSUE HISTORY DIALOG
// -------------------------------------------------------------
@Composable
fun MaterialHistoryDialog(
    material: FactoryMaterial,
    transactions: List<MaterialTransaction>,
    onDismiss: () -> Unit
) {
    val purchasesChronological = remember(transactions) {
        transactions
            .filter { it.actionType == MaterialActionType.REFILL }
            .sortedWith(compareBy<MaterialTransaction> { it.date }.thenBy { it.timestamp })
    }
    val firstPurchase = purchasesChronological.firstOrNull()
    val subsequentPurchases = if (purchasesChronological.size > 1) {
        purchasesChronological.drop(1).reversed()
    } else emptyList()
    val issueTransactions = remember(transactions) {
        transactions
            .filter { it.actionType == MaterialActionType.ISSUE }
            .sortedWith(compareByDescending<MaterialTransaction> { it.date }.thenByDescending { it.timestamp })
    }
    val lossTransactions = remember(transactions) {
        transactions
            .filter { it.actionType == MaterialActionType.DAMAGE || it.actionType == MaterialActionType.MALFUNCTION }
            .sortedWith(compareByDescending<MaterialTransaction> { it.date }.thenByDescending { it.timestamp })
    }

    var selectedTab by remember { mutableStateOf(0) } // 0 = Summary & All, 1 = Purchases, 2 = Issued

    MobileFormDialog(
        title = material.name,
        subtitle = "${material.itemCategory} • خریداری اور اجراء کی مکمل تفصیل",
        onDismiss = onDismiss,
        footerContent = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White)
            ) {
                Text("Close", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        // Overview Banner
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = WaterSkyLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Available Stock:", fontSize = 10.sp, color = TextSecondaryDark)
                    Text(
                        "${formatMaterialQty(material.currentStock)} ${material.uom}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WaterNavyPrimary
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Minimum Stock Level:", fontSize = 10.sp, color = TextSecondaryDark)
                    Text(
                        if (material.minStockLevel > 0.0) "${formatMaterialQty(material.minStockLevel)} ${material.uom}" else "Not Set",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (material.minStockLevel > 0.0 && material.currentStock <= material.minStockLevel) ErrorRed else WaterNavyPrimary
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Latest Rate:", fontSize = 10.sp, color = TextSecondaryDark)
                    Text("Rs. ${material.purchaseRate.toInt()} / ${material.uom}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                }
                HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("First Purchase (پہلی خریداری):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    Text(
                        firstPurchase?.let { "${it.date} (${formatMaterialQty(it.quantity)} ${it.uom})" } ?: "No purchase logged",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SuccessGreen
                    )
                }
            }
        }

        // Section Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                0 to "All (${transactions.size})",
                1 to "Purchases (${purchasesChronological.size})",
                2 to "Issued (${issueTransactions.size})"
            ).forEach { (idx, label) ->
                FilterChip(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    colors = waterFilterChipColors(),
                    label = { Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (selectedTab) {
            1 -> {
                if (purchasesChronological.isEmpty()) {
                    Text("No purchase records found for this item.", color = TextMuted, fontSize = 11.sp)
                } else {
                    firstPurchase?.let { first ->
                        Text("First Purchase (پہلی خریداری)", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                        MaterialHistoryTxCard(tx = first, highlightBadge = "First Purchase")
                    }
                    if (subsequentPurchases.isNotEmpty()) {
                        Text("Subsequent Purchases (بعد کی خریداریاں)", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterNavyPrimary)
                        subsequentPurchases.forEach { tx ->
                            MaterialHistoryTxCard(tx = tx)
                        }
                    }
                }
            }
            2 -> {
                if (issueTransactions.isEmpty()) {
                    Text("No issue records found for this item.", color = TextMuted, fontSize = 11.sp)
                } else {
                    issueTransactions.forEach { tx ->
                        MaterialHistoryTxCard(tx = tx)
                    }
                }
            }
            else -> {
                if (transactions.isEmpty()) {
                    Text("No transaction history recorded yet for this item.", color = TextMuted, fontSize = 11.sp)
                } else {
                    if (purchasesChronological.isNotEmpty()) {
                        Text("Purchases (خریداری کا ریکارڈ)", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                        firstPurchase?.let { first ->
                            MaterialHistoryTxCard(tx = first, highlightBadge = "First Purchase")
                        }
                        subsequentPurchases.take(15).forEach { tx ->
                            MaterialHistoryTxCard(tx = tx)
                        }
                    }
                    if (issueTransactions.isNotEmpty()) {
                        Text("Issued History (اجراء کا ریکارڈ)", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterNavyPrimary)
                        issueTransactions.take(15).forEach { tx ->
                            MaterialHistoryTxCard(tx = tx)
                        }
                    }
                    if (lossTransactions.isNotEmpty()) {
                        Text("Damage & Malfunction", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = ErrorRed)
                        lossTransactions.take(10).forEach { tx ->
                            MaterialHistoryTxCard(tx = tx)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialHistoryTxCard(
    tx: MaterialTransaction,
    highlightBadge: String? = null
) {
    val txColor = when (tx.actionType) {
        MaterialActionType.REFILL -> SuccessGreen
        MaterialActionType.DAMAGE, MaterialActionType.MALFUNCTION -> ErrorRed
        else -> WaterNavyPrimary
    }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${tx.date} • ${tx.actionType.label}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = txColor
                    )
                    if (!highlightBadge.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(4.dp), color = SuccessGreenLight) {
                            Text(
                                text = highlightBadge,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                val costStr = if (tx.totalCost > 0.0) " • Rs. ${tx.totalCost.toInt()}" else ""
                Text(
                    "Qty: ${formatMaterialQty(tx.quantity)} ${tx.uom}$costStr • Bal: ${formatMaterialQty(tx.balanceAfter)}",
                    fontSize = 10.sp,
                    color = TextPrimaryDark
                )
                val details = listOfNotNull(
                    tx.issueCategory?.label,
                    tx.supplierOrReceiver.takeIf { it.isNotBlank() },
                    tx.notes.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (details.isNotBlank()) {
                    Text(
                        details,
                        fontSize = 9.5.sp,
                        color = TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MaterialTransactionRow(tx: MaterialTransaction) {
    val isIn = tx.actionType == MaterialActionType.REFILL
    val isLoss = tx.actionType == MaterialActionType.DAMAGE || tx.actionType == MaterialActionType.MALFUNCTION
    val color = when {
        isIn -> SuccessGreen
        isLoss -> ErrorRed
        else -> WaterNavyPrimary
    }
    val icon = when (tx.actionType) {
        MaterialActionType.REFILL -> Icons.Default.AddShoppingCart
        MaterialActionType.DAMAGE -> Icons.Default.BrokenImage
        MaterialActionType.MALFUNCTION -> Icons.Default.ReportProblem
        else -> Icons.Default.Output
    }

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
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${tx.materialName} • ${tx.actionType.label}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = WaterNavyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOfNotNull(
                        tx.date,
                        tx.issueCategory?.label,
                        tx.supplierOrReceiver.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    Text(
                        meta,
                        fontSize = 9.5.sp,
                        color = TextSecondaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (isIn) "+" else "-"}${formatMaterialQty(tx.quantity)} ${tx.uom}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = color
                )
                if (tx.totalCost > 0) {
                    Text(
                        "Rs. ${tx.totalCost.toInt()}",
                        fontSize = 9.5.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

private fun formatMaterialQty(qty: Double): String {
    return if (qty % 1.0 == 0.0) qty.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", qty)
}
