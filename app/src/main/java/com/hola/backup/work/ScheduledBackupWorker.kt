package com.hola.backup.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hola.backup.data.BackupPrefs
import com.hola.backup.data.CallLogReader
import com.hola.backup.data.MediaReader
import com.hola.backup.data.SmsReader
import com.hola.backup.data.TelegramUploader
import com.hola.backup.data.TreeUriReader
import com.hola.backup.data.ZipBuilder
import com.hola.backup.domain.BackupOrchestrator
import java.io.File

class ScheduledBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val prefs = BackupPrefs(applicationContext).load()
            if (prefs.botToken.isBlank() || prefs.chatId.isBlank()) {
                error("Missing bot credentials")
            }

            val orchestrator = BackupOrchestrator(
                smsReader = SmsReader(applicationContext),
                callLogReader = CallLogReader(applicationContext),
                mediaReader = MediaReader(applicationContext),
                treeUriReader = TreeUriReader(applicationContext),
                zipBuilder = ZipBuilder(applicationContext.contentResolver),
                telegramUploader = TelegramUploader()
            )

            orchestrator.runBackup(
                options = prefs.options,
                outputDir = File(applicationContext.filesDir, "backups"),
                botToken = prefs.botToken,
                chatId = prefs.chatId
            )
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
