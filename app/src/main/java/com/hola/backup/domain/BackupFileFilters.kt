package com.hola.backup.domain

data class BackupFileFilters(
    val includeExtensionsCsv: String = "",
    val excludeExtensionsCsv: String = "",
    val includeMimePrefixesCsv: String = "",
    val excludeMimePrefixesCsv: String = "",
    val excludeFolderNamesCsv: String = "Android,.thumbnails",
    val minSizeBytes: Long = 0,
    val maxSizeBytes: Long = 0,
    val modifiedWithinDays: Int = 0,
    val includeHiddenFiles: Boolean = false
)
