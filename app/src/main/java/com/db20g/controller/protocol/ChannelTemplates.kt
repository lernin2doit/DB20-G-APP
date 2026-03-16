package com.db20g.controller.protocol

import com.db20g.controller.protocol.GmrsConstants.GMRS_CHANNELS

/**
 * Pre-built channel templates for quick radio programming.
 * Each template returns a list of RadioChannels ready to be written to the radio.
 */
object ChannelTemplates {

    data class Template(
        val id: String,
        val name: String,
        val description: String,
        val channelCount: Int,
        val builder: () -> List<RadioChannel>
    )

    val ALL_TEMPLATES = listOf(
        Template(
            id = "gmrs_standard_22",
            name = "GMRS Standard 22",
            description = "All 22 GMRS channels with correct power limits. The starting point for any GMRS user.",
            channelCount = 22,
            builder = ::buildGmrsStandard22
        ),
        Template(
            id = "emergency_kit",
            name = "Emergency Kit",
            description = "Channel 20 (unofficial emergency), FRS interop channels 1-7, and channels 15-22 for repeater access.",
            channelCount = 30,
            builder = ::buildEmergencyKit
        ),
        Template(
            id = "family_pack",
            name = "Family Pack",
            description = "Simple 8-channel setup for family use. FRS-compatible channels 1-7 plus Channel 20 for emergencies.",
            channelCount = 8,
            builder = ::buildFamilyPack
        ),
        Template(
            id = "full_with_repeaters",
            name = "Full GMRS + Repeater Pairs",
            description = "All 22 simplex channels plus repeater input/output pairs for channels 15R-22R. Ready for repeater access.",
            channelCount = 30,
            builder = ::buildFullWithRepeaters
        )
    )

    fun getTemplate(id: String): Template? = ALL_TEMPLATES.find { it.id == id }

    /**
     * Standard 22 GMRS simplex channels with FCC-correct power limits.
     */
    private fun buildGmrsStandard22(): List<RadioChannel> {
        return GMRS_CHANNELS.mapIndexed { index, def ->
            RadioChannel(
                number = index,
                rxFrequency = def.rxFreqMHz,
                txFrequency = def.txFreqMHz,
                power = when {
                    def.maxPowerW <= 0.5 -> PowerLevel.LOW
                    def.maxPowerW <= 5.0 -> PowerLevel.MEDIUM
                    else -> PowerLevel.HIGH
                },
                wideband = def.number !in 8..14,  // Interstitial channels are narrowband
                scan = true,
                name = "GMRS ${def.number}",
                isEmpty = false
            )
        }
    }

    /**
     * Emergency-focused channel set:
     * - Ch 20 (unofficial national emergency/calling) first
     * - FRS interop channels 1-7 (low power, anyone can hear)
     * - Channels 15-22 simplex
     * - Channels 15R-22R repeater pairs
     */
    private fun buildEmergencyKit(): List<RadioChannel> {
        val channels = mutableListOf<RadioChannel>()
        var slot = 0

        // Channel 20 first — unofficial GMRS emergency/calling channel
        val ch20Def = GMRS_CHANNELS.find { it.number == 20 }
        if (ch20Def != null) {
            channels.add(RadioChannel(
                number = slot++,
                rxFrequency = ch20Def.rxFreqMHz,
                txFrequency = ch20Def.txFreqMHz,
                power = PowerLevel.HIGH,
                wideband = true,
                scan = true,
                name = "EMERG CH20",
                isEmpty = false
            ))
        }

        // FRS interop channels 1-7 (low power, compatible with FRS radios)
        for (chNum in 1..7) {
            val def = GMRS_CHANNELS.find { it.number == chNum } ?: continue
            channels.add(RadioChannel(
                number = slot++,
                rxFrequency = def.rxFreqMHz,
                txFrequency = def.txFreqMHz,
                power = PowerLevel.LOW,
                wideband = true,
                scan = true,
                name = "FRS $chNum",
                isEmpty = false
            ))
        }

        // GMRS simplex channels 15-22
        for (chNum in 15..22) {
            val def = GMRS_CHANNELS.find { it.number == chNum } ?: continue
            channels.add(RadioChannel(
                number = slot++,
                rxFrequency = def.rxFreqMHz,
                txFrequency = def.txFreqMHz,
                power = PowerLevel.HIGH,
                wideband = true,
                scan = true,
                name = "GMRS $chNum",
                isEmpty = false
            ))
        }

        // Repeater pairs 15R-22R (5 MHz offset, common CTCSS tones)
        val repeaterChannels = listOf(15, 16, 17, 18, 19, 20, 21, 22)
        for (chNum in repeaterChannels) {
            val def = GMRS_CHANNELS.find { it.number == chNum } ?: continue
            val inputFreq = def.rxFreqMHz + 5.0  // Standard +5 MHz input offset
            channels.add(RadioChannel(
                number = slot++,
                rxFrequency = def.rxFreqMHz,
                txFrequency = inputFreq,
                power = PowerLevel.HIGH,
                wideband = true,
                scan = true,
                name = "${chNum}R RPT",
                isEmpty = false
            ))
        }

        return channels
    }

    /**
     * Simplified 8-channel family setup.
     * FRS-compatible channels 1-7 at low power plus emergency Channel 20.
     */
    private fun buildFamilyPack(): List<RadioChannel> {
        val channels = mutableListOf<RadioChannel>()
        var slot = 0

        // FRS channels 1-7 at low power
        for (chNum in 1..7) {
            val def = GMRS_CHANNELS.find { it.number == chNum } ?: continue
            channels.add(RadioChannel(
                number = slot++,
                rxFrequency = def.rxFreqMHz,
                txFrequency = def.txFreqMHz,
                power = PowerLevel.LOW,
                wideband = true,
                scan = true,
                name = "Family $chNum",
                isEmpty = false
            ))
        }

        // Channel 20 for emergencies
        val ch20Def = GMRS_CHANNELS.find { it.number == 20 }
        if (ch20Def != null) {
            channels.add(RadioChannel(
                number = slot++,
                rxFrequency = ch20Def.rxFreqMHz,
                txFrequency = ch20Def.txFreqMHz,
                power = PowerLevel.HIGH,
                wideband = true,
                scan = true,
                name = "Emergency",
                isEmpty = false
            ))
        }

        return channels
    }

    /**
     * Full GMRS 22 channels + 8 repeater pairs for channels 15R-22R.
     */
    private fun buildFullWithRepeaters(): List<RadioChannel> {
        val channels = mutableListOf<RadioChannel>()
        var slot = 0

        // All 22 simplex channels
        for (def in GMRS_CHANNELS) {
            channels.add(RadioChannel(
                number = slot++,
                rxFrequency = def.rxFreqMHz,
                txFrequency = def.txFreqMHz,
                power = when {
                    def.maxPowerW <= 0.5 -> PowerLevel.LOW
                    def.maxPowerW <= 5.0 -> PowerLevel.MEDIUM
                    else -> PowerLevel.HIGH
                },
                wideband = def.number !in 8..14,
                scan = true,
                name = "GMRS ${def.number}",
                isEmpty = false
            ))
        }

        // Repeater pairs
        for (chNum in 15..22) {
            val def = GMRS_CHANNELS.find { it.number == chNum } ?: continue
            val inputFreq = def.rxFreqMHz + 5.0
            channels.add(RadioChannel(
                number = slot++,
                rxFrequency = def.rxFreqMHz,
                txFrequency = inputFreq,
                power = PowerLevel.HIGH,
                wideband = true,
                scan = true,
                name = "${chNum}R RPT",
                isEmpty = false
            ))
        }

        return channels
    }
}
