package com.nuvio.tv.ui.screens.immersive

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.WatchProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

// ── Infinite-scroll grid with per-row recenter ──────────────────────────────
//
// The grid is an infinite carousel in both axes.  All rows share a single
// animated column value (animatedCol) so that left/right input scrolls every
// row by one tile in lockstep.  Each row may have a different number of unique
// items; items repeat via mod-wrapping to fill the infinite strip.
//
// Recenter system
// ───────────────
// After RECENTER_DELAY_MS of idle, every *non-focused* row smoothly slides to
// its nearest "col-0" (the position where item 0 is centred on screen).
// The focused row keeps its position — the user's scroll offset is preserved.
//
// Because rows have different item counts, their col-0 positions differ.
// A per-row offset map (rowOffsets) stores the delta between animatedCol and
// each non-focused row's actual column.  When the user scrolls, animatedCol
// changes and every offset stays constant, so all rows move together.
//
// Vertical navigation after recenter
// ───────────────────────────────────
// When the user presses Up/Down after a recenter has happened:
//   1. The old focused row's offset is recorded as 0 (its true position).
//   2. animatedCol is shifted to match the target row's visual column.
//   3. Every stored offset is compensated by the same shift so that no row
//      moves on screen.
// This guarantees zero visual jumps for any row regardless of item counts.
//
// The next recenter (after another idle timeout) will smoothly bring all
// non-focused rows back to their col-0.

private const val VISIBLE_ROWS = 3.25f
private const val TILE_MARGIN_DP = 4f
private const val ASPECT_RATIO = 2f / 3f // poster 2:3
private const val SPRING_STIFFNESS = 200f
private const val RECENTER_DELAY_MS = 5000L
private const val RECENTER_ANIM_MS = 800

private fun mod(a: Int, b: Int): Int {
    if (b <= 0) return 0
    return ((a % b) + b) % b
}

private fun nearestCol0(currentCol: Int, itemCount: Int): Int {
    if (itemCount <= 0) return currentCol
    val wrapped = mod(currentCol, itemCount)
    val distLeft = wrapped
    val distRight = itemCount - wrapped
    return if (distLeft <= distRight) currentCol - distLeft else currentCol + distRight
}

private fun Modifier.pixelSize(widthPx: Int, heightPx: Int): Modifier =
    layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
        layout(widthPx, heightPx) {
            placeable.placeRelative(0, 0)
        }
    }

private val rowOffsetsSaver = listSaver<SnapshotStateMap<Int, Int>, Int>(
    save = { map ->
        buildList { map.forEach { (k, v) -> add(k); add(v) } }
    },
    restore = { list ->
        SnapshotStateMap<Int, Int>().also { map ->
            for (i in list.indices step 2) map[list[i]] = list[i + 1]
        }
    }
)

private fun ensureMinItems(items: List<MetaPreview>, minCount: Int): List<MetaPreview> {
    if (items.isEmpty()) return items
    if (items.size >= minCount) return items
    val result = items.toMutableList()
    while (result.size < minCount) {
        result.addAll(items)
    }
    return result
}

