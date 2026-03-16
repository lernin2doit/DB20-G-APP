package com.db20g.controller.repeater

/**
 * Represents a GMRS repeater with its operating parameters and location.
 */
data class GmrsRepeater(
    val id: String,
    val callsign: String,
    val outputFrequency: Double,    // MHz — what you receive (repeater TX)
    val inputFrequency: Double,     // MHz — what you transmit (repeater RX)
    val ctcssTone: Double,          // Hz, 0.0 if none required
    val dcsCode: Int,               // DCS code, 0 if none
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val state: String,
    val county: String,
    val description: String,
    val status: RepeaterStatus,
    val gmrsChannel: String         // GMRS repeater channel (e.g., "15R", "20R")
) {
    /** Distance from a given point in miles */
    fun distanceMilesFrom(lat: Double, lon: Double): Double {
        val earthRadiusMiles = 3958.8
        val dLat = Math.toRadians(latitude - lat)
        val dLon = Math.toRadians(longitude - lon)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusMiles * c
    }

    /** Bearing from a given point in degrees */
    fun bearingFrom(lat: Double, lon: Double): Double {
        val dLon = Math.toRadians(longitude - lon)
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(latitude)
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }

    val displayName: String
        get() = buildString {
            append(callsign)
            if (city.isNotEmpty()) append(" — $city")
            if (state.isNotEmpty()) append(", $state")
        }

    val frequencyDisplay: String
        get() = "%.4f / %.4f MHz".format(outputFrequency, inputFrequency)

    val toneDisplay: String
        get() = when {
            ctcssTone > 0 -> "%.1f Hz".format(ctcssTone)
            dcsCode > 0 -> "DCS %03d".format(dcsCode)
            else -> "None"
        }
}

enum class RepeaterStatus {
    ON_AIR,
    OFF_AIR,
    TESTING,
    UNKNOWN
}
