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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
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
import com.aetherwave.core.designsystem.components.ArtworkPalette
import com.aetherwave.core.designsystem.components.DynamicMeshBackground
import com.aetherwave.core.designsystem.components.liquidGlass
import com.aetherwave.core.designsystem.components.liquidGlassSource
import com.aetherwave.core.designsystem.components.rememberLiquidGlassState
import androidx.core.content.ContextCompat
import com.aetherwave.ui.components.MiniPlayer
import com.aetherwave.ui.screens.HomeScreen
import com.aetherwave.ui.screens.LibraryScreen
import com.aetherwave.ui.screens.NowPlayingScreen
import com.aetherwave.ui.screens.SearchScreen
import com.aetherwave.ui.viewmodel.HomeViewModel
import com.aetherwave.ui.viewmodel.LibraryViewModel
import com.aetherwave.ui.viewmodel.MainPlayerViewModel
import com.aetherwave.ui.viewmodel.SearchViewModel
import androidx.compose.runtime.collectAsState

@Composable
fun AetherWaveApp() {
    val navController = rememberNavController()
    val hazeState = rememberLiquidGlassState()
    val playerViewModel: MainPlayerViewModel = hiltViewModel()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val searchViewModel: SearchViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()

    val playerState by playerViewModel.state.collectAsState()
    val homeState by homeViewModel.state.collectAsState()
    val searchState by searchViewModel.state.collectAsState()
    val libraryState by libraryViewModel.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route.orEmpty()
    val isPlayerRoute = route == "player"

    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { homeViewModel.refreshLocalAudio() }
    val requestedPermissions = androidx.compose.runtime.remember {
        buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        val missing = requestedPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) homeViewModel.refreshLocalAudio() else permissionLauncher.launch(missing.toTypedArray())
    }

    Box(Modifier.fillMaxSize()) {
        DynamicMeshBackground(
            palette = ArtworkPalette.Default,
            modifier = Modifier.fillMaxSize().liquidGlassSource(hazeState),
        )
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = if (isPlayerRoute) 0.dp else if (playerState.currentTrack != null) 180.dp else 100.dp),
        ) {
            composable("home") {
                HomeScreen(
                    state = homeState,
                    hazeState = hazeState,
                    onPlayQueue = playerViewModel::playQueue,
                    onOpenLibrary = { tab -> navController.navigate("library/$tab") },
                    onRefreshLocal = {
                        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
                            homeViewModel.refreshLocalAudio()
                        } else {
                            permissionLauncher.launch(arrayOf(audioPermission))
                        }
                    },
                )
            }
            composable("search") {
                SearchScreen(
                    state = searchState,
                    playlists = libraryState.playlists,
                    hazeState = hazeState,
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
                    onLibrary = { navController.navigate("library/0") { launchSingleTop = true } },
                )
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
    onLibrary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().liquidGlass(hazeState, Color(0xFF1A2035).copy(alpha = 0.78f), RoundedCornerShape(24.dp)).padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationItem("⌂", "HOME", selectedRoute == "home", Modifier.weight(1f), onHome)
        NavigationItem("⌕", "SEARCH", selectedRoute == "search", Modifier.weight(1f), onSearch)
        NavigationItem("♫", "LIBRARY", selectedRoute.startsWith("library"), Modifier.weight(1f), onLibrary)
    }
}

@Composable
private fun NavigationItem(icon: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(icon, color = if (selected) Color(0xFFD1D2FF) else Color.White.copy(alpha = 0.62f), fontSize = 19.sp)
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = 0.54f), fontSize = 8.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
    }
}

