package com.hola.backup.data

import android.content.Context
import com.hola.backup.domain.BackupFileFilters
import com.hola.backup.domain.BackupOptions

data class BackupSettings(
    val botToken: String,
    val chatId: String,
    val options: BackupOptions,
    val scheduleDaily: Boolean
)

class BackupPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    fun save(settings: BackupSettings) {
        prefs.edit()
            .putString(KEY_BOT_TOKEN, settings.botToken)
            .putString(KEY_CHAT_ID, settings.chatId)
            .putBoolean(KEY_SMS, settings.options.includeSms)
            .putBoolean(KEY_CALLS, settings.options.includeCallLogs)
            .putBoolean(KEY_MEDIA, settings.options.includeMediaFiles)
            .putBoolean(KEY_TREE_FILES, settings.options.includeTreeFiles)
            .putString(KEY_TREE_URI, settings.options.treeUri)
            .putString(KEY_INCLUDE_EXT, settings.options.fileFilters.includeExtensionsCsv)
            .putString(KEY_EXCLUDE_EXT, settings.options.fileFilters.excludeExtensionsCsv)
            .putString(KEY_INCLUDE_MIME, settings.options.fileFilters.includeMimePrefixesCsv)
            .putString(KEY_EXCLUDE_MIME, settings.options.fileFilters.excludeMimePrefixesCsv)
            .putString(KEY_EXCLUDE_FOLDERS, settings.options.fileFilters.excludeFolderNamesCsv)
            .putLong(KEY_MIN_SIZE, settings.options.fileFilters.minSizeBytes)
            .putLong(KEY_MAX_SIZE, settings.options.fileFilters.maxSizeBytes)
            .putInt(KEY_MODIFIED_DAYS, settings.options.fileFilters.modifiedWithinDays)
            .putBoolean(KEY_INCLUDE_HIDDEN, settings.options.fileFilters.includeHiddenFiles)
            .putInt(KEY_MAX_MEDIA, settings.options.maxMediaFiles)
            .putInt(KEY_MAX_TREE, settings.options.maxTreeFiles)
            .putBoolean(KEY_DAILY, settings.scheduleDaily)
            .apply()
    }

    fun load(): BackupSettings {
        return BackupSettings(
            botToken = prefs.getString(KEY_BOT_TOKEN, "") ?: "",
            chatId = prefs.getString(KEY_CHAT_ID, "") ?: "",
            options = BackupOptions(
                includeSms = prefs.getBoolean(KEY_SMS, true),
                includeCallLogs = prefs.getBoolean(KEY_CALLS, true),
                includeMediaFiles = prefs.getBoolean(KEY_MEDIA, true),
                includeTreeFiles = prefs.getBoolean(KEY_TREE_FILES, false),
                treeUri = prefs.getString(KEY_TREE_URI, null),
                fileFilters = BackupFileFilters(
                    includeExtensionsCsv = prefs.getString(KEY_INCLUDE_EXT, "") ?: "",
                    excludeExtensionsCsv = prefs.getString(KEY_EXCLUDE_EXT, "") ?: "",
                    includeMimePrefixesCsv = prefs.getString(KEY_INCLUDE_MIME, "") ?: "",
                    excludeMimePrefixesCsv = prefs.getString(KEY_EXCLUDE_MIME, "") ?: "",
                    excludeFolderNamesCsv = prefs.getString(KEY_EXCLUDE_FOLDERS, "Android,.thumbnails")
                        ?: "Android,.thumbnails",
                    minSizeBytes = prefs.getLong(KEY_MIN_SIZE, 0L),
                    maxSizeBytes = prefs.getLong(KEY_MAX_SIZE, 0L),
                    modifiedWithinDays = prefs.getInt(KEY_MODIFIED_DAYS, 0),
                    includeHiddenFiles = prefs.getBoolean(KEY_INCLUDE_HIDDEN, false)
                ),
                maxMediaFiles = prefs.getInt(KEY_MAX_MEDIA, 500),
                maxTreeFiles = prefs.getInt(KEY_MAX_TREE, 1000)
            ),
            scheduleDaily = prefs.getBoolean(KEY_DAILY, false)
        )
    }

    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_SMS = "include_sms"
        private const val KEY_CALLS = "include_calls"
        private const val KEY_MEDIA = "include_media"
        private const val KEY_TREE_FILES = "include_tree_files"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_INCLUDE_EXT = "include_ext"
        private const val KEY_EXCLUDE_EXT = "exclude_ext"
        private const val KEY_INCLUDE_MIME = "include_mime"
        private const val KEY_EXCLUDE_MIME = "exclude_mime"
        private const val KEY_EXCLUDE_FOLDERS = "exclude_folders"
        private const val KEY_MIN_SIZE = "min_size"
        private const val KEY_MAX_SIZE = "max_size"
        private const val KEY_MODIFIED_DAYS = "modified_days"
        private const val KEY_INCLUDE_HIDDEN = "include_hidden"
        private const val KEY_MAX_MEDIA = "max_media"
        private const val KEY_MAX_TREE = "max_tree"
        private const val KEY_DAILY = "daily"
    }
}
