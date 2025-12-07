package com.example.flowmoney

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter

data class AppInfo(val name: String, val packageName: String, val icon: Drawable)

private fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return packages.filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }.map {
        AppInfo(
            name = it.loadLabel(pm).toString(),
            packageName = it.packageName,
            icon = it.loadIcon(pm)
        )
    }
}

@Composable
fun AppSelectionCard(selectedApp: AppInfo?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (selectedApp != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = rememberDrawablePainter(drawable = selectedApp.icon), contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(selectedApp.name, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Text("Nenhum app selecionado", style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = onClick) {
                Text(if (selectedApp == null) "Selecionar" else "Mudar")
            }
        }
    }
}

@Composable
fun InstalledAppsScreen(onAppSelected: (AppInfo) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = getInstalledApps(context)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(apps) { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAppSelected(app) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painter = rememberDrawablePainter(drawable = app.icon), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(app.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    categories: List<Category>,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onAddKeyword: (Category) -> Unit,
    onRemoveKeyword: (Category, String) -> Unit,
    prefs: AppPreferences,
    onTestModeChanged: (Boolean) -> Unit,
    onShowTestNotificationsChanged: (Boolean) -> Unit = {},
    onShowDeleteOptionChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val sortedCategories = remember(categories) {
        val (outros, others) = categories.partition { it.name == "Outros" }
        others.sortedBy { it.name } + outros
    }

    var showAppSelection by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showPermissionHelpDialog by remember { mutableStateOf(false) }
    var showTestModeDialog by remember { mutableStateOf(false) }
    var isFullAccessMode by remember { mutableStateOf(prefs.getFullAccessMode()) }
    var showTestNotifications by remember { mutableStateOf(prefs.getShowTestNotifications()) }
    var showDeleteOption by remember { mutableStateOf(prefs.getShowDeleteOption()) }

    if (showPermissionHelpDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionHelpDialog = false },
            title = { Text("Permissão de Acesso às Notificações") },
            text = { Text("Para que o FlowMoney consiga ler suas notificações de compra, é necessário conceder permissão de acesso. Clique em 'Abrir Configurações' e ative o FlowMoney na lista.") },
            confirmButton = {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        showPermissionHelpDialog = false
                    }
                ) {
                    Text("Abrir Configurações")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionHelpDialog = false }) {
                    Text("Agora não")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        val packageName = prefs.getSelectedApp()
        if (packageName != null) {
            val pm = context.packageManager
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                selectedApp = AppInfo(
                    name = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // App não está mais instalado
            }
        }
    }

    if (showAppSelection) {
        AlertDialog(
            onDismissRequest = { showAppSelection = false },
            title = { Text("Selecione um App") },
            text = {
                InstalledAppsScreen { app ->
                    selectedApp = app
                    prefs.saveSelectedApp(app.packageName)
                    showAppSelection = false
                }
            },
            confirmButton = { TextButton(onClick = { showAppSelection = false }) { Text("Cancelar") } }
        )
    }

    if (showTestModeDialog) {
        AlertDialog(
            onDismissRequest = { showTestModeDialog = false },
            title = { Text("Permissão Total") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Configure as opções de permissão total de forma independente:")
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("Permissão Total Ativa", fontWeight = FontWeight.SemiBold)
                            Text("Habilita as opções abaixo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        androidx.compose.material3.Switch(checked = isFullAccessMode, onCheckedChange = { isFullAccessMode = it })
                    }
                    
                    Divider()
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("Botão de Notificação Teste", fontWeight = FontWeight.SemiBold)
                            Text("Mostrar botão para enviar notificações", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        androidx.compose.material3.Switch(checked = showTestNotifications, onCheckedChange = { showTestNotifications = it }, enabled = isFullAccessMode)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("Opção de Deletar Cards", fontWeight = FontWeight.SemiBold)
                            Text("Permitir exclusão de registros", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        androidx.compose.material3.Switch(checked = showDeleteOption, onCheckedChange = { showDeleteOption = it }, enabled = isFullAccessMode)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    prefs.saveFullAccessMode(isFullAccessMode)
                    prefs.saveShowTestNotifications(showTestNotifications)
                    prefs.saveShowDeleteOption(showDeleteOption)
                    onTestModeChanged(isFullAccessMode)
                    onShowTestNotificationsChanged(showTestNotifications)
                    onShowDeleteOptionChanged(showDeleteOption)
                    showTestModeDialog = false
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showTestModeDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                actions = {
                    IconButton(onClick = { showPermissionHelpDialog = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Ajuda com permissões")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCategory,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, "Adicionar Categoria")
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Monitoramento de Notificações",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            item {
                AppSelectionCard(selectedApp = selectedApp) {
                    showAppSelection = true
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Permissão Total", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (isFullAccessMode) "Ativado" else "Desativado", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Button(onClick = { showTestModeDialog = true }) { Text("Configurar") }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                Text(
                    "Gerenciar Categorias e Palavras-Chave",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            items(sortedCategories) { category ->
                var isExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                category.name,
                                modifier = Modifier.weight(1f),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (category.name != "Outros") {
                                IconButton(onClick = { onEditCategory(category) }) {
                                    Icon(Icons.Filled.Edit, "Editar Nome", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { onDeleteCategory(category) }) {
                                    Icon(Icons.Filled.Delete, "Excluir Categoria", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            IconButton(onClick = { isExpanded = !isExpanded }) {
                                Icon(
                                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (isExpanded) "Recolher" else "Expandir",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Palavras-chave:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                category.keywords.forEach { keyword ->
                                    InputChip(
                                        selected = false,
                                        onClick = {},
                                        label = { Text(keyword) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Remover",
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable { onRemoveKeyword(category, keyword) }
                                            )
                                        }
                                    )
                                }
                                if (category.name != "Outros") {
                                    Button(
                                        onClick = { onAddKeyword(category) },
                                        modifier = Modifier.height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Adicionar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditDialog(
    initialValue: String = "",
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
