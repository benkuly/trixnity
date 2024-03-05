package net.folivo.trixnity.core.model.events.m.room

data class GeoUri(val full: String) {
    val latitude: Double
    val longitude: Double

    init {
        val uri = full.substringAfter("geo:")
        val parts = uri.split(",")
        latitude = parts[0].toDouble()
        longitude = parts[1].toDouble()
    }

    constructor(latitude: Double, longitude: Double) : this("geo:$latitude,$longitude")
}
