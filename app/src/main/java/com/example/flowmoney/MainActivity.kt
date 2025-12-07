package com.example.flowmoney

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowmoney.ui.theme.FlowMoneyTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.text.DateFormatSymbols
import java.util.Locale
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

@Parcelize
data class Category(val name: String, val keywords: List<String>) : Parcelable

private fun generateRandomPurchase(categories: List<Category>): Purchase {
    val establishments = listOf("Padaria do Zé", "Supermercado Pão de Mel", "Posto Shell", "Farmácia Droga Raia", "Amazon.com.br", "Netflix", "Uber", "Spotify", "Disney+")
    val randomEstablishment = establishments.random()
    val randomValue = String.format(Locale.US, "%.2f", 10 + (Random.nextDouble() * 200))

    // Generate random timestamp within the last year
    val calendar = java.util.Calendar.getInstance()
    val randomDaysAgo = Random.nextInt(0, 365)
    calendar.add(java.util.Calendar.DAY_OF_YEAR, -randomDaysAgo)
    val randomMinutesAgo = Random.nextInt(0, 60 * 24)
    calendar.add(java.util.Calendar.MINUTE, -randomMinutesAgo)

    val timestamp = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(calendar.time)

    val category = getCategoryForEstablishment(randomEstablishment, categories)

    return Purchase(randomValue, randomEstablishment, timestamp, category)
}

private fun getCategoryForEstablishment(establishment: String, categories: List<Category>): String {
    for (category in categories) {
        if (category.keywords.any { establishment.contains(it, ignoreCase = true) }) {
            return category.name
        }
    }
    return "Outros"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@PreviewScreenSizes
@Composable
fun FlowMoneyApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var purchaseToEdit by remember { mutableStateOf<Purchase?>(null) }

    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    var categories by remember { mutableStateOf(prefs.getCategories()) }
    var purchaseList by remember { mutableStateOf(prefs.getPurchases()) }

    LaunchedEffect(categories) { prefs.saveCategories(categories) }
    LaunchedEffect(purchaseList) { prefs.savePurchases(purchaseList) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var categoryForNewKeyword by remember { mutableStateOf<Category?>(null) }

    val onPurchaseClick: (Purchase) -> Unit = { purchase -> purchaseToEdit = purchase }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> if (isGranted) sendRandomPurchaseNotification(NotificationHelper(context)) }
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
                        val purchasesByYearMonth = remember(purchaseList) {
                            purchaseList.mapNotNull { p -> p.getYearAndMonth()?.let { ym -> ym to p } }
                                .groupBy({ it.first }, { it.second })
                        }

                        val sortedYearMonths = remember(purchasesByYearMonth) {
                            val existingYearMonths = purchasesByYearMonth.keys.toMutableSet()
                            val currentCalendar = java.util.Calendar.getInstance()
                            val currentYearMonth = currentCalendar.get(java.util.Calendar.YEAR) to (currentCalendar.get(java.util.Calendar.MONTH) + 1)
                            existingYearMonths.add(currentYearMonth)
                            existingYearMonths.sortedWith(compareBy({ it.first }, { it.second }))
                        }

                        val currentCalendar = java.util.Calendar.getInstance()
                        val currentYearMonth = currentCalendar.get(java.util.Calendar.YEAR) to (currentCalendar.get(java.util.Calendar.MONTH) + 1)

                        val initialPage = remember(sortedYearMonths, currentYearMonth) {
                            val index = sortedYearMonths.indexOf(currentYearMonth)
                            if (index != -1) index else sortedYearMonths.size - 1
                        }

                        val pagerState = rememberPagerState(initialPage = initialPage) {
                            sortedYearMonths.size
                        }

                        LaunchedEffect(pagerState.currentPage) {
                            if (selectedCategory != null) {
                                selectedCategory = null
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            val (year, month) = sortedYearMonths[pagerState.currentPage]
                            val monthlyPurchases = purchasesByYearMonth[year to month] ?: emptyList()
                            val totalValue = monthlyPurchases.sumOf { p -> p.value.toDoubleOrNull() ?: 0.0 }
                            val monthName = DateFormatSymbols(Locale("pt", "BR")).months[month - 1]

                            TotalSummary(total = totalValue)

                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${monthName.replaceFirstChar { it.uppercase() }} de $year",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) { pageIndex ->
                                val (pageYear, pageMonth) = sortedYearMonths[pageIndex]
                                val pagePurchases = purchasesByYearMonth[pageYear to pageMonth] ?: emptyList()

                                if (pagePurchases.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Nenhuma compra registrada neste mês.", color = MaterialTheme.colorScheme.onSurface)
                                    }
                                } else {
                                    val purchasesForDetails = pagePurchases.filter { p -> p.category == selectedCategory }
                                    if (selectedCategory == null) {
                                        SummaryScreen(purchases = pagePurchases) { cat -> selectedCategory = cat }
                                    } else {
                                        CategoryDetailScreen(
                                            categoryName = selectedCategory!!,
                                            purchases = purchasesForDetails,
                                            onBackClick = { selectedCategory = null },
                                            onPurchaseClick = onPurchaseClick
                                        )
                                    }
                                }
                            }

//                            Row(
//                                modifier = Modifier.fillMaxWidth().padding(16.dp),
//                                horizontalArrangement = Arrangement.SpaceAround
//                            ) {
//                                Button(onClick = { sendRandomPurchaseNotification(NotificationHelper(context)) }) { Text("Notificação") }
//                                Button(onClick = { purchaseList = purchaseList + generateRandomPurchase(categories) }) { Text("Gerar Teste") }
//                                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Listener") }
//                            }
                        }
                    }
                    AppDestinations.ANALYTICS -> AnalyticsScreen(purchases = purchaseList)
                    AppDestinations.SETTINGS -> SettingsScreen(
                        categories = categories,
                        onAddCategory = { showAddCategoryDialog = true },
                        onEditCategory = { cat -> categoryToEdit = cat },
                        onDeleteCategory = { cat -> categoryToDelete = cat },
                        onAddKeyword = { cat -> categoryForNewKeyword = cat },
                        onRemoveKeyword = { category, keyword ->
                            categories = categories.map { c -> if (c.name == category.name) c.copy(keywords = c.keywords.filter { it != keyword }) else c }
                        },
                        prefs = prefs
                    )
                }
            }
        }
    }
}

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("flow_money_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun savePurchases(purchases: List<Purchase>) {
        val json = gson.toJson(purchases)
        preferences.edit().putString("purchases", json).apply()
    }

    fun getPurchases(): List<Purchase> {
        val json = preferences.getString("purchases", null)
        return if (json != null) {
            val type = object : TypeToken<List<Purchase>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }

    fun saveCategories(categories: List<Category>) {
        val json = gson.toJson(categories)
        preferences.edit().putString("categories", json).apply()
    }

    fun getCategories(): List<Category> {
        val json = preferences.getString("categories", null)
        return if (json != null) {
            val type = object : TypeToken<List<Category>>() {}.type
            gson.fromJson(json, type)
        } else {
            listOf(
                Category("Alimentação", listOf("Padaria", "Supermercado", "iFood")),
                Category("Assinaturas", listOf("Netflix", "Spotify", "Disney+")),
                Category("Gasolina", listOf("Posto")),
                Category("Saúde", listOf("Farmácia", "Droga")),
                Category("Compras", listOf("Amazon", "Mercado Livre")),
                Category("Outros", emptyList())
            )
        }
    }

    fun saveSelectedApp(packageName: String) {
        preferences.edit().putString("selected_app", packageName).apply()
    }

    fun getSelectedApp(): String? {
        return preferences.getString("selected_app", null)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(purchases: List<Purchase>) {
    val purchasesByYearMonth = purchases
        .mapNotNull { p -> p.getYearAndMonth()?.let { ym -> ym to p } }
        .groupBy({ it.first }, { it.second })

    val sortedYearMonths = purchasesByYearMonth.keys.sortedWith(compareBy({ it.first }, { it.second }))

    if (sortedYearMonths.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhuma compra registrada para análise.", color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = sortedYearMonths.size - 1) {
        sortedYearMonths.size
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val currentYearMonth = sortedYearMonths[pagerState.currentPage]
        val (year, month) = currentYearMonth
        val monthName = DateFormatSymbols(Locale("pt", "BR")).months[month - 1]

        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "${monthName.replaceFirstChar { it.uppercase() }} de $year",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val (pageYear, pageMonth) = sortedYearMonths[pageIndex]
            val filteredPurchases = purchasesByYearMonth[pageYear to pageMonth] ?: emptyList()
            val spendingByCategory = filteredPurchases.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.value.toDoubleOrNull() ?: 0.0 } }
            val totalSpending = spendingByCategory.values.sum()
            val chartColors = listOf(Color(0xFFF9A825), Color(0xFFFBC02D), Color(0xFFFFD54F), Color(0xFFFFE082), Color(0xFFFFECB3), Color(0xFFFFF8E1))

            if (filteredPurchases.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma compra neste mês.", color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
    val groupedPurchases = purchases.groupBy { it.category }
    val (outros, otherCategories) = groupedPurchases.entries.partition { it.key == "Outros" }
    val sortedCategories = otherCategories.sortedBy { it.key } + outros

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sortedCategories.forEach { (category, purchaseList) ->
            item {
                val totalValue = purchaseList.sumOf { it.value.toDoubleOrNull() ?: 0.0 }
                CategorySummaryItem(category, purchaseList.size, totalValue, onClick = { onCategoryClick(category) })
            }
        }
    }
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
