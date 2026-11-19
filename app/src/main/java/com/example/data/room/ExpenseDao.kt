package com.example.data.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM daily_plant_expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<DailyPlantExpenseEntity>>

    @Query("SELECT * FROM daily_plant_expenses WHERE date = :date ORDER BY timestamp DESC")
    fun getExpensesByDate(date: String): Flow<List<DailyPlantExpenseEntity>>

    @Query("SELECT * FROM daily_plant_expenses WHERE categoryId = :categoryId ORDER BY timestamp DESC")
    fun getExpensesByCategory(categoryId: String): Flow<List<DailyPlantExpenseEntity>>

    @Query("SELECT SUM(amount) FROM daily_plant_expenses WHERE date = :date")
    fun getTotalExpenseForDate(date: String): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: DailyPlantExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<DailyPlantExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: DailyPlantExpenseEntity)

    @Query("DELETE FROM daily_plant_expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: String)

    @Query("DELETE FROM daily_plant_expenses")
    suspend fun clearAllExpenses()
}
