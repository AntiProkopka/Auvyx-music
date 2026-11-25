package com.nexus.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.SkipPrevious
import dev.chrisbanes.haze.*
import com.nexus.music.R
import com.nexus.music.ui.theme.*
import com.nexus.music.ui.components.BrandIcon
import com.nexus.music.ui.components.rememberArtworkAccent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nexus.music.ui.theme.LocalStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
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

internal val LocalTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
internal fun Modifier.sharedCover(): Modifier {
    val transition = LocalTransition.current ?: return this
    val visibility = LocalVisibility.current ?: return this
    return with(transition) { this@sharedCover.sharedElement(rememberSharedContentState("playing-cover"), visibility) }
}

internal val LocalBottomBarPadding = staticCompositionLocalOf { 0.dp }

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

    val openPlayer: () -> Unit = {
        vm.albumTarget.value = null
        vm.artistTarget.value = null
        nav.navigate("player") { launchSingleTop = true }
    }

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
    val glass = style == "glass"
    val wallpaper = rememberHazeState()
    val page = rememberHazeState()
    val lamp = remember { mutableStateOf(Offset.Unspecified) }
    val playingArt = if (glass) artworkBitmap(player.artworkUri) else null
    LaunchedEffect(player.mediaId) { if (player.mediaId == null) lamp.value = Offset.Unspecified }
    val primary = MaterialTheme.colorScheme.primary
    val artLight by animateColorAsState(if (glass) rememberArtworkAccent(playingArt, primary) ?: Color.Transparent else Color.Transparent, tween(580))
    SharedTransitionLayout {
    CompositionLocalProvider(LocalWallpaper provides wallpaper, LocalPage provides page, LocalLamp provides lamp,
        LocalArtLight provides artLight, LocalTransition provides if (glass) this else null) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (glass) Box(Modifier.fillMaxSize().hazeSource(page, zIndex = 0f).hazeSource(wallpaper).background(MaterialTheme.colorScheme.background)) {
            GlassWallpaper(vm)
            AtmosphericLight(route == "player")
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
                AnimatedVisibility(visible = route != "player", exit = fadeOut(tween(210)), enter = fadeIn(tween(240))) {
                    val miniVisibility = this
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (player.mediaId != null) CompositionLocalProvider(LocalVisibility provides miniVisibility) { MiniPlayer(vm, onOpen = openPlayer) }
                        if (glass) {
                            Row(Modifier.fillMaxWidth().height(64.dp).frosted(CircleShape, overPage = true).testTag("bottom-dock"),
                                verticalAlignment = Alignment.CenterVertically) {
                                navItems.forEachIndexed { index, item ->
                                    val active = route == item.route || (item.route == "library" && route?.startsWith("playlist") == true)
                                    val color by animateColorAsState(if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(180))
                                    Box(Modifier.weight(1f).fillMaxHeight().testTag("nav-${item.route}")
                                        .selectable(active, role = androidx.compose.ui.semantics.Role.Tab) {
                                            vm.albumTarget.value = null
                                            vm.artistTarget.value = null
                                            navigateTo(nav, item.route)
                                        }, contentAlignment = Alignment.Center) {
                                        Icon(painterResource(listOf(R.drawable.glass_home, R.drawable.glass_search,
                                            R.drawable.glass_library, R.drawable.glass_settings)[index]), item.label, tint = color)
                                        if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                                            .size(20.dp, 3.dp).clip(CircleShape).background(Brush.horizontalGradient(
                                                listOf(color.copy(alpha=.18f), color, color.copy(alpha=.18f)))))
                                    }
                                }
                            }
                        } else if (style != "lane" || route?.startsWith("playlist") == true) NavigationBar(
                            modifier = Modifier.clip(MaterialTheme.shapes.extraLarge)
                                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 1f))
                                .testTag("bottom-dock"),
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 0.dp
                        ) {
                            navItems.forEach { item ->
                                val selected = route == item.route || (item.route == "library" && route?.startsWith("playlist") == true)
                                NavigationBarItem(
                                    modifier = Modifier.testTag("nav-${item.route}"),
                                    selected = selected,
                                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    onClick = { vm.albumTarget.value = null; vm.artistTarget.value = null; navigateTo(nav, item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) }
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            val artist by vm.artistTarget.collectAsStateWithLifecycle()
            val album by vm.albumTarget.collectAsStateWithLifecycle()
            val direction = LocalLayoutDirection.current
            val bottomPadding = padding.calculateBottomPadding()
            // Keep the viewport behind the transparent controls and reserve room at the end of scrolling.
            CompositionLocalProvider(LocalBottomBarPadding provides if (route == "player") 0.dp else bottomPadding) {
                Box(Modifier.padding(
                    start = padding.calculateStartPadding(direction),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(direction),
                    bottom = if (route == "player") bottomPadding else 0.dp
                ).fillMaxSize().then(if (glass) Modifier.hazeSource(page, zIndex = 1f) else Modifier)) {
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
                        composable("player") { CompositionLocalProvider(LocalVisibility provides this) { PlayerScreen(vm, onClose = { nav.popBackStack() }) } }
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
                            androidx.compose.runtime.key(target) { com.nexus.music.ui.screens.ArtistScreen(vm, target, onOpenPlayer = openPlayer) }
                        }
                    }
                }
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

    Column(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(if (isGlass) Modifier.frosted(MaterialTheme.shapes.large, overPage = true) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
            .clickable(interactionSource = null, indication = ripple(), onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isGlass) {
                val lamp = LocalLamp.current
                ArtTile(s.artworkUri, "Обложка", Modifier.size(46.dp).sharedCover().onGloballyPositioned {
                    lamp.value = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                })
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(s.title.ifBlank { "Ничего не играет" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isGlass) IconButton(onClick = vm.player::previous) {
                BrandIcon(R.drawable.glass_previous, Icons.Default.SkipPrevious, "Предыдущий")
            }
            IconButton(onClick = { vm.player.togglePlayPause() }) {
                BrandIcon(if (s.showPause) R.drawable.glass_pause else R.drawable.glass_play, if (s.showPause) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (s.showPause) "Пауза" else "Играть")
            }
            IconButton(onClick = { vm.player.next() }) { BrandIcon(R.drawable.glass_next, Icons.Default.SkipNext, contentDescription = "Следующий") }
        }
        LinearProgressIndicator(
            progress = { (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
            drawStopIndicator = {}
        )
    }
}
