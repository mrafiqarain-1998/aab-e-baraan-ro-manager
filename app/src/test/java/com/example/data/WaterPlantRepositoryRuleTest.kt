package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.Customer
import com.example.model.UserAccount
import com.example.model.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.util.UUID

@Ignore("Requires running Firebase Local Emulator on 127.0.0.1:8085")
class WaterPlantRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createUserOnPhoneA_authenticatesOnPhoneB_andSyncsCustomersFromCloud() = runBlocking {
        val runSuffix = UUID.randomUUID().toString().take(8)
        val ownerEmail = "owner_$runSuffix@aabebaraan.com"
        val ownerUid = signInTestUser(ownerEmail)

        WaterPlantRepository.resetInstanceForTesting()
        val repoPhoneA = WaterPlantRepository.getInstance(appContext)
        val adminProfile = repoPhoneA.authenticate("admin", "admin123")
        assertNotNull("Default Admin should authenticate on Phone A", adminProfile)

        val staffUsername = "rashid_$runSuffix"
        val staffAccount = UserAccount(
            id = "user_$runSuffix",
            username = staffUsername,
            password = "Staff@$runSuffix",
            name = "Rashid Cloud $runSuffix",
            phone = "03001112233",
            role = UserRole.INCHARGE,
            active = true
        )
        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repoPhoneA.createUserAccount(staffAccount)
        }
        assertTrue("Cloud staff creation should succeed", createResult.isNotBlank())

        val customerId = "cust_$runSuffix"
        val customer = Customer(
            id = customerId,
            customerCode = "C-$runSuffix",
            name = "Cloud Hotel $runSuffix",
            phone = "03009998877",
            active = true
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            val payload = FirestoreSyncHelper.toFirestorePayload(
                data = customer,
                ownerId = ownerUid,
                memberUids = listOf(ownerUid),
                memberEmails = listOf(ownerEmail)
            )
            firestore.collection("customers").document(customerId).set(payload).await()
        }

        // Wipe local JSON files to prove Phone B pulls created staff & customer from Firestore Emulator (not disk cache)
        File(appContext.filesDir, "db_user_accounts.json").delete()
        File(appContext.filesDir, "db_customers.json").delete()

        WaterPlantRepository.resetInstanceForTesting()
        val repoPhoneB = WaterPlantRepository.getInstance(appContext)
        val loggedInStaff = withTimeout(DEFAULT_TIMEOUT_MS) {
            repoPhoneB.authenticateAsync(staffUsername, "Staff@$runSuffix")
        }
        assertNotNull("Staff created on Phone A must log in on Phone B via Firestore", loggedInStaff)
        assertEquals(UserRole.INCHARGE, loggedInStaff?.role)
        assertEquals(staffUsername, loggedInStaff?.username)

        repoPhoneB.refreshCloudData()
        assertTrue(
            "Phone B must receive customer written on Phone A",
            repoPhoneB.customers.value.any { it.id == customerId && it.name == "Cloud Hotel $runSuffix" }
        )
    }

    @Test
    fun outsiderUser_cannotReadAnotherOwnersPlantUsersOrCustomers() = runBlocking {
        val runSuffix = UUID.randomUUID().toString().take(8)
        val ownerEmail = "owner_iso_$runSuffix@aabebaraan.com"
        val ownerUid = signInTestUser(ownerEmail)

        WaterPlantRepository.resetInstanceForTesting()
        val repoOwner = WaterPlantRepository.getInstance(appContext)
        repoOwner.authenticate("admin", "admin123")
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repoOwner.createUserAccount(
                UserAccount(
                    id = "user_iso_$runSuffix",
                    username = "staff_iso_$runSuffix",
                    password = "Secret@$runSuffix",
                    name = "Isolated Staff",
                    role = UserRole.INCHARGE,
                    active = true
                )
            )
        }

        // Switch Firebase Auth to an unrelated outsider account
        signInTestUser("outsider_$runSuffix@other.com")
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users")
                    .whereArrayContains("memberUids", ownerUid)
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED when outsider queries owner's plant users")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun unauthenticatedCaller_rejectedWithPermissionDenied() = runBlocking {
        auth.signOut()
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").get().await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated users collection read")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10000L
    }
}
