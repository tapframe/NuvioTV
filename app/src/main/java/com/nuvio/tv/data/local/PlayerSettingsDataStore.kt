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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    val verticalOffset: Int = 5, // Percentage from bottom (-20 to 50)
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
    val bufferForPlaybackMs: Int = 2_500,
    val bufferForPlaybackAfterRebufferMs: Int = 5_000,
    val targetBufferSizeMb: Int = 0, // 0 = ExoPlayer default
    val backBufferDurationMs: Int = 0,
    val retainBackBufferFromKeyframe: Boolean = false
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
    val playerPreference: PlayerPreference = PlayerPreference.INTERNAL,
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
    val skipIntroEnabled: Boolean = true,
    // Dolby Vision Profile 7 → HEVC fallback (requires forked ExoPlayer)
    val mapDV7ToHevc: Boolean = false,
    // Display settings
    val frameRateMatchingMode: FrameRateMatchingMode = FrameRateMatchingMode.OFF,
    // Stream selection settings
    val streamAutoPlayMode: StreamAutoPlayMode = StreamAutoPlayMode.MANUAL,
    val streamAutoPlaySource: StreamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES,
    val streamAutoPlaySelectedAddons: Set<String> = emptySet(),
    val streamAutoPlaySelectedPlugins: Set<String> = emptySet(),
    val streamAutoPlayRegex: String = "",
    val streamReuseLastLinkEnabled: Boolean = false,
    val streamReuseLastLinkCacheHours: Int = 24
)

enum class StreamAutoPlayMode {
    MANUAL,
    FIRST_STREAM,
    REGEX_MATCH
}

enum class StreamAutoPlaySource {
    ALL_SOURCES,
    INSTALLED_ADDONS_ONLY,
    ENABLED_PLUGINS_ONLY
}

enum class FrameRateMatchingMode {
    OFF,
    START,
    START_STOP
enum class PlayerPreference {
    INTERNAL,
    EXTERNAL,
    ASK_EVERY_TIME
}

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
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Player preference key
    private val playerPreferenceKey = stringPreferencesKey("player_preference")

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
    private val skipIntroEnabledKey = booleanPreferencesKey("skip_intro_enabled")
    private val mapDV7ToHevcKey = booleanPreferencesKey("map_dv7_to_hevc")
    private val frameRateMatchingKey = booleanPreferencesKey("frame_rate_matching")
    private val frameRateMatchingModeKey = stringPreferencesKey("frame_rate_matching_mode")
    private val streamAutoPlayModeKey = stringPreferencesKey("stream_auto_play_mode")
    private val streamAutoPlaySourceKey = stringPreferencesKey("stream_auto_play_source")
    private val streamAutoPlaySelectedAddonsKey = stringSetPreferencesKey("stream_auto_play_selected_addons")
    private val streamAutoPlaySelectedPluginsKey = stringSetPreferencesKey("stream_auto_play_selected_plugins")
    private val streamAutoPlayRegexKey = stringPreferencesKey("stream_auto_play_regex")
    private val streamReuseLastLinkEnabledKey = booleanPreferencesKey("stream_reuse_last_link_enabled")
    private val streamReuseLastLinkCacheHoursKey = intPreferencesKey("stream_reuse_last_link_cache_hours")

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
    private val retainBackBufferFromKeyframeKey = booleanPreferencesKey("retain_back_buffer_from_keyframe")

    private val migrationLoadControlDefaultsAlignedDoneKey = booleanPreferencesKey("migration_load_control_defaults_aligned_done")

    init {
        ioScope.launch {
            dataStore.edit { prefs ->
                val loadControlMigrated = prefs[migrationLoadControlDefaultsAlignedDoneKey] ?: false
                if (!loadControlMigrated) {
                    val currentMin = prefs[minBufferMsKey]
                    val currentMax = prefs[maxBufferMsKey]

                    val legacyDefaultsDetected = (currentMin == null && currentMax == null) ||
                        (currentMin == 15_000 && currentMax == 25_000)

                    if (legacyDefaultsDetected) {
                        prefs[minBufferMsKey] = 50_000
                        prefs[maxBufferMsKey] = 50_000
                    }

                    prefs[migrationLoadControlDefaultsAlignedDoneKey] = true
                }

                val min = prefs[minBufferMsKey]
                val max = prefs[maxBufferMsKey]
                if (min != null && max != null && max < min) {
                    prefs[maxBufferMsKey] = min
                }
            }
        }
    }

