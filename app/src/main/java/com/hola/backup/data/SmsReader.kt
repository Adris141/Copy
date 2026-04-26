package com.hola.backup.data

import android.content.Context
import android.provider.Telephony

class SmsReader(private val context: Context) {
    fun readAll(): List<SmsRecord> {
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf("_id", "address", "body", "date", "type")
        val result = mutableListOf<SmsRecord>()

        context.contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndex("_id")
            val addressIndex = cursor.getColumnIndex("address")
            val bodyIndex = cursor.getColumnIndex("body")
            val dateIndex = cursor.getColumnIndex("date")
            val typeIndex = cursor.getColumnIndex("type")

            while (cursor.moveToNext()) {
                result += SmsRecord(
                    id = cursor.getString(idIndex),
                    address = cursor.getString(addressIndex),
                    body = cursor.getString(bodyIndex),
                    dateMillis = cursor.getLong(dateIndex),
                    type = cursor.getInt(typeIndex)
                )
            }
        }
        return result
    }
}
