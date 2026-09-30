package com.qqmmxx.piaojia.repository

import android.net.Uri
import com.qqmmxx.piaojia.data.AppDatabase
import com.qqmmxx.piaojia.data.ImageManager
import com.qqmmxx.piaojia.model.Expense
import com.qqmmxx.piaojia.model.ExpenseType
import com.qqmmxx.piaojia.model.Project
import com.qqmmxx.piaojia.model.toEntity
import com.qqmmxx.piaojia.model.toExpense
import com.qqmmxx.piaojia.model.toProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Date

class ExpenseRepository(
    private val database: AppDatabase,
    private val imageManager: ImageManager
) {
    private val projectDao = database.projectDao()
    private val expenseDao = database.expenseDao()

    /**
     * 所有项目（含各自费用）。
     *
     * 这里同时订阅 projects 和 expenses 两张表：原实现只订阅 projects 表，
     * 新增一笔费用并不会让项目列表里的「笔数 / 总金额」刷新（Room 只会因为
     * SQL 里出现的表变化而重新发射）。
     */
    val projects: Flow<List<Project>> =
        combine(projectDao.getAllProjects(), expenseDao.getAllExpenses()) { projectEntities, expenseEntities ->
            val expensesByProject = expenseEntities.groupBy { it.projectId }
            projectEntities.map { entity ->
                val expenses = expensesByProject[entity.id].orEmpty().map { it.toExpense() }
                entity.toProject(expenses)
            }
        }.flowOn(Dispatchers.IO)

    /** 单个项目 + 其费用，项目或费用任一变化都会重新发射 */
    fun projectWithExpenses(projectId: String): Flow<Project?> =
        combine(
            projectDao.getProjectFlowById(projectId),
            expenseDao.getExpensesForProject(projectId)
        ) { projectEntity, expenseEntities ->
            projectEntity?.toProject(expenseEntities.map { it.toExpense() })
        }.flowOn(Dispatchers.IO)

    suspend fun getProject(projectId: String): Project? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(projectId)?.toProject()
    }

    // ---------- Project ----------

    /** 新建项目，返回新项目 id */
    suspend fun addProject(name: String, description: String = ""): String {
        val project = Project(name = name.trim(), description = description.trim())
        withContext(Dispatchers.IO) { projectDao.insert(project.toEntity()) }
        return project.id
    }

    /**
     * 以指定 id 插入项目。
     * Excel 导入必须先插入项目行，否则费用会挂在一个不存在的 projectId 上：
     * Room 的 @Update 对不存在的行是静默 no-op，项目列表里就永远看不到这些费用。
     */
    suspend fun insertProject(project: Project) = withContext(Dispatchers.IO) {
        projectDao.insert(project.toEntity())
    }

    suspend fun updateProject(project: Project) = withContext(Dispatchers.IO) {
        projectDao.update(project.toEntity())
    }

    suspend fun markProjectAsCompleted(projectId: String) = withContext(Dispatchers.IO) {
        projectDao.markProjectAsCompleted(projectId)
    }

    suspend fun markProjectAsUncompleted(projectId: String) = withContext(Dispatchers.IO) {
        projectDao.markProjectAsUncompleted(projectId)
    }

    suspend fun deleteProject(projectId: String) {
        withContext(Dispatchers.IO) {
            val expenses = expenseDao.getExpensesForProjectSync(projectId).map { it.toExpense() }
            // 先落库删除，成功后再清理图片文件；反过来的话一旦数据库操作失败就只剩一堆孤儿文件
            expenseDao.deleteAllByProjectId(projectId)
            projectDao.deleteById(projectId)
            expenses.flatMap { it.imageUris }
                .filter { imageManager.isInternalImage(it) }
                .let { imageManager.deleteImages(it) }
        }
    }

    // ---------- Expense ----------

    suspend fun addExpense(
        projectId: String,
        type: ExpenseType,
        amount: Double,
        description: String = "",
        date: Date = Date(),
        imageUris: List<Uri> = emptyList()
    ): Boolean {
        val internalImageUris = imageManager.copyImagesToInternal(imageUris)
        val expense = Expense(
            projectId = projectId,
            type = type,
            amount = amount,
            description = description.trim(),
            date = date,
            imageUris = internalImageUris
        )
        withContext(Dispatchers.IO) { expenseDao.insert(expense.toEntity()) }
        // 图片复制失败时依然保存报销记录，但把情况告诉上层
        return internalImageUris.size == imageUris.size
    }

    suspend fun updateExpense(expense: Expense) {
        withContext(Dispatchers.IO) {
            val originalExpense = expenseDao.getExpenseById(expense.id)?.toExpense()
            if (originalExpense == null) {
                // 记录已不存在（例如在别处被删掉），退化为直接写入，避免用户改动丢失
                expenseDao.update(expense.toEntity())
                return@withContext
            }

            val originalImages = originalExpense.imageUris
            val newImages = expense.imageUris

            val keptImages = originalImages.filter { it in newImages }
            val imagesToDelete = originalImages.filter { it !in newImages }
                .filter { imageManager.isInternalImage(it) }
            val addedInternalImages = imageManager.copyImagesToInternal(
                newImages.filter { it !in originalImages }
            )

            val updated = expense.copy(imageUris = keptImages + addedInternalImages)
            expenseDao.update(updated.toEntity())
            // 数据库更新成功后再删文件
            imageManager.deleteImages(imagesToDelete)
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        withContext(Dispatchers.IO) {
            val expense = expenseDao.getExpenseById(expenseId)?.toExpense()
            expenseDao.deleteById(expenseId)
            if (expense != null) {
                imageManager.deleteImages(
                    expense.imageUris.filter { imageManager.isInternalImage(it) }
                )
            }
        }
    }

    /** 同步读取某项目的全部费用（导出时需要一次性拿全量快照） */
    suspend fun getExpensesForProjectSync(projectId: String): List<Expense> =
        withContext(Dispatchers.IO) {
            expenseDao.getExpensesForProjectSync(projectId).map { it.toExpense() }
        }

    /**
     * 把用户刚选中的图片存进应用私有目录。
     *
     * 入参是**已经读好的字节**而不是 URI：系统照片选择器授予的读取权限是瞬时的，
     * 必须由选图回调同步读取，晚一步（哪怕只是丢进协程）URI 就已经读不到了。
     */
    suspend fun savePickedImage(bytes: ByteArray): Uri? =
        imageManager.copyReceiptBytesToInternal(bytes)

    /** 用于取消表单时清理已经拷进来的孤儿图片 */
    suspend fun deleteImages(uris: List<Uri>) {
        imageManager.deleteImages(uris.filter { imageManager.isInternalImage(it) })
    }
}
