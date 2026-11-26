package com.nexus.music.data.settings

import org.json.JSONArray
import org.json.JSONObject

enum class GlassAccent(val title: String, val photoFolder: String, val ink: Long, val light: Long, val mist: Long) {
    Default("Default AUVYX", "auvyx default", 0xff8e7bff, 0xffc89bff, 0xffff8fd8),
    Ice("Ice Blue", "ice blue", 0xff72b8ff, 0xff8fd8ff, 0xffddf6ff),
    Emerald("Emerald Night", "emerald night", 0xff38d39f, 0xff67f0c2, 0xffb6ffe7),
    Sunset("Sunset Glow", "sunset glow", 0xffac642b, 0xffffad42, 0xffffe2a3),
    Rose("Rose Pulse", "rose pulse", 0xffff5fa8, 0xffff82c9, 0xffffd1ea),
    Crimson("Crimson Wave", "crimson wave", 0xffe64545, 0xffff6b6b, 0xffffc1c1),
    Silver("Silver Mist", "silver mist", 0xffbfc4cf, 0xffe8e8e8, 0xffffffff),
    Ocean("Ocean Deep", "ocean deep", 0xff2fa7ff, 0xff52d6ff, 0xff9cebff),
    Forest("Forest Haze", "forest haze", 0xff456b40, 0xff91b580, 0xffd6e8c9)
}

enum class GlassQuality { Medium, High, Ultra }

data class FollowedArtist(val name: String, val owner: Long?, val cloud: Boolean, val permalink: String?, val avatar: String?) {
    val key get() = if (cloud) "sc/" + (owner?.toString() ?: permalink) else "local/" + name.trim().lowercase()
    fun json() = JSONObject().put("name", name).put("owner", owner).put("cloud", cloud)
        .put("permalink", permalink).put("avatar", avatar)
    companion object {
        fun read(raw: String): List<FollowedArtist> {
            val array = JSONArray(raw)
            return (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                FollowedArtist(o.getString("name"), o.optLong("owner").takeIf { it > 0 }, o.getBoolean("cloud"),
                    o.optString("permalink").ifBlank { null }, o.optString("avatar").ifBlank { null })
            }
        }
    }
}

internal fun orderedStrings(raw: String?): List<String> {
    val array = JSONArray(raw ?: "[]")
    return (0 until array.length()).map(array::getString)
}
