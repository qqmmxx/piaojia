package com.qqmmxx.piaojia.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 图片管理器：负责把用户选中的图片复制到应用私有目录，以及删除不再使用的图片。
 *
 * 设计要点：
 * - 应用内图片一律以 FileProvider 的 content:// URI 形式记录，因为 file:// URI 在
 *   Android 7.0+ 上跨进程传递会触发 FileUriExposedException。
 * - 因此「解析 URI -> 真实文件」必须走 [getImageFile]，绝不能直接拿 uri.path 当路径用。
 */
class ImageManager(private val context: Context) {

    companion object {
        private const val TAG = "ImageManager"
        private const val IMAGE_DIR = "expense_images"
        private const val IMAGE_QUALITY = 90
        private const val FILE_PROVIDER_AUTHORITY = "com.qqmmxx.piaojia.fileprovider"

        /** 解码时的最大边长，超过就降采样，避免大图直接 OOM */
        private const val MAX_BITMAP_DIMENSION = 2048
    }

    private fun getImageDirectory(): File =
        File(context.filesDir, IMAGE_DIR).apply { if (!exists()) mkdirs() }

    /**
     * 把外部图片复制到应用私有目录。
     * 返回应用内 FileProvider URI；失败返回 null（调用方需要处理 null，不要假设一定成功）。
     */
    suspend fun copyImageToInternal(sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            // 先从内容提供者一次性读出字节，再交给 decodeByteArray 处理。
            // 走 decodeStream 需要重复打开两次输入流，对「授权只在一瞬间有效」的
            // 照片选择器 URI 来说风险太大。
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                Log.e(TAG, "无法打开图片输入流或内容为空: $sourceUri")
                return@withContext null
            }
            saveReceiptBytes(bytes, sourceUri.toString())
        } catch (e: Exception) {
            Log.e(TAG, "复制图片失败: $sourceUri", e)
            null
        }
    }

    /**
     * 把已经在内存里的图片字节存成应用私有 JPEG。
     *
     * 为什么需要这个入口：系统照片选择器返回的 content://media/picker/... 授权是**瞬时**的
     * —— 实测在结果回调里同步读取能拿到数据，但哪怕只延后十几毫秒（丢进协程）就已经读不到了。
     * 所以必须由选图回调同步 readBytes()，再把字节交到这里异步解码落盘。
     */
    suspend fun copyReceiptBytesToInternal(bytes: ByteArray): Uri? = withContext(Dispatchers.IO) {
        saveReceiptBytes(bytes, "内存字节")
    }

    private fun saveReceiptBytes(bytes: ByteArray, origin: String): Uri? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.e(TAG, "不是可识别的图片: $origin")
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        // decodeByteArray 对损坏/不支持的数据会返回 null，直接 .compress 会 NPE 崩溃
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        if (bitmap == null) {
            Log.e(TAG, "图片解码失败: $origin")
            return null
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        val imageFile = File(getImageDirectory(), "IMG_${timeStamp}_${UUID.randomUUID()}.jpg")
        try {
            FileOutputStream(imageFile).use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)) {
                    throw IllegalStateException("图片压缩失败")
                }
                outputStream.flush()
            }
        } finally {
            bitmap.recycle()
        }

        Log.d(TAG, "图片已存入应用内存储: ${imageFile.absolutePath}")
        return getContentUri(imageFile)
    }

    /** 按最大边长计算 2 的幂次降采样比例 */
    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w / 2 >= MAX_BITMAP_DIMENSION && h / 2 >= MAX_BITMAP_DIMENSION) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun getContentUri(file: File): Uri =
        FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)

    /**
     * 批量复制，逐个容错，自动跳过失败项。
     *
     * 对已经是应用内私有图片的 URI 直接原样返回，保证幂等：
     * 选图时已经拷过一次并换成了 FileProvider URI，保存时不该再拷出第二份。
     * 反过来说，外部 URI 只在这里兜底——正常情况下不应该走到这条分支。
     */
    suspend fun copyImagesToInternal(sourceUris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        sourceUris.mapNotNull { uri ->
            if (isInternalImage(uri)) uri else copyImageToInternal(uri)
        }
    }

    /**
     * 删除一张应用内图片。
     * 关键修复：原先用 File(imageUri.path)，而 FileProvider URI 的 path 是
     * "/expense_images/xxx.jpg"，这个路径在文件系统里不存在，导致图片永远删不掉。
     */
    suspend fun deleteImage(imageUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getImageFile(imageUri)
            if (file != null && file.exists() && file.isFile) {
                val deleted = file.delete()
                Log.d(TAG, "删除图片${if (deleted) "成功" else "失败"}: ${file.absolutePath}")
                deleted
            } else {
                Log.w(TAG, "待删除的图片不存在，跳过: $imageUri")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除图片失败: $imageUri", e)
            false
        }
    }

    suspend fun deleteImages(imageUris: List<Uri>) = withContext(Dispatchers.IO) {
        imageUris.forEach { deleteImage(it) }
    }

    /** 是否为应用内私有图片（外部相册的原图我们不能删） */
    fun isInternalImage(uri: Uri): Boolean {
        if (uri.authority == FILE_PROVIDER_AUTHORITY) return true
        val path = uri.path ?: return false
        return path.contains("/$IMAGE_DIR/")
    }

    /**
     * URI -> 应用内文件。仅处理应用私有目录下的图片。
     * 通过 FileProvider URI 的路径段解析，而不是把 uri.path 当绝对路径用。
     */
    fun getImageFile(uri: Uri): File? {
        return try {
            val fileName = when {
                uri.authority == FILE_PROVIDER_AUTHORITY -> uri.lastPathSegment
                uri.scheme == "file" -> uri.path?.let { File(it).name }
                else -> uri.lastPathSegment
            } ?: return null

            val file = File(getImageDirectory(), fileName)
            if (file.exists() && file.isFile) file else null
        } catch (e: Exception) {
            Log.e(TAG, "获取图片文件失败: $uri", e)
            null
        }
    }

    /**
     * 清理未被任何报销记录引用的图片文件。
     *
     * 注意：原实现用 uri.path 和 file.absolutePath 比较，两者永远不相等，
     * 一旦被调用就会删光所有图片。这里改为统一按文件名比对。
     *
     * @param usedImageUris 当前所有仍在使用的图片 URI
     * @param excludeFileNames 额外需要保留的文件名（例如正在导出的图片）
     */
    suspend fun cleanUnusedImages(
        usedImageUris: List<Uri>,
        excludeFileNames: Set<String> = emptySet()
    ): Int = withContext(Dispatchers.IO) {
        val imageDir = getImageDirectory()
        val allFiles = imageDir.listFiles() ?: return@withContext 0

        val usedNames = usedImageUris
            .mapNotNull { getImageFile(it)?.name }
            .toSet() + excludeFileNames

        var deleted = 0
        allFiles.forEach { file ->
            if (file.isFile && file.name !in usedNames) {
                if (file.delete()) {
                    deleted++
                    Log.d(TAG, "删除未使用图片: ${file.name}")
                }
            }
        }
        deleted
    }
}
