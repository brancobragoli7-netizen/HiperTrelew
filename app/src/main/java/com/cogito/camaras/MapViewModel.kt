package com.cogito.camaras

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(o: Bounds) = o.south >= south && o.north <= north && o.west >= west && o.east <= east
    fun expanded(f: Double): Bounds {
        val dy = (north - south) * (f - 1) / 2
        val dx = (east - west) * (f - 1) / 2
        return Bounds(south - dy, west - dx, north + dy, east + dx)
    }
}

enum class LoadState { TOO_FAR, LOADING, READY, ERROR }

class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()

    private val _cameras = MutableStateFlow<Map<Long, Camera>>(emptyMap())
    val cameras = _cameras.asStateFlow()

    private val _load = MutableStateFlow(LoadState.TOO_FAR)
    val load = _load.asStateFlow()

    val filter = MutableStateFlow(Filter.ALL)
    val selected = MutableStateFlow<Camera?>(null)

    /** null = no se está marcando; si no, el formulario de la cámara nueva. */
    val draft = MutableStateFlow<Draft?>(null)
    val placing = MutableStateFlow(false)
    val publishing = MutableStateFlow(false)

    val user = MutableStateFlow(OsmAuth.userName(app))
    val message = MutableStateFlow<String?>(null)

    val results = MutableStateFlow<List<Place>>(emptyList())
    val searching = MutableStateFlow(false)

    private var loaded: Bounds? = null
    private var fetchJob: Job? = null
    private var pendingPublish = false

    /** Se llama cuando el mapa deja de moverse. */
    fun onCameraIdle(visible: Bounds, zoom: Double) {
        if (zoom < Config.MIN_ZOOM_CAMERAS) {
            fetchJob?.cancel()
            _load.value = LoadState.TOO_FAR
            return
        }
        val prev = loaded
        if (prev != null && prev.contains(visible) && _load.value != LoadState.ERROR) {
            _load.value = LoadState.READY
            return
        }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            delay(450) // espera a que el usuario termine de mover el mapa
            _load.value = LoadState.LOADING
            val area = visible.expanded(1.4)
            try {
                val list = CameraSource.fetch(area.south, area.west, area.north, area.east)
                _cameras.update { old ->
                    // Conserva lo ya cargado (hasta un límite) y suma lo nuevo
                    val merged = if (old.size > 12_000) HashMap() else HashMap(old)
                    list.forEach { merged[it.id] = it }
                    merged
                }
                loaded = area
                _load.value = LoadState.READY
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _load.value = LoadState.ERROR
            }
        }
    }

    fun retry(visible: Bounds, zoom: Double) {
        loaded = null
        onCameraIdle(visible, zoom)
    }

    fun search(q: String, nearLat: Double?, nearLon: Double?) {
        if (q.isBlank()) { results.value = emptyList(); return }
        viewModelScope.launch {
            searching.value = true
            results.value = runCatching { PlaceSearch.search(q.trim(), nearLat, nearLon) }.getOrElse {
                message.value = "No se pudo buscar. Revisá la conexión."
                emptyList()
            }
            if (results.value.isEmpty() && message.value == null) message.value = "Sin resultados para «$q»"
            searching.value = false
        }
    }

    // ── Marcar una cámara ──

    fun startPlacing() { selected.value = null; placing.value = true }
    fun cancelPlacing() { placing.value = false; draft.value = null }

    fun confirmPlace(lat: Double, lon: Double) {
        placing.value = false
        draft.value = (draft.value ?: Draft(lat, lon)).copy(lat = lat, lon = lon)
    }

    fun updateDraft(d: Draft) { draft.value = d }

    fun publish() {
        val d = draft.value ?: return
        if (!OsmAuth.enabled) return
        if (OsmAuth.token(ctx) == null) {
            pendingPublish = true
            OsmAuth.startLogin(ctx)
            return
        }
        viewModelScope.launch {
            publishing.value = true
            try {
                val id = OsmEditor.publish(ctx, d)
                val cam = Camera(id, d.lat, d.lon, d.tags())
                _cameras.update { it + (id to cam) }
                draft.value = null
                message.value = "¡Listo! La cámara ya está en OpenStreetMap. Puede tardar unos minutos en verse para todos."
            } catch (e: HttpException) {
                if (e.code == 401 || e.code == 403) {
                    OsmAuth.logout(ctx); user.value = null
                    message.value = "La sesión venció. Volvé a iniciar sesión y publicá de nuevo."
                } else {
                    message.value = "OpenStreetMap rechazó el cambio (${e.code}). Probá más tarde."
                }
            } catch (e: Exception) {
                message.value = "No se pudo publicar. Revisá la conexión y probá de nuevo."
            } finally {
                publishing.value = false
            }
        }
    }

    fun login() = OsmAuth.startLogin(ctx)

    fun logout() {
        OsmAuth.logout(ctx)
        user.value = null
        message.value = "Sesión cerrada"
    }

    fun onAuthRedirect(uri: Uri) {
        viewModelScope.launch {
            try {
                val name = OsmAuth.finishLogin(ctx, uri)
                user.value = name
                message.value = "Sesión iniciada como $name"
                if (pendingPublish && draft.value != null) {
                    pendingPublish = false
                    publish()
                }
            } catch (e: Exception) {
                pendingPublish = false
                message.value = e.message ?: "No se pudo iniciar sesión"
            }
        }
    }
}
