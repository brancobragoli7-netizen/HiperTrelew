package com.cogito.camaras.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cogito.camaras.Bounds
import com.cogito.camaras.Camera
import com.cogito.camaras.Config
import com.cogito.camaras.Filter
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Acceso al mapa desde la pantalla (centrar, ubicación, etc.). */
class MapHandle {
    var map: MapLibreMap? = null
    /** Estado observable: cuando el estilo termina de cargar, se dibujan los pines. */
    var style by mutableStateOf<Style?>(null)

    val center: LatLng? get() = map?.cameraPosition?.target
    val zoom: Double get() = map?.cameraPosition?.zoom ?: 0.0

    fun bounds(): Bounds? {
        val b = map?.projection?.visibleRegion?.latLngBounds ?: return null
        return Bounds(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast)
    }

    fun flyTo(lat: Double, lon: Double, zoom: Double = 16.0) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom), 900)
    }

    fun fitBox(south: Double, north: Double, west: Double, east: Double) {
        val b = LatLngBounds.Builder().include(LatLng(south, west)).include(LatLng(north, east)).build()
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 120), 900)
    }

    fun resetNorth() {
        val m = map ?: return
        m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).tilt(0.0).build()), 400)
    }

    @SuppressLint("MissingPermission")
    fun enableLocation(context: Context) {
        val m = map ?: return
        val s = style ?: return
        runCatching {
            val lc = m.locationComponent
            if (!lc.isLocationComponentActivated) {
                lc.activateLocationComponent(
                    LocationComponentActivationOptions.builder(context, s).useDefaultLocationEngine(true).build()
                )
            }
            lc.isLocationComponentEnabled = true
            lc.cameraMode = CameraMode.NONE
            lc.renderMode = RenderMode.COMPASS
        }
    }

    @SuppressLint("MissingPermission")
    fun myLocation(): LatLng? = runCatching {
        map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.lastKnownLocation
            ?.let { LatLng(it.latitude, it.longitude) }
    }.getOrNull()
}

private const val SRC = "camaras"
private const val L_CLUSTER = "camaras-grupos"
private const val L_COUNT = "camaras-cantidad"
private const val L_PINS = "camaras-pines"

@Composable
fun MapCanvas(
    handle: MapHandle,
    cameras: Collection<Camera>,
    filter: Filter,
    selectedId: Long?,
    startLat: Double,
    startLon: Double,
    startZoom: Double,
    onIdle: (Bounds, Double) -> Unit,
    onPick: (Long?) -> Unit,
    onMoveStart: () -> Unit,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val idle by rememberUpdatedState(onIdle)
    val pick by rememberUpdatedState(onPick)
    val moveStart by rememberUpdatedState(onMoveStart)
    val ready by rememberUpdatedState(onReady)

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            handle.map = map
            map.uiSettings.apply {
                isCompassEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = false
                setAttributionMargins(24, 0, 0, 24)
                setLogoMargins(24, 0, 0, 24)
            }
            map.cameraPosition = CameraPosition.Builder().target(LatLng(startLat, startLon)).zoom(startZoom).build()
            map.setStyle(Style.Builder().fromUri(Config.STYLE_URL)) { style ->
                handle.style = style
                val d = context.resources.displayMetrics.density
                Pins.all(d).forEach { (name, bmp) -> style.addImage(name, bmp) }

                style.addSource(
                    GeoJsonSource(
                        SRC, FeatureCollection.fromFeatures(emptyList()),
                        GeoJsonOptions().withCluster(true).withClusterMaxZoom(15).withClusterRadius(48),
                    )
                )
                // Grupos de cámaras cuando el mapa está lejos
                style.addLayer(
                    CircleLayer(L_CLUSTER, SRC).withFilter(Expression.has("point_count")).withProperties(
                        PropertyFactory.circleColor(
                            Expression.step(
                                Expression.get("point_count"), Expression.color(0xFFE8574C.toInt()),
                                Expression.stop(25, Expression.color(0xFFD93025.toInt())),
                                Expression.stop(150, Expression.color(0xFFA50E0E.toInt())),
                            )
                        ),
                        PropertyFactory.circleRadius(
                            Expression.step(
                                Expression.get("point_count"), Expression.literal(15f),
                                Expression.stop(25, Expression.literal(19f)),
                                Expression.stop(150, Expression.literal(24f)),
                            )
                        ),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2.5f),
                        PropertyFactory.circleOpacity(0.92f),
                    )
                )
                style.addLayer(
                    SymbolLayer(L_COUNT, SRC).withFilter(Expression.has("point_count")).withProperties(
                        PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                        PropertyFactory.textSize(12.5f),
                        PropertyFactory.textColor("#FFFFFF"),
                        PropertyFactory.textAllowOverlap(true),
                        PropertyFactory.textIgnorePlacement(true),
                    )
                )
                style.addLayer(
                    SymbolLayer(L_PINS, SRC).withFilter(Expression.not(Expression.has("point_count"))).withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAnchor("bottom"),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconSize(1f),
                    )
                )
                ready()
                handle.bounds()?.let { idle(it, map.cameraPosition.zoom) }
            }

            map.addOnCameraIdleListener { handle.bounds()?.let { idle(it, map.cameraPosition.zoom) } }
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) moveStart()
            }
            map.addOnMapClickListener { latLng ->
                val pt = map.projection.toScreenLocation(latLng)
                val box = android.graphics.RectF(pt.x - 24, pt.y - 60, pt.x + 24, pt.y + 8)
                val pins = map.queryRenderedFeatures(box, L_PINS)
                if (pins.isNotEmpty()) {
                    // El más cercano al toque
                    val f = pins.minByOrNull { f ->
                        val p = (f.geometry() as? Point) ?: return@minByOrNull Double.MAX_VALUE
                        val s = map.projection.toScreenLocation(LatLng(p.latitude(), p.longitude()))
                        (s.x - pt.x) * (s.x - pt.x) + (s.y - 20 - pt.y) * (s.y - 20 - pt.y).toDouble()
                    }
                    pick(f?.getStringProperty("id")?.toLongOrNull())
                    return@addOnMapClickListener true
                }
                val groups = map.queryRenderedFeatures(android.graphics.RectF(pt.x - 26, pt.y - 26, pt.x + 26, pt.y + 26), L_CLUSTER)
                if (groups.isNotEmpty()) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, map.cameraPosition.zoom + 2), 500)
                    return@addOnMapClickListener true
                }
                pick(null)
                false
            }
        }
    }

    // Actualiza los pines cuando cambian los datos o el filtro
    LaunchedEffect(cameras, filter, handle.style) {
        val style = handle.style ?: return@LaunchedEffect
        val features = cameras.asSequence().filter { it.matches(filter) }.map { c ->
            Feature.fromGeometry(Point.fromLngLat(c.lon, c.lat)).apply {
                addStringProperty("id", c.id.toString())
                addStringProperty("icon", c.icon)
            }
        }.toList()
        style.getSourceAs<GeoJsonSource>(SRC)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Agranda el pin elegido
    LaunchedEffect(selectedId, handle.style) {
        val layer = handle.style?.getLayerAs<SymbolLayer>(L_PINS) ?: return@LaunchedEffect
        layer.setProperties(
            PropertyFactory.iconSize(
                Expression.switchCase(
                    Expression.eq(Expression.get("id"), Expression.literal(selectedId?.toString() ?: "-")),
                    Expression.literal(1.35f),
                    Expression.literal(1f),
                )
            )
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
