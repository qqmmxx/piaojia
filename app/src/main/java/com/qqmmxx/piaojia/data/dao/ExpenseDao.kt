package com.qqmmxx.piaojia.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qqmmxx.piaojia.model.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE projectId = :projectId")
    suspend fun deleteAllByProjectId(projectId: String)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteById(expenseId: String)

    /** 按日期倒序，保证列表顺序稳定（之前没有排序，顺序取决于 SQLite 内部实现） */
    @Query("SELECT * FROM expenses WHERE projectId = :projectId ORDER BY date DESC, id DESC")
    fun getExpensesForProject(projectId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE projectId = :projectId ORDER BY date DESC, id DESC")
    suspend fun getExpensesForProjectSync(projectId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: String): ExpenseEntity?

    /** 供项目列表聚合使用：任一费用变化都能让项目列表刷新 */
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
}
