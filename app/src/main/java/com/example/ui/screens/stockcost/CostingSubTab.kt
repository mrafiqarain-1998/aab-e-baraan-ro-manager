package com.example.ui.screens.stockcost

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.ui.components.CompactDropdownField
import com.example.ui.components.CompactFormTextField
import com.example.ui.components.MobileFormDialog
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class BottleCostingOption(
    val key: String,
    val label: String,
    val bottleSizeId: String,
    val bottleSizeName: String,
    val packingType: CostingPackingType,
    val defaultPackQty: Int,
    val isMultiTimeUse: Boolean,
    val defaultSalePrice: Double
)

private fun buildBottleCostingOptions(bottleSizes: List<BottleSize>): List<BottleCostingOption> {
    val active = bottleSizes.filter { it.active }

    fun findByCapacity(minL: Double, maxL: Double, fallbackId: String): BottleSize? {
        return active.find { it.id.equals(fallbackId, ignoreCase = true) }
            ?: active.find { it.capacityLitres in minL..maxL }
    }

    val s19 = findByCapacity(18.0, 20.5, "size_19l")
    val s12 = findByCapacity(11.0, 13.0, "size_12l")
    val s6 = findByCapacity(5.5, 6.5, "size_6l")
    val s15 = findByCapacity(1.4, 1.6, "size_1_5l")
    val s500 = findByCapacity(0.45, 0.55, "size_500ml")

    val options = mutableListOf(
        BottleCostingOption(
            key = "${s19?.id ?: "size_19l"}__SINGLE",
            label = "19L",
            bottleSizeId = s19?.id ?: "size_19l",
            bottleSizeName = "19L",
            packingType = CostingPackingType.SINGLE,
            defaultPackQty = 1,
            isMultiTimeUse = s19?.let { it.usageType == BottleUsageType.MULTIPLE_TIME || it.isReturnableBottle } ?: true,
            defaultSalePrice = s19?.defaultRefillPrice ?: 150.0
        ),
        BottleCostingOption(
            key = "${s12?.id ?: "size_12l"}__SINGLE",
            label = "12L",
            bottleSizeId = s12?.id ?: "size_12l",
            bottleSizeName = "12L",
            packingType = CostingPackingType.SINGLE,
            defaultPackQty = 1,
            isMultiTimeUse = s12?.let { it.usageType == BottleUsageType.MULTIPLE_TIME || it.isReturnableBottle } ?: true,
            defaultSalePrice = s12?.defaultRefillPrice ?: 110.0
        ),
        BottleCostingOption(
            key = "${s6?.id ?: "size_6l"}__SINGLE",
            label = "6L",
            bottleSizeId = s6?.id ?: "size_6l",
            bottleSizeName = "6L",
            packingType = CostingPackingType.SINGLE,
            defaultPackQty = 1,
            isMultiTimeUse = s6?.let { it.usageType == BottleUsageType.MULTIPLE_TIME || it.isReturnableBottle } ?: false,
            defaultSalePrice = s6?.defaultRefillPrice ?: 140.0
        ),
        BottleCostingOption(
            key = "${s15?.id ?: "size_1_5l"}__CARTON",
            label = "1.5 Liter Carton",
            bottleSizeId = s15?.id ?: "size_1_5l",
            bottleSizeName = "1.5 Liter Carton",
            packingType = CostingPackingType.CARTON,
            defaultPackQty = 12,
            isMultiTimeUse = false,
            defaultSalePrice = s15?.defaultRefillPrice ?: 600.0
        ),
        BottleCostingOption(
            key = "${s15?.id ?: "size_1_5l"}__PACKET",
            label = "1.5 Liter Packet",
            bottleSizeId = s15?.id ?: "size_1_5l",
            bottleSizeName = "1.5 Liter Packet",
            packingType = CostingPackingType.PACKET,
            defaultPackQty = 6,
            isMultiTimeUse = false,
            defaultSalePrice = s15?.defaultRefillPrice ?: 300.0
        ),
        BottleCostingOption(
            key = "${s500?.id ?: "size_500ml"}__CARTON",
            label = "500ml Carton",
            bottleSizeId = s500?.id ?: "size_500ml",
            bottleSizeName = "500ml Carton",
            packingType = CostingPackingType.CARTON,
            defaultPackQty = 24,
            isMultiTimeUse = false,
            defaultSalePrice = s500?.defaultRefillPrice ?: 320.0
        ),
        BottleCostingOption(
            key = "${s500?.id ?: "size_500ml"}__PACKET",
            label = "500ml Packet",
            bottleSizeId = s500?.id ?: "size_500ml",
            bottleSizeName = "500ml Packet",
            packingType = CostingPackingType.PACKET,
            defaultPackQty = 12,
            isMultiTimeUse = false,
            defaultSalePrice = s500?.defaultRefillPrice ?: 180.0
        )
    )

    val coveredIds = setOfNotNull(s19?.id, s12?.id, s6?.id, s15?.id, s500?.id, "size_19l", "size_12l", "size_6l", "size_1_5l", "size_500ml")
    active.filter { it.id !in coveredIds }.forEach { extra ->
        val isMulti = extra.usageType == BottleUsageType.MULTIPLE_TIME || extra.isReturnableBottle
        val packType = if (extra.packQuantity > 1) CostingPackingType.PACKET else CostingPackingType.SINGLE
        options.add(
            BottleCostingOption(
                key = "${extra.id}__${packType.name}",
                label = extra.name,
                bottleSizeId = extra.id,
                bottleSizeName = extra.name,
                packingType = packType,
                defaultPackQty = extra.packQuantity.coerceAtLeast(1),
                isMultiTimeUse = isMulti,
                defaultSalePrice = extra.defaultRefillPrice
            )
        )
    }
    return options
}

