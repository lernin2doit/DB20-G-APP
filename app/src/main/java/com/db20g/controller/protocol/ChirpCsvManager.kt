package com.db20g.controller.protocol

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * Import and export CHIRP-compatible CSV files for DB20-G channel data.
 *
 * CHIRP CSV format uses these columns (order may vary):
 * Location, Name, Frequency, Duplex, Offset, Tone, rToneFreq, cToneFreq,
 * DtcsCode, DtcsPolarity, Mode, TStep, Skip, Comment, URCALL, RPT1CALL, RPT2CALL
 *
 * Also supports import of raw .img memory images (binary, same as save/load).
 */
object ChirpCsvManager {

    private const val TAG = "ChirpCsvManager"

    // CHIRP CSV header columns
    private val CHIRP_COLUMNS = listOf(
        "Location", "Name", "Frequency", "Duplex", "Offset", "Tone",
        "rToneFreq", "cToneFreq", "DtcsCode", "DtcsPolarity", "Mode",
        "TStep", "Skip", "Comment", "URCALL", "RPT1CALL", "RPT2CALL"
    )

    // --- Export ---

    /**
     * Export channels to CHIRP-compatible CSV format.
     * Writes to the given output stream.
     */
    fun exportCsv(channels: List<RadioChannel>, output: OutputStream) {
        output.bufferedWriter().use { writer ->
            // Header
            writer.write(CHIRP_COLUMNS.joinToString(","))
            writer.newLine()

            for (ch in channels) {
                if (ch.isEmpty) continue
                writer.write(channelToCsvRow(ch))
                writer.newLine()
            }
        }
    }

    /**
     * Export channels to a CSV string for preview.
     */
    fun exportCsvString(channels: List<RadioChannel>): String {
        val sb = StringBuilder()
        sb.appendLine(CHIRP_COLUMNS.joinToString(","))
        for (ch in channels) {
            if (ch.isEmpty) continue
            sb.appendLine(channelToCsvRow(ch))
        }
        return sb.toString()
    }

    private fun channelToCsvRow(ch: RadioChannel): String {
        val location = ch.number
        val name = csvEscape(ch.name)
        val frequency = "%.6f".format(ch.rxFrequency)

        // Duplex: "", "+", "-", "split", "off"
        val offset = ch.txFrequency - ch.rxFrequency
        val duplex = when {
            kotlin.math.abs(offset) < 0.0001 -> ""
            offset > 0 -> "+"
            else -> "-"
        }
        val offsetStr = "%.6f".format(kotlin.math.abs(offset))

        // Capture tones as local vals for smart cast
        val txTone = ch.txTone
        val rxTone = ch.rxTone

        // Tone mode: "", "Tone", "TSQL", "DTCS", "Cross"
        val toneMode = when {
            txTone is ToneValue.CTCSS && rxTone is ToneValue.CTCSS -> "TSQL"
            txTone is ToneValue.CTCSS -> "Tone"
            txTone is ToneValue.DCS -> "DTCS"
            else -> ""
        }

        val rToneFreq = when (txTone) {
            is ToneValue.CTCSS -> "%.1f".format(txTone.frequency)
            else -> "88.5"  // CHIRP default
        }

        val cToneFreq = when (rxTone) {
            is ToneValue.CTCSS -> "%.1f".format(rxTone.frequency)
            else -> "88.5"
        }

        val dtcsCode = when {
            txTone is ToneValue.DCS -> "%03d".format(txTone.code)
            rxTone is ToneValue.DCS -> "%03d".format(rxTone.code)
            else -> "023"
        }

        val dtcsPolarity = when {
            txTone is ToneValue.DCS -> {
                val txPol = if (txTone.polarity == DcsPolarity.INVERTED) "R" else "N"
                val rxPol = if (rxTone is ToneValue.DCS && rxTone.polarity == DcsPolarity.INVERTED) "R" else "N"
                "${txPol}${rxPol}"
            }
            else -> "NN"
        }

        val mode = if (ch.wideband) "FM" else "NFM"
        val tStep = "5.00"
        val skip = if (!ch.scan) "S" else ""
        val comment = ""

        return listOf(
            location, name, frequency, duplex, offsetStr, toneMode,
            rToneFreq, cToneFreq, dtcsCode, dtcsPolarity, mode,
            tStep, skip, comment, "", "", ""
        ).joinToString(",")
    }

