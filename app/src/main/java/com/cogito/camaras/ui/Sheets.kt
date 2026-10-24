package com.cogito.camaras.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cogito.camaras.Agency
import com.cogito.camaras.Camera
import com.cogito.camaras.Config
import com.cogito.camaras.Draft
import com.cogito.camaras.Kind

val Red = Color(0xFFD93025)
val Gray = Color(0xFF5F6368)
val Blue = Color(0xFF1A73E8)

fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun directions(context: Context, lat: Double, lon: Double) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(Punto)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(nav) }.onFailure {
        open(context, "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon")
    }
}

private val DIRS = listOf("Norte", "Noreste", "Este", "Sureste", "Sur", "Suroeste", "Oeste", "Noroeste")
fun directionName(deg: Int) = DIRS[((deg + 22) % 360) / 45]

private val ZONES = mapOf(
    "traffic" to "Tránsito", "street" to "Calle", "town" to "Espacio público", "parking" to "Estacionamiento",
    "shop" to "Comercio", "building" to "Edificio", "public" to "Espacio público", "bank" to "Banco",
    "atm" to "Cajero", "entrance" to "Entrada", "outdoor" to "Exterior", "area" to "Área",
)
private val MOUNTS = mapOf(
    "pole" to "Poste", "wall" to "Pared", "ceiling" to "Techo", "street_lamp" to "Farola",
    "traffic_signals" to "Semáforo", "building" to "Edificio",
)

// ───────────────────────── Detalle de un punto ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSheet(cam: Camera, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(cam.kind.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            val badge = cam.agency?.takeIf { it != Agency.UNKNOWN }?.label ?: "Sin organismo"
            val color = Color(Pins.colorOf(cam.kind))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(badge, color = color, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Button(onClick = { directions(context, cam.lat, cam.lon) }) {
                    Icon(Icons.Default.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Cómo llegar")
                }
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Editar")
                }
                OutlinedButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = Red); Spacer(Modifier.width(8.dp)); Text("Eliminar", color = Red)
                }
            }
            Spacer(Modifier.height(20.dp))

            cam.agency?.let { Info("Quién lo administra", it.label) }
            Info("Organismo", cam.operator ?: "Sin datos")
            cam.directionDeg?.let { deg ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Mira hacia", style = MaterialTheme.typography.labelMedium, color = Gray)
                        Text("${directionName(deg)} ($deg°)", style = MaterialTheme.typography.bodyLarge)
                    }
                    Icon(Icons.Default.Navigation, null, tint = Blue, modifier = Modifier.size(28.dp).rotate(deg.toFloat()))
                }
            }
            cam.tags["description"]?.let { Info("Notas", it) }
            cam.tags["ref"]?.let { Info("Número", it) }
            cam.timestamp?.let { ts ->
                val d = ts.take(10).split("-")
                if (d.size == 3) Info("Marcado / editado", "${d[2]}/${d[1]}/${d[0]}")
            }
            if (cam.seenInPerson) Info("Verificación", "Visitado en persona")
            Spacer(Modifier.height(10.dp))
            Text(
                "%.6f, %.6f".format(java.util.Locale.US, cam.lat, cam.lon),
                style = MaterialTheme.typography.bodySmall, color = Gray,
            )
            Text("Guardado solo en este teléfono", style = MaterialTheme.typography.bodySmall, color = Gray)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar este punto?") },
            text = { Text("Se borra de este teléfono. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Info(label: String, value: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Gray)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// ───────────────────────── Formulario de punto nuevo ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftSheet(
    draft: Draft,
    onChange: (Draft) -> Unit,
    onMove: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val publishing = false
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!publishing) onDismiss() }, sheetState = state, containerColor = Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Text(if (draft.editingId == null) "Nuevo punto" else "Editar punto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%.5f, %.5f".format(java.util.Locale.US, draft.lat, draft.lon),
                    style = MaterialTheme.typography.bodySmall, color = Gray, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onMove, enabled = !publishing) { Text("Mover") }
            }

            Section("¿Qué es?")
            ChipRow {
                Kind.entries.forEach { k ->
                    FilterChip(selected = draft.kind == k, onClick = { onChange(draft.copy(kind = k)) }, label = { Text(k.label) })
                }
            }

            Section("¿Quién lo administra?")
            ChipRow {
                Agency.entries.forEach { a ->
                    FilterChip(selected = draft.agency == a, onClick = { onChange(draft.copy(agency = a)) }, label = { Text(a.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.operatorName,
                onValueChange = { onChange(draft.copy(operatorName = it)) },
                label = { Text("Organismo (opcional)") },
                placeholder = { Text(draft.agency.hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Section("¿Hacia dónde mira? (opcional)")
            ChipRow {
                FilterChip(selected = draft.direction == null, onClick = { onChange(draft.copy(direction = null)) }, label = { Text("No sé") })
                listOf("N" to 0, "NE" to 45, "E" to 90, "SE" to 135, "S" to 180, "SO" to 225, "O" to 270, "NO" to 315).forEach { (n, deg) ->
                    FilterChip(selected = draft.direction == deg, onClick = { onChange(draft.copy(direction = deg)) }, label = { Text(n) })
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { onChange(draft.copy(notes = it.take(255))) },
                label = { Text("Notas (opcional)") },
                placeholder = { Text("Ej.: se entra por el camino de ripio, mejor al atardecer") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = draft.seenInPerson, onCheckedChange = { onChange(draft.copy(seenInPerson = it)) })
                Text("Lo visité en persona y la ubicación es correcta", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Se guarda solo en este teléfono. Podés pasarle tus puntos a otra persona desde el menú → Exportar.",
                style = MaterialTheme.typography.bodySmall, color = Gray,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSave,
                enabled = !publishing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
            ) {
                if (publishing) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text("Publicando…")
                } else {
                    Text("Guardar")
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ───────────────────────── Diálogos ─────────────────────────

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("streetviewer") },
        text = {
            Text(
                "Mapa de lugares.\n\n" +
                    "• Violeta: murales.\n" +
                    "• Verde: puntos panorámicos.\n" +
                    "• Azul: estacionamientos.\n\n" +
                    "Los puntos que marcás se guardan solo en este teléfono: no se suben a ningún lado. " +
                    "Si desinstalás la app se pierden, así que de vez en cuando hacé una copia con «Exportar».\n\n" +
                    "El mapa de fondo necesita internet. Mapa base: OpenFreeMap, datos © colaboradores de OpenStreetMap."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } },
    )
}
