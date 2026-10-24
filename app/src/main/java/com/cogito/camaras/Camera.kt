package com.cogito.camaras

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

enum class Kind(val label: String) {
    FIXED("Cámara fija"),
    DOME("Cámara domo"),
    ALPR("Lectora de patentes"),
}

enum class Filter(val label: String) { ALL("Todas"), STATE("Estatales"), ALPR("Patentes") }

/** Una cámara tal como está cargada en OpenStreetMap. */
data class Camera(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String>,
    val timestamp: String? = null,
) {
    val kind: Kind = when {
        tags["surveillance:type"].equals("ALPR", ignoreCase = true) -> Kind.ALPR
        tags["camera:type"] == "dome" || tags["camera:type"] == "panning" -> Kind.DOME
        else -> Kind.FIXED
    }

    /** Estatal si OpenStreetMap lo dice, o si el nombre del operador es un organismo público. */
    val isState: Boolean = run {
        val type = tags["operator:type"]?.lowercase()
        if (type != null) return@run type in STATE_TYPES
        val op = (tags["operator"] ?: "").lowercase()
        op.isNotEmpty() && STATE_WORDS.containsMatchIn(op)
    }

    val isKnownPrivate: Boolean =
        tags["operator:type"]?.lowercase() in setOf("private", "business", "commercial", "private_non_profit") ||
            tags["surveillance"] == "indoor"

    val operator: String? = tags["operator"]

    val directionDeg: Int? = tags["camera:direction"]?.let { parseDirection(it) }

    fun matches(f: Filter) = when (f) {
        Filter.ALL -> true
        Filter.STATE -> isState
        Filter.ALPR -> kind == Kind.ALPR
    }

    /** Nombre del ícono en el mapa: color por quién la opera, dibujo por tipo. */
    val icon: String get() = "pin_" + (if (isState) "s" else "o") + "_" + kind.name.lowercase()

    companion object {
        val STATE_TYPES = setOf(
            "government", "public", "military", "municipal", "municipality", "state", "national",
            "federal", "police", "governmental", "public_sector",
        )

        val STATE_WORDS = Regex(
            "polic|municip|gobierno|ministerio|ayuntamiento|intendencia|prefectura|gendarmer|" +
                "tr[aá]nsito|seguridad ciudadana|ciudad de|provincia|estado|federal|nacional|" +
                "police|council|city of|county|government|department of|state of|dot\\b|carabineros",
        )

        private val CARDINAL = mapOf(
            "N" to 0, "NNE" to 22, "NE" to 45, "ENE" to 67, "E" to 90, "ESE" to 112, "SE" to 135, "SSE" to 157,
            "S" to 180, "SSW" to 202, "SW" to 225, "WSW" to 247, "W" to 270, "WNW" to 292, "NW" to 315, "NNW" to 337,
        )

        fun parseDirection(v: String): Int? {
            val s = v.trim().substringBefore(';').substringBefore('-').uppercase()
            return s.toDoubleOrNull()?.toInt()?.mod(360) ?: CARDINAL[s]
        }

        fun fromOverpass(json: String): List<Camera> {
            val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
            val out = ArrayList<Camera>(els.length())
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                if (e.optString("type") != "node") continue
                val t = e.optJSONObject("tags")
                val tags = HashMap<String, String>()
                if (t != null) for (k in t.keys()) tags[k] = t.getString(k)
                // Guardias y otras formas de vigilancia que no son cámaras quedan afuera
                val st = tags["surveillance:type"]?.lowercase()
                if (st != null && st != "camera" && st != "alpr") continue
                out += Camera(e.getLong("id"), e.getDouble("lat"), e.getDouble("lon"), tags, e.optString("timestamp").ifEmpty { null })
            }
            return out
        }
    }
}

/** Lee las cámaras de un área desde OpenStreetMap (servidores Overpass). */
object CameraSource {

    suspend fun fetch(south: Double, west: Double, north: Double, east: Double): List<Camera> {
        val bbox = "%.5f,%.5f,%.5f,%.5f".format(java.util.Locale.US, south, west, north, east)
        val query = """[out:json][timeout:25];node["man_made"="surveillance"]($bbox);out meta 5000;"""
        var last: Exception? = null
        for (server in Config.OVERPASS) {
            try {
                val json = Http.request(server, "POST", Http.form("data" to query), timeoutMs = 40_000)
                return Camera.fromOverpass(json)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Sin servidores")
    }
}

data class Place(val name: String, val lat: Double, val lon: Double, val bbox: DoubleArray?)

/** Búsqueda de direcciones y lugares (Nominatim, de OpenStreetMap). */
object PlaceSearch {
    suspend fun search(q: String, nearLat: Double?, nearLon: Double?): List<Place> {
        var url = "${Config.NOMINATIM}?format=jsonv2&limit=6&accept-language=es&q=" + Http.enc(q)
        if (nearLat != null && nearLon != null) {
            // Prioriza resultados cerca de lo que estás mirando, sin excluir el resto
            val d = 1.0
            url += "&viewbox=${nearLon - d},${nearLat + d},${nearLon + d},${nearLat - d}"
        }
        val arr = org.json.JSONArray(Http.request(url))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val bb = o.optJSONArray("boundingbox")?.let { b -> DoubleArray(4) { b.getString(it).toDouble() } }
            Place(o.getString("display_name"), o.getString("lat").toDouble(), o.getString("lon").toDouble(), bb)
        }
    }
}
