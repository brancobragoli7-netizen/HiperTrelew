package com.cogito.camaras

/** Lo que el usuario completa al marcar (o editar) un punto. */
data class Draft(
    val lat: Double,
    val lon: Double,
    val kind: Kind = Kind.FIXED,
    val agency: Agency = Agency.UNKNOWN,
    val operatorName: String = "",
    val direction: Int? = null,
    val notes: String = "",
    val seenInPerson: Boolean = false,
    /** null = punto nuevo; si no, el id del punto que se está editando. */
    val editingId: Long? = null,
) {
    fun tags(): Map<String, String> = buildMap {
        put("surveillance:type", if (kind == Kind.ALPR) "ALPR" else "camera")
        put("camera:type", if (kind == Kind.DOME) "dome" else "fixed")
        put("surveillance:zone", if (kind == Kind.ALPR || agency == Agency.TRAFFIC) "traffic" else "street")
        put("operator:type", "government")
        put("agency", agency.name)
        operatorName.trim().takeIf { it.isNotEmpty() }?.let { put("operator", it.take(255)) }
        direction?.let { put("camera:direction", it.toString()) }
        notes.trim().takeIf { it.isNotEmpty() }?.let { put("description", it.take(500)) }
        if (seenInPerson) put("seen", "yes")
    }

    companion object {
        fun from(c: Camera) = Draft(
            lat = c.lat,
            lon = c.lon,
            kind = c.kind,
            agency = c.agency ?: Agency.UNKNOWN,
            operatorName = c.operator ?: "",
            direction = c.directionDeg,
            notes = c.tags["description"] ?: "",
            seenInPerson = c.seenInPerson,
            editingId = c.id,
        )
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
