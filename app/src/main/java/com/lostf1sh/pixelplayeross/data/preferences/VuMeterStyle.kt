package com.lostf1sh.pixelplayeross.data.preferences

object VuMeterStyle {
    const val SEGMENTS = "segments"
    const val BARS = "bars"
    const val NEEDLES = "needles"
    const val OFF = "off"
    fun sanitize(value: String?): String = when (value) {
        BARS, NEEDLES, OFF -> value
        else -> SEGMENTS
    }
}
