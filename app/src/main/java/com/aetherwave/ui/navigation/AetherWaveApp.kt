package com.aetherwave.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aetherwave.core.designsystem.components.AwGlyph
import com.aetherwave.R
import androidx.compose.ui.res.stringResource
import com.aetherwave.core.designsystem.components.ArtworkPalette
import com.aetherwave.core.designsystem.components.DynamicMeshBackground
import com.aetherwave.core.designsystem.components.liquidGlass
import com.aetherwave.core.designsystem.components.LocalLiquidGlassEnabled
import com.aetherwave.core.designsystem.components.liquidGlassSource
import com.aetherwave.core.designsystem.components.rememberLiquidGlassState
import androidx.core.content.ContextCompat
import com.aetherwave.ui.components.MiniPlayer
import com.aetherwave.ui.screens.HomeScreen
import com.aetherwave.ui.screens.LibraryScreen
import com.aetherwave.ui.screens.NowPlayingScreen
import com.aetherwave.ui.screens.SearchScreen
import com.aetherwave.ui.screens.SettingsScreen
import com.aetherwave.ui.viewmodel.HomeViewModel
import com.aetherwave.ui.viewmodel.LibraryViewModel
import com.aetherwave.ui.viewmodel.ExploreViewModel
import com.aetherwave.ui.screens.ExploreScreen
import com.aetherwave.ui.viewmodel.MainPlayerViewModel
import com.aetherwave.ui.viewmodel.SearchViewModel
import androidx.compose.runtime.collectAsState

