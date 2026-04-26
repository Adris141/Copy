package com.hola.backup.data

import com.hola.backup.domain.BackupFileFilters
import java.util.concurrent.TimeUnit

class BackupFileFilterMatcher(private val filters: BackupFileFilters) {
    private val includeExt = csv(filters.includeExtensionsCsv)
    private val excludeExt = csv(filters.excludeExtensionsCsv)
    private val includeMimePrefixes = csv(filters.includeMimePrefixesCsv)
    private val excludeMimePrefixes = csv(filters.excludeMimePrefixesCsv)
    private val excludeFolderNames = csv(filters.excludeFolderNamesCsv)

    fun shouldSkipFolder(folderName: String): Boolean {
        if (!filters.includeHiddenFiles && folderName.startsWith(".")) return true
        return excludeFolderNames.contains(folderName.lowercase())
    }

    fun matches(
        name: String,
        mimeType: String?,
        sizeBytes: Long,
        modifiedAtMillis: Long
    ): Boolean {
        if (!filters.includeHiddenFiles && name.startsWith(".")) return false
        if (sizeBytes < filters.minSizeBytes) return false
        if (filters.maxSizeBytes > 0 && sizeBytes > filters.maxSizeBytes) return false

        if (filters.modifiedWithinDays > 0 && modifiedAtMillis > 0) {
            val minTs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(filters.modifiedWithinDays.toLong())
            if (modifiedAtMillis < minTs) return false
        }

        val ext = extension(name)
        if (includeExt.isNotEmpty() && !includeExt.contains(ext)) return false
        if (excludeExt.contains(ext)) return false

        val mime = (mimeType ?: "").lowercase()
        if (includeMimePrefixes.isNotEmpty() && includeMimePrefixes.none { mime.startsWith(it) }) return false
        if (excludeMimePrefixes.any { mime.startsWith(it) }) return false

        return true
    }

    private fun extension(name: String): String {
        val idx = name.lastIndexOf('.')
        if (idx <= 0 || idx == name.length - 1) return ""
        return name.substring(idx + 1).lowercase()
    }

    private fun csv(raw: String): Set<String> {
        return raw
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
