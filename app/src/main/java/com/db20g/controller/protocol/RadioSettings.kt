package com.db20g.controller.protocol

/**
 * Radio-wide settings for the DB20-G.
 */
data class RadioSettings(
    var squelch: Int = 3,              // 0-9
    var saveMode: SaveMode = SaveMode.OFF,
    var vox: Int = 0,                  // 0=off, 1-10
    var backlight: Int = 5,            // 0=off, 1-10
    var tdr: Boolean = false,          // Dual watch
    var timeoutTimer: Int = 3,         // index -> n*15 seconds, 0=off
    var beep: Boolean = true,
    var voice: Boolean = true,
    var language: Language = Language.ENGLISH,
    var dtmfSideTone: DtmfSideTone = DtmfSideTone.OFF,
    var scanMode: ScanMode = ScanMode.TIME_OPERATED,
    var pttId: PttIdMode = PttIdMode.OFF,
    var pttDelay: Int = 0,             // 0-30
    var channelADisplay: ChannelDisplay = ChannelDisplay.CH_NAME,
    var channelBDisplay: ChannelDisplay = ChannelDisplay.CH_NAME,
    var bcl: Boolean = false,
    var autoLock: Boolean = false,
    var alarmMode: AlarmMode = AlarmMode.SITE,
    var alarmSound: Boolean = true,
    var txUnderTdr: TxUnderTdr = TxUnderTdr.OFF,
    var tailNoiseClear: Boolean = false,
    var rptNoiseClear: Int = 0,        // 0=off, 100-1000 ms
    var rptNoiseDetect: Int = 0,
    var roger: Boolean = false,
    var fmRadioDisabled: Boolean = false,
    var workMode: WorkMode = WorkMode.CHANNEL,
    var keyLock: Boolean = false
)

enum class SaveMode { OFF, MODE1, MODE2, MODE3 }
enum class Language { ENGLISH, CHINESE }
enum class DtmfSideTone { OFF, KB_SIDE_TONE, ANI_SIDE_TONE, BOTH, KB_ANI }
enum class ScanMode { TIME_OPERATED, CARRIER_OPERATED, SEARCH }
enum class ChannelDisplay { CH_NAME, CH_FREQ }
enum class AlarmMode { SITE, TONE, CODE }
enum class TxUnderTdr { OFF, BAND_A, BAND_B }
enum class WorkMode { VFO, CHANNEL }
