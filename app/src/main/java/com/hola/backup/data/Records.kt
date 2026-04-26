package com.hola.backup.data

import kotlinx.serialization.Serializable

@Serializable
data class SmsRecord(
    val id: String,
    val address: String?,
    val body: String?,
    val dateMillis: Long,
    val type: Int
)

@Serializable
data class CallLogRecord(
    val id: String,
    val number: String?,
    val dateMillis: Long,
    val durationSec: Long,
    val type: Int,
    val cachedName: String?
)

@Serializable
data class MediaMeta(
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val contentUri: String
)

@Serializable
data class BackupManifest(
    val createdAtEpochMs: Long,
    val device: String,
    val androidVersion: String,
    val includesSms: Boolean,
    val includesCallLogs: Boolean,
    val includesMediaFiles: Boolean,
    val includesTreeFiles: Boolean,
    val smsCount: Int,
    val callLogsCount: Int,
    val mediaCount: Int,
    val treeFileCount: Int
)
