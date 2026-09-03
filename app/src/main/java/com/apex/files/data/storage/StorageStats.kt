package com.apex.files.data.storage

import android.os.StatFs

object StorageStats {

    data class Usage(val totalBytes: Long, val usedBytes: Long, val availableBytes: Long)

    fun usageOf(path: String): Usage? = try {
        val stat = StatFs(path)
        val blockSize = stat.blockSizeLong
        val total = blockSize * stat.blockCountLong
        val available = blockSize * stat.availableBlocksLong
        Usage(totalBytes = total, usedBytes = total - available, availableBytes = available)
    } catch (e: Exception) {
        null
    }
}