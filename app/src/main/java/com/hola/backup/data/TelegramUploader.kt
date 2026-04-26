package com.hola.backup.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.RandomAccessFile

class TelegramUploader(
    private val client: OkHttpClient = OkHttpClient()
) {
    fun uploadBackup(botToken: String, chatId: String, file: File) {
        val maxPart = 49L * 1024L * 1024L
        if (file.length() <= maxPart) {
            sendPart(botToken, chatId, file, caption = "backup")
            return
        }

        val parts = splitFile(file, maxPart)
        try {
            parts.forEachIndexed { index, part ->
                sendPart(
                    botToken = botToken,
                    chatId = chatId,
                    file = part,
                    caption = "backup part ${index + 1}/${parts.size}"
                )
            }
        } finally {
            parts.forEach { it.delete() }
        }
    }

    private fun sendPart(botToken: String, chatId: String, file: File, caption: String) {
        val url = "https://api.telegram.org/bot$botToken/sendDocument"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption)
            .addFormDataPart(
                "document",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                error("Telegram upload failed (${response.code}): $errorBody")
            }
        }
    }

    private fun splitFile(input: File, maxBytesPerPart: Long): List<File> {
        val output = mutableListOf<File>()
        val totalParts = ((input.length() + maxBytesPerPart - 1) / maxBytesPerPart).toInt()

        RandomAccessFile(input, "r").use { raf ->
            val buffer = ByteArray(1024 * 1024)
            for (partIndex in 0 until totalParts) {
                val partFile = File(input.parentFile, "${input.name}.part${partIndex + 1}")
                partFile.outputStream().use { out ->
                    var remaining = minOf(maxBytesPerPart, input.length() - (partIndex * maxBytesPerPart))
                    while (remaining > 0) {
                        val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                output += partFile
            }
        }
        return output
    }
}
