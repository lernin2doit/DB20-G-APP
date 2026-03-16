package com.db20g.controller.repeater

import android.content.Context
import android.location.Geocoder
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Travel Route Repeater Planner.
 * Finds GMRS repeaters along a travel corridor and generates
 * an optimized channel list for programming into the radio.
 *
 * Samples waypoints along a great-circle path between start/end,
 * queries RepeaterBook for nearby repeaters at each waypoint,
 * deduplicates, and sorts by route position to produce a
 * sequential handoff list.
 */
class RoutePlanner(private val context: Context) {

    companion object {
        private const val TAG = "RoutePlanner"
        private const val SAVED_ROUTES_DIR = "saved_routes"
        /** Default corridor width: repeaters within this many miles of the route */
        private const val DEFAULT_CORRIDOR_MILES = 35
        /** Sample a waypoint every this many miles along the route */
        private const val WAYPOINT_INTERVAL_MILES = 25.0
        /** Minimum gap distance in miles to flag a coverage gap */
        private const val COVERAGE_GAP_THRESHOLD_MILES = 50.0
    }

    private val api = RepeaterBookApi(context)
    private val savedRoutesDir = File(context.filesDir, SAVED_ROUTES_DIR).also { it.mkdirs() }

    /**
     * A waypoint along the travel route.
     */
    data class Waypoint(
        val latitude: Double,
        val longitude: Double,
        val distanceFromStartMiles: Double
    )

    /**
     * A repeater positioned along the route, with its distance from the start.
     */
    data class RouteRepeater(
        val repeater: GmrsRepeater,
        val routeDistanceMiles: Double,
        val corridorDistanceMiles: Double
    )

    /**
     * A gap in repeater coverage along the route.
     */
    data class CoverageGap(
        val startMiles: Double,
        val endMiles: Double,
        val gapMiles: Double,
        val startWaypoint: Waypoint,
        val endWaypoint: Waypoint
    )

    /**
     * Full route plan result.
     */
    data class RoutePlan(
        val startAddress: String,
        val endAddress: String,
        val startLat: Double,
        val startLon: Double,
        val endLat: Double,
        val endLon: Double,
        val totalDistanceMiles: Double,
        val corridorMiles: Int,
        val repeaters: List<RouteRepeater>,
        val coverageGaps: List<CoverageGap>,
        val waypoints: List<Waypoint>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val summary: String
            get() = buildString {
                appendLine("Route: $startAddress → $endAddress")
                appendLine("Distance: ${"%.0f".format(totalDistanceMiles)} miles")
                appendLine("Repeaters found: ${repeaters.size}")
                appendLine("Coverage corridor: ±${corridorMiles} miles")
                if (coverageGaps.isNotEmpty()) {
                    appendLine("Coverage gaps: ${coverageGaps.size}")
                    coverageGaps.forEach { gap ->
                        appendLine("  Gap: ${"%.0f".format(gap.startMiles)}-${"%.0f".format(gap.endMiles)} mi (${"%.0f".format(gap.gapMiles)} mi)")
                    }
                } else {
                    appendLine("Full coverage along route!")
                }
            }
    }

