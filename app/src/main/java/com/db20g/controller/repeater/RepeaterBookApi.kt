package com.db20g.controller.repeater

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the RepeaterBook GMRS API.
 * Provides live repeater search with local caching and offline fallback.
 *
 * API docs: https://www.repeaterbook.com/api/
 * GMRS endpoint returns JSON array of repeater objects.
 */
class RepeaterBookApi(private val context: Context) {

    companion object {
        private const val TAG = "RepeaterBookApi"
        private const val BASE_URL = "https://www.repeaterbook.com/api/export.php"
        private const val CACHE_DIR = "repeaterbook_cache"
        private const val CACHE_INDEX_FILE = "cache_index.json"
        private const val DEFAULT_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    private val cacheDir: File = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
    var cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS

    // --- Public API ---

    /**
     * Search GMRS repeaters near a location. Returns cached data if available and fresh,
     * otherwise fetches from RepeaterBook API. Falls back to cache if offline.
     */
    suspend fun searchNearby(
        latitude: Double,
        longitude: Double,
        radiusMiles: Int = 50
    ): ApiResult<List<GmrsRepeater>> = withContext(Dispatchers.IO) {
        val cacheKey = "nearby_%.3f_%.3f_%d".format(latitude, longitude, radiusMiles)
        val cached = loadFromCache(cacheKey)
        if (cached != null) {
            return@withContext ApiResult.Success(cached, fromCache = true)
        }

        if (!isNetworkAvailable()) {
            val stale = loadFromCache(cacheKey, ignoreExpiry = true)
            if (stale != null) {
                return@withContext ApiResult.Success(stale, fromCache = true, stale = true)
            }
            return@withContext ApiResult.Error("No internet connection and no cached data available")
        }

        try {
            val params = buildString {
                append("type=gmrs")
                append("&lat=%.6f".format(latitude))
                append("&lng=%.6f".format(longitude))
                append("&distance=$radiusMiles")
            }
            val repeaters = fetchAndParse(params)
            saveToCache(cacheKey, repeaters)
            ApiResult.Success(repeaters, fromCache = false)
        } catch (e: Exception) {
            Log.e(TAG, "API request failed", e)
            val stale = loadFromCache(cacheKey, ignoreExpiry = true)
            if (stale != null) {
                ApiResult.Success(stale, fromCache = true, stale = true)
            } else {
                ApiResult.Error("Search failed: ${e.message}")
            }
        }
    }

    /**
     * Search GMRS repeaters by US state abbreviation (e.g., "CA", "TX").
     */
    suspend fun searchByState(state: String): ApiResult<List<GmrsRepeater>> =
        withContext(Dispatchers.IO) {
            val stateUpper = state.uppercase().take(2)
            val cacheKey = "state_$stateUpper"
            val cached = loadFromCache(cacheKey)
            if (cached != null) {
                return@withContext ApiResult.Success(cached, fromCache = true)
            }

            if (!isNetworkAvailable()) {
                val stale = loadFromCache(cacheKey, ignoreExpiry = true)
                if (stale != null) {
                    return@withContext ApiResult.Success(stale, fromCache = true, stale = true)
                }
                return@withContext ApiResult.Error("No internet connection and no cached data available")
            }

            try {
                val params = "type=gmrs&state=$stateUpper"
                val repeaters = fetchAndParse(params)
                saveToCache(cacheKey, repeaters)
                ApiResult.Success(repeaters, fromCache = false)
            } catch (e: Exception) {
                Log.e(TAG, "State search failed", e)
                val stale = loadFromCache(cacheKey, ignoreExpiry = true)
                if (stale != null) {
                    ApiResult.Success(stale, fromCache = true, stale = true)
                } else {
                    ApiResult.Error("Search failed: ${e.message}")
                }
            }
        }

    /**
     * Search GMRS repeaters by frequency (MHz).
     */
    suspend fun searchByFrequency(frequencyMHz: Double): ApiResult<List<GmrsRepeater>> =
        withContext(Dispatchers.IO) {
            val cacheKey = "freq_%.4f".format(frequencyMHz)
            val cached = loadFromCache(cacheKey)
            if (cached != null) {
                return@withContext ApiResult.Success(cached, fromCache = true)
            }

            if (!isNetworkAvailable()) {
                val stale = loadFromCache(cacheKey, ignoreExpiry = true)
                if (stale != null) {
                    return@withContext ApiResult.Success(stale, fromCache = true, stale = true)
                }
                return@withContext ApiResult.Error("No internet connection and no cached data available")
            }

            try {
                val params = "type=gmrs&frequency=%.4f".format(frequencyMHz)
                val repeaters = fetchAndParse(params)
                saveToCache(cacheKey, repeaters)
                ApiResult.Success(repeaters, fromCache = false)
            } catch (e: Exception) {
                Log.e(TAG, "Frequency search failed", e)
                val stale = loadFromCache(cacheKey, ignoreExpiry = true)
                if (stale != null) {
                    ApiResult.Success(stale, fromCache = true, stale = true)
                } else {
                    ApiResult.Error("Search failed: ${e.message}")
                }
            }
        }

    /**
     * Clear all cached data.
     */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "Cache cleared")
    }

    /**
     * Get cache statistics: number of entries, total size.
     */
    fun cacheStats(): CacheStats {
        val files = cacheDir.listFiles()?.filter { it.name != CACHE_INDEX_FILE } ?: emptyList()
        val totalBytes = files.sumOf { it.length() }
        return CacheStats(files.size, totalBytes)
    }

    // --- HTTP ---

    private fun fetchAndParse(queryParams: String): List<GmrsRepeater> {
        val url = URL("$BASE_URL?$queryParams")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "DB20G-Controller-Android/1.0")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw ApiException("HTTP $responseCode: ${connection.responseMessage}")
            }

            val body = connection.inputStream.bufferedReader().readText()
            return parseRepeaterBookResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parse the RepeaterBook JSON response into GmrsRepeater objects.
     * RepeaterBook returns either a JSON array of results or an object with a "results" array.
     */
    private fun parseRepeaterBookResponse(body: String): List<GmrsRepeater> {
        val repeaters = mutableListOf<GmrsRepeater>()
        val trimmed = body.trim()

        val array: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                when {
                    obj.has("results") -> obj.getJSONArray("results")
                    obj.has("data") -> obj.getJSONArray("data")
                    obj.has("count") && obj.getInt("count") == 0 -> return emptyList()
                    else -> {
                        Log.w(TAG, "Unexpected response format: ${trimmed.take(200)}")
                        return emptyList()
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unexpected response: ${trimmed.take(200)}")
                return emptyList()
            }
        }

        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                repeaters.add(parseRepeaterBookEntry(obj))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed entry $i: ${e.message}")
            }
        }

        return repeaters
    }

    /**
     * Map RepeaterBook JSON fields to our GmrsRepeater model.
     * RepeaterBook field names vary slightly; handle common variations.
     */
    private fun parseRepeaterBookEntry(obj: JSONObject): GmrsRepeater {
        val outputFreq = obj.optDouble("Frequency", obj.optDouble("frequency", 0.0))
        val inputFreq = obj.optDouble("Input Freq", obj.optDouble("input_freq",
            obj.optDouble("input_frequency", outputFreq - 5.0)))

        val ctcssTone = obj.optDouble("PL", obj.optDouble("pl",
            obj.optDouble("Uplink Tone", obj.optDouble("ctcss_tone", 0.0))))

        val dcsCode = obj.optInt("TSQ", obj.optInt("dcs_code", 0))

        val lat = obj.optDouble("Lat", obj.optDouble("lat", obj.optDouble("latitude", 0.0)))
        val lon = obj.optDouble("Long", obj.optDouble("lng",
            obj.optDouble("lon", obj.optDouble("longitude", 0.0))))

        val callsign = obj.optString("Callsign", obj.optString("callsign", ""))
        val city = obj.optString("Nearest City", obj.optString("city", ""))
        val state = obj.optString("State", obj.optString("state", ""))
        val county = obj.optString("County", obj.optString("county", ""))

        val statusStr = obj.optString("Operational Status",
            obj.optString("status", "On-Air")).lowercase()
        val status = when {
            statusStr.contains("on") || statusStr.contains("active") -> RepeaterStatus.ON_AIR
            statusStr.contains("off") || statusStr.contains("inactive") -> RepeaterStatus.OFF_AIR
            statusStr.contains("test") -> RepeaterStatus.TESTING
            else -> RepeaterStatus.UNKNOWN
        }

        val lastUpdate = obj.optString("Last Update", obj.optString("last_update", ""))
        val notes = obj.optString("Notes", obj.optString("notes", ""))
        val use = obj.optString("Use", obj.optString("use", "OPEN"))

        // Build description from available metadata
        val description = buildString {
            if (use.isNotBlank() && use != "OPEN") append("Use: $use. ")
            if (notes.isNotBlank()) append(notes.take(200))
            if (lastUpdate.isNotBlank()) append(" (Updated: $lastUpdate)")
        }.trim()

        // Determine GMRS channel from output frequency
        val gmrsChannel = gmrsChannelFromFrequency(outputFreq)

        return GmrsRepeater(
            id = obj.optString("State ID", obj.optString("id", "${callsign}_${outputFreq}")),
            callsign = callsign,
            outputFrequency = outputFreq,
            inputFrequency = inputFreq,
            ctcssTone = ctcssTone,
            dcsCode = dcsCode,
            latitude = lat,
            longitude = lon,
            city = city,
            state = state,
            county = county,
            description = description,
            status = status,
            gmrsChannel = gmrsChannel
        )
    }

    /**
     * Determine the GMRS channel designation from the output frequency.
     * Channels 15R-22R are the GMRS repeater outputs.
     */
    private fun gmrsChannelFromFrequency(freqMHz: Double): String {
        val channelMap = mapOf(
            462.5500 to "15R", 462.5750 to "16R", 462.6000 to "17R", 462.6250 to "18R",
            462.6500 to "19R", 462.6750 to "20R", 462.7000 to "21R", 462.7250 to "22R"
        )
        // Match within 1kHz tolerance
        for ((freq, ch) in channelMap) {
            if (kotlin.math.abs(freqMHz - freq) < 0.001) return ch
        }
        return ""
    }

    // --- Cache ---

    private fun loadFromCache(key: String, ignoreExpiry: Boolean = false): List<GmrsRepeater>? {
        val file = File(cacheDir, "${key.hashCode()}.json")
        if (!file.exists()) return null

        try {
            val json = JSONObject(file.readText())
            val timestamp = json.getLong("timestamp")
            if (!ignoreExpiry && (System.currentTimeMillis() - timestamp) > cacheTtlMs) {
                return null // Expired
            }
            val array = json.getJSONArray("data")
            val repeaters = mutableListOf<GmrsRepeater>()
            for (i in 0 until array.length()) {
                repeaters.add(parseRepeaterBookEntry(array.getJSONObject(i)))
            }
            Log.d(TAG, "Cache hit for $key: ${repeaters.size} repeaters")
            return repeaters
        } catch (e: Exception) {
            Log.w(TAG, "Cache read error for $key", e)
            return null
        }
    }

    private fun saveToCache(key: String, repeaters: List<GmrsRepeater>) {
        try {
            val array = JSONArray()
            for (r in repeaters) {
                array.put(JSONObject().apply {
                    put("Callsign", r.callsign)
                    put("Frequency", r.outputFrequency)
                    put("Input Freq", r.inputFrequency)
                    put("PL", r.ctcssTone)
                    put("TSQ", r.dcsCode)
                    put("Lat", r.latitude)
                    put("Long", r.longitude)
                    put("Nearest City", r.city)
                    put("State", r.state)
                    put("County", r.county)
                    put("Notes", r.description)
                    put("Operational Status", r.status.name)
                    put("State ID", r.id)
                })
            }
            val wrapper = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("key", key)
                put("data", array)
            }
            val file = File(cacheDir, "${key.hashCode()}.json")
            file.writeText(wrapper.toString())
            Log.d(TAG, "Cached ${repeaters.size} repeaters for $key")
        } catch (e: Exception) {
            Log.w(TAG, "Cache write error for $key", e)
        }
    }

    // --- Network ---

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // --- Data classes ---

    sealed class ApiResult<out T> {
        data class Success<T>(
            val data: T,
            val fromCache: Boolean = false,
            val stale: Boolean = false
        ) : ApiResult<T>()

        data class Error(val message: String) : ApiResult<Nothing>()
    }

    data class CacheStats(val entries: Int, val totalBytes: Long) {
        val totalKb: Long get() = totalBytes / 1024
    }

    class ApiException(message: String) : Exception(message)
}
