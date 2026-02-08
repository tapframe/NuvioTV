package com.nuvio.tv.data.local

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_settings")

/**
 * Available subtitle languages
 */
data class SubtitleLanguage(
    val code: String,
    val name: String
)

val AVAILABLE_SUBTITLE_LANGUAGES = listOf(
    SubtitleLanguage("en", "English"),
    SubtitleLanguage("es", "Spanish"),
    SubtitleLanguage("fr", "French"),
    SubtitleLanguage("de", "German"),
    SubtitleLanguage("it", "Italian"),
    SubtitleLanguage("pt", "Portuguese"),
    SubtitleLanguage("ru", "Russian"),
    SubtitleLanguage("ja", "Japanese"),
    SubtitleLanguage("ko", "Korean"),
    SubtitleLanguage("zh", "Chinese"),
    SubtitleLanguage("ar", "Arabic"),
    SubtitleLanguage("hi", "Hindi"),
    SubtitleLanguage("tr", "Turkish"),
    SubtitleLanguage("pl", "Polish"),
    SubtitleLanguage("nl", "Dutch"),
    SubtitleLanguage("sv", "Swedish"),
    SubtitleLanguage("da", "Danish"),
    SubtitleLanguage("no", "Norwegian"),
    SubtitleLanguage("fi", "Finnish"),
    SubtitleLanguage("th", "Thai"),
    SubtitleLanguage("vi", "Vietnamese"),
    SubtitleLanguage("id", "Indonesian"),
    SubtitleLanguage("ms", "Malay"),
    SubtitleLanguage("he", "Hebrew"),
    SubtitleLanguage("el", "Greek"),
    SubtitleLanguage("cs", "Czech"),
    SubtitleLanguage("hu", "Hungarian"),
    SubtitleLanguage("ro", "Romanian"),
    SubtitleLanguage("uk", "Ukrainian"),
    SubtitleLanguage("bg", "Bulgarian"),
    SubtitleLanguage("hr", "Croatian"),
    SubtitleLanguage("sk", "Slovak"),
    SubtitleLanguage("sl", "Slovenian"),
    SubtitleLanguage("sr", "Serbian"),
    SubtitleLanguage("ta", "Tamil"),
    SubtitleLanguage("te", "Telugu"),
    SubtitleLanguage("ml", "Malayalam"),
    SubtitleLanguage("bn", "Bengali"),
    SubtitleLanguage("mr", "Marathi"),
    SubtitleLanguage("gu", "Gujarati"),
    SubtitleLanguage("kn", "Kannada"),
    SubtitleLanguage("pa", "Punjabi")
)

/**
 * Data class representing subtitle style settings
 */
data class SubtitleStyleSettings(
    val preferredLanguage: String = "en",
    val secondaryPreferredLanguage: String? = null,
    val size: Int = 120, // Percentage (50-200)
    val verticalOffset: Int = 5, // Percentage from bottom (0-50)
    val bold: Boolean = false,
    val textColor: Int = Color.White.toArgb(),
    val backgroundColor: Int = Color.Transparent.toArgb(),
    val outlineEnabled: Boolean = true,
    val outlineColor: Int = Color.Black.toArgb(),
    val outlineWidth: Int = 2 // 1-5
)

/**
 * Data class representing buffer settings
 */
data class BufferSettings(
    val minBufferMs: Int = 50_000,
    val maxBufferMs: Int = 50_000,
    val bufferForPlaybackMs: Int = 3_000,
    val bufferForPlaybackAfterRebufferMs: Int = 9_000,
    val targetBufferSizeMb: Int = 0, // 0 = auto (calculated from available heap)
    val backBufferDurationMs: Int = 0,
    val useParallelConnections: Boolean = true
)

/**
 * Available audio language options
 */
object AudioLanguageOption {
    const val DEFAULT = "default"  // Use media file default
    const val DEVICE = "device"    // Use device locale
}

