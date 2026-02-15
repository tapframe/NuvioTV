package com.nuvio.tv.core.player

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaExtractor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Auto frame rate matching utility.
 * Switches the display refresh rate to match the video frame rate for judder-free playback.
 * Inspired by Kodi's Android implementation (SetRefreshRate/SetDisplayMode) and Just (Video) Player:
 * - DisplayManager listener + timeout to resume playback even if no display-changed callback fires.
 * - Capture/restore original display mode when leaving playback.
 */
object FrameRateUtils {

    private const val TAG = "FrameRateUtils"
    private const val SWITCH_TIMEOUT_MS = 5000L
    private const val MULTIPLE_TOLERANCE = 0.02f

    /** Active display listener (for cleanup). */
    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    private var pendingAfterSwitch: ((Display.Mode) -> Unit)? = null
    private var pendingDisplayId: Int? = null
    private var pendingMode: Display.Mode? = null
    private var originalModeId: Int? = null

    /**
     * Normalize a refresh rate to an integer × 100 for safe floating-point comparison.
     */
    private fun normRate(rate: Float): Int = (rate * 100f).toInt()

    private data class ModeCandidate(
        val mode: Display.Mode,
        val multiple: Int,
        val multipleError: Float
    )

    private fun completeSwitch(reason: String) {
        Log.d(TAG, "Display mode switch completed ($reason)")
        val callback = pendingAfterSwitch
        val mode = pendingMode
        cleanupDisplayListener()
        if (callback != null && mode != null) {
            callback(mode)
        }
    }

