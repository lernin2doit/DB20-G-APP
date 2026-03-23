package com.db20g.controller.serial

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Manages the configurable RJ-45 pinout mapping for the radio handset port.
 *
 * The DB20-G RJ-45 pinout has not been verified on actual hardware. This allows
 * users to configure which RJ-45 pin carries which signal, with a default
 * assumed standard pinout. Presets are provided for common configurations.
 */
class PinoutConfig(context: Context) {

    companion object {
        private const val PREFS_NAME = "pinout_config"
        private const val KEY_PINOUT_JSON = "pinout_json"
        private const val KEY_PRESET_NAME = "preset_name"

        const val PRESET_DB20G_DEFAULT = "DB20-G Default"
        const val PRESET_KENWOOD_MOBILE = "Kenwood-Mobile"
        const val PRESET_CUSTOM = "Custom"

        val SIGNAL_NAMES = listOf("VCC", "MIC", "GND", "PTT", "SPK+", "SPK-", "UP", "DOWN")
    }

    enum class Signal {
        VCC, MIC, GND, PTT, SPK_POS, SPK_NEG, UP, DOWN
    }

    data class Pinout(
        val name: String,
        val pins: Map<Int, Signal>
    ) {
        fun signalForPin(pin: Int): Signal? = pins[pin]
        fun pinForSignal(signal: Signal): Int? = pins.entries.find { it.value == signal }?.key

        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("name", name)
            val pinsJson = JSONObject()
            pins.forEach { (pin, signal) -> pinsJson.put(pin.toString(), signal.name) }
            json.put("pins", pinsJson)
            return json
        }

        companion object {
            fun fromJson(json: JSONObject): Pinout {
                val name = json.getString("name")
                val pinsJson = json.getJSONObject("pins")
                val pins = mutableMapOf<Int, Signal>()
                pinsJson.keys().forEach { key ->
                    pins[key.toInt()] = Signal.valueOf(pinsJson.getString(key))
                }
                return Pinout(name, pins)
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The assumed standard DB20-G pinout. */
    val db20gDefault = Pinout(
        name = PRESET_DB20G_DEFAULT,
        pins = mapOf(
            1 to Signal.VCC,
            2 to Signal.MIC,
            3 to Signal.GND,
            4 to Signal.PTT,
            5 to Signal.SPK_POS,
            6 to Signal.SPK_NEG,
            7 to Signal.UP,
            8 to Signal.DOWN
        )
    )

    /** Alternative Kenwood-mobile-compatible pinout. */
    val kenwoodMobile = Pinout(
        name = PRESET_KENWOOD_MOBILE,
        pins = mapOf(
            1 to Signal.MIC,
            2 to Signal.VCC,
            3 to Signal.UP,
            4 to Signal.DOWN,
            5 to Signal.PTT,
            6 to Signal.SPK_POS,
            7 to Signal.SPK_NEG,
            8 to Signal.GND
        )
    )

    val presets: List<Pinout> = listOf(db20gDefault, kenwoodMobile)

    var currentPinout: Pinout
        get() {
            val json = prefs.getString(KEY_PINOUT_JSON, null)
            return if (json != null) {
                try {
                    Pinout.fromJson(JSONObject(json))
                } catch (_: Exception) {
                    db20gDefault
                }
            } else {
                db20gDefault
            }
        }
        set(value) {
            prefs.edit()
                .putString(KEY_PINOUT_JSON, value.toJson().toString())
                .putString(KEY_PRESET_NAME, value.name)
                .apply()
        }

    val currentPresetName: String
        get() = prefs.getString(KEY_PRESET_NAME, PRESET_DB20G_DEFAULT) ?: PRESET_DB20G_DEFAULT

    fun signalDisplayName(signal: Signal): String = when (signal) {
        Signal.VCC -> "+V Supply"
        Signal.MIC -> "Microphone"
        Signal.GND -> "Ground"
        Signal.PTT -> "PTT"
        Signal.SPK_POS -> "Speaker+"
        Signal.SPK_NEG -> "Speaker−"
        Signal.UP -> "UP Button"
        Signal.DOWN -> "DOWN Button"
    }
}
