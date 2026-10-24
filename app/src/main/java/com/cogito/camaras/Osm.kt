package com.cogito.camaras

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Inicio de sesión con OpenStreetMap (OAuth 2 con PKCE, sin contraseñas en la app).
 * El usuario inicia sesión en la página de OSM y vuelve con camarasmap://oauth?code=...
 */
object OsmAuth {

    private fun prefs(c: Context) = c.getSharedPreferences("osm", Context.MODE_PRIVATE)

    val enabled: Boolean get() = Config.OSM_CLIENT_ID.isNotBlank()

    fun token(c: Context): String? = prefs(c).getString("token", null)
    fun userName(c: Context): String? = prefs(c).getString("user", null)

    fun logout(c: Context) = prefs(c).edit().remove("token").remove("user").apply()

    fun startLogin(c: Context) {
        val verifier = random(48)
        val state = random(16)
        prefs(c).edit().putString("verifier", verifier).putString("state", state).apply()
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val url = Uri.parse("${Config.OSM_WEB}/oauth2/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", Config.OSM_CLIENT_ID)
            .appendQueryParameter("redirect_uri", Config.REDIRECT_URI)
            .appendQueryParameter("scope", "read_prefs write_api")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
        val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        tabs.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        tabs.launchUrl(c, url)
    }

    /** Completa el inicio de sesión cuando OSM devuelve a la app. Devuelve el nombre de usuario. */
    suspend fun finishLogin(c: Context, uri: Uri): String {
        val p = prefs(c)
        uri.getQueryParameter("error")?.let { throw IllegalStateException("Inicio de sesión cancelado") }
        val code = uri.getQueryParameter("code") ?: throw IllegalStateException("Falta el código")
        if (uri.getQueryParameter("state") != p.getString("state", null)) throw IllegalStateException("Respuesta inválida")
        val verifier = p.getString("verifier", null) ?: throw IllegalStateException("Sesión vencida, probá de nuevo")

        val res = Http.request(
            "${Config.OSM_WEB}/oauth2/token", "POST",
            Http.form(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to Config.REDIRECT_URI,
                "client_id" to Config.OSM_CLIENT_ID,
                "code_verifier" to verifier,
            ),
        )
        val token = JSONObject(res).getString("access_token")
        val details = JSONObject(Http.request("${Config.OSM_API}/user/details.json", bearer = token))
        val name = details.getJSONObject("user").getString("display_name")
        p.edit().putString("token", token).putString("user", name).remove("verifier").remove("state").apply()
        return name
    }

    private fun random(bytes: Int): String = b64(ByteArray(bytes).also { SecureRandom().nextBytes(it) })
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

/** Lo que el usuario completa al marcar una cámara. */
data class Draft(
    val lat: Double,
    val lon: Double,
    val kind: Kind = Kind.FIXED,
    val agency: Agency = Agency.UNKNOWN,
    val operatorName: String = "",
    val direction: Int? = null,
    val notes: String = "",
    val seenInPerson: Boolean = false,
) {
    fun tags(): Map<String, String> = buildMap {
        put("man_made", "surveillance")
        put("surveillance", "public")
        put("surveillance:type", if (kind == Kind.ALPR) "ALPR" else "camera")
        put("camera:type", if (kind == Kind.DOME) "dome" else "fixed")
        put("surveillance:zone", if (kind == Kind.ALPR || agency == Agency.TRAFFIC) "traffic" else "street")
        put("operator:type", "government")
        operatorName.trim().takeIf { it.isNotEmpty() }?.let { put("operator", it.take(255)) }
        direction?.let { put("camera:direction", it.toString()) }
        notes.trim().takeIf { it.isNotEmpty() }?.let { put("description", it.take(255)) }
    }
}

enum class Agency(val label: String, val hint: String) {
    CITY("Municipio", "Ej.: Municipalidad de Rosario"),
    POLICE("Policía", "Ej.: Policía de la Ciudad"),
    PROVINCE("Provincia / Estado", "Ej.: Ministerio de Seguridad de Córdoba"),
    NATIONAL("Gobierno nacional", "Ej.: Gendarmería Nacional"),
    TRAFFIC("Tránsito", "Ej.: Agencia de Seguridad Vial"),
    UNKNOWN("No sé cuál", "Si sabés el organismo, escribilo"),
}

/** Publica una cámara nueva en OpenStreetMap: abre un "changeset", crea el punto y lo cierra. */
object OsmEditor {

    suspend fun publish(c: Context, draft: Draft): Long {
        val token = OsmAuth.token(c) ?: throw IllegalStateException("Iniciá sesión en OpenStreetMap")
        val cs = Http.request(
            "${Config.OSM_API}/changeset/create", "PUT",
            osm(
                "<changeset>" +
                    tag("created_by", "Camaras Android ${BuildInfo.VERSION}") +
                    tag("comment", "Agrega cámara de vigilancia estatal") +
                    tag("source", "survey") +
                    tag("locale", "es") +
                    "</changeset>",
            ),
            contentType = "text/xml; charset=utf-8", bearer = token,
        ).trim().toLong()
        try {
            val lat = "%.7f".format(java.util.Locale.US, draft.lat)
            val lon = "%.7f".format(java.util.Locale.US, draft.lon)
            val node = Http.request(
                "${Config.OSM_API}/node/create", "PUT",
                osm("<node changeset=\"$cs\" lat=\"$lat\" lon=\"$lon\">" + draft.tags().entries.joinToString("") { tag(it.key, it.value) } + "</node>"),
                contentType = "text/xml; charset=utf-8", bearer = token,
            ).trim().toLong()
            return node
        } finally {
            runCatching {
                Http.request("${Config.OSM_API}/changeset/$cs/close", "PUT", "", contentType = "text/xml", bearer = token)
            }
        }
    }

    private fun osm(inner: String) = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><osm version=\"0.6\">$inner</osm>"
    private fun tag(k: String, v: String) = "<tag k=\"${esc(k)}\" v=\"${esc(v)}\"/>"
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;").replace("\n", " ")
}

object BuildInfo {
    const val VERSION = "1.0"
}
