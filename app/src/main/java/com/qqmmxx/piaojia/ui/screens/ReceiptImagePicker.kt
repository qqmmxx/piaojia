package com.qqmmxx.piaojia.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * 统一封装「选一张票据图片」并落盘到应用私有目录。
 *
 * 这里有两个必须遵守的约束，都是真机踩出来的：
 *
 * 1) 不能用 ActivityResultContracts.GetContent()。
 *    ColorOS 等 OEM 相册会返回 content://media/external_primary/... 的 MediaStore URI，
 *    而应用声明了 READ_MEDIA_IMAGES 却从不申请运行时权限，于是根本读不到。
 *    PickVisualMedia 走系统照片选择器，由系统直接授予所选 URI 的读取权限。
 *
 * 2) 必须在结果回调里**同步**把字节读出来。
 *    系统照片选择器给的授权是瞬时的：实测在回调中同步 openInputStream 能读到数据，
 *    但只要延后到协程里（哪怕只有十几毫秒）就已经失败返回 null，
 *    图片会被静默丢掉。所以同步 readBytes()，再把字节丢给后台解码落盘。
 *
 * @param saveImage 后台把字节存成应用内图片，返回应用内 URI；失败返回 null
 * @param onSaved   保存成功回调，参数是应用内 URI
 * @param onBusy    开始/结束回调，用于禁掉「保存」按钮，避免图片还没落盘就提交
 */
@Composable
fun rememberReceiptImagePicker(
    saveImage: suspend (ByteArray) -> android.net.Uri?,
    onSaved: (android.net.Uri) -> Unit,
    onBusy: (Boolean) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        // 关键：同步读，不能等协程调度
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e("ReceiptImagePicker", "同步读取选中图片失败: $uri", e)
            null
        }

        if (bytes == null || bytes.isEmpty()) {
            Log.e("ReceiptImagePicker", "选中的图片读不到内容: $uri")
            Toast.makeText(context, "图片读取失败，请换一张图片重试", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            onBusy(true)
            try {
                val internalUri = saveImage(bytes)
                if (internalUri != null) {
                    onSaved(internalUri)
                } else {
                    Log.e("ReceiptImagePicker", "图片保存失败，共 ${bytes.size} 字节")
                    Toast.makeText(context, "图片保存失败，请重试", Toast.LENGTH_LONG).show()
                }
            } finally {
                onBusy(false)
            }
        }
    }

    return remember(picker) {
        {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}
