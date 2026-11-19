package com.example.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CustomerDeliveryEntity::class,
        CustomerBalanceEntity::class,
        DailyPlantExpenseEntity::class,
        StockItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WaterPlantDatabase : RoomDatabase() {

    abstract fun deliveryDao(): DeliveryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun customerBalanceDao(): CustomerBalanceDao
    abstract fun stockDao(): StockDao

    companion object {
        @Volatile
        private var INSTANCE: WaterPlantDatabase? = null

        fun getDatabase(context: Context): WaterPlantDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WaterPlantDatabase::class.java,
                    "aab_e_baraan_room.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
