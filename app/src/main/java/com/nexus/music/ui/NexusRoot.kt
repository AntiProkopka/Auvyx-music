package com.nexus.music.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nexus.music.ui.theme.LocalStyle
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
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
    val relink by vm.relinkTarget.collectAsStateWithLifecycle()
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        relink?.let { if (uri != null) vm.relink(it, uri) }
        vm.relinkTarget.value = null
    }
    LaunchedEffect(relink) { if (relink != null) picker.launch(arrayOf("audio/*", "application/ogg")) }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val player by vm.player.state.collectAsStateWithLifecycle()

    val title = when {
        route == "home" -> "auvyx"
        route == "search" -> "Поиск"
        route == "library" -> "Медиатека"
        route == "settings" -> "Настройки"
        route?.startsWith("playlist") == true -> "Плейлист"
        route == "player" -> "Сейчас играет"
        else -> "auvyx"
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm.player) { vm.player.errors.collect { snackbar.showSnackbar(it) } }

    val style = LocalStyle.current
    val glass = style in listOf("hyper", "glass")
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (glass) {
            Box(Modifier.fillMaxSize().blur(if (style == "hyper") 35.dp else 14.dp).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.tertiaryContainer))))
        }
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (route != "player" && route?.startsWith("playlist") != true) {
                    if (style == "lane") {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            navItems.forEach { item -> IconButton(modifier = Modifier.testTag("nav-${item.route}"), onClick = { vm.albumTarget.value = null; vm.artistTarget.value = null; navigateTo(nav, item.route) }) {
                                Icon(item.icon, item.label, tint = if (route == item.route) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            } }
                        }
                    } else if (style == "hyper") {
                        Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.statusBarsPadding().padding(horizontal = 22.dp, vertical = 10.dp))
                    } else CenterAlignedTopAppBar(
                        title = {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            bottomBar = {
                if (route != "player") {
                    Column {
                        if (player.mediaId != null) MiniPlayer(vm = vm, onOpen = { vm.albumTarget.value = null; vm.artistTarget.value = null; nav.navigate("player") })
                        if (style != "lane" || route?.startsWith("playlist") == true) NavigationBar(
                            modifier = when (style) {
                                "glass" -> Modifier.padding(horizontal = 14.dp, vertical = 6.dp).shadow(8.dp, RoundedCornerShape(36.dp))
                                    .border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(36.dp)).clip(RoundedCornerShape(36.dp))
                                "hyper" -> Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                else -> Modifier
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp
                        ) {
                            navItems.forEach { item ->
                                val selected = route == item.route || (item.route == "library" && route?.startsWith("playlist") == true)
                                NavigationBarItem(
                                    modifier = Modifier.testTag("nav-${item.route}"),
                                    alwaysShowLabel = style != "glass",
                                    selected = selected,
                                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    onClick = { vm.albumTarget.value = null; vm.artistTarget.value = null; navigateTo(nav, item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            val artist by vm.artistTarget.collectAsStateWithLifecycle()
            val album by vm.albumTarget.collectAsStateWithLifecycle()
            Box(Modifier.padding(padding).fillMaxSize()) {
                val motion = MaterialTheme.motionScheme
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    enterTransition = { fadeIn(motion.defaultEffectsSpec()) },
                    exitTransition = { fadeOut(motion.fastEffectsSpec()) }
                ) {
                    composable("home") { HomeScreen(vm) }
                    composable("search") { SearchScreen(vm) }
                    composable("library") { LibraryScreen(vm, onOpenPlaylist = { id -> nav.navigate("playlist/$id") }) }
                    composable("settings") { SettingsScreen(vm) }
                    composable(route = "playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        PlaylistScreen(vm, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                    }
                    composable("player") { PlayerScreen(vm, onClose = { nav.popBackStack() }) }
                }
                album?.let { target ->
                    androidx.activity.compose.BackHandler(enabled = artist == null) { vm.albumTarget.value = null }
                    Surface(Modifier.fillMaxSize()) {
                        androidx.compose.runtime.key(target) { com.nexus.music.ui.screens.AlbumScreen(vm, target) }
                    }
                }
                artist?.let { target ->
                    androidx.activity.compose.BackHandler { vm.artistTarget.value = null }
                    Surface(Modifier.fillMaxSize()) {
                        androidx.compose.runtime.key(target) { com.nexus.music.ui.screens.ArtistScreen(vm, target) }
                    }
                }
            }
        }
    }
    FirstRunNicknameDialog(vm)
}

@Composable
private fun FirstRunNicknameDialog(vm: AppViewModel) {
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    val ready by vm.nicknameReady.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    if (ready && nickname == null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Как к вам обращаться?") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(32) },
                    singleLine = true,
                    label = { Text("Имя или никнейм") },
                    placeholder = { Text("Например, Alex") }
                )
            },
            dismissButton = { TextButton(onClick = { vm.setNickname("") }) { Text("Пропустить") } },
            confirmButton = {
                Button(onClick = { vm.setNickname(draft) }, enabled = draft.isNotBlank()) {
                    Text("Продолжить")
                }
            }
        )
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
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("mini-player").clickable(onClick = onOpen)
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
