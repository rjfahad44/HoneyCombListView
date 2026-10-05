package com.bitbytestudio.honeycomblistview.honeycomb

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Shapes (unchanged)
// ---------------------------------------------------------------------------

val PointyHexagonShape: Shape = GenericShape { size, _ ->
    val radius = min(size.width, size.height) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (i in 0 until 6) {
        val a = Math.toRadians((i * 60 - 90).toDouble())
        val x = cx + radius * cos(a).toFloat()
        val y = cy + radius * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

val FlatHexagonShape: Shape = GenericShape { size, _ ->
    val radius = min(size.width, size.height) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (i in 0 until 6) {
        val a = Math.toRadians((i * 60).toDouble())
        val x = cx + radius * cos(a).toFloat()
        val y = cy + radius * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// ---------------------------------------------------------------------------
// Layout math
// ---------------------------------------------------------------------------

fun calculateAutoColumns(itemCount: Int): Int {
    if (itemCount <= 0) return 1
    return kotlin.math.round(kotlin.math.sqrt(itemCount * 0.866f)).toInt().coerceAtLeast(1)
}

fun calculateCircularGridPositions(
    itemCount: Int,
    columns: Int,
    xStep: Float,
    yStep: Float
): List<Offset> {
    if (itemCount <= 0) return emptyList()

    val cols = if (columns > 0) columns else calculateAutoColumns(itemCount)
    val rows = (itemCount / cols) + 4

    val centerX = (cols - 1) / 2f * xStep
    val centerY = (rows - 1) / 2f * yStep

    val xs = FloatArray(cols * rows)
    val ys = FloatArray(cols * rows)
    val dist = FloatArray(cols * rows)
    var n = 0
    for (row in 0 until rows) {
        val stagger = if (row % 2 != 0) xStep / 2f else 0f
        for (col in 0 until cols) {
            val gx = col * xStep + stagger
            val gy = row * yStep
            xs[n] = gx
            ys[n] = gy
            val dx = gx - centerX
            val dy = gy - centerY
            dist[n] = dx * dx + dy * dy
            n++
        }
    }

    return (0 until n)
        .sortedBy { dist[it] }
        .take(itemCount)
        .map { Offset(xs[it], ys[it]) }
}

// ---------------------------------------------------------------------------
// Pan state + limits
// ---------------------------------------------------------------------------

private data class PanLimits(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val initialX: Float,
    val initialY: Float
)

private fun computePanLimits(
    positions: List<Offset>,
    itemSizePx: Float,
    containerW: Float,
    containerH: Float
): PanLimits {
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (p in positions) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    val initialX = (containerW - (minX + maxX + itemSizePx)) / 2f
    val initialY = (containerH - (minY + maxY + itemSizePx)) / 2f

    val marginX = (containerW * 0.35f).coerceAtMost(itemSizePx * 1.5f)
    val marginY = (containerH * 0.35f).coerceAtMost(itemSizePx * 1.5f)

    return PanLimits(
        minX = minOf(marginX - (maxX + itemSizePx), initialX),
        maxX = maxOf(containerW - marginX - minX, initialX),
        minY = minOf(marginY - (maxY + itemSizePx), initialY),
        maxY = maxOf(containerH - marginY - minY, initialY),
        initialX = initialX,
        initialY = initialY
    )
}

/**
 * Pan is held in primitive float states and written synchronously from the gesture
 * (no coroutine per drag event). Only the fling uses animation.
 */
@Stable
private class HoneycombPanState(initialX: Float, initialY: Float) {
    var panX by mutableFloatStateOf(initialX)
    var panY by mutableFloatStateOf(initialY)
    private var flingJob: Job? = null

    fun stopFling() {
        flingJob?.cancel()
        flingJob = null
    }

    fun dragBy(dx: Float, dy: Float, l: PanLimits) {
        panX = (panX + dx).coerceIn(l.minX, l.maxX)
        panY = (panY + dy).coerceIn(l.minY, l.maxY)
    }

    fun clamp(l: PanLimits) {
        panX = panX.coerceIn(l.minX, l.maxX)
        panY = panY.coerceIn(l.minY, l.maxY)
    }

    fun fling(
        vx: Float,
        vy: Float,
        l: PanLimits,
        decay: DecayAnimationSpec<Float>,
        scope: CoroutineScope
    ) {
        stopFling()
        flingJob = scope.launch {
            // Each axis decays independently and stops cleanly at its own bound,
            // so hitting one wall doesn't kill the motion on the other axis.
            launch {
                Animatable(panX).apply { updateBounds(l.minX, l.maxX) }
                    .animateDecay(vx, decay) { panX = value }
            }
            launch {
                Animatable(panY).apply { updateBounds(l.minY, l.maxY) }
                    .animateDecay(vy, decay) { panY = value }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Core implementation (shared by both public composables)
// ---------------------------------------------------------------------------

@Composable
private fun HoneycombCore(
    itemCount: Int,
    modifier: Modifier,
    columns: Int,
    itemSize: Dp,
    spacingX: Dp,
    spacingY: Dp,
    maxScale: Float,
    minScale: Float,
    maxAlpha: Float,
    minAlpha: Float,
    content: @Composable (index: Int, modifier: Modifier) -> Unit
) {
    if (itemCount <= 0) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()

    val itemSizePx = with(density) { itemSize.toPx() }
    val xStep = itemSizePx + with(density) { spacingX.toPx() }
    val yStep = (itemSizePx + with(density) { spacingY.toPx() }) * 0.866f

    val positions = remember(itemCount, columns, xStep, yStep) {
        calculateCircularGridPositions(itemCount, columns, xStep, yStep)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        val limits = remember(positions, itemSizePx, containerW, containerH) {
            computePanLimits(positions, itemSizePx, containerW, containerH)
        }
        val state = remember { HoneycombPanState(limits.initialX, limits.initialY) }

        // Keep pan valid if the layout changes (e.g. paging adds items / rotation).
        LaunchedEffect(limits) { state.clamp(limits) }

        // Gesture lambda never restarts; it always sees the latest limits.
        val currentLimits by rememberUpdatedState(limits)

        val fisheyeRadius = min(containerW, containerH) * 0.55f
        val cullMargin = itemSizePx * 1.5f

        // Only recomposes when the SET of visible indices actually changes,
        // not on every drag frame.
        val visibleIndices by remember(positions, containerW, containerH, itemSizePx) {
            derivedStateOf {
                val px = state.panX
                val py = state.panY
                val out = ArrayList<Int>(64)
                for (i in positions.indices) {
                    val cx = positions[i].x + px + itemSizePx / 2f
                    val cy = positions[i].y + py + itemSizePx / 2f
                    if (cx in -cullMargin..(containerW + cullMargin) &&
                        cy in -cullMargin..(containerH + cullMargin)
                    ) out.add(i)
                }
                out
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            state.stopFling()
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            state.dragBy(drag.x, drag.y, currentLimits)
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            state.fling(v.x, v.y, currentLimits, decay, scope)
                        },
                        onDragCancel = {
                            val v = tracker.calculateVelocity()
                            state.fling(v.x, v.y, currentLimits, decay, scope)
                        }
                    )
                }
        ) {
            for (index in visibleIndices) {
                key(index) {
                    val pos = positions[index]
                    Box(
                        modifier = Modifier.graphicsLayer {
                            // State is read ONLY here (draw phase) -> no recomposition while dragging.
                            val tx = pos.x + state.panX
                            val ty = pos.y + state.panY

                            val dist = hypot(
                                tx + itemSizePx / 2f - containerW / 2f,
                                ty + itemSizePx / 2f - containerH / 2f
                            )
                            val t = (dist / fisheyeRadius).coerceIn(0f, 1.2f) / 1.2f

                            translationX = tx
                            translationY = ty
                            val s = maxScale - (maxScale - minScale) * t
                            scaleX = s
                            scaleY = s
                            alpha = maxAlpha - (maxAlpha - minAlpha) * t
                        }
                    ) {
                        content(index, Modifier.size(itemSize))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Public API (same signatures as before)
// ---------------------------------------------------------------------------

@Composable
fun <T> Honeycomb2DGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    columns: Int = 0,
    itemSize: Dp = 96.dp,
    spacingX: Dp = 8.dp,
    spacingY: Dp = 8.dp,
    maxScale: Float = 1.15f,
    minScale: Float = 0.45f,
    maxAlpha: Float = 1.0f,
    minAlpha: Float = 0.20f,
    itemContent: @Composable (item: T, index: Int, modifier: Modifier) -> Unit
) {
    HoneycombCore(
        itemCount = items.size,
        modifier = modifier,
        columns = columns,
        itemSize = itemSize,
        spacingX = spacingX,
        spacingY = spacingY,
        maxScale = maxScale,
        minScale = minScale,
        maxAlpha = maxAlpha,
        minAlpha = minAlpha
    ) { index, mod ->
        items.getOrNull(index)?.let { itemContent(it, index, mod) }
    }
}

@Composable
fun <T : Any> Honeycomb2DPagingGrid(
    items: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    columns: Int = 0,
    itemSize: Dp = 96.dp,
    spacingX: Dp = 8.dp,
    spacingY: Dp = 8.dp,
    maxScale: Float = 1.15f,
    minScale: Float = 0.45f,
    maxAlpha: Float = 1.0f,
    minAlpha: Float = 0.20f,
    itemContent: @Composable (item: T, index: Int, modifier: Modifier) -> Unit
) {
    HoneycombCore(
        itemCount = items.itemCount,
        modifier = modifier,
        columns = columns,
        itemSize = itemSize,
        spacingX = spacingX,
        spacingY = spacingY,
        maxScale = maxScale,
        minScale = minScale,
        maxAlpha = maxAlpha,
        minAlpha = minAlpha
    ) { index, mod ->
        // Only visible indices are accessed, so Paging prefetches around what's on screen.
        items[index]?.let { itemContent(it, index, mod) }
    }
}