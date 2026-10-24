package com.cogito.camaras

object Config {
    /**
     * Client ID de la aplicación registrada en openstreetmap.org/oauth2/applications.
     * Vacío = la app abre el editor web de OpenStreetMap para agregar cámaras.
     */
    const val OSM_CLIENT_ID = ""

    const val REDIRECT_URI = "camarasmap://oauth"
    const val OSM_WEB = "https://www.openstreetmap.org"
    const val OSM_API = "https://api.openstreetmap.org/api/0.6"

    /** Estilo de mapa claro tipo Google Maps, gratis y sin clave (OpenFreeMap). */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** Servidores públicos que responden consultas sobre los datos de OpenStreetMap. */
    val OVERPASS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    const val NOMINATIM = "https://nominatim.openstreetmap.org/search"

    /** Identificación que piden los servidores de OpenStreetMap. */
    const val USER_AGENT = "CamarasMapa/1.0 (Android; app comunitaria de camaras de vigilancia)"

    /** Por debajo de este zoom no se piden cámaras (el área sería enorme). */
    const val MIN_ZOOM_CAMERAS = 12.0
}