    // --- Import ---

    /**
     * Import channels from a CHIRP CSV file.
     * Returns a list of parsed channels (non-empty ones only).
     */
    fun importCsv(input: InputStream): ImportResult {
        val reader = BufferedReader(InputStreamReader(input))
        val headerLine = reader.readLine()
            ?: return ImportResult(emptyList(), listOf("Empty file"))

        val headers = parseCsvLine(headerLine)
        val columnMap = headers.withIndex().associate { (i, name) -> name.trim() to i }

        // Validate required columns
        val required = listOf("Location", "Frequency")
        val missing = required.filter { it !in columnMap }
        if (missing.isNotEmpty()) {
            return ImportResult(emptyList(), listOf("Missing required columns: ${missing.joinToString()}"))
        }

        val channels = mutableListOf<RadioChannel>()
        val warnings = mutableListOf<String>()
        var lineNum = 1

        reader.forEachLine { line ->
            lineNum++
            if (line.isBlank()) return@forEachLine

            try {
                val fields = parseCsvLine(line)
                val channel = parseCsvRow(fields, columnMap, lineNum)
                if (channel != null) {
                    channels.add(channel)
                }
            } catch (e: Exception) {
                warnings.add("Line $lineNum: ${e.message}")
                Log.w(TAG, "CSV parse error at line $lineNum", e)
            }
        }

        return ImportResult(channels, warnings)
    }

    /**
     * Import from a .img binary file. Returns channels parsed from the memory image.
     */
    fun importImg(data: ByteArray, protocol: DB20GProtocol): ImportResult {
        if (data.size < DB20GProtocol.MEM_TOTAL) {
            return ImportResult(emptyList(), listOf("Invalid .img file: too small (${data.size} bytes, expected at least ${DB20GProtocol.MEM_TOTAL})"))
        }

        val channels = protocol.parseChannels(data)
        val activeChannels = channels.filter { !it.isEmpty }
        return ImportResult(activeChannels, emptyList())
    }

    /**
     * Detect file type from content/extension and import accordingly.
     */
    fun detectAndImport(file: File, protocol: DB20GProtocol): ImportResult {
        val extension = file.extension.lowercase()

        return when {
            extension == "csv" || extension == "CSV" -> {
                file.inputStream().use { importCsv(it) }
            }
            extension == "img" || extension == "IMG" -> {
                importImg(file.readBytes(), protocol)
            }
            else -> {
                // Try to detect by content
                val bytes = file.readBytes()
                if (bytes.size >= DB20GProtocol.MEM_TOTAL) {
                    // Likely a binary .img file
                    importImg(bytes, protocol)
                } else {
                    val text = String(bytes)
                    if (text.contains("Location") && text.contains("Frequency")) {
                        bytes.inputStream().use { importCsv(it) }
                    } else {
                        ImportResult(emptyList(), listOf("Unrecognized file format"))
                    }
                }
            }
        }
    }

