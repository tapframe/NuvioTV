package com.nuvio.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import androidx.tv.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.ThemeDataStore
import com.nuvio.tv.domain.model.AppTheme
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.updater.UpdateViewModel
import com.nuvio.tv.updater.ui.UpdatePromptDialog
import androidx.tv.material3.NavigationDrawerItemDefaults
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
                    // Wait for DataStore to emit before rendering to avoid flickering
                    val layoutChosen = hasChosenLayout ?: return@Surface

                    val sidebarCollapsed by layoutPreferenceDataStore.sidebarCollapsedByDefault.collectAsState(initial = false)
                    val selectedLayout by layoutPreferenceDataStore.selectedLayout.collectAsState(initial = HomeLayout.CLASSIC)

                    val updateViewModel: UpdateViewModel = hiltViewModel(this@MainActivity)
                    val updateState by updateViewModel.uiState.collectAsState()

                    val startDestination = if (!layoutChosen) {
                        Screen.LayoutSelection.route
                    } else if (selectedLayout == HomeLayout.IMMERSIVE) {
                        Screen.Immersive.route
                    } else {
                        Screen.Home.route
                    }
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                    val rootRoutes = remember {
                        setOf(
                            Screen.Home.route,
                            Screen.Immersive.route,
                            Screen.Search.route,
                            Screen.Library.route,
                            Screen.Settings.route,
                            Screen.AddonManager.route
                        )
                    }

                    LaunchedEffect(currentRoute) {
                        drawerState.setValue(DrawerValue.Closed)
                    }

                    BackHandler(enabled = currentRoute in rootRoutes && drawerState.currentValue == DrawerValue.Closed) {
                        drawerState.setValue(DrawerValue.Open)
                    }

                    val homeRoute = if (selectedLayout == HomeLayout.IMMERSIVE) Screen.Immersive.route else Screen.Home.route

                    val drawerItems = remember(homeRoute) {
                        listOf(
                            homeRoute to ("Home" to Icons.Filled.Home),
                            Screen.Search.route to ("Search" to Icons.Filled.Search),
                            Screen.Library.route to ("Library" to Icons.Filled.Bookmark),
                            Screen.AddonManager.route to ("Addons" to Icons.Filled.Extension),
                            Screen.Settings.route to ("Settings" to Icons.Filled.Settings)
                        )
                    }

                    val showSidebar = currentRoute in rootRoutes

                    val closedDrawerWidth = if (sidebarCollapsed || currentRoute == Screen.Immersive.route) 0.dp else 72.dp
                    val openDrawerWidth = 260.dp

                    val focusManager = LocalFocusManager.current

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
                                            if (keyEvent.key == Key.DirectionRight &&
                                                keyEvent.type == KeyEventType.KeyDown
                                            ) {
                                                drawerState.setValue(DrawerValue.Closed)
                                                focusManager.moveFocus(FocusDirection.Right)
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                ) {
                                    if (drawerValue == DrawerValue.Open) {
                                        Image(
                                            painter = painterResource(id = R.drawable.nuviotv_logo),
                                            contentDescription = "NuvioTV",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .padding(top = 12.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = R.drawable.nuvio_n),
                                            contentDescription = "Nuvio",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .padding(top = 12.dp),
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
                                        drawerItems.forEach { (route, item) ->
                                            val (label, icon) = item
                                            NavigationDrawerItem(
                                                selected = currentRoute == route,
                                                onClick = {
                                                    if (currentRoute != route) {
                                                        navController.navigate(route) {
                                                            popUpTo(navController.graph.startDestinationId) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                    drawerState.setValue(DrawerValue.Closed)
                                                    focusManager.moveFocus(FocusDirection.Right)
                                                },
                                                colors = itemColors,
                                                leadingContent = {
                                                    Icon(imageVector = icon, contentDescription = null)
                                                }
                                            ) {
                                                if (drawerValue == DrawerValue.Open) {
                                                    Text(label)
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
                        Box(modifier = Modifier.fillMaxSize().padding(start = contentStartPadding)) {
                            NuvioNavHost(navController = navController, startDestination = startDestination)
                        }
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