    /**
     * Geocode an address string to lat/lon coordinates.
     * Returns null if geocoding fails.
     */
    fun geocodeAddress(address: String): Pair<Double, Double>? {
        return try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.US)
            val results = geocoder.getFromLocationName(address, 1)
            if (!results.isNullOrEmpty()) {
                Pair(results[0].latitude, results[0].longitude)
            } else {
                Log.w(TAG, "Geocoding returned no results for: $address")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed for: $address", e)
            null
        }
    }

    /**
     * Plan a route between two coordinates, finding all GMRS repeaters
     * along the corridor.
     */
    suspend fun planRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        corridorMiles: Int = DEFAULT_CORRIDOR_MILES,
        startAddress: String = "%.4f, %.4f".format(startLat, startLon),
        endAddress: String = "%.4f, %.4f".format(endLat, endLon)
    ): RoutePlan {
        // Generate waypoints along the great-circle path
        val totalDistance = haversineDistance(startLat, startLon, endLat, endLon)
        val waypoints = generateWaypoints(startLat, startLon, endLat, endLon, totalDistance)

        Log.d(TAG, "Route: ${"%.0f".format(totalDistance)} miles, ${waypoints.size} waypoints")

        // Query repeaters near each waypoint
        val allRepeaters = mutableMapOf<String, RouteRepeater>()
        for (wp in waypoints) {
            val result = api.searchNearby(wp.latitude, wp.longitude, corridorMiles)
            if (result is RepeaterBookApi.ApiResult.Success) {
                for (rpt in result.data) {
                    // Calculate where along the route this repeater is nearest
                    val nearestWp = waypoints.minByOrNull {
                        rpt.distanceMilesFrom(it.latitude, it.longitude)
                    } ?: wp
                    val corridorDist = rpt.distanceMilesFrom(nearestWp.latitude, nearestWp.longitude)

                    // Only include repeaters actually within the corridor
                    if (corridorDist <= corridorMiles) {
                        val key = rpt.id.ifEmpty { "${rpt.callsign}_${rpt.outputFrequency}" }
                        val existing = allRepeaters[key]
                        if (existing == null || corridorDist < existing.corridorDistanceMiles) {
                            allRepeaters[key] = RouteRepeater(
                                repeater = rpt,
                                routeDistanceMiles = nearestWp.distanceFromStartMiles,
                                corridorDistanceMiles = corridorDist
                            )
                        }
                    }
                }
            }
        }

        // Sort by route position (distance from start)
        val sortedRepeaters = allRepeaters.values
            .filter { it.repeater.status == RepeaterStatus.ON_AIR || it.repeater.status == RepeaterStatus.UNKNOWN }
            .sortedBy { it.routeDistanceMiles }

        // Detect coverage gaps
        val gaps = detectCoverageGaps(sortedRepeaters, waypoints, totalDistance, corridorMiles)

        return RoutePlan(
            startAddress = startAddress,
            endAddress = endAddress,
            startLat = startLat,
            startLon = startLon,
            endLat = endLat,
            endLon = endLon,
            totalDistanceMiles = totalDistance,
            corridorMiles = corridorMiles,
            repeaters = sortedRepeaters,
            coverageGaps = gaps,
            waypoints = waypoints
        )
    }

    /**
     * Generate evenly-spaced waypoints along a great-circle path.
     */
    private fun generateWaypoints(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
        totalDistanceMiles: Double
    ): List<Waypoint> {
        val waypoints = mutableListOf<Waypoint>()
        val numSegments = maxOf(2, (totalDistanceMiles / WAYPOINT_INTERVAL_MILES).toInt())

        for (i in 0..numSegments) {
            val fraction = i.toDouble() / numSegments
            val point = interpolateGreatCircle(startLat, startLon, endLat, endLon, fraction)
            waypoints.add(
                Waypoint(
                    latitude = point.first,
                    longitude = point.second,
                    distanceFromStartMiles = totalDistanceMiles * fraction
                )
            )
        }
        return waypoints
    }

    /**
     * Interpolate a point along the great-circle path between two coordinates.
     * @param fraction 0.0 = start, 1.0 = end
     */
    private fun interpolateGreatCircle(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
        fraction: Double
    ): Pair<Double, Double> {
        val phi1 = Math.toRadians(lat1)
        val lambda1 = Math.toRadians(lon1)
        val phi2 = Math.toRadians(lat2)
        val lambda2 = Math.toRadians(lon2)

        val dPhi = phi2 - phi1
        val dLambda = lambda2 - lambda1
        val a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2)
        val delta = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        if (delta < 1e-10) return Pair(lat1, lon1)

        val aSin = Math.sin((1 - fraction) * delta) / Math.sin(delta)
        val bSin = Math.sin(fraction * delta) / Math.sin(delta)

        val x = aSin * Math.cos(phi1) * Math.cos(lambda1) + bSin * Math.cos(phi2) * Math.cos(lambda2)
        val y = aSin * Math.cos(phi1) * Math.sin(lambda1) + bSin * Math.cos(phi2) * Math.sin(lambda2)
        val z = aSin * Math.sin(phi1) + bSin * Math.sin(phi2)

        val lat = Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y)))
        val lon = Math.toDegrees(Math.atan2(y, x))
        return Pair(lat, lon)
    }

    /**
     * Detect coverage gaps along the route where no repeater is within range.
     */
    private fun detectCoverageGaps(
        repeaters: List<RouteRepeater>,
        waypoints: List<Waypoint>,
        totalDistance: Double,
        corridorMiles: Int
    ): List<CoverageGap> {
        if (repeaters.isEmpty()) {
            // The entire route is a gap
            return listOf(
                CoverageGap(
                    startMiles = 0.0,
                    endMiles = totalDistance,
                    gapMiles = totalDistance,
                    startWaypoint = waypoints.first(),
                    endWaypoint = waypoints.last()
                )
            )
        }

        val gaps = mutableListOf<CoverageGap>()
        val coverageRadius = corridorMiles.toDouble()

        // Check gap from start to first repeater
        val firstRpt = repeaters.first()
        val startGap = firstRpt.routeDistanceMiles - coverageRadius
        if (startGap > COVERAGE_GAP_THRESHOLD_MILES) {
            gaps.add(
                CoverageGap(
                    startMiles = 0.0,
                    endMiles = firstRpt.routeDistanceMiles - coverageRadius,
                    gapMiles = startGap,
                    startWaypoint = waypoints.first(),
                    endWaypoint = findNearestWaypoint(waypoints, firstRpt.routeDistanceMiles - coverageRadius)
                )
            )
        }

        // Check gaps between consecutive repeaters
        for (i in 0 until repeaters.size - 1) {
            val current = repeaters[i]
            val next = repeaters[i + 1]
            val gapStart = current.routeDistanceMiles + coverageRadius
            val gapEnd = next.routeDistanceMiles - coverageRadius
            val gapDist = gapEnd - gapStart

            if (gapDist > COVERAGE_GAP_THRESHOLD_MILES) {
                gaps.add(
                    CoverageGap(
                        startMiles = gapStart,
                        endMiles = gapEnd,
                        gapMiles = gapDist,
                        startWaypoint = findNearestWaypoint(waypoints, gapStart),
                        endWaypoint = findNearestWaypoint(waypoints, gapEnd)
                    )
                )
            }
        }

        // Check gap from last repeater to end
        val lastRpt = repeaters.last()
        val endGap = totalDistance - (lastRpt.routeDistanceMiles + coverageRadius)
        if (endGap > COVERAGE_GAP_THRESHOLD_MILES) {
            gaps.add(
                CoverageGap(
                    startMiles = lastRpt.routeDistanceMiles + coverageRadius,
                    endMiles = totalDistance,
                    gapMiles = endGap,
                    startWaypoint = findNearestWaypoint(waypoints, lastRpt.routeDistanceMiles + coverageRadius),
                    endWaypoint = waypoints.last()
                )
            )
        }

        return gaps
    }

    private fun findNearestWaypoint(waypoints: List<Waypoint>, routeMiles: Double): Waypoint {
        return waypoints.minByOrNull { kotlin.math.abs(it.distanceFromStartMiles - routeMiles) }
            ?: waypoints.first()
    }

    // --- Haversine ---

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMiles = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusMiles * c
    }

    // --- Save / Load Route Plans ---

    /**
     * Save a route plan to local storage for reuse.
     */
    fun savePlan(plan: RoutePlan, name: String): Boolean {
        return try {
            val json = JSONObject().apply {
                put("name", name)
                put("startAddress", plan.startAddress)
                put("endAddress", plan.endAddress)
                put("startLat", plan.startLat)
                put("startLon", plan.startLon)
                put("endLat", plan.endLat)
                put("endLon", plan.endLon)
                put("corridorMiles", plan.corridorMiles)
                put("timestamp", plan.timestamp)
                put("repeaters", JSONArray().apply {
                    plan.repeaters.forEach { rr ->
                        put(JSONObject().apply {
                            put("callsign", rr.repeater.callsign)
                            put("outputFrequency", rr.repeater.outputFrequency)
                            put("inputFrequency", rr.repeater.inputFrequency)
                            put("ctcssTone", rr.repeater.ctcssTone)
                            put("dcsCode", rr.repeater.dcsCode)
                            put("latitude", rr.repeater.latitude)
                            put("longitude", rr.repeater.longitude)
                            put("city", rr.repeater.city)
                            put("state", rr.repeater.state)
                            put("county", rr.repeater.county)
                            put("gmrsChannel", rr.repeater.gmrsChannel)
                            put("routeDistance", rr.routeDistanceMiles)
                            put("corridorDistance", rr.corridorDistanceMiles)
                        })
                    }
                })
            }
            val safeFileName = name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            File(savedRoutesDir, "$safeFileName.json").writeText(json.toString(2))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save route plan", e)
            false
        }
    }

    /**
     * List all saved route plan names.
     */
    fun listSavedPlans(): List<SavedPlanInfo> {
        return savedRoutesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val json = JSONObject(file.readText())
                    SavedPlanInfo(
                        name = json.getString("name"),
                        startAddress = json.getString("startAddress"),
                        endAddress = json.getString("endAddress"),
                        repeaterCount = json.getJSONArray("repeaters").length(),
                        timestamp = json.getLong("timestamp"),
                        fileName = file.name
                    )
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    /**
     * Load a saved route plan by file name.
     */
    fun loadPlan(fileName: String): RoutePlan? {
        return try {
            val json = JSONObject(File(savedRoutesDir, fileName).readText())
            val repeaters = mutableListOf<RouteRepeater>()
            val array = json.getJSONArray("repeaters")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                repeaters.add(
                    RouteRepeater(
                        repeater = GmrsRepeater(
                            id = "${obj.getString("callsign")}_${obj.getDouble("outputFrequency")}",
                            callsign = obj.getString("callsign"),
                            outputFrequency = obj.getDouble("outputFrequency"),
                            inputFrequency = obj.getDouble("inputFrequency"),
                            ctcssTone = obj.optDouble("ctcssTone", 0.0),
                            dcsCode = obj.optInt("dcsCode", 0),
                            latitude = obj.getDouble("latitude"),
                            longitude = obj.getDouble("longitude"),
                            city = obj.optString("city", ""),
                            state = obj.optString("state", ""),
                            county = obj.optString("county", ""),
                            description = "",
                            status = RepeaterStatus.ON_AIR,
                            gmrsChannel = obj.optString("gmrsChannel", "")
                        ),
                        routeDistanceMiles = obj.getDouble("routeDistance"),
                        corridorDistanceMiles = obj.getDouble("corridorDistance")
                    )
                )
            }
            RoutePlan(
                startAddress = json.getString("startAddress"),
                endAddress = json.getString("endAddress"),
                startLat = json.getDouble("startLat"),
                startLon = json.getDouble("startLon"),
                endLat = json.getDouble("endLat"),
                endLon = json.getDouble("endLon"),
                totalDistanceMiles = haversineDistance(
                    json.getDouble("startLat"), json.getDouble("startLon"),
                    json.getDouble("endLat"), json.getDouble("endLon")
                ),
                corridorMiles = json.getInt("corridorMiles"),
                repeaters = repeaters,
                coverageGaps = emptyList(), // Gaps not re-computed from saved data
                waypoints = emptyList(),
                timestamp = json.getLong("timestamp")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load route plan", e)
            null
        }
    }

    /**
     * Delete a saved route plan.
     */
    fun deletePlan(fileName: String): Boolean {
        return File(savedRoutesDir, fileName).delete()
    }

    data class SavedPlanInfo(
        val name: String,
        val startAddress: String,
        val endAddress: String,
        val repeaterCount: Int,
        val timestamp: Long,
        val fileName: String
    )
}
