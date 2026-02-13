package com.nuvio.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.ThemeDataStore
import com.nuvio.tv.domain.model.AppTheme
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.updater.UpdateViewModel
import com.nuvio.tv.updater.ui.UpdatePromptDialog
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import javax.inject.Inject
import kotlinx.coroutines.delay

data class DrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeDataStore: ThemeDataStore

    @Inject
    lateinit var layoutPreferenceDataStore: LayoutPreferenceDataStore

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val currentTheme by themeDataStore.selectedTheme.collectAsState(initial = AppTheme.OCEAN)
            val hasChosenLayout by layoutPreferenceDataStore.hasChosenLayout.collectAsState(initial = null as Boolean?)

            NuvioTheme(appTheme = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    val layoutChosen = hasChosenLayout ?: return@Surface

                    val sidebarCollapsed by layoutPreferenceDataStore.sidebarCollapsedByDefault.collectAsState(initial = false)
                    val modernSidebarEnabled by layoutPreferenceDataStore.modernSidebarEnabled.collectAsState(initial = false)
                    val modernSidebarBlurEnabled by layoutPreferenceDataStore.modernSidebarBlurEnabled.collectAsState(initial = false)
                    val hideBuiltInHeadersForFloatingPill = modernSidebarEnabled && !sidebarCollapsed

                    val updateViewModel: UpdateViewModel = hiltViewModel(this@MainActivity)
                    val updateState by updateViewModel.uiState.collectAsState()

                    val startDestination = if (layoutChosen) Screen.Home.route else Screen.LayoutSelection.route
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val rootRoutes = remember {
                        setOf(
                            Screen.Home.route,
                            Screen.Search.route,
                            Screen.Library.route,
                            Screen.Settings.route,
                            Screen.AddonManager.route
                        )
                    }

                    val drawerItems = remember {
                        listOf(
                            DrawerItem(
                                route = Screen.Home.route,
                                label = "Home",
                                icon = Icons.Filled.Home
                            ),
                            DrawerItem(
                                route = Screen.Search.route,
                                label = "Search",
                                icon = Icons.Filled.Search
                            ),
                            DrawerItem(
                                route = Screen.Library.route,
                                label = "Library",
                                icon = Icons.Filled.Bookmark
                            ),
                            DrawerItem(
                                route = Screen.AddonManager.route,
                                label = "Addons",
                                icon = Icons.Filled.Extension
                            ),
                            DrawerItem(
                                route = Screen.Settings.route,
                                label = "Settings",
                                icon = Icons.Filled.Settings
                            )
                        )
                    }
                    val selectedDrawerRoute = drawerItems.firstOrNull { item ->
                        currentRoute == item.route || currentRoute?.startsWith("${item.route}/") == true
                    }?.route
                    val selectedDrawerItem = drawerItems.firstOrNull { it.route == selectedDrawerRoute } ?: drawerItems.first()

                    if (modernSidebarEnabled) {
                        ModernSidebarScaffold(
                            navController = navController,
                            startDestination = startDestination,
                            currentRoute = currentRoute,
                            rootRoutes = rootRoutes,
                            drawerItems = drawerItems,
                            selectedDrawerRoute = selectedDrawerRoute,
                            selectedDrawerItem = selectedDrawerItem,
                            sidebarCollapsed = sidebarCollapsed,
                            modernSidebarBlurEnabled = modernSidebarBlurEnabled,
                            hideBuiltInHeaders = hideBuiltInHeadersForFloatingPill
                        )
                    } else {
                        LegacySidebarScaffold(
                            navController = navController,
                            startDestination = startDestination,
                            currentRoute = currentRoute,
                            rootRoutes = rootRoutes,
                            drawerItems = drawerItems,
                            selectedDrawerRoute = selectedDrawerRoute,
                            sidebarCollapsed = sidebarCollapsed,
                            hideBuiltInHeaders = false
                        )
                    }

                    UpdatePromptDialog(
                        state = updateState,
                        onDismiss = { updateViewModel.dismissDialog() },
                        onDownload = { updateViewModel.downloadUpdate() },
                        onInstall = { updateViewModel.installUpdateOrRequestPermission() },
                        onIgnore = { updateViewModel.ignoreThisVersion() },
                        onOpenUnknownSources = { updateViewModel.openUnknownSourcesSettings() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LegacySidebarScaffold(
    navController: NavHostController,
    startDestination: String,
    currentRoute: String?,
    rootRoutes: Set<String>,
    drawerItems: List<DrawerItem>,
    selectedDrawerRoute: String?,
    sidebarCollapsed: Boolean,
    hideBuiltInHeaders: Boolean
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerItemFocusRequesters = remember(drawerItems) {
        drawerItems.associate { item -> item.route to FocusRequester() }
    }
    val showSidebar = currentRoute in rootRoutes

    LaunchedEffect(currentRoute) {
        drawerState.setValue(DrawerValue.Closed)
    }

    BackHandler(enabled = currentRoute in rootRoutes && drawerState.currentValue == DrawerValue.Closed) {
        drawerState.setValue(DrawerValue.Open)
    }

    val closedDrawerWidth = if (sidebarCollapsed) 0.dp else 72.dp
    val openDrawerWidth = 260.dp

    val focusManager = LocalFocusManager.current
    var pendingContentFocusTransfer by remember { mutableStateOf(false) }

    LaunchedEffect(drawerState.currentValue, pendingContentFocusTransfer) {
        if (!pendingContentFocusTransfer || drawerState.currentValue != DrawerValue.Closed) {
            return@LaunchedEffect
        }
        repeat(2) { withFrameNanos { } }
        focusManager.moveFocus(FocusDirection.Right)
        pendingContentFocusTransfer = false
    }

    LaunchedEffect(drawerState.currentValue, selectedDrawerRoute, showSidebar) {
        if (!showSidebar || drawerState.currentValue != DrawerValue.Open) return@LaunchedEffect
        val targetRoute = selectedDrawerRoute ?: return@LaunchedEffect
        val requester = drawerItemFocusRequesters[targetRoute] ?: return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        runCatching { requester.requestFocus() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            if (showSidebar) {
                val drawerWidth = if (drawerValue == DrawerValue.Open) openDrawerWidth else closedDrawerWidth
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(drawerWidth)
                        .background(NuvioColors.Background)
                        .padding(12.dp)
                        .selectableGroup()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown) {
                                drawerState.setValue(DrawerValue.Closed)
                                pendingContentFocusTransfer = true
                                true
                            } else {
                                false
                            }
                        }
                ) {
                    val sidebarLogoTopPadding = 20.dp

                    if (drawerValue == DrawerValue.Open) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo_wordmark),
                            contentDescription = "NuvioTV",
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(1214f / 408f)
                                .padding(top = sidebarLogoTopPadding),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo_mark),
                            contentDescription = "Nuvio",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(top = sidebarLogoTopPadding),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    val itemColors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = NuvioColors.BackgroundCard,
                        focusedContainerColor = NuvioColors.FocusBackground,
                        pressedContainerColor = NuvioColors.FocusBackground,
                        selectedContentColor = NuvioColors.TextPrimary,
                        focusedContentColor = NuvioColors.FocusRing,
                        pressedContentColor = NuvioColors.FocusRing
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        drawerItems.forEach { item ->
                            NavigationDrawerItem(
                                selected = selectedDrawerRoute == item.route,
                                onClick = {
                                    navigateToDrawerRoute(
                                        navController = navController,
                                        currentRoute = currentRoute,
                                        targetRoute = item.route
                                    )
                                    drawerState.setValue(DrawerValue.Closed)
                                    pendingContentFocusTransfer = true
                                },
                                colors = itemColors,
                                modifier = Modifier.focusRequester(
                                    drawerItemFocusRequesters.getValue(item.route)
                                ),
                                leadingContent = {
                                    Icon(imageVector = item.icon, contentDescription = null)
                                }
                            ) {
                                if (drawerValue == DrawerValue.Open) {
                                    Text(item.label)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    ) {
        val contentStartPadding = if (showSidebar) closedDrawerWidth else 0.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = contentStartPadding)
        ) {
            NuvioNavHost(
                navController = navController,
                startDestination = startDestination,
                hideBuiltInHeaders = hideBuiltInHeaders
            )
        }
    }
}

@Composable
private fun ModernSidebarScaffold(
    navController: NavHostController,
    startDestination: String,
    currentRoute: String?,
    rootRoutes: Set<String>,
    drawerItems: List<DrawerItem>,
    selectedDrawerRoute: String?,
    selectedDrawerItem: DrawerItem,
    sidebarCollapsed: Boolean,
    modernSidebarBlurEnabled: Boolean,
    hideBuiltInHeaders: Boolean
) {
    val showSidebar = currentRoute in rootRoutes
    val collapsedSidebarWidth = if (sidebarCollapsed) 0.dp else 184.dp
    val openSidebarWidth = 262.dp

    val focusManager = LocalFocusManager.current
    val drawerItemFocusRequesters = remember(drawerItems) {
        drawerItems.associate { item -> item.route to FocusRequester() }
    }

    var isSidebarExpanded by remember { mutableStateOf(false) }
    var sidebarCollapsePending by remember { mutableStateOf(false) }
    var pendingContentFocusTransfer by remember { mutableStateOf(false) }
    var pendingSidebarFocusRequest by remember { mutableStateOf(false) }
    var focusedDrawerIndex by remember { mutableStateOf(-1) }
    var isCollapsedPillIconOnly by remember { mutableStateOf(false) }
    val keepSidebarFocusDuringCollapse =
        isSidebarExpanded || sidebarCollapsePending || pendingContentFocusTransfer

    LaunchedEffect(showSidebar) {
        if (!showSidebar) {
            isSidebarExpanded = false
            sidebarCollapsePending = false
            pendingContentFocusTransfer = false
            pendingSidebarFocusRequest = false
            isCollapsedPillIconOnly = false
        }
    }

    BackHandler(enabled = currentRoute in rootRoutes) {
        if (isSidebarExpanded || sidebarCollapsePending) {
            pendingContentFocusTransfer = true
            sidebarCollapsePending = true
        } else {
            isSidebarExpanded = true
            sidebarCollapsePending = false
            pendingSidebarFocusRequest = true
            isCollapsedPillIconOnly = false
        }
    }

    LaunchedEffect(sidebarCollapsePending, isSidebarExpanded, showSidebar) {
        if (!showSidebar || !sidebarCollapsePending) {
            return@LaunchedEffect
        }
        if (!isSidebarExpanded) {
            sidebarCollapsePending = false
            return@LaunchedEffect
        }
        delay(95L)
        isSidebarExpanded = false
        sidebarCollapsePending = false
    }

    val sidebarVisible = showSidebar && (isSidebarExpanded || !sidebarCollapsed)
    val sidebarHazeState = remember { HazeState() }
    val targetSidebarWidth = when {
        !sidebarVisible -> 0.dp
        isSidebarExpanded -> openSidebarWidth
        else -> collapsedSidebarWidth
    }
    val sidebarWidth by animateDpAsState(
        targetValue = targetSidebarWidth,
        animationSpec = if (isSidebarExpanded) {
            keyframes {
                durationMillis = 365
                (openSidebarWidth + 12.dp) at 175
            }
        } else {
            tween(durationMillis = 385, easing = LinearOutSlowInEasing)
        },
        label = "sidebarWidth"
    )
    val sidebarSlideX by animateDpAsState(
        targetValue = if (sidebarVisible) 0.dp else (-24).dp,
        animationSpec = tween(durationMillis = 205, easing = FastOutSlowInEasing),
        label = "sidebarSlideX"
    )
    val sidebarSurfaceAlpha by animateFloatAsState(
        targetValue = if (sidebarVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 135, easing = FastOutSlowInEasing),
        label = "sidebarSurfaceAlpha"
    )
    val sidebarLabelAlpha by animateFloatAsState(
        targetValue = if (isSidebarExpanded) 1f else 0f,
        animationSpec = if (isSidebarExpanded) {
            tween(durationMillis = 125, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 145, easing = LinearOutSlowInEasing)
        },
        label = "sidebarLabelAlpha"
    )
    val sidebarExpandProgress by animateFloatAsState(
        targetValue = if (isSidebarExpanded) 1f else 0f,
        animationSpec = if (isSidebarExpanded) {
            tween(durationMillis = 345, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 385, easing = LinearOutSlowInEasing)
        },
        label = "sidebarExpandProgress"
    )
    val sidebarIconScale by animateFloatAsState(
        targetValue = if (isSidebarExpanded) 1f else 0.92f,
        animationSpec = tween(durationMillis = 145, easing = FastOutSlowInEasing),
        label = "sidebarIconScale"
    )
    val sidebarBloomScale by animateFloatAsState(
        targetValue = if (isSidebarExpanded) 1f else 0.9f,
        animationSpec = if (isSidebarExpanded) {
            tween(durationMillis = 345, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 395, easing = LinearOutSlowInEasing)
        },
        label = "sidebarBloomScale"
    )
    val sidebarDeflateOffsetX by animateDpAsState(
        targetValue = if (isSidebarExpanded) 0.dp else (-10).dp,
        animationSpec = if (isSidebarExpanded) {
            tween(durationMillis = 345, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 395, easing = LinearOutSlowInEasing)
        },
        label = "sidebarDeflateOffsetX"
    )
    val sidebarDeflateOffsetY by animateDpAsState(
        targetValue = if (isSidebarExpanded) 0.dp else (-8).dp,
        animationSpec = if (isSidebarExpanded) {
            tween(durationMillis = 345, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 395, easing = LinearOutSlowInEasing)
        },
        label = "sidebarDeflateOffsetY"
    )

    LaunchedEffect(isSidebarExpanded, sidebarCollapsePending, pendingContentFocusTransfer, showSidebar) {
        if (!showSidebar || !pendingContentFocusTransfer || isSidebarExpanded || sidebarCollapsePending) {
            return@LaunchedEffect
        }
        repeat(2) { withFrameNanos { } }
        focusManager.moveFocus(FocusDirection.Right)
        pendingContentFocusTransfer = false
    }

    LaunchedEffect(isSidebarExpanded, pendingSidebarFocusRequest, showSidebar, selectedDrawerRoute) {
        if (!showSidebar || !pendingSidebarFocusRequest || !isSidebarExpanded) {
            return@LaunchedEffect
        }
        val targetRoute = selectedDrawerRoute ?: run {
            pendingSidebarFocusRequest = false
            return@LaunchedEffect
        }
        val requester = drawerItemFocusRequesters[targetRoute] ?: run {
            pendingSidebarFocusRequest = false
            return@LaunchedEffect
        }
        repeat(2) { withFrameNanos { } }
        runCatching { requester.requestFocus() }
        pendingSidebarFocusRequest = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(sidebarHazeState)
                .onPreviewKeyEvent { keyEvent ->
                    if (
                        showSidebar &&
                        !sidebarCollapsed &&
                        !isSidebarExpanded &&
                        keyEvent.type == KeyEventType.KeyDown
                    ) {
                        when (keyEvent.key) {
                            Key.DirectionDown -> isCollapsedPillIconOnly = true
                            Key.DirectionUp -> isCollapsedPillIconOnly = false
                            else -> Unit
                        }
                    }
                    if (
                        isSidebarExpanded &&
                        !sidebarCollapsePending &&
                        sidebarExpandProgress > 0.2f &&
                        keyEvent.type == KeyEventType.KeyDown &&
                        isBlockedContentKey(keyEvent.key)
                    ) {
                        true
                    } else {
                        false
                    }
                }
                .onKeyEvent { keyEvent ->
                    if (
                        showSidebar &&
                        !isSidebarExpanded &&
                        keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.DirectionLeft
                    ) {
                        if (focusManager.moveFocus(FocusDirection.Left)) {
                            true
                        } else {
                            isSidebarExpanded = true
                            sidebarCollapsePending = false
                            pendingSidebarFocusRequest = true
                            isCollapsedPillIconOnly = false
                            true
                        }
                    } else {
                        false
                    }
                }
        ) {
            NuvioNavHost(
                navController = navController,
                startDestination = startDestination,
                hideBuiltInHeaders = hideBuiltInHeaders
            )
        }

        if (showSidebar && (sidebarVisible || sidebarWidth > 0.dp)) {
            val panelShape = RoundedCornerShape(30.dp)
            val showExpandedPanel = isSidebarExpanded || sidebarExpandProgress > 0.01f

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(sidebarWidth)
                    .padding(start = 14.dp, top = 16.dp, bottom = 12.dp, end = 8.dp)
                    .offset(x = sidebarSlideX + sidebarDeflateOffsetX, y = sidebarDeflateOffsetY)
                    .graphicsLayer(
                        alpha = sidebarSurfaceAlpha,
                        scaleX = sidebarBloomScale,
                        scaleY = sidebarBloomScale,
                        transformOrigin = TransformOrigin(0f, 0f)
                    )
                    .selectableGroup()
                    .onPreviewKeyEvent { keyEvent ->
                        if (!isSidebarExpanded || keyEvent.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                focusedDrawerIndex == 0
                            }

                            Key.DirectionDown -> {
                                focusedDrawerIndex == drawerItems.lastIndex
                            }

                            Key.DirectionRight, Key.Back -> {
                                pendingContentFocusTransfer = true
                                sidebarCollapsePending = true
                                true
                            }

                            else -> false
                        }
                    }
            ) {
                if (showExpandedPanel) {
                    ModernSidebarBlurPanel(
                        drawerItems = drawerItems,
                        selectedDrawerRoute = selectedDrawerRoute,
                        keepSidebarFocusDuringCollapse = keepSidebarFocusDuringCollapse,
                        sidebarLabelAlpha = sidebarLabelAlpha,
                        sidebarIconScale = sidebarIconScale,
                        sidebarExpandProgress = sidebarExpandProgress,
                        isSidebarExpanded = isSidebarExpanded,
                        sidebarCollapsePending = sidebarCollapsePending,
                        blurEnabled = modernSidebarBlurEnabled,
                        sidebarHazeState = sidebarHazeState,
                        panelShape = panelShape,
                        drawerItemFocusRequesters = drawerItemFocusRequesters,
                        onDrawerItemFocused = { focusedDrawerIndex = it },
                        onDrawerItemClick = { targetRoute ->
                            navigateToDrawerRoute(
                                navController = navController,
                                currentRoute = currentRoute,
                                targetRoute = targetRoute
                            )
                            pendingContentFocusTransfer = true
                            sidebarCollapsePending = true
                        }
                    )
                }
            }

            if (!sidebarCollapsed) {
                CollapsedSidebarPill(
                    label = selectedDrawerItem.label,
                    icon = selectedDrawerItem.icon,
                    hazeState = sidebarHazeState,
                    blurEnabled = modernSidebarBlurEnabled,
                    iconOnly = isCollapsedPillIconOnly,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = 40.dp + sidebarSlideX + sidebarDeflateOffsetX,
                            y = 16.dp + sidebarDeflateOffsetY
                        )
                        .graphicsLayer(
                            alpha = 1f - sidebarExpandProgress,
                            scaleX = 0.9f + (0.1f * (1f - sidebarExpandProgress)),
                            scaleY = 0.9f + (0.1f * (1f - sidebarExpandProgress)),
                            transformOrigin = TransformOrigin(0f, 0f)
                        ),
                    onExpand = {
                        isSidebarExpanded = true
                        sidebarCollapsePending = false
                        pendingSidebarFocusRequest = true
                        isCollapsedPillIconOnly = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CollapsedSidebarPill(
    label: String,
    icon: ImageVector,
    hazeState: HazeState,
    blurEnabled: Boolean,
    iconOnly: Boolean,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit
) {
    val pillShape = RoundedCornerShape(999.dp)
    val innerBlurShape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .focusProperties { canFocus = false }
            .animateContentSize()
            .clickable(onClick = onExpand)
            .padding(horizontal = 1.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.25.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_chevron_compact_left),
            contentDescription = "Expand sidebar",
            modifier = Modifier
                .width(8.5.dp)
                .height(16.dp)
                .offset(y = (-0.5).dp)
        )

        Box(
            modifier = Modifier
                .height(44.dp)
                .graphicsLayer {
                    shape = pillShape
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .clip(pillShape)
                .background(
                    brush = if (blurEnabled) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xD1424851),
                                Color(0xC73B4149)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                NuvioColors.BackgroundElevated,
                                NuvioColors.BackgroundCard
                            )
                        )
                    },
                    shape = pillShape
                )
                .border(
                    width = 1.dp,
                    color = if (blurEnabled) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        NuvioColors.Border.copy(alpha = 0.9f)
                    },
                    shape = pillShape
                )
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 2.25.dp, top = 2.25.dp, end = 5.dp, bottom = 2.25.dp)
                    .graphicsLayer {
                        shape = innerBlurShape
                        clip = true
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .clip(innerBlurShape)
                    .then(
                        if (blurEnabled) {
                            Modifier.hazeChild(
                                state = hazeState,
                                shape = innerBlurShape,
                                tint = Color.Unspecified,
                                blurRadius = 3.dp,
                                noiseFactor = 0f
                            )
                        } else {
                            Modifier
                        }
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .padding(start = 5.dp, end = if (iconOnly) 6.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (iconOnly) 0.dp else 9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F555E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .offset(y = (-0.5).dp)
                    )
                }

                if (!iconOnly) {
                    Text(
                        text = label,
                        color = Color.White,
                        style = androidx.tv.material3.MaterialTheme.typography.titleLarge.copy(
                            lineHeight = 30.sp
                        ),
                        modifier = Modifier.offset(y = (-0.5).dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun navigateToDrawerRoute(
    navController: NavHostController,
    currentRoute: String?,
    targetRoute: String
) {
    if (currentRoute == targetRoute) {
        return
    }
    navController.navigate(targetRoute) {
        popUpTo(navController.graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun isBlockedContentKey(key: Key): Boolean {
    return key == Key.DirectionUp ||
        key == Key.DirectionDown ||
        key == Key.DirectionLeft ||
        key == Key.DirectionRight ||
        key == Key.DirectionCenter ||
        key == Key.Enter
}
