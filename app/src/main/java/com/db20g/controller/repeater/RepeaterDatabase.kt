package com.db20g.controller.repeater

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages the GMRS repeater database. Loads from bundled assets and supports
 * user-added repeaters saved to local storage.
 */
class RepeaterDatabase(private val context: Context) {

    companion object {
        private const val TAG = "RepeaterDatabase"
        private const val ASSET_FILE = "gmrs_repeaters.json"
        private const val USER_FILE = "user_repeaters.json"
    }

    private val repeaters = mutableListOf<GmrsRepeater>()
    private var assetRepeaterCount = 0
    val all: List<GmrsRepeater> get() = repeaters.toList()
    val count: Int get() = repeaters.size

    /**
     * Load repeaters from the bundled asset file and any user-added repeaters.
     */
    fun load() {
        repeaters.clear()

        // Load bundled repeaters from assets
        try {
            val json = context.assets.open(ASSET_FILE).bufferedReader().readText()
            parseRepeaterJson(json)
            assetRepeaterCount = repeaters.size
            Log.i(TAG, "Loaded ${repeaters.size} repeaters from assets")
        } catch (e: Exception) {
            Log.w(TAG, "No bundled repeater data found: ${e.message}")
        }

        // Load user-added repeaters
        try {
            val userFile = File(context.filesDir, USER_FILE)
            if (userFile.exists()) {
                val json = userFile.readText()
                val countBefore = repeaters.size
                parseRepeaterJson(json)
                Log.i(TAG, "Loaded ${repeaters.size - countBefore} user repeaters")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading user repeaters: ${e.message}")
        }
    }

    /**
     * Find the nearest repeaters to the given location.
     */
    fun findNearest(latitude: Double, longitude: Double, limit: Int = 10): List<RepeaterWithDistance> {
        return repeaters
            .filter { it.status != RepeaterStatus.OFF_AIR }
            .map { RepeaterWithDistance(it, it.distanceMilesFrom(latitude, longitude)) }
            .sortedBy { it.distanceMiles }
            .take(limit)
    }

    /**
     * Find repeaters within a given radius (miles).
     */
    fun findWithinRadius(latitude: Double, longitude: Double, radiusMiles: Double): List<RepeaterWithDistance> {
        return repeaters
            .filter { it.status != RepeaterStatus.OFF_AIR }
            .map { RepeaterWithDistance(it, it.distanceMilesFrom(latitude, longitude)) }
            .filter { it.distanceMiles <= radiusMiles }
            .sortedBy { it.distanceMiles }
    }

    /**
     * Search repeaters by callsign, city, or state.
     */
    fun search(query: String): List<GmrsRepeater> {
        val q = query.uppercase()
        return repeaters.filter {
            it.callsign.uppercase().contains(q) ||
            it.city.uppercase().contains(q) ||
            it.state.uppercase().contains(q) ||
            it.county.uppercase().contains(q)
        }
    }

    /**
     * Add a user-defined repeater and save to local storage.
     */
    fun addUserRepeater(repeater: GmrsRepeater) {
        repeaters.add(repeater)
        saveUserRepeaters()
    }

    /**
     * Import repeaters from a JSON string (e.g., downloaded from myGMRS.com export).
     */
    fun importFromJson(json: String): Int {
        val countBefore = repeaters.size
        parseRepeaterJson(json)
        saveUserRepeaters()
        return repeaters.size - countBefore
    }

    private fun parseRepeaterJson(json: String) {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            try {
                repeaters.add(parseRepeater(obj))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed repeater entry at index $i: ${e.message}")
            }
        }
    }

    private fun parseRepeater(obj: JSONObject): GmrsRepeater {
        return GmrsRepeater(
            id = obj.optString("id", ""),
            callsign = obj.getString("callsign"),
            outputFrequency = obj.getDouble("output_frequency"),
            inputFrequency = obj.getDouble("input_frequency"),
            ctcssTone = obj.optDouble("ctcss_tone", 0.0),
            dcsCode = obj.optInt("dcs_code", 0),
            latitude = obj.getDouble("latitude"),
            longitude = obj.getDouble("longitude"),
            city = obj.optString("city", ""),
            state = obj.optString("state", ""),
            county = obj.optString("county", ""),
            description = obj.optString("description", ""),
            status = when (obj.optString("status", "unknown").lowercase()) {
                "on_air", "on air", "active" -> RepeaterStatus.ON_AIR
                "off_air", "off air", "inactive" -> RepeaterStatus.OFF_AIR
                "testing" -> RepeaterStatus.TESTING
                else -> RepeaterStatus.UNKNOWN
            },
            gmrsChannel = obj.optString("gmrs_channel", obj.optString("gmrsChannel", ""))
        )
    }

    private fun saveUserRepeaters() {
        try {
            // Only save repeaters added after the asset-loaded ones
            val userRepeaters = repeaters.drop(assetRepeaterCount)
            if (userRepeaters.isEmpty()) return
            val userFile = File(context.filesDir, USER_FILE)
            val array = JSONArray()
            for (r in userRepeaters) {
                array.put(repeaterToJson(r))
            }
            userFile.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user repeaters", e)
        }
    }

    private fun repeaterToJson(r: GmrsRepeater): JSONObject {
        return JSONObject().apply {
            put("id", r.id)
            put("callsign", r.callsign)
            put("output_frequency", r.outputFrequency)
            put("input_frequency", r.inputFrequency)
            put("ctcss_tone", r.ctcssTone)
            put("dcs_code", r.dcsCode)
            put("latitude", r.latitude)
            put("longitude", r.longitude)
            put("city", r.city)
            put("state", r.state)
            put("county", r.county)
            put("description", r.description)
            put("status", r.status.name.lowercase())
            put("gmrs_channel", r.gmrsChannel)
        }
    }
}

data class RepeaterWithDistance(
    val repeater: GmrsRepeater,
    val distanceMiles: Double
) {
    val distanceDisplay: String
        get() = if (distanceMiles < 1.0) {
            "%.1f mi".format(distanceMiles)
        } else {
            "%.0f mi".format(distanceMiles)
        }
}
