package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppModule
import com.example.model.PermissionAction
import com.example.model.UserRole
import com.example.ui.screens.stockcost.BottleStockSubTab
import com.example.ui.screens.stockcost.CostingSubTab
import com.example.ui.screens.stockcost.OtherMaterialStockSubTab
import com.example.ui.screens.stockcost.StaffManagementSubTab
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel

enum class StockCostSubTab(
    val title: String,
    val urduTitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val adminOnly: Boolean = false
) {
    BOTTLE_STOCK("Bottles", "بوتل اسٹاک", Icons.Default.WaterDrop),
    OTHER_MATERIALS("Materials", "دیگر سامان", Icons.Default.Category),
    COSTING("Costing", "لاگت و منافع", Icons.Default.MonetizationOn, adminOnly = true),
    STAFF("Staff", "عملہ", Icons.Default.Badge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: WaterPlantViewModel,
    onManageSizesClick: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val canViewBottles = viewModel.hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.VIEW) ||
        viewModel.hasPermission(AppModule.BOTTLE_ISSUE, PermissionAction.VIEW) ||
        viewModel.hasPermission(AppModule.REFILL_BATCHES, PermissionAction.VIEW)
    val canViewMaterials = viewModel.hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.VIEW)
    val canViewCosting = viewModel.hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.EDIT) ||
        viewModel.hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.MANAGE)
    val canViewStaffTab = viewModel.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.VIEW)
    val canManageSizes = viewModel.hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.MANAGE)

    val availableTabs = remember(currentUser, canViewBottles, canViewMaterials, canViewCosting, canViewStaffTab) {
        buildList {
            if (canViewBottles) add(StockCostSubTab.BOTTLE_STOCK)
            if (canViewMaterials) add(StockCostSubTab.OTHER_MATERIALS)
            if (canViewCosting) add(StockCostSubTab.COSTING)
            if (canViewStaffTab) add(StockCostSubTab.STAFF)
        }
    }

    var activeTab by remember { mutableStateOf(availableTabs.firstOrNull() ?: StockCostSubTab.BOTTLE_STOCK) }

    LaunchedEffect(availableTabs) {
        if (availableTabs.isNotEmpty() && activeTab !in availableTabs) {
            activeTab = availableTabs.first()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Surface(
            color = WaterNavyPrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Compact Stock & Cost strip (wrap-width label + Admin Manage Sizes action)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = WaterMint,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Stock & Cost",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (canManageSizes) {
                        Surface(
                            onClick = onManageSizesClick,
                            color = WaterCyanBright.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("manage_bottle_sizes_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Manage Sizes",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Sizes",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Sub-tabs Segmented Bar (Single-line compact tabs)
                TabRow(
                    selectedTabIndex = availableTabs.indexOf(activeTab).coerceAtLeast(0),
                    containerColor = WaterNavyPrimary,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        val index = availableTabs.indexOf(activeTab).coerceAtLeast(0)
                        if (index < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[index]),
                                color = WaterMint,
                                height = 2.5.dp
                            )
                        }
                    },
                    divider = {}
                ) {
                    availableTabs.forEach { tab ->
                        val isSelected = activeTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { activeTab = tab },
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("subtab_${tab.name.lowercase()}"),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) WaterMint else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = tab.title,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = 10.5.sp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.88f),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
        ) {
            when (activeTab) {
                StockCostSubTab.BOTTLE_STOCK -> {
                    BottleStockSubTab(
                        viewModel = viewModel,
                        isAdmin = viewModel.hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.EDIT) ||
                            viewModel.hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.DELETE)
                    )
                }
                StockCostSubTab.OTHER_MATERIALS -> {
                    OtherMaterialStockSubTab(
                        viewModel = viewModel,
                        isAdmin = viewModel.hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.EDIT) ||
                            viewModel.hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.DELETE)
                    )
                }
                StockCostSubTab.COSTING -> {
                    CostingSubTab(
                        viewModel = viewModel,
                        isAdmin = canViewCosting
                    )
                }
                StockCostSubTab.STAFF -> {
                    StaffManagementSubTab(
                        viewModel = viewModel,
                        isAdmin = viewModel.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.ADD) ||
                            viewModel.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.EDIT) ||
                            viewModel.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)
                    )
                }
            }
        }
    }
}
