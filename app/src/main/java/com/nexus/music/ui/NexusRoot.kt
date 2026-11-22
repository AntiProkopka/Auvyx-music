package com.nexus.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexus.music.AppViewModel
import com.nexus.music.ui.screens.HomeScreen
import com.nexus.music.ui.screens.LibraryScreen
import com.nexus.music.ui.screens.PlayerScreen
import com.nexus.music.ui.screens.PlaylistScreen
import com.nexus.music.ui.screens.SearchScreen
import com.nexus.music.ui.screens.SettingsScreen

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("home", "Главная", Icons.Default.Home),
    NavItem("search", "Поиск", Icons.Default.Search),
    NavItem("library", "Медиатека", Icons.Default.LibraryMusic),
    NavItem("settings", "Настройки", Icons.Default.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val player by vm.player.state.collectAsStateWithLifecycle()

    val title = when {
        route == "home" -> "AUVYX"
        route == "search" -> "Поиск"
        route == "library" -> "Медиатека"
        route == "settings" -> "Настройки"
        route?.startsWith("playlist") == true -> "Плейлист"
        route == "player" -> "Сейчас играет"
        else -> "AUVYX"
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm.player) { vm.player.errors.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (route != "player" && route?.startsWith("playlist") != true) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            if (route != "player") {
                Column {
                    if (player.mediaId != null) MiniPlayer(vm = vm, onOpen = { nav.navigate("player") })
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEach { item ->
                            val selected = route == item.route || (item.route == "library" && route?.startsWith("playlist") == true)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(nav, item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm) }
            composable("search") { SearchScreen(vm) }
            composable("library") { LibraryScreen(vm, onOpenPlaylist = { id -> nav.navigate("playlist/$id") }) }
            composable("settings") { SettingsScreen(vm) }
            composable(route = "playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PlaylistScreen(vm, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable("player") { PlayerScreen(vm, onClose = { nav.popBackStack() }) }
        }
    }
}

private fun navigateTo(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MiniPlayer(vm: AppViewModel, onOpen: () -> Unit) {
    val s by vm.player.state.collectAsStateWithLifecycle()
    val pos by vm.player.position.collectAsStateWithLifecycle()
    val duration = s.durationMs.coerceAtLeast(1L)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onOpen)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.title.ifBlank { "Ничего не играет" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.player.togglePlayPause() }) {
                    Icon(if (s.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (s.isPlaying) "Пауза" else "Играть")
                }
                IconButton(onClick = { vm.player.next() }) { Icon(Icons.Default.SkipNext, contentDescription = "Следующий") }
            }
            LinearProgressIndicator(
                progress = { (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}
