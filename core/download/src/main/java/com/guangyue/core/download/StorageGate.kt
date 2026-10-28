package com.guangyue.core.download

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * 下载前的存储门禁。
 *
 * - API 29+：写入 `Music/GuangYue/` 走 MediaStore，**不需要任何权限**；
 * - API 26~28：需要 `WRITE_EXTERNAL_STORAGE` 运行时权限。
 *
 * UI 在点击「下载」时先调 [prepare]，返回 [StorageGate.NeedPermission] 就发权限请求，
 * 授权后重试同一个动作即可。
 */
object StorageGate {

    sealed interface Result {
        /** 可以直接下载。 */
        data object Ready : Result

        /** 需要用户授予 [permission]。 */
        data class NeedPermission(val permission: String) : Result

        /** 外部存储不可用（只读 / 未挂载）。 */
        data class Unavailable(val reason: String) : Result
    }

    fun prepare(context: Context): Result {
        if (!isExternalStorageWritable()) {
            return Result.Unavailable("外部存储不可用，请检查存储状态")
        }
        return if (PublicMusicStorage.needsPermission(context)) {
            Result.NeedPermission(PublicMusicStorage.requiredPermission)
        } else {
            Result.Ready
        }
    }

    /** 目标目录（给「前往文件夹」用）。 */
    fun targetDir(): String = DownloadPaths.relativeDir()

    /** 当前系统是否真的需要那个权限（UI 用来决定是否请求）。 */
    fun requiresLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private fun isExternalStorageWritable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    /** 权限是否已经授予（仅 API 26~28 有意义）。 */
    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
}
