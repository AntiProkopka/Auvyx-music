package com.nexus.music.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.music.AppViewModel
import com.nexus.music.R
import com.nexus.music.ui.LocalBottomBarPadding
import com.nexus.music.ui.theme.*

internal data class CoverItem(val key: String, val title: String, val subtitle: String?, val artwork: String?, val onClick: () -> Unit)

@Composable
internal fun CoverCarousel(title: String, items: List<CoverItem>, labels: Boolean = true, empty: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.titleLarge)
        if (items.isEmpty()) Text(empty, Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.key }) { item ->
                Column(Modifier.width(138.dp).clip(RoundedCornerShape(17.dp)).clickable(onClick = item.onClick)) {
                    ArtTile(item.artwork, item.title, Modifier.fillMaxWidth().aspectRatio(1f))
                    if (labels) {
                        Text(item.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        item.subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlassHome(vm: AppViewModel) {
    val history by vm.recent.collectAsStateWithLifecycle()
    val followed by vm.followed.collectAsStateWithLifecycle()
    val labels by vm.recentLabels.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomBarPadding.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { HomeBanner("welcome back", groupPhoto(vm), false) }
        item { CoverCarousel("Recently Played", history.distinctBy { it.id }.map {
            CoverItem(it.id.toString(), it.title, it.artist, it.artworkUri) { vm.playTrack(it) }
        }, labels, "Здесь появятся прослушанные песни") }
        item { HomeBanner("here's music for you now", groupPhoto(vm, alternate = true), true) }
        item { CoverCarousel("Followed Artists", followed.map {
            CoverItem(it.key, it.name, null, it.avatar) { vm.artistTarget.value = AppViewModel.ArtistTarget(it.name, it.cloud, it.owner, it.permalink) }
        }, empty = "Здесь появятся исполнители, на которых ты подпишешься") }
    }
}

@Composable
private fun HomeBanner(title: String, photo: String?, mix: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(if (mix) 154.dp else 186.dp)
        .frosted(RoundedCornerShape(30.dp))) {
        GlassPhoto(photo, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.Black.copy(alpha=.45f), Color.Transparent))))
        Text(title, Modifier.align(if (mix) Alignment.TopStart else Alignment.BottomStart).padding(20.dp),
            style = if (mix) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge, color = Color.White)
        if (mix) IconButton(onClick = {}, enabled = false, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
            Icon(painterResource(R.drawable.glass_play), "Daily Mix пока недоступен")
        }
    }
}
