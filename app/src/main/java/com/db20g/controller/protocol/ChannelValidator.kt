package com.db20g.controller.protocol

/**
 * Validates RadioChannel configurations against FCC GMRS rules.
 *
 * Produces a list of ValidationIssue items for each channel, classified as
 * ERROR (blocks upload) or WARNING (caution but allowed).
 */
object ChannelValidator {

    /**
     * Validate a single channel. Returns empty list if valid.
     */
    fun validate(channel: RadioChannel): List<ValidationIssue> {
        if (channel.isEmpty) return emptyList()
        val issues = mutableListOf<ValidationIssue>()

        validateFrequency(channel, issues)
        validatePower(channel, issues)
        validateOffset(channel, issues)
        validateTone(channel, issues)
        validateBandwidth(channel, issues)

        return issues
    }

    /**
     * Validate all channels and return a map of channel number → issues.
     * Only includes channels that have at least one issue.
     */
    fun validateAll(channels: List<RadioChannel>): Map<Int, List<ValidationIssue>> {
        val results = mutableMapOf<Int, List<ValidationIssue>>()
        for (ch in channels) {
            val issues = validate(ch)
            if (issues.isNotEmpty()) {
                results[ch.number] = issues
            }
        }
        return results
    }

    /**
     * Check if any channel has a blocking ERROR. Used to gate uploads.
     */
    fun hasBlockingErrors(channels: List<RadioChannel>): Boolean {
        return channels.any { ch ->
            validate(ch).any { it.severity == Severity.ERROR }
        }
    }

    /**
     * Generate a summary report string for all channels.
     */
    fun generateReport(channels: List<RadioChannel>): String {
        val allIssues = validateAll(channels)
        if (allIssues.isEmpty()) return "All channels pass validation."

        val errors = allIssues.values.flatten().count { it.severity == Severity.ERROR }
        val warnings = allIssues.values.flatten().count { it.severity == Severity.WARNING }

        return buildString {
            appendLine("Validation: $errors error(s), $warnings warning(s)")
            appendLine()
            for ((chNum, issues) in allIssues.entries.sortedBy { it.key }) {
                appendLine("Ch ${chNum + 1}:")
                for (issue in issues) {
                    val icon = if (issue.severity == Severity.ERROR) "✗" else "⚠"
                    appendLine("  $icon ${issue.message}")
                }
            }
        }.trimEnd()
    }

    // --- Frequency validation ---

    private fun validateFrequency(channel: RadioChannel, issues: MutableList<ValidationIssue>) {
        val rxFreq = channel.rxFrequency
        val txFreq = channel.txFrequency

        if (rxFreq <= 0 || txFreq <= 0) return // Empty/unset

        // Check RX frequency is within GMRS band
        if (!isGmrsFrequency(rxFreq)) {
            issues.add(ValidationIssue(
                severity = Severity.ERROR,
                field = "rxFrequency",
                message = "RX frequency %.4f MHz is outside the GMRS band".format(rxFreq),
                explanation = "GMRS frequencies are 462.5500-462.7250 MHz and 467.5500-467.7125 MHz. " +
                        "Transmitting on non-GMRS frequencies violates FCC Part 95 rules."
            ))
        }

        if (!isGmrsFrequency(txFreq)) {
            issues.add(ValidationIssue(
                severity = Severity.ERROR,
                field = "txFrequency",
                message = "TX frequency %.4f MHz is outside the GMRS band".format(txFreq),
                explanation = "GMRS frequencies are 462.5500-462.7250 MHz and 467.5500-467.7125 MHz. " +
                        "Transmitting on non-GMRS frequencies violates FCC Part 95 rules."
            ))
        }

        // Check if frequency matches a known GMRS channel
        val matchedChannel = findGmrsChannel(rxFreq, txFreq)
        if (matchedChannel == null && isGmrsFrequency(rxFreq)) {
            issues.add(ValidationIssue(
                severity = Severity.WARNING,
                field = "rxFrequency",
                message = "%.4f MHz does not match a standard GMRS channel".format(rxFreq),
                explanation = "This frequency is within the GMRS band but doesn't match one of the " +
                        "22 standard GMRS channels or repeater pairs. Verify this is intentional."
            ))
        }
    }

    // --- Power validation ---

