package com.cogito.camaras

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

enum class Kind(val label: String) {
    FIXED("Mural"),
    DOME("Punto panorámico"),
    ALPR("Estacionamiento"),
}

enum class Filter(val label: String, val kind: Kind?) {
    ALL("Todos", null),
    MURAL("Murales", Kind.FIXED),
    VIEW("Panorámicos", Kind.DOME),
    PARKING("Estacionamientos", Kind.ALPR),
}

/** Un punto marcado y guardado en este teléfono. */
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

    val agency: Agency? = tags["agency"]?.let { a -> Agency.entries.find { it.name == a } }

    /** Estatal si así se marcó, o si el nombre del operador es un organismo público. */
    val isState: Boolean = run {
        val type = tags["operator:type"]?.lowercase()
        if (type != null) return@run type in STATE_TYPES
        val op = (tags["operator"] ?: "").lowercase()
        op.isNotEmpty() && STATE_WORDS.containsMatchIn(op)
    }

    val isKnownPrivate: Boolean =
        tags["operator:type"]?.lowercase() in setOf("private", "business", "commercial", "private_non_profit")

    val operator: String? = tags["operator"]

    val seenInPerson: Boolean = tags["seen"] == "yes"

    val directionDeg: Int? = tags["camera:direction"]?.let { parseDirection(it) }

    fun matches(f: Filter) = f.kind == null || f.kind == kind

    /** Nombre del ícono en el mapa: color y dibujo según el tipo de lugar. */
    val icon: String get() = "pin_" + kind.name.lowercase()

    companion object {
        val STATE_TYPES = setOf(
            "government", "public", "military", "municipal", "municipality", "state", "national",
            "federal", "police", "governmental", "public_sector",
        )

        val STATE_WORDS = Regex(
            "polic|municip|gobierno|ministerio|ayuntamiento|intendencia|prefectura|gendarmer|" +
                "tr[aá]nsito|seguridad ciudadana|ciudad de|provincia|estado|federal|nacional|" +
                "police|council|city of|county|government|department of|state of|carabineros",
        )

        private val CARDINAL = mapOf(
            "N" to 0, "NNE" to 22, "NE" to 45, "ENE" to 67, "E" to 90, "ESE" to 112, "SE" to 135, "SSE" to 157,
            "S" to 180, "SSW" to 202, "SW" to 225, "WSW" to 247, "W" to 270, "WNW" to 292, "NW" to 315, "NNW" to 337,
        )

        fun parseDirection(v: String): Int? {
            val s = v.trim().substringBefore(';').substringBefore('-').uppercase()
            return s.toDoubleOrNull()?.toInt()?.mod(360) ?: CARDINAL[s]
        }
    }
}

data class Place(val name: String, val lat: Double, val lon: Double, val bbox: DoubleArray?)

/** Búsqueda de direcciones con el buscador que trae el propio Android. */
object PlaceSearch {
    suspend fun search(context: Context, q: String): List<Place> = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) throw IllegalStateException("Este teléfono no tiene buscador de direcciones")
        val geo = Geocoder(context, Locale("es", "AR"))
        @Suppress("DEPRECATION")
        val list = geo.getFromLocationName(q, 6) ?: emptyList()
        list.map { a ->
            val lines = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }
            val full = lines.joinToString(", ").ifEmpty { a.featureName ?: q }
            Place(full, a.latitude, a.longitude, null)
        }
    }
}

