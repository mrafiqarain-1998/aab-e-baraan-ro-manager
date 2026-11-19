package com.example.data.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    @Query("SELECT * FROM stock_items ORDER BY sizeName ASC")
    fun getAllStock(): Flow<List<StockItemEntity>>

    @Query("SELECT * FROM stock_items WHERE id = :id LIMIT 1")
    suspend fun getStockById(id: String): StockItemEntity?

    @Query("SELECT * FROM stock_items WHERE bottleSizeId = :bottleSizeId LIMIT 1")
    suspend fun getStockByBottleSizeId(bottleSizeId: String): StockItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStock(item: StockItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStockList(items: List<StockItemEntity>)

    @Update
    suspend fun updateStock(item: StockItemEntity)

    @Query("UPDATE stock_items SET atPlantFull = :full, atPlantEmpty = :empty, inUse = :inUse, fullPackets = :fullPackets, fullLooseBottles = :fullLooseBottles, damaged = :damaged, withStaff = :staff, withCustomers = :customers, lastUpdated = :timestamp WHERE id = :id")
    suspend fun setStockCounts(id: String, full: Int, empty: Int, damaged: Int, staff: Int, customers: Int, inUse: Int = 0, fullPackets: Int = 0, fullLooseBottles: Int = 0, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM stock_items WHERE id = :id")
    suspend fun deleteStockById(id: String)

    @Query("DELETE FROM stock_items WHERE bottleSizeId = :bottleSizeId")
    suspend fun deleteStockByBottleSizeId(bottleSizeId: String)

    @Query("DELETE FROM stock_items")
    suspend fun clearAllStock()
}
