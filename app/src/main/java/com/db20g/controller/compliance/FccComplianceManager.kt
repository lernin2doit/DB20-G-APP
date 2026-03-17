package com.db20g.controller.compliance

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * FCC compliance engine for GMRS operations.
 *
 * Features:
 * - FCC ULS license lookup & validation
 * - Family member authorization tracking (GMRS covers household)
 * - Power limit enforcement by channel
 * - Callsign ID compliance monitoring
 * - Part 95 quick-reference
 * - License renewal reminders
 * - Real-time violation alerts
 */
class FccComplianceManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fcc_compliance", Context.MODE_PRIVATE)

    private var listener: ComplianceListener? = null

    // --- GMRS Channel Power Limits (Part 95 Subpart E) ---

    data class GmrsChannel(
        val number: Int,
        val frequency: Double,     // MHz
        val maxPowerWatts: Double,
        val bandwidthKhz: Double,
        val repeaterInput: Boolean,
        val notes: String
    )

    val gmrsChannels: List<GmrsChannel> = listOf(
        // Channels 1-7: FRS/GMRS shared, 5W ERP max for GMRS licensees (462 MHz simplex)
        GmrsChannel(1, 462.5625, 5.0, 20.0, false, "FRS/GMRS shared"),
        GmrsChannel(2, 462.5875, 5.0, 20.0, false, "FRS/GMRS shared"),
        GmrsChannel(3, 462.6125, 5.0, 20.0, false, "FRS/GMRS shared"),
        GmrsChannel(4, 462.6375, 5.0, 20.0, false, "FRS/GMRS shared"),
        GmrsChannel(5, 462.6625, 5.0, 20.0, false, "FRS/GMRS shared"),
        GmrsChannel(6, 462.6875, 5.0, 20.0, false, "FRS/GMRS shared"),
        GmrsChannel(7, 462.7125, 5.0, 20.0, false, "FRS/GMRS shared"),
        // Channels 8-14: FRS-only, 0.5W max (462 MHz, 12.5 kHz)
        GmrsChannel(8, 467.5625, 0.5, 12.5, false, "FRS interstitial — 0.5W max"),
        GmrsChannel(9, 467.5875, 0.5, 12.5, false, "FRS interstitial — 0.5W max"),
        GmrsChannel(10, 467.6125, 0.5, 12.5, false, "FRS interstitial — 0.5W max"),
        GmrsChannel(11, 467.6375, 0.5, 12.5, false, "FRS interstitial — 0.5W max"),
        GmrsChannel(12, 467.6625, 0.5, 12.5, false, "FRS interstitial — 0.5W max"),
        GmrsChannel(13, 467.6875, 0.5, 12.5, false, "FRS interstitial — 0.5W max"),
        GmrsChannel(14, 467.7125, 0.5, 12.5, false, "FRS interstitial — 0.5W max"),
        // Channels 15-22: GMRS only, 50W max (462 MHz simplex)
        GmrsChannel(15, 462.5500, 50.0, 20.0, false, "GMRS simplex — 50W max"),
        GmrsChannel(16, 462.5750, 50.0, 20.0, false, "GMRS simplex — 50W max"),
        GmrsChannel(17, 462.6000, 50.0, 20.0, false, "GMRS simplex — 50W max"),
        GmrsChannel(18, 462.6250, 50.0, 20.0, false, "GMRS simplex — 50W max"),
        GmrsChannel(19, 462.6500, 50.0, 20.0, false, "GMRS simplex — 50W max"),
        GmrsChannel(20, 462.6750, 50.0, 20.0, false, "GMRS simplex — 50W max"),
        GmrsChannel(21, 462.7000, 50.0, 20.0, false, "GMRS simplex — 50W max"),
        GmrsChannel(22, 462.7250, 50.0, 20.0, false, "GMRS simplex — 50W max"),
        // Repeater input channels (467 MHz)
        GmrsChannel(15, 467.5500, 50.0, 20.0, true, "Repeater input for CH 15"),
        GmrsChannel(16, 467.5750, 50.0, 20.0, true, "Repeater input for CH 16"),
        GmrsChannel(17, 467.6000, 50.0, 20.0, true, "Repeater input for CH 17"),
        GmrsChannel(18, 467.6250, 50.0, 20.0, true, "Repeater input for CH 18"),
        GmrsChannel(19, 467.6500, 50.0, 20.0, true, "Repeater input for CH 19"),
        GmrsChannel(20, 467.6750, 50.0, 20.0, true, "Repeater input for CH 20"),
        GmrsChannel(21, 467.7000, 50.0, 20.0, true, "Repeater input for CH 21"),
        GmrsChannel(22, 467.7250, 50.0, 20.0, true, "Repeater input for CH 22")
    )

    // --- License Data ---

    data class LicenseInfo(
        val callsign: String,
        val name: String,
        val status: String, // "Active", "Expired", "Cancelled"
        val grantDate: String,
        val expireDate: String,
        val frn: String,
        val serviceCode: String
    )

    data class FamilyMember(
        val name: String,
        val relationship: String,
        val addedDate: Long
    )

    data class Violation(
        val type: ViolationType,
        val channel: Int,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class ViolationType {
        EXCESSIVE_POWER,
        FRS_ONLY_CHANNEL,
        NO_CALLSIGN_ID,
        EXPIRED_LICENSE,
        NARROW_BAND_REQUIRED
    }

    interface ComplianceListener {
        fun onLicenseLoaded(license: LicenseInfo)
        fun onLicenseLookupFailed(error: String)
        fun onViolationDetected(violation: Violation)
        fun onComplianceStatusChanged(isCompliant: Boolean)
    }

    fun setListener(l: ComplianceListener) { listener = l }

    // --- FCC ULS License Lookup ---

    /**
     * Look up a GMRS license by callsign via the FCC ULS API.
     */
    suspend fun lookupLicense(callsign: String): LicenseInfo? = withContext(Dispatchers.IO) {
        try {
            val cleanCallsign = callsign.trim().uppercase(Locale.US)
            val apiUrl = "https://data.fcc.gov/api/license-view/basicSearch/getLicenses" +
                "?searchValue=$cleanCallsign&format=json"
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "DB20-G-Controller/1.0")

            if (connection.responseCode != 200) {
                listener?.onLicenseLookupFailed("HTTP ${connection.responseCode}")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val json = JSONObject(response)
            val searchResult = json.optJSONObject("SearchResult") ?: run {
                listener?.onLicenseLookupFailed("Invalid response format")
                return@withContext null
            }

            val licenses = searchResult.optJSONObject("Licenses")
                ?.optJSONArray("License")
                ?: run {
                    listener?.onLicenseLookupFailed("No licenses found for $cleanCallsign")
                    return@withContext null
                }

            // Find GMRS license (serviceCode = "ZA" for GMRS)
            for (i in 0 until licenses.length()) {
                val lic = licenses.getJSONObject(i)
                val serviceCode = lic.optString("serviceCode", "")
                if (serviceCode == "ZA" || lic.optString("callSign", "") == cleanCallsign) {
                    val info = LicenseInfo(
                        callsign = lic.optString("callSign", cleanCallsign),
                        name = lic.optString("licName", "Unknown"),
                        status = lic.optString("statusDesc", "Unknown"),
                        grantDate = lic.optString("grantDate", ""),
                        expireDate = lic.optString("expiredDate", ""),
                        frn = lic.optString("frn", ""),
                        serviceCode = serviceCode
                    )

                    // Cache license data
                    saveLicenseToPrefs(info)
                    listener?.onLicenseLoaded(info)
                    return@withContext info
                }
            }

            listener?.onLicenseLookupFailed("No GMRS license found for $cleanCallsign")
            null
        } catch (e: Exception) {
            listener?.onLicenseLookupFailed("Network error: ${e.message}")
            null
        }
    }

    private fun saveLicenseToPrefs(info: LicenseInfo) {
        prefs.edit()
            .putString("license_callsign", info.callsign)
            .putString("license_name", info.name)
            .putString("license_status", info.status)
            .putString("license_grant_date", info.grantDate)
            .putString("license_expire_date", info.expireDate)
            .putString("license_frn", info.frn)
            .putString("license_service_code", info.serviceCode)
            .apply()
    }

    fun getCachedLicense(): LicenseInfo? {
        val callsign = prefs.getString("license_callsign", null) ?: return null
        return LicenseInfo(
            callsign = callsign,
            name = prefs.getString("license_name", "") ?: "",
            status = prefs.getString("license_status", "") ?: "",
            grantDate = prefs.getString("license_grant_date", "") ?: "",
            expireDate = prefs.getString("license_expire_date", "") ?: "",
            frn = prefs.getString("license_frn", "") ?: "",
            serviceCode = prefs.getString("license_service_code", "") ?: ""
        )
    }

    // --- License Expiration & Renewal ---

    /**
     * Returns days until license expiration, or -1 if unknown.
     */
    fun daysUntilExpiration(): Int {
        val expireStr = prefs.getString("license_expire_date", null) ?: return -1
        return try {
            val sdf = SimpleDateFormat("MM/dd/yyyy", Locale.US)
            val expireDate = sdf.parse(expireStr) ?: return -1
            val now = Calendar.getInstance().time
            val diffMs = expireDate.time - now.time
            (diffMs / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            -1
        }
    }

    fun isLicenseExpired(): Boolean {
        val days = daysUntilExpiration()
        return days != -1 && days <= 0
    }

    fun needsRenewalSoon(): Boolean {
        val days = daysUntilExpiration()
        return days in 1..90
    }

    // --- Family Member Tracking ---

    fun addFamilyMember(name: String, relationship: String) {
        val members = getFamilyMembers().toMutableList()
        members.add(FamilyMember(name, relationship, System.currentTimeMillis()))
        saveFamilyMembers(members)
    }

    fun removeFamilyMember(name: String) {
        val members = getFamilyMembers().filter { it.name != name }
        saveFamilyMembers(members)
    }

    fun getFamilyMembers(): List<FamilyMember> {
        val json = prefs.getString("family_members", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                FamilyMember(
                    name = obj.getString("name"),
                    relationship = obj.getString("relationship"),
                    addedDate = obj.getLong("addedDate")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFamilyMembers(members: List<FamilyMember>) {
        val arr = org.json.JSONArray()
        for (m in members) {
            val obj = JSONObject()
            obj.put("name", m.name)
            obj.put("relationship", m.relationship)
            obj.put("addedDate", m.addedDate)
            arr.put(obj)
        }
        prefs.edit().putString("family_members", arr.toString()).apply()
    }

    // --- Power Limit Enforcement ---

    /**
     * Check if the given power on a channel is within legal limits.
     * Returns a Violation if out of compliance, null if OK.
     */
    fun checkPowerCompliance(channelNumber: Int, powerWatts: Double): Violation? {
        val channel = gmrsChannels.firstOrNull {
            it.number == channelNumber && !it.repeaterInput
        } ?: return null

        // FRS-only channels (8-14) — check BEFORE generic power limit
        if (channelNumber in 8..14 && powerWatts > 0.5) {
            val v = Violation(
                type = ViolationType.FRS_ONLY_CHANNEL,
                channel = channelNumber,
                message = "CH $channelNumber is FRS interstitial — max 0.5W, narrowband only"
            )
            listener?.onViolationDetected(v)
            return v
        }

        if (powerWatts > channel.maxPowerWatts) {
            val v = Violation(
                type = ViolationType.EXCESSIVE_POWER,
                channel = channelNumber,
                message = "CH $channelNumber: ${powerWatts}W exceeds ${channel.maxPowerWatts}W limit"
            )
            listener?.onViolationDetected(v)
            return v
        }

        return null
    }

    // --- Callsign ID Compliance ---

    /**
     * Track last callsign ID time. GMRS requires ID at beginning and end of
     * communication, and at least every 15 minutes during.
     */
    fun recordCallsignId() {
        prefs.edit().putLong("last_callsign_id", System.currentTimeMillis()).apply()
    }

    fun getLastCallsignIdTime(): Long = prefs.getLong("last_callsign_id", 0L)

    /**
     * Check if callsign ID is due — returns true if >15 min since last ID.
     */
    fun isCallsignIdDue(): Boolean {
        val lastId = getLastCallsignIdTime()
        if (lastId == 0L) return true
        val elapsed = System.currentTimeMillis() - lastId
        return elapsed > 15 * 60 * 1000  // 15 minutes
    }

    fun getCallsignIdRemainingSeconds(): Int {
        val lastId = getLastCallsignIdTime()
        if (lastId == 0L) return 0
        val elapsed = System.currentTimeMillis() - lastId
        val remaining = (15 * 60 * 1000) - elapsed
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }

    // --- FCC Part 95 Quick Reference ---

    data class Part95Rule(
        val section: String,
        val title: String,
        val summary: String,
        val category: String
    )

    val part95Reference: List<Part95Rule> = listOf(
        Part95Rule("95.1705", "Scope",
            "GMRS is authorized under Part 95 Subpart E. Available to individuals (not businesses) for personal, family, or household use.",
            "General"),
        Part95Rule("95.1705(a)", "License Required",
            "A GMRS license is required to transmit. License covers the licensee and their immediate family members (spouse, children, parents, siblings living in the same household).",
            "Licensing"),
        Part95Rule("95.1731", "Permissible Communications",
            "GMRS may be used for short-distance two-way voice communications. One-way transmissions are permitted for brief tests, emergency, and traveler assistance.",
            "Operations"),
        Part95Rule("95.1733", "Prohibited Communications",
            "No business communications, no transmissions for illegal purposes, no music/entertainment broadcasts, no advertisements.",
            "Operations"),
        Part95Rule("95.1751", "Station Identification",
            "Must transmit callsign at the beginning and end of each communication, and at intervals not exceeding 15 minutes. Use spoken callsign in English.",
            "Operations"),
        Part95Rule("95.1761", "Transmitter Power",
            "CH 1-7: 5W max ERP. CH 8-14: 0.5W ERP (FRS). CH 15-22: 50W max ERP. Repeater output: 50W max ERP.",
            "Technical"),
        Part95Rule("95.1763", "Antennas",
            "GMRS stations may use fixed, mobile, or portable antennas. Antenna height for fixed stations limited to 20 feet above existing structure or 60 feet above ground.",
            "Technical"),
        Part95Rule("95.1767", "Bandwidth",
            "CH 1-7, 15-22: 20 kHz bandwidth. CH 8-14: 12.5 kHz bandwidth (narrowband).",
            "Technical"),
        Part95Rule("95.1771", "Repeater Operations",
            "GMRS licensees may operate repeaters on CH 15R-22R (467 MHz input / 462 MHz output). Must follow coordination guidelines.",
            "Repeaters"),
        Part95Rule("95.1775", "Interconnection",
            "GMRS stations may be interconnected to the public switched telephone network (phone patches).",
            "Operations"),
        Part95Rule("95.305", "License Term",
            "GMRS licenses are issued for 10-year terms. Renewal must be filed before expiration.",
            "Licensing"),
        Part95Rule("95.335", "License Fees",
            "GMRS license fee: $35 (as of 2023). Covers entire family/household.",
            "Licensing")
    )

    fun getRulesByCategory(category: String): List<Part95Rule> {
        return part95Reference.filter { it.category == category }
    }

    fun searchRules(query: String): List<Part95Rule> {
        val lq = query.lowercase(Locale.US)
        return part95Reference.filter {
            it.title.lowercase(Locale.US).contains(lq) ||
                it.summary.lowercase(Locale.US).contains(lq) ||
                it.section.lowercase(Locale.US).contains(lq)
        }
    }

    // --- Violation Log ---

    private val violationLog = mutableListOf<Violation>()

    fun getViolationLog(): List<Violation> = violationLog.toList()

    fun logViolation(violation: Violation) {
        violationLog.add(violation)
        listener?.onViolationDetected(violation)

        // Persist recent violations
        val arr = org.json.JSONArray()
        for (v in violationLog.takeLast(100)) {
            val obj = JSONObject()
            obj.put("type", v.type.name)
            obj.put("channel", v.channel)
            obj.put("message", v.message)
            obj.put("timestamp", v.timestamp)
            arr.put(obj)
        }
        prefs.edit().putString("violation_log", arr.toString()).apply()
    }

    fun loadViolationLog() {
        val json = prefs.getString("violation_log", null) ?: return
        try {
            val arr = org.json.JSONArray(json)
            violationLog.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                violationLog.add(Violation(
                    type = ViolationType.valueOf(obj.getString("type")),
                    channel = obj.getInt("channel"),
                    message = obj.getString("message"),
                    timestamp = obj.getLong("timestamp")
                ))
            }
        } catch (e: Exception) {
            // Ignore corrupt data
        }
    }

    fun clearViolationLog() {
        violationLog.clear()
        prefs.edit().remove("violation_log").apply()
    }

    /**
     * Run a full compliance check and return all active issues.
     */
    fun runComplianceAudit(): List<String> {
        val issues = mutableListOf<String>()

        // License check
        val license = getCachedLicense()
        if (license == null) {
            issues.add("No FCC license on file — enter your callsign to validate")
        } else {
            if (license.status != "Active") {
                issues.add("License status: ${license.status}")
            }
            val days = daysUntilExpiration()
            if (days in 1..90) {
                issues.add("License expires in $days days — file for renewal")
            } else if (days <= 0 && days != -1) {
                issues.add("LICENSE EXPIRED — do not transmit until renewed")
            }
        }

        // Callsign ID check
        if (isCallsignIdDue()) {
            issues.add("Callsign ID is due — identify with your callsign")
        }

        val isCompliant = issues.isEmpty()
        listener?.onComplianceStatusChanged(isCompliant)
        return issues
    }
}