    private fun validatePower(channel: RadioChannel, issues: MutableList<ValidationIssue>) {
        val matchedChannel = findGmrsChannel(channel.rxFrequency, channel.txFrequency) ?: return

        val maxWatts = matchedChannel.maxPowerW
        val actualWatts = channel.power.watts.toDouble()

        if (actualWatts > maxWatts) {
            val isInterstitial = matchedChannel.number in GmrsConstants.INTERSTITIAL_CHANNELS
            issues.add(ValidationIssue(
                severity = Severity.ERROR,
                field = "power",
                message = "${channel.power.label} power (${channel.power.watts}W) exceeds %.1fW limit for GMRS Ch ${matchedChannel.number}".format(maxWatts),
                explanation = if (isInterstitial) {
                    "Channels 8-14 are shared with FRS and limited to 0.5W (500mW) by FCC Part 95.1767. " +
                            "These are interstitial channels intended for low-power handheld use only."
                } else {
                    "This GMRS channel is limited to %.0fW by FCC Part 95. ".format(maxWatts) +
                            "Reduce power level to comply."
                }
            ))
        }
    }

    // --- Offset / duplex validation ---

    private fun validateOffset(channel: RadioChannel, issues: MutableList<ValidationIssue>) {
        val rxFreq = channel.rxFrequency
        val txFreq = channel.txFrequency
        if (rxFreq <= 0 || txFreq <= 0) return

        val offset = txFreq - rxFreq
        val matchedChannel = findGmrsChannel(rxFreq, txFreq)

        if (matchedChannel != null) {
            val expectedOffset = matchedChannel.txFreqMHz - matchedChannel.rxFreqMHz
            // Repeater channels should have +5 MHz offset
            if (kotlin.math.abs(expectedOffset) > 0.001) {
                // This is a repeater-capable channel
                if (kotlin.math.abs(offset) < 0.001) {
                    // Simplex on a repeater channel is fine
                } else if (kotlin.math.abs(offset - expectedOffset) > 0.001) {
                    issues.add(ValidationIssue(
                        severity = Severity.ERROR,
                        field = "txFrequency",
                        message = "Offset %.3f MHz incorrect for GMRS Ch ${matchedChannel.number} (expected +%.3f MHz)".format(offset, expectedOffset),
                        explanation = "GMRS repeater channels 15-22 use a +5.000 MHz TX offset. " +
                                "The TX frequency should be ${matchedChannel.txFreqMHz} MHz."
                    ))
                }
            } else {
                // Simplex channel — TX and RX should match
                if (kotlin.math.abs(offset) > 0.001) {
                    issues.add(ValidationIssue(
                        severity = Severity.WARNING,
                        field = "txFrequency",
                        message = "Ch ${matchedChannel.number} is simplex but has %.3f MHz TX offset".format(offset),
                        explanation = "GMRS channels 1-14 are simplex-only. TX and RX frequencies should be the same. " +
                                "An offset here may indicate a misconfiguration."
                    ))
                }
            }
        } else if (kotlin.math.abs(offset) > 0.001 && kotlin.math.abs(offset) != 5.0) {
            issues.add(ValidationIssue(
                severity = Severity.WARNING,
                field = "txFrequency",
                message = "Unusual TX offset: %.3f MHz".format(offset),
                explanation = "Standard GMRS repeater offset is +5.000 MHz. " +
                        "This non-standard offset may indicate a programming error."
            ))
        }
    }

    // --- Tone validation ---

