package com.hola.backup.domain

import android.os.Build
import com.hola.backup.data.BackupManifest
import com.hola.backup.data.BackupBinaryFile
import com.hola.backup.data.CallLogReader
import com.hola.backup.data.BackupFileFilterMatcher
import com.hola.backup.data.MediaMeta
import com.hola.backup.data.MediaReader
import com.hola.backup.data.SmsReader
import com.hola.backup.data.TelegramUploader
import com.hola.backup.data.TreeUriReader
import com.hola.backup.data.ZipBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class BackupOrchestrator(
    private val smsReader: SmsReader,
    private val callLogReader: CallLogReader,
    private val mediaReader: MediaReader,
    private val treeUriReader: TreeUriReader,
    private val zipBuilder: ZipBuilder,
    private val telegramUploader: TelegramUploader
) {
    data class Result(
        val zipPath: String,
        val smsCount: Int,
        val callCount: Int,
        val mediaCount: Int,
        val treeCount: Int
    )

    fun runBackup(
        options: BackupOptions,
        outputDir: File,
        botToken: String,
        chatId: String
    ): Result {
        outputDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        val zip = File(outputDir, "backup_$timestamp.zip")
        val matcher = BackupFileFilterMatcher(options.fileFilters)

        val sms = if (options.includeSms) smsReader.readAll() else emptyList()
        val calls = if (options.includeCallLogs) callLogReader.readAll() else emptyList()
        val media = if (options.includeMediaFiles) {
            mediaReader.readRecent(options.maxMediaFiles).filter {
                matcher.matches(
                    name = it.displayName,
                    mimeType = it.mimeType,
                    sizeBytes = it.sizeBytes,
                    modifiedAtMillis = it.modifiedAtMillis
                )
            }
        } else {
            emptyList()
        }
        val treeFiles = if (options.includeTreeFiles && !options.treeUri.isNullOrBlank()) {
            treeUriReader.readFiles(options.treeUri, options.maxTreeFiles, matcher)
        } else {
            emptyList()
        }

        val manifest = BackupManifest(
            createdAtEpochMs = timestamp,
            device = Build.MODEL ?: "unknown",
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            includesSms = options.includeSms,
            includesCallLogs = options.includeCallLogs,
            includesMediaFiles = options.includeMediaFiles,
            includesTreeFiles = options.includeTreeFiles,
            smsCount = sms.size,
            callLogsCount = calls.size,
            mediaCount = media.size,
            treeFileCount = treeFiles.size
        )

        val json = Json { prettyPrint = true }
        val mediaMeta = media.map {
            MediaMeta(
                displayName = it.displayName,
                mimeType = it.mimeType,
                sizeBytes = it.sizeBytes,
                contentUri = it.uri.toString()
            )
        }

        val zipFiles = mutableListOf<BackupBinaryFile>()
        zipFiles += media.map {
            BackupBinaryFile(
                uri = it.uri,
                zipPath = "media/${it.displayName.replace("/", "_")}",
                mimeType = it.mimeType,
                sizeBytes = it.sizeBytes
            )
        }
        zipFiles += treeFiles.map {
            BackupBinaryFile(
                uri = it.uri,
                zipPath = "files/${it.relativePath}",
                mimeType = it.mimeType,
                sizeBytes = it.sizeBytes
            )
        }

        zipBuilder.buildBackupZip(
            outputZip = zip,
            manifestJson = json.encodeToString(manifest),
            smsJson = if (sms.isNotEmpty()) json.encodeToString(sms) else null,
            callsJson = if (calls.isNotEmpty()) json.encodeToString(calls) else null,
            files = zipFiles
        )

        val metaFile = File(outputDir, "backup_${timestamp}_media_meta.json")
        metaFile.writeText(json.encodeToString(mediaMeta))

        telegramUploader.uploadBackup(botToken, chatId, zip)

        return Result(
            zipPath = zip.absolutePath,
            smsCount = sms.size,
            callCount = calls.size,
            mediaCount = media.size,
            treeCount = treeFiles.size
        )
    }
}
