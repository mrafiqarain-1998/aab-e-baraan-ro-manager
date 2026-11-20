package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BottleSize
import com.example.model.BottleUsageType
import com.example.ui.screens.stockcost.BottleSizeFormDialog
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottleSizesScreen(
    viewModel: WaterPlantViewModel
) {
    val context = LocalContext.current
    val bottleSizes by viewModel.bottleSizes.collectAsState()
    val activeSizes = remember(bottleSizes) { bottleSizes.filter { it.active } }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingBottleSize by remember { mutableStateOf<BottleSize?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WaterSkySurface),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Bottle Sizes & Rates (بوتل سائز و ریٹ)",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "نئے بوتل سائز رجسٹر کریں، فلنگ ریٹ، قیمت خرید اور سیکیورٹی ڈپازٹ سیٹ کریں",
                        color = WaterCyanBright,
                        fontSize = 10.sp
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registered Bottle Sizes (${activeSizes.size})",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp,
                    color = WaterNavyPrimary
                )

                Button(
                    onClick = { showAddDialog = true },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add New", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        items(activeSizes, key = { it.id }) { size ->
            val isSingle = size.usageType == BottleUsageType.ONE_TIME
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = WaterCyanBright,
                                modifier = Modifier.size(22.dp)
                            )
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
                                        color = if (isSingle) SuccessGreenLight else WaterSkyLight
                                    ) {
                                        Text(
                                            text = if (isSingle) "Single Time" else "Multiple Time",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSingle) SuccessGreen else WaterBlueDeep,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${size.capacityLitres} Litres • UOM: ${size.uom}",
                                    fontSize = 10.sp,
                                    color = WaterAqua,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(onClick = { editingBottleSize = size }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Size", tint = WaterNavyPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DividerColor)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Filling Price", fontSize = 9.5.sp, color = TextSecondaryDark)
                            Text("Rs. ${size.defaultRefillPrice.toInt()}", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterNavyPrimary)
                        }
                        Column {
                            Text("Purchase Cost", fontSize = 9.5.sp, color = TextSecondaryDark)
                            Text("Rs. ${size.latestPurchaseRate.toInt()}", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimaryDark)
                        }
                        Column {
                            Text("Deposit", fontSize = 9.5.sp, color = TextSecondaryDark)
                            Text("Rs. ${size.depositAmount.toInt()}", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterAqua)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingBottleSize != null) {
        val editing = editingBottleSize
        BottleSizeFormDialog(
            existingSize = editing,
            onDismiss = {
                showAddDialog = false
                editingBottleSize = null
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
                        showAddDialog = false
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
                        if (success) editingBottleSize = null
                    }
                }
            }
        )
    }
}
