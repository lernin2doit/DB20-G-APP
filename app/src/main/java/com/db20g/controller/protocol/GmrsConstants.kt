package com.db20g.controller.protocol

/**
 * GMRS standard frequency definitions and CTCSS/DCS code tables.
 */
object GmrsConstants {

    data class GmrsChannelDef(
        val number: Int,
        val rxFreqMHz: Double,
        val txFreqMHz: Double = rxFreqMHz,
        val maxPowerW: Double = 50.0
    )

    /** Standard GMRS channels */
    val GMRS_CHANNELS = listOf(
        GmrsChannelDef(1, 462.5625),
        GmrsChannelDef(2, 462.5875),
        GmrsChannelDef(3, 462.6125),
        GmrsChannelDef(4, 462.6375),
        GmrsChannelDef(5, 462.6625),
        GmrsChannelDef(6, 462.6875),
        GmrsChannelDef(7, 462.7125),
        GmrsChannelDef(8, 467.5625, maxPowerW = 0.5),  // Interstitial
        GmrsChannelDef(9, 467.5875, maxPowerW = 0.5),
        GmrsChannelDef(10, 467.6125, maxPowerW = 0.5),
        GmrsChannelDef(11, 467.6375, maxPowerW = 0.5),
        GmrsChannelDef(12, 467.6625, maxPowerW = 0.5),
        GmrsChannelDef(13, 467.6875, maxPowerW = 0.5),
        GmrsChannelDef(14, 467.7125, maxPowerW = 0.5),
        GmrsChannelDef(15, 462.5500, 467.5500),  // Repeater capable
        GmrsChannelDef(16, 462.5750, 467.5750),
        GmrsChannelDef(17, 462.6000, 467.6000),
        GmrsChannelDef(18, 462.6250, 467.6250),
        GmrsChannelDef(19, 462.6500, 467.6500),
        GmrsChannelDef(20, 462.6750, 467.6750),
        GmrsChannelDef(21, 462.7000, 467.7000),
        GmrsChannelDef(22, 462.7250, 467.7250)
    )

    /** Interstitial channels (low power, handheld only, 0.5W max FCC) */
    val INTERSTITIAL_CHANNELS = setOf(8, 9, 10, 11, 12, 13, 14)

    /** Standard CTCSS tones */
    val CTCSS_TONES = doubleArrayOf(
        67.0, 69.3, 71.9, 74.4, 77.0, 79.7, 82.5, 85.4, 88.5, 91.5,
        94.8, 97.4, 100.0, 103.5, 107.2, 110.9, 114.8, 118.8, 123.0, 127.3,
        131.8, 136.5, 141.3, 146.2, 151.4, 156.7, 159.8, 162.2, 165.5, 167.9,
        171.3, 173.8, 177.3, 179.9, 183.5, 186.2, 189.9, 192.8, 196.6, 199.5,
        203.5, 206.5, 210.7, 218.1, 225.7, 229.1, 233.6, 241.8, 250.3, 254.1
    )

    /** Standard DCS codes */
    val DCS_CODES = intArrayOf(
        23, 25, 26, 31, 32, 36, 43, 47, 51, 53, 54, 65, 71, 72, 73, 74,
        114, 115, 116, 122, 125, 131, 132, 134, 143, 145, 152, 155, 156,
        162, 165, 172, 174, 205, 212, 223, 225, 226, 243, 244, 245, 246,
        251, 252, 255, 261, 263, 265, 266, 271, 274, 306, 311, 315, 325,
        331, 332, 343, 346, 351, 356, 364, 365, 371, 411, 412, 413, 423,
        431, 432, 445, 446, 452, 454, 455, 462, 464, 465, 466, 503, 506,
        516, 523, 526, 532, 546, 565, 606, 612, 624, 627, 631, 632, 654,
        662, 664, 703, 712, 723, 731, 732, 734, 743, 754,
        // DB20-G family also supports 645
        645
    )
}