@Composable
fun AetherWaveApp(
    darkTheme: Boolean,
    themeMode: String,
    onThemeModeChanged: (String) -> Unit,
    liquidGlassEnabled: Boolean,
    onLiquidGlassChanged: (Boolean) -> Unit,
    language: String,
    onLanguageChanged: (String) -> Unit,
    accent: String,
    onAccentChanged: (String) -> Unit,
    dynamicIsland: Boolean,
    onDynamicIslandChanged: (Boolean) -> Unit,
    islandX: Float,
    islandY: Float,
    onIslandPositionChanged: (Float, Float) -> Unit,
    islandStyle: String,
    islandCorner: Float,
    onIslandLookChanged: (String, Float) -> Unit,
) {
    val navController = rememberNavController()
    val hazeState = rememberLiquidGlassState()
    val playerViewModel: MainPlayerViewModel = hiltViewModel()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val searchViewModel: SearchViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val exploreViewModel: ExploreViewModel = hiltViewModel()

    val playerState by playerViewModel.state.collectAsState()
    val homeState by homeViewModel.state.collectAsState()
    val searchState by searchViewModel.state.collectAsState()
    val libraryState by libraryViewModel.state.collectAsState()
    val exploreState by exploreViewModel.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route.orEmpty()
    val isPlayerRoute = route == "player"

    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions -> if (permissions[audioPermission] == true) homeViewModel.refreshLocalAudio() }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }
    LaunchedEffect(route, backStackEntry?.arguments?.getInt("tab")) {
        val isLocalFilesTab = route == "library/{tab}" && backStackEntry?.arguments?.getInt("tab") == 1
        if (isLocalFilesTab) {
            if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
                homeViewModel.refreshLocalAudio()
            } else {
                permissionLauncher.launch(arrayOf(audioPermission))
            }
        }
    }

    CompositionLocalProvider(LocalLiquidGlassEnabled provides liquidGlassEnabled) {
    Box(Modifier.fillMaxSize()) {
        if (liquidGlassEnabled) {
            DynamicMeshBackground(
                palette = ArtworkPalette.Default,
                modifier = Modifier.fillMaxSize().liquidGlassSource(hazeState),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = if (isPlayerRoute) 0.dp else if (playerState.currentTrack != null) 180.dp else 100.dp),
        ) {
            composable("explore") {
                LaunchedEffect(Unit) { exploreViewModel.ensureLoaded() }
                ExploreScreen(
                    state = exploreState,
                    onPlayQueue = playerViewModel::playQueue,
                )
            }
            composable("home") {
                HomeScreen(
                    state = homeState,
                    hazeState = hazeState,
                    onPlayQueue = playerViewModel::playQueue,
                    playlists = libraryState.playlists,
                    onAddToPlaylist = libraryViewModel::addTrack,
                    onOpenLibrary = { tab -> navController.navigate("library/$tab") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenSearch = { navController.navigate("search") { launchSingleTop = true } },
                )
            }
            composable("settings") {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    liquidGlassEnabled = liquidGlassEnabled,
                    onLiquidGlassChanged = onLiquidGlassChanged,
                    language = language,
                    onLanguageChanged = onLanguageChanged,
                    playback = playerState.playback,
                    onSetNormalize = playerViewModel::setNormalizeVolume,
                    onSetLoudness = playerViewModel::setLoudnessLevel,
                    onSetPure = playerViewModel::setPureMode,
                    onSetSkipSilence = playerViewModel::setSkipSilence,
                    onSetSoundStyle = playerViewModel::setSoundStyle,
                    onSetSpeed = playerViewModel::setPlaybackSpeed,
                    onSetPitch = playerViewModel::setPitch,
                    onSetAutoSkip = playerViewModel::setAutoSkipOnError,
                    onSetHaptics = playerViewModel::setHapticsEnabled,
                    accent = accent,
                    onAccentChanged = onAccentChanged,
                    dynamicIsland = dynamicIsland,
                    onDynamicIslandChanged = onDynamicIslandChanged,
                    islandX = islandX,
                    islandY = islandY,
                    onIslandPositionChanged = onIslandPositionChanged,
                    islandStyle = islandStyle,
                    islandCorner = islandCorner,
                    onIslandLookChanged = onIslandLookChanged,
                    onSetSponsorBlock = playerViewModel::setSponsorBlockEnabled,
                    onSetScrobble = playerViewModel::setScrobbleEnabled,
                    onSetListenBrainzToken = playerViewModel::setListenBrainzToken,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("search") {
                SearchScreen(
                    state = searchState,
                    playlists = libraryState.playlists,
                    hazeState = hazeState,
                    playbackError = playerState.playback.errorMessage,
                    onQueryChanged = searchViewModel::onQueryChanged,
                    onPlayQueue = playerViewModel::playQueue,
                    onAddToPlaylist = libraryViewModel::addTrack,
                )
            }
            composable(
                route = "library/{tab}",
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
            ) { entry ->
                val tab = entry.arguments?.getInt("tab")?.coerceIn(0, 3) ?: 0
                LibraryScreen(
                    initialTab = tab,
                    state = libraryState,
                    homeState = homeState,
                    hazeState = hazeState,
                    onTabSelected = { selected -> navController.navigate("library/$selected") { launchSingleTop = true } },
                    onCreatePlaylist = libraryViewModel::createPlaylist,
                    onDeletePlaylist = libraryViewModel::deletePlaylist,
                    onPlayQueue = playerViewModel::playQueue,
                    onRemoveTrack = libraryViewModel::removeTrack,
                    onRefreshLocal = {
                        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
                            homeViewModel.refreshLocalAudio()
                        } else {
                            permissionLauncher.launch(arrayOf(audioPermission))
                        }
                    },
                )
            }
            composable(
                route = "player",
                enterTransition = { slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(260)) },
                exitTransition = { slideOutVertically(tween(200)) { it / 4 } + fadeOut(tween(180)) },
                popEnterTransition = { slideInVertically(tween(220)) { -it / 4 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(180)) },
            ) {
                NowPlayingScreen(
                    state = playerState,
                    hazeState = hazeState,
                    onBack = { navController.popBackStack() },
                    onPlayPause = playerViewModel::togglePlayPause,
                    onNext = playerViewModel::next,
                    onPrevious = playerViewModel::previous,
                    onSeek = playerViewModel::seekTo,
                    onShuffle = playerViewModel::toggleShuffle,
                    onRepeat = playerViewModel::cycleRepeat,
                    onLike = playerViewModel::toggleLike,
                    onDownload = playerViewModel::downloadCurrentTrack,
                    onToggleLyrics = playerViewModel::toggleLyrics,
                    onSetSpeed = playerViewModel::setPlaybackSpeed,
                    onSetPitch = playerViewModel::setPitch,
                    onSetSkipSilence = playerViewModel::setSkipSilence,
                    onSetSleepTimer = playerViewModel::setSleepTimer,
                    onSetSleepEndOfTrack = playerViewModel::setSleepAtEndOfTrack,
                    onCancelSleep = playerViewModel::cancelSleepTimer,
                    onMoveQueueItem = playerViewModel::moveQueueItem,
                    onRemoveQueueItem = playerViewModel::removeQueueItem,
                    onJumpQueueItem = playerViewModel::jumpToQueueItem,
                    onSetNormalize = playerViewModel::setNormalizeVolume,
                    onSetSoundStyle = playerViewModel::setSoundStyle,
                    onSetLoudness = playerViewModel::setLoudnessLevel,
                    onSetPure = playerViewModel::setPureMode,
                )
            }
        }

        if (!isPlayerRoute) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                playerState.currentTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = playerState.playback.isPlaying,
                        position = playerState.playback.position,
                        duration = playerState.playback.duration,
                        hazeState = hazeState,
                        onOpen = { navController.navigate("player") },
                        onPlayPause = playerViewModel::togglePlayPause,
                    )
                }
                BottomNavigationBar(
                    selectedRoute = route,
                    hazeState = hazeState,
                    onHome = { navController.navigate("home") { launchSingleTop = true; popUpTo("home") { saveState = true } } },
                    onSearch = { navController.navigate("search") { launchSingleTop = true } },
                    onExplore = { navController.navigate("explore") { launchSingleTop = true } },
                    onLibrary = { navController.navigate("library/0") { launchSingleTop = true } },
                )
            }
        }
    }
    }
}

@Composable
private fun BottomNavigationBar(
    selectedRoute: String,
    hazeState: dev.chrisbanes.haze.HazeState,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onExplore: () -> Unit,
    onLibrary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().liquidGlass(hazeState, MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationItem("⌂", stringResource(R.string.nav_home), selectedRoute == "home", Modifier.weight(1f), onHome)
        NavigationItem("⌕", stringResource(R.string.nav_search), selectedRoute == "search", Modifier.weight(1f), onSearch)
        NavigationItem("✦", stringResource(R.string.nav_explore), selectedRoute == "explore", Modifier.weight(1f), onExplore)
        NavigationItem("▦", stringResource(R.string.nav_library), selectedRoute.startsWith("library"), Modifier.weight(1f), onLibrary)
    }
}

@Composable
private fun NavigationItem(icon: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AwGlyph(icon, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f), 22.dp, 19.sp)
        Text(label, color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f), fontSize = 9.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold)
    }
}

