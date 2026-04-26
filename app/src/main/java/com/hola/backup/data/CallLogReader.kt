package com.hola.backup.data

import android.content.Context
import android.provider.CallLog

class CallLogReader(private val context: Context) {
    fun readAll(): List<CallLogRecord> {
        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME
        )
        val result = mutableListOf<CallLogRecord>()

        context.contentResolver.query(uri, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val number = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val date = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val duration = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val type = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val name = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)

            while (cursor.moveToNext()) {
                result += CallLogRecord(
                    id = cursor.getString(id),
                    number = cursor.getString(number),
                    dateMillis = cursor.getLong(date),
                    durationSec = cursor.getLong(duration),
                    type = cursor.getInt(type),
                    cachedName = cursor.getString(name)
                )
            }
        }
        return result
    }
}