    /**
     * Flow of current player settings
     */
    val playerSettings: Flow<PlayerSettings> = dataStore.data.map { prefs ->
        PlayerSettings(
            playerPreference = prefs[playerPreferenceKey]?.let {
                runCatching { PlayerPreference.valueOf(it) }.getOrDefault(PlayerPreference.INTERNAL)
            } ?: PlayerPreference.INTERNAL,
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
            skipIntroEnabled = prefs[skipIntroEnabledKey] ?: true,
            mapDV7ToHevc = prefs[mapDV7ToHevcKey] ?: false,
            frameRateMatchingMode = prefs[frameRateMatchingModeKey]?.let {
                runCatching { FrameRateMatchingMode.valueOf(it) }.getOrNull()
            } ?: if (prefs[frameRateMatchingKey] == true) {
                FrameRateMatchingMode.START_STOP
            } else {
                FrameRateMatchingMode.OFF
            },
            streamAutoPlayMode = prefs[streamAutoPlayModeKey]?.let {
                runCatching { StreamAutoPlayMode.valueOf(it) }.getOrDefault(StreamAutoPlayMode.MANUAL)
            } ?: StreamAutoPlayMode.MANUAL,
            streamAutoPlaySource = prefs[streamAutoPlaySourceKey]?.let {
                runCatching { StreamAutoPlaySource.valueOf(it) }.getOrDefault(StreamAutoPlaySource.ALL_SOURCES)
            } ?: StreamAutoPlaySource.ALL_SOURCES,
            streamAutoPlaySelectedAddons = prefs[streamAutoPlaySelectedAddonsKey] ?: emptySet(),
            streamAutoPlaySelectedPlugins = prefs[streamAutoPlaySelectedPluginsKey] ?: emptySet(),
            streamAutoPlayRegex = prefs[streamAutoPlayRegexKey] ?: "",
            streamReuseLastLinkEnabled = prefs[streamReuseLastLinkEnabledKey] ?: false,
            streamReuseLastLinkCacheHours = (prefs[streamReuseLastLinkCacheHoursKey] ?: 24).coerceIn(1, 168),
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
                bufferForPlaybackMs = prefs[bufferForPlaybackMsKey] ?: 2_500,
                bufferForPlaybackAfterRebufferMs = prefs[bufferForPlaybackAfterRebufferMsKey] ?: 5_000,
                targetBufferSizeMb = prefs[targetBufferSizeMbKey] ?: 0,
                backBufferDurationMs = prefs[backBufferDurationMsKey] ?: 0,
                retainBackBufferFromKeyframe = prefs[retainBackBufferFromKeyframeKey] ?: false
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

    // Player preference setter

    suspend fun setPlayerPreference(preference: PlayerPreference) {
        dataStore.edit { prefs ->
            prefs[playerPreferenceKey] = preference.name
        }
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

    suspend fun setSkipIntroEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[skipIntroEnabledKey] = enabled
        }
    }

    suspend fun setLoadingOverlayEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[loadingOverlayEnabledKey] = enabled
        }
    }

    suspend fun setFrameRateMatchingMode(mode: FrameRateMatchingMode) {
        dataStore.edit { prefs ->
            prefs[frameRateMatchingModeKey] = mode.name
            prefs[frameRateMatchingKey] = mode != FrameRateMatchingMode.OFF
        }
    }

    suspend fun setStreamAutoPlayMode(mode: StreamAutoPlayMode) {
        dataStore.edit { prefs ->
            prefs[streamAutoPlayModeKey] = mode.name
        }
    }

    suspend fun setStreamAutoPlaySource(source: StreamAutoPlaySource) {
        dataStore.edit { prefs ->
            prefs[streamAutoPlaySourceKey] = source.name
        }
    }

    suspend fun setStreamAutoPlaySelectedAddons(addons: Set<String>) {
        dataStore.edit { prefs ->
            prefs[streamAutoPlaySelectedAddonsKey] = addons
        }
    }

    suspend fun setStreamAutoPlaySelectedPlugins(plugins: Set<String>) {
        dataStore.edit { prefs ->
            prefs[streamAutoPlaySelectedPluginsKey] = plugins
        }
    }

    suspend fun setStreamAutoPlayRegex(regex: String) {
        dataStore.edit { prefs ->
            prefs[streamAutoPlayRegexKey] = regex.trim()
        }
    }

    suspend fun setStreamReuseLastLinkEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[streamReuseLastLinkEnabledKey] = enabled
        }
    }

    suspend fun setStreamReuseLastLinkCacheHours(hours: Int) {
        dataStore.edit { prefs ->
            prefs[streamReuseLastLinkCacheHoursKey] = hours.coerceIn(1, 168)
        }
    }

    suspend fun setMapDV7ToHevc(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[mapDV7ToHevcKey] = enabled
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
            prefs[subtitleVerticalOffsetKey] = offset.coerceIn(-20, 50)
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

    suspend fun setBufferMinBufferMs(ms: Int) {
        dataStore.edit { prefs ->
            val newMin = ms.coerceIn(5_000, 120_000)
            prefs[minBufferMsKey] = newMin
            val currentMax = prefs[maxBufferMsKey] ?: 50_000
            if (currentMax < newMin) {
                prefs[maxBufferMsKey] = newMin
            }
        }
    }

    suspend fun setBufferMaxBufferMs(ms: Int) {
        dataStore.edit { prefs ->
            val currentMin = prefs[minBufferMsKey] ?: 50_000
            prefs[maxBufferMsKey] = ms.coerceIn(currentMin, 120_000)
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

    suspend fun setBufferRetainBackBufferFromKeyframe(retain: Boolean) {
        dataStore.edit { prefs ->
            prefs[retainBackBufferFromKeyframeKey] = retain
        }
    }
}
