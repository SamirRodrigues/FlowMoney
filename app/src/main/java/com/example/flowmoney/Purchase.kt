package com.example.flowmoney

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Locale

@Parcelize
data class Purchase(
    val value: String,
    val establishment: String,
    val timestamp: String,
    val category: String
) : Parcelable {
    fun getYearAndMonth(): Pair<Int, Int>? {
        return try {
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val date = format.parse(timestamp)
            if (date != null) {
                val calendar = java.util.Calendar.getInstance()
                calendar.time = date
                Pair(calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