private fun cleanNumText(value: Double): String {
    if (value == 0.0) return ""
    return if (kotlin.math.abs(value - value.toLong().toDouble()) < 0.001) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

private fun filterDecimal(input: String): String {
    var dotSeen = false
    return buildString {
        input.forEach { ch ->
            when {
                ch.isDigit() -> append(ch)
                ch == '.' && !dotSeen -> {
                    dotSeen = true
                    append(ch)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostingSubTab(
    viewModel: WaterPlantViewModel,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Strict Admin Access Gate
    if (!isAdmin) {
        AccessDeniedCard()
        return
    }

    val bottleSizes by viewModel.bottleSizes.collectAsState()
    val bottleCostings by viewModel.bottleCostings.collectAsState()
    val monthlyOperationalCosts by viewModel.monthlyOperationalCosts.collectAsState()
    val monthlyRecurringExpenses by viewModel.monthlyRecurringExpenses.collectAsState()
    val plantWorkers by viewModel.plantWorkers.collectAsState()
    val deliveries by viewModel.deliveries.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val payments by viewModel.payments.collectAsState()

    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedPnlPeriod by remember { mutableStateOf(CostingReportPeriod.DAILY) }
    var pnlReferenceDate by remember { mutableStateOf(todayDate) }
    var selectedMonth by remember { mutableStateOf(todayDate.take(7)) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }
    var costingToDelete by remember { mutableStateOf<BottleCostingRecord?>(null) }

    val pnlReport = remember(
        selectedPnlPeriod,
        pnlReferenceDate,
        deliveries,
        payments,
        expenses,
        bottleSizes,
        bottleCostings,
        monthlyOperationalCosts,
        monthlyRecurringExpenses,
        plantWorkers
    ) {
        viewModel.getCostingProfitLossReport(selectedPnlPeriod, pnlReferenceDate)
    }

    // Section 2: Bottle Costing Dropdown + 10-Field Form State
    val costingOptions = remember(bottleSizes) { buildBottleCostingOptions(bottleSizes) }
    var selectedOptionKey by remember(costingOptions) {
        mutableStateOf(costingOptions.firstOrNull()?.key ?: "")
    }
    val selectedOption = remember(costingOptions, selectedOptionKey) {
        costingOptions.find { it.key == selectedOptionKey } ?: costingOptions.firstOrNull()
    }
    val existingRecordForOption = remember(bottleCostings, selectedOption) {
        selectedOption?.let { opt ->
            bottleCostings.find {
                it.bottleSizeId.equals(opt.bottleSizeId, ignoreCase = true) && it.packingType == opt.packingType
            }
        }
    }

    var packQty by remember { mutableIntStateOf(1) }
    var bottlePriceText by remember { mutableStateOf("") }
    var labelPriceText by remember { mutableStateOf("") }
    var sealPriceText by remember { mutableStateOf("") }
    var waterFillingText by remember { mutableStateOf("") }
    var cartonCostText by remember { mutableStateOf("") }
    var tapeCostText by remember { mutableStateOf("") }
    var deliveryCostText by remember { mutableStateOf("") }
    var laborCostText by remember { mutableStateOf("") }
    var miscCostText by remember { mutableStateOf("") }
    var capCostText by remember { mutableStateOf("") }
    var confirmedCostText by remember { mutableStateOf("") }
    var confirmedManuallyEdited by remember { mutableStateOf(false) }

    LaunchedEffect(selectedOption?.key, existingRecordForOption?.timestamp) {
        val opt = selectedOption ?: return@LaunchedEffect
        val rec = existingRecordForOption
        if (rec != null) {
            packQty = rec.packQty.coerceAtLeast(1)
            bottlePriceText = cleanNumText(if (rec.usesDetailedCosting) rec.bottlePrice else rec.bottlePurchaseRate)
            labelPriceText = cleanNumText(rec.labelPrice)
            sealPriceText = cleanNumText(rec.sealPrice)
            waterFillingText = cleanNumText(if (rec.usesDetailedCosting) rec.waterFillingCost else rec.fillingRate)
            cartonCostText = cleanNumText(if (rec.usesDetailedCosting) rec.cartonCost else rec.cartonPolytheneCost)
            tapeCostText = cleanNumText(rec.tapeCost)
            deliveryCostText = cleanNumText(rec.deliveryCost)
            laborCostText = cleanNumText(rec.labourCost)
            miscCostText = cleanNumText(rec.miscCost)
            capCostText = cleanNumText(rec.capCost)
            val savedConfirmed = rec.confirmedPacketCost.takeIf { it > 0.0 } ?: rec.effectiveUnitCostForPnl()
            confirmedCostText = cleanNumText(savedConfirmed)
            confirmedManuallyEdited = rec.confirmedPacketCost > 0.0 &&
                kotlin.math.abs(rec.confirmedPacketCost - rec.calculatedPackCost) > 0.01
        } else {
            packQty = opt.defaultPackQty
            bottlePriceText = ""
            labelPriceText = ""
            sealPriceText = ""
            waterFillingText = ""
            cartonCostText = ""
            tapeCostText = ""
            deliveryCostText = ""
            laborCostText = ""
            miscCostText = ""
            capCostText = ""
            confirmedCostText = ""
            confirmedManuallyEdited = false
        }
    }

    val bottlePriceVal = bottlePriceText.toDoubleOrNull() ?: 0.0
    val labelPriceVal = labelPriceText.toDoubleOrNull() ?: 0.0
    val sealPriceVal = sealPriceText.toDoubleOrNull() ?: 0.0
    val waterFillingVal = waterFillingText.toDoubleOrNull() ?: 0.0
    val capCostVal = capCostText.toDoubleOrNull() ?: 0.0
    val cartonCostVal = cartonCostText.toDoubleOrNull() ?: 0.0
    val tapeCostVal = tapeCostText.toDoubleOrNull() ?: 0.0
    val deliveryCostVal = deliveryCostText.toDoubleOrNull() ?: 0.0
    val laborCostVal = laborCostText.toDoubleOrNull() ?: 0.0
    val miscCostVal = miscCostText.toDoubleOrNull() ?: 0.0

    val perBottleSum = bottlePriceVal + labelPriceVal + sealPriceVal + waterFillingVal + capCostVal
    val perPackSum = cartonCostVal + tapeCostVal + deliveryCostVal + laborCostVal + miscCostVal
    val autoCalculatedTotal = (perBottleSum * packQty.coerceAtLeast(1)) + perPackSum

    LaunchedEffect(autoCalculatedTotal) {
        if (!confirmedManuallyEdited) {
            confirmedCostText = cleanNumText(autoCalculatedTotal)
        }
    }

    // Section 3: Monthly Operational Costs (6 Budget/Actual Fields) + Auto Staff Salaries
    val activeWorkers = remember(plantWorkers) { plantWorkers.filter { it.active } }
    val monthlyStaffSalaries = remember(activeWorkers) { activeWorkers.sumOf { it.monthlySalary.coerceAtLeast(0.0) } }
    val daysInSelectedMonth = remember(selectedMonth) {
        val parts = selectedMonth.split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
        Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, (m - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(28)
    }
    val dailyStaffRate = if (daysInSelectedMonth > 0) monthlyStaffSalaries / daysInSelectedMonth else 0.0

    val loadedMonthRecord = remember(selectedMonth, monthlyOperationalCosts) {
        viewModel.getMonthlyOperationalCostForMonth(selectedMonth)
    }

    var vehMaintBudget by remember { mutableStateOf("") }
    var vehMaintActual by remember { mutableStateOf("") }
    var roMaintBudget by remember { mutableStateOf("") }
    var roMaintActual by remember { mutableStateOf("") }
    var roRentBudget by remember { mutableStateOf("") }
    var roRentActual by remember { mutableStateOf("") }
    var vehRentBudget by remember { mutableStateOf("") }
    var vehRentActual by remember { mutableStateOf("") }
    var elecBudget by remember { mutableStateOf("") }
    var elecActual by remember { mutableStateOf("") }
    var otherBudget by remember { mutableStateOf("") }
    var otherActual by remember { mutableStateOf("") }

    LaunchedEffect(selectedMonth, loadedMonthRecord.timestamp) {
        vehMaintBudget = cleanNumText(loadedMonthRecord.vehicleMaintenance.budgetAmount)
        vehMaintActual = cleanNumText(loadedMonthRecord.vehicleMaintenance.actualAmount)
        roMaintBudget = cleanNumText(loadedMonthRecord.roMaintenance.budgetAmount)
        roMaintActual = cleanNumText(loadedMonthRecord.roMaintenance.actualAmount)
        roRentBudget = cleanNumText(loadedMonthRecord.roPlantRent.budgetAmount)
        roRentActual = cleanNumText(loadedMonthRecord.roPlantRent.actualAmount)
        vehRentBudget = cleanNumText(loadedMonthRecord.vehicleMonthlyRent.budgetAmount)
        vehRentActual = cleanNumText(loadedMonthRecord.vehicleMonthlyRent.actualAmount)
        elecBudget = cleanNumText(loadedMonthRecord.electricityBill.budgetAmount)
        elecActual = cleanNumText(loadedMonthRecord.electricityBill.actualAmount)
        otherBudget = cleanNumText(loadedMonthRecord.otherFixedCosts.budgetAmount)
        otherActual = cleanNumText(loadedMonthRecord.otherFixedCosts.actualAmount)
    }

    val draftOpRecord = remember(
        selectedMonth,
        vehMaintBudget, vehMaintActual,
        roMaintBudget, roMaintActual,
        roRentBudget, roRentActual,
        vehRentBudget, vehRentActual,
        elecBudget, elecActual,
        otherBudget, otherActual
    ) {
        MonthlyOperationalCostRecord(
            id = "opcost_$selectedMonth",
            month = selectedMonth,
            vehicleMaintenance = OperationalCostLine(vehMaintBudget.toDoubleOrNull() ?: 0.0, vehMaintActual.toDoubleOrNull() ?: 0.0),
            roMaintenance = OperationalCostLine(roMaintBudget.toDoubleOrNull() ?: 0.0, roMaintActual.toDoubleOrNull() ?: 0.0),
            roPlantRent = OperationalCostLine(roRentBudget.toDoubleOrNull() ?: 0.0, roRentActual.toDoubleOrNull() ?: 0.0),
            vehicleMonthlyRent = OperationalCostLine(vehRentBudget.toDoubleOrNull() ?: 0.0, vehRentActual.toDoubleOrNull() ?: 0.0),
            electricityBill = OperationalCostLine(elecBudget.toDoubleOrNull() ?: 0.0, elecActual.toDoubleOrNull() ?: 0.0),
            otherFixedCosts = OperationalCostLine(otherBudget.toDoubleOrNull() ?: 0.0, otherActual.toDoubleOrNull() ?: 0.0)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("costing_sub_tab"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Costing & Profit Matrix",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ErrorRed
                            ) {
                                Text(
                                    text = "ADMIN ONLY",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "مکمل بوتل لاگت، ماہانہ آپریشنل اخراجات اور خالص منافع",
                            color = WaterSkyLight.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // =========================================================
        // SECTION 1: UNIFIED PROFIT & LOSS DASHBOARD (DAY/WEEK/MONTH/YEAR)
        // =========================================================
        item {
            CostingProfitLossDashboard(
                selectedPeriod = selectedPnlPeriod,
                onPeriodSelected = { selectedPnlPeriod = it },
                referenceDate = pnlReferenceDate,
                onReferenceDateChanged = { newDate ->
                    pnlReferenceDate = newDate
                    if (newDate.length >= 7) {
                        selectedMonth = newDate.take(7)
                    }
                },
                report = pnlReport
            )
        }

        // =========================================================
        // SECTION 2: BOTTLE COSTING (DROPDOWN + 10 FIELDS + CONFIRM COST)
        // =========================================================
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bottle_costing_section_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Bottle Costing (بوتل و پیکٹ لاگت)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = WaterNavyPrimary
                        )
                        Text(
                            text = "Select bottle size & packaging type to configure 10-field cost and confirm lump-sum packet cost",
                            fontSize = 9.5.sp,
                            color = TextSecondaryDark
                        )
                    }

                    // Bottle Size & Packaging Type Dropdown
                    CompactDropdownField(
                        label = "Bottle Size & Packaging Type*",
                        selectedText = selectedOption?.label ?: "",
                        options = costingOptions,
                        optionLabel = { opt ->
                            val saved = bottleCostings.find {
                                it.bottleSizeId.equals(opt.bottleSizeId, ignoreCase = true) && it.packingType == opt.packingType
                            }
                            if (saved != null) "${opt.label}  •  Rs. ${formatPnlAmount(saved.effectiveUnitCostForPnl())}"
                            else opt.label
                        },
                        onOptionSelected = { opt -> selectedOptionKey = opt.key },
                        modifier = Modifier.testTag("bottle_costing_dropdown")
                    )

                    // Bottles Per Pack / Carton Chips (1, 6, 12, 24)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bottles per Pack/Carton:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyDark
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 6, 12, 24).forEach { count ->
                                val isSel = packQty == count
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) WaterNavyPrimary else Color(0xFFF1F5F9),
                                    border = if (isSel) null else BorderStroke(1.dp, BorderLight),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { packQty = count }
                                        .testTag("pack_qty_chip_$count")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.White else WaterNavyPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Group A: Per-Bottle Costs (× packQty)
                    Text(
                        text = "Per-Bottle Material & Filling Costs (× $packQty)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlueDeep
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CostingFieldInput(
                            label = "1. Bottle Price",
                            value = bottlePriceText,
                            onValueChange = { bottlePriceText = filterDecimal(it) },
                            tag = "cost_bottle_price",
                            modifier = Modifier.weight(1f)
                        )
                        CostingFieldInput(
                            label = "2. Label Price",
                            value = labelPriceText,
                            onValueChange = { labelPriceText = filterDecimal(it) },
                            tag = "cost_label_price",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CostingFieldInput(
                            label = "3. Seal Price",
                            value = sealPriceText,
                            onValueChange = { sealPriceText = filterDecimal(it) },
                            tag = "cost_seal_price",
                            modifier = Modifier.weight(1f)
                        )
                        CostingFieldInput(
                            label = "4. Water Filling",
                            value = waterFillingText,
                            onValueChange = { waterFillingText = filterDecimal(it) },
                            tag = "cost_water_filling",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CostingFieldInput(
                            label = "10. Cap",
                            value = capCostText,
                            onValueChange = { capCostText = filterDecimal(it) },
                            tag = "cost_cap_price",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Group B: Per-Pack / Carton Costs
                    Text(
                        text = "Per-Pack / Carton Costs",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlueDeep
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CostingFieldInput(
                            label = "5. Carton",
                            value = cartonCostText,
                            onValueChange = { cartonCostText = filterDecimal(it) },
                            tag = "cost_carton",
                            modifier = Modifier.weight(1f)
                        )
                        CostingFieldInput(
                            label = "6. Tape",
                            value = tapeCostText,
                            onValueChange = { tapeCostText = filterDecimal(it) },
                            tag = "cost_tape",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CostingFieldInput(
                            label = "7. Delivery Cost",
                            value = deliveryCostText,
                            onValueChange = { deliveryCostText = filterDecimal(it) },
                            tag = "cost_delivery",
                            modifier = Modifier.weight(1f)
                        )
                        CostingFieldInput(
                            label = "8. Labor Cost",
                            value = laborCostText,
                            onValueChange = { laborCostText = filterDecimal(it) },
                            tag = "cost_labor",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CostingFieldInput(
                            label = "9. Miscellaneous Cost",
                            value = miscCostText,
                            onValueChange = { miscCostText = filterDecimal(it) },
                            tag = "cost_misc",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Automatic Calculated Total Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WaterSkyLight,
                        border = BorderStroke(1.dp, WaterAqua.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Auto Calculated ${selectedOption?.packingType?.shortLabel ?: "Pack"} Cost",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WaterNavyDark
                                )
                                Text(
                                    text = "Rs. ${formatPnlAmount(autoCalculatedTotal)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WaterNavyPrimary
                                )
                            }
                            Text(
                                text = "(Per-Bottle Rs. ${formatPnlAmount(perBottleSum)} × $packQty) + Pack Costs Rs. ${formatPnlAmount(perPackSum)}",
                                fontSize = 9.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }

                    // Confirm Packet Cost Input (Manual Lump-Sum Override)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactFormTextField(
                            value = confirmedCostText,
                            onValueChange = {
                                confirmedCostText = filterDecimal(it)
                                confirmedManuallyEdited = true
                            },
                            label = "Confirm Packet Cost (حتمی لاگت Rs.)*",
                            placeholder = formatPnlAmount(autoCalculatedTotal),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            height = 42.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_packet_cost_input")
                        )
                        if (confirmedManuallyEdited) {
                            OutlinedButton(
                                onClick = {
                                    confirmedManuallyEdited = false
                                    confirmedCostText = cleanNumText(autoCalculatedTotal)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text("Use Auto", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val opt = selectedOption ?: return@Button
                            val finalConfirmed = (confirmedCostText.toDoubleOrNull() ?: autoCalculatedTotal).coerceAtLeast(0.0)
                            val record = BottleCostingRecord(
                                id = existingRecordForOption?.id ?: "",
                                bottleSizeId = opt.bottleSizeId,
                                bottleSizeName = opt.bottleSizeName,
                                packingType = opt.packingType,
                                isMultiTimeUse = opt.isMultiTimeUse,
                                packQty = packQty.coerceAtLeast(1),
                                bottlePrice = bottlePriceVal,
                                labelPrice = labelPriceVal,
                                sealPrice = sealPriceVal,
                                waterFillingCost = waterFillingVal,
                                cartonCost = cartonCostVal,
                                tapeCost = tapeCostVal,
                                deliveryCost = deliveryCostVal,
                                labourCost = laborCostVal,
                                miscCost = miscCostVal,
                                capCost = capCostVal,
                                calculatedPackCost = autoCalculatedTotal,
                                confirmedPacketCost = finalConfirmed,
                                usesDetailedCosting = true,
                                totalCostPerPackOrBottle = finalConfirmed,
                                salePrice = existingRecordForOption?.salePrice?.takeIf { it > 0.0 } ?: opt.defaultSalePrice,
                                date = todayDate
                            )
                            viewModel.saveBottleCosting(record) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = waterPrimaryButtonColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("save_bottle_costing_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save Costing for ${selectedOption?.label ?: "Selected Bottle"}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Compact List of Saved Bottle Costing Entries
                    if (bottleCostings.isNotEmpty()) {
                        HorizontalDivider(color = BorderLight)
                        Text(
                            text = "Saved Bottle Costings (${bottleCostings.size})",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyPrimary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            bottleCostings.forEach { rec ->
                                val effectiveCost = rec.effectiveUnitCostForPnl()
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, BorderLight),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val matchingOpt = costingOptions.find {
                                                it.bottleSizeId.equals(rec.bottleSizeId, ignoreCase = true) &&
                                                    it.packingType == rec.packingType
                                            }
                                            if (matchingOpt != null) {
                                                selectedOptionKey = matchingOpt.key
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${rec.bottleSizeName} (${rec.packingType.shortLabel} • ${rec.packQty} btl)",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WaterNavyDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (rec.usesDetailedCosting && rec.calculatedPackCost > 0.0) {
                                                    "Auto: Rs. ${formatPnlAmount(rec.calculatedPackCost)}  •  Confirmed: Rs. ${formatPnlAmount(effectiveCost)}"
                                                } else {
                                                    "Confirmed Cost: Rs. ${formatPnlAmount(effectiveCost)}"
                                                },
                                                fontSize = 9.sp,
                                                color = TextSecondaryDark
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Rs. ${formatPnlAmount(effectiveCost)}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = WaterNavyPrimary
                                            )
                                            IconButton(
                                                onClick = { costingToDelete = rec },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Bottle Costing",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(16.dp)
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

        // =========================================================
        // SECTION 3: MONTHLY OPERATIONAL COSTS & AUTO STAFF LABOR COST
        // =========================================================
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_operational_costs_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header + Month Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Monthly Operational Costs",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = WaterNavyPrimary
                            )
                            Text(
                                text = "ماہانہ اخراجات (اصل رقم درج ہونے پر بجٹ کی جگہ اصل رقم استعمال ہوگی)",
                                fontSize = 9.5.sp,
                                color = TextSecondaryDark
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WaterNavyPrimary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, WaterNavyPrimary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showMonthPickerDialog = true }
                                .testTag("monthly_op_month_picker_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Select Month", tint = WaterNavyPrimary, modifier = Modifier.size(16.dp))
                                Text(selectedMonth, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                            }
                        }
                    }

                    // Auto Staff Labor Cost Card (Read-Only — Fetched Automatically from Staff Module)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, WaterNavyPrimary.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_staff_labor_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = WaterNavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Staff Labor Cost (Auto from Staff Module)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WaterNavyPrimary
                                    )
                                    Text(
                                        text = "${activeWorkers.size} active staff • Rs. ${formatPnlAmount(dailyStaffRate)}/day (÷ ${daysInSelectedMonth}d)",
                                        fontSize = 9.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                            }
                            Text(
                                text = "Rs. ${formatPnlAmount(monthlyStaffSalaries)}/mo",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WaterNavyPrimary
                            )
                        }
                    }

                    // Column Header for Budget vs Actual
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Budget Amount (Rs.)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondaryDark,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Actual Expense (Overrides Budget)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 6 Operational Cost Fields (Budget vs Actual)
                    OperationalCostRowInput(
                        title = "1. Vehicle Maintenance (گاڑی مرمت)",
                        budgetText = vehMaintBudget,
                        onBudgetChange = { vehMaintBudget = filterDecimal(it) },
                        actualText = vehMaintActual,
                        onActualChange = { vehMaintActual = filterDecimal(it) },
                        isUsingActual = draftOpRecord.vehicleMaintenance.isUsingActual,
                        tagPrefix = "op_vehicle_maint"
                    )
                    OperationalCostRowInput(
                        title = "2. RO Maintenance (آر او پلانٹ مرمت)",
                        budgetText = roMaintBudget,
                        onBudgetChange = { roMaintBudget = filterDecimal(it) },
                        actualText = roMaintActual,
                        onActualChange = { roMaintActual = filterDecimal(it) },
                        isUsingActual = draftOpRecord.roMaintenance.isUsingActual,
                        tagPrefix = "op_ro_maint"
                    )
                    OperationalCostRowInput(
                        title = "3. RO Plant Rent (آر او پلانٹ کرایہ)",
                        budgetText = roRentBudget,
                        onBudgetChange = { roRentBudget = filterDecimal(it) },
                        actualText = roRentActual,
                        onActualChange = { roRentActual = filterDecimal(it) },
                        isUsingActual = draftOpRecord.roPlantRent.isUsingActual,
                        tagPrefix = "op_ro_rent"
                    )
                    OperationalCostRowInput(
                        title = "4. Vehicle Monthly Rent (گاڑی ماہانہ کرایہ)",
                        budgetText = vehRentBudget,
                        onBudgetChange = { vehRentBudget = filterDecimal(it) },
                        actualText = vehRentActual,
                        onActualChange = { vehRentActual = filterDecimal(it) },
                        isUsingActual = draftOpRecord.vehicleMonthlyRent.isUsingActual,
                        tagPrefix = "op_vehicle_rent"
                    )
                    OperationalCostRowInput(
                        title = "5. Electricity Bill (بجلی کا بل)",
                        budgetText = elecBudget,
                        onBudgetChange = { elecBudget = filterDecimal(it) },
                        actualText = elecActual,
                        onActualChange = { elecActual = filterDecimal(it) },
                        isUsingActual = draftOpRecord.electricityBill.isUsingActual,
                        tagPrefix = "op_electricity"
                    )
                    OperationalCostRowInput(
                        title = "6. Other Fixed Costs (دیگر مستقل اخراجات)",
                        budgetText = otherBudget,
                        onBudgetChange = { otherBudget = filterDecimal(it) },
                        actualText = otherActual,
                        onActualChange = { otherActual = filterDecimal(it) },
                        isUsingActual = draftOpRecord.otherFixedCosts.isUsingActual,
                        tagPrefix = "op_other_fixed"
                    )

                    // Effective Monthly Summary Banner
                    val dailyOpRate = if (daysInSelectedMonth > 0) draftOpRecord.totalEffectiveCost / daysInSelectedMonth else 0.0
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ReportRow(
                                label = "Total Operational Budget",
                                value = "Rs. ${formatPnlAmount(draftOpRecord.totalBudget)}"
                            )
                            ReportRow(
                                label = "Total Actual Entered",
                                value = "Rs. ${formatPnlAmount(draftOpRecord.totalActual)}",
                                isPositive = draftOpRecord.totalActual > 0.0
                            )
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            ReportRow(
                                label = "Active Monthly Op Cost (Actual > Budget)",
                                value = "Rs. ${formatPnlAmount(draftOpRecord.totalEffectiveCost)}",
                                isBold = true
                            )
                            ReportRow(
                                label = "Daily Share (Op Cost + Auto Staff)",
                                value = "Rs. ${formatPnlAmount(dailyOpRate + dailyStaffRate)} / day",
                                isBold = true
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveMonthlyOperationalCost(draftOpRecord) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = waterPrimaryButtonColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("save_monthly_op_costs_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save Monthly Costs ($selectedMonth)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Delete Costing Record Confirmation Dialog
    costingToDelete?.let { costing ->
        AlertDialog(
            onDismissRequest = { costingToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            title = { Text("Delete Costing Record?", fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = {
                Text(
                    "Are you sure you want to delete costing for ${costing.bottleSizeName} (${costing.packingType.shortLabel})?",
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBottleCosting(costing.id) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            costingToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { costingToDelete = null }) { Text("Cancel", color = TextSecondaryDark) }
            }
        )
    }

    if (showMonthPickerDialog) {
        MonthPickerDialogComposable(
            initialMonth = selectedMonth,
            onDismiss = { showMonthPickerDialog = false },
            onMonthSelected = { newMonth ->
                selectedMonth = newMonth
                if (pnlReferenceDate.take(7) != newMonth) {
                    pnlReferenceDate = "$newMonth-01"
                }
                showMonthPickerDialog = false
            }
        )
    }
}

@Composable
private fun CostingFieldInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    CompactFormTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = "0",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        height = 40.dp,
        modifier = modifier.testTag(tag)
    )
}

@Composable
private fun OperationalCostRowInput(
    title: String,
    budgetText: String,
    onBudgetChange: (String) -> Unit,
    actualText: String,
    onActualChange: (String) -> Unit,
    isUsingActual: Boolean,
    tagPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isUsingActual) SuccessGreenLight else Color(0xFFF1F5F9)
            ) {
                Text(
                    text = if (isUsingActual) "Actual Active" else "Budget",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUsingActual) SuccessGreen else TextSecondaryDark,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactFormTextField(
                value = budgetText,
                onValueChange = onBudgetChange,
                label = "Budget",
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                height = 38.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${tagPrefix}_budget")
            )
            CompactFormTextField(
                value = actualText,
                onValueChange = onActualChange,
                label = "Actual",
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                height = 38.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${tagPrefix}_actual")
            )
        }
    }
}

// -------------------------------------------------------------
// FINANCIAL KPI CARD & REPORT ROW
// -------------------------------------------------------------
@Composable
fun FinancialKpiCard(
    title: String,
    urdu: String,
    amount: String,
    color: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Text(amount, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 9.8.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(urdu, fontSize = 8.5.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ReportRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    isPositive: Boolean = false,
    isAlert: Boolean = false
) {
    val color = when {
        isAlert -> ErrorRed
        isPositive -> SuccessGreen
        isBold -> WaterNavyPrimary
        else -> TextPrimaryDark
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = if (isBold) 11.sp else 10.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = if (isBold) WaterNavyPrimary else TextSecondaryDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Text(
            text = value,
            fontSize = if (isBold) 11.5.sp else 10.5.sp,
            fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

// -------------------------------------------------------------
// ACCESS DENIED VIEW
// -------------------------------------------------------------
@Composable
fun AccessDeniedCard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Access Denied (صرف ایڈمن کی رسائی)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = ErrorRed)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "The Costing and Financial Profit module is strictly restricted to the Admin role. Please switch to an authorized Admin profile to access factory costing and net profit calculations.",
                    fontSize = 10.5.sp,
                    color = TextPrimaryDark,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MonthPickerDialogComposable(
    initialMonth: String,
    onDismiss: () -> Unit,
    onMonthSelected: (String) -> Unit
) {
    val parts = initialMonth.split("-")
    var selectedYear by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 2026) }
    val currentSelectedMonthNum = parts.getOrNull(1)?.toIntOrNull() ?: 1

    val months = listOf(
        1 to "Jan", 2 to "Feb", 3 to "Mar", 4 to "Apr",
        5 to "May", 6 to "Jun", 7 to "Jul", 8 to "Aug",
        9 to "Sep", 10 to "Oct", 11 to "Nov", 12 to "Dec"
    )

    MobileFormDialog(
        title = "Select Month",
        subtitle = "ماہانہ اخراجات اور رپورٹ کا مہینہ منتخب کریں",
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { selectedYear-- }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year", tint = WaterNavyPrimary)
            }
            Text(
                text = "$selectedYear",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = WaterNavyPrimary
            )
            IconButton(onClick = { selectedYear++ }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Year", tint = WaterNavyPrimary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (chunk in months.chunked(3)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunk.forEach { (mNum, mName) ->
                        val isCurrentSelected = (selectedYear == (parts.getOrNull(0)?.toIntOrNull() ?: 2026) && mNum == currentSelectedMonthNum)
                        Button(
                            onClick = {
                                val formatted = String.format(Locale.US, "%04d-%02d", selectedYear, mNum)
                                onMonthSelected(formatted)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = if (isCurrentSelected) {
                                waterPrimaryButtonColors()
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = WaterSkyLight,
                                    contentColor = WaterNavyPrimary
                                )
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = mName,
                                fontSize = 10.5.sp,
                                fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
