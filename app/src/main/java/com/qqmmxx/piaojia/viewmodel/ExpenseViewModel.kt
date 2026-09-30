package com.qqmmxx.piaojia.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qqmmxx.piaojia.ExpenseApplication
import com.qqmmxx.piaojia.data.ImageManager
import com.qqmmxx.piaojia.model.Expense
import com.qqmmxx.piaojia.model.ExpenseType
import com.qqmmxx.piaojia.model.Project
import com.qqmmxx.piaojia.model.displayName
import com.qqmmxx.piaojia.model.formatAmount
import com.qqmmxx.piaojia.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val IMPORT_TAG = "ImportExcel"
        private const val EXPORT_TAG = "ExportExcel"

        /** 「所有项目」导出时使用的虚拟项目 id */
        const val ALL_PROJECTS_ID = "all"

        private const val MAX_CELL_TEXT = 32000
        private const val EXCEL_SHEET_NAME_LIMIT = 31
    }

    private val repository: ExpenseRepository
    val projects: Flow<List<Project>>

    /** 当前打开的项目；null 表示正在加载或项目不存在 */
    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject

    private val _currentExpenses = MutableStateFlow<List<Expense>>(emptyList())
    val currentExpenses: StateFlow<List<Expense>> = _currentExpenses

    /** 项目确实不存在（已删除等），用于把「加载中」和「找不到」区分开 */
    private val _projectMissing = MutableStateFlow(false)
    val projectMissing: StateFlow<Boolean> = _projectMissing

    private val _exportStatus = MutableStateFlow<ExportStatus>(ExportStatus.None)
    val exportStatus: StateFlow<ExportStatus> = _exportStatus

    /**
     * 详情页数据流。原先每次增删改都 viewModelScope.launch 一个新的 collect，
     * 而 Room 的 Flow 永不结束，于是同一个项目会挂上多个 collector 并发写状态，
     * 切换项目时旧项目的 collector 也不取消，会把另一个项目的数据覆盖进来。
     * 现在改成：始终只有一个 Job，切换项目时先取消旧的。
     */
    private var detailJob: Job? = null

    init {
        val app = application as ExpenseApplication
        repository = ExpenseRepository(app.database, app.imageManager)
        projects = repository.projects
    }

    // ---------- 项目详情 ----------

    fun loadProjectDetails(projectId: String) {
        // 重复调用（例如增删改后刷新）不再叠加 collector
        if (detailJob?.isActive == true && loadedProjectId == projectId) return

        detailJob?.cancel()
        loadedProjectId = projectId
        // 先清空，避免短暂显示上一个项目的数据
        _currentProject.value = null
        _currentExpenses.value = emptyList()
        _projectMissing.value = false

        detailJob = viewModelScope.launch {
            repository.projectWithExpenses(projectId).collect { project ->
                _projectMissing.value = project == null
                _currentProject.value = project
                _currentExpenses.value = project?.expenses.orEmpty()
            }
        }
    }

    private var loadedProjectId: String? = null

    fun clearProjectDetails() {
        detailJob?.cancel()
        detailJob = null
        loadedProjectId = null
        _currentProject.value = null
        _currentExpenses.value = emptyList()
        _projectMissing.value = false
    }

    // ---------- Project ----------

    fun addProject(name: String, description: String = "") {
        viewModelScope.launch { repository.addProject(name, description) }
    }

    fun updateProject(project: Project) {
        viewModelScope.launch { repository.updateProject(project) }
    }

    fun markProjectAsCompleted(projectId: String) {
        viewModelScope.launch { repository.markProjectAsCompleted(projectId) }
    }

    fun markProjectAsUncompleted(projectId: String) {
        viewModelScope.launch { repository.markProjectAsUncompleted(projectId) }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch { repository.deleteProject(projectId) }
    }

    // ---------- Expense ----------

    fun addExpense(
        projectId: String,
        type: ExpenseType,
        amount: Double,
        description: String = "",
        date: Date = Date(),
        imageUris: List<Uri> = emptyList()
    ) {
        viewModelScope.launch {
            val allImagesSaved = repository.addExpense(
                projectId = projectId,
                type = type,
                amount = amount,
                description = description,
                date = date,
                imageUris = imageUris
            )
            if (!allImagesSaved) {
                _exportStatus.value = ExportStatus.Warning("部分图片保存失败，报销记录已保存")
            }
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { repository.updateExpense(expense) }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch { repository.deleteExpense(expenseId) }
    }

    /**
     * 把选中的图片存进应用私有目录。
     *
     * 入参是**已读好的字节**：照片选择器的授权是瞬时的，必须由选图回调同步读出来，
     * 等点「保存」再读必然拿到 null。
     */
    suspend fun savePickedImage(bytes: ByteArray): Uri? = repository.savePickedImage(bytes)

    /** 用户还没保存就删图/取消表单时，回收已经拷进来的图片 */
    fun discardImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch { repository.deleteImages(uris) }
    }

    // ---------- 导出 / 导入状态 ----------

    fun clearExportStatus() {
        _exportStatus.value = ExportStatus.None
    }

    // ---------- 导出 ----------

    suspend fun exportToExcel(project: Project, uri: Uri, context: Context) =
        withContext(Dispatchers.IO) {
            try {
                _exportStatus.value = ExportStatus.InProgress("导出中...")
                val expenses = project.expenses
                Log.d(EXPORT_TAG, "开始导出「${project.name}」，费用 ${expenses.size} 笔，目标 $uri")

                if (expenses.any { it.imageUris.isNotEmpty() }) {
                    exportWithImages(project, expenses, uri, context)
                } else {
                    exportExcelOnly(project, expenses, uri, context)
                }

                _exportStatus.value = ExportStatus.Success("导出成功")
                Log.d(EXPORT_TAG, "导出完成")
            } catch (e: Exception) {
                Log.e(EXPORT_TAG, "导出失败", e)
                _exportStatus.value = ExportStatus.Error(e.message ?: "导出失败")
                throw e
            }
        }

    /** 生成工作簿。调用方负责 write + close。 */
    private suspend fun buildWorkbook(project: Project, expenses: List<Expense>): XSSFWorkbook {
        val workbook = XSSFWorkbook()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

        if (project.id == ALL_PROJECTS_ID) {
            val nameById = resolveProjectNames(expenses.map { it.projectId })
            createSummarySheet(workbook, expenses, nameById)

            expenses.groupBy { it.projectId }.forEach { (projectId, projectExpenses) ->
                val info = repository.getProject(projectId)
                    ?: Project(
                        id = projectId,
                        name = nameById[projectId] ?: "未知项目",
                        description = "项目信息不可用"
                    )
                createProjectSheet(workbook, info, projectExpenses, dateFormat)
            }
        } else {
            createProjectSheet(workbook, project, expenses, dateFormat)
        }
        return workbook
    }

    private suspend fun resolveProjectNames(projectIds: Collection<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (id in projectIds.distinct()) {
            result[id] = repository.getProject(id)?.name ?: "未知项目"
        }
        return result
    }

    private suspend fun exportExcelOnly(
        project: Project,
        expenses: List<Expense>,
        uri: Uri,
        context: Context
    ) = withContext(Dispatchers.IO) {
        val workbook = buildWorkbook(project, expenses)
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                workbook.write(outputStream)
            } ?: throw IOException("无法打开输出流，写入失败")
        } finally {
            workbook.close()
        }
    }

    /** 有图片时导出 zip：内含一份 xlsx + images/ 目录下的原图 */
    private suspend fun exportWithImages(
        project: Project,
        expenses: List<Expense>,
        uri: Uri,
        context: Context
    ) = withContext(Dispatchers.IO) {
        val excelBytes = ByteArrayOutputStream().use { bos ->
            val workbook = buildWorkbook(project, expenses)
            try {
                workbook.write(bos)
            } finally {
                workbook.close()
            }
            bos.toByteArray()
        }

        val imageManager = getApplication<ExpenseApplication>().imageManager

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                val excelFilename = "票夹_${sanitizeFileName(project.name)}.xlsx"
                zipOut.putNextEntry(ZipEntry(excelFilename))
                zipOut.write(excelBytes)
                zipOut.closeEntry()

                var added = 0
                expenses.forEach { expense ->
                    expense.imageUris.forEachIndexed { index, imageUri ->
                        val entryName = "images/${expense.id}_$index.jpg"
                        val written = writeImageEntry(zipOut, entryName, imageUri, context, imageManager)
                        if (written) added++
                    }
                }
                Log.d(EXPORT_TAG, "压缩包完成，图片 $added 张")
            }
        } ?: throw IOException("无法创建压缩包")
    }

    private fun writeImageEntry(
        zipOut: ZipOutputStream,
        entryName: String,
        imageUri: Uri,
        context: Context,
        imageManager: ImageManager
    ): Boolean {
        return try {
            val file = imageManager.getImageFile(imageUri)
            if (file != null) {
                zipOut.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { copyStream(it, zipOut) }
                zipOut.closeEntry()
                true
            } else {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    zipOut.putNextEntry(ZipEntry(entryName))
                    copyStream(input, zipOut)
                    zipOut.closeEntry()
                    true
                } ?: run {
                    Log.e(EXPORT_TAG, "无法读取图片: $imageUri")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(EXPORT_TAG, "写入图片失败: $entryName", e)
            false
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        val buffered = BufferedInputStream(input)
        var len = buffered.read(buffer)
        while (len > 0) {
            output.write(buffer, 0, len)
            len = buffered.read(buffer)
        }
    }

    // ---------- 工作表构建 ----------

    /**
     * POI 在创建同名工作表时会直接抛异常，项目重名就会导致整个导出失败。
     * 这里统一做去重 + 长度截断。
     */
    private fun Workbook.createUniqueSheet(rawName: String): Sheet {
        val cleaned = rawName.take(EXCEL_SHEET_NAME_LIMIT)
            .replace(Regex("[\\\\/?*\\[\\]:]"), "_")
            .ifBlank { "工作表" }
        var candidate = cleaned
        var suffix = 2
        while (getSheet(candidate) != null) {
            val tail = "_$suffix"
            candidate = cleaned.take(EXCEL_SHEET_NAME_LIMIT - tail.length) + tail
            suffix++
        }
        return createSheet(candidate)
    }

    private fun createSummarySheet(
        workbook: XSSFWorkbook,
        expenses: List<Expense>,
        projectNames: Map<String, String>
    ) {
        val sheet = workbook.createUniqueSheet("统计概览")
        var rowNum = 0

        var row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue("票夹统计概览")

        row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue(
            "导出时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())}"
        )

        rowNum++

        row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue("项目名称")
        row.createCell(1).setCellValue("费用笔数")
        row.createCell(2).setCellValue("总金额")

        sheet.setColumnWidth(0, 25 * 256)
        sheet.setColumnWidth(1, 10 * 256)
        sheet.setColumnWidth(2, 15 * 256)

        var totalCount = 0
        var totalAmount = 0.0

        expenses.groupBy { it.projectId }.forEach { (projectId, projectExpenses) ->
            val projectTotal = projectExpenses.sumOf { it.amount }
            row = sheet.createRow(rowNum++)
            row.createCell(0).setCellValue(projectNames[projectId] ?: "未知项目")
            row.createCell(1).setCellValue(projectExpenses.size.toDouble())
            row.createCell(2).setCellValue("¥${formatAmount(projectTotal)}")
            totalCount += projectExpenses.size
            totalAmount += projectTotal
        }

        rowNum++
        row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue("总计")
        row.createCell(1).setCellValue(totalCount.toDouble())
        row.createCell(2).setCellValue("¥${formatAmount(totalAmount)}")

        rowNum += 2
        row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue("费用类型统计")

        row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue("费用类型")
        row.createCell(1).setCellValue("费用笔数")
        row.createCell(2).setCellValue("总金额")

        expenses.groupBy { it.type }.forEach { (type, typeExpenses) ->
            row = sheet.createRow(rowNum++)
            row.createCell(0).setCellValue(type.displayName)
            row.createCell(1).setCellValue(typeExpenses.size.toDouble())
            row.createCell(2).setCellValue("¥${formatAmount(typeExpenses.sumOf { it.amount })}")
        }

        rowNum++
        row = sheet.createRow(rowNum)
        row.createCell(0).setCellValue("总计")
        row.createCell(1).setCellValue(expenses.size.toDouble())
        row.createCell(2).setCellValue("¥${formatAmount(expenses.sumOf { it.amount })}")
    }

    private fun createProjectSheet(
        workbook: XSSFWorkbook,
        project: Project,
        expenses: List<Expense>,
        dateFormat: SimpleDateFormat
    ) {
        val sheet = workbook.createUniqueSheet(project.name)
        var rowNum = 0

        var row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue("项目名称：${project.name}")

        row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue("项目描述：${project.description}")

        row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue(
            "导出时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())}"
        )

        rowNum++

        row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue("类型")
        row.createCell(1).setCellValue("日期")
        row.createCell(2).setCellValue("金额")
        row.createCell(3).setCellValue("描述")
        row.createCell(4).setCellValue("图片")

        sheet.setColumnWidth(0, 15 * 256)
        sheet.setColumnWidth(1, 15 * 256)
        sheet.setColumnWidth(2, 12 * 256)
        sheet.setColumnWidth(3, 30 * 256)
        sheet.setColumnWidth(4, 30 * 256)

        if (expenses.isEmpty()) {
            sheet.createRow(rowNum++).createCell(0).setCellValue("暂无费用记录")
            rowNum++
            row = sheet.createRow(rowNum)
            row.createCell(0).setCellValue("总计")
            row.createCell(2).setCellValue("¥0.00")
            return
        }

        var totalAmount = 0.0

        expenses.groupBy { it.type }.forEach { (type, typeExpenses) ->
            val typeTotal = typeExpenses.sumOf { it.amount }
            totalAmount += typeTotal

            row = sheet.createRow(rowNum++)
            row.createCell(0).setCellValue("【${type.displayName}】")
            row.createCell(2).setCellValue("小计：¥${formatAmount(typeTotal)}")

            typeExpenses.forEach { expense ->
                row = sheet.createRow(rowNum++)
                row.createCell(0).setCellValue(type.displayName)
                row.createCell(1).setCellValue(dateFormat.format(expense.date))
                row.createCell(2).setCellValue(expense.amount)
                row.createCell(3).setCellValue(expense.description.take(MAX_CELL_TEXT))
                if (expense.imageUris.isNotEmpty()) {
                    row.createCell(4).setCellValue(
                        expense.imageUris.mapIndexed { index, _ -> "images/${expense.id}_$index.jpg" }
                            .joinToString(", ")
                    )
                }
            }
            rowNum++
        }

        row = sheet.createRow(rowNum)
        row.createCell(0).setCellValue("总计")
        row.createCell(2).setCellValue("¥${formatAmount(totalAmount)}")
    }

    // ---------- 导入 ----------

    /**
     * 从 Excel 导入。支持一次导入多个工作表（每个含有效表头的工作表作为一个项目）。
     * 与导出格式互补，因此「导出所有数据」得到的文件可以重新导入。
     */
    suspend fun importFromExcel(uri: Uri, context: Context) = withContext(Dispatchers.IO) {
        try {
            _exportStatus.value = ExportStatus.InProgress("导入中...")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

            val workbook = context.contentResolver.openInputStream(uri)?.use { XSSFWorkbook(it) }
                ?: throw IOException("无法读取所选文件")

            var importedProjects = 0
            var importedExpenses = 0

            try {
                if (workbook.numberOfSheets == 0) {
                    throw IOException("文件里没有任何工作表")
                }

                for (sheetIndex in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIndex)
                    val headerRowIndex = findHeaderRowIndex(sheet) ?: continue

                    val parsed = parseExpenseRows(sheet, headerRowIndex, dateFormat)
                    if (parsed.isEmpty()) continue

                    val project = Project(
                        id = UUID.randomUUID().toString(),
                        name = readProjectName(sheet)
                            ?: "导入的票夹 ${dateFormat.format(Date())}",
                        description = "从 Excel 导入于 ${dateFormat.format(Date())}"
                    )
                    // 必须真正插入项目行；原先调用 updateProject 对不存在的行是 no-op，
                    // 结果费用全部变成挂在不存在项目上的孤儿数据
                    repository.insertProject(project)
                    importedProjects++

                    parsed.forEach { row ->
                        repository.addExpense(
                            projectId = project.id,
                            type = row.type,
                            amount = row.amount,
                            description = row.description,
                            date = row.date
                        )
                        importedExpenses++
                    }
                    Log.d(IMPORT_TAG, "工作表「${sheet.sheetName}」导入 ${parsed.size} 笔")
                }
            } finally {
                workbook.close()
            }

            _exportStatus.value = when {
                importedExpenses > 0 -> ExportStatus.Success(
                    "已导入 $importedProjects 个项目、$importedExpenses 笔费用"
                )
                else -> ExportStatus.Warning("没有找到可导入的数据，请确认表头包含「类型 / 日期 / 金额 / 描述」")
            }
        } catch (e: Exception) {
            Log.e(IMPORT_TAG, "导入失败", e)
            _exportStatus.value = ExportStatus.Error(e.message ?: "导入失败")
            throw e
        }
    }

    private data class ParsedExpenseRow(
        val type: ExpenseType,
        val amount: Double,
        val date: Date,
        val description: String
    )

    /** 在前若干行里找表头；不再假设表头一定在第 0 行 */
    private fun findHeaderRowIndex(sheet: Sheet): Int? {
        val lastCandidate = minOf(sheet.lastRowNum, 20)
        for (rowIndex in 0..lastCandidate) {
            val row = sheet.getRow(rowIndex) ?: continue
            if (row.cellText(0) == "类型" &&
                row.cellText(1) == "日期" &&
                row.cellText(2) == "金额" &&
                row.cellText(3) == "描述"
            ) {
                return rowIndex
            }
        }
        return null
    }

    /** 从导出的文件里还原项目名（第 0 行的「项目名称：xxx」） */
    private fun readProjectName(sheet: Sheet): String? {
        val lastCandidate = minOf(sheet.lastRowNum, 5)
        for (rowIndex in 0..lastCandidate) {
            val text = sheet.getRow(rowIndex)?.cellText(0) ?: continue
            if (text.startsWith("项目名称")) {
                return text.substringAfter("项目名称").trimStart('：', ':', ' ', '\u3000').trim()
                    .ifBlank { null }
            }
        }
        return null
    }

    private fun parseExpenseRows(
        sheet: Sheet,
        headerRowIndex: Int,
        dateFormat: SimpleDateFormat
    ): List<ParsedExpenseRow> {
        val result = mutableListOf<ParsedExpenseRow>()

        for (rowIndex in (headerRowIndex + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            if (isMarkerRow(row)) continue

            val type = parseExpenseType(row.cellText(0)) ?: continue
            val amount = parseAmount(row.getCell(2)) ?: continue
            if (amount <= 0) continue

            result += ParsedExpenseRow(
                type = type,
                amount = amount,
                date = parseDate(row.cellText(1), dateFormat),
                description = row.cellText(3).take(MAX_CELL_TEXT)
            )
        }
        return result
    }

    /** 小计行、总计行、类型标题行都不是真实数据 */
    private fun isMarkerRow(row: Row): Boolean {
        val first = row.cellText(0)
        if (first.startsWith("【") || first == "总计" || first == "暂无费用记录") return true
        return row.cellText(2).startsWith("小计")
    }

    private fun parseExpenseType(text: String): ExpenseType? = when {
        text.isEmpty() -> null
        text.contains("机票") || text.contains("高铁") -> ExpenseType.FLIGHT_TRAIN
        text.contains("滴滴") || text.contains("出行") -> ExpenseType.DIDI
        text.contains("酒店") || text.contains("住宿") -> ExpenseType.HOTEL
        text.contains("其他") -> ExpenseType.OTHER
        else -> null
    }

    private fun parseAmount(cell: Cell?): Double? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue
                .replace("¥", "")
                .replace(",", "")
                .trim()
                .toDoubleOrNull()
            else -> null
        }
    }

    private fun parseDate(text: String, dateFormat: SimpleDateFormat): Date {
        if (text.isBlank()) return Date()
        return try {
            dateFormat.parse(text) ?: Date()
        } catch (e: ParseException) {
            Date()
        }
    }

    private fun Row.cellText(index: Int): String {
        val cell = getCell(index) ?: return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            else -> ""
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "导出" }
}

sealed class ExportStatus {
    object None : ExportStatus()
    data class InProgress(val message: String) : ExportStatus()
    data class Success(val message: String) : ExportStatus()
    data class Warning(val message: String) : ExportStatus()
    data class Error(val message: String) : ExportStatus()
}
