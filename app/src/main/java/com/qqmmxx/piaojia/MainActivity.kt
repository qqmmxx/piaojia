package com.qqmmxx.piaojia

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqmmxx.piaojia.ui.screens.ProjectDetailScreen
import com.qqmmxx.piaojia.ui.screens.ProjectListScreen
import com.qqmmxx.piaojia.ui.theme.FaP2Theme
import com.qqmmxx.piaojia.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaP2Theme {
                ExpenseReimbursementApp()
            }
        }
    }
}

sealed class Screen {
    object ProjectList : Screen()
    data class ProjectDetail(val projectId: String) : Screen()
}

@Composable
fun ExpenseReimbursementApp() {
    // 创建ViewModel
    val expenseViewModel: ExpenseViewModel = viewModel()
    // 当前屏幕状态
    var currentScreen by remember { mutableStateOf<Screen>(Screen.ProjectList) }
    // 协程作用域
    val coroutineScope = rememberCoroutineScope()
    // 上次按返回键的时间
    var lastBackPressedTime by remember { mutableLongStateOf(0L) }
    // 获取Context
    val context = LocalContext.current

    // 获取返回按键分发器
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // 处理返回按键
    DisposableEffect(currentScreen) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    is Screen.ProjectDetail -> {
                        // 在项目详情页面，返回到项目列表
                        currentScreen = Screen.ProjectList
                    }
                    is Screen.ProjectList -> {
                        // 在项目列表页面，实现双击退出
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressedTime > 2000) {
                            // 如果两次点击间隔超过2秒，提示用户再按一次退出
                            lastBackPressedTime = currentTime
                            Toast.makeText(
                                context, 
                                "再按一次退出应用", 
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // 两次点击间隔在2秒内，退出应用
                            isEnabled = false
                            backDispatcher?.onBackPressed()
                        }
                    }
                }
            }
        }

        // 注册回调
        backDispatcher?.addCallback(callback)

        // 当组件被销毁时移除回调
        onDispose {
            callback.remove()
        }
    }

    // 处理屏幕切换
    when (val screen = currentScreen) {
        is Screen.ProjectList -> {
            // 项目列表屏幕
            ProjectListScreen(
                viewModel = expenseViewModel,
                onProjectSelected = { projectId ->
                    // 确保我们选择了有效的项目ID
                    if (projectId.isNotEmpty()) {
                        Log.d("Navigation", "Navigating to project: $projectId")
                        // 切换屏幕
                        currentScreen = Screen.ProjectDetail(projectId)
                    }
                }
            )
        }
        is Screen.ProjectDetail -> {
            // 项目详情屏幕
            val projectId = screen.projectId
            
            // 调试信息
            LaunchedEffect(projectId) {
                Log.d("ProjectDetailScreen", "Loading project details for ID: $projectId")
            }
            
            // 显示项目详情屏幕
            ProjectDetailScreen(
                projectId = projectId,
                viewModel = expenseViewModel,
                onNavigateBack = {
                    // 返回项目列表
                    Log.d("Navigation", "Navigating back to project list")
                    currentScreen = Screen.ProjectList
                }
            )
        }
    }
}