package com.bustyre.tracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bustyre.tracker.assistant.AssistantScreen
import com.bustyre.tracker.assistant.LiveScreen
import com.bustyre.tracker.data.AddResult
import com.bustyre.tracker.data.FleetRepository
import com.bustyre.tracker.data.VehicleEntity
import com.bustyre.tracker.export.PdfExporter
import com.bustyre.tracker.ui.DetailScreen
import com.bustyre.tracker.ui.StarListScreen
import com.bustyre.tracker.ui.VehicleListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val ROUTE_VEHICLES = "vehicles"
private const val ROUTE_ASSISTANT = "assistant"
private const val ROUTE_LIVE = "live"
private fun starsRoute(stars: Int) = "stars/$stars"
private fun detailRoute(number: String, stars: Int) = "detail/$number/$stars"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as BusTyreApp).repository
        setContent {
            MaterialTheme {
                AppRoot(repo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(repo: FleetRepository) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val vehicles by repo.observeVehicles().collectAsState(initial = emptyList())
    val backStackEntry by nav.currentBackStackEntryAsState()

    // True while a vehicle still has tyres to enter. Blocks back, search, and menu navigation.
    var blocked by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    fun guard(action: () -> Unit) {
        if (blocked) {
            scope.launch { snackbar.showSnackbar("Enter every tyre of this vehicle first") }
        } else {
            action()
        }
    }

    BackHandler(enabled = blocked) { /* swallowed until the vehicle is complete */ }

    // Exact match of the typed number opens that vehicle automatically.
    val exactMatch = vehicles.firstOrNull { it.number == repo.normalise(query) }
    LaunchedEffect(exactMatch?.number) {
        val match = exactMatch
        if (searchOpen && match != null && !blocked) {
            nav.navigate(detailRoute(match.number, 0))
            searchOpen = false
            query = ""
        }
    }

    fun exportPdf() {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                val rows = repo.snapshotForExport()
                if (rows.isEmpty()) null else PdfExporter.write(context, rows)
            }
            if (file == null) {
                snackbar.showSnackbar("No vehicles to export yet")
            } else {
                sharePdf(context, file)
            }
        }
    }

    val canGoBack = backStackEntry != null && nav.previousBackStackEntry != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bus Tyre Tracker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { guard { nav.popBackStack() } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        guard {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        }
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    Box {
                        IconButton(onClick = { guard { menuOpen = true } }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("3 ⭐ Vehicles") },
                                onClick = {
                                    menuOpen = false
                                    guard { nav.navigate(starsRoute(3)) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("2 ⭐ Vehicles") },
                                onClick = {
                                    menuOpen = false
                                    guard { nav.navigate(starsRoute(2)) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("1 ⭐ Vehicles") },
                                onClick = {
                                    menuOpen = false
                                    guard { nav.navigate(starsRoute(1)) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Vehicle") },
                                onClick = {
                                    menuOpen = false
                                    guard { showAdd = true }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🎙 Live / ಲೈವ್") },
                                onClick = {
                                    menuOpen = false
                                    guard { nav.navigate(ROUTE_LIVE) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export PDF") },
                                onClick = {
                                    menuOpen = false
                                    guard { exportPdf() }
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (backStackEntry?.destination?.route == ROUTE_VEHICLES) {
                AssistantHomeButton { guard { nav.navigate(ROUTE_ASSISTANT) } }
                LiveHomeButton { guard { nav.navigate(ROUTE_LIVE) } }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            NavHost(
                navController = nav,
                startDestination = ROUTE_VEHICLES,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(ROUTE_VEHICLES) {
                    VehicleListScreen(repo) { number ->
                        nav.navigate(detailRoute(number, 0))
                    }
                }
                composable(ROUTE_ASSISTANT) {
                    AssistantScreen(
                        repo = repo,
                        onOpenVehicle = { number -> nav.navigate(detailRoute(number, 0)) },
                        onExportPdf = { exportPdf() }
                    )
                }
                composable(ROUTE_LIVE) {
                    LiveScreen(
                        repo = repo,
                        onOpenVehicle = { number -> nav.navigate(detailRoute(number, 0)) },
                        onExportPdf = { exportPdf() }
                    )
                }
                composable(
                    "stars/{stars}",
                    arguments = listOf(navArgument("stars") { type = NavType.IntType })
                ) { entry ->
                    val stars = entry.arguments?.getInt("stars") ?: 0
                    StarListScreen(repo, stars) { number ->
                        nav.navigate(detailRoute(number, stars))
                    }
                }
                composable(
                    "detail/{number}/{stars}",
                    arguments = listOf(
                        navArgument("number") { type = NavType.StringType },
                        navArgument("stars") { type = NavType.IntType }
                    )
                ) { entry ->
                    DetailScreen(
                        repo = repo,
                        number = entry.arguments?.getString("number") ?: "",
                        stars = entry.arguments?.getInt("stars") ?: 0,
                        onBlockChange = { blocked = it }
                    )
                }
            }

            // Search panel floats over the current screen. The screen underneath is never
            // recreated, so scroll position and place are kept when search closes.
            if (searchOpen) {
                SearchPanel(
                    query = query,
                    results = vehicles.filter { it.number.contains(repo.normalise(query)) }.take(8),
                    onQueryChange = { query = it },
                    onPick = { number ->
                        guard {
                            nav.navigate(detailRoute(number, 0))
                            searchOpen = false
                            query = ""
                        }
                    },
                    onClose = {
                        searchOpen = false
                        query = ""
                    }
                )
            }
        }
        }
    }

    if (showAdd) {
        AddVehicleDialog(
            repo = repo,
            onDismiss = { showAdd = false },
            onAdded = { number ->
                showAdd = false
                nav.navigate(detailRoute(number, 0))
            }
        )
    }
}

@Composable
private fun AssistantHomeButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .defaultMinSize(minHeight = 96.dp)
    ) {
        Text("🎤 Assistant / ಸಹಾಯಕ", fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LiveHomeButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .defaultMinSize(minHeight = 80.dp)
    ) {
        Text("🎙 Live / ಲೈವ್", fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchPanel(
    query: String,
    results: List<VehicleEntity>,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    label = { Text("Search vehicle number") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                }
            }
            if (query.isNotBlank() && results.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    items(results, key = { it.number }) { vehicle ->
                        Text(
                            vehicle.number,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(vehicle.number) }
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddVehicleDialog(
    repo: FleetRepository,
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Vehicle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    singleLine = true,
                    label = { Text("Vehicle number") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (val result = repo.addVehicle(text)) {
                        is AddResult.Added -> onAdded(result.number)
                        AddResult.Duplicate -> error = "This vehicle already exists"
                        AddResult.Invalid -> error = "Use letters, then digits, 5 characters at most (for example F3334)"
                    }
                }
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF"))
}
