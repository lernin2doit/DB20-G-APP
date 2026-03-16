package com.db20g.controller.protocol

/**
 * Represents a single channel/memory entry on the DB20-G radio.
 * Based on the CHIRP-documented memory layout for the Radioddity GA-510 family.
 */
data class RadioChannel(
    val number: Int,
    var rxFrequency: Double = 0.0,    // in MHz
    var txFrequency: Double = 0.0,    // in MHz
    var rxTone: ToneValue = ToneValue.None,
    var txTone: ToneValue = ToneValue.None,
    var signalCode: Int = 0,
    var pttId: PttIdMode = PttIdMode.OFF,
    var power: PowerLevel = PowerLevel.HIGH,
    var wideband: Boolean = true,     // true=Wide(FM), false=Narrow(NFM)
    var bcl: Boolean = false,         // Busy Channel Lockout
    var scan: Boolean = true,         // Include in scan
    var fhss: Boolean = false,
    var name: String = "",
    var isEmpty: Boolean = true
) {
    val duplexMode: DuplexMode
        get() {
            if (isEmpty) return DuplexMode.SIMPLEX
            val offset = txFrequency - rxFrequency
            return when {
                txFrequency == 0.0 -> DuplexMode.OFF
                offset == 0.0 -> DuplexMode.SIMPLEX
                offset > 0 && offset < 10.0 -> DuplexMode.PLUS
                offset < 0 && offset > -10.0 -> DuplexMode.MINUS
                else -> DuplexMode.SPLIT
            }
        }

    val offsetMHz: Double
        get() = kotlin.math.abs(txFrequency - rxFrequency)

    val formattedRxFreq: String
        get() = "%.4f".format(rxFrequency)

    val formattedTxFreq: String
        get() = "%.4f".format(txFrequency)
}

enum class PowerLevel(val label: String, val watts: Int) {
    HIGH("High", 20),
    MEDIUM("Mid", 10),
    LOW("Low", 1);
}

enum class PttIdMode { OFF, BOT, EOT, BOTH }

enum class DuplexMode(val symbol: String) {
    SIMPLEX(""),
    PLUS("+"),
    MINUS("-"),
    SPLIT("Split"),
    OFF("TX Off")
}

sealed class ToneValue {
    object None : ToneValue() {
        override fun toString() = "None"
    }
    data class CTCSS(val frequency: Double) : ToneValue() {
        override fun toString() = "%.1f Hz".format(frequency)
    }
    data class DCS(val code: Int, val polarity: DcsPolarity) : ToneValue() {
        override fun toString() = "D%03d%s".format(code, polarity.symbol)
    }
}

enum class DcsPolarity(val symbol: String) {
    NORMAL("N"),
    INVERTED("R")
}
