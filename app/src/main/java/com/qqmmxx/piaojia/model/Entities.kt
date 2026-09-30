package com.qqmmxx.piaojia.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val type: String,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    // 列名保持 imageUrisJson 不变，避免无谓的 schema 变更
    val imageUrisJson: String = ""
)

/** 实体关联关系 */
data class ProjectWithExpenses(
    val project: ProjectEntity,
    val expenses: List<ExpenseEntity> = emptyList()
)

// ---------- 实体 <-> 领域模型 转换 ----------

fun ProjectEntity.toProject(expenses: List<Expense> = emptyList()): Project = Project(
    id = id,
    name = name,
    description = description,
    isCompleted = isCompleted,
    createdAt = Date(createdAt),
    expenses = expenses
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    description = description,
    isCompleted = isCompleted,
    createdAt = createdAt.time
)

fun ExpenseEntity.toExpense(): Expense = Expense(
    id = id,
    projectId = projectId,
    // 容错解析：遇到无法识别的类型退化为「其他费用」，而不是直接崩溃
    type = ExpenseType.fromStorage(type),
    amount = amount,
    date = Date(date),
    description = description,
    imageUris = parseUriList(imageUrisJson)
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    projectId = projectId,
    type = type.name,
    amount = amount,
    date = date.time,
    description = description,
    imageUrisJson = serializeUriList(imageUris)
)

// ---------- URI 列表序列化 ----------

private const val URI_SEPARATOR = "|"

fun serializeUriList(uris: List<Uri>): String =
    uris.joinToString(URI_SEPARATOR) { it.toString() }

fun parseUriList(raw: String): List<Uri> {
    if (raw.isBlank() || raw == "[]") return emptyList()
    return raw.split(URI_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
}
