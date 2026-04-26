package com.hola.backup.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class TreeUriReader(private val context: Context) {
    data class TreeFile(
        val uri: Uri,
        val name: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val relativePath: String
    )

    fun readFiles(treeUri: String, limit: Int, matcher: BackupFileFilterMatcher): List<TreeFile> {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return emptyList()
        val out = mutableListOf<TreeFile>()
        walk(root, "", out, limit, matcher)
        return out
    }

    private fun walk(
        dir: DocumentFile,
        pathPrefix: String,
        out: MutableList<TreeFile>,
        limit: Int,
        matcher: BackupFileFilterMatcher
    ) {
        if (!dir.isDirectory || out.size >= limit) return

        dir.listFiles().forEach { child ->
            if (out.size >= limit) return@forEach
            val safeName = (child.name ?: "unnamed").replace("/", "_")
            if (child.isDirectory && matcher.shouldSkipFolder(safeName)) return@forEach
            val relative = if (pathPrefix.isBlank()) safeName else "$pathPrefix/$safeName"
            if (child.isDirectory) {
                walk(child, relative, out, limit, matcher)
            } else if (child.isFile) {
                if (matcher.matches(
                        name = safeName,
                        mimeType = child.type,
                        sizeBytes = child.length(),
                        modifiedAtMillis = child.lastModified()
                    )
                ) {
                    out += TreeFile(
                        uri = child.uri,
                        name = safeName,
                        mimeType = child.type,
                        sizeBytes = child.length(),
                        relativePath = relative
                    )
                }
            }
        }
    }
}
