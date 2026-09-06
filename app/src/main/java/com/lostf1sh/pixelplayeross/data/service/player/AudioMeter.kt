package com.lostf1sh.pixelplayeross.data.service.player

import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/** Small, bounded measurement queues, independent of the high-frequency player UI state. */
internal object AudioMeter {
    private val listeners = AtomicInteger()
    private val sources = CopyOnWriteArraySet<Source>()
    val enabled: Boolean get() = listeners.get() > 0
    fun subscribe() { listeners.incrementAndGet() }
    fun unsubscribe() { listeners.decrementAndGet() }
    fun register(source: Source) { sources.add(source) }
    fun unregister(source: Source) { sources.remove(source) }

    data class Reading(val left: Float = 0f, val right: Float = 0f, val available: Boolean = false)

    fun read(): Reading {
        var left = 0f
        var right = 0f
        var available = false
        for (source in sources) {
            val reading = source.read()
            left += reading.left * reading.left
            right += reading.right * reading.right
            available = available || reading.available
        }
        // During crossfade, display combined channel energy, not a fabricated waveform.
        return Reading(sqrt(left).coerceAtMost(1f), sqrt(right).coerceAtMost(1f), available)
    }

    class Source(private val nanoTime: () -> Long = System::nanoTime) {
        private data class Window(val timeUs: Long, val durationUs: Long, val levels: PcmLevelReader.Levels)
        private val windows = ArrayDeque<Window>()
        var playing = false
            @Synchronized set
        var available = false
            @Synchronized set
        var volume = 1f
            @Synchronized set
        var speed = 1f
            @Synchronized set
        private var positionUs = Long.MIN_VALUE
        private var clockNs = 0L

        @Synchronized fun position(positionUs: Long) {
            this.positionUs = positionUs
            clockNs = nanoTime()
        }

        @Synchronized fun add(timeUs: Long, durationUs: Long, levels: PcmLevelReader.Levels) {
            if (windows.size >= 256) windows.removeFirst()
            windows.addLast(Window(timeUs, durationUs, levels))
        }

        @Synchronized fun clear() { windows.clear(); positionUs = Long.MIN_VALUE }

        @Synchronized fun read(): Reading {
            if (!playing || positionUs == Long.MIN_VALUE) return Reading(available = available)
            // The audio sink clock compensates for decoder/AudioTrack buffering and playback speed.
            val nowUs = positionUs + ((nanoTime() - clockNs) / 1000 * speed).toLong()
            while (windows.size > 1 && windows.elementAt(1).timeUs <= nowUs) windows.removeFirst()
            val window = windows.firstOrNull() ?: return Reading(available = available)
            if (nowUs < window.timeUs || nowUs > window.timeUs + window.durationUs + 100_000) {
                return Reading(available = available)
            }
            return Reading(window.levels.left * volume, window.levels.right * volume, available)
        }
    }
}
