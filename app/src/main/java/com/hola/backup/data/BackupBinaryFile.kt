package com.hola.backup.data

import android.net.Uri

data class BackupBinaryFile(
    val uri: Uri,
    val zipPath: String,
    val mimeType: String?,
    val sizeBytes: Long
)
