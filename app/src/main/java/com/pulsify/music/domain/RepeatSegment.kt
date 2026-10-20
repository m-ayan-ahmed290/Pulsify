package com.pulsify.music.domain

/** Loop points are valid only within the current track, with a one-second minimum. */
data class RepeatSegment(val start: Long = -1, val end: Long = -1) {
    val active: Boolean get() = start >= 0 && end >= start + 1000
    fun mark(position: Long, duration: Long): RepeatSegment {
        val point = position.coerceIn(0, duration.coerceAtLeast(0))
        return when { start < 0 -> RepeatSegment(point); end < 0 && point >= start + 1000 -> copy(end = point); else -> RepeatSegment(point) }
    }
    fun shouldSeek(position: Long) = active && position >= end
}
