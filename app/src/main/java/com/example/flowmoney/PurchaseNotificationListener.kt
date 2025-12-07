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

    private val TAG = "PurchaseListener"

    // Static keyword map for the service. The UI will have the dynamic version.
    private val categoryKeywordMap = mapOf(
        "Alimentação" to listOf("Padaria", "Supermercado", "iFood"),
        "Assinaturas" to listOf("Netflix", "Spotify", "Disney+"),
        "Gasolina" to listOf("Posto"),
        "Saúde" to listOf("Farmácia", "Droga"),
        "Compras" to listOf("Amazon", "Mercado Livre")
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val notification = sbn?.notification ?: return
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT)?.toString()

        Log.d(TAG, "Notification received: Title='$title', Text='$text'")

        val purchaseKeywords = listOf("compra", "pagamento", "realizada")

        if (purchaseKeywords.any { title.contains(it, ignoreCase = true) } && text != null) {
            val pattern = Pattern.compile("""Compra de R\$ (.*) em (.*)\.""")
            val matcher = pattern.matcher(text)

            if (matcher.find()) {
                val value = matcher.group(1)
                val establishment = matcher.group(2)
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val category = getCategoryForEstablishment(establishment)

                Log.d(TAG, "Purchase Detected!")

                val intent = Intent(ACTION_PURCHASE_NOTIFICATION)
                intent.setPackage(packageName)
                intent.putExtra(EXTRA_VALUE, value)
                intent.putExtra(EXTRA_ESTABLISHMENT, establishment)
                intent.putExtra(EXTRA_TIMESTAMP, timestamp)
                intent.putExtra(EXTRA_CATEGORY, category)
                sendBroadcast(intent)
            }
        }
    }

    private fun getCategoryForEstablishment(establishment: String): String {
        for ((category, keywords) in categoryKeywordMap) {
            if (keywords.any { establishment.contains(it, ignoreCase = true) }) {
                return category
            }
        }
        return "Outros"
    }

    companion object {
        const val ACTION_PURCHASE_NOTIFICATION = "com.example.flowmoney.PURCHASE_NOTIFICATION"
        const val EXTRA_VALUE = "extra_value"
        const val EXTRA_ESTABLISHMENT = "extra_establishment"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_CATEGORY = "extra_category"
    }
}
