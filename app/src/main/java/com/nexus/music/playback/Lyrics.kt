package com.nexus.music.playback

internal fun timedLyrics(content: String): List<Pair<Long, String>> {
    val stamp = Regex("""\[(\d+):(\d{2})(?:[.:](\d{1,3}))?]""")
    val offset = Regex("""(?i)\[offset:([+-]?\d{1,8})]""").find(content)?.groupValues?.get(1)?.toLong() ?: 0L
    return content.lineSequence().flatMap { line ->
        val label = stamp.replace(line, "").trim()
        stamp.findAll(line).mapNotNull { match ->
            val minute = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            if (minute > 10_000) return@mapNotNull null
            val ms = match.groupValues[3].padEnd(3, '0').toLong()
            ((minute * 60 + match.groupValues[2].toLong()) * 1000 + ms - offset).coerceAtLeast(0L) to label
        }
    }.sortedBy { it.first }.toList()
}

internal fun activeLyricIndex(lines: List<Pair<Long, String>>, positionMs: Long): Int {
    var start = 0
    var end = lines.size
    while (start < end) {
        val mid = start + (end - start) / 2
        if (lines[mid].first <= positionMs) start = mid + 1 else end = mid
    }
    return start - 1
}
