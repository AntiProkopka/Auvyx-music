package com.nexus.music.playback

import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

internal data class FiniteRepeat(
    val queue: List<String>,
    val index: Int,
    val total: Int,
    val remaining: Int,
    val restoreMode: Int
) {
    val iteration: Int get() = total - remaining + 1
    val playerMode: Int get() = when {
        remaining > 1 -> Player.REPEAT_MODE_ONE
        queue.size == 1 -> Player.REPEAT_MODE_OFF
        else -> restoreMode
    }

    init {
        require(index in queue.indices && total in 2..99 && remaining in 1..total)
        require(restoreMode == Player.REPEAT_MODE_OFF || restoreMode == Player.REPEAT_MODE_ALL)
    }

    fun afterRepeat(): FiniteRepeat? = if (remaining > 1) copy(remaining = remaining - 1) else null

    fun encode(): String = JSONObject().put("ids", JSONArray(queue)).put("index", index)
        .put("total", total).put("remaining", remaining).put("restore", restoreMode).toString()

    companion object {
        fun decode(text: String): FiniteRepeat? = runCatching {
            val json = JSONObject(text)
            val ids = json.getJSONArray("ids")
            FiniteRepeat(List(ids.length(), ids::getString), json.getInt("index"),
                json.getInt("total"), json.getInt("remaining"), json.getInt("restore"))
        }.getOrNull()
    }
}
