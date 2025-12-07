package com.example.flowmoney

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class PurchaseNotificationListener : NotificationListenerService() {

    companion object {
        const val ACTION_PURCHASE_NOTIFICATION = "com.example.flowmoney.ACTION_PURCHASE_NOTIFICATION"
        const val EXTRA_VALUE = "extra_value"
        const val EXTRA_ESTABLISHMENT = "extra_establishment"
        const val EXTRA_IS_FULL_ACCESS = "extra_is_full_access"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        private const val TAG = "PurchaseListener"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = AppPreferences(applicationContext)
        val selectedApp = prefs.getSelectedApp()

        val packageName = sbn.packageName
        val notification = sbn.notification
        var title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        var text = notification.extras.getString(Notification.EXTRA_TEXT) ?: ""

        Log.d(TAG, "Notification received from: $packageName")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "Text: $text")
        Log.d(TAG, "Selected app: $selectedApp")


        if (selectedApp == null || packageName == selectedApp) {
            if (selectedApp == null) {
                Log.i(TAG, "No app selected, processing notification from any app.")
            } else {
                Log.i(TAG, "Notification is from the selected app. Processing...")
            }

            // detect full-access marker and strip it if present
            val fullAccessMarker = "[FULL_ACCESS]"
            var isFullAccessNotification = false
            if (title.contains(fullAccessMarker)) {
                isFullAccessNotification = true
                title = title.replace(fullAccessMarker, "").trim()
            }
            if (text.contains(fullAccessMarker)) {
                isFullAccessNotification = true
                text = text.replace(fullAccessMarker, "").trim()
            }

            val purchaseDetails = extractPurchaseDetails(title, text)

            if (purchaseDetails != null) {
                val (value, establishment) = purchaseDetails
                val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(sbn.postTime))

                Log.i(TAG, "Purchase detected! Value: $value, Establishment: $establishment")

                val intent = Intent(ACTION_PURCHASE_NOTIFICATION).apply {
                    putExtra(EXTRA_VALUE, value)
                    putExtra(EXTRA_ESTABLISHMENT, establishment)
                    putExtra(EXTRA_TIMESTAMP, timestamp)
                    putExtra(EXTRA_IS_FULL_ACCESS, isFullAccessNotification)
                }
                sendBroadcast(intent)
                Log.i(TAG, "Broadcast sent for purchase (isFullAccess=$isFullAccessNotification)")
            } else {
                Log.d(TAG, "No purchase details found in the notification.")
            }
        } else {
            Log.d(TAG, "Notification is from a different app ($packageName). Ignoring.")
        }
    }

    private fun extractPurchaseDetails(title: String, text: String): Pair<String, String>? {
        val patterns = listOf(
            Pattern.compile("Compra de R\\$\\s?([\\d,.]+) APROVADA em (.*?)[,.]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("compra de r\\$\\s?([\\d,.]+) em (.*?)[,.]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("compra aprovada no valor de r\\$\\s?([\\d,.]+) em (.*?)(?:\\.|,|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("você usou r\\$\\s?([\\d,.]+) em (.*?)[,.]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Compra de R\\$\\s?([\\d,.]+) em (.*?)[,.]?$", Pattern.CASE_INSENSITIVE)
        )

        val combinedText = "$title. $text"
        Log.d(TAG, "Combined text for regex matching: $combinedText")

        for (pattern in patterns) {
            val matcher = pattern.matcher(combinedText)
            if (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    val value = matcher.group(1)!!.replace(',', '.')
                    val establishment = matcher.group(2)!!.trim()
                    Log.d(TAG, "Pattern matched: ${pattern.pattern()}")
                    return value to establishment
                }
            }
        }
        Log.d(TAG, "No pattern matched.")
        return null
    }
}
