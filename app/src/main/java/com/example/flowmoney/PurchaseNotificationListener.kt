package com.example.flowmoney

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PurchaseNotificationListener : NotificationListenerService() {

    companion object {
        const val ACTION_PURCHASE_NOTIFICATION = "com.example.flowmoney.ACTION_PURCHASE_NOTIFICATION"
        const val EXTRA_VALUE = "extra_value"
        const val EXTRA_ESTABLISHMENT = "extra_establishment"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = AppPreferences(applicationContext)
        val selectedApp = prefs.getSelectedApp()

        if (sbn.packageName == selectedApp) {
            val notification = sbn.notification
            val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = notification.extras.getString(Notification.EXTRA_TEXT) ?: ""

            val purchaseDetails = extractPurchaseDetails(text)

            if (purchaseDetails != null) {
                val (value, establishment) = purchaseDetails
                val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(sbn.postTime))

                val intent = Intent(ACTION_PURCHASE_NOTIFICATION).apply {
                    putExtra(EXTRA_VALUE, value)
                    putExtra(EXTRA_ESTABLISHMENT, establishment)
                    putExtra(EXTRA_TIMESTAMP, timestamp)
                }
                sendBroadcast(intent)
            }
        }
    }

    private fun extractPurchaseDetails(notificationText: String): Pair<String, String>? {
        val regex = Regex("Compra de R\\$ (\\d+\\.\\d{2}) em (.*?)\\.?$", RegexOption.IGNORE_CASE)
        val matchResult = regex.find(notificationText)
        return if (matchResult != null && matchResult.groupValues.size == 3) {
            matchResult.groupValues[1] to matchResult.groupValues[2]
        } else {
            null
        }
    }
}