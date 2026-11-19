package com.example.data.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerBalanceDao {

    @Query("SELECT * FROM customer_balances ORDER BY customerName ASC")
    fun getAllCustomerBalances(): Flow<List<CustomerBalanceEntity>>

    @Query("SELECT * FROM customer_balances WHERE currentBalance > 0 ORDER BY currentBalance DESC")
    fun getCustomersWithOutstandingBalance(): Flow<List<CustomerBalanceEntity>>

    @Query("SELECT * FROM customer_balances WHERE customerId = :customerId LIMIT 1")
    fun getCustomerBalance(customerId: String): Flow<CustomerBalanceEntity?>

    @Query("SELECT * FROM customer_balances WHERE customerId = :customerId LIMIT 1")
    suspend fun getCustomerBalanceDirect(customerId: String): CustomerBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomerBalance(balance: CustomerBalanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomerBalances(balances: List<CustomerBalanceEntity>)

    @Query("UPDATE customer_balances SET currentBalance = currentBalance + :amountChange, emptyBottlesOwed = emptyBottlesOwed + :bottleChange, lastUpdated = :timestamp WHERE customerId = :customerId")
    suspend fun adjustCustomerBalance(customerId: String, amountChange: Double, bottleChange: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM customer_balances WHERE customerId = :customerId")
    suspend fun deleteCustomerBalance(customerId: String)

    @Query("DELETE FROM customer_balances")
    suspend fun clearAll()
}
