package com.pulsify.music.domain

data class LyricLine(val timeMs: Long, val text: String)
data class ParsedLyrics(val plainText: String, val lines: List<LyricLine>) {
    val synced get() = lines.isNotEmpty()
    fun activeIndex(positionMs: Long, delayMs: Long = 0): Int {
        val position = positionMs - delayMs
        var low = 0; var high = lines.lastIndex; var active = -1
        while(low <= high) {
            val middle = (low + high) ushr 1
            if(lines[middle].timeMs <= position) { active = middle; low = middle + 1 } else high = middle - 1
        }
        return active
    }
    fun seekPosition(index: Int, delayMs: Long = 0) = (lines[index].timeMs + delayMs).coerceAtLeast(0)
}
data class LyricsDocument(val songId: String, val text: String, val delayMs: Long = 0)

private val timestamp = Regex("\\[(?:(\\d{1,3}):)?(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
private val offsetTag = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
private val metadataTag = Regex("^\\[(?:ar|al|ti|au|by|re|ve|length|offset):.*]$", RegexOption.IGNORE_CASE)

/** Standard LRC line timestamps, repeated tags, file offset, and optional hour timestamps. */
fun parseLyrics(source: String): ParsedLyrics {
    val text = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    val fileOffset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-3_600_000,3_600_000) ?: 0
    val timed = sortedMapOf<Long, MutableList<String>>()
    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        val matches = timestamp.findAll(line).toList()
        if(matches.isNotEmpty() && matches.first().range.first == 0) {
            val words = timestamp.replace(line, "").trim()
            matches.forEach { match ->
                val (hours,minutes,seconds,fraction) = match.destructured
                if(seconds.toInt() < 60 && (hours.isEmpty() || minutes.toInt() < 60)) {
                    val millis = fraction.padEnd(3,'0').toLongOrNull() ?: 0
                    val time = (hours.toLongOrNull() ?: 0) * 3_600_000 + minutes.toLong() * 60_000 + seconds.toLong() * 1000 + millis - fileOffset
                    timed.getOrPut(time) { mutableListOf() }.add(words)
                }
            }
        }
    }
    return ParsedLyrics(text.lineSequence().filterNot { metadataTag.matches(it.trim()) }.joinToString("\n").trim(),
        timed.map { (time, words) -> LyricLine(time, words.distinct().joinToString("\n")) })
}

fun isLyricsFilename(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("txt", "lrc")