    private fun validateTone(channel: RadioChannel, issues: MutableList<ValidationIssue>) {
        when (val tone = channel.txTone) {
            is ToneValue.CTCSS -> {
                if (tone.frequency !in GmrsConstants.CTCSS_TONES.toList()) {
                    issues.add(ValidationIssue(
                        severity = Severity.WARNING,
                        field = "txTone",
                        message = "TX CTCSS tone %.1f Hz is non-standard".format(tone.frequency),
                        explanation = "Standard CTCSS tones range from 67.0 to 254.1 Hz. " +
                                "Non-standard tones may not be recognized by other radios."
                    ))
                }
            }
            is ToneValue.DCS -> {
                if (tone.code !in GmrsConstants.DCS_CODES.toList()) {
                    issues.add(ValidationIssue(
                        severity = Severity.WARNING,
                        field = "txTone",
                        message = "TX DCS code %03d is non-standard".format(tone.code),
                        explanation = "This DCS code is not in the standard set. " +
                                "Non-standard codes may not be recognized by other radios."
                    ))
                }
            }
            is ToneValue.None -> { /* No tone is valid */ }
        }

        when (val tone = channel.rxTone) {
            is ToneValue.CTCSS -> {
                if (tone.frequency !in GmrsConstants.CTCSS_TONES.toList()) {
                    issues.add(ValidationIssue(
                        severity = Severity.WARNING,
                        field = "rxTone",
                        message = "RX CTCSS tone %.1f Hz is non-standard".format(tone.frequency),
                        explanation = "Standard CTCSS tones range from 67.0 to 254.1 Hz."
                    ))
                }
            }
            is ToneValue.DCS -> {
                if (tone.code !in GmrsConstants.DCS_CODES.toList()) {
                    issues.add(ValidationIssue(
                        severity = Severity.WARNING,
                        field = "rxTone",
                        message = "RX DCS code %03d is non-standard".format(tone.code),
                        explanation = "This DCS code is not in the standard set."
                    ))
                }
            }
            is ToneValue.None -> { /* No tone is valid */ }
        }

        // TX/RX tone mismatch for repeater channels
        val matchedChannel = findGmrsChannel(channel.rxFrequency, channel.txFrequency)
        if (matchedChannel != null && kotlin.math.abs(channel.txFrequency - channel.rxFrequency) > 0.001) {
            // This is a repeater setup — tone should be set
            if (channel.txTone is ToneValue.None) {
                issues.add(ValidationIssue(
                    severity = Severity.WARNING,
                    field = "txTone",
                    message = "No TX tone set on repeater channel",
                    explanation = "Most GMRS repeaters require a CTCSS or DCS tone to activate. " +
                            "Without a tone, you may not be able to access the repeater."
                ))
            }
        }
    }

    // --- Bandwidth validation ---

    private fun validateBandwidth(channel: RadioChannel, issues: MutableList<ValidationIssue>) {
        val matchedChannel = findGmrsChannel(channel.rxFrequency, channel.txFrequency) ?: return

        if (matchedChannel.number in GmrsConstants.INTERSTITIAL_CHANNELS && channel.wideband) {
            issues.add(ValidationIssue(
                severity = Severity.WARNING,
                field = "wideband",
                message = "Wideband (25kHz) on interstitial Ch ${matchedChannel.number}",
                explanation = "Channels 8-14 are shared with FRS which uses narrowband (12.5kHz). " +
                        "Using wideband may cause interference with FRS users on adjacent channels."
            ))
        }
    }

    // --- Helpers ---

    private fun isGmrsFrequency(freq: Double): Boolean {
        return GmrsConstants.GMRS_CHANNELS.any { ch ->
            kotlin.math.abs(ch.rxFreqMHz - freq) < 0.0005 ||
                    kotlin.math.abs(ch.txFreqMHz - freq) < 0.0005
        }
    }

    /**
     * Find the matching GMRS channel definition for the given RX/TX pair.
     * Matches simplex (RX=TX on any channel) or repeater (RX=output, TX=input).
     */
    private fun findGmrsChannel(rxFreq: Double, txFreq: Double): GmrsConstants.GmrsChannelDef? {
        for (ch in GmrsConstants.GMRS_CHANNELS) {
            // Simplex match
            if (kotlin.math.abs(ch.rxFreqMHz - rxFreq) < 0.0005 &&
                kotlin.math.abs(ch.rxFreqMHz - txFreq) < 0.0005) {
                return ch
            }
            // Repeater match (RX=repeater output, TX=repeater input)
            if (kotlin.math.abs(ch.rxFreqMHz - rxFreq) < 0.0005 &&
                kotlin.math.abs(ch.txFreqMHz - txFreq) < 0.0005) {
                return ch
            }
        }
        return null
    }
}

// --- Data classes ---

data class ValidationIssue(
    val severity: Severity,
    val field: String,
    val message: String,
    val explanation: String
)

enum class Severity {
    ERROR,   // Blocks upload — likely illegal or clearly wrong
    WARNING  // Caution flag — may be intentional but unusual
}