@Composable
fun ImmersiveGrid(
    catalogRows: List<CatalogRow>,
    metadataState: MetadataPopupState,
    watchProgressMap: Map<String, WatchProgress> = emptyMap(),
    nextUpIds: Set<String> = emptySet(),
    onFocusChanged: (MetaPreview?) -> Unit,
    onItemClick: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    if (catalogRows.isEmpty()) return

    @Suppress("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val marginPx = (TILE_MARGIN_DP * density.density).roundToInt()
        val cellHeightPx = floor(screenHeightPx / VISIBLE_ROWS).roundToInt()
        val tileHeightPx = cellHeightPx - 2 * marginPx
        val tileWidthPx = (tileHeightPx * ASPECT_RATIO).roundToInt()
        val cellWidthPx = tileWidthPx + 2 * marginPx

        val tileHeight: Dp = with(density) { tileHeightPx.toDp() }
        val tileWidth: Dp = with(density) { tileWidthPx.toDp() }
        val tileMargin: Dp = with(density) { marginPx.toDp() }

        val rawCols = (screenWidthPx / cellWidthPx).roundToInt().coerceAtLeast(1)
        val visibleCols = if (rawCols % 2 == 0) rawCols + 1 else rawCols
        val centerCol = visibleCols / 2

        val gridOffsetX = (screenWidthPx.toInt() - visibleCols * cellWidthPx) / 2f

        val visibleRows = VISIBLE_ROWS.roundToInt() + 2
        val centerRow = 1

        val processedRows = remember(catalogRows) {
            catalogRows.map { row ->
                row.copy(items = ensureMinItems(row.items, visibleCols + 1))
            }
        }

        val totalCatalogRows = processedRows.size

        var focusedRow by rememberSaveable { mutableIntStateOf(1) }
        var focusedCol by rememberSaveable { mutableIntStateOf(0) }

        val initialCol = focusedCol
        val initialRow = focusedRow
        val animatedCol = remember { Animatable(initialCol.toFloat()) }
        val animatedRow = remember { Animatable(initialRow.toFloat()) }
        val scope = rememberCoroutineScope()

        // Recenter animation progress: 0 = idle, animates to 1 during recenter.
        val recenterProgress = remember { Animatable(0f) }

        // Per-row column offsets (see file-level comment for full explanation).
        val rowOffsets = rememberSaveable(saver = rowOffsetsSaver) { mutableStateMapOf<Int, Int>() }

        // True once the first recenter has completed. Controls offset-based vertical nav.
        var recentered by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(focusedRow, focusedCol) {
            if (!recentered) {
                recenterProgress.snapTo(0f)
            }
            delay(RECENTER_DELAY_MS)
            recenterProgress.animateTo(1f, tween(RECENTER_ANIM_MS))
            // Recenter complete: update offsets to col-0 positions for all non-focused rows
            val focusedWrapped = mod(focusedRow, totalCatalogRows)
            for (i in processedRows.indices) {
                if (i != focusedWrapped && processedRows[i].items.isNotEmpty()) {
                    val oldOffset = rowOffsets[i] ?: 0
                    val effectiveCol = focusedCol + oldOffset
                    rowOffsets[i] = nearestCol0(effectiveCol, processedRows[i].items.size) - focusedCol
                }
            }
            recenterProgress.snapTo(0f)
            recentered = true
        }

        LaunchedEffect(focusedCol) {
            scope.launch {
                animatedCol.animateTo(
                    targetValue = focusedCol.toFloat(),
                    animationSpec = spring(stiffness = SPRING_STIFFNESS)
                )
            }
        }

        LaunchedEffect(focusedRow) {
            scope.launch {
                animatedRow.animateTo(
                    targetValue = focusedRow.toFloat(),
                    animationSpec = spring(stiffness = SPRING_STIFFNESS)
                )
            }
        }

        // Notify ViewModel of focused item — re-fire when catalog count changes
        LaunchedEffect(focusedRow, focusedCol, totalCatalogRows) {
            val wrappedRow = mod(focusedRow, totalCatalogRows)
            val row = processedRows[wrappedRow]
            val wrappedCol = mod(focusedCol, row.items.size)
            Log.d("ImmersiveGrid", "Focus: row=$wrappedRow col=$wrappedCol catalog=\"${row.catalogName}\" (${row.addonName})")
            onFocusChanged(row.items.getOrNull(wrappedCol))
        }

        val focusRequester = remember { FocusRequester() }
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                focusRequester.requestFocus()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (keyEvent.key) {
                        Key.DirectionRight -> {
                            scope.launch { recenterProgress.snapTo(0f) }
                            focusedCol++
                            true
                        }
                        Key.DirectionLeft -> {
                            scope.launch { recenterProgress.snapTo(0f) }
                            focusedCol--
                            true
                        }
                        Key.DirectionDown, Key.DirectionUp -> {
                            val delta = if (keyEvent.key == Key.DirectionDown) 1 else -1
                            val wrappedNew = mod(focusedRow + delta, totalCatalogRows)
                            if (recentered) {
                                // If recenter animation is in progress, complete it: finalize offsets to col-0
                                if (recenterProgress.value > 0.01f) {
                                    val fw = mod(focusedRow, totalCatalogRows)
                                    for (i in processedRows.indices) {
                                        if (i != fw && processedRows[i].items.isNotEmpty()) {
                                            val old = rowOffsets[i] ?: 0
                                            val eff = focusedCol + old
                                            rowOffsets[i] = nearestCol0(eff, processedRows[i].items.size) - focusedCol
                                        }
                                    }
                                }
                                scope.launch { recenterProgress.snapTo(0f) }

                                // Record old focused row's effective offset (0, since it was focused)
                                val focusedWrapped = mod(focusedRow, totalCatalogRows)
                                rowOffsets[focusedWrapped] = 0

                                // Shift animatedCol to match the target row's visual position
                                val targetOffset = rowOffsets[wrappedNew] ?: 0
                                if (targetOffset != 0) {
                                    focusedCol += targetOffset
                                    scope.launch { animatedCol.snapTo(focusedCol.toFloat()) }
                                    // Compensate all offsets so every row keeps its visual position
                                    for (key in rowOffsets.keys.toList()) {
                                        rowOffsets[key] = rowOffsets[key]!! - targetOffset
                                    }
                                }
                                // Target row is now focused — its offset is not used during rendering
                                rowOffsets.remove(wrappedNew)
                            }
                            focusedRow += delta
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> {
                            val wrappedRow = mod(focusedRow, totalCatalogRows)
                            val row = processedRows[wrappedRow]
                            val wrappedCol = mod(focusedCol, row.items.size)
                            row.items.getOrNull(wrappedCol)?.let { onItemClick(it) }
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
        ) {
            val currentAnimCol = animatedCol.value
            val currentAnimRow = animatedRow.value
            val recenterProg = recenterProgress.value

            for (screenRow in -1..visibleRows) {
                val gridRow = floor(currentAnimRow).toInt() + (screenRow - centerRow)
                val wrappedRow = mod(gridRow, totalCatalogRows)
                val row = processedRows[wrappedRow]
                if (row.items.isEmpty()) continue

                val isFocusedRow = gridRow == focusedRow

                // Per-row effective column using stored offsets.
                // During recenter animation (recenterProg > 0), rows interpolate toward their col-0.
                val effectiveAnimCol = if (isFocusedRow) {
                    currentAnimCol
                } else {
                    val rowOffset = (rowOffsets[wrappedRow] ?: 0).toFloat()
                    val baseCol = currentAnimCol + rowOffset
                    if (recenterProg <= 0f) {
                        baseCol
                    } else {
                        val baseWrapped = mod(floor(baseCol).toInt(), row.items.size)
                        if (baseWrapped == 0) {
                            baseCol
                        } else {
                            val target = nearestCol0(floor(baseCol).toInt(), row.items.size).toFloat()
                            baseCol + (target - baseCol) * recenterProg
                        }
                    }
                }

                for (screenCol in -1..visibleCols) {
                    val gridCol = floor(effectiveAnimCol).toInt() + (screenCol - centerCol)
                    val wrappedCol = mod(gridCol, row.items.size)
                    val item = row.items[wrappedCol]

                    val fracCol = effectiveAnimCol - floor(effectiveAnimCol)
                    val fracRow = currentAnimRow - floor(currentAnimRow)

                    val pixelX = gridOffsetX + (screenCol - fracCol) * cellWidthPx
                    val pixelY = (screenRow - fracRow) * cellHeightPx

                    val isFocused = isFocusedRow && gridCol == focusedCol
                    val expand = if (isFocused) marginPx else 0

                    Box(
                        modifier = Modifier
                            .pixelSize(cellWidthPx + 2 * expand, cellHeightPx + 2 * expand)
                            .zIndex(if (isFocused) 1f else 0f)
                            .graphicsLayer {
                                translationX = pixelX - expand
                                translationY = pixelY - expand
                            }
                    ) {
                        ImmersiveTile(
                            item = item,
                            isFocused = isFocused,
                            tileWidth = tileWidth,
                            tileHeight = tileHeight,
                            watchProgress = watchProgressMap[item.id],
                            isNextUp = item.id in nextUpIds,
                            tileMargin = if (isFocused) tileMargin * 2 else tileMargin,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Metadata overlay
            val overlayWidthCells = 3
            val focusTileLeftPx = gridOffsetX + centerCol * cellWidthPx
            // Inset by marginPx so the overlay left edge aligns with the tile image edge (+2px buffer)
            val overlayContainerWidthPx = overlayWidthCells * cellWidthPx - marginPx + 2
            val overlayContainerLeftPx = focusTileLeftPx - overlayContainerWidthPx
            // Match the expanded focused tile height
            val overlayHeightPx = cellHeightPx + 2 * marginPx

            // Resolve the focused catalog info for the overlay
            val focusedWrappedRow = mod(focusedRow, totalCatalogRows)
            val focusedCatalogRow = processedRows[focusedWrappedRow]
            val catalogLabel = if (focusedCatalogRow.addonName.isBlank()) {
                focusedCatalogRow.catalogName
            } else {
                "${focusedCatalogRow.catalogName} - ${focusedCatalogRow.type.toApiString().replaceFirstChar { it.uppercase() }}"
            }

            Box(
                modifier = Modifier
                    .pixelSize(overlayContainerWidthPx, overlayHeightPx)
                    .graphicsLayer {
                        val fracRow = currentAnimRow - floor(currentAnimRow)
                        translationX = overlayContainerLeftPx
                        translationY = (centerRow - fracRow) * cellHeightPx - marginPx
                    }
                    .clipToBounds(),
                contentAlignment = Alignment.CenterEnd
            ) {
                ImmersiveMetadataOverlay(
                    state = metadataState,
                    catalogLabel = catalogLabel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
