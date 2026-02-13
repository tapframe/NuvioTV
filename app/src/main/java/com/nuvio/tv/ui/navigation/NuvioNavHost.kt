package com.nuvio.tv.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nuvio.tv.ui.screens.CatalogSeeAllScreen
import com.nuvio.tv.ui.screens.LayoutSelectionScreen
import com.nuvio.tv.ui.screens.detail.MetaDetailsScreen
import com.nuvio.tv.ui.screens.home.HomeScreen
import com.nuvio.tv.ui.screens.addon.AddonManagerScreen
import com.nuvio.tv.ui.screens.addon.CatalogOrderScreen
import com.nuvio.tv.ui.screens.library.LibraryScreen
import com.nuvio.tv.ui.screens.player.PlayerScreen
import com.nuvio.tv.ui.screens.plugin.PluginScreen
import com.nuvio.tv.ui.screens.search.SearchScreen
import com.nuvio.tv.ui.screens.settings.AboutScreen
import com.nuvio.tv.ui.screens.settings.LayoutSettingsScreen
import com.nuvio.tv.ui.screens.settings.PlaybackSettingsScreen
import com.nuvio.tv.ui.screens.settings.SettingsScreen
import com.nuvio.tv.ui.screens.settings.ThemeSettingsScreen
import com.nuvio.tv.ui.screens.settings.TraktScreen
import com.nuvio.tv.ui.screens.settings.TmdbSettingsScreen
import com.nuvio.tv.ui.screens.stream.StreamScreen
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem

@Composable
fun NuvioNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    hideBuiltInHeaders: Boolean = false
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(150)) },
        exitTransition = { fadeOut(animationSpec = tween(150)) },
        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
        popExitTransition = { fadeOut(animationSpec = tween(150)) }
    ) {
        composable(Screen.LayoutSelection.route) {
            LayoutSelectionScreen(
                onContinue = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.LayoutSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl))
                },
                onContinueWatchingClick = { item ->
                    val route = when (item) {
                        is ContinueWatchingItem.InProgress -> Screen.Stream.createRoute(
                            videoId = item.progress.videoId,
                            contentType = item.progress.contentType,
                            title = item.progress.name,
                            poster = item.progress.poster,
                            backdrop = item.progress.backdrop,
                            logo = item.progress.logo,
                            season = item.progress.season,
                            episode = item.progress.episode,
                            episodeName = item.progress.episodeTitle,
                            genres = null,
                            year = null,
                            contentId = item.progress.contentId,
                            contentName = item.progress.name,
                            runtime = null
                        )
                        is ContinueWatchingItem.NextUp -> Screen.Stream.createRoute(
                            videoId = item.info.videoId,
                            contentType = item.info.contentType,
                            title = item.info.name,
                            poster = item.info.poster,
                            backdrop = item.info.backdrop,
                            logo = item.info.logo,
                            season = item.info.season,
                            episode = item.info.episode,
                            episodeName = item.info.episodeTitle,
                            genres = null,
                            year = null,
                            contentId = item.info.contentId,
                            contentName = item.info.name,
                            runtime = null
                        )
                    }
                    navController.navigate(route)
                },
                onNavigateToCatalogSeeAll = { catalogId, addonId, type ->
                    navController.navigate(Screen.CatalogSeeAll.createRoute(catalogId, addonId, type))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("itemType") { type = NavType.StringType },
                navArgument("addonBaseUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            MetaDetailsScreen(
                onBackPress = { navController.popBackStack() },
                onPlayClick = { videoId, contentType, contentId, title, poster, backdrop, logo, season, episode, episodeName, genres, year, runtime ->
                    navController.navigate(
                        Screen.Stream.createRoute(
                            videoId = videoId,
                            contentType = contentType,
                            title = title,
                            poster = poster,
                            backdrop = backdrop,
                            logo = logo,
                            season = season,
                            episode = episode,
                            episodeName = episodeName,
                            genres = genres,
                            year = year,
                            contentId = contentId,
                            contentName = title,
                            runtime = runtime
                        )
                    )
                }
            )
        }

        composable(
            route = Screen.Stream.route,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("contentType") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("poster") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("backdrop") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("logo") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("season") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episodeName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("genres") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("year") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("runtime") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            StreamScreen(
                onBackPress = { navController.popBackStack() },
                onStreamSelected = { playbackInfo ->
                    playbackInfo.url?.let { url ->
                        navController.navigate(
                            Screen.Player.createRoute(
                                streamUrl = url,
                                title = playbackInfo.title,
                                streamName = playbackInfo.streamName,
                                year = playbackInfo.year,
                                headers = playbackInfo.headers,
                                contentId = playbackInfo.contentId,
                                contentType = playbackInfo.contentType,
                                contentName = playbackInfo.contentName,
                                poster = playbackInfo.poster,
                                backdrop = playbackInfo.backdrop,
                                logo = playbackInfo.logo,
                                videoId = playbackInfo.videoId,
                                season = playbackInfo.season,
                                episode = playbackInfo.episode,
                                episodeTitle = playbackInfo.episodeTitle
                            )
                        )
                    }
                }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("streamUrl") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("streamName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("year") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("headers") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentType") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("poster") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("backdrop") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("logo") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("videoId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("season") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episodeTitle") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            PlayerScreen(
                onBackPress = {
                    val returnedToStream = navController.popBackStack(Screen.Stream.route, inclusive = false)
                    if (!returnedToStream) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                showBuiltInHeader = !hideBuiltInHeaders,
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl))
                },
                onNavigateToSeeAll = { catalogId, addonId, type ->
                    navController.navigate(Screen.CatalogSeeAll.createRoute(catalogId, addonId, type))
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                showBuiltInHeader = !hideBuiltInHeaders,
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                showBuiltInHeader = !hideBuiltInHeaders,
                onNavigateToPlugins = { navController.navigate(Screen.Plugins.route) },
                onNavigateToTrakt = { navController.navigate(Screen.Trakt.route) }
            )
        }

        composable(Screen.Trakt.route) {
            TraktScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.TmdbSettings.route) {
            TmdbSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.ThemeSettings.route) {
            ThemeSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.PlaybackSettings.route) {
            PlaybackSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.AddonManager.route) {
            AddonManagerScreen(
                showBuiltInHeader = !hideBuiltInHeaders,
                onNavigateToCatalogOrder = { navController.navigate(Screen.CatalogOrder.route) }
            )
        }

        composable(Screen.CatalogOrder.route) {
            CatalogOrderScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.Plugins.route) {
            PluginScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.LayoutSettings.route) {
            LayoutSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CatalogSeeAll.route,
            arguments = listOf(
                navArgument("catalogId") { type = NavType.StringType },
                navArgument("addonId") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val catalogId = backStackEntry.arguments?.getString("catalogId") ?: ""
            val addonId = backStackEntry.arguments?.getString("addonId") ?: ""
            val type = backStackEntry.arguments?.getString("type") ?: ""
            CatalogSeeAllScreen(
                catalogId = catalogId,
                addonId = addonId,
                type = type,
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl))
                },
                onBackPress = { navController.popBackStack() }
            )
        }
    }
}
