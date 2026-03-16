package com.db20g.controller.protocol

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class QsoLogger(private val context: Context) {

    data class QsoRecord(
        val id: String = UUID.randomUUID().toString(),
        val callsign: String,
        val theirCallsign: String,
        val frequency: Double,
        val channel: Int,
        val mode: String = "FM",
        val rstSent: String = "59",
        val rstReceived: String = "59",
        val power: String = "",
        val notes: String = "",
        val startTime: Long = System.currentTimeMillis(),
        val endTime: Long = System.currentTimeMillis(),
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    )

    data class Contact(
        val callsign: String,
        var name: String = "",
        var city: String = "",
        var state: String = "",
        var notes: String = "",
        var totalQsos: Int = 0,
        var lastContactTime: Long = 0,
        var favoriteChannel: Int = -1
    )

    data class NetSchedule(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val frequency: Double,
        val channel: Int,
        val tone: String = "",
        val dayOfWeek: Int, // Calendar.SUNDAY etc.
        val timeUtc: String, // "HH:mm"
        val netControl: String = "",
        val notes: String = "",
        val enabled: Boolean = true
    )

    data class RepeaterReport(
        val repeaterId: String,
        val callsign: String,
        val frequency: Double,
        val status: RepeaterStatus,
        val toneCorrection: String = "",
        val coverageFeedback: String = "",
        val reporterCallsign: String,
        val timestamp: Long = System.currentTimeMillis(),
        val notes: String = ""
    )

    enum class RepeaterStatus { ONLINE, OFFLINE, INTERMITTENT, UNKNOWN }

    private val prefs: SharedPreferences = context.getSharedPreferences("qso_logger", Context.MODE_PRIVATE)
    private val qsoFile get() = File(context.filesDir, "qso_log.json")
    private val contactsFile get() = File(context.filesDir, "contacts.json")
    private val netsFile get() = File(context.filesDir, "net_schedules.json")
    private val reportsFile get() = File(context.filesDir, "repeater_reports.json")

    private val qsoList = mutableListOf<QsoRecord>()
    private val MAX_QSO_ENTRIES = 5000
    private val contactMap = mutableMapOf<String, Contact>()
    private val netSchedules = mutableListOf<NetSchedule>()
    private val repeaterReports = mutableListOf<RepeaterReport>()

    init {
        loadAll()
    }

    // --- QSO Logging ---

    fun logQso(record: QsoRecord) {
        qsoList.add(0, record)
        if (qsoList.size > MAX_QSO_ENTRIES) {
            qsoList.subList(MAX_QSO_ENTRIES, qsoList.size).clear()
        }

        // Update contact database
        val contact = contactMap.getOrPut(record.theirCallsign.uppercase(Locale.US)) {
            Contact(callsign = record.theirCallsign.uppercase(Locale.US))
        }
        contact.totalQsos++
        contact.lastContactTime = record.startTime
        if (contact.favoriteChannel < 0) contact.favoriteChannel = record.channel

        saveQsos()
        saveContacts()
    }

    fun getQsoLog(): List<QsoRecord> = qsoList.toList()

    fun searchQsos(query: String): List<QsoRecord> {
        val q = query.uppercase(Locale.US)
        return qsoList.filter { qso ->
            qso.theirCallsign.uppercase(Locale.US).contains(q) ||
            qso.callsign.uppercase(Locale.US).contains(q) ||
            qso.notes.uppercase(Locale.US).contains(q)
        }
    }

    fun getQsoStats(): Map<String, Any> {
        val totalQsos = qsoList.size
        val uniqueCallsigns = qsoList.map { it.theirCallsign.uppercase(Locale.US) }.toSet().size
        val channelCounts = qsoList.groupBy { it.channel }.mapValues { it.value.size }
        val topChannels = channelCounts.entries.sortedByDescending { it.value }.take(5)
        val callsignCounts = qsoList.groupBy { it.theirCallsign.uppercase(Locale.US) }.mapValues { it.value.size }
        val topContacts = callsignCounts.entries.sortedByDescending { it.value }.take(5)

        return mapOf(
            "totalQsos" to totalQsos,
            "uniqueCallsigns" to uniqueCallsigns,
            "topChannels" to topChannels.map { "CH${it.key + 1}: ${it.value}" },
            "topContacts" to topContacts.map { "${it.key}: ${it.value}" }
        )
    }

    fun deleteQso(id: String) {
        qsoList.removeAll { it.id == id }
        saveQsos()
    }

    // --- Contact Database ---

    fun getContacts(): List<Contact> = contactMap.values.sortedByDescending { it.lastContactTime }

    fun getContact(callsign: String): Contact? = contactMap[callsign.uppercase(Locale.US)]

    fun updateContact(contact: Contact) {
        contactMap[contact.callsign.uppercase(Locale.US)] = contact
        saveContacts()
    }

    fun deleteContact(callsign: String) {
        contactMap.remove(callsign.uppercase(Locale.US))
        saveContacts()
    }

    fun searchContacts(query: String): List<Contact> {
        val q = query.uppercase(Locale.US)
        return contactMap.values.filter { c ->
            c.callsign.uppercase(Locale.US).contains(q) ||
            c.name.uppercase(Locale.US).contains(q) ||
            c.city.uppercase(Locale.US).contains(q)
        }
    }

    // --- Net Schedules ---

    fun getNetSchedules(): List<NetSchedule> = netSchedules.toList()

    fun addNetSchedule(net: NetSchedule) {
        netSchedules.add(net)
        saveNets()
    }

    fun updateNetSchedule(net: NetSchedule) {
        val idx = netSchedules.indexOfFirst { it.id == net.id }
        if (idx >= 0) netSchedules[idx] = net
        saveNets()
    }

    fun deleteNetSchedule(id: String) {
        netSchedules.removeAll { it.id == id }
        saveNets()
    }

    fun getUpcomingNets(): List<NetSchedule> {
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_WEEK)
        return netSchedules.filter { it.enabled && it.dayOfWeek == day }
            .sortedBy { it.timeUtc }
    }

    // --- Repeater Reports ---

    fun submitRepeaterReport(report: RepeaterReport) {
        repeaterReports.add(0, report)
        if (repeaterReports.size > 500) {
            repeaterReports.subList(500, repeaterReports.size).clear()
        }
        saveReports()
    }

    fun getRepeaterReports(callsign: String = ""): List<RepeaterReport> {
        return if (callsign.isEmpty()) repeaterReports.toList()
        else repeaterReports.filter { it.callsign.equals(callsign, ignoreCase = true) }
    }

    // --- Export: ADIF ---

    fun exportAdif(output: OutputStream) {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val stf = SimpleDateFormat("HHmm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        output.bufferedWriter().use { w ->
            w.appendLine("ADIF Export from DB20-G Controller")
            w.appendLine("<adif_ver:5>3.1.4")
            w.appendLine("<programid:14>DB20GController")
            w.appendLine("<EOH>")
            w.appendLine()

            for (qso in qsoList) {
                val dateStr = sdf.format(Date(qso.startTime))
                val timeStr = stf.format(Date(qso.startTime))
                val freqStr = "%.6f".format(qso.frequency)
                val call = qso.theirCallsign

                w.append("<call:${call.length}>$call")
                w.append("<qso_date:${dateStr.length}>$dateStr")
                w.append("<time_on:${timeStr.length}>$timeStr")
                w.append("<freq:${freqStr.length}>$freqStr")
                w.append("<mode:${qso.mode.length}>${qso.mode}")
                w.append("<band:4>70cm") // GMRS is in 70cm range

                if (qso.rstSent.isNotEmpty()) {
                    w.append("<rst_sent:${qso.rstSent.length}>${qso.rstSent}")
                }
                if (qso.rstReceived.isNotEmpty()) {
                    w.append("<rst_rcvd:${qso.rstReceived.length}>${qso.rstReceived}")
                }
                if (qso.notes.isNotEmpty()) {
                    w.append("<comment:${qso.notes.length}>${qso.notes}")
                }
                if (qso.power.isNotEmpty()) {
                    w.append("<tx_pwr:${qso.power.length}>${qso.power}")
                }

                w.appendLine("<EOR>")
            }
        }
    }

    // --- Export: CSV ---

    fun exportCsv(output: OutputStream) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        output.bufferedWriter().use { w ->
            w.appendLine("Date,Time,MyCallsign,TheirCallsign,Frequency,Channel,Mode,RST_Sent,RST_Rcvd,Power,Notes")
            for (qso in qsoList) {
                val dt = sdf.format(Date(qso.startTime))
                val parts = dt.split(" ")
                w.appendLine("${parts[0]},${parts[1]},${qso.callsign},${qso.theirCallsign}," +
                    "%.4f,%d,%s,%s,%s,%s,\"%s\"".format(
                        qso.frequency, qso.channel + 1, qso.mode,
                        qso.rstSent, qso.rstReceived, qso.power,
                        qso.notes.replace("\"", "\"\"")
                    ))
            }
        }
    }

    // --- Share channel config as JSON ---

    fun exportChannelConfigJson(channels: List<RadioChannel>): String {
        val arr = JSONArray()
        for (ch in channels.filter { !it.isEmpty }) {
            val obj = JSONObject().apply {
                put("number", ch.number)
                put("name", ch.name)
                put("rxFrequency", ch.rxFrequency)
                put("txFrequency", ch.txFrequency)
                put("rxTone", ch.rxTone.toString())
                put("txTone", ch.txTone.toString())
                put("power", ch.power)
                put("wideband", ch.wideband)
            }
            arr.put(obj)
        }
        return JSONObject().apply {
            put("app", "DB20-G Controller")
            put("exportDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
            put("channelCount", arr.length())
            put("channels", arr)
        }.toString(2)
    }

    // --- Import net schedules from JSON ---

    fun importNetSchedules(json: String): Int {
        val arr = JSONArray(json)
        var imported = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val net = NetSchedule(
                name = obj.getString("name"),
                frequency = obj.getDouble("frequency"),
                channel = obj.optInt("channel", 0),
                tone = obj.optString("tone", ""),
                dayOfWeek = obj.getInt("dayOfWeek"),
                timeUtc = obj.getString("timeUtc"),
                netControl = obj.optString("netControl", ""),
                notes = obj.optString("notes", "")
            )
            netSchedules.add(net)
            imported++
        }
        saveNets()
        return imported
    }

    fun exportNetSchedulesJson(): String {
        val arr = JSONArray()
        for (net in netSchedules) {
            arr.put(JSONObject().apply {
                put("name", net.name)
                put("frequency", net.frequency)
                put("channel", net.channel)
                put("tone", net.tone)
                put("dayOfWeek", net.dayOfWeek)
                put("timeUtc", net.timeUtc)
                put("netControl", net.netControl)
                put("notes", net.notes)
            })
        }
        return arr.toString(2)
    }

    // --- Persistence ---

    private fun loadAll() {
        loadQsos()
        loadContacts()
        loadNets()
        loadReports()
    }

    private fun loadQsos() {
        if (!qsoFile.exists()) return
        try {
            val arr = JSONArray(qsoFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                qsoList.add(QsoRecord(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    callsign = o.getString("callsign"),
                    theirCallsign = o.getString("theirCallsign"),
                    frequency = o.getDouble("frequency"),
                    channel = o.getInt("channel"),
                    mode = o.optString("mode", "FM"),
                    rstSent = o.optString("rstSent", "59"),
                    rstReceived = o.optString("rstReceived", "59"),
                    power = o.optString("power", ""),
                    notes = o.optString("notes", ""),
                    startTime = o.getLong("startTime"),
                    endTime = o.optLong("endTime", o.getLong("startTime")),
                    latitude = o.optDouble("latitude", 0.0),
                    longitude = o.optDouble("longitude", 0.0)
                ))
            }
        } catch (_: Exception) {}
    }

    private fun saveQsos() {
        val arr = JSONArray()
        for (q in qsoList) {
            arr.put(JSONObject().apply {
                put("id", q.id)
                put("callsign", q.callsign)
                put("theirCallsign", q.theirCallsign)
                put("frequency", q.frequency)
                put("channel", q.channel)
                put("mode", q.mode)
                put("rstSent", q.rstSent)
                put("rstReceived", q.rstReceived)
                put("power", q.power)
                put("notes", q.notes)
                put("startTime", q.startTime)
                put("endTime", q.endTime)
                put("latitude", q.latitude)
                put("longitude", q.longitude)
            })
        }
        qsoFile.writeText(arr.toString())
    }

    private fun loadContacts() {
        if (!contactsFile.exists()) return
        try {
            val arr = JSONArray(contactsFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val cs = o.getString("callsign")
                contactMap[cs] = Contact(
                    callsign = cs,
                    name = o.optString("name", ""),
                    city = o.optString("city", ""),
                    state = o.optString("state", ""),
                    notes = o.optString("notes", ""),
                    totalQsos = o.optInt("totalQsos", 0),
                    lastContactTime = o.optLong("lastContactTime", 0),
                    favoriteChannel = o.optInt("favoriteChannel", -1)
                )
            }
        } catch (_: Exception) {}
    }

    private fun saveContacts() {
        val arr = JSONArray()
        for (c in contactMap.values) {
            arr.put(JSONObject().apply {
                put("callsign", c.callsign)
                put("name", c.name)
                put("city", c.city)
                put("state", c.state)
                put("notes", c.notes)
                put("totalQsos", c.totalQsos)
                put("lastContactTime", c.lastContactTime)
                put("favoriteChannel", c.favoriteChannel)
            })
        }
        contactsFile.writeText(arr.toString())
    }

    private fun loadNets() {
        if (!netsFile.exists()) return
        try {
            val arr = JSONArray(netsFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                netSchedules.add(NetSchedule(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.getString("name"),
                    frequency = o.getDouble("frequency"),
                    channel = o.optInt("channel", 0),
                    tone = o.optString("tone", ""),
                    dayOfWeek = o.getInt("dayOfWeek"),
                    timeUtc = o.getString("timeUtc"),
                    netControl = o.optString("netControl", ""),
                    notes = o.optString("notes", ""),
                    enabled = o.optBoolean("enabled", true)
                ))
            }
        } catch (_: Exception) {}
    }

    private fun saveNets() {
        val arr = JSONArray()
        for (n in netSchedules) {
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("name", n.name)
                put("frequency", n.frequency)
                put("channel", n.channel)
                put("tone", n.tone)
                put("dayOfWeek", n.dayOfWeek)
                put("timeUtc", n.timeUtc)
                put("netControl", n.netControl)
                put("notes", n.notes)
                put("enabled", n.enabled)
            })
        }
        netsFile.writeText(arr.toString())
    }

    private fun loadReports() {
        if (!reportsFile.exists()) return
        try {
            val arr = JSONArray(reportsFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                repeaterReports.add(RepeaterReport(
                    repeaterId = o.optString("repeaterId", ""),
                    callsign = o.getString("callsign"),
                    frequency = o.getDouble("frequency"),
                    status = try { RepeaterStatus.valueOf(o.getString("status")) } catch (_: Exception) { RepeaterStatus.UNKNOWN },
                    toneCorrection = o.optString("toneCorrection", ""),
                    coverageFeedback = o.optString("coverageFeedback", ""),
                    reporterCallsign = o.optString("reporterCallsign", ""),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    notes = o.optString("notes", "")
                ))
            }
        } catch (_: Exception) {}
    }

    private fun saveReports() {
        val arr = JSONArray()
        for (r in repeaterReports) {
            arr.put(JSONObject().apply {
                put("repeaterId", r.repeaterId)
                put("callsign", r.callsign)
                put("frequency", r.frequency)
                put("status", r.status.name)
                put("toneCorrection", r.toneCorrection)
                put("coverageFeedback", r.coverageFeedback)
                put("reporterCallsign", r.reporterCallsign)
                put("timestamp", r.timestamp)
                put("notes", r.notes)
            })
        }
        reportsFile.writeText(arr.toString())
    }
}
