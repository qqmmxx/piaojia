package com.qqmmxx.piaojia.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.qqmmxx.piaojia.model.Expense
import com.qqmmxx.piaojia.model.ExpenseType
import com.qqmmxx.piaojia.model.displayName
import com.qqmmxx.piaojia.model.formatYuan
import com.qqmmxx.piaojia.viewmodel.ExpenseViewModel
import com.qqmmxx.piaojia.viewmodel.ExportStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val ZIP_MIME = "application/zip"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    // 添加报销对话框状态
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    // 编辑报销对话框状态
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    // 加载项目详情。ViewModel 侧对同一 projectId 是幂等的，不会重复挂 collector
    LaunchedEffect(projectId) {
        viewModel.loadProjectDetails(projectId)
    }

    // 获取当前项目和报销数据
    val project by viewModel.currentProject.collectAsState()
    val expenses by viewModel.currentExpenses.collectAsState()
    val projectMissing by viewModel.projectMissing.collectAsState()

    // 只关心 Warning / Error（例如图片保存失败）。Success 由导出回调自己提示，
    // 这里再弹一次就重复了。
    val exportStatus by viewModel.exportStatus.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(exportStatus) {
        when (val status = exportStatus) {
            is ExportStatus.Warning -> {
                Toast.makeText(context, "警告：${status.message}", Toast.LENGTH_LONG).show()
                viewModel.clearExportStatus()
            }
            is ExportStatus.Error -> {
                Toast.makeText(context, "错误：${status.message}", Toast.LENGTH_LONG).show()
                viewModel.clearExportStatus()
            }
            else -> Unit
        }
    }

    // 项目为空时要把「加载中」和「项目不存在」区分开，
    // 否则导入产生过孤儿数据的项目点进来会永远停在「加载中…」
    if (project == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(if (projectMissing) "项目不存在或已被删除" else "加载中...")
        }
        return
    }

    // 创建一个包含最新费用列表的项目对象
    val currentProject = project!!.copy(expenses = expenses)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = currentProject.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentProject.isCompleted) "已完成" else "进行中",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (currentProject.isCompleted) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    var showExportProgress by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    val hasImages = expenses.any { it.imageUris.isNotEmpty() }

                    // 导出完成的收尾：提示 + 复位状态，避免残留的 Success 状态
                    // 让返回列表页时又弹一次 Toast
                    val onExportFinished: (Throwable?) -> Unit = { error ->
                        showExportProgress = false
                        viewModel.clearExportStatus()
                        if (error == null) {
                            Toast.makeText(context, "导出成功", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(
                                context,
                                "导出失败：${error.message ?: "未知错误"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val runExport: (Uri) -> Unit = { uri ->
                        scope.launch {
                            showExportProgress = true
                            try {
                                viewModel.exportToExcel(currentProject, uri, context)
                                onExportFinished(null)
                            } catch (e: Exception) {
                                onExportFinished(e)
                            }
                        }
                    }

                    // 关键修复：ActivityResultContract 在 remember 时就被固定住，
                    // 原来用 hasImages 动态构造 contract，导致 MIME 冻结在首次组合的取值上
                    // （首次组合时费用还没加载出来，hasImages 恒为 false）。
                    // 结果带图片的项目会把 ZIP 内容写进强制 .xlsx 的目标文件里，导出的文件打不开。
                    // 改成两个各自固定 MIME 的 launcher 按需选择。
                    val exportAsExcelLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument(XLSX_MIME)
                    ) { uri -> uri?.let(runExport) }

                    val exportAsZipLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument(ZIP_MIME)
                    ) { uri -> uri?.let(runExport) }

                    IconButton(
                        onClick = {
                            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            val safeProjectName = currentProject.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            val fileName = "票夹_${safeProjectName}_${dateFormat.format(Date())}"
                            if (hasImages) {
                                exportAsZipLauncher.launch("$fileName.zip")
                            } else {
                                exportAsExcelLauncher.launch("$fileName.xlsx")
                            }
                        }
                    ) {
                        if (showExportProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "导出"
                                )
                                Text(
                                    "导出",
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!currentProject.isCompleted) {
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }
                val density = LocalDensity.current.density
                val buttonSize = 96.dp
                val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                
                FloatingActionButton(
                    onClick = { showAddExpenseDialog = true },
                    modifier = Modifier
                        .size(buttonSize)
                        .offset(offsetX.dp, offsetY.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newX = (offsetX + dragAmount.x / density).coerceIn(
                                    -screenWidth.value + buttonSize.value,
                                    0f
                                )
                                val newY = (offsetY + dragAmount.y / density).coerceIn(
                                    -screenHeight.value + buttonSize.value,
                                    0f
                                )
                                offsetX = newX
                                offsetY = newY
                            }
                        }
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "添加报销",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (currentProject.description.isNotEmpty()) {
                Text(
                    text = currentProject.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无报销记录${if (!currentProject.isCompleted) "，点击下方按钮添加新报销" else ""}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (expenses.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "费用统计",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                                )
                                
                                ExpenseStatisticsSummary(
                                    expenses = expenses,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    
                    items(expenses) { expense ->
                        ExpenseItem(
                            expense = expense,
                            enabled = !currentProject.isCompleted,
                            onClick = { 
                                if (!currentProject.isCompleted) {
                                    editingExpense = expense
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 添加报销对话框
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            projectId = currentProject.id,
            viewModel = viewModel,
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { type, amount, description, date, imageUris ->
                viewModel.addExpense(
                    projectId = currentProject.id,
                    type = type,
                    amount = amount,
                    description = description,
                    date = date,
                    imageUris = imageUris
                )
                showAddExpenseDialog = false
            }
        )
    }
    
    // 编辑报销对话框
    editingExpense?.let { expense ->
        EditExpenseDialog(
            projectId = currentProject.id,
            viewModel = viewModel,
            expense = expense,
            onDismiss = { editingExpense = null },
            onUpdate = { type, amount, description, imageUris ->
                val updatedExpense = expense.copy(
                    type = type,
                    amount = amount,
                    description = description,
                    imageUris = imageUris
                )
                viewModel.updateExpense(updatedExpense)
                editingExpense = null
            },
            onDelete = {
                viewModel.deleteExpense(expense.id)
                editingExpense = null
            }
        )
    }
}

@Composable
fun ExpenseItem(
    expense: Expense,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clickable(enabled = enabled, onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = expense.type.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatter.format(expense.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                Text(
                    text = formatYuan(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (expense.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (expense.imageUris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow {
                    items(expense.imageUris) { uri ->
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(4.dp)
                                )
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    ImageRequest.Builder(LocalContext.current)
                                        .data(data = uri)
                                        .build()
                                ),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    projectId: String,
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit,
    onConfirm: (ExpenseType, Double, String, Date, List<Uri>) -> Unit
) {
    var selectedType by remember { mutableStateOf(ExpenseType.FLIGHT_TRAIN) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val date = remember { Date() } // 自动生成当前日期，不需要用户手动输入
    val imageUris = remember { mutableStateListOf<Uri>() }

    var typeMenuExpanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    // 选图相关：选中即拷贝，取消表单时回收本次新拷进来的图片
    val sessionImages = remember { mutableStateListOf<Uri>() }
    var imageSaving by remember { mutableStateOf(false) }
    val pickImage = rememberReceiptImagePicker(
        saveImage = { bytes -> viewModel.savePickedImage(bytes) },
        onSaved = { internalUri ->
            imageUris.add(internalUri)
            sessionImages.add(internalUri)
        },
        onBusy = { imageSaving = it }
    )

    fun dismissAndCleanup() {
        // 只回收「本次表单新拷进来、而且还没保存」的图片；
        // 已经在列表里的会被丢弃，但用户确实从相册选过，所以要删掉副本
        viewModel.discardImages(sessionImages.filter { it in imageUris })
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { dismissAndCleanup() },
        title = { Text("添加新报销") },
        text = {
            Column {
                // 1. 费用类型下拉框
                Text("费用类型", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = !typeMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = { },
                        readOnly = true,
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        ExpenseType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedType = type
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 2. 金额字段
                Text("金额", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { 
                        amount = it
                        amountError = it.isEmpty() || it.toDoubleOrNull() == null
                    },
                    isError = amountError,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Text(
                            "¥",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (amountError) MaterialTheme.colorScheme.error 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (amountError) {
                    Text(
                        "请输入有效金额",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 3. 描述字段
                Text("描述 (可选)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("请输入费用描述") }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 4. 自动生成的日期和时间
                Text("日期和时间", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = formatter.format(date),
                    onValueChange = { },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 5. 图片选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("添加图片 (可选)", style = MaterialTheme.typography.bodyMedium)
                    if (imageSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        IconButton(
                            onClick = { pickImage() }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "添加图片")
                        }
                    }
                }
                
                // 显示已选择的图片
                if (imageUris.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        items(imageUris.toList()) { uri ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(4.dp)
                                    )
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        ImageRequest.Builder(LocalContext.current)
                                            .data(data = uri)
                                            .build()
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                
                                // 删除图片按钮
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .background(
                                            MaterialTheme.colorScheme.errorContainer,
                                            RoundedCornerShape(bottomStart = 4.dp)
                                        )
                                        .clickable {
                                            imageUris.remove(uri)
                                            // 本次新拷进来的图，用户又删了，直接回收掉
                                            if (sessionImages.remove(uri)) {
                                                viewModel.discardImages(listOf(uri))
                                            }
                                        }
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除图片",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                // 拷贝还没结束时禁掉，避免图片还没进列表就保存了
                enabled = !imageSaving,
                onClick = {
                    if (amount.isEmpty() || amount.toDoubleOrNull() == null) {
                        amountError = true
                    } else {
                        onConfirm(
                            selectedType,
                            amount.toDouble(),
                            description,
                            date,
                            imageUris.toList()
                        )
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { dismissAndCleanup() }) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    projectId: String,
    viewModel: ExpenseViewModel,
    expense: Expense,
    onDismiss: () -> Unit,
    onUpdate: (ExpenseType, Double, String, List<Uri>) -> Unit,
    onDelete: () -> Unit
) {
    var selectedType by remember { mutableStateOf(expense.type) }
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    var description by remember { mutableStateOf(expense.description) }
    val imageUris = remember { mutableStateListOf<Uri>().apply { addAll(expense.imageUris) } }
    
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    // 本次编辑里新拷进来的图片；取消时只回收这些，原有的图片文件不能动
    val sessionImages = remember { mutableStateListOf<Uri>() }
    var imageSaving by remember { mutableStateOf(false) }
    val pickImage = rememberReceiptImagePicker(
        saveImage = { bytes -> viewModel.savePickedImage(bytes) },
        onSaved = { internalUri ->
            imageUris.add(internalUri)
            sessionImages.add(internalUri)
        },
        onBusy = { imageSaving = it }
    )

    fun dismissAndCleanup() {
        viewModel.discardImages(sessionImages.filter { it in imageUris })
        onDismiss()
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条报销记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 整条记录都要删了，本次新拷进来的临时图先回收
                        viewModel.discardImages(sessionImages.toList())
                        sessionImages.clear()
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("取消")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { dismissAndCleanup() },
            title = { Text("编辑报销") },
            text = {
                Column {
                    // 1. 费用类型下拉框
                    Text("费用类型", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = typeMenuExpanded,
                        onExpandedChange = { typeMenuExpanded = !typeMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedType.displayName,
                            onValueChange = { },
                            readOnly = true,
                            trailingIcon = { 
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        
                        ExposedDropdownMenu(
                            expanded = typeMenuExpanded,
                            onDismissRequest = { typeMenuExpanded = false }
                        ) {
                            ExpenseType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
                                        selectedType = type
                                        typeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 2. 金额字段
                    Text("金额", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { 
                            amount = it
                            amountError = it.isEmpty() || it.toDoubleOrNull() == null
                        },
                        isError = amountError,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Text(
                                "¥",
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (amountError) MaterialTheme.colorScheme.error 
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    if (amountError) {
                        Text(
                            "请输入有效金额",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 3. 描述字段
                    Text("描述 (可选)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("请输入费用描述") }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 4. 日期信息（只读）
                    Text("日期和时间", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = formatter.format(expense.date),
                        onValueChange = { },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 5. 图片选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("添加图片 (可选)", style = MaterialTheme.typography.bodyMedium)
                        if (imageSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            IconButton(
                                onClick = { pickImage() }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "添加图片")
                            }
                        }
                    }
                    
                    // 显示已选择的图片
                    if (imageUris.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            items(imageUris.toList()) { uri ->
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(4.dp)
                                        )
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            ImageRequest.Builder(LocalContext.current)
                                                .data(data = uri)
                                                .build()
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    
                                    // 删除图片按钮
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopEnd)
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer,
                                                RoundedCornerShape(bottomStart = 4.dp)
                                            )
                                            .clickable {
                                            imageUris.remove(uri)
                                            // 本次新拷进来的图，用户又删了，直接回收掉
                                            if (sessionImages.remove(uri)) {
                                                viewModel.discardImages(listOf(uri))
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "删除图片",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // 删除按钮
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除报销",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("删除此报销")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !imageSaving,
                    onClick = {
                        if (amount.isEmpty() || amount.toDoubleOrNull() == null) {
                            amountError = true
                        } else {
                            onUpdate(
                                selectedType,
                                amount.toDouble(),
                                description,
                                imageUris.toList()
                            )
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissAndCleanup() }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ExpenseStatisticsSummary(
    expenses: List<Expense>,
    modifier: Modifier = Modifier
) {
    val flightTrainTotal = expenses
        .filter { it.type == ExpenseType.FLIGHT_TRAIN }
        .sumOf { it.amount }
        
    val didiTotal = expenses
        .filter { it.type == ExpenseType.DIDI }
        .sumOf { it.amount }
        
    val hotelTotal = expenses
        .filter { it.type == ExpenseType.HOTEL }
        .sumOf { it.amount }
        
    val otherTotal = expenses
        .filter { it.type == ExpenseType.OTHER }
        .sumOf { it.amount }
        
    val totalAmount = expenses.sumOf { it.amount }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 显示各类型费用
            if (flightTrainTotal > 0) {
                ExpenseTypeItem(label = "机票/高铁", amount = flightTrainTotal, color = MaterialTheme.colorScheme.primary)
            }
            
            if (didiTotal > 0) {
                ExpenseTypeItem(label = "滴滴/出行", amount = didiTotal, color = MaterialTheme.colorScheme.secondary)
            }
            
            if (hotelTotal > 0) {
                ExpenseTypeItem(label = "酒店住宿", amount = hotelTotal, color = MaterialTheme.colorScheme.tertiary)
            }
            
            if (otherTotal > 0) {
                ExpenseTypeItem(label = "其他费用", amount = otherTotal, color = MaterialTheme.colorScheme.error)
            }
            
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            
            // 显示总金额
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "总计",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatYuan(totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ExpenseTypeItem(label: String, amount: Double, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = formatYuan(amount),
            style = MaterialTheme.typography.bodyMedium
        )
    }
} 