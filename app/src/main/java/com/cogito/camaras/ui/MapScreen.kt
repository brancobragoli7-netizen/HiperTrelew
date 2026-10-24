package com.cogito.camaras.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cogito.camaras.Bounds
import com.cogito.camaras.Filter
import com.cogito.camaras.MapViewModel

private fun Context.hasLocation() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun MapScreen(vm: MapViewModel) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val handle = remember { MapHandle() }
    val prefs = remember { context.getSharedPreferences("mapa", Context.MODE_PRIVATE) }

    val cameras by vm.cameras.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val filter by vm.filter.collectAsState()
    val selected by vm.selected.collectAsState()
    val placing by vm.placing.collectAsState()
    val draft by vm.draft.collectAsState()
    val message by vm.message.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf<Bounds?>(null) }
    var zoom by remember { mutableStateOf(0.0) }
    var centerOnLocation by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    // Copias de seguridad: elegir dónde guardar / qué archivo abrir
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    val locationPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) {
            handle.enableLocation(context)
            centerOnLocation = true
        }
    }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.message.value = null }
    }
    // Espera a que llegue la primera ubicación y centra el mapa
    LaunchedEffect(centerOnLocation) {
        if (!centerOnLocation) return@LaunchedEffect
        repeat(20) {
            handle.myLocation()?.let { handle.flyTo(it.latitude, it.longitude, 16.0); centerOnLocation = false; return@LaunchedEffect }
            kotlinx.coroutines.delay(300)
        }
        centerOnLocation = false
        vm.message.value = "Todavía no hay ubicación. Revisá que el GPS esté activado."
    }

    BackHandler(enabled = placing || searchFocused || results.isNotEmpty()) {
        when {
            placing -> vm.cancelPlacing()
            else -> { vm.results.value = emptyList(); focus.clearFocus() }
        }
    }

    // Arranca donde lo dejaste (o en Buenos Aires la primera vez)
    val startLat = remember { prefs.getFloat("lat", -34.6037f).toDouble() }
    val startLon = remember { prefs.getFloat("lon", -58.3816f).toDouble() }
    val startZoom = remember { prefs.getFloat("zoom", 13f).toDouble() }
    val firstRun = remember { !prefs.contains("lat") }

    Box(Modifier.fillMaxSize()) {
        MapCanvas(
            handle = handle,
            cameras = cameras.values,
            filter = filter,
            selectedId = selected?.id,
            startLat = startLat, startLon = startLon, startZoom = startZoom,
            onIdle = { b, z ->
                visible = b; zoom = z
                handle.center?.let {
                    prefs.edit().putFloat("lat", it.latitude.toFloat()).putFloat("lon", it.longitude.toFloat())
                        .putFloat("zoom", z.toFloat()).apply()
                }
            },
            onPick = { id -> vm.selected.value = id?.let { cameras[it] } },
            onMoveStart = { focus.clearFocus() },
            onReady = {
                if (context.hasLocation()) {
                    handle.enableLocation(context)
                    if (firstRun) centerOnLocation = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Pin para ubicar el punto nuevo ──
        if (placing) {
            val d = LocalDensity.current.density
            val pin = remember { Pins.placing(d).asImageBitmap() }
            Image(
                pin, null,
                Modifier.align(Alignment.Center).offset(y = -(pin.height / d / 2).dp),
            )
            Box(Modifier.align(Alignment.Center).size(6.dp).background(Color(0x66000000), CircleShape))
        }

        // ── Parte de arriba: buscador, filtros, estado ──
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp)) {
            if (!placing) {
                SearchBar(
                    query = query,
                    onQuery = { query = it },
                    focused = searchFocused,
                    onFocus = { searchFocused = it },
                    onSearch = {
                        focus.clearFocus()
                        vm.search(query)
                    },
                    onClear = { query = ""; vm.results.value = emptyList() },
                    searching = searching,
                    menu = menu,
                    onMenu = { menu = it },
                    onExport = {
                        menu = false
                        if (cameras.isEmpty()) vm.message.value = "Todavía no hay puntos para exportar"
                        else exporter.launch("streetviewer-puntos.json")
                    },
                    onImport = { menu = false; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onAbout = { menu = false; about = true },
                )
                if (results.isNotEmpty()) {
                    Surface(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp, color = Color.White,
                    ) {
                        LazyColumn(Modifier.heightIn(max = 340.dp)) {
                            items(results) { p ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        vm.results.value = emptyList()
                                        query = p.name.substringBefore(",")
                                        val bb = p.bbox
                                        if (bb != null && (bb[1] - bb[0]) > 0.003) handle.fitBox(bb[0], bb[1], bb[2], bb[3])
                                        else handle.flyTo(p.lat, p.lon, 17.0)
                                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Place, null, tint = Gray)
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(p.name.substringBefore(","), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(p.name.substringAfter(", ", ""), style = MaterialTheme.typography.bodySmall, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                            }
                        }
                    }
                }
                if (results.isEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Filter.entries.forEach { f ->
                            val icon = when (f) {
                                Filter.ALL -> Icons.Default.Layers
                                Filter.MURAL -> Icons.Default.Palette
                                Filter.VIEW -> Icons.Default.Landscape
                                Filter.PARKING -> Icons.Default.LocalParking
                            }
                            FilterChip(
                                selected = filter == f,
                                onClick = { vm.filter.value = f },
                                label = { Text(f.label) },
                                leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.White,
                                    selectedContainerColor = Color(0xFFD3E3FD),
                                ),
                                elevation = FilterChipDefaults.filterChipElevation(elevation = 3.dp),
                                border = null,
                                shape = RoundedCornerShape(18.dp),
                            )
                        }
                    }
                    if (loaded) {
                        StatusPill(
                            total = cameras.size,
                            inView = cameras.values.count { c -> c.matches(filter) && visible?.contains(c.lat, c.lon) == true },
                        )
                    }
                }
            } else {
                Surface(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp, color = Color.White,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Ubicá el punto", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Mové el mapa hasta que la marca quede justo sobre el lugar. Acercate todo lo que puedas.",
                            style = MaterialTheme.typography.bodyMedium, color = Gray,
                        )
                    }
                }
            }
        }

        // ── Abajo: leyenda y botones ──
        if (!placing) {
            Legend(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 34.dp))
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        if (context.hasLocation()) { handle.enableLocation(context); centerOnLocation = true }
                        else locationPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    containerColor = Color.White, contentColor = Blue,
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Default.MyLocation, "Mi ubicación") }
                ExtendedFloatingActionButton(
                    onClick = {
                        vm.startPlacing()
                        if (handle.zoom < 17) handle.center?.let { handle.flyTo(it.latitude, it.longitude, 18.0) }
                    },
                    containerColor = Blue, contentColor = Color.White,
                    icon = { Icon(Icons.Default.AddLocationAlt, null) },
                    text = { Text("Marcar punto") },
                )
            }
        } else {
            Surface(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), shadowElevation = 12.dp, color = Color.White,
            ) {
                Row(
                    Modifier.navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = vm::cancelPlacing, modifier = Modifier.weight(1f).height(48.dp)) { Text("Cancelar") }
                    Button(
                        onClick = {
                            val c = handle.center ?: return@Button
                            vm.confirmPlace(c.latitude, c.longitude)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Acá está") }
                }
            }
        }

        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (placing) 90.dp else 100.dp),
        )
    }

    selected?.let { cam ->
        CameraSheet(
            cam = cam,
            onEdit = { vm.edit(cam) },
            onDelete = { vm.delete(cam) },
            onDismiss = { vm.selected.value = null },
        )
    }
    draft?.let { d ->
        if (!placing) {
            DraftSheet(
                draft = d,
                onChange = vm::updateDraft,
                onMove = {
                    vm.placing.value = true
                    handle.flyTo(d.lat, d.lon, maxOf(handle.zoom, 18.0))
                },
                onSave = vm::save,
                onDismiss = vm::cancelPlacing,
            )
        }
    }
    if (about) AboutDialog { about = false }
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    focused: Boolean,
    onFocus: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    searching: Boolean,
    menu: Boolean,
    onMenu: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAbout: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp), shadowElevation = 6.dp, color = Color.White,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 4.dp)) {
            if (focused) {
                IconButton(onClick = { focusManager.clearFocus(); onClear() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Search, null, tint = Gray) }
            }
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Buscá un lugar o dirección", color = Gray, fontSize = 16.sp)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = Color(0xFF202124)),
                    cursorBrush = SolidColor(Blue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
                )
            }
            when {
                searching -> CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                query.isNotEmpty() -> IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Borrar") }
            }
            Box {
                IconButton(onClick = { onMenu(true) }) {
                    Icon(Icons.Default.MoreVert, "Menú", tint = Gray)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { onMenu(false) }) {
                    DropdownMenuItem(text = { Text("Exportar mis puntos") }, onClick = onExport)
                    DropdownMenuItem(text = { Text("Importar puntos") }, onClick = onImport)
                    DropdownMenuItem(text = { Text("Acerca de") }, onClick = onAbout)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(total: Int, inView: Int) {
    val text = when {
        total == 0 -> "Todavía no marcaste puntos · Tocá «Marcar punto»"
        inView == 0 -> "No hay puntos en esta zona"
        else -> "$inView " + (if (inView == 1) "punto" else "puntos") + " en esta zona"
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp, color = Color.White) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge, color = Color(0xFF3C4043),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun Legend(modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), shadowElevation = 3.dp, color = Color.White.copy(alpha = 0.95f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendRow(Color(Pins.PURPLE), "Mural")
            LegendRow(Color(Pins.GREEN), "Panorámico")
            LegendRow(Color(Pins.BLUE), "Estacionamiento")
        }
    }
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color(0xFF3C4043))
    }
}