    private fun scheduleSwitchTimeout() {
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            Log.w(TAG, "Display mode switch timeout after ${SWITCH_TIMEOUT_MS}ms")
            completeSwitch("timeout")
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, SWITCH_TIMEOUT_MS)
    }

    private fun recordOriginalMode(display: Display) {
        if (originalModeId == null) {
            originalModeId = display.mode.modeId
        }
    }

    /**
     * Attempt to match the display refresh rate to the video [frameRate].
     *
     * Kodi-style coordination with playback:
     * - [onBeforeSwitch] is invoked right before the mode switch is applied (caller should pause the player).
     * - [onAfterSwitch] is invoked once the display has actually changed (caller should resume playback).
     * - If no switch is needed, neither callback is invoked.
     * - A safety timeout ensures playback resumes even if the TV never fires the display-changed callback.
     *
     * @param activity The current Activity (needed for window attributes).
     * @param frameRate The detected video frame rate (e.g. 23.976, 24, 25, 29.97, 30, 50, 59.94).
     * @param onBeforeSwitch Called on the main thread right before the mode switch. Pause the player here.
     * @param onAfterSwitch Called on the main thread when the display change completes (or times out).
     *                      Provides the target [Display.Mode].
     * @return `true` if a mode switch was requested, `false` if not needed or unavailable.
     */
    fun matchFrameRate(
        activity: Activity,
        frameRate: Float,
        onBeforeSwitch: (() -> Unit)? = null,
        onAfterSwitch: ((Display.Mode) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (frameRate <= 0f) return false

        try {
            val window = activity.window ?: return false
            val display = window.decorView.display ?: return false
            val supportedModes = display.supportedModes
            val activeMode = display.mode

            if (supportedModes.size <= 1) return false

            // Collect modes that match current resolution
            val sameSizeModes = supportedModes.filter {
                it.physicalWidth == activeMode.physicalWidth &&
                        it.physicalHeight == activeMode.physicalHeight
            }

            if (sameSizeModes.size <= 1) return false

            // Track the highest refresh rate mode at same resolution
            val modeTop = sameSizeModes.maxByOrNull { normRate(it.refreshRate) } ?: activeMode

            // Find the best mode - prefer close integer multiples of the video FPS.
            val candidates = sameSizeModes.map { mode ->
                val ratio = mode.refreshRate / frameRate
                val multiple = ratio.roundToInt().coerceAtLeast(1)
                val error = abs(ratio - multiple.toFloat())
                ModeCandidate(mode, multiple, error)
            }

            val modeBest = candidates
                .filter { it.multipleError <= MULTIPLE_TOLERANCE }
                .sortedWith(
                    compareBy<ModeCandidate> { it.multiple }
                        .thenBy { it.multipleError }
                )
                .firstOrNull()
                ?.mode
                ?: modeTop

            val switchNeeded = modeBest.modeId != activeMode.modeId
            if (switchNeeded) {
                Log.d(TAG, "Switching display mode: ${activeMode.refreshRate}Hz → ${modeBest.refreshRate}Hz " +
                        "(video ${frameRate}fps)")

                // Clean up any previous listener
                cleanupDisplayListener()
                recordOriginalMode(display)

                var completeImmediately = false
                // Register DisplayManager listener to know when the TV has finished switching
                if (onAfterSwitch != null) {
                    pendingAfterSwitch = onAfterSwitch
                    pendingMode = modeBest
                    pendingDisplayId = display.displayId
                    displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                    displayListener = object : DisplayManager.DisplayListener {
                        override fun onDisplayAdded(displayId: Int) {}
                        override fun onDisplayRemoved(displayId: Int) {}
                        override fun onDisplayChanged(displayId: Int) {
                            if (displayId != pendingDisplayId) return
                            completeSwitch("displayChanged")
                        }
                    }
                    if (displayManager != null) {
                        displayManager?.registerDisplayListener(
                            displayListener,
                            Handler(Looper.getMainLooper())
                        )
                        scheduleSwitchTimeout()
                    } else {
                        completeImmediately = true
                    }
                }

                // Notify caller to pause playback before the switch
                onBeforeSwitch?.invoke()

                // Apply the mode switch
                val layoutParams = window.attributes
                layoutParams.preferredDisplayModeId = modeBest.modeId
                window.attributes = layoutParams
                if (completeImmediately) {
                    completeSwitch("noDisplayManager")
                }
            } else {
                Log.d(TAG, "Display already at optimal rate ${activeMode.refreshRate}Hz for ${frameRate}fps")
            }

            return switchNeeded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to match frame rate", e)
            if (pendingAfterSwitch != null) {
                completeSwitch("error")
            }
            return false
        }
    }

    /**
     * Clean up any active DisplayManager listener and pending timeout.
     * Safe to call multiple times.
     */
    fun cleanupDisplayListener() {
        timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        timeoutRunnable = null
        timeoutHandler = null
        displayListener?.let { displayManager?.unregisterDisplayListener(it) }
        displayListener = null
        displayManager = null
        pendingAfterSwitch = null
        pendingDisplayId = null
        pendingMode = null
    }

    /**
     * Clear any stored original display mode without restoring.
     * Useful for "start only" mode where we don't revert on exit.
     */
    fun clearOriginalDisplayMode() {
        originalModeId = null
    }

    /**
     * Restore the original display mode captured before the first switch.
     * Kodi-style: reset display preferences on exit.
     */
    fun restoreOriginalDisplayMode(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val targetModeId = originalModeId ?: return false

        return try {
            val window = activity.window ?: return false
            val display = window.decorView.display ?: return false
            if (display.mode.modeId == targetModeId) {
                originalModeId = null
                true
            } else {
                cleanupDisplayListener()
                val layoutParams = window.attributes
                layoutParams.preferredDisplayModeId = targetModeId
                window.attributes = layoutParams
                originalModeId = null
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore display mode", e)
            false
        }
    }

    /**
     * Detect the video frame rate from an ExoPlayer Format and snap to standard rates.
     *
     * @param formatFrameRate The frame rate reported by ExoPlayer's video track Format.
     * @return The snapped standard frame rate, or the original if no standard match.
     */
    fun snapToStandardRate(formatFrameRate: Float): Float {
        if (formatFrameRate <= 0f) return formatFrameRate
        return when {
            formatFrameRate in 23.90f..23.988f -> 24000f / 1001f  // 23.976 (NTSC film)
            formatFrameRate in 23.988f..24.1f -> 24f
            formatFrameRate in 24.9f..25.1f -> 25f                 // PAL
            formatFrameRate in 29.90f..29.985f -> 30000f / 1001f  // 29.97 NTSC
            formatFrameRate in 29.985f..30.1f -> 30f
            formatFrameRate in 49.9f..50.1f -> 50f                 // PAL interlaced
            formatFrameRate in 59.9f..59.97f -> 60000f / 1001f    // 59.94 NTSC
            formatFrameRate in 59.97f..60.1f -> 60f
            else -> formatFrameRate
        }
    }

    /**
     * Fallback FPS detection using MediaExtractor, similar to JustPlayer.
     * Useful when ExoPlayer track Format doesn't report frameRate.
     */
    fun detectFrameRateFromSource(
        context: Context,
        sourceUrl: String,
        headers: Map<String, String> = emptyMap()
    ): Float {
        val extractor = MediaExtractor()
        return try {
            val uri = Uri.parse(sourceUrl)
            when (uri.scheme?.lowercase()) {
                "http", "https" -> extractor.setDataSource(sourceUrl, headers)
                else -> extractor.setDataSource(context, uri, headers)
            }

            var videoTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    break
                }
            }
            if (videoTrackIndex < 0) return 0f

            extractor.selectTrack(videoTrackIndex)
            val timestamps = ArrayList<Long>(400)
            val ignoreSamples = 30
            val targetSamples = 350 + ignoreSamples

            while (timestamps.size < targetSamples) {
                val ts = extractor.sampleTime
                if (ts < 0) break
                timestamps.add(ts)
                if (!extractor.advance()) break
            }

            if (timestamps.size <= ignoreSamples + 1) return 0f

            var totalFrameDurationUs = 0L
            for (i in 1 until (timestamps.size - ignoreSamples)) {
                totalFrameDurationUs += (timestamps[i] - timestamps[i - 1])
            }
            val sampleCount = (timestamps.size - ignoreSamples - 1).coerceAtLeast(1)
            val averageFrameDurationUs = totalFrameDurationUs.toFloat() / sampleCount.toFloat()
            if (averageFrameDurationUs <= 0f) return 0f

            val measured = 1_000_000f / averageFrameDurationUs
            snapToStandardRate(measured)
        } catch (e: Exception) {
            Log.w(TAG, "Frame rate probe failed: ${e.message}")
            0f
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }
}
