package com.example.data.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryDao {

    @Query("SELECT * FROM customer_deliveries ORDER BY timestamp DESC")
    fun getAllDeliveries(): Flow<List<CustomerDeliveryEntity>>

    @Query("SELECT * FROM customer_deliveries WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getDeliveriesForCustomer(customerId: String): Flow<List<CustomerDeliveryEntity>>

    @Query("SELECT * FROM customer_deliveries WHERE paymentCollected < totalAmount ORDER BY date ASC")
    fun getUnpaidDeliveries(): Flow<List<CustomerDeliveryEntity>>

    @Query("SELECT * FROM customer_deliveries WHERE customerId = :customerId AND paymentCollected < totalAmount ORDER BY date ASC")
    fun getUnpaidDeliveriesForCustomer(customerId: String): Flow<List<CustomerDeliveryEntity>>

    @Query("SELECT * FROM customer_deliveries WHERE id = :id LIMIT 1")
    suspend fun getDeliveryById(id: String): CustomerDeliveryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDelivery(delivery: CustomerDeliveryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeliveries(deliveries: List<CustomerDeliveryEntity>)

    @Update
    suspend fun updateDelivery(delivery: CustomerDeliveryEntity)

    @Query("UPDATE customer_deliveries SET paymentCollected = :newPaymentCollected, paymentMethod = :newMethod WHERE id = :deliveryId")
    suspend fun updateDeliveryPayment(deliveryId: String, newPaymentCollected: Double, newMethod: String)

    @Query("DELETE FROM customer_deliveries WHERE id = :id")
    suspend fun deleteDeliveryById(id: String)

    @Query("DELETE FROM customer_deliveries")
    suspend fun clearAllDeliveries()
}