/**
 * Data class representing player settings
 */
data class PlayerSettings(
    val useLibass: Boolean = false,
    val libassRenderType: LibassRenderType = LibassRenderType.OVERLAY_OPEN_GL,
    val subtitleStyle: SubtitleStyleSettings = SubtitleStyleSettings(),
    val bufferSettings: BufferSettings = BufferSettings(),
    // Audio settings
    val decoderPriority: Int = 1, // EXTENSION_RENDERER_MODE_ON (0=off, 1=on, 2=prefer)
    val tunnelingEnabled: Boolean = false,
    val skipSilence: Boolean = false,
    val preferredAudioLanguage: String = AudioLanguageOption.DEVICE,
    val loadingOverlayEnabled: Boolean = true,
    val pauseOverlayEnabled: Boolean = true,
    // Display settings
    val frameRateMatching: Boolean = false
)

/**
 * Enum representing the different libass render types
 * Maps to io.github.peerless2012.ass.media.type.AssRenderType
 */
enum class LibassRenderType {
    CUES,              // Standard SubtitleView rendering (no animation support)
    EFFECTS_CANVAS,    // Effect-based Canvas rendering (supports animations)
    EFFECTS_OPEN_GL,   // Effect-based OpenGL rendering (supports animations, faster)
    OVERLAY_CANVAS,    // Overlay Canvas rendering (supports HDR)
    OVERLAY_OPEN_GL    // Overlay OpenGL rendering (supports HDR, recommended)
}

