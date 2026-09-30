package com.qqmmxx.piaojia.model

import android.net.Uri
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Date = Date(),
    val expenses: List<Expense> = emptyList()
) {
    /** 项目总金额 */
    val totalAmount: Double get() = expenses.sumOf { it.amount }
}

enum class ExpenseType {
    FLIGHT_TRAIN, DIDI, HOTEL, OTHER;

    companion object {
        /**
         * 从数据库里存的字符串还原枚举。
         * 用容错查找而不是 valueOf()，避免历史/异常数据导致读取时抛 IllegalArgumentException。
         */
        fun fromStorage(raw: String?): ExpenseType =
            values().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OTHER
    }
}

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val type: ExpenseType,
    val amount: Double,
    val date: Date = Date(),
    val description: String = "",
    val imageUris: List<Uri> = emptyList()
)

/** 费用类型的中文名，全应用统一从这里取，避免多处重复定义。 */
val ExpenseType.displayName: String
    get() = when (this) {
        ExpenseType.FLIGHT_TRAIN -> "机票/高铁"
        ExpenseType.DIDI -> "滴滴/出行"
        ExpenseType.HOTEL -> "酒店住宿"
        ExpenseType.OTHER -> "其他费用"
    }

/**
 * 金额格式化。固定用 Locale.CHINA，避免在非中文系统上出现 "123,45" 这种
 * 小数点被替换成逗号的输出，也不能再出现 "¥316.0" / "¥1.0E7" 这种默认 toString。
 */
fun formatAmount(amount: Double): String = String.format(Locale.CHINA, "%.2f", amount)

fun formatYuan(amount: Double): String = "¥" + formatAmount(amount)
