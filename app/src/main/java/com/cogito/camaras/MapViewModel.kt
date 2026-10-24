package com.cogito.camaras

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(lat: Double, lon: Double) = lat in south..north && lon in west..east
}

class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val store = PointStore(app)

    private val _cameras = MutableStateFlow<Map<Long, Camera>>(emptyMap())
    val cameras = _cameras.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()

    val filter = MutableStateFlow(Filter.ALL)
    val selected = MutableStateFlow<Camera?>(null)

    /** null = no se está marcando; si no, el formulario del punto. */
    val draft = MutableStateFlow<Draft?>(null)
    val placing = MutableStateFlow(false)

    val message = MutableStateFlow<String?>(null)

    val results = MutableStateFlow<List<Place>>(emptyList())
    val searching = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            _cameras.value = store.load().associateBy { it.id }
            _loaded.value = true
        }
    }

    private fun persist() {
        val snapshot = _cameras.value.values.toList()
        viewModelScope.launch {
            runCatching { store.save(snapshot) }.onFailure { message.value = "No se pudo guardar en el teléfono" }
        }
    }

    fun search(q: String) {
        if (q.isBlank()) { results.value = emptyList(); return }
        viewModelScope.launch {
            searching.value = true
            results.value = runCatching { PlaceSearch.search(getApplication(), q.trim()) }.getOrElse {
                message.value = it.message?.takeIf { m -> m.startsWith("Este") } ?: "No se pudo buscar. Revisá la conexión."
                emptyList()
            }
            if (results.value.isEmpty() && message.value == null) message.value = "Sin resultados para «$q»"
            searching.value = false
        }
    }

    // ── Marcar o editar un punto ──

    fun startPlacing() { selected.value = null; draft.value = null; placing.value = true }
    fun cancelPlacing() { placing.value = false; draft.value = null }

    fun confirmPlace(lat: Double, lon: Double) {
        placing.value = false
        draft.value = (draft.value ?: Draft(lat, lon)).copy(lat = lat, lon = lon)
    }

    fun updateDraft(d: Draft) { draft.value = d }

    fun edit(c: Camera) {
        selected.value = null
        draft.value = Draft.from(c)
    }

    fun save() {
        val d = draft.value ?: return
        val old = d.editingId?.let { _cameras.value[it] }
        val id = old?.id ?: PointStore.newId()
        val cam = Camera(id, d.lat, d.lon, d.tags(), PointStore.now())
        _cameras.value = _cameras.value + (id to cam)
        draft.value = null
        persist()
        message.value = if (old == null) "Punto guardado" else "Cambios guardados"
    }

    fun delete(c: Camera) {
        _cameras.value = _cameras.value - c.id
        selected.value = null
        persist()
        message.value = "Punto eliminado"
    }

    // ── Copias de seguridad ──

    fun exportTo(uri: Uri) {
        val points = _cameras.value.values.toList()
        viewModelScope.launch {
            message.value = runCatching { store.export(uri, points) }
                .fold({ "Se exportaron ${points.size} puntos" }, { "No se pudo exportar" })
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            runCatching { store.import(uri) }.onSuccess { list ->
                // Suma los puntos nuevos; si alguno ya existe, se queda la versión del archivo
                val before = _cameras.value.size
                _cameras.value = _cameras.value + list.associateBy { it.id }
                persist()
                val added = _cameras.value.size - before
                message.value = "Se importaron ${list.size} puntos ($added nuevos)"
            }.onFailure {
                message.value = "Ese archivo no es una copia de streetviewer"
            }
        }
    }
}
