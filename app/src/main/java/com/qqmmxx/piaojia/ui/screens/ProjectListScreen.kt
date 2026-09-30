package com.qqmmxx.piaojia.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.qqmmxx.piaojia.model.Project
import com.qqmmxx.piaojia.model.formatYuan
import com.qqmmxx.piaojia.viewmodel.ExpenseViewModel
import com.qqmmxx.piaojia.viewmodel.ExportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val ZIP_MIME = "application/zip"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ExpenseViewModel,
    themeSeed: Color,
    onThemeSeedChange: (Color) -> Unit,
    onProjectSelected: (String) -> Unit
) {
    val projects by viewModel.projects.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var showThemePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportStatus by viewModel.exportStatus.collectAsState()
    
    // 添加设置菜单状态
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showImportProgress by remember { mutableStateOf(false) }
    var showExportProgress by remember { mutableStateOf(false) }
    
    // 添加显示已完成项目的状态
    var showCompletedOnly by remember { mutableStateOf(false) }

    // 只要有任何一笔带图片的报销，导出内容就必须是 ZIP。
    // 原实现固定用 .xlsx 文件名 + xlsx MIME，却把 ZIP 内容写进去，文件直接打不开。
    val anyImages = projects.any { project -> project.expenses.any { it.imageUris.isNotEmpty() } }
    
    // 根据筛选条件获取要显示的项目
    val displayProjects = if (showCompletedOnly) {
        projects.filter { it.isCompleted }
    } else {
        projects.filter { !it.isCompleted } // 只显示未完成的项目
    }
    
    // 导入文件选择器
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                showImportProgress = true
                try {
                    viewModel.importFromExcel(uri, context)
                } catch (e: Exception) {
                    // 失败原因已记录在 ViewModel 的状态里，这里只保证进度条收起
                    Log.e("ProjectListScreen", "导入失败", e)
                } finally {
                    showImportProgress = false
                }
            }
        }
    }
    
    // 导出文件选择器。
    // ActivityResultContract 一旦 remember 下来，MIME 就被固定在首次组合时的取值上，
    // 所以不能用「按 hasImages 动态构造 contract」的写法；这里准备两个固定 MIME 的
    // launcher，导出时按实际内容二选一。
    val exportAsExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(XLSX_MIME)
    ) { uri ->
        uri?.let { startExportAllProjects(it, projects, viewModel, scope, context) { showExportProgress = it } }
    }

    val exportAsZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ZIP_MIME)
    ) { uri ->
        uri?.let { startExportAllProjects(it, projects, viewModel, scope, context) { showExportProgress = it } }
    }
    
    // 处理导入/导出结果提示。
    // 提示完必须复位状态：否则下次成功时状态值完全相同，LaunchedEffect 不会重新触发，
    // 表现为「第二次导出没有任何提示」。
    LaunchedEffect(exportStatus) {
        when (val status = exportStatus) {
            is ExportStatus.Success -> {
                Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                viewModel.clearExportStatus()
            }
            is ExportStatus.Error -> {
                Toast.makeText(context, "错误：${status.message}", Toast.LENGTH_LONG).show()
                viewModel.clearExportStatus()
            }
            is ExportStatus.Warning -> {
                Toast.makeText(context, "警告：${status.message}", Toast.LENGTH_LONG).show()
                viewModel.clearExportStatus()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("报销项目") },
                actions = {
                    // 添加筛选按钮
                    IconButton(
                        onClick = { showCompletedOnly = !showCompletedOnly }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (showCompletedOnly) "显示进行中项目" else "显示已完成项目",
                            tint = if (showCompletedOnly) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            if (showImportProgress || showExportProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Icon(Icons.Default.Settings, contentDescription = "设置")
                            }
                        }
                        
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("导入Excel") },
                                onClick = {
                                    showSettingsMenu = false
                                    importFileLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                }
                            )
                            
                            DropdownMenuItem(
                                text = { Text(if (anyImages) "导出所有数据 (含图片.zip)" else "导出所有数据") },
                                onClick = {
                                    showSettingsMenu = false
                                    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                    if (anyImages) {
                                        exportAsZipLauncher.launch("票夹_${dateFormat.format(Date())}.zip")
                                    } else {
                                        exportAsExcelLauncher.launch("票夹_${dateFormat.format(Date())}.xlsx")
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("配色") },
                                onClick = {
                                    showSettingsMenu = false
                                    showThemePicker = true
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(themeSeed)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                CircleShape
                                            )
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }
            val density = LocalDensity.current.density
            val buttonSize = 96.dp
            val configuration = LocalConfiguration.current
            val maxOffsetX = configuration.screenWidthDp - buttonSize.value
            val maxOffsetY = configuration.screenHeightDp - buttonSize.value
            
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .size(buttonSize)
                    .offset(offsetX.dp, offsetY.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x / density).coerceIn(-maxOffsetX, 0f)
                            offsetY = (offsetY + dragAmount.y / density).coerceIn(-maxOffsetY, 0f)
                        }
                    }
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "添加项目",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 添加筛选状态提示
            if (showCompletedOnly) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "当前显示已完成项目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (displayProjects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (showCompletedOnly) 
                            "暂无已完成的项目" 
                        else 
                            "暂无进行中的项目，点击下方按钮添加新项目",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayProjects) { project ->
                        ProjectItem(
                            project = project,
                            onProjectClick = { onProjectSelected(project.id) },
                            onCompleteClick = { viewModel.markProjectAsCompleted(project.id) },
                            onUncompleteClick = { viewModel.markProjectAsUncompleted(project.id) },
                            onEditClick = { editingProject = project },
                            onDeleteClick = { viewModel.deleteProject(project.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ProjectDialog(
            project = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, description ->
                viewModel.addProject(name, description)
                showAddDialog = false
            }
        )
    }

    if (showThemePicker) {
        ThemePickerDialog(
            current = themeSeed,
            onDismiss = { showThemePicker = false },
            onConfirm = { newSeed ->
                onThemeSeedChange(newSeed)
                showThemePicker = false
            }
        )
    }

    editingProject?.let { project ->
        ProjectDialog(
            project = project,
            onDismiss = { editingProject = null },
            onConfirm = { name, description ->
                viewModel.updateProject(
                    project.copy(
                        name = name,
                        description = description
                    )
                )
                editingProject = null
            }
        )
    }
}

/**
 * 「导出所有数据」的公共入口：把全部项目打包成一个虚拟项目交给 ViewModel 导出。
 * 导出结果通过 viewModel.exportStatus 回传，由上面的 LaunchedEffect 统一提示。
 */
private fun startExportAllProjects(
    uri: Uri,
    projects: List<Project>,
    viewModel: ExpenseViewModel,
    scope: CoroutineScope,
    context: Context,
    onProgress: (Boolean) -> Unit
) {
    val allExpenses = projects.flatMap { it.expenses }
    Log.d("ExportAllData", "导出所有项目：${projects.size} 个项目 / ${allExpenses.size} 笔费用")

    val allProjectsData = Project(
        id = ExpenseViewModel.ALL_PROJECTS_ID,
        name = "所有项目",
        description = "导出的所有项目数据，包含 ${projects.size} 个项目",
        expenses = allExpenses
    )

    scope.launch {
        onProgress(true)
        try {
            viewModel.exportToExcel(allProjectsData, uri, context)
        } catch (e: Exception) {
            // 失败原因已记录在 ViewModel 状态里
            Log.e("ExportAllData", "导出失败", e)
        } finally {
            onProgress(false)
        }
    }
}

@Composable
fun ProjectItem(
    project: Project,
    onProjectClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onUncompleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showCompleteConfirmDialog by remember { mutableStateOf(false) }
    var showUncompleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProjectClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleLarge,
                    textDecoration = if (project.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (project.isCompleted) {
                            DropdownMenuItem(
                                text = { Text("取消完成") },
                                onClick = {
                                    showUncompleteConfirmDialog = true
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("标记为已完成") },
                                onClick = {
                                    showCompleteConfirmDialog = true
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("编辑项目") },
                                onClick = {
                                    onEditClick()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("删除项目") },
                            onClick = {
                                showDeleteConfirmDialog = true
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            if (project.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "状态: ${if (project.isCompleted) "已完成" else "进行中"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (project.isCompleted) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Text(
                    text = "报销: ${project.expenses.size} 笔",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "总金额: ${formatYuan(project.totalAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    
    if (showCompleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirmDialog = false },
            title = { Text("确认完成") },
            text = { Text("确定要将「${project.name}」标记为已完成吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCompleteClick()
                        showCompleteConfirmDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompleteConfirmDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    if (showUncompleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUncompleteConfirmDialog = false },
            title = { Text("取消完成") },
            text = { Text("确定要将「${project.name}」恢复为进行中状态吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUncompleteClick()
                        showUncompleteConfirmDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUncompleteConfirmDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${project.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ProjectDialog(
    project: Project?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf(project?.name ?: "") }
    var description by remember { mutableStateOf(project?.description ?: "") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (project == null) "添加新项目" else "编辑项目")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text("项目名称") },
                    isError = nameError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError) {
                    Text(
                        "项目名称不能为空",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("项目描述 (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onConfirm(name, description)
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
} 