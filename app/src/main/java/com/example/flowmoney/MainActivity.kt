package com.example.flowmoney

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowmoney.ui.theme.FlowMoneyTheme
import com.example.flowmoney.ui.theme.Typography
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper(this).createNotificationChannel()
        enableEdgeToEdge()
        setContent {
            FlowMoneyTheme {
                FlowMoneyApp()
            }
        }
    }
}

data class Category(val name: String, val keywords: List<String>)

private fun getCategoryForEstablishment(establishment: String, categories: List<Category>): String {
    for (category in categories) {
        if (category.keywords.any { establishment.contains(it, ignoreCase = true) }) {
            return category.name
        }
    }
    return "Outros"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@PreviewScreenSizes
@Composable
fun FlowMoneyApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var purchaseToEdit by remember { mutableStateOf<Purchase?>(null) }
    var categories by remember {
        mutableStateOf(listOf(
            Category("Alimentação", listOf("Padaria", "Supermercado", "iFood")),
            Category("Assinaturas", listOf("Netflix", "Spotify", "Disney+")),
            Category("Gasolina", listOf("Posto")),
            Category("Saúde", listOf("Farmácia", "Droga")),
            Category("Compras", listOf("Amazon", "Mercado Livre")),
            Category("Outros", emptyList())
        ))
    }
    val context = LocalContext.current
    val notificationHelper = NotificationHelper(context)
    var purchaseList by remember { mutableStateOf(listOf<Purchase>()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var categoryForNewKeyword by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(currentDestination) {
        if (currentDestination != AppDestinations.HOME) selectedCategory = null
    }

    val onPurchaseClick: (Purchase) -> Unit = { purchase -> purchaseToEdit = purchase }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> if (isGranted) sendRandomPurchaseNotification(notificationHelper) }
    )

    val broadcastReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == PurchaseNotificationListener.ACTION_PURCHASE_NOTIFICATION) {
                    val value = intent.getStringExtra(PurchaseNotificationListener.EXTRA_VALUE) ?: ""
                    val establishment = intent.getStringExtra(PurchaseNotificationListener.EXTRA_ESTABLISHMENT) ?: ""
                    val timestamp = intent.getStringExtra(PurchaseNotificationListener.EXTRA_TIMESTAMP) ?: ""
                    val category = getCategoryForEstablishment(establishment, categories)
                    purchaseList = purchaseList + Purchase(value, establishment, timestamp, category)
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter(PurchaseNotificationListener.ACTION_PURCHASE_NOTIFICATION)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        context.registerReceiver(broadcastReceiver, filter, flags)
        onDispose { context.unregisterReceiver(broadcastReceiver) }
    }

    // --- DIALOGS ---
    purchaseToEdit?.let {
        EditPurchaseDialog(purchase = it, categories = categories.map { c -> c.name }, onDismiss = { purchaseToEdit = null }, onConfirm = { updatedPurchase ->
            purchaseList = purchaseList.map { p -> if (p.timestamp == updatedPurchase.timestamp && p.establishment == updatedPurchase.establishment) updatedPurchase else p }
            purchaseToEdit = null
        })
    }

    if (showAddCategoryDialog) {
        CategoryEditDialog(onDismiss = { showAddCategoryDialog = false }, onConfirm = { newName ->
            if (newName.isNotBlank() && categories.none { it.name.equals(newName, ignoreCase = true) }) {
                categories = categories + Category(newName, emptyList())
            }
            showAddCategoryDialog = false
        }, title = "Adicionar Categoria", label = "Nome da Categoria")
    }

    categoryToEdit?.let { oldCategory ->
        CategoryEditDialog(initialValue = oldCategory.name, onDismiss = { categoryToEdit = null }, onConfirm = { newName ->
            if (newName.isNotBlank() && categories.none { it.name.equals(newName, ignoreCase = true) }) {
                categories = categories.map { if (it.name == oldCategory.name) it.copy(name = newName) else it }
                purchaseList = purchaseList.map { if (it.category == oldCategory.name) it.copy(category = newName) else it }
            }
            categoryToEdit = null
        }, title = "Editar Categoria", label = "Novo Nome")
    }

    categoryToDelete?.let {
        AlertDialog(onDismissRequest = { categoryToDelete = null }, title = { Text("Excluir Categoria") }, text = { Text("Tem certeza que deseja excluir a categoria '${it.name}'? As compras serão realocadas automaticamente.") }, confirmButton = { Button({
            purchaseList = purchaseList.map {
                p -> if (p.category == it.name) p.copy(category = getCategoryForEstablishment(p.establishment, categories.filter { c -> c.name != it.name })) else p
            }
            categories = categories.filter { c -> c.name != it.name }
            categoryToDelete = null
        }) { Text("Excluir") } }, dismissButton = { TextButton({ categoryToDelete = null }) { Text("Cancelar") } })
    }

    categoryForNewKeyword?.let {
        CategoryEditDialog(onDismiss = { categoryForNewKeyword = null }, onConfirm = { newKeyword ->
            val isDuplicate = categories.any { c -> c.keywords.any { it.equals(newKeyword, ignoreCase = true) } }
            if (newKeyword.isNotBlank() && !isDuplicate) {
                categories = categories.map { c -> if (c.name == it.name) c.copy(keywords = c.keywords + newKeyword) else c }
            } else {
                scope.launch { snackbarHostState.showSnackbar("Palavra-chave já existe em outra categoria.") }
            }
            categoryForNewKeyword = null
        }, title = "Adicionar Palavra-Chave", label = "Palavra-Chave")
    }


    // --- UI SCAFFOLD ---
    NavigationSuiteScaffold(containerColor = MaterialTheme.colorScheme.background, navigationSuiteItems = { AppDestinations.entries.forEach { item(icon = { Icon(it.icon, it.label) }, label = { Text(it.label) }, selected = it == currentDestination, onClick = { currentDestination = it }) } }) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbarHostState) }) {
            Column(modifier = Modifier.padding(it).fillMaxSize()) {
                when (currentDestination) {
                    AppDestinations.HOME -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            val totalValue = purchaseList.sumOf { p -> p.value.toDoubleOrNull() ?: 0.0 }
                            TotalSummary(total = totalValue)
                            Box(modifier = Modifier.weight(1f)) {
                                if (selectedCategory == null) SummaryScreen(purchases = purchaseList) { cat -> selectedCategory = cat } else CategoryDetailScreen(categoryName = selectedCategory!!, purchases = purchaseList.filter { p -> p.category == selectedCategory }, onBackClick = { selectedCategory = null }, onPurchaseClick = onPurchaseClick)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                                Button(onClick = { sendRandomPurchaseNotification(notificationHelper) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Send Notification") }
                                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Enable Listener") }
                            }
                        }
                    }
                    AppDestinations.ANALYTICS -> AnalyticsScreen(purchases = purchaseList)
                    AppDestinations.SETTINGS -> SettingsScreen(categories = categories, onAddCategory = { showAddCategoryDialog = true }, onEditCategory = { cat -> categoryToEdit = cat }, onDeleteCategory = { cat -> categoryToDelete = cat }, onAddKeyword = { cat -> categoryForNewKeyword = cat }, onRemoveKeyword = { category, keyword ->
                        categories = categories.map { c -> if (c.name == category.name) c.copy(keywords = c.keywords.filter { it != keyword }) else c }
                    })
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen(purchases: List<Purchase>) {
    val spendingByCategory = purchases.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.value.toDoubleOrNull() ?: 0.0 } }
    val totalSpending = spendingByCategory.values.sum()
    val chartColors = listOf(Color(0xFF6200EE), Color(0xFF03DAC5), Color(0xFF00, 0x85, 0x77), Color(0xFFD0, 0x00, 0x85), Color(0xFF37, 0x00, 0xB3), Color(0xFF01, 0x87, 0x86))

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Análise de Gastos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        if (totalSpending == 0.0) {
            item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhuma compra registrada para análise.", color = MaterialTheme.colorScheme.onSurface) } }
        } else {
            item { TotalSummary(total = totalSpending) }
            spendingByCategory.entries.sortedByDescending { it.value }.forEachIndexed { index, (category, amount) ->
                item {
                    val percentage = if (totalSpending > 0) (amount / totalSpending).toFloat() else 0f
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "$category (${String.format("%.1f%%", percentage * 100)})", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(String.format("R$ %.2f", amount), color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(24.dp).background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(4.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(percentage).height(24.dp).background(chartColors[index % chartColors.size], shape = RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TotalSummary(total: Double) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gasto Total", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(String.format("R$ %.2f", total), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPurchaseDialog(purchase: Purchase, categories: List<String>, onDismiss: () -> Unit, onConfirm: (Purchase) -> Unit) {
    var newCategory by remember { mutableStateOf(purchase.category) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar Compra") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estabelecimento: ${purchase.establishment}")
            Text("Valor: R$ ${purchase.value}")
            Text("Horário: ${purchase.timestamp}")
            Spacer(modifier = Modifier.height(16.dp))
            ExposedDropdownMenuBox(expanded = isDropdownExpanded, onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }) {
                TextField(value = newCategory, onValueChange = {}, readOnly = true, label = { Text("Categoria") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) }, modifier = Modifier.menuAnchor())
                ExposedDropdownMenu(expanded = isDropdownExpanded, onDismissRequest = { isDropdownExpanded = false }) {
                    categories.forEach { category -> DropdownMenuItem(text = { Text(category) }, onClick = { newCategory = category; isDropdownExpanded = false }) }
                }
            }
        }
    }, confirmButton = { Button(onClick = { onConfirm(purchase.copy(category = newCategory)) }, enabled = newCategory != purchase.category) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

private fun sendRandomPurchaseNotification(notificationHelper: NotificationHelper) {
    val establishments = listOf("Padaria do Zé", "Supermercado Pão de Mel", "Posto Shell", "Farmácia Droga Raia", "Amazon.com.br", "Netflix", "Uber")
    val randomEstablishment = establishments.random()
    val randomValue = String.format("%.2f", 10 + (Random.nextDouble() * 200)).replace(",", ".")
    val notificationText = "Compra de R$ $randomValue em $randomEstablishment."
    val titles = listOf("Compra Aprovada", "Pagamento Confirmado", "Nova Compra Realizada")
    val randomTitle = titles.random()
    notificationHelper.showNotification(randomTitle, notificationText)
}

@Composable
fun SummaryScreen(purchases: List<Purchase>, onCategoryClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        purchases.groupBy { it.category }.entries.sortedByDescending { entry -> entry.value.sumOf { it.value.toDoubleOrNull() ?: 0.0 } }.forEach { (category, purchaseList) ->
            item {
                val totalValue = purchaseList.sumOf { it.value.toDoubleOrNull() ?: 0.0 }
                CategorySummaryItem(category, purchaseList.size, totalValue, onClick = { onCategoryClick(category) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(categories: List<Category>, onAddCategory: () -> Unit, onEditCategory: (Category) -> Unit, onDeleteCategory: (Category) -> Unit, onAddKeyword: (Category) -> Unit, onRemoveKeyword: (Category, String) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, floatingActionButton = { FloatingActionButton(onClick = onAddCategory, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Filled.Add, "Adicionar Categoria") } }) {
        LazyColumn(modifier = Modifier.padding(it).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Gerenciar Categorias e Palavras-Chave", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
            items(categories) {
                category ->
                var isExpanded by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(category.name, modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            if (category.name != "Outros") {
                                IconButton(onClick = { onEditCategory(category) }) { Icon(Icons.Filled.Edit, "Editar Nome", tint = MaterialTheme.colorScheme.onSurface) }
                                IconButton(onClick = { onDeleteCategory(category) }) { Icon(Icons.Filled.Delete, "Excluir Categoria", tint = MaterialTheme.colorScheme.onSurface) }
                            }
                            IconButton(onClick = { isExpanded = !isExpanded }) {
                                Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (isExpanded) "Recolher" else "Expandir", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Palavras-chave:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                category.keywords.forEach { keyword ->
                                    InputChip(selected = false, onClick = {}, label = { Text(keyword) }, trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remover", modifier = Modifier.size(18.dp).clickable { onRemoveKeyword(category, keyword) }) })
                                }
                                if (category.name != "Outros") {
                                    Button(onClick = { onAddKeyword(category) }, modifier = Modifier.height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Adicionar") }
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
fun CategoryEditDialog(initialValue: String = "", title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { TextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true) }, confirmButton = { Button(onClick = { onConfirm(text) }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySummaryItem(category: String, count: Int, totalValue: Double, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = category, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Total de Compras: $count", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text(String.format("Valor Gasto: R$ %.2f", totalValue), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun CategoryDetailScreen(categoryName: String, purchases: List<Purchase>, onBackClick: () -> Unit, onPurchaseClick: (Purchase) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onSurface) }
            Text(text = categoryName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        PurchaseLogScreen(purchases = purchases, onPurchaseClick = onPurchaseClick)
    }
}

@Composable
fun PurchaseLogScreen(purchases: List<Purchase>, onPurchaseClick: (Purchase) -> Unit) {
    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(purchases.reversed()) { PurchaseItem(it, onClick = { onPurchaseClick(it) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseItem(purchase: Purchase, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(purchase.establishment, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Valor: R$ ${purchase.value}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text(purchase.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(purchase.timestamp, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    ANALYTICS("Análise", Icons.Filled.PieChart),
    SETTINGS("Configurações", Icons.Filled.Settings),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) { Text(text = "Hello $name!", modifier = modifier) }

@Preview(showBackground = true)
@Composable
fun GreetingPreview() { FlowMoneyTheme { Greeting("Android") } }
