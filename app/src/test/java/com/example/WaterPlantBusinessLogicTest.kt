package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LoginOutcome
import com.example.data.WaterPlantRepository
import com.example.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WaterPlantBusinessLogicTest {

    private lateinit var repository: WaterPlantRepository

    @Before
    fun setUp() {
        WaterPlantRepository.resetInstanceForTesting()
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = WaterPlantRepository.getInstance(context)
        repository.switchUserRole(UserRole.ADMIN)
    }

    @Test
    fun testCustomerBottleSizeAssignmentAndIndependence() {
        // Customer A: Assign 19L at Rs. 130 and 6L at Rs. 55
        repository.setCustomerPrice(
            customerId = "test_cust_a",
            bottleSizeId = "size_19l",
            customPrice = 130.0,
            bottleSizeName = "19 Litre Bottle",
            appliedDate = "2026-10-01"
        )
        repository.setCustomerPrice(
            customerId = "test_cust_a",
            bottleSizeId = "size_6l",
            customPrice = 55.0,
            bottleSizeName = "6 Litre Bottle",
            appliedDate = "2026-10-01"
        )

        // Customer B: Assign 12.5L at Rs. 85 and 1.5L at Rs. 350
        repository.setCustomerPrice(
            customerId = "test_cust_b",
            bottleSizeId = "size_12l",
            customPrice = 85.0,
            bottleSizeName = "12.5 Litre Bottle",
            appliedDate = "2026-10-01"
        )
        repository.setCustomerPrice(
            customerId = "test_cust_b",
            bottleSizeId = "size_1_5l",
            customPrice = 350.0,
            bottleSizeName = "1.5L Pack (Carton)",
            appliedDate = "2026-10-01"
        )

        // Verify Customer A rates
        val ratesA = repository.getCustomerRates("test_cust_a").filter { it.isAssigned }
        assertEquals(2, ratesA.size)
        assertEquals(130.0, repository.getCustomerPrice("test_cust_a", "size_19l"), 0.01)
        assertEquals(55.0, repository.getCustomerPrice("test_cust_a", "size_6l"), 0.01)

        // Verify Customer B rates are independent
        val ratesB = repository.getCustomerRates("test_cust_b").filter { it.isAssigned }
        assertEquals(2, ratesB.size)
        assertEquals(85.0, repository.getCustomerPrice("test_cust_b", "size_12l"), 0.01)
        assertEquals(350.0, repository.getCustomerPrice("test_cust_b", "size_1_5l"), 0.01)

        // Unassigned size for Customer A should default to ZERO (0.0) per Customer Rate Rules
        val defaultPriceForA = repository.getCustomerPrice("test_cust_a", "size_12l")
        assertEquals(0.0, defaultPriceForA, 0.01)
    }

    @Test
    fun testEffectiveDatedRateHistoryAndHistoricalIntegrity() {
        val custId = "cust_effective_test"

        // Initial rate on 2026-09-01: Rs. 100
        repository.setCustomerPrice(
            customerId = custId,
            bottleSizeId = "size_19l",
            customPrice = 100.0,
            bottleSizeName = "19 Litre Bottle",
            appliedDate = "2026-09-01"
        )

        // Updated rate on 2026-10-10: Rs. 120
        repository.setCustomerPrice(
            customerId = custId,
            bottleSizeId = "size_19l",
            customPrice = 120.0,
            bottleSizeName = "19 Litre Bottle",
            appliedDate = "2026-10-10"
        )

        // For a delivery on 2026-09-15 (between 09-01 and 10-10), applicable rate must be 100
        val historicalRate = repository.getCustomerRateForDate(custId, "size_19l", "2026-09-15")
        assertEquals(100.0, historicalRate, 0.01)

        // For a delivery on 2026-10-12 (after 10-10), applicable rate must be 120
        val currentRate = repository.getCustomerRateForDate(custId, "size_19l", "2026-10-12")
        assertEquals(120.0, currentRate, 0.01)

        // Verify rate history has 2 entries
        val rateRecord = repository.getCustomerRates(custId).find { it.bottleSizeId == "size_19l" }
        assertNotNull(rateRecord)
        assertEquals(2, rateRecord!!.rateHistory.size)
    }

    @Test
    fun testDeactivateAndReactivateBottleAssignment() {
        val custId = "cust_deact_test"

        repository.setCustomerPrice(
            customerId = custId,
            bottleSizeId = "size_6l",
            customPrice = 60.0,
            bottleSizeName = "6 Litre Bottle",
            appliedDate = "2026-10-01"
        )

        val beforeDeact = repository.getAssignedCustomerRates(custId)
        assertTrue(beforeDeact.any { it.bottleSizeId == "size_6l" && it.isAssigned })

        // Deactivate size
        repository.removeCustomerBottleSize(custId, "size_6l")
        val afterDeact = repository.getAssignedCustomerRates(custId)
        assertFalse(afterDeact.any { it.bottleSizeId == "size_6l" && it.isAssigned })

        // Reactivate size
        repository.reactivateCustomerBottleSize(custId, "size_6l")
        val afterReactivity = repository.getAssignedCustomerRates(custId)
        assertTrue(afterReactivity.any { it.bottleSizeId == "size_6l" && it.isAssigned })
    }

    @Test
    fun testSecurityDepositReconciliation() {
        val custId = "cust_sec_test"
        val custName = "Sec Customer"

        // Initial deposit: 5000
        repository.recordSecurityDepositTransaction(
            SecurityDepositTransaction(
                id = "sec_tx_1",
                customerId = custId,
                customerName = custName,
                amount = 5000.0,
                type = SecurityDepositTransactionType.INITIAL_DEPOSIT,
                date = "2026-09-01",
                paymentMethod = PaymentMethod.CASH
            )
        )

        assertEquals(5000.0, repository.getCustomerSecurityDepositBalance(custId), 0.01)

        // Additional deposit: 2000 -> Total 7000
        repository.recordSecurityDepositTransaction(
            SecurityDepositTransaction(
                id = "sec_tx_2",
                customerId = custId,
                customerName = custName,
                amount = 2000.0,
                type = SecurityDepositTransactionType.ADDITIONAL_DEPOSIT,
                date = "2026-09-15",
                paymentMethod = PaymentMethod.CASH
            )
        )
        assertEquals(7000.0, repository.getCustomerSecurityDepositBalance(custId), 0.01)

        // Partial refund: 1500 -> Remaining 5500
        repository.recordSecurityDepositTransaction(
            SecurityDepositTransaction(
                id = "sec_tx_3",
                customerId = custId,
                customerName = custName,
                amount = 1500.0,
                type = SecurityDepositTransactionType.PARTIAL_REFUND,
                date = "2026-10-01",
                paymentMethod = PaymentMethod.CASH
            )
        )
        assertEquals(5500.0, repository.getCustomerSecurityDepositBalance(custId), 0.01)

        // Adjustment: -500 -> Remaining 5000
        repository.recordSecurityDepositTransaction(
            SecurityDepositTransaction(
                id = "sec_tx_4",
                customerId = custId,
                customerName = custName,
                amount = -500.0,
                type = SecurityDepositTransactionType.ADJUSTMENT,
                date = "2026-10-05",
                paymentMethod = PaymentMethod.CASH
            )
        )
        assertEquals(5000.0, repository.getCustomerSecurityDepositBalance(custId), 0.01)
    }

    @Test
    fun testDeliveryCalculationWithMultipleAssignedBottles() {
        val custId = "cust_multi_del"

        // Create Customer
        val customer = Customer(
            id = custId,
            customerCode = "C-DEL-01",
            name = "Delivery Customer",
            phone = "0300-9999999",
            paymentCycle = PaymentCycle.DAILY
        )
        repository.addCustomer(customer)

        val items = listOf(
            DeliveryItem(
                bottleSizeId = "size_19l",
                bottleName = "19 Litre Bottle",
                quantity = 4,
                unitPrice = 120.0,
                emptyCollected = 4,
                lineTotal = 480.0
            ),
            DeliveryItem(
                bottleSizeId = "size_6l",
                bottleName = "6 Litre Bottle",
                quantity = 2,
                unitPrice = 50.0,
                emptyCollected = 1,
                lineTotal = 100.0
            )
        )

        val delNum = repository.recordDelivery(
            customerId = custId,
            items = items,
            assignedStaffName = "Rider Test",
            notes = "Test Delivery",
            deliveryCharges = 50.0,
            immediatePaymentAmount = 630.0, // Full payment (480 + 100 + 50 = 630)
            paymentMethod = PaymentMethod.CASH
        )

        assertNotNull(delNum)
        assertTrue(delNum.startsWith("DEL-"))

        val savedDel = repository.deliveries.value.find { it.customerId == custId }
        assertNotNull(savedDel)
        assertEquals(580.0, savedDel!!.subtotal, 0.01)
        assertEquals(630.0, savedDel.totalAmount, 0.01)
        assertEquals(6, savedDel.totalFullDelivered)
        // 19L is multiple-use (4 collected); 6L is single-use (emptyCollected sanitized to 0)
        assertEquals(4, savedDel.totalEmptyCollected)
    }

    @Test
    fun testDarkModeToggle() {
        val initial = repository.isDarkMode.value
        repository.toggleDarkMode()
        assertEquals(!initial, repository.isDarkMode.value)
        repository.setDarkMode(true)
        assertTrue(repository.isDarkMode.value)
        repository.setDarkMode(false)
        assertFalse(repository.isDarkMode.value)
    }

    @Test
    fun testAdminDeleteDeliveryRevertsBalanceAndInventory() {
        val custId = "cust_delete_test"
        val cust = Customer(
            id = custId,
            customerCode = "DEL-TEST",
            name = "Test Delete Customer",
            phone = "0300-1112223",
            currentBalance = 0.0,
            emptyBottlesOwed = 0
        )
        repository.addCustomer(cust)

        val items = listOf(
            DeliveryItem(
                bottleSizeId = "size_19l",
                bottleName = "19 Litre Bottle",
                quantity = 5,
                unitPrice = 120.0,
                emptyCollected = 3,
                lineTotal = 600.0
            )
        )

        repository.recordDelivery(
            customerId = custId,
            items = items,
            assignedStaffName = "Rashid Minhas",
            notes = "Test",
            deliveryCharges = 0.0,
            immediatePaymentAmount = 200.0, // Customer owes 400
            paymentMethod = PaymentMethod.CASH
        )

        val recorded = repository.deliveries.value.find { it.customerId == custId }!!
        val custAfterDelivery = repository.getCustomerById(custId)!!
        assertEquals(400.0, custAfterDelivery.currentBalance, 0.01)
        assertEquals(2, custAfterDelivery.emptyBottlesOwed)

        // Non-admin attempt should be rejected
        repository.switchUserRole(UserRole.INCHARGE)
        val nonAdminDelete = repository.deleteDelivery(recorded.id)
        assertFalse(nonAdminDelete)
        assertNotNull(repository.deliveries.value.find { it.id == recorded.id })

        // Admin deletes delivery
        repository.switchUserRole(UserRole.ADMIN)
        val deleteSuccess = repository.deleteDelivery(recorded.id)
        assertTrue(deleteSuccess)

        // Customer balance and empty bottles must be restored back to 0
        val custAfterDelete = repository.getCustomerById(custId)!!
        assertEquals(0.0, custAfterDelete.currentBalance, 0.01)
        assertEquals(0, custAfterDelete.emptyBottlesOwed)
        assertNull(repository.deliveries.value.find { it.id == recorded.id })
    }

    @Test
    fun testAdminDeleteExpense() {
        val voucher = repository.addExpense(
            categoryId = "cat_test",
            categoryName = "Maintenance",
            description = "Filter replacement",
            amount = 1500.0,
            method = PaymentMethod.CASH,
            payee = "Technician",
            notes = "Test expense",
            type = ExpenseType.OPEX
        )

        val recorded = repository.expenses.value.find { it.voucherNumber == voucher }!!

        // Non-admin attempt should be rejected
        repository.switchUserRole(UserRole.INCHARGE)
        val nonAdminDelete = repository.deleteExpense(recorded.id)
        assertFalse(nonAdminDelete)
        assertNotNull(repository.expenses.value.find { it.id == recorded.id })

        // Admin deletes expense
        repository.switchUserRole(UserRole.ADMIN)
        val deleteSuccess = repository.deleteExpense(recorded.id)
        assertTrue(deleteSuccess)
        assertNull(repository.expenses.value.find { it.id == recorded.id })
    }

    @Test
    fun testAdminDeletePaymentRestoresBalance() {
        val custId = "cust_pay_del_test"
        val cust = Customer(
            id = custId,
            customerCode = "PAY-TEST",
            name = "Pay Delete Customer",
            phone = "0300-3334445",
            currentBalance = 1000.0
        )
        repository.addCustomer(cust)

        val payment = repository.recordPayment(
            customerId = custId,
            amount = 400.0,
            method = PaymentMethod.CASH,
            notes = "Cash received"
        )

        val custAfterPay = repository.getCustomerById(custId)!!
        assertEquals(600.0, custAfterPay.currentBalance, 0.01)

        // Admin deletes the payment
        val deleteSuccess = repository.deletePayment(payment.id)
        assertTrue(deleteSuccess)

        // Customer balance should be restored back to 1000
        val custAfterDelete = repository.getCustomerById(custId)!!
        assertEquals(1000.0, custAfterDelete.currentBalance, 0.01)
        assertNull(repository.payments.value.find { it.id == payment.id })
    }

    @Test
    fun testSupervisorRoleTitleAndDisplay() {
        assertEquals("Supervisor (سپروائزر)", UserRole.INCHARGE.displayName)
        assertEquals("سپروائزر", UserRole.INCHARGE.urduTitle)

        repository.switchUserRole(UserRole.INCHARGE)
        assertEquals(UserRole.INCHARGE, repository.currentUser.value.role)
        assertEquals("Rashid Minhas (Supervisor)", repository.currentUser.value.name)
    }

    @Test
    fun testViewOnlyRoleRestrictions() {
        repository.switchUserRole(UserRole.VIEW_ONLY)
        assertEquals(UserRole.VIEW_ONLY, repository.currentUser.value.role)

        // View-only cannot create customers
        val custResult = repository.addCustomer(
            Customer(id = "cust_vo_test", name = "VO Cust", phone = "0300-0000000")
        )
        assertEquals("", custResult)

        // View-only cannot record delivery
        val delResult = repository.recordDelivery(
            customerId = "cust_vo_test",
            items = listOf(DeliveryItem(bottleSizeId = "size_19l", bottleName = "19L", quantity = 1, unitPrice = 120.0, lineTotal = 120.0)),
            assignedStaffName = "Staff",
            notes = "Test"
        )
        assertTrue(delResult.contains("View-only"))

        // View-only cannot add expenses
        val expResult = repository.addExpense(
            categoryId = "cat_test",
            categoryName = "Misc",
            description = "Test expense",
            amount = 100.0,
            method = PaymentMethod.CASH,
            payee = "Vendor",
            notes = "None"
        )
        assertEquals("", expResult)
    }

    @Test
    fun testAuthorizedVsUnauthorizedGoogleLogin() {
        // 1. Primary Admin is always authorized with ADMIN role
        val adminAuth = repository.handleGoogleAuthSuccess(
            uid = "uid_admin_primary",
            email = "mrafiqarain1998@gmail.com",
            displayName = "Muhammad Rafiq"
        )
        assertTrue(adminAuth)
        assertEquals(UserRole.ADMIN, repository.currentUser.value.role)

        // 1b. Removed old admin email must be rejected
        val removedAdminAuth = repository.handleGoogleAuthSuccess(
            uid = "uid_removed_old",
            email = "muhammadrafiq.utopia@gmail.com",
            displayName = "Old Admin"
        )
        assertFalse(removedAdminAuth)

        // 2. Stranger/unapproved Google account must be rejected
        val strangerAuth = repository.handleGoogleAuthSuccess(
            uid = "uid_stranger_1",
            email = "hacker.unknown@gmail.com",
            displayName = "Attacker"
        )
        assertFalse(strangerAuth)

        // 3. Admin creates an approved incharge and viewer account
        repository.switchUserRole(UserRole.ADMIN)
        repository.createUserAccount(
            UserAccount(
                username = "incharge",
                name = "Rashid Minhas",
                phone = "0300-1234567",
                role = UserRole.INCHARGE,
                active = true
            )
        )
        repository.createUserAccount(
            UserAccount(
                username = "viewer",
                name = "Plant Auditor",
                phone = "0300-7654321",
                role = UserRole.VIEW_ONLY,
                active = true
            )
        )

        // Approved staff member matching an authorized username is granted their role
        val inchargeAuth = repository.handleGoogleAuthSuccess(
            uid = "uid_incharge_staff",
            email = "incharge@aabebaraan.com",
            displayName = "Rashid Minhas"
        )
        assertTrue(inchargeAuth)
        assertEquals(UserRole.INCHARGE, repository.currentUser.value.role)

        // 4. Approved viewer matching viewer username is granted VIEW_ONLY
        val viewerAuth = repository.handleGoogleAuthSuccess(
            uid = "uid_viewer_staff",
            email = "viewer@aabebaraan.com",
            displayName = "Plant Auditor"
        )
        assertTrue(viewerAuth)
        assertEquals(UserRole.VIEW_ONLY, repository.currentUser.value.role)
    }

    @Test
    fun testUnpaidDeliveryTurnsToPaidWhenPaymentReceivedOnDashboard() {
        val custId = "cust_unpaid_test"
        val cust = Customer(
            id = custId,
            name = "Tariq Mahmood",
            customerCode = "CUST-999",
            phone = "03009998877",
            area = "Model Town",
            currentBalance = 0.0
        )
        repository.addCustomer(cust)

        // Create an unpaid delivery with 5 bottles of 19L (Total Rs. 650, Unpaid)
        val delNumber = repository.recordDelivery(
            customerId = custId,
            items = listOf(
                DeliveryItem(bottleSizeId = "size_19l", bottleName = "19 Litre", quantity = 5, unitPrice = 130.0, emptyCollected = 5, lineTotal = 650.0)
            ),
            assignedStaffName = "Rider Ali",
            notes = "Test Unpaid Delivery",
            deliveryCharges = 0.0,
            immediatePaymentAmount = 0.0
        )
        val delivery = repository.deliveries.value.first { it.deliveryNumber == delNumber }
        val deliveryId = delivery.id

        // Verify initial state: delivery is Unpaid with 0 payment collected
        val initialDel = repository.deliveries.value.find { it.id == deliveryId }
        assertNotNull(initialDel)
        assertEquals(0.0, initialDel!!.paymentCollected, 0.01)
        assertEquals("Unpaid", initialDel.paymentMethod)

        // Customer receives cash on Dashboard (Receive Payment Screen with synthetic/invoice allocation)
        val payment = repository.recordPayment(
            customerId = custId,
            amount = 650.0,
            method = PaymentMethod.CASH,
            manualAllocations = mapOf("inv_$deliveryId" to 650.0),
            notes = "Full cash received on dashboard"
        )
        assertNotNull(payment)

        // Verify that delivery in repository now reflects Paid status!
        val updatedDel = repository.deliveries.value.find { it.id == deliveryId }
        assertNotNull(updatedDel)
        assertEquals(650.0, updatedDel!!.paymentCollected, 0.01)
        assertEquals("Cash (Naqd)", updatedDel.paymentMethod)
    }

    @Test
    fun testDirectMarkDeliveryAsPaid() {
        val custId = "cust_direct_pay_test"
        val cust = Customer(id = custId, name = "Direct Pay Customer", phone = "03112223344", currentBalance = 300.0)
        repository.addCustomer(cust)

        val delNumber = repository.recordDelivery(
            customerId = custId,
            items = listOf(
                DeliveryItem(bottleSizeId = "size_19l", bottleName = "19 Litre", quantity = 2, unitPrice = 150.0, emptyCollected = 2, lineTotal = 300.0)
            ),
            assignedStaffName = "Rider Ali",
            notes = "Direct Pay Delivery",
            immediatePaymentAmount = 0.0
        )
        val delivery = repository.deliveries.value.first { it.deliveryNumber == delNumber }
        val deliveryId = delivery.id

        // Direct receive payment from delivery card
        val success = repository.markDeliveryAsPaid(deliveryId = deliveryId, amountPaid = 300.0, method = PaymentMethod.CASH)
        assertTrue(success)

        val updatedDel = repository.deliveries.value.find { it.id == deliveryId }
        assertNotNull(updatedDel)
        assertEquals(300.0, updatedDel!!.paymentCollected, 0.01)
        assertEquals("Cash (Naqd)", updatedDel.paymentMethod)
    }

    @Test
    fun testAdminStockEditingAndReset() {
        repository.switchUserRole(UserRole.ADMIN)

        // 1. Admin directly edits bottle stock counts
        val editSuccess = repository.editBottleStock(
            bottleSizeId = "size_19l",
            full = 150,
            empty = 80,
            damaged = 5,
            withCustomers = 200,
            withStaff = 15
        )
        assertTrue(editSuccess)

        val inv = repository.inventory.value.find { it.bottleSizeId == "size_19l" }
        assertNotNull(inv)
        assertEquals(150, inv!!.atPlantFull)
        assertEquals(80, inv.atPlantEmpty)
        assertEquals(5, inv.damaged)
        assertEquals(200, inv.withCustomers)
        assertEquals(15, inv.withStaff)

        // 2. Admin deletes bottle stock record
        val deleteSuccess = repository.deleteBottleStock("size_19l")
        assertTrue(deleteSuccess)

        val deletedInv = repository.inventory.value.find { it.bottleSizeId == "size_19l" }
        assertNull("Deleted bottle stock should be removed from inventory", deletedInv)
    }

    @Test
    fun testDailyPlantExpenseRecording() {
        repository.switchUserRole(UserRole.ADMIN)

        val expense = repository.recordDailyPlantExpense(
            categoryId = "cat_electricity",
            categoryName = "Electricity Bill (WAPDA)",
            description = "Monthly Plant Electricity Bill",
            amount = 12500.0,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            payee = "IESCO / WAPDA",
            notes = "Paid via online banking"
        )

        assertNotNull(expense)
        assertEquals(12500.0, expense.amount, 0.01)
        assertEquals(PaymentMethod.BANK_TRANSFER, expense.paymentMethod)

        // Verify present in expenses flow
        val found = repository.expenses.value.find { it.id == expense.id }
        assertNotNull(found)
        assertEquals(12500.0, found!!.amount, 0.01)
    }

    @Test
    fun testReturnBottlesFromCustomerAndInventoryAdjustment() {
        repository.switchUserRole(UserRole.ADMIN)

        // Register a customer with 10 empty bottles owed
        val custId = repository.addCustomer(
            Customer(
                name = "Babar Azam",
                phone = "0300-1112233",
                address = "Gulberg Greens, Islamabad",
                area = "Gulberg",
                type = CustomerType.HOME,
                paymentCycle = PaymentCycle.WEEKLY,
                paymentDueDate = "2026-11-15",
                openingBalance = 0.0,
                emptyBottlesOwed = 10,
                whatsapp = "03001112233"
            )
        )

        val custBefore = repository.getCustomerById(custId)
        assertNotNull(custBefore)
        assertEquals(10, custBefore!!.emptyBottlesOwed)

        // Customer returns 4 empty bottles
        repository.returnBottlesFromCustomer(
            customerId = custId,
            bottleSizeId = "size_19l",
            count = 4,
            isFull = false,
            reason = "Customer returned excess bottles"
        )

        // Customer should now owe 10 - 4 = 6 bottles
        val custAfter = repository.getCustomerById(custId)
        assertNotNull(custAfter)
        assertEquals(6, custAfter!!.emptyBottlesOwed)

        // Verify movement logged
        val movement = repository.bottleMovements.value.firstOrNull { it.customerId == custId }
        assertNotNull(movement)
        assertEquals(4, movement!!.quantity)
        assertEquals(BottleMovementType.RETURN_IN, movement.movementType)
    }

    @Test
    fun testAddAndDeleteExpenseCategory() {
        repository.switchUserRole(UserRole.ADMIN)

        repository.addExpenseCategory(
            name = "Plant Fuel & Diesel",
            urdu = "پلانٹ ڈیزل و فیول",
            type = ExpenseType.OPEX
        )

        val cat = repository.expenseCategories.value.find { it.name == "Plant Fuel & Diesel" }
        assertNotNull(cat)
        assertEquals("پلانٹ ڈیزل و فیول", cat!!.urduName)

        val deleted = repository.deleteExpenseCategory(cat.id)
        assertTrue(deleted)
        assertNull(repository.expenseCategories.value.find { it.id == cat.id })
    }

    @Test
    fun testSwitchUserByCredentialsAndInactiveProtection() {
        repository.switchUserRole(UserRole.ADMIN)
        repository.createUserAccount(
            UserAccount(
                id = "staff_switch_1",
                username = "driver_ali",
                password = "secret99",
                name = "Ali Raza",
                phone = "0300-1110000",
                role = UserRole.INCHARGE,
                active = true
            )
        )

        // Missing password or wrong password rejected
        assertFalse(repository.switchUserByCredentials("driver_ali", "").first)
        assertFalse(repository.switchUserByCredentials("driver_ali", "wrong").first)

        // Valid credentials switch to user's assigned role & name
        val okSwitch = repository.switchUserByCredentials("driver_ali", "secret99")
        assertTrue(okSwitch.first)
        assertEquals(UserRole.INCHARGE, repository.currentUser.value.role)
        assertEquals("Ali Raza", repository.currentUser.value.name)

        // Deactivate user as Admin -> switching into inactive user rejected
        val adminAcc = repository.userAccounts.value.first { it.id == "user_admin_01" }
        assertTrue(repository.switchUserByCredentials(adminAcc.username, adminAcc.password).first)
        val toggled = repository.toggleUserAccountActive("staff_switch_1")
        assertTrue(toggled.first)
        val inactiveSwitch = repository.switchUserByCredentials("driver_ali", "secret99")
        assertFalse(inactiveSwitch.first)
    }

    @Test
    fun testDeleteProtectionAndChangeOwnPassword() {
        repository.switchUserRole(UserRole.ADMIN)
        // Primary admin account cannot be deleted or deactivated
        assertFalse(repository.deleteUserAccount("user_admin_01").first)
        assertFalse(repository.toggleUserAccountActive("user_admin_01").first)

        // Staff account can be deleted cleanly
        repository.createUserAccount(
            UserAccount(
                id = "staff_del_1",
                username = "temp_staff",
                password = "pass123",
                name = "Temp Staff",
                role = UserRole.VIEW_ONLY,
                active = true
            )
        )
        assertTrue(repository.deleteUserAccount("staff_del_1").first)
        assertNull(repository.userAccounts.value.find { it.id == "staff_del_1" })

        // Change own password checks current password first
        val adminAcc = repository.userAccounts.value.first { it.id == "user_admin_01" }
        assertFalse(repository.changeOwnPassword("wrong_curr", "newpass12").first)
        assertTrue(repository.changeOwnPassword(adminAcc.password, "newAdmin99").first)
        assertTrue(repository.switchUserByCredentials(adminAcc.username, "newAdmin99").first)
    }

    @Test
    fun testAddBottleStockPaymentSourceAndMode() {
        repository.switchUserRole(UserRole.ADMIN)
        val initialExpCount = repository.expenses.value.size
        val initialEmpty = repository.inventory.value.find { it.bottleSizeId == "size_19l" }?.atPlantEmpty ?: 0
        val testDate = "2026-11-01"
        val baselineDrawer = repository.getDailyCashSummary(testDate)

        // Pay from Investment Account -> adds stock to Plant Empty AND logs Expense with PAY_FROM_INVESTMENT
        val invPurchase = repository.refillBottleStock(
            bottleSizeId = "size_19l",
            quantity = 10,
            purchaseDate = testDate,
            supplierName = "Plastics Co",
            unitPurchaseRate = 500.0,
            notes = "Investment batch",
            paymentSource = PaymentSource.PAY_FROM_INVESTMENT,
            paymentMode = StockPaymentMode.CASH
        )
        assertTrue(invPurchase.first)
        assertEquals(initialExpCount + 1, repository.expenses.value.size)
        val invExp = repository.expenses.value.first()
        assertEquals(5000.0, invExp.amount, 0.01)
        assertEquals(PaymentSource.PAY_FROM_INVESTMENT, invExp.paymentSource)
        val afterInvEmpty = repository.inventory.value.find { it.bottleSizeId == "size_19l" }?.atPlantEmpty ?: 0
        assertEquals(initialEmpty + 10, afterInvEmpty)
        // Daily Cash Drawer must NOT deduct Investment Account expenses
        assertEquals(baselineDrawer.cashExpenses, repository.getDailyCashSummary(testDate).cashExpenses, 0.01)

        // Pay from Sale -> adds stock to Plant Empty AND records CAPEX Expense with PAY_FROM_SALE
        val salePurchase = repository.refillBottleStock(
            bottleSizeId = "size_19l",
            quantity = 5,
            purchaseDate = testDate,
            supplierName = "Plastics Co",
            unitPurchaseRate = 500.0,
            notes = "From sale drawer",
            paymentSource = PaymentSource.PAY_FROM_SALE,
            paymentMode = StockPaymentMode.CASH
        )
        assertTrue(salePurchase.first)
        assertEquals(initialExpCount + 2, repository.expenses.value.size)
        val saleExp = repository.expenses.value.first()
        assertEquals(2500.0, saleExp.amount, 0.01)
        assertEquals(PaymentSource.PAY_FROM_SALE, saleExp.paymentSource)
        // Daily Cash Drawer deducts Cash + PAY_FROM_SALE expenses
        assertEquals(baselineDrawer.cashExpenses + 2500.0, repository.getDailyCashSummary(testDate).cashExpenses, 0.01)

        // Single-use Add Stock must ALSO increase Plant Empty (not Plant Full)
        val singleSize = repository.bottleSizes.value.first { it.usageType == BottleUsageType.ONE_TIME }
        val beforeSingleEmpty = repository.inventory.value.find { it.bottleSizeId == singleSize.id }?.atPlantEmpty ?: 0
        val beforeSingleFull = repository.inventory.value.find { it.bottleSizeId == singleSize.id }?.atPlantFull ?: 0
        val singlePurchase = repository.refillBottleStock(
            bottleSizeId = singleSize.id,
            quantity = 50,
            purchaseDate = testDate,
            supplierName = "PET Suppliers",
            unitPurchaseRate = 10.0,
            notes = "Single-use empty bottles",
            paymentSource = PaymentSource.PAY_FROM_SALE,
            paymentMode = StockPaymentMode.CASH
        )
        assertTrue(singlePurchase.first)
        val afterSingleInv = repository.inventory.value.find { it.bottleSizeId == singleSize.id }!!
        assertEquals(beforeSingleEmpty + 50, afterSingleInv.atPlantEmpty)
        assertEquals(beforeSingleFull, afterSingleInv.atPlantFull)
    }

    @Test
    fun testSingleUseRefillBatchAndIssueBounds() {
        repository.switchUserRole(UserRole.ADMIN)

        // Refill Batch strictly rejects Multiple Time bottle
        val rejectMulti = repository.refillSingleUseBatch("size_19l", 20, "2026-11-01", "Invalid")
        assertFalse(rejectMulti.first)

        val singleSize = repository.bottleSizes.value.first { it.usageType == BottleUsageType.ONE_TIME }
        // 1. Add 100 empty single-use bottles -> increases Plant Empty
        assertTrue(
            repository.refillBottleStock(
                bottleSizeId = singleSize.id,
                quantity = 100,
                purchaseDate = "2026-11-01",
                supplierName = "PET Co",
                unitPurchaseRate = 10.0,
                notes = "Empty single-use stock"
            ).first
        )
        val afterAddInv = repository.inventory.value.find { it.bottleSizeId == singleSize.id }!!

        // 2. Issuing more than Plant Empty to RO Plant is rejected
        val failTooMuchEmpty = repository.issueBottleStock(
            bottleSizeId = singleSize.id,
            target = BottleIssueTarget.INTERNAL_USE,
            customerId = null,
            quantity = afterAddInv.atPlantEmpty + 100,
            issueDate = "2026-11-01",
            deposit = 0.0,
            notes = "Overdraw"
        )
        assertFalse(failTooMuchEmpty.first)

        // 3. Issue 40 to RO Plant -> moves Plant Empty to In Use
        val okRoIssue = repository.issueBottleStock(
            bottleSizeId = singleSize.id,
            target = BottleIssueTarget.INTERNAL_USE,
            customerId = null,
            quantity = 40,
            issueDate = "2026-11-01",
            deposit = 0.0,
            notes = "RO Plant line"
        )
        assertTrue(okRoIssue.first)
        val afterRoInv = repository.inventory.value.find { it.bottleSizeId == singleSize.id }!!
        assertEquals(afterAddInv.atPlantEmpty - 40, afterRoInv.atPlantEmpty)
        assertEquals(afterAddInv.inUse + 40, afterRoInv.inUse)

        // 4. Refill Batch exceeding In Use must be rejected
        val overRefill = repository.refillSingleUseBatch(singleSize.id, afterRoInv.inUse + 1, "2026-11-01", "Too many")
        assertFalse(overRefill.first)

        // 5. Refill Batch within In Use moves In Use -> Plant Full
        val okBatch = repository.refillSingleUseBatch(singleSize.id, 30, "2026-11-01", "Morning fill")
        assertTrue(okBatch.first)
        val afterBatchInv = repository.inventory.value.find { it.bottleSizeId == singleSize.id }!!
        assertEquals(afterRoInv.inUse - 30, afterBatchInv.inUse)
        assertEquals(afterRoInv.atPlantFull + 30, afterBatchInv.atPlantFull)
    }

    @Test
    fun testBottleLossAndMaterialDamageMalfunction() {
        repository.switchUserRole(UserRole.ADMIN)
        repository.editBottleStock("size_19l", full = 20, empty = 10, damaged = 0)

        // Exceeding Plant Full stock rejected
        assertFalse(repository.recordBottleLossOrDamage(BottleLossSource.PLANT_FULL, "size_19l", 25, "2026-11-01", "Broken").first)
        // Valid loss updates Plant Full & Damaged
        assertTrue(repository.recordBottleLossOrDamage(BottleLossSource.PLANT_FULL, "size_19l", 3, "2026-11-01", "Dropped").first)
        val invAfter = repository.inventory.value.find { it.bottleSizeId == "size_19l" }!!
        assertEquals(17, invAfter.atPlantFull)
        assertEquals(3, invAfter.damaged)

        // Material Add + Damage + Malfunction bounds
        val matId = repository.addFactoryMaterial(
            FactoryMaterial(
                name = "RO Cartridge 20in",
                itemCategory = "Filter & Cartridge",
                uom = "Piece",
                openingStock = 10.0,
                currentStock = 10.0,
                purchaseRate = 450.0
            )
        )
        assertFalse(repository.recordMaterialDamageOrMalfunction(matId, MaterialActionType.DAMAGE, 15.0, "2026-11-01", "Too high").first)
        assertTrue(repository.recordMaterialDamageOrMalfunction(matId, MaterialActionType.DAMAGE, 2.0, "2026-11-01", "Cracked").first)
        assertTrue(repository.recordMaterialDamageOrMalfunction(matId, MaterialActionType.MALFUNCTION, 3.0, "2026-11-01", "Faulty valve").first)
        val updatedMat = repository.factoryMaterials.value.find { it.id == matId }!!
        assertEquals(5.0, updatedMat.currentStock, 0.01)
    }

    @Test
    fun testExpensePaymentSourceCategoriesAndPdf() {
        repository.switchUserRole(UserRole.ADMIN)

        // Verify all 13 requested categories exist in SampleData
        val catNames = com.example.data.SampleData.expenseCategories.map { it.name.lowercase() }
        listOf(
            "asset / machinery",
            "entertainment",
            "fuel / rent",
            "electricity bill",
            "plant maintenance",
            "vehicle maintenance",
            "filter and cartridge",
            "packing material",
            "chemical and mineral",
            "general",
            "staff salary",
            "raw material",
            "others"
        ).forEach { expected ->
            assertTrue("Missing category: $expected", catNames.contains(expected))
        }

        val testDate = "2026-11-12"
        val baselineSummary = repository.getDailyCashSummary(testDate)

        // Expense paid from Investment must NOT deduct from Sale Cash Drawer
        val invVoucher = repository.addExpense(
            categoryId = "cat_asset_machinery",
            categoryName = "Asset / Machinery",
            description = "New Booster Pump",
            amount = 5000.0,
            method = PaymentMethod.CASH,
            payee = "Al-Fatah Machinery",
            notes = "Paid by owner investment",
            type = ExpenseType.CAPEX,
            expenseDate = testDate,
            paymentSource = PaymentSource.PAY_FROM_INVESTMENT
        )
        val afterInvSummary = repository.getDailyCashSummary(testDate)
        assertEquals(baselineSummary.cashExpenses, afterInvSummary.cashExpenses, 0.01)

        // Expense paid from Sale MUST deduct from Sale Cash Drawer
        val saleVoucher = repository.addExpense(
            categoryId = "cat_fuel",
            categoryName = "Fuel / Rent",
            description = "Van Diesel",
            amount = 1200.0,
            method = PaymentMethod.CASH,
            payee = "PSO Pump",
            notes = "Daily route fuel",
            type = ExpenseType.OPEX,
            expenseDate = testDate,
            paymentSource = PaymentSource.PAY_FROM_SALE
        )
        val afterSaleSummary = repository.getDailyCashSummary(testDate)
        assertEquals(baselineSummary.cashExpenses + 1200.0, afterSaleSummary.cashExpenses, 0.01)

        // Non-admin cannot edit or delete expense; Admin can edit & delete
        val saleExp = repository.expenses.value.first { it.voucherNumber == saleVoucher }
        repository.switchUserRole(UserRole.INCHARGE)
        assertFalse(repository.updateExpense(saleExp.copy(amount = 1500.0)))
        assertFalse(repository.deleteExpense(saleExp.id))

        repository.switchUserRole(UserRole.ADMIN)
        assertTrue(repository.updateExpense(saleExp.copy(amount = 1500.0, payee = "Shell Pump")))
        val editedExp = repository.expenses.value.first { it.id == saleExp.id }
        assertEquals(1500.0, editedExp.amount, 0.01)
        assertEquals("Shell Pump", editedExp.payee)

        // Verify Expense voucher summary formatting for sharing
        val summary = com.example.util.PdfGenerator.buildExpenseShareSummary(editedExp)
        assertTrue(summary.contains(saleVoucher))
        assertTrue(summary.contains("Rs. 1500"))
        assertTrue(summary.contains("Shell Pump"))
        assertTrue(summary.contains("From Sale"))

        // Cleanup expense
        val invExp = repository.expenses.value.first { it.voucherNumber == invVoucher }
        assertTrue(repository.deleteExpense(editedExp.id))
        assertTrue(repository.deleteExpense(invExp.id))
    }

    @Test
    fun testDeliveryRatesInstallmentsAndAdminEdit() {
        repository.switchUserRole(UserRole.ADMIN)
        val custId = repository.addCustomer(
            Customer(
                id = "cust_del_test",
                name = "Test Delivery Customer",
                phone = "03001234567",
                area = "DHA Phase 5",
                emptyBottlesOwed = 2,
                currentBalance = 0.0
            )
        )
        val customer = repository.customers.value.first { it.id == custId }
        val initialBalance = customer.currentBalance
        val initialEmptiesOwed = customer.emptyBottlesOwed

        // 1. Assign custom rate for 19L & verify getCustomerRateForDate resolves it
        repository.setCustomerPrice(
            customerId = customer.id,
            bottleSizeId = "size_19l",
            customPrice = 160.0,
            bottleSizeName = "19 Liter",
            appliedDate = "2026-11-01"
        )
        assertEquals(160.0, repository.getCustomerRateForDate(customer.id, "size_19l", "2026-11-12"), 0.01)

        // 2. Record Credit delivery combining Multi-Time (19L) + One-Time (500ml) with Delivery Charges
        val items = listOf(
            DeliveryItem(
                bottleSizeId = "size_19l",
                bottleName = "19 Liter",
                quantity = 4,
                emptyCollected = 2,
                unitPrice = 160.0,
                lineTotal = 640.0
            ),
            DeliveryItem(
                bottleSizeId = "size_500ml",
                bottleName = "500 ml",
                quantity = 10,
                emptyCollected = 0, // Locked for single-use
                unitPrice = 30.0,
                lineTotal = 300.0
            )
        )
        val delNumber = repository.recordDelivery(
            customerId = customer.id,
            items = items,
            assignedStaffName = "Tariq",
            notes = "Credit test delivery",
            deliveryCharges = 60.0,
            immediatePaymentAmount = 0.0,
            paymentMethod = PaymentMethod.CASH,
            deliveryDate = "2026-11-10"
        )
        val delivery = repository.deliveries.value.first { it.deliveryNumber == delNumber }
        assertEquals(1000.0, delivery.totalAmount, 0.01)
        assertEquals("Unpaid", delivery.paymentMethod)
        assertEquals(0.0, delivery.paymentCollected, 0.01)

        // Customer empties owed increases ONLY by (4 - 2) = 2 (500ml one-time is NOT counted as owed)
        val custAfterDel = repository.customers.value.first { it.id == customer.id }
        assertEquals(initialEmptiesOwed + 2, custAfterDel.emptyBottlesOwed)
        assertEquals(initialBalance + 1000.0, custAfterDel.currentBalance, 0.01)

        // 3. Partial installment 1 (Cash Rs. 400) + Partial installment 2 (Online Rs. 600)
        assertTrue(repository.markDeliveryAsPaid(delivery.id, amountPaid = 400.0, method = PaymentMethod.CASH))
        val afterFirstPay = repository.deliveries.value.first { it.id == delivery.id }
        assertEquals(400.0, afterFirstPay.paymentCollected, 0.01)

        assertTrue(repository.markDeliveryAsPaid(delivery.id, amountPaid = 600.0, method = PaymentMethod.BANK_TRANSFER))
        val afterSecondPay = repository.deliveries.value.first { it.id == delivery.id }
        assertEquals(1000.0, afterSecondPay.paymentCollected, 0.01)

        val relatedPayments = com.example.util.PdfGenerator.findRelatedPaymentsForDelivery(afterSecondPay, repository.payments.value)
        assertEquals(2, relatedPayments.size)
        assertEquals(1000.0, relatedPayments.sumOf { it.amount }, 0.01)

        // 4. Non-admin cannot edit delivery; Admin can edit delivery date, qty & charges cleanly
        val editedItems = listOf(
            items[0].copy(quantity = 5, emptyCollected = 3, lineTotal = 800.0),
            items[1]
        )
        val editedPayload = afterSecondPay.copy(
            date = "2026-11-11",
            items = editedItems,
            totalFullDelivered = 15,
            totalEmptyCollected = 3,
            subtotal = 1100.0,
            deliveryCharges = 100.0,
            totalAmount = 1200.0
        )

        repository.switchUserRole(UserRole.INCHARGE)
        assertFalse(repository.updateDelivery(editedPayload))

        repository.switchUserRole(UserRole.ADMIN)
        assertTrue(repository.updateDelivery(editedPayload))

        val updatedDel = repository.deliveries.value.first { it.id == delivery.id }
        assertEquals("2026-11-11", updatedDel.date)
        assertEquals(1200.0, updatedDel.totalAmount, 0.01)

        val updatedInvoice = repository.invoices.value.first { it.id == updatedDel.invoiceId }
        assertEquals("2026-11-11", updatedInvoice.date)
        assertEquals(1200.0, updatedInvoice.totalAmount, 0.01)
        assertEquals(200.0, updatedInvoice.balanceDue, 0.01)

        // Clean up test delivery
        assertTrue(repository.deleteDelivery(delivery.id))
    }

    @Test
    fun testPerSizeBottleAllocationIssueReturnAndDamage() {
        repository.switchUserRole(UserRole.ADMIN)
        val custId = repository.addCustomer(
            Customer(
                id = "cust_alloc_sizes",
                name = "Multi Size Allocation Customer",
                phone = "03005556677",
                area = "Bahria Town"
            )
        )

        // Seed Plant Empty stock before issuing multiple-use bottles to customer
        repository.editBottleStock("size_19l", full = 10, empty = 20, damaged = 0)
        repository.editBottleStock("size_12l", full = 10, empty = 20, damaged = 0)

        // Issue 6 bottles of 19L and 4 bottles of 12.5L
        repository.issueBottlesToCustomer(custId, "size_19l", 6, 3000.0)
        repository.issueBottlesToCustomer(custId, "size_12l", 4, 1600.0)

        // Return 2 bottles of 19L and 1 bottle of 12.5L
        repository.returnBottlesFromCustomer(custId, "size_19l", 2, isFull = false, reason = "Routine return")
        repository.returnBottlesFromCustomer(custId, "size_12l", 1, isFull = false, reason = "Routine return")

        // Mark 1 damaged on 19L
        repository.recordCustomerDamage(custId, "size_19l", 1, "Cracked neck")

        val alloc19 = repository.customerAllocations.value.first { it.customerId == custId && it.bottleSizeId == "size_19l" }
        val alloc12 = repository.customerAllocations.value.first { it.customerId == custId && it.bottleSizeId == "size_12l" }

        assertEquals(6, alloc19.totalIssued)
        assertEquals(2, alloc19.returnedCount)
        assertEquals(1, alloc19.damagedCount)
        assertEquals(3, alloc19.emptyHeld)

        assertEquals(4, alloc12.totalIssued)
        assertEquals(1, alloc12.returnedCount)
        assertEquals(0, alloc12.damagedCount)
        assertEquals(3, alloc12.emptyHeld)

        val updatedCust = repository.getCustomerById(custId)!!
        assertEquals(6, updatedCust.emptyBottlesOwed)
    }

    @Test
    fun testRefillBatchPacketConversionExampleScenario() {
        repository.switchUserRole(UserRole.ADMIN)
        val size500 = repository.bottleSizes.value.firstOrNull { it.id == "size_500ml" }
            ?: repository.bottleSizes.value.first { it.usageType == BottleUsageType.ONE_TIME }

        // Start clean on 500ml
        repository.editBottleStock(size500.id, full = 0, empty = 0, damaged = 0, withCustomers = 0, withStaff = 0)

        // Step 1: Add 200 bottles -> all go to Plant Empty
        assertTrue(
            repository.refillBottleStock(
                bottleSizeId = size500.id,
                quantity = 200,
                purchaseDate = "2026-11-14",
                supplierName = "PET Factory",
                unitPurchaseRate = 8.0,
                notes = "Initial 200 empty"
            ).first
        )
        val afterAdd = repository.inventory.value.first { it.bottleSizeId == size500.id }
        assertEquals(200, afterAdd.atPlantEmpty)
        assertEquals(0, afterAdd.inUse)
        assertEquals(0, afterAdd.atPlantFull)

        // Step 2: Issue 125 bottles to RO Plant -> Plant Empty = 75, In Use = 125
        assertTrue(
            repository.issueBottleStock(
                bottleSizeId = size500.id,
                target = BottleIssueTarget.INTERNAL_USE,
                customerId = null,
                quantity = 125,
                issueDate = "2026-11-14",
                deposit = 0.0,
                notes = "Shift 1 bottling"
            ).first
        )
        val afterRo = repository.inventory.value.first { it.bottleSizeId == size500.id }
        assertEquals(75, afterRo.atPlantEmpty)
        assertEquals(125, afterRo.inUse)

        // Validation guards
        assertFalse(repository.refillSingleUseBatchWithConversion(size500.id, 130, 12, 0, "2026-11-14", "").first) // exceeds In Use
        assertFalse(repository.refillSingleUseBatchWithConversion(size500.id, 125, 12, 126, "2026-11-14", "").first) // damaged > refill
        assertFalse(repository.refillSingleUseBatchWithConversion(size500.id, 125, 10, 5, "2026-11-14", "").first) // invalid packet size

        // Step 3: Exact prompt example -> Refill 125, Packet Size 12, Damaged 5
        // Expected: In Use = 0, Usable = 120, Complete Packets = 10, Remaining Loose = 0, Plant Full = 120, Damaged = 5
        val convResult = repository.refillSingleUseBatchWithConversion(
            bottleSizeId = size500.id,
            refillQuantity = 125,
            packetSize = 12,
            damagedQuantity = 5,
            date = "2026-11-14",
            notes = "Batch #1"
        )
        assertTrue(convResult.first)

        val afterConv = repository.inventory.value.first { it.bottleSizeId == size500.id }
        assertEquals(75, afterConv.atPlantEmpty)
        assertEquals(0, afterConv.inUse)
        assertEquals(120, afterConv.atPlantFull)
        assertEquals(10, afterConv.fullPackets)
        assertEquals(0, afterConv.fullLooseBottles)
        assertEquals(5, afterConv.damaged)

        val latestRec = repository.bottleRefills.value.first()
        assertEquals(120, latestRec.quantityFilled)
        assertEquals(12, latestRec.packetSize)
        assertEquals(10, latestRec.completePackets)
        assertEquals(120, latestRec.packedBottles)
        assertEquals(0, latestRec.looseBottles)
        assertEquals(5, latestRec.damagedBottles)

        // Step 4: Test remaining loose bottles case (issue remaining 75, refill 75 with 24-pack & 1 damaged -> 74 usable = 3 packets + 2 loose)
        assertTrue(repository.issueBottleStock(size500.id, BottleIssueTarget.INTERNAL_USE, null, 75, "2026-11-14", 0.0, "Shift 2").first)
        assertTrue(repository.refillSingleUseBatchWithConversion(size500.id, 75, 24, 1, "2026-11-14", "Batch #2").first)
        val afterSecond = repository.inventory.value.first { it.bottleSizeId == size500.id }
        assertEquals(0, afterSecond.inUse)
        assertEquals(120 + 74, afterSecond.atPlantFull)
        assertEquals(10 + 3, afterSecond.fullPackets)
        assertEquals(2, afterSecond.fullLooseBottles)
        assertEquals(5 + 1, afterSecond.damaged)
    }

    @Test
    fun testCustomerIssueMultipleUseOnlyZeroRatesAndSingleUseEmptyLock() {
        repository.switchUserRole(UserRole.ADMIN)
        val custId = repository.addCustomer(
            Customer(
                id = "cust_rules_test",
                name = "Rules Check Customer",
                phone = "03001112244",
                area = "Johar Town"
            )
        )

        // 1. Default bottle rates must be ZERO for unassigned sizes
        val sizes = repository.bottleSizes.value.filter { it.active }
        sizes.forEach { sz ->
            assertEquals(0.0, repository.getCustomerPrice(custId, sz.id), 0.01)
            assertEquals(0.0, repository.getCustomerRateForDate(custId, sz.id, "2026-11-14"), 0.01)
        }

        // 2. Single-use bottle cannot be issued to customer
        val oneTime = repository.bottleSizes.value.first { it.usageType == BottleUsageType.ONE_TIME }
        repository.refillBottleStock(oneTime.id, 20, "2026-11-14", "S", 10.0, "")
        val singleCustIssue = repository.issueBottleStock(
            bottleSizeId = oneTime.id,
            target = BottleIssueTarget.CUSTOMER,
            customerId = custId,
            quantity = 2,
            issueDate = "2026-11-14",
            deposit = 0.0,
            notes = ""
        )
        assertFalse(singleCustIssue.first)

        // 3. Multiple-use customer issue deducts strictly from Plant Empty and updates Customer allocation
        repository.refillBottleStock("size_19l", 10, "2026-11-14", "Plastic Co", 500.0, "Seed 19L empty")
        val before19Empty = repository.inventory.value.first { it.bottleSizeId == "size_19l" }.atPlantEmpty
        val before19Full = repository.inventory.value.first { it.bottleSizeId == "size_19l" }.atPlantFull
        val multiCustIssue = repository.issueBottleStock(
            bottleSizeId = "size_19l",
            target = BottleIssueTarget.CUSTOMER,
            customerId = custId,
            quantity = 3,
            issueDate = "2026-11-14",
            deposit = 1500.0,
            notes = "Initial bottles"
        )
        assertTrue(multiCustIssue.first)
        val after19Inv = repository.inventory.value.first { it.bottleSizeId == "size_19l" }
        assertEquals(before19Empty - 3, after19Inv.atPlantEmpty)
        assertEquals(before19Full, after19Inv.atPlantFull)
        val alloc19 = repository.customerAllocations.value.first { it.customerId == custId && it.bottleSizeId == "size_19l" }
        assertEquals(3, alloc19.emptyHeld)

        // 4. Single-use delivery forces emptyCollected = 0 even if non-zero passed, and deducts Plant Full
        repository.editBottleStock(oneTime.id, full = 30, empty = 0, damaged = 0)
        repository.recordDelivery(
            customerId = custId,
            items = listOf(
                DeliveryItem(
                    bottleSizeId = oneTime.id,
                    bottleName = oneTime.name,
                    quantity = 6,
                    emptyCollected = 5, // Must be sanitized to 0!
                    unitPrice = 40.0,
                    lineTotal = 240.0
                )
            ),
            assignedStaffName = "Rider",
            notes = "Single-use delivery",
            deliveryDate = "2026-11-14"
        )
        val del = repository.deliveries.value.first { it.customerId == custId }
        assertEquals(0, del.totalEmptyCollected)
        assertEquals(0, del.items.first().emptyCollected)
        assertEquals(24, repository.inventory.value.first { it.bottleSizeId == oneTime.id }.atPlantFull)
        // Customer empties owed remains 3 from 19L issue (unaffected by single-use delivery)
        assertEquals(3, repository.getCustomerById(custId)!!.emptyBottlesOwed)
    }

    @Test
    fun testStockAndCostAdminOnlyEditAndDelete() {
        repository.switchUserRole(UserRole.ADMIN)
        val tempSizeId = repository.addNewBottleSize(
            name = "Test Size Admin Guard",
            purchaseRate = 100.0,
            packQty = 1,
            uom = "Bottle",
            openingStock = 0,
            usageType = BottleUsageType.MULTIPLE_TIME,
            defaultRefillPrice = 0.0,
            salePrice = 0.0,
            capacityLitres = 5.0,
            depositAmount = 200.0
        )
        val tempMatId = repository.addFactoryMaterial(
            FactoryMaterial(
                name = "Test Guard Material",
                itemCategory = "General",
                uom = "Piece",
                openingStock = 0.0,
                purchaseRate = 50.0
            )
        )

        // Non-Admin (Supervisor / View-Only) must be blocked from editing or deleting Stock & Cost items
        listOf(UserRole.INCHARGE, UserRole.VIEW_ONLY).forEach { nonAdmin ->
            repository.switchUserRole(nonAdmin)
            val editSizeRes = repository.updateBottleSizeDetails(tempSizeId, "Hacked Name", 5.0, BottleUsageType.MULTIPLE_TIME, 10.0, 100.0, 200.0, "Bottle")
            assertFalse(editSizeRes.first)
            val delSizeRes = repository.deleteOrArchiveBottleSize(tempSizeId)
            assertFalse(delSizeRes.first)
            val forceDelRes = repository.forceDeleteBottleSize(tempSizeId)
            assertFalse(forceDelRes.first)

            val matBefore = repository.factoryMaterials.value.first { it.id == tempMatId }
            repository.updateFactoryMaterial(matBefore.copy(name = "Hacked Mat"))
            assertEquals("Test Guard Material", repository.factoryMaterials.value.first { it.id == tempMatId }.name)
            val delMatRes = repository.deleteOrArchiveFactoryMaterial(tempMatId)
            assertFalse(delMatRes.first)
        }

        // Admin can edit and delete cleanly
        repository.switchUserRole(UserRole.ADMIN)
        assertTrue(repository.updateBottleSizeDetails(tempSizeId, "Verified Admin Edit", 5.0, BottleUsageType.MULTIPLE_TIME, 20.0, 100.0, 200.0, "Bottle").first)
        assertEquals("Verified Admin Edit", repository.bottleSizes.value.first { it.id == tempSizeId }.name)
        assertTrue(repository.deleteOrArchiveBottleSize(tempSizeId).first)

        val matCurrent = repository.factoryMaterials.value.first { it.id == tempMatId }
        repository.updateFactoryMaterial(matCurrent.copy(name = "Verified Admin Material"))
        assertEquals("Verified Admin Material", repository.factoryMaterials.value.first { it.id == tempMatId }.name)
        assertTrue(repository.deleteOrArchiveFactoryMaterial(tempMatId).first)
    }

    @Test
    fun testDualSourceExpensesSummedInTotalsAndReports() {
        repository.switchUserRole(UserRole.ADMIN)
        val date = "2026-11-14"
        val baselineCash = repository.getDailyCashSummary(date).cashExpenses

        repository.addExpense(
            categoryId = "cat_fuel",
            categoryName = "Fuel / Rent",
            description = "Fuel from sale",
            amount = 800.0,
            method = PaymentMethod.CASH,
            payee = "PSO",
            notes = "",
            type = ExpenseType.OPEX,
            expenseDate = date,
            paymentSource = PaymentSource.PAY_FROM_SALE
        )
        repository.addExpense(
            categoryId = "cat_asset_machinery",
            categoryName = "Asset / Machinery",
            description = "Pump from investment",
            amount = 3200.0,
            method = PaymentMethod.CASH,
            payee = "Pump Co",
            notes = "",
            type = ExpenseType.CAPEX,
            expenseDate = date,
            paymentSource = PaymentSource.PAY_FROM_INVESTMENT
        )

        val dayExpenses = repository.expenses.value.filter { it.date == date }
        assertEquals(2, dayExpenses.size)
        assertEquals(4000.0, dayExpenses.sumOf { it.amount }, 0.01)
        assertEquals(800.0, dayExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_SALE }.sumOf { it.amount }, 0.01)
        assertEquals(3200.0, dayExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_INVESTMENT }.sumOf { it.amount }, 0.01)

        // Cash drawer continues to deduct strictly PAY_FROM_SALE cash expenses
        assertEquals(baselineCash + 800.0, repository.getDailyCashSummary(date).cashExpenses, 0.01)
    }

    @Test
    fun testBottleStockAndMaterialCostCategoryOpexVsCapex() {
        repository.switchUserRole(UserRole.ADMIN)
        val date = "2026-11-15"

        // 1. Multiple-use bottle purchase must log as CAPEX
        val singleSize = repository.bottleSizes.value.first { it.usageType == BottleUsageType.ONE_TIME }
        val multiSize = repository.bottleSizes.value.first { it.usageType == BottleUsageType.MULTIPLE_TIME }

        assertTrue(
            repository.refillBottleStock(
                bottleSizeId = multiSize.id,
                quantity = 10,
                purchaseDate = date,
                supplierName = "Petra Multi",
                unitPurchaseRate = 500.0,
                notes = "Multi purchase"
            ).first
        )
        val multiExp = repository.expenses.value.first()
        assertEquals(ExpenseType.CAPEX, multiExp.expenseType)

        // 2. Single-use bottle purchase must log as OPEX
        assertTrue(
            repository.refillBottleStock(
                bottleSizeId = singleSize.id,
                quantity = 100,
                purchaseDate = date,
                supplierName = "Single PET",
                unitPurchaseRate = 10.0,
                notes = "Single purchase"
            ).first
        )
        val singleExp = repository.expenses.value.first()
        assertEquals(ExpenseType.OPEX, singleExp.expenseType)

        // 3. FactoryMaterial purchase inherits item's OPEX vs CAPEX category
        val capexMatId = repository.addFactoryMaterial(
            FactoryMaterial(
                name = "High Pressure Membrane",
                itemCategory = "Asset / Machinery",
                costType = ExpenseType.CAPEX,
                uom = "Piece",
                openingStock = 0.0,
                purchaseRate = 15000.0
            )
        )
        assertTrue(
            repository.refillFactoryMaterial(
                materialId = capexMatId,
                quantity = 2.0,
                purchaseRate = 15000.0,
                date = date,
                supplierName = "RO Mach",
                invoiceNo = "INV-CAP-1",
                notes = "Membrane purchase"
            ).first
        )
        assertEquals(ExpenseType.CAPEX, repository.expenses.value.first().expenseType)

        val opexMatId = repository.addFactoryMaterial(
            FactoryMaterial(
                name = "Anti-Scalant Chemical",
                itemCategory = "Chemical & Mineral",
                costType = ExpenseType.OPEX,
                uom = "Litre",
                openingStock = 0.0,
                purchaseRate = 800.0
            )
        )
        assertTrue(
            repository.refillFactoryMaterial(
                materialId = opexMatId,
                quantity = 5.0,
                purchaseRate = 800.0,
                date = date,
                supplierName = "Chem Co",
                invoiceNo = "INV-OP-1",
                notes = "Chem purchase"
            ).first
        )
        assertEquals(ExpenseType.OPEX, repository.expenses.value.first().expenseType)
    }

    @Test
    fun testCustomBottlePacketCartonConversionLooseReturnAndDelivery() {
        repository.switchUserRole(UserRole.ADMIN)

        // 1. Add custom single-use bottle "300Ml Water Bottle"
        val bottleId = repository.addNewBottleSize(
            name = "300Ml Water Bottle",
            purchaseRate = 7.0,
            packQty = 1,
            uom = "Bottle",
            openingStock = 0,
            usageType = BottleUsageType.ONE_TIME,
            defaultRefillPrice = 0.0,
            salePrice = 20.0,
            capacityLitres = 0.3,
            depositAmount = 0.0
        )

        // 2. Purchase 100 bottles -> OPEX expense & Plant Empty = 100
        assertTrue(
            repository.refillBottleStock(
                bottleSizeId = bottleId,
                quantity = 100,
                purchaseDate = "2026-11-16",
                supplierName = "PET Co",
                unitPurchaseRate = 7.0,
                notes = "Initial 300ml"
            ).first
        )
        assertEquals(ExpenseType.OPEX, repository.expenses.value.first().expenseType)
        assertEquals(100, repository.inventory.value.first { it.bottleSizeId == bottleId }.atPlantEmpty)

        // 3. Issue 60 bottles to RO Plant -> Empty = 40, In Use = 60
        assertTrue(
            repository.issueBottleStock(
                bottleSizeId = bottleId,
                target = BottleIssueTarget.INTERNAL_USE,
                customerId = null,
                quantity = 60,
                issueDate = "2026-11-16",
                deposit = 0.0,
                notes = "RO line"
            ).first
        )

        // 4. Convert 50 bottles into 12-Bottle PACKET with 0 damaged and returnLooseToInUse = true
        // 50 / 12 = 4 complete packets (48 bottles in Plant Full), 2 loose returned to In Use!
        // Remaining In Use = 60 - 50 + 2 = 12.
        val convPacket = repository.refillSingleUseBatchWithConversion(
            bottleSizeId = bottleId,
            refillQuantity = 50,
            packetSize = 12,
            damagedQuantity = 0,
            date = "2026-11-16",
            notes = "Packet run",
            containerType = PackContainerType.PACKET,
            returnLooseToInUse = true
        )
        assertTrue(convPacket.first)

        val invAfterPacket = repository.inventory.value.first { it.bottleSizeId == bottleId }
        assertEquals(12, invAfterPacket.inUse)
        assertEquals(48, invAfterPacket.atPlantFull)
        assertEquals(4, invAfterPacket.fullPackets)
        assertEquals(0, invAfterPacket.fullLooseBottles)
        val pktVariant = invAfterPacket.packVariants.first { it.variantId == makePackVariantId(bottleId, 12, PackContainerType.PACKET) }
        assertEquals("300Ml Water Bottle (12 Bottle Packet)", pktVariant.displayName)
        assertEquals(4, pktVariant.packetCount)

        // 5. Convert remaining 12 In Use into 6-Bottle CARTON -> produces 2 cartons coexisting separately!
        val convCarton = repository.refillSingleUseBatchWithConversion(
            bottleSizeId = bottleId,
            refillQuantity = 12,
            packetSize = 6,
            damagedQuantity = 0,
            date = "2026-11-16",
            notes = "Carton run",
            containerType = PackContainerType.CARTON,
            returnLooseToInUse = true
        )
        assertTrue(convCarton.first)

        val invAfterBoth = repository.inventory.value.first { it.bottleSizeId == bottleId }
        assertEquals(0, invAfterBoth.inUse)
        assertEquals(60, invAfterBoth.atPlantFull)
        assertEquals(6, invAfterBoth.fullPackets)
        assertEquals(2, invAfterBoth.packVariants.size)
        val cartonVariant = invAfterBoth.packVariants.first { it.variantId == makePackVariantId(bottleId, 6, PackContainerType.CARTON) }
        assertEquals("300Ml Water Bottle (6 Bottle Carton)", cartonVariant.displayName)
        assertEquals(2, cartonVariant.packetCount)

        // 6. Deliver 2 packets of "300Ml Water Bottle (12 Bottle Packet)" -> deducts 2 packets (24 bottles)
        val custId = repository.addCustomer(
            Customer(
                id = "cust_variant_del",
                name = "Mart Variant",
                phone = "03007778899",
                area = "Model Town"
            )
        )
        val delNum = repository.recordDelivery(
            customerId = custId,
            items = listOf(
                DeliveryItem(
                    bottleSizeId = bottleId,
                    variantId = pktVariant.variantId,
                    packSize = 12,
                    bottleName = pktVariant.displayName,
                    quantity = 2,
                    unitPrice = 240.0,
                    emptyCollected = 0,
                    lineTotal = 480.0
                )
            ),
            assignedStaffName = "Rider",
            notes = "Pack variant delivery",
            deliveryDate = "2026-11-16"
        )
        val savedDel = repository.deliveries.value.first { it.deliveryNumber == delNum }
        assertEquals("300Ml Water Bottle (12 Bottle Packet)", savedDel.items.first().bottleName)

        val invAfterDel = repository.inventory.value.first { it.bottleSizeId == bottleId }
        assertEquals(2, invAfterDel.packVariants.first { it.variantId == pktVariant.variantId }.packetCount)
        assertEquals(2, invAfterDel.packVariants.first { it.variantId == cartonVariant.variantId }.packetCount)
        assertEquals(4, invAfterDel.fullPackets)
        assertEquals(36, invAfterDel.atPlantFull)

        // 7. Deleting the delivery restores variant packetCount, fullPackets, and atPlantFull cleanly
        assertTrue(repository.deleteDelivery(savedDel.id))
        val invRestored = repository.inventory.value.first { it.bottleSizeId == bottleId }
        assertEquals(4, invRestored.packVariants.first { it.variantId == pktVariant.variantId }.packetCount)
        assertEquals(6, invRestored.fullPackets)
        assertEquals(60, invRestored.atPlantFull)
    }

    @Test
    fun testPdfLogoBitmapsAreCompactRgb565() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (headerLogo, watermarkLogo) = com.example.util.PdfGenerator.getCompactLogoBitmaps(context)
        assertNotNull("Header logo bitmap should decode", headerLogo)
        assertNotNull("Watermark logo bitmap should decode", watermarkLogo)

        val h = headerLogo!!
        val w = watermarkLogo!!
        assertEquals(android.graphics.Bitmap.Config.RGB_565, h.config)
        assertEquals(android.graphics.Bitmap.Config.RGB_565, w.config)
        assertTrue("Header bitmap width should be compact (<=72px)", h.width <= 72)
        assertTrue("Watermark bitmap width should be compact (<=110px)", w.width <= 110)

        val totalBitmapBytes = h.allocationByteCount + w.allocationByteCount
        assertTrue(
            "Combined uncompressed logo bitmaps should be < 40KB for tiny PDFs, was $totalBitmapBytes",
            totalBitmapBytes < 40 * 1024
        )
    }

    @Test
    fun testFirestoreSyncHelperAddsAccessMetadataAndNormalizesEmails() {
        val sampleCustomer = Customer(
            id = "cust_sync_1",
            name = "Cloud Sync Mart",
            phone = "03005551122",
            active = true
        )
        val custPayload = com.example.data.FirestoreSyncHelper.toFirestorePayload(
            data = sampleCustomer,
            ownerId = "owner_uid_1",
            memberUids = listOf("staff_uid_2"),
            memberEmails = listOf("MRafiqArain1998@gmail.com", "StaffMember.Test@gmail.com")
        )
        assertEquals("owner_uid_1", custPayload["ownerId"])
        @Suppress("UNCHECKED_CAST")
        val uids = custPayload["memberUids"] as List<String>
        assertTrue(uids.containsAll(listOf("owner_uid_1", "staff_uid_2")))
        @Suppress("UNCHECKED_CAST")
        val emails = custPayload["memberEmails"] as List<String>
        assertTrue(emails.contains("mrafiqarain1998@gmail.com"))
        assertTrue(emails.contains("staffmember.test@gmail.com"))
        assertNotNull(custPayload["updatedAt"])

        val expensePayload = com.example.data.FirestoreSyncHelper.toFirestorePayload(
            data = Expense(
                id = "exp_sync_1",
                date = "2026-11-16",
                expenseType = ExpenseType.OPEX,
                amount = 500.0,
                description = "Fuel"
            ),
            ownerId = "owner_uid_1",
            memberUids = listOf("owner_uid_1"),
            memberEmails = listOf("mrafiqarain1998@gmail.com")
        )
        assertEquals("OPEX", expensePayload["expenseType"])
        assertEquals("owner_uid_1", expensePayload["ownerId"])
    }

    @Test
    fun testFreshInstallStartsUnauthenticatedOnLoginScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Ensure a fresh install (cleared session files) never auto-logs in as Admin
        java.io.File(context.filesDir, "db_session_login.txt").delete()
        java.io.File(context.filesDir, "db_clean_slate_v2.txt").delete()
        java.io.File(context.filesDir, "db_clean_slate_v3_wiped.txt").delete()
        WaterPlantRepository.resetInstanceForTesting()

        val freshRepo = WaterPlantRepository.getInstance(context)
        assertFalse("Fresh install must start on Login page (not auto-logged in)", freshRepo.isUserLoggedIn())
    }

    @Test
    fun testCreatedUserPersistsAcrossAppRestartAndLogsInWithOutcome() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Clean fresh Run-1 install
        java.io.File(context.filesDir, "db_clean_slate_v2.txt").delete()
        java.io.File(context.filesDir, "db_clean_slate_v3_wiped.txt").delete()
        java.io.File(context.filesDir, "db_user_accounts.json").delete()
        java.io.File(context.filesDir, "db_session_login.txt").delete()
        WaterPlantRepository.resetInstanceForTesting()

        val run1Repo = WaterPlantRepository.getInstance(context)
        assertFalse(run1Repo.isUserLoggedIn())
        assertNotNull(run1Repo.authenticate("admin", "admin123"))

        // Create a staff user in Run 1 with leading '@' & whitespace edge cases + Gmail
        val staffId = run1Repo.createUserAccount(
            UserAccount(
                username = " @RashidStaff ",
                password = " pass9988 ",
                name = "Rashid Khan",
                phone = "03001234567",
                googleEmail = "Rashid.Khan@Gmail.com",
                role = UserRole.INCHARGE,
                active = true
            )
        )
        assertTrue(staffId.isNotBlank())
        run1Repo.logout()
        assertFalse(run1Repo.isUserLoggedIn())

        // Simulate Run 2 (app restart / APK reinstall over Run 1): Run 2 MUST NOT wipe user accounts!
        WaterPlantRepository.resetInstanceForTesting()
        val run2Repo = WaterPlantRepository.getInstance(context)
        assertFalse("After logout, restart must still require login", run2Repo.isUserLoggedIn())

        val persistedStaff = run2Repo.userAccounts.value.find { it.id == staffId }
        assertNotNull("Staff created in Run 1 must survive Run 2 initialization", persistedStaff)
        assertEquals("rashidstaff", persistedStaff?.username)
        assertEquals("rashid.khan@gmail.com", persistedStaff?.googleEmail)

        // Unknown user on unsynced offline device reports CLOUD_SYNC_REQUIRED instead of false wrong-password
        val unknownOutcome = run2Repo.authenticateWithOutcome("someone_else", "abc")
        assertEquals(LoginOutcome.CLOUD_SYNC_REQUIRED, unknownOutcome.first)

        // Wrong password for existing user reports INVALID_CREDENTIALS
        val wrongPwdOutcome = run2Repo.authenticateWithOutcome("rashidstaff", "wrong")
        assertEquals(LoginOutcome.INVALID_CREDENTIALS, wrongPwdOutcome.first)

        // Correct credentials (even with '@' prefix or spaces) succeed with exact role
        val okOutcome = run2Repo.authenticateWithOutcome("@RashidStaff", "pass9988")
        assertEquals(LoginOutcome.SUCCESS, okOutcome.first)
        assertEquals(UserRole.INCHARGE, run2Repo.currentUser.value.role)
        assertTrue(run2Repo.isUserLoggedIn())

        // Inactive user returns INACTIVE_ACCOUNT
        assertNotNull(run2Repo.authenticate("admin", "admin123"))
        assertTrue(run2Repo.toggleUserAccountActive(staffId).first)
        val inactiveOutcome = run2Repo.authenticateWithOutcome("rashidstaff", "pass9988")
        assertEquals(LoginOutcome.INACTIVE_ACCOUNT, inactiveOutcome.first)
    }

    @Test
    fun testSingleBottlePackFormattingOmitsPacketLabel() {
        val fullName1 = formatPackVariantName("500ml Bottle", packSize = 1, containerType = PackContainerType.PACKET)
        assertEquals("500ml Bottle (1 Bottle)", fullName1)
        assertFalse(fullName1.contains("Packet", ignoreCase = true))

        val short1 = formatPackVariantShortLabel("500ml Bottle", capacityLitres = 0.5, packSize = 1, containerType = PackContainerType.PACKET)
        assertEquals("500ml (1B)", short1)
        assertFalse(short1.contains("Pkt", ignoreCase = true))

        val fullName12 = formatPackVariantName("500ml Bottle", packSize = 12, containerType = PackContainerType.PACKET)
        assertEquals("500ml Bottle (12 Bottle Packet)", fullName12)

        val short12 = formatPackVariantShortLabel("500ml Bottle", capacityLitres = 0.5, packSize = 12, containerType = PackContainerType.PACKET)
        assertEquals("500ml (12B Pkt)", short12)
    }

    @Test
    fun testCustomerDamageValidatesSizeSpecificHeldCountNotAggregate() {
        val custId = repository.addCustomer(
            Customer(name = "Size Held Test Customer", phone = "03001112233", active = true)
        )
        // Purchase 10 empty 19L bottles so Plant Empty has stock to issue
        val stockRes = repository.refillBottleStock(
            bottleSizeId = "size_19l",
            quantity = 10,
            purchaseDate = "2026-11-18",
            supplierName = "Factory",
            unitPurchaseRate = 300.0,
            notes = "Test stock"
        )
        assertTrue(stockRes.first)
        // Customer holds 4 x 19L bottles and 0 x 12.5L bottles
        repository.issueBottlesToCustomer(custId, "size_19l", 4, 2000.0)
        assertEquals(
            4,
            repository.customerAllocations.value.find { it.customerId == custId && it.bottleSizeId == "size_19l" }?.emptyHeld
        )

        // Trying to damage 1 x 12.5L MUST fail (size-specific held is 0, not aggregate 4)
        val wrongSizeRes = repository.recordCustomerDamageStock(
            customerId = custId,
            bottleSizeId = "size_12l",
            damagedQuantity = 1,
            date = "2026-11-18",
            remarks = "Cracked"
        )
        assertFalse("Damage on size with 0 held must be rejected", wrongSizeRes.first)
        assertTrue(wrongSizeRes.second.contains("(0)"))

        // Trying to damage 5 x 19L MUST fail (held is 4)
        val overRes = repository.recordCustomerDamageStock(
            customerId = custId,
            bottleSizeId = "size_19l",
            damagedQuantity = 5,
            date = "2026-11-18",
            remarks = "Cracked"
        )
        assertFalse(overRes.first)

        // Damaging 2 x 19L MUST succeed and decrement size_19l emptyHeld from 4 to 2
        val okRes = repository.recordCustomerDamageStock(
            customerId = custId,
            bottleSizeId = "size_19l",
            damagedQuantity = 2,
            date = "2026-11-18",
            remarks = "Dropped by customer"
        )
        assertTrue(okRes.first)
        val alloc19 = repository.customerAllocations.value.find { it.customerId == custId && it.bottleSizeId == "size_19l" }
        assertEquals(2, alloc19?.emptyHeld)
        assertEquals(2, alloc19?.damagedCount)
    }

    @Test
    fun testFactoryMaterialMinStockLevelAndCustomDatesPersist() {
        val matId = repository.addFactoryMaterial(
            FactoryMaterial(
                name = "500ml Shrink Roll",
                itemCategory = "Packing Material",
                usageType = MaterialUsageType.PRODUCTION_CONSUMABLE,
                uom = "Rolls",
                openingStock = 10.0,
                minStockLevel = 4.0,
                purchaseRate = 1200.0,
                supplierName = "Pak Packaging"
            )
        )
        val savedMat = repository.factoryMaterials.value.find { it.id == matId }
        assertNotNull(savedMat)
        assertEquals(4.0, savedMat!!.minStockLevel, 0.001)

        // Add stock with manual date
        val matRefill = repository.refillFactoryMaterial(
            materialId = matId,
            quantity = 5.0,
            purchaseRate = 1250.0,
            date = "2026-11-10",
            supplierName = "Pak Packaging",
            invoiceNo = "INV-88",
            notes = "Manual date refill"
        )
        assertTrue(matRefill.first)
        val refillTx = repository.materialTransactions.value.firstOrNull {
            it.materialId == matId && it.actionType == MaterialActionType.REFILL && it.date == "2026-11-10"
        }
        assertNotNull("Material refill must preserve manual purchase date", refillTx)

        // Add bottle stock with manual date
        val btlRefill = repository.refillBottleStock(
            bottleSizeId = "size_19l",
            quantity = 20,
            purchaseDate = "2026-11-05",
            supplierName = "Bottle Factory",
            unitPurchaseRate = 300.0,
            notes = "Manual bottle stock date"
        )
        assertTrue(btlRefill.first)
        val movement = repository.bottleMovements.value.firstOrNull {
            it.bottleSizeId == "size_19l" && it.date == "2026-11-05" && it.movementType == BottleMovementType.PURCHASE_NEW
        }
        assertNotNull("Bottle stock add must preserve manual purchase date", movement)
    }

    @Test
    fun testNonAdminPaymentStartsUnsettledUntilAdminMarksClear() {
        val custId = repository.addCustomer(
            Customer(name = "Payment Verify Customer", phone = "03009998877", currentBalance = 2000.0, active = true)
        )

        // Record payment as Incharge (non-admin) -> must start Unsettle (isAcknowledged = false)
        repository.switchUserRole(UserRole.INCHARGE)
        val staffPayment = repository.recordPayment(
            customerId = custId,
            amount = 500.0,
            method = PaymentMethod.CASH,
            notes = "Collected on route"
        )
        assertFalse("Non-admin payment must start Unsettle", staffPayment.isAcknowledged)

        // Non-admin attempt to acknowledge must be ignored
        repository.acknowledgePayment(staffPayment.id)
        val stillUnsettled = repository.payments.value.first { it.id == staffPayment.id }
        assertFalse("Non-admin must not be able to Clear payment", stillUnsettled.isAcknowledged)

        // Admin marks payment Clear -> isAcknowledged becomes true
        repository.switchUserRole(UserRole.ADMIN)
        repository.acknowledgePayments(listOf(staffPayment.id))
        val clearedPayment = repository.payments.value.first { it.id == staffPayment.id }
        assertTrue("Admin acknowledgement must mark payment Clear", clearedPayment.isAcknowledged)
        assertTrue(clearedPayment.acknowledgedBy.isNotBlank())
    }

    @Test
    fun testCleanSlateSampleDataIsEmptyForMaterialsCostingsRecurring() {
        assertTrue(com.example.data.SampleData.initialFactoryMaterials.isEmpty())
        assertTrue(com.example.data.SampleData.initialBottleCostings.isEmpty())
        assertTrue(com.example.data.SampleData.initialMonthlyRecurringExpenses.isEmpty())
    }
}
