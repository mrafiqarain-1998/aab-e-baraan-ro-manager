package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LoginOutcome
import com.example.data.WaterPlantRepository
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.data.room.*

class WaterPlantViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WaterPlantRepository.getInstance(application)

    val currentUser = repository.currentUser
    val userAccounts = repository.userAccounts
    val passwordResetRequests = repository.passwordResetRequests
    val customers = repository.customers
    val bottleSizes = repository.bottleSizes
    val customerRates = repository.customerRates
    val securityDeposits = repository.securityDeposits
    val customerAllocations = repository.customerAllocations
    val deliveries = repository.deliveries
    val invoices = repository.invoices
    val payments = repository.payments
    val expenses = repository.expenses
    val expenseCategories = repository.expenseCategories
    val inventory = repository.inventory
    val bottleMovements = repository.bottleMovements
    val roPlantItems = repository.roPlantItems
    val roTransactions = repository.roTransactions
    val bottlePurchases = repository.bottlePurchases
    val bottleRefills = repository.bottleRefills
    val bottleIssueSales = repository.bottleIssueSales
    val factoryMaterials = repository.factoryMaterials
    val materialTransactions = repository.materialTransactions
    val bottleCostings = repository.bottleCostings
    val monthlyRecurringExpenses = repository.monthlyRecurringExpenses
    val cashClosings = repository.cashClosings
    val notifications = repository.notifications
    val syncStatus = repository.syncStatus
    val isCloudConnected = repository.isCloudConnected
    val cloudUserEmail = repository.cloudUserEmail
    val isDarkMode = repository.isDarkMode
    val auditLogs = repository.auditLogs
    val plantBranches = repository.plantBranches
    val activePlantId = repository.activePlantId
    val backendConfig = repository.backendConfig
    val isSyncFlushing = repository.isSyncFlushing
    val syncQueueItems = repository.syncQueueItems
    val syncConflicts = repository.syncConflicts
    val isDeviceOnline = repository.isDeviceOnline
    val appReleaseInfo = repository.appReleaseInfo
    val availableAppUpdate = repository.availableAppUpdate
    val isCheckingAppUpdate = repository.isCheckingAppUpdate
    val appUpdateStatusMessage = repository.appUpdateStatusMessage
    val seenRecordKeys = repository.seenRecordKeys

    fun markRecordSeen(type: String, recordId: String) {
        repository.markRecordSeen(type, recordId)
    }

    fun isRecordNewForCurrentUser(
        type: String,
        recordId: String,
        createdByUid: String,
        createdByName: String,
        createdAt: Long,
        seenKeys: Set<String> = seenRecordKeys.value,
        user: UserProfile = currentUser.value
    ): Boolean {
        return repository.isRecordNewForCurrentUser(
            type = type,
            recordId = recordId,
            createdByUid = createdByUid,
            createdByName = createdByName,
            createdAt = createdAt,
            seenKeys = seenKeys,
            user = user
        )
    }

    fun hasPermission(module: AppModule, action: PermissionAction): Boolean {
        return repository.hasPermission(module, action)
    }

    // Room Database Reactive Flows (Customer deliveries, outstanding balances, daily plant expenses, and stock items)
    val roomDeliveries: StateFlow<List<CustomerDeliveryEntity>> = repository.deliveryDao.getAllDeliveries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val roomOutstandingBalances: StateFlow<List<CustomerBalanceEntity>> = repository.customerBalanceDao.getCustomersWithOutstandingBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val roomAllBalances: StateFlow<List<CustomerBalanceEntity>> = repository.customerBalanceDao.getAllCustomerBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val roomDailyExpenses: StateFlow<List<DailyPlantExpenseEntity>> = repository.expenseDao.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val roomStock: StateFlow<List<StockItemEntity>> = repository.stockDao.getAllStock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Backup & Restore states
    val backupHistory = repository.backupHistory
    val isAutoBackupEnabled = repository.isAutoBackupEnabled
    val lastBackupTime = repository.lastBackupTime
    val isBackupInProgress = repository.isBackupInProgress

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer = _selectedCustomer.asStateFlow()

    private val _selectedDeliveryForDetail = MutableStateFlow<Delivery?>(null)
    val selectedDeliveryForDetail = _selectedDeliveryForDetail.asStateFlow()

    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage = _userFeedbackMessage.asStateFlow()

    fun clearFeedback() {
        _userFeedbackMessage.value = null
    }

    fun selectCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
    }

    fun selectDeliveryForDetail(delivery: Delivery?) {
        _selectedDeliveryForDetail.value = delivery
    }

    // Authentication & User Management (Admin creates & updates ID & Password)
    suspend fun loginWithOutcome(username: String, pass: String): LoginOutcome {
        val (outcome, profile) = repository.authenticateWithOutcome(username, pass)
        when (outcome) {
            LoginOutcome.SUCCESS -> _userFeedbackMessage.value = "Welcome, ${profile?.name ?: username}!"
            LoginOutcome.INACTIVE_ACCOUNT -> _userFeedbackMessage.value = "Account is deactivated by Admin."
            LoginOutcome.CLOUD_SYNC_REQUIRED -> _userFeedbackMessage.value = "Connect internet or Admin Google/Cloud Sync to verify this staff account."
            LoginOutcome.INVALID_CREDENTIALS -> _userFeedbackMessage.value = "Invalid username or password."
        }
        return outcome
    }

    suspend fun login(username: String, pass: String): Boolean {
        return loginWithOutcome(username, pass) == LoginOutcome.SUCCESS
    }

    fun loginWithBiometrics(targetUsername: String? = null): Boolean {
        val success = repository.loginWithBiometrics(targetUsername)
        if (success) {
            _userFeedbackMessage.value = "Fingerprint verified / فنگر پرنٹ کی تصدیق کامیاب"
        }
        return success
    }

    fun getLastLoggedInUser(): String? {
        return repository.getLastLoggedInUser()
    }

    fun isUserLoggedIn(): Boolean {
        return repository.isUserLoggedIn()
    }

    fun logout() {
        repository.logout()
        _userFeedbackMessage.value = "Logged out successfully"
    }

    fun handleGoogleAuthSuccess(uid: String, email: String, displayName: String): Boolean {
        val success = repository.handleGoogleAuthSuccess(uid, email, displayName)
        if (success) {
            _userFeedbackMessage.value = "Signed in as ${displayName.ifEmpty { email }} (Cloud Connected)"
        } else {
            _userFeedbackMessage.value = "Google account '$email' is not authorized. Ask Admin to add staff or use Admin Google account."
        }
        return success
    }

    suspend fun handleGoogleAuthSuccessAsync(uid: String, email: String, displayName: String): Boolean {
        val success = repository.handleGoogleAuthSuccessAsync(uid, email, displayName)
        if (success) {
            _userFeedbackMessage.value = "Signed in as ${displayName.ifEmpty { email }} (Cloud Connected)"
        } else {
            _userFeedbackMessage.value = "Google account '$email' is not authorized. Ask Admin to add your Gmail in Staff Accounts or use Admin Google account."
        }
        return success
    }

    fun connectGoogleCloudSession(uid: String, email: String, displayName: String): Boolean {
        val success = repository.connectGoogleCloudSession(uid, email, displayName)
        if (success) {
            _userFeedbackMessage.value = "Cloud Sync activated for $email (کلاؤڈ سنک فعال ہو گیا)"
        } else {
            _userFeedbackMessage.value = "Google account '$email' is not authorized for plant Cloud Sync."
        }
        return success
    }

    fun createUserAccount(account: UserAccount) {
        repository.createUserAccount(account)
        _userFeedbackMessage.value = "Staff account created: ${account.username}"
    }

    fun updateUserAccount(account: UserAccount) {
        repository.updateUserAccount(account)
        _userFeedbackMessage.value = "Updated credentials for ${account.name}"
    }

    fun resetUserPassword(userId: String, newPassword: String): Boolean {
        val success = repository.resetUserPassword(userId, newPassword)
        if (success) {
            _userFeedbackMessage.value = "Password updated successfully"
        }
        return success
    }

    fun requestPasswordReset(username: String): String {
        val reqId = repository.requestPasswordReset(username)
        _userFeedbackMessage.value = "Password reset request submitted for '$username'."
        return reqId
    }

    fun resolvePasswordReset(requestId: String, newPassword: String? = null): Boolean {
        val success = repository.resolvePasswordReset(requestId, newPassword)
        if (success) {
            _userFeedbackMessage.value = "Password reset request resolved."
        }
        return success
    }

    fun deleteUserAccount(id: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val (success, msg) = repository.deleteUserAccount(id)
        _userFeedbackMessage.value = msg
        onResult?.invoke(success, msg)
    }

    fun toggleUserAccountActive(userId: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val (success, msg) = repository.toggleUserAccountActive(userId)
        _userFeedbackMessage.value = msg
        onResult?.invoke(success, msg)
    }

    fun changeOwnPassword(
        currentPassword: String,
        newPassword: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, msg) = repository.changeOwnPassword(currentPassword, newPassword)
        _userFeedbackMessage.value = msg
        onResult?.invoke(success, msg)
    }

    fun switchUserWithCredentials(
        username: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val (success, msg) = repository.switchUserByCredentialsAsync(username, password)
            if (success) {
                _userFeedbackMessage.value = msg
            }
            onResult(success, msg)
        }
    }

    fun switchRole(role: UserRole) {
        repository.switchUserRole(role)
        _userFeedbackMessage.value = "Switched to ${role.displayName} view"
    }

    fun verifyAdminPassword(password: String): Boolean {
        return repository.verifyAdminPassword(password)
    }

    fun verifyRolePassword(role: UserRole, password: String): Boolean {
        return repository.verifyRolePassword(role, password)
    }

    fun refreshDataFromCloud() {
        val signedIn = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        } catch (_: Exception) {
            false
        }
        if (!signedIn) {
            _userFeedbackMessage.value = "Sign in with Staff ID or Google first to sync with Cloud."
            return
        }
        repository.flushOfflineSyncQueueNow()
        repository.refreshCloudData()
        _userFeedbackMessage.value = "Cloud sync triggered (کلاؤڈ ڈیٹا سنک ہو رہا ہے)"
    }

    fun retrySyncQueueItem(itemId: String) {
        repository.retrySyncQueueItem(itemId)
        _userFeedbackMessage.value = "Retrying queued sync item..."
    }

    fun retrySyncActionGroup(groupId: String) {
        repository.retrySyncActionGroup(groupId)
        _userFeedbackMessage.value = "Retrying pending sync action..."
    }

    fun retryAllFailedSyncItems() {
        repository.retryAllFailedSyncItems()
        _userFeedbackMessage.value = "Retrying all queued & failed actions..."
    }

    fun flushOfflineSyncQueueNow() {
        repository.flushOfflineSyncQueueNow()
        _userFeedbackMessage.value = "Syncing offline queue..."
    }

    fun clearCompletedSyncItems() {
        repository.clearCompletedSyncItems()
    }

    fun resolveSyncConflict(conflictId: String, resolutionNote: String = "Reviewed & acknowledged") {
        repository.resolveSyncConflict(conflictId, resolutionNote)
        _userFeedbackMessage.value = "Sync conflict marked as resolved."
    }

    fun switchActivePlantBranch(plantId: String): Boolean {
        val (ok, msg) = repository.switchActivePlantBranch(plantId)
        _userFeedbackMessage.value = msg
        return ok
    }

    fun addOrUpdatePlantBranch(branch: PlantBranch): Boolean {
        val (ok, msg) = repository.addOrUpdatePlantBranch(branch)
        _userFeedbackMessage.value = msg
        return ok
    }

    fun checkForAppUpdate(force: Boolean = true) {
        repository.checkForAppUpdate(force = force)
    }

    fun publishAppRelease(
        versionCode: Int,
        versionName: String,
        downloadUrl: String,
        githubRepo: String,
        releaseNotes: String,
        mandatory: Boolean = false,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val (ok, msg) = repository.publishAppRelease(
                versionCode = versionCode,
                versionName = versionName,
                downloadUrl = downloadUrl,
                githubRepo = githubRepo,
                releaseNotes = releaseNotes,
                mandatory = mandatory
            )
            _userFeedbackMessage.value = msg
            onResult?.invoke(ok, msg)
        }
    }

    fun updateBackendConfig(staffAuthUrl: String) {
        repository.updateBackendConfig(staffAuthUrl)
        _userFeedbackMessage.value = "Backend Server URL saved."
    }

    fun testBackendConnection(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val (ok, msg) = repository.testBackendConnection()
            _userFeedbackMessage.value = msg
            onResult(ok, msg)
        }
    }

    // Bottle Sizes & Rates
    fun addBottleSize(size: BottleSize) {
        repository.addBottleSize(size)
        _userFeedbackMessage.value = "Added bottle size '${size.name}' (Rate: Rs. ${size.defaultRefillPrice})"
    }

    fun updateBottleSize(size: BottleSize) {
        repository.updateBottleSize(size)
        _userFeedbackMessage.value = "Updated rate for '${size.name}' to Rs. ${size.defaultRefillPrice}"
    }

    fun getCustomerPrice(customerId: String, bottleSizeId: String): Double {
        return repository.getCustomerPrice(customerId, bottleSizeId)
    }

    fun toggleDarkMode() {
        repository.toggleDarkMode()
    }

    fun setDarkMode(enabled: Boolean) {
        repository.setDarkMode(enabled)
    }

    fun getAssignedCustomerRates(customerId: String): List<com.example.model.CustomerRate> {
        return repository.getAssignedCustomerRates(customerId)
    }

    fun getCustomerRates(customerId: String): List<com.example.model.CustomerRate> {
        return repository.getCustomerRates(customerId)
    }

    fun assignCustomerBottleRate(
        customerId: String,
        bottleSizeId: String,
        bottleSizeName: String,
        rate: Double,
        appliedDate: String
    ) {
        repository.setCustomerPrice(customerId, bottleSizeId, rate, bottleSizeName, appliedDate)
        _userFeedbackMessage.value = "Updated $bottleSizeName at Rs. $rate"
    }

    fun removeCustomerBottleSize(customerId: String, bottleSizeId: String) {
        repository.removeCustomerBottleSize(customerId, bottleSizeId)
        _userFeedbackMessage.value = "Bottle size unassigned from customer"
    }

    fun reactivateCustomerBottleSize(customerId: String, bottleSizeId: String) {
        repository.reactivateCustomerBottleSize(customerId, bottleSizeId)
        _userFeedbackMessage.value = "Bottle size reactivated for customer"
    }

    fun getCustomerRateForDate(customerId: String, bottleSizeId: String, deliveryDate: String): Double {
        return repository.getCustomerRateForDate(customerId, bottleSizeId, deliveryDate)
    }

    fun getCustomerSecurityDeposits(customerId: String): List<com.example.model.SecurityDepositTransaction> {
        return repository.getCustomerSecurityDeposits(customerId)
    }

    fun getCustomerSecurityDepositBalance(customerId: String): Double {
        return repository.getCustomerSecurityDepositBalance(customerId)
    }

    fun recordSecurityDepositTransaction(tx: com.example.model.SecurityDepositTransaction) {
        repository.recordSecurityDepositTransaction(tx)
        _userFeedbackMessage.value = "Security deposit transaction recorded"
    }

    fun setCustomerPrice(customerId: String, bottleSizeId: String, customPrice: Double) {
        repository.setCustomerPrice(customerId, bottleSizeId, customPrice)
        _userFeedbackMessage.value = "Updated custom bottle price"
    }

    // Customer Operations
    fun addCustomer(customer: Customer): String {
        val newId = repository.addCustomer(customer)
        _userFeedbackMessage.value = "Customer ${customer.name} saved!"
        return newId
    }

    fun updateCustomer(customer: Customer) {
        repository.updateCustomer(customer)
        _userFeedbackMessage.value = "Customer details updated"
    }

    fun deleteCustomer(customerId: String): Boolean {
        val success = repository.deleteCustomer(customerId)
        if (success) {
            _userFeedbackMessage.value = "Customer deleted successfully"
        } else {
            _userFeedbackMessage.value = "Cannot delete customer with pending balance or bottles held."
        }
        return success
    }

    fun isValidStrongPassword(password: String): Boolean = repository.isValidStrongPassword(password)

    fun changeUserPassword(userId: String, newPass: String): Boolean = repository.changeUserPassword(userId, newPass)

    fun toggleCustomerActive(customerId: String) {
        repository.toggleCustomerActive(customerId)
        _userFeedbackMessage.value = "Customer active status updated"
    }

    // Customer Bottle Allocation & Damage Tracking
    fun getCustomerBottleAllocations(customerId: String): List<CustomerBottleAllocation> {
        return repository.getCustomerBottleAllocations(customerId)
    }

    fun recordCustomerDamage(customerId: String, bottleSizeId: String, count: Int, reason: String) {
        repository.recordCustomerDamage(customerId, bottleSizeId, count, reason)
        _userFeedbackMessage.value = "Recorded $count broken/damaged bottles for this customer"
    }

    fun issueBottlesToCustomer(customerId: String, bottleSizeId: String, count: Int, deposit: Double) {
        repository.issueBottlesToCustomer(customerId, bottleSizeId, count, deposit)
        _userFeedbackMessage.value = "Issued $count bottles to customer"
    }

    fun returnBottlesFromCustomer(customerId: String, bottleSizeId: String, count: Int, isFull: Boolean, reason: String) {
        repository.returnBottlesFromCustomer(customerId, bottleSizeId, count, isFull, reason)
        _userFeedbackMessage.value = "Returned $count bottles from customer and added to inventory"
    }

    fun addPlantStock(bottleSizeId: String, count: Int, purchaseCost: Double, notes: String) {
        repository.addPlantStock(bottleSizeId, count, purchaseCost, notes)
        _userFeedbackMessage.value = "Added $count bottles to plant inventory"
    }

    // Deliveries
    fun recordDelivery(
        customerId: String,
        items: List<DeliveryItem>,
        assignedStaffName: String,
        notes: String,
        deliveryCharges: Double = 0.0,
        immediatePaymentAmount: Double = 0.0,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        deliveryDate: String? = null
    ) {
        val delNum = repository.recordDelivery(
            customerId, items, assignedStaffName, notes, deliveryCharges, immediatePaymentAmount, paymentMethod, deliveryDate
        )
        _userFeedbackMessage.value = "Delivery $delNum recorded! Invoice generated."
    }

    fun getUnpaidInvoicesForCustomer(customerId: String): List<Invoice> {
        return repository.getUnpaidInvoicesForCustomer(customerId)
    }

    fun getCustomerTotalDue(customer: Customer): Double {
        val invoiceDue = repository.getUnpaidInvoicesForCustomer(customer.id).sumOf { it.balanceDue }
        val unpaidDeliveriesDue = repository.deliveries.value
            .filter { it.customerId == customer.id && (it.paymentMethod == "Unpaid" || it.paymentCollected < it.totalAmount) }
            .sumOf { it.totalAmount - it.paymentCollected }
        val calculatedDue = maxOf(customer.currentBalance, invoiceDue, unpaidDeliveriesDue)
        return if (calculatedDue > 0.0) calculatedDue else customer.currentBalance
    }

    fun recordPayment(
        customerId: String,
        amount: Double,
        method: PaymentMethod,
        allocations: Map<String, Double>?,
        notes: String
    ): Payment {
        val payment = repository.recordPayment(customerId, amount, method, allocations, notes)
        _userFeedbackMessage.value = "Receipt ${payment.receiptNumber} generated! Allocated across invoices."
        return payment
    }

    fun addExpense(
        categoryId: String,
        categoryName: String,
        description: String,
        amount: Double,
        method: PaymentMethod,
        payee: String,
        notes: String,
        type: ExpenseType = ExpenseType.OPEX,
        expenseDate: String? = null,
        paymentSource: PaymentSource = PaymentSource.PAY_FROM_SALE
    ) {
        val voucher = repository.addExpense(categoryId, categoryName, description, amount, method, payee, notes, type, expenseDate, paymentSource)
        _userFeedbackMessage.value = "Expense voucher $voucher saved!"
    }

    fun acknowledgeExpense(expenseId: String) {
        repository.acknowledgeExpense(expenseId)
        _userFeedbackMessage.value = "Expense voucher verified by Admin."
    }

    fun acknowledgePayment(paymentId: String) {
        repository.acknowledgePayment(paymentId)
        _userFeedbackMessage.value = "Payment marked Clear by Admin."
    }

    fun acknowledgePayments(paymentIds: List<String>) {
        repository.acknowledgePayments(paymentIds)
        _userFeedbackMessage.value = "Payment(s) marked Clear by Admin."
    }

    fun deleteDelivery(deliveryId: String) {
        if (repository.deleteDelivery(deliveryId)) {
            _userFeedbackMessage.value = "Delivery deleted successfully"
        } else {
            _userFeedbackMessage.value = "Failed to delete delivery (Admin permission required)"
        }
    }

    fun updateDelivery(delivery: Delivery): Boolean {
        val success = repository.updateDelivery(delivery)
        if (success) {
            _userFeedbackMessage.value = "Delivery updated successfully"
        } else {
            _userFeedbackMessage.value = "Failed to update delivery (Admin permission required)"
        }
        return success
    }

    fun deleteExpense(expenseId: String) {
        if (repository.deleteExpense(expenseId)) {
            _userFeedbackMessage.value = "Expense voucher deleted successfully"
        } else {
            _userFeedbackMessage.value = "Failed to delete expense (Admin permission required)"
        }
    }

    fun updateExpense(expense: Expense): Boolean {
        val success = repository.updateExpense(expense)
        if (success) {
            _userFeedbackMessage.value = "Expense voucher updated successfully"
        } else {
            _userFeedbackMessage.value = "Failed to update expense (Admin permission required)"
        }
        return success
    }

    fun deletePayment(paymentId: String) {
        if (repository.deletePayment(paymentId)) {
            _userFeedbackMessage.value = "Payment deleted successfully"
        }
    }

    fun addExpenseCategory(name: String, urdu: String, type: ExpenseType = ExpenseType.OPEX) {
        repository.addExpenseCategory(name, urdu, type)
        _userFeedbackMessage.value = "Category '$name' added"
    }

    fun deleteExpenseCategory(categoryId: String): Boolean {
        val success = repository.deleteExpenseCategory(categoryId)
        if (success) {
            _userFeedbackMessage.value = "Expense category deleted"
        }
        return success
    }

    fun getDailyCashSummary(date: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())): CashClosingSummary {
        return repository.getDailyCashSummary(date)
    }

    fun submitCashClosing(summary: CashClosingSummary, actualCash: Double, notes: String) {
        val closing = repository.submitCashClosing(summary, actualCash, notes)
        val diffText = if (closing.isBalanced) "Perfect match!" else "Difference: Rs. ${closing.difference}"
        _userFeedbackMessage.value = "Daily cash closing submitted. $diffText"
    }

    fun adjustInventory(sizeId: String, full: Int, empty: Int, damaged: Int, reason: String) {
        repository.adjustBottleStock(sizeId, fullChange = full, emptyChange = empty, damagedChange = damaged, reason = reason)
        _userFeedbackMessage.value = "Inventory updated: $reason"
    }

    // -------------------------------------------------------------------------
    // BOTTLE FLEET & COST MANAGEMENT METHODS
    // -------------------------------------------------------------------------
    fun recordBottlePurchase(
        bottleSizeId: String,
        quantity: Int,
        unitCost: Double,
        supplierName: String,
        invoiceOrBillNo: String,
        notes: String
    ): String {
        val id = repository.recordBottlePurchase(
            bottleSizeId = bottleSizeId,
            quantity = quantity,
            unitCost = unitCost,
            supplierName = supplierName,
            invoiceOrBillNo = invoiceOrBillNo,
            notes = notes
        )
        _userFeedbackMessage.value = "Recorded purchase of $quantity new bottles."
        return id
    }

    fun recordBottleRefill(
        bottleSizeId: String,
        quantity: Int,
        refillCostPerBottle: Double,
        operatorName: String,
        notes: String
    ): Boolean {
        val success = repository.recordBottleRefill(
            bottleSizeId = bottleSizeId,
            quantity = quantity,
            refillCostPerBottle = refillCostPerBottle,
            operatorName = operatorName,
            notes = notes
        )
        if (success) {
            _userFeedbackMessage.value = "Recorded refill batch of $quantity bottles."
        }
        return success
    }

    fun updateBottleCostConfig(
        bottleSizeId: String,
        refillCost: Double,
        deliveryCost: Double,
        deliveryUnit: DeliveryUnit,
        packetPackagingQty: Int,
        isReturnableBottle: Boolean
    ): Boolean {
        val success = repository.updateBottleCostConfig(
            bottleSizeId = bottleSizeId,
            refillCost = refillCost,
            deliveryCost = deliveryCost,
            deliveryUnit = deliveryUnit,
            packetPackagingQty = packetPackagingQty,
            isReturnableBottle = isReturnableBottle
        )
        if (success) {
            _userFeedbackMessage.value = "Updated refill & delivery cost configuration."
        }
        return success
    }

    // -------------------------------------------------------------------------
    // RO PLANT GENERAL INVENTORY & SPARES METHODS
    // -------------------------------------------------------------------------
    fun addRoPlantItem(item: RoPlantItem): String {
        val id = repository.addRoPlantItem(item)
        _userFeedbackMessage.value = "Added '${item.name}' to RO Plant inventory."
        return id
    }

    fun updateRoPlantItem(item: RoPlantItem): Boolean {
        val success = repository.updateRoPlantItem(item)
        if (success) {
            _userFeedbackMessage.value = "Updated RO Plant item '${item.name}'."
        }
        return success
    }

    fun deleteRoPlantItem(itemId: String): Boolean {
        val success = repository.deleteRoPlantItem(itemId)
        if (success) {
            _userFeedbackMessage.value = "Item removed from catalog."
        }
        return success
    }

    fun recordRoItemAction(
        itemId: String,
        actionType: InventoryActionType,
        quantity: Double,
        unitCost: Double,
        employee: String,
        reasonOrPurpose: String,
        notes: String
    ): Boolean {
        val success = repository.recordRoItemAction(
            itemId = itemId,
            actionType = actionType,
            quantity = quantity,
            unitCost = unitCost,
            employee = employee,
            reasonOrPurpose = reasonOrPurpose,
            notes = notes
        )
        if (success) {
            _userFeedbackMessage.value = "Recorded ${actionType.label}: ${quantity} units."
        }
        return success
    }

    // Dashboard Statistics
    val dashboardStats = combine(
        deliveries,
        payments,
        expenses,
        customers,
        inventory
    ) { dels, pays, exps, custs, invs ->
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayDeliveries = dels.filter { it.date == today }
        val todaySales = todayDeliveries.sumOf { it.totalAmount }
        val todayCashCollected = pays.filter { it.date == today && it.paymentMethod.isCash }.sumOf { it.amount } +
                todayDeliveries.filter { it.paymentMethod == "Cash" }.sumOf { it.paymentCollected }
        val todayDigitalCollected = pays.filter { it.date == today && !it.paymentMethod.isCash }.sumOf { it.amount }

        val totalDelivered19L = todayDeliveries.flatMap { it.items }.filter { it.bottleSizeId == "size_19l" }.sumOf { it.quantity }
        val totalDeliveredOther = todayDeliveries.flatMap { it.items }.filter { it.bottleSizeId != "size_19l" }.sumOf { it.quantity }
        val totalEmptyCollected = todayDeliveries.sumOf { it.totalEmptyCollected }

        val todayExpenses = exps.filter { it.date == today }.sumOf { it.amount }
        val estimatedProfit = todaySales - todayExpenses

        val totalReceivables = custs.sumOf { cust ->
            val due = getCustomerTotalDue(cust)
            if (due > 0.0) due else 0.0
        }
        val activeCustomerCount = custs.count { it.active }

        val totalBottlesAtPlant = invs.sumOf { it.totalPlantBottles }
        val totalBottlesWithCustomers = invs.sumOf { it.withCustomers }
        val totalDamagedBottles = invs.sumOf { it.damaged }

        DashboardStats(
            todaySales = todaySales,
            todayCashCollected = todayCashCollected,
            todayDigitalCollected = todayDigitalCollected,
            todayTotalCollected = todayCashCollected + todayDigitalCollected,
            todayExpenses = todayExpenses,
            estimatedProfit = estimatedProfit,
            deliveryCount = todayDeliveries.size,
            delivered19L = totalDelivered19L,
            deliveredOther = totalDeliveredOther,
            emptyBottlesCollected = totalEmptyCollected,
            totalReceivables = totalReceivables,
            activeCustomers = activeCustomerCount,
            bottlesAtPlant = totalBottlesAtPlant,
            bottlesWithCustomers = totalBottlesWithCustomers,
            totalDamagedBottles = totalDamagedBottles
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardStats()
    )

    fun generateCustomerStatement(customer: Customer): String {
        val unpaidInvs = repository.getUnpaidInvoicesForCustomer(customer.id)
        val allocations = repository.getCustomerBottleAllocations(customer.id)
        val sb = StringBuilder()
        sb.appendLine("💧 *Aab e Baraan RO Water Plant* 💧")
        sb.appendLine("Pure Mineral Water Service")
        sb.appendLine("--------------------------------")
        sb.appendLine("👤 *Customer:* ${customer.name} (${customer.customerCode})")
        sb.appendLine("📍 *Address:* ${customer.address}")
        sb.appendLine("💰 *Total Outstanding:* Rs. ${customer.currentBalance}")
        sb.appendLine("🍾 *Empty Bottles Owed:* ${customer.emptyBottlesOwed} bottles")
        val broken = allocations.sumOf { it.damagedCount }
        if (broken > 0) {
            sb.appendLine("⚠️ *Damaged / Broken Bottles:* $broken bottles")
        }
        sb.appendLine("--------------------------------")
        if (unpaidInvs.isNotEmpty()) {
            sb.appendLine("📋 *Pending Invoices:*")
            unpaidInvs.forEach { inv ->
                sb.appendLine("• ${inv.invoiceNumber} (${inv.date}): Rs. ${inv.balanceDue}")
            }
            sb.appendLine("--------------------------------")
        }
        sb.appendLine("📞 For queries: 0300-1234567")
        sb.appendLine("JazakAllah Khair!")
        return sb.toString()
    }

    // 1. Daily WhatsApp Report with Customer-wise Deliveries, Expenses, and Income
    fun generateDailyReportSummary(): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dateDisplay = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())

        val todayDeliveries = deliveries.value.filter { it.date == today }
        val todayExpenses = expenses.value.filter { it.date == today }
        val todayPayments = payments.value.filter { it.date == today }

        val totalSales = todayDeliveries.sumOf { it.totalAmount }
        val cashCollected = todayPayments.filter { it.paymentMethod.isCash }.sumOf { it.amount } +
                todayDeliveries.filter { it.paymentMethod == "Cash" }.sumOf { it.paymentCollected }
        val onlineCollected = todayPayments.filter { !it.paymentMethod.isCash }.sumOf { it.amount }
        val totalIncome = cashCollected + onlineCollected
        val totalExpenseAmount = todayExpenses.sumOf { it.amount }
        val netProfit = totalSales - totalExpenseAmount

        val sb = StringBuilder()
        sb.appendLine("💧 *Aab e Baraan — Daily Report (روزانہ رپورٹ)* 💧")
        sb.appendLine("📅 Date: $dateDisplay")
        sb.appendLine("================================")
        sb.appendLine("🚚 *CUSTOMER-WISE DELIVERIES (گاہک ترسیلات):*")
        if (todayDeliveries.isEmpty()) {
            sb.appendLine("  (No deliveries recorded today)")
        } else {
            todayDeliveries.forEachIndexed { idx, del ->
                val itemsStr = del.items.joinToString(", ") { "${it.quantity}x ${it.bottleName}" }
                val paidStr = if (del.paymentCollected > 0) "Paid (Rs. ${del.paymentCollected.toInt()})" else "Unpaid"
                sb.appendLine("${idx + 1}. *${del.customerName}* (${del.customerArea})")
                sb.appendLine("   📦 $itemsStr")
                sb.appendLine("   🍾 Empties Collected: ${del.totalEmptyCollected}")
                sb.appendLine("   💵 Bill: Rs. ${del.totalAmount.toInt()} | Status: $paidStr")
            }
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("💰 *DAILY INCOME & COLLECTIONS (آمدن / وصولی):*")
        sb.appendLine("• Total Billed Sales: Rs. ${totalSales.toInt()}")
        sb.appendLine("• Cash Collected (Naqd): Rs. ${cashCollected.toInt()}")
        sb.appendLine("• Online / Bank: Rs. ${onlineCollected.toInt()}")
        sb.appendLine("• *Total Cash & Online Income:* Rs. ${totalIncome.toInt()}")
        sb.appendLine("--------------------------------")
        sb.appendLine("📉 *DAILY EXPENSES (روزانہ اخراجات):*")
        if (todayExpenses.isEmpty()) {
            sb.appendLine("  (No expenses recorded today)")
        } else {
            val fromSaleTotal = todayExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_SALE }.sumOf { it.amount }
            val investmentTotal = todayExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_INVESTMENT }.sumOf { it.amount }
            todayExpenses.forEach { exp ->
                val srcTag = if (exp.paymentSource == PaymentSource.PAY_FROM_INVESTMENT) "Investment" else "From Sale"
                sb.appendLine("• ${exp.categoryName} [$srcTag]: Rs. ${exp.amount.toInt()} (${exp.description})")
            }
            sb.appendLine("• From Sale Expenses: Rs. ${fromSaleTotal.toInt()}")
            sb.appendLine("• Investment Account Expenses: Rs. ${investmentTotal.toInt()}")
            sb.appendLine("• *Total Daily Expenses:* Rs. ${totalExpenseAmount.toInt()}")
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("💹 *EST. NET OPERATING PROFIT (خالص بچت):* Rs. ${netProfit.toInt()}")
        sb.appendLine("================================")
        sb.appendLine("Generated by Aab e Baraan System")
        return sb.toString()
    }

    // 2. Weekly WhatsApp Report (Customer Deliveries, Weekly Expenses & Income)
    fun generateWeeklyReportSummary(): String {
        val todayCal = Calendar.getInstance()
        val endDateDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(todayCal.time)
        todayCal.add(Calendar.DAY_OF_YEAR, -7)
        val startDateDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(todayCal.time)

        val recentDeliveries = deliveries.value.take(25)
        val totalSales = recentDeliveries.sumOf { it.totalAmount }
        val totalBottlesDelivered = recentDeliveries.sumOf { it.totalFullDelivered }
        val totalEmpties = recentDeliveries.sumOf { it.totalEmptyCollected }

        val recentExpenses = expenses.value.take(15)
        val totalExpenses = recentExpenses.sumOf { it.amount }
        val netProfit = totalSales - totalExpenses

        val customerDeliverySummary = recentDeliveries.groupBy { it.customerName }

        val sb = StringBuilder()
        sb.appendLine("📊 *Aab e Baraan — Weekly Summary Report (ہفتہ وار رپورٹ)* 📊")
        sb.appendLine("📅 Period: $startDateDisplay to $endDateDisplay")
        sb.appendLine("================================")
        sb.appendLine("🚚 *WEEKLY DELIVERIES BY CUSTOMER:*")
        customerDeliverySummary.forEach { (custName, dels) ->
            val custBottles = dels.sumOf { it.totalFullDelivered }
            val custAmount = dels.sumOf { it.totalAmount }
            sb.appendLine("• *${custName}*: $custBottles bottles delivered (Rs. ${custAmount.toInt()})")
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("📈 *WEEKLY TOTALS:*")
        sb.appendLine("• Total Orders: ${recentDeliveries.size}")
        sb.appendLine("• Total Bottles Delivered: $totalBottlesDelivered bottles")
        sb.appendLine("• Empty Bottles Returned: $totalEmpties bottles")
        sb.appendLine("• *Total Gross Sales:* Rs. ${totalSales.toInt()}")
        sb.appendLine("--------------------------------")
        sb.appendLine("📉 *WEEKLY EXPENSES (اخراجات):*")
        val weekFromSale = recentExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_SALE }.sumOf { it.amount }
        val weekInvestment = recentExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_INVESTMENT }.sumOf { it.amount }
        recentExpenses.groupBy { it.categoryName }.forEach { (cat, exps) ->
            sb.appendLine("• $cat: Rs. ${exps.sumOf { it.amount }.toInt()}")
        }
        sb.appendLine("• From Sale Expenses: Rs. ${weekFromSale.toInt()}")
        sb.appendLine("• Investment Account Expenses: Rs. ${weekInvestment.toInt()}")
        sb.appendLine("• *Total Weekly Expenses:* Rs. ${totalExpenses.toInt()}")
        sb.appendLine("--------------------------------")
        sb.appendLine("💹 *WEEKLY OPERATING PROFIT (خالص بچت):* Rs. ${netProfit.toInt()}")
        sb.appendLine("================================")
        sb.appendLine("Generated by Aab e Baraan Smart System")
        return sb.toString()
    }

    // 3. Monthly WhatsApp Report
    fun generateMonthlyReportSummary(): String {
        val monthDisplay = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

        val allDels = deliveries.value
        val totalSales = allDels.sumOf { it.totalAmount }
        val totalBottles = allDels.sumOf { it.totalFullDelivered }
        val totalEmpties = allDels.sumOf { it.totalEmptyCollected }

        val allExps = expenses.value
        val totalExpenses = allExps.sumOf { it.amount }
        val netProfit = totalSales - totalExpenses

        val custSummary = allDels.groupBy { it.customerName }
        val totalReceivables = customers.value.filter { it.currentBalance > 0 }.sumOf { it.currentBalance }

        val sb = StringBuilder()
        sb.appendLine("📑 *Aab e Baraan — Monthly Business Report (ماہانہ رپورٹ)* 📑")
        sb.appendLine("🗓️ Month: $monthDisplay")
        sb.appendLine("================================")
        sb.appendLine("👥 *CUSTOMER VOLUME BREAKDOWN:*")
        custSummary.forEach { (custName, dels) ->
            val bCount = dels.sumOf { it.totalFullDelivered }
            val amt = dels.sumOf { it.totalAmount }
            sb.appendLine("• *${custName}*: $bCount bottles (Rs. ${amt.toInt()})")
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("💰 *MONTHLY FINANCIAL PERFORMANCE:*")
        sb.appendLine("• Total Deliveries: ${allDels.size} dispatches")
        sb.appendLine("• Total Bottles Supplied: $totalBottles bottles")
        sb.appendLine("• Total Empty Bottles Returned: $totalEmpties bottles")
        val monthFromSale = allExps.filter { it.paymentSource == PaymentSource.PAY_FROM_SALE }.sumOf { it.amount }
        val monthInvestment = allExps.filter { it.paymentSource == PaymentSource.PAY_FROM_INVESTMENT }.sumOf { it.amount }
        sb.appendLine("• *Total Billed Sales:* Rs. ${totalSales.toInt()}")
        sb.appendLine("• From Sale Expenses: Rs. ${monthFromSale.toInt()}")
        sb.appendLine("• Investment Account Expenses: Rs. ${monthInvestment.toInt()}")
        sb.appendLine("• *Total Operating Expenses:* Rs. ${totalExpenses.toInt()}")
        sb.appendLine("• *Estimated Net Profit:* Rs. ${netProfit.toInt()}")
        sb.appendLine("• *Total Market Receivables (Udhaar):* Rs. ${totalReceivables.toInt()}")
        sb.appendLine("================================")
        sb.appendLine("Generated by Aab e Baraan Smart System")
        return sb.toString()
    }

    // ==========================================
    // BACKUP & RESTORE OPERATIONS
    // ==========================================

    fun backupNow(notes: String = "", onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        repository.backupNow(notes) { success, msg ->
            _userFeedbackMessage.value = msg
            onResult(success, msg)
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        repository.toggleAutoBackup(enabled)
    }

    fun verifyBackupJson(jsonString: String): BackupVerificationReport {
        return repository.verifyBackupPayload(jsonString)
    }

    fun restoreFromVerifiedBackup(
        backupData: PlantBackupData,
        requireSafety: Boolean = true,
        onComplete: (Boolean, String) -> Unit
    ) {
        repository.restoreFromBackup(backupData, requireSafety) { success, msg ->
            _userFeedbackMessage.value = msg
            onComplete(success, msg)
        }
    }

    fun fetchCloudBackups(onResult: (List<BackupMetadata>) -> Unit) {
        repository.fetchCloudBackups(onResult)
    }

    fun fetchCloudBackupData(backupId: String, onResult: (PlantBackupData?) -> Unit) {
        repository.fetchCloudBackupData(backupId, onResult)
    }

    fun exportBackupFile(backupId: String): java.io.File? {
        return repository.exportLocalBackupFile(backupId)
    }

    fun generateCsvExport(collectionType: String): java.io.File? {
        return repository.generateCsvExport(collectionType)
    }

    fun markDeliveryAsPaid(deliveryId: String, amountPaid: Double? = null, method: PaymentMethod = PaymentMethod.CASH): Boolean {
        val success = repository.markDeliveryAsPaid(deliveryId, amountPaid, method)
        if (success) {
            _userFeedbackMessage.value = "Payment marked as paid for delivery."
        }
        return success
    }

    fun recordDailyPlantExpense(
        categoryId: String,
        categoryName: String,
        description: String,
        amount: Double,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        payee: String = "",
        notes: String = "",
        date: String? = null
    ): Expense {
        val exp = repository.recordDailyPlantExpense(
            categoryId = categoryId,
            categoryName = categoryName,
            description = description,
            amount = amount,
            paymentMethod = paymentMethod,
            payee = payee,
            notes = notes,
            date = date
        )
        _userFeedbackMessage.value = "Recorded $categoryName: Rs. ${amount.toInt()}"
        return exp
    }

    fun editBottleStock(
        bottleSizeId: String,
        full: Int,
        empty: Int,
        damaged: Int,
        withStaff: Int = 0,
        withCustomers: Int = 0,
        reason: String = "Manual Edit"
    ): Boolean {
        val success = repository.editBottleStock(bottleSizeId, full, empty, damaged, withStaff, withCustomers, reason)
        if (success) {
            _userFeedbackMessage.value = "Stock updated successfully."
        }
        return success
    }

    fun deleteBottleStock(bottleSizeId: String): Boolean {
        val success = repository.deleteBottleStock(bottleSizeId)
        if (success) {
            _userFeedbackMessage.value = "Stock record deleted."
        }
        return success
    }

    fun editRoPlantItem(item: RoPlantItem): Boolean {
        val success = repository.editRoPlantItem(item)
        if (success) {
            _userFeedbackMessage.value = "RO item updated."
        }
        return success
    }

    fun clearAllStock(): Boolean {
        val success = repository.clearAllStock()
        if (success) {
            _userFeedbackMessage.value = "All warehouse stock reset to 0."
        }
        return success
    }

    fun clearAllDeliveriesAndExpenses(): Boolean {
        val success = repository.clearAllDeliveriesAndExpenses()
        if (success) {
            _userFeedbackMessage.value = "Deliveries and expenses cleared."
        }
        return success
    }

    // =============================================================
    // COMPLETE REBUILT STOCK & COST VIEWMODEL METHODS
    // =============================================================

    fun addNewBottleSize(
        name: String,
        purchaseRate: Double,
        packQty: Int,
        uom: String,
        openingStock: Int,
        usageType: BottleUsageType,
        defaultRefillPrice: Double,
        salePrice: Double,
        customUsageType: String = "",
        capacityLitres: Double? = null,
        depositAmount: Double = 0.0,
        onSuccess: (() -> Unit)? = null
    ) {
        repository.addNewBottleSize(
            name = name,
            purchaseRate = purchaseRate,
            packQty = packQty,
            uom = uom,
            openingStock = openingStock,
            usageType = usageType,
            defaultRefillPrice = defaultRefillPrice,
            salePrice = salePrice,
            customUsageType = customUsageType,
            capacityLitres = capacityLitres,
            depositAmount = depositAmount
        )
        _userFeedbackMessage.value = "New bottle size added successfully."
        onSuccess?.invoke()
    }

    fun updateBottleSizeDetails(
        bottleSizeId: String,
        name: String,
        capacityLitres: Double,
        usageType: BottleUsageType,
        defaultRefillPrice: Double,
        purchaseCost: Double,
        depositAmount: Double,
        uom: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.updateBottleSizeDetails(
            bottleSizeId = bottleSizeId,
            name = name,
            capacityLitres = capacityLitres,
            usageType = usageType,
            defaultRefillPrice = defaultRefillPrice,
            purchaseCost = purchaseCost,
            depositAmount = depositAmount,
            uom = uom
        )
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun refillBottleStock(
        bottleSizeId: String,
        quantity: Int,
        purchaseDate: String,
        supplierName: String,
        unitPurchaseRate: Double,
        notes: String,
        paymentSource: PaymentSource = PaymentSource.PAY_FROM_SALE,
        paymentMode: StockPaymentMode = StockPaymentMode.CASH,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.refillBottleStock(
            bottleSizeId = bottleSizeId,
            quantity = quantity,
            purchaseDate = purchaseDate,
            supplierName = supplierName,
            unitPurchaseRate = unitPurchaseRate,
            notes = notes,
            paymentSource = paymentSource,
            paymentMode = paymentMode
        )
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun refillSingleUseBatch(
        bottleSizeId: String,
        quantity: Int,
        date: String,
        notes: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.refillSingleUseBatch(bottleSizeId, quantity, date, notes)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun refillSingleUseBatchWithConversion(
        bottleSizeId: String,
        refillQuantity: Int,
        packetSize: Int,
        damagedQuantity: Int,
        date: String,
        notes: String,
        containerType: PackContainerType = PackContainerType.PACKET,
        returnLooseToInUse: Boolean = false,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.refillSingleUseBatchWithConversion(
            bottleSizeId = bottleSizeId,
            refillQuantity = refillQuantity,
            packetSize = packetSize,
            damagedQuantity = damagedQuantity,
            date = date,
            notes = notes,
            containerType = containerType,
            returnLooseToInUse = returnLooseToInUse
        )
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun recordBottleLossOrDamage(
        source: BottleLossSource,
        bottleSizeId: String,
        quantity: Int,
        date: String,
        remarks: String,
        customerId: String? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.recordBottleLossOrDamage(
            source = source,
            bottleSizeId = bottleSizeId,
            quantity = quantity,
            date = date,
            remarks = remarks,
            customerId = customerId
        )
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun issueBottleStock(
        bottleSizeId: String,
        target: BottleIssueTarget,
        customerId: String?,
        quantity: Int,
        issueDate: String,
        deposit: Double,
        notes: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.issueBottleStock(bottleSizeId, target, customerId, quantity, issueDate, deposit, notes)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun returnCustomerBottleStock(
        customerId: String,
        bottleSizeId: String,
        quantity: Int,
        returnDate: String,
        notes: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.returnCustomerBottleStock(customerId, bottleSizeId, quantity, returnDate, notes)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun recordCustomerDamageStock(
        customerId: String,
        bottleSizeId: String,
        damagedQuantity: Int,
        date: String,
        remarks: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.recordCustomerDamageStock(customerId, bottleSizeId, damagedQuantity, date, remarks)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun consumeOneTimeBottles(
        bottleSizeId: String,
        quantity: Int,
        date: String,
        notes: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.consumeOneTimeBottles(bottleSizeId, quantity, date, notes)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun deleteOrArchiveBottleSize(
        bottleSizeId: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.deleteOrArchiveBottleSize(bottleSizeId)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun forceDeleteBottleSize(
        bottleSizeId: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.forceDeleteBottleSize(bottleSizeId)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun deleteBottleMovement(movementId: String): Boolean {
        val success = repository.deleteBottleMovement(movementId)
        if (success) _userFeedbackMessage.value = "Movement record deleted."
        return success
    }

    fun clearAllBottleMovements(): Boolean {
        val success = repository.clearAllBottleMovements()
        if (success) _userFeedbackMessage.value = "All stock movement logs cleared."
        return success
    }

    fun deleteMaterialTransaction(txId: String): Boolean {
        val success = repository.deleteMaterialTransaction(txId)
        if (success) _userFeedbackMessage.value = "Material transaction record deleted."
        return success
    }

    fun clearAllMaterialTransactions(): Boolean {
        val success = repository.clearAllMaterialTransactions()
        if (success) _userFeedbackMessage.value = "All material transfer records cleared."
        return success
    }

    fun addFactoryMaterial(
        material: FactoryMaterial,
        onSuccess: (() -> Unit)? = null
    ) {
        repository.addFactoryMaterial(material)
        _userFeedbackMessage.value = "Material added successfully."
        onSuccess?.invoke()
    }

    fun updateFactoryMaterial(
        material: FactoryMaterial,
        onSuccess: (() -> Unit)? = null
    ) {
        repository.updateFactoryMaterial(material)
        _userFeedbackMessage.value = "Material updated."
        onSuccess?.invoke()
    }

    fun issueFactoryMaterial(
        materialId: String,
        category: MaterialIssueCategory,
        quantity: Double,
        date: String,
        responsiblePerson: String,
        remarks: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.issueFactoryMaterial(materialId, category, quantity, date, responsiblePerson, remarks)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun refillFactoryMaterial(
        materialId: String,
        quantity: Double,
        purchaseRate: Double,
        date: String,
        supplierName: String,
        invoiceNo: String,
        notes: String,
        paymentSource: PaymentSource = PaymentSource.PAY_FROM_SALE,
        paymentMode: StockPaymentMode = StockPaymentMode.CASH,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.refillFactoryMaterial(
            materialId = materialId,
            quantity = quantity,
            purchaseRate = purchaseRate,
            date = date,
            supplierName = supplierName,
            invoiceNo = invoiceNo,
            notes = notes,
            paymentSource = paymentSource,
            paymentMode = paymentMode
        )
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun recordMaterialDamageOrMalfunction(
        materialId: String,
        actionType: MaterialActionType,
        quantity: Double,
        date: String,
        remarks: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.recordMaterialDamageOrMalfunction(
            materialId = materialId,
            actionType = actionType,
            quantity = quantity,
            date = date,
            remarks = remarks
        )
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun deleteOrArchiveFactoryMaterial(
        materialId: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.deleteOrArchiveFactoryMaterial(materialId)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun saveBottleCosting(
        record: BottleCostingRecord,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.saveBottleCosting(record)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun deleteBottleCosting(
        costingId: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.deleteBottleCosting(costingId)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun addMonthlyRecurringExpense(
        expense: MonthlyRecurringExpense,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.addMonthlyRecurringExpense(expense)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun deleteMonthlyRecurringExpense(
        expenseId: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val (success, message) = repository.deleteMonthlyRecurringExpense(expenseId)
        _userFeedbackMessage.value = message
        onResult?.invoke(success, message)
    }

    fun getMonthlyFinancialSummary(month: String): MonthlyFinancialSummary {
        return repository.getMonthlyFinancialSummary(month)
    }
}

data class DashboardStats(
    val todaySales: Double = 0.0,
    val todayCashCollected: Double = 0.0,
    val todayDigitalCollected: Double = 0.0,
    val todayTotalCollected: Double = 0.0,
    val todayExpenses: Double = 0.0,
    val estimatedProfit: Double = 0.0,
    val deliveryCount: Int = 0,
    val delivered19L: Int = 0,
    val deliveredOther: Int = 0,
    val emptyBottlesCollected: Int = 0,
    val totalReceivables: Double = 0.0,
    val activeCustomers: Int = 0,
    val bottlesAtPlant: Int = 0,
    val bottlesWithCustomers: Int = 0,
    val totalDamagedBottles: Int = 0
)
