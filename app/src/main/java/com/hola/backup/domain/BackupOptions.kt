package com.hola.backup.domain

data class BackupOptions(
    val includeSms: Boolean,
    val includeCallLogs: Boolean,
    val includeMediaFiles: Boolean,
    val includeTreeFiles: Boolean = false,
    val treeUri: String? = null,
    val fileFilters: BackupFileFilters = BackupFileFilters(),
    val maxMediaFiles: Int = 500,
    val maxTreeFiles: Int = 1000
)
