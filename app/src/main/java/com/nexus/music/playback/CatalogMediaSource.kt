package com.nexus.music.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.*
import androidx.media3.exoplayer.upstream.Allocator
import com.nexus.music.data.catalog.NativeCatalog
import kotlinx.coroutines.*
import java.io.IOException

/** Resolve only when ExoPlayer prepares this queue entry, including restored queues. */
internal class CatalogMediaSource(private val item: MediaItem, private val factory: MediaSource.Factory) :
    CompositeMediaSource<Unit>() {
    private var job: Job? = null
    private var child: MediaSource? = null
    private var failure: IOException? = null

    override fun getMediaItem() = item
    override fun prepareSourceInternal(listener: TransferListener?) {
        super.prepareSourceInternal(listener)
        val handler = Handler(requireNotNull(Looper.myLooper()))
        job = CoroutineScope(Dispatchers.IO).launch {
            val owner = coroutineContext.job
            val result = runCatching { NativeCatalog.resolve(item) }
            handler.post {
                if (job !== owner) return@post
                result.onSuccess { resolved ->
                    val source = factory.createMediaSource(resolved)
                    child = source
                    prepareChildSource(Unit, source)
                }.onFailure { failure = IOException(it.message ?: "Аудиопоток недоступен", it) }
            }
        }
    }

    override fun onChildSourceInfoRefreshed(id: Unit, mediaSource: MediaSource, timeline: Timeline) = refreshSourceInfo(timeline)
    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
        requireNotNull(child).createPeriod(id, allocator, startPositionUs)
    override fun releasePeriod(period: MediaPeriod) = requireNotNull(child).releasePeriod(period)
    override fun maybeThrowSourceInfoRefreshError() {
        failure?.let { throw it }
        super.maybeThrowSourceInfoRefreshError()
    }
    override fun releaseSourceInternal() {
        job?.cancel()
        job = null
        super.releaseSourceInternal()
        child = null
        failure = null
    }
}

internal class CatalogMediaFactory(private val base: MediaSource.Factory) : MediaSource.Factory by base {
    override fun createMediaSource(item: MediaItem): MediaSource =
        if (item.localConfiguration?.uri?.scheme in setOf("auvyx-spotify", "auvyx-yt")) CatalogMediaSource(item, base)
        else base.createMediaSource(item)
}
