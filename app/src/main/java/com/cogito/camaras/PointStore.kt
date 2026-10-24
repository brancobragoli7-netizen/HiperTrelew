package com.cogito.camaras

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Guarda los puntos en el teléfono, en un archivo propio de la app. Nada sale del celular. */
class PointStore(context: Context) {

    private val file = File(context.filesDir, "puntos.json")
    private val resolver = context.contentResolver

    suspend fun load(): List<Camera> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    suspend fun save(points: Collection<Camera>) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson(points))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    /** Copia de seguridad o para pasarle los puntos a otra persona. */
    suspend fun export(uri: Uri, points: Collection<Camera>) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(toJson(points).toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("No se pudo escribir el archivo")
    }

    suspend fun import(uri: Uri): List<Camera> = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("No se pudo leer el archivo")
        parse(text)
    }

    private fun toJson(points: Collection<Camera>): String {
        val arr = JSONArray()
        points.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("lat", c.lat)
                put("lon", c.lon)
                put("fecha", c.timestamp ?: "")
                put("datos", JSONObject(c.tags))
            })
        }
        return JSONObject().put("app", "streetviewer").put("version", 1).put("puntos", arr).toString(1)
    }

    private fun parse(text: String): List<Camera> {
        val arr = JSONObject(text).getJSONArray("puntos")
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val t = o.getJSONObject("datos")
                val tags = HashMap<String, String>()
                for (k in t.keys()) tags[k] = t.getString(k)
                val lat = o.getDouble("lat")
                val lon = o.getDouble("lon")
                require(lat in -90.0..90.0 && lon in -180.0..180.0)
                Camera(o.getLong("id"), lat, lon, tags, o.optString("fecha").ifEmpty { null })
            }.getOrNull()
        }
    }

    companion object {
        fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

        /** Id nuevo, único aunque se importen puntos de otro teléfono. */
        fun newId(): Long = System.currentTimeMillis() * 1000 + (0..999).random()
    }
}
