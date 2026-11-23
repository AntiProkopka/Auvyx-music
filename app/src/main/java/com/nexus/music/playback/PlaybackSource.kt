package com.nexus.music.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import com.nexus.music.data.soundcloud.SoundCloudRepository

@UnstableApi
internal fun playbackSource(context: Context): DataSource.Factory {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
    val upstreamFactory = DefaultDataSource.Factory(context, httpFactory)
    val resolvingFactory = ResolvingDataSource.Factory(
        upstreamFactory,
        ResolvingDataSource.Resolver { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == "auvyx-sc") {
                dataSpec.buildUpon()
                    .setUri(SoundCloudRepository.resolvePlaybackUri(uri))
                    .build()
            } else if (uri.scheme == "content") {
                val copy = com.nexus.music.data.local.OfflineAudio.file(context, uri.toString())
                if (copy.isFile) dataSpec.withUri(android.net.Uri.fromFile(copy)) else dataSpec
            } else {
                dataSpec
            }
        }
    )
    return resolvingFactory
}
