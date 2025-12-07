package com.example.flowmoney

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

@Composable
fun HandlePermissions(activity: Activity) {
    var showPermissionDialog by remember { mutableStateOf(false) }

    fun hasNotificationListenerPermission(context: Context): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }

    LaunchedEffect(Unit) {
        if (!hasNotificationListenerPermission(activity)) {
            showPermissionDialog = true
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permissão Necessária") },
            text = { Text("Para o aplicativo funcionar corretamente, por favor, conceda a permissão para escutar as notificações.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        activity.startActivity(intent)
                        showPermissionDialog = false
                    }
                ) {
                    Text("Abrir Configurações")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}