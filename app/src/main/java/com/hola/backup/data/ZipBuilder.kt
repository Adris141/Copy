package com.hola.backup.data

import android.content.ContentResolver
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipBuilder(private val contentResolver: ContentResolver) {
    fun buildBackupZip(
        outputZip: File,
        manifestJson: String,
        smsJson: String?,
        callsJson: String?,
        files: List<BackupBinaryFile>
    ) {
        ZipOutputStream(FileOutputStream(outputZip)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson.toByteArray())
            zip.closeEntry()

            if (smsJson != null) {
                zip.putNextEntry(ZipEntry("sms.json"))
                zip.write(smsJson.toByteArray())
                zip.closeEntry()
            }

            if (callsJson != null) {
                zip.putNextEntry(ZipEntry("call_logs.json"))
                zip.write(callsJson.toByteArray())
                zip.closeEntry()
            }

            for (file in files) {
                zip.putNextEntry(ZipEntry(file.zipPath))
                contentResolver.openInputStream(file.uri)?.use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
    }
}