    private fun parseCsvRow(
        fields: List<String>,
        columnMap: Map<String, Int>,
        lineNum: Int
    ): RadioChannel? {
        fun get(name: String): String = columnMap[name]?.let { fields.getOrNull(it)?.trim() } ?: ""

        val locationStr = get("Location")
        val location = locationStr.toIntOrNull() ?: return null

        val freqStr = get("Frequency")
        val frequency = freqStr.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid frequency: $freqStr")

        if (frequency <= 0.0) return null // Empty slot

        val name = get("Name").take(10)
        val duplex = get("Duplex")
        val offsetStr = get("Offset")
        val offset = offsetStr.toDoubleOrNull() ?: 0.0

        val txFrequency = when (duplex) {
            "+" -> frequency + offset
            "-" -> frequency - offset
            "split" -> offset // In split mode, offset field contains the actual TX freq
            "off" -> frequency // TX off
            else -> frequency
        }

        // Parse tone settings
        val toneMode = get("Tone")
        val rToneFreq = get("rToneFreq").toDoubleOrNull() ?: 0.0
        val cToneFreq = get("cToneFreq").toDoubleOrNull() ?: 0.0
        val dtcsCode = get("DtcsCode").toIntOrNull() ?: 0
        val dtcsPolStr = get("DtcsPolarity")

        val txTone: ToneValue = when (toneMode) {
            "Tone" -> if (rToneFreq > 0) ToneValue.CTCSS(rToneFreq) else ToneValue.None
            "TSQL" -> if (rToneFreq > 0) ToneValue.CTCSS(rToneFreq) else ToneValue.None
            "DTCS" -> {
                val pol = if (dtcsPolStr.firstOrNull() == 'R') DcsPolarity.INVERTED else DcsPolarity.NORMAL
                if (dtcsCode > 0) ToneValue.DCS(dtcsCode, pol) else ToneValue.None
            }
            "Cross" -> {
                // Cross mode: TX tone -> RX tone, parse the CrossMode field if present
                val crossMode = get("CrossMode") // e.g. "Tone->Tone", "Tone->DTCS", "DTCS->Tone"
                val parts = crossMode.split("->")
                when (parts.firstOrNull()?.trim()) {
                    "Tone" -> if (rToneFreq > 0) ToneValue.CTCSS(rToneFreq) else ToneValue.None
                    "DTCS" -> {
                        val pol = if (dtcsPolStr.firstOrNull() == 'R') DcsPolarity.INVERTED else DcsPolarity.NORMAL
                        if (dtcsCode > 0) ToneValue.DCS(dtcsCode, pol) else ToneValue.None
                    }
                    else -> ToneValue.None
                }
            }
            else -> ToneValue.None
        }

        val rxTone: ToneValue = when (toneMode) {
            "TSQL" -> if (cToneFreq > 0) ToneValue.CTCSS(cToneFreq) else ToneValue.None
            "DTCS" -> {
                val pol = if (dtcsPolStr.getOrNull(1) == 'R') DcsPolarity.INVERTED else DcsPolarity.NORMAL
                if (dtcsCode > 0) ToneValue.DCS(dtcsCode, pol) else ToneValue.None
            }
            "Cross" -> {
                val crossMode = get("CrossMode")
                val parts = crossMode.split("->")
                val rxDtcsCode = get("RxDtcsCode").toIntOrNull() ?: dtcsCode
                when (parts.getOrNull(1)?.trim()) {
                    "Tone" -> if (cToneFreq > 0) ToneValue.CTCSS(cToneFreq) else ToneValue.None
                    "DTCS" -> {
                        val pol = if (dtcsPolStr.getOrNull(1) == 'R') DcsPolarity.INVERTED else DcsPolarity.NORMAL
                        if (rxDtcsCode > 0) ToneValue.DCS(rxDtcsCode, pol) else ToneValue.None
                    }
                    else -> ToneValue.None
                }
            }
            else -> ToneValue.None
        }

        val mode = get("Mode")
        val wideband = mode != "NFM"

        val skip = get("Skip")
        val scan = skip != "S"

        val power = PowerLevel.HIGH // CHIRP CSV doesn't always include power for GMRS

        return RadioChannel(
            number = location,
            name = name,
            rxFrequency = frequency,
            txFrequency = txFrequency,
            txTone = txTone,
            rxTone = rxTone,
            power = power,
            wideband = wideband,
            scan = scan,
            isEmpty = false
        )
    }

    /**
     * Parse a CSV line handling quoted fields.
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        fields.add(current.toString())
        return fields
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    // --- Data classes ---

    data class ImportResult(
        val channels: List<RadioChannel>,
        val warnings: List<String>
    ) {
        val success: Boolean get() = channels.isNotEmpty()
        val summary: String
            get() = buildString {
                append("${channels.size} channel(s) imported")
                if (warnings.isNotEmpty()) {
                    append(", ${warnings.size} warning(s)")
                }
            }
    }
}