@Singleton
class PlayerSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.playerSettingsDataStore

    // Libass settings keys
    private val useLibassKey = booleanPreferencesKey("use_libass")
    private val libassRenderTypeKey = stringPreferencesKey("libass_render_type")
    
    // Audio settings keys
    private val decoderPriorityKey = intPreferencesKey("decoder_priority")
    private val tunnelingEnabledKey = booleanPreferencesKey("tunneling_enabled")
    private val skipSilenceKey = booleanPreferencesKey("skip_silence")
    private val preferredAudioLanguageKey = stringPreferencesKey("preferred_audio_language")
    private val loadingOverlayEnabledKey = booleanPreferencesKey("loading_overlay_enabled")
    private val pauseOverlayEnabledKey = booleanPreferencesKey("pause_overlay_enabled")
    private val frameRateMatchingKey = booleanPreferencesKey("frame_rate_matching")

    // Subtitle style settings keys
    private val subtitlePreferredLanguageKey = stringPreferencesKey("subtitle_preferred_language")
    private val subtitleSecondaryLanguageKey = stringPreferencesKey("subtitle_secondary_language")
    private val subtitleSizeKey = intPreferencesKey("subtitle_size")
    private val subtitleVerticalOffsetKey = intPreferencesKey("subtitle_vertical_offset")
    private val subtitleBoldKey = booleanPreferencesKey("subtitle_bold")
    private val subtitleTextColorKey = intPreferencesKey("subtitle_text_color")
    private val subtitleBackgroundColorKey = intPreferencesKey("subtitle_background_color")
    private val subtitleOutlineEnabledKey = booleanPreferencesKey("subtitle_outline_enabled")
    private val subtitleOutlineColorKey = intPreferencesKey("subtitle_outline_color")
    private val subtitleOutlineWidthKey = intPreferencesKey("subtitle_outline_width")

    // Buffer settings keys
    private val minBufferMsKey = intPreferencesKey("min_buffer_ms")
    private val maxBufferMsKey = intPreferencesKey("max_buffer_ms")
    private val bufferForPlaybackMsKey = intPreferencesKey("buffer_for_playback_ms")
    private val bufferForPlaybackAfterRebufferMsKey = intPreferencesKey("buffer_for_playback_after_rebuffer_ms")
    private val targetBufferSizeMbKey = intPreferencesKey("target_buffer_size_mb")
    private val backBufferDurationMsKey = intPreferencesKey("back_buffer_duration_ms")
    private val useParallelConnectionsKey = booleanPreferencesKey("use_parallel_connections")

    /**
     * Flow of current player settings
     */
    val playerSettings: Flow<PlayerSettings> = dataStore.data.map { prefs ->
        PlayerSettings(
            useLibass = prefs[useLibassKey] ?: false,
            libassRenderType = prefs[libassRenderTypeKey]?.let { 
                try { LibassRenderType.valueOf(it) } catch (e: Exception) { LibassRenderType.OVERLAY_OPEN_GL }
            } ?: LibassRenderType.OVERLAY_OPEN_GL,
            decoderPriority = prefs[decoderPriorityKey] ?: 1,
            tunnelingEnabled = prefs[tunnelingEnabledKey] ?: false,
            skipSilence = prefs[skipSilenceKey] ?: false,
            preferredAudioLanguage = prefs[preferredAudioLanguageKey] ?: AudioLanguageOption.DEVICE,
            loadingOverlayEnabled = prefs[loadingOverlayEnabledKey] ?: true,
            pauseOverlayEnabled = prefs[pauseOverlayEnabledKey] ?: true,
            frameRateMatching = prefs[frameRateMatchingKey] ?: false,
            subtitleStyle = SubtitleStyleSettings(
                preferredLanguage = prefs[subtitlePreferredLanguageKey] ?: "en",
                secondaryPreferredLanguage = prefs[subtitleSecondaryLanguageKey],
                size = prefs[subtitleSizeKey] ?: 100,
                verticalOffset = prefs[subtitleVerticalOffsetKey] ?: 5,
                bold = prefs[subtitleBoldKey] ?: false,
                textColor = prefs[subtitleTextColorKey] ?: Color.White.toArgb(),
                backgroundColor = prefs[subtitleBackgroundColorKey] ?: Color.Transparent.toArgb(),
                outlineEnabled = prefs[subtitleOutlineEnabledKey] ?: true,
                outlineColor = prefs[subtitleOutlineColorKey] ?: Color.Black.toArgb(),
                outlineWidth = prefs[subtitleOutlineWidthKey] ?: 2
            ),
            bufferSettings = BufferSettings(
                minBufferMs = prefs[minBufferMsKey] ?: 50_000,
                maxBufferMs = prefs[maxBufferMsKey] ?: 50_000,
                bufferForPlaybackMs = prefs[bufferForPlaybackMsKey] ?: 3_000,
                bufferForPlaybackAfterRebufferMs = prefs[bufferForPlaybackAfterRebufferMsKey] ?: 9_000,
                targetBufferSizeMb = prefs[targetBufferSizeMbKey] ?: 0,
                backBufferDurationMs = prefs[backBufferDurationMsKey] ?: 0,
                useParallelConnections = prefs[useParallelConnectionsKey] ?: false
            )
        )
    }

    /**
     * Flow for just the libass toggle
     */
    val useLibass: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[useLibassKey] ?: false
    }

    /**
     * Flow for the libass render type
     */
    val libassRenderType: Flow<LibassRenderType> = dataStore.data.map { prefs ->
        prefs[libassRenderTypeKey]?.let { 
            try { LibassRenderType.valueOf(it) } catch (e: Exception) { LibassRenderType.OVERLAY_OPEN_GL }
        } ?: LibassRenderType.OVERLAY_OPEN_GL
    }

    // Audio settings setters

    suspend fun setDecoderPriority(priority: Int) {
        dataStore.edit { prefs ->
            prefs[decoderPriorityKey] = priority.coerceIn(0, 2)
        }
    }

    suspend fun setTunnelingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[tunnelingEnabledKey] = enabled
        }
    }

    suspend fun setSkipSilence(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[skipSilenceKey] = enabled
        }
    }

    suspend fun setPreferredAudioLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[preferredAudioLanguageKey] = language
        }
    }

    suspend fun setPauseOverlayEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[pauseOverlayEnabledKey] = enabled
        }
    }

    suspend fun setLoadingOverlayEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[loadingOverlayEnabledKey] = enabled
        }
    }

    suspend fun setFrameRateMatching(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[frameRateMatchingKey] = enabled
        }
    }

    /**
     * Set whether to use libass for ASS/SSA subtitle rendering
     */
    suspend fun setUseLibass(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[useLibassKey] = enabled
        }
    }

    /**
     * Set the libass render type
     */
    suspend fun setLibassRenderType(renderType: LibassRenderType) {
        dataStore.edit { prefs ->
            prefs[libassRenderTypeKey] = renderType.name
        }
    }
    
    // Subtitle style settings functions
    
    suspend fun setSubtitlePreferredLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[subtitlePreferredLanguageKey] = language
        }
    }
    
    suspend fun setSubtitleSecondaryLanguage(language: String?) {
        dataStore.edit { prefs ->
            if (language != null) {
                prefs[subtitleSecondaryLanguageKey] = language
            } else {
                prefs.remove(subtitleSecondaryLanguageKey)
            }
        }
    }
    
    suspend fun setSubtitleSize(size: Int) {
        dataStore.edit { prefs ->
            prefs[subtitleSizeKey] = size.coerceIn(50, 200)
        }
    }
    
    suspend fun setSubtitleVerticalOffset(offset: Int) {
        dataStore.edit { prefs ->
            prefs[subtitleVerticalOffsetKey] = offset.coerceIn(0, 50)
        }
    }
    
    suspend fun setSubtitleBold(bold: Boolean) {
        dataStore.edit { prefs ->
            prefs[subtitleBoldKey] = bold
        }
    }
    
    suspend fun setSubtitleTextColor(color: Int) {
        dataStore.edit { prefs ->
            prefs[subtitleTextColorKey] = color
        }
    }
    
    suspend fun setSubtitleBackgroundColor(color: Int) {
        dataStore.edit { prefs ->
            prefs[subtitleBackgroundColorKey] = color
        }
    }
    
    suspend fun setSubtitleOutlineEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[subtitleOutlineEnabledKey] = enabled
        }
    }
    
    suspend fun setSubtitleOutlineColor(color: Int) {
        dataStore.edit { prefs ->
            prefs[subtitleOutlineColorKey] = color
        }
    }
    
    suspend fun setSubtitleOutlineWidth(width: Int) {
        dataStore.edit { prefs ->
            prefs[subtitleOutlineWidthKey] = width.coerceIn(1, 5)
        }
    }

    // Buffer settings functions

    fun calculateDefaultBufferSizeMb(): Int {
        val maxHeapBytes = Runtime.getRuntime().maxMemory()
        val thirtyPercent = (maxHeapBytes * 0.30).toLong() / (1024 * 1024)
        return thirtyPercent.toInt().coerceIn(75, 300)
    }

    suspend fun setBufferMinBufferMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[minBufferMsKey] = ms.coerceIn(5_000, 120_000)
        }
    }

    suspend fun setBufferMaxBufferMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[maxBufferMsKey] = ms.coerceIn(5_000, 120_000)
        }
    }

    suspend fun setBufferForPlaybackMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[bufferForPlaybackMsKey] = ms.coerceIn(1_000, 30_000)
        }
    }

    suspend fun setBufferForPlaybackAfterRebufferMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[bufferForPlaybackAfterRebufferMsKey] = ms.coerceIn(1_000, 60_000)
        }
    }

    suspend fun setBufferTargetSizeMb(mb: Int) {
        dataStore.edit { prefs ->
            prefs[targetBufferSizeMbKey] = mb.coerceAtLeast(0)
        }
    }

    suspend fun setBufferBackBufferDurationMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[backBufferDurationMsKey] = ms.coerceIn(0, 120_000)
        }
    }

    suspend fun setUseParallelConnections(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[useParallelConnectionsKey] = enabled
        }
    }
}
