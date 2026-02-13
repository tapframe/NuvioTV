package com.nuvio.tv.ui.screens.detail

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.theme.NuvioTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.platform.LocalContext
import coil.decode.SvgDecoder
import coil.request.ImageRequest

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroContentSection(
    meta: Meta,
    nextEpisode: Video?,
    nextToWatch: NextToWatch?,
    onPlayClick: () -> Unit,
    isInLibrary: Boolean,
    onToggleLibrary: () -> Unit,
    onLibraryLongPress: () -> Unit,
    isMovieWatched: Boolean,
    isMovieWatchedPending: Boolean,
    onToggleMovieWatched: () -> Unit,
    isTrailerPlaying: Boolean = false,
    playButtonFocusRequester: FocusRequester? = null,
    restorePlayFocusToken: Int = 0,
    onPlayFocusRestored: () -> Unit = {}
) {
    var isDescriptionExpanded by remember(meta.id) { mutableStateOf(false) }
    var descriptionHasOverflow by remember(meta.id) { mutableStateOf(false) }

    // Animate logo properties for trailer mode
    val logoHeight by animateDpAsState(
        targetValue = if (isTrailerPlaying) 60.dp else 100.dp,
        animationSpec = tween(600),
        label = "logoHeight"
    )
    val logoBottomPadding by animateDpAsState(
        targetValue = if (isTrailerPlaying) 24.dp else 16.dp,
        animationSpec = tween(600),
        label = "logoPadding"
    )
    val logoMaxWidth by animateFloatAsState(
        targetValue = if (isTrailerPlaying) 0.25f else 0.4f,
        animationSpec = tween(600),
        label = "logoWidth"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(600))
                .padding(start = 48.dp, end = 48.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Logo/Title — always visible during trailer, animates size
            if (meta.logo != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(meta.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = meta.name,
                    modifier = Modifier
                        .height(logoHeight)
                        .fillMaxWidth(logoMaxWidth)
                        .padding(bottom = logoBottomPadding),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                // Text title hides entirely during trailer
                AnimatedVisibility(
                    visible = !isTrailerPlaying,
                    enter = fadeIn(tween(400)),
                    exit = fadeOut(tween(400))
                ) {
                    Text(
                        text = meta.name,
                        style = MaterialTheme.typography.displayMedium,
                        color = NuvioColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // "Press back to exit" hint during trailer
            AnimatedVisibility(
                visible = isTrailerPlaying,
                enter = fadeIn(tween(600)),
                exit = fadeOut(tween(300))
            ) {
                Text(
                    text = "Press back to exit trailer",
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Everything below the logo fades out during trailer
            AnimatedVisibility(
                visible = !isTrailerPlaying,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(400))
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayButton(
                            text = nextToWatch?.displayText ?: when {
                                nextEpisode != null -> "Play S${nextEpisode.season}, E${nextEpisode.episode}"
                                else -> "Play"
                            },
                            onClick = onPlayClick,
                            focusRequester = playButtonFocusRequester,
                            restoreFocusToken = restorePlayFocusToken,
                            onFocusRestored = onPlayFocusRestored
                        )

                        ActionIconButton(
                            icon = if (isInLibrary) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (isInLibrary) "Remove from library" else "Add to library",
                            onClick = onToggleLibrary,
                            onLongPress = onLibraryLongPress
                        )

                        if (meta.apiType == "movie") {
                            ActionIconButton(
                                icon = if (isMovieWatched) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                },
                                contentDescription = if (isMovieWatched) {
                                    "Mark as unwatched"
                                } else {
                                    "Mark as watched"
                                },
                                onClick = onToggleMovieWatched,
                                enabled = !isMovieWatchedPending,
                                selected = isMovieWatched,
                                selectedContainerColor = Color.White,
                                selectedContentColor = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Director/Writer line above description
                    val directorLine = meta.director.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    val writerLine = meta.writer.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    val creditLine = if (!directorLine.isNullOrBlank()) {
                        "Director: $directorLine"
                    } else if (!writerLine.isNullOrBlank()) {
                        "Writer: $writerLine"
                    } else {
                        null
                    }

                    if (!creditLine.isNullOrBlank()) {
                        Text(
                            text = creditLine,
                            style = MaterialTheme.typography.labelLarge,
                            color = NuvioTheme.extendedColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.6f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Always show series/movie description, not episode description
                    if (meta.description != null) {
                        Text(
                            text = meta.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioColors.TextPrimary,
                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                if (!isDescriptionExpanded) {
                                    descriptionHasOverflow = result.hasVisualOverflow
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .padding(bottom = 12.dp)
                        )

                        if (descriptionHasOverflow || isDescriptionExpanded) {
                            Button(
                                onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                                modifier = Modifier
                                    .height(34.dp)
                                    .padding(bottom = 12.dp),
                                colors = ButtonDefaults.colors(
                                    containerColor = NuvioColors.BackgroundElevated,
                                    focusedContainerColor = NuvioColors.FocusBackground,
                                    contentColor = NuvioColors.TextPrimary,
                                    focusedContentColor = NuvioColors.TextPrimary
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(20.dp)),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, NuvioColors.FocusRing),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (isDescriptionExpanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    MetaInfoRow(meta = meta)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun PlayButton(
    text: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    restoreFocusToken: Int = 0,
    onFocusRestored: () -> Unit = {}
) {
    LaunchedEffect(restoreFocusToken) {
        if (restoreFocusToken > 0 && focusRequester != null) {
            focusRequester.requestFocusAfterFrames()
        }
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused) {
                    onFocusRestored()
                }
            }
            .focusProperties { up = FocusRequester.Cancel },
        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.White,
            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
            contentColor = androidx.compose.ui.graphics.Color.Black,
            focusedContentColor = androidx.compose.ui.graphics.Color.Black
        ),
        shape = ButtonDefaults.shape(
            shape = RoundedCornerShape(32.dp)
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(32.dp)
            )
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    selectedContainerColor: Color = Color(0xFF7CFF9B),
    selectedContentColor: Color = Color.Black
) {
    var longPressTriggered by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            if (longPressTriggered) {
                longPressTriggered = false
            } else {
                onClick()
            }
        },
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (onLongPress != null && native.action == AndroidKeyEvent.ACTION_DOWN) {
                    if (native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                        longPressTriggered = true
                        onLongPress()
                        return@onPreviewKeyEvent true
                    }

                    val isSelectKey = native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                        native.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                    if ((native.isLongPress || native.repeatCount > 0) && isSelectKey) {
                        longPressTriggered = true
                        onLongPress()
                        return@onPreviewKeyEvent true
                    }
                }

                if (native.action == AndroidKeyEvent.ACTION_UP && longPressTriggered) {
                    val isSelectKey = native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                        native.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                        native.keyCode == AndroidKeyEvent.KEYCODE_MENU
                    if (isSelectKey) return@onPreviewKeyEvent true
                }
                false
            }
            .focusProperties { up = FocusRequester.Cancel },
        colors = IconButtonDefaults.colors(
            containerColor = if (selected) selectedContainerColor else NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.Secondary,
            contentColor = if (selected) selectedContentColor else NuvioColors.TextPrimary,
            focusedContentColor = NuvioColors.OnPrimary
        ),
        border = IconButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = CircleShape
            )
        ),
        shape = IconButtonDefaults.shape(
            shape = CircleShape
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetaInfoRow(meta: Meta) {
    val genresText = remember(meta.genres) { meta.genres.joinToString(" • ") }
    val runtimeText = remember(meta.runtime) { meta.runtime?.let { formatRuntime(it) } }
    val yearText = remember(meta.releaseInfo) {
        meta.releaseInfo?.split("-")?.firstOrNull() ?: meta.releaseInfo
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Primary row: Genres, Runtime, Release, Ratings
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show all genres
            if (meta.genres.isNotEmpty()) {
                Text(
                    text = genresText,
                    style = MaterialTheme.typography.labelLarge,
                    color = NuvioTheme.extendedColors.textSecondary
                )
                MetaInfoDivider()
            }

            // Runtime
            runtimeText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = NuvioTheme.extendedColors.textSecondary
                )
                MetaInfoDivider()
            }

            yearText?.let { year ->
                Text(
                    text = year,
                    style = MaterialTheme.typography.labelLarge,
                    color = NuvioTheme.extendedColors.textSecondary
                )
                MetaInfoDivider()
            }

            meta.imdbRating?.let { rating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val context = LocalContext.current
                    val imdbModel = remember {
                        ImageRequest.Builder(context)
                            .data(com.nuvio.tv.R.raw.imdb_logo_2016)
                            .decoderFactory(SvgDecoder.Factory())
                            .build()
                    }
                    AsyncImage(
                        model = imdbModel,
                        contentDescription = "Rating",
                        modifier = Modifier.size(30.dp),
                        contentScale = ContentScale.Fit
                    )
                    val ratingText = remember(rating) { String.format("%.1f", rating) }
                    Text(
                        text = ratingText,
                        style = MaterialTheme.typography.labelLarge,
                        color = NuvioTheme.extendedColors.textSecondary
                    )
                }
            }
        }

        // Secondary row: Country, Language
        val hasSecondaryInfo = meta.country != null || meta.language != null
        if (hasSecondaryInfo) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                meta.country?.let { country ->
                    Text(
                        text = country,
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioTheme.extendedColors.textTertiary
                    )
                }

                if (meta.country != null && meta.language != null) {
                    MetaInfoDivider()
                }

                meta.language?.let { language ->
                    Text(
                        text = language.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioTheme.extendedColors.textTertiary
                    )
                }
            }
        }
    }
}

private fun formatRuntime(runtime: String): String {
    val minutes = runtime.filter { it.isDigit() }.toIntOrNull() ?: return runtime
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
    } else {
        "${minutes}m"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetaInfoDivider() {
    Text(
        text = "•",
        style = MaterialTheme.typography.labelLarge,
        color = NuvioTheme.extendedColors.textTertiary
    )
}
