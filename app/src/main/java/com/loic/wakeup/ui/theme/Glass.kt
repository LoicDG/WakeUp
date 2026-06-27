package com.loic.wakeup.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The pre-dawn sky every screen floats on: a near-black zenith fading through
 * midnight, with a cool indigo aurora high on the right and a warm dawn glow
 * rising from the lower left — the horizon the alarm wakes you toward.
 *
 * Draw this on the layer you also mark as a Haze `hazeSource`, together with the
 * scrolling content above it, so a pinned glass surface blurs the *whole* scene
 * (aurora + cards) as it passes underneath — not a flat gradient, which barely
 * blurs at all.
 */
fun Modifier.auroraSky(): Modifier = drawBehind { drawAurora() }

private fun DrawScope.drawAurora() {
    drawRect(
        Brush.verticalGradient(
            0f to DeepNight,
            0.5f to Midnight,
            1f to DeepNavy,
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(IndigoGlow.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(size.width * 0.78f, size.height * 0.10f),
            radius = size.maxDimension * 0.62f,
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(DawnGlow.copy(alpha = 0.42f), Color.Transparent),
            center = Offset(size.width * 0.18f, size.height * 0.94f),
            radius = size.maxDimension * 0.58f,
        )
    )
}

/**
 * A translucent frosted panel for surfaces that sit *over* the aurora rather than
 * over busy content. There's nothing detailed behind them to blur, so this skips
 * Haze entirely (cheaper, and identical-looking) — a dark frosted fill that tames
 * the dawn glow for legibility, a white sheen on top, and the same specular
 * hairline so it reads as the same material as the real glass.
 */
fun Modifier.frostedPanel(shape: Shape): Modifier = this
    .clip(shape)
    .background(GlassFill)
    .background(GlassTint)
    .specularEdge(shape)

/**
 * True liquid glass: clips to [shape] and blurs whatever Haze [hazeState] is
 * capturing behind it (real backdrop blur on API 31+, a translucent navy scrim
 * below). Reserve this for a surface that overlaps moving content — a pinned bar
 * the list scrolls under, or buttons over the animated ringing pulse.
 */
fun Modifier.liquidGlass(
    hazeState: HazeState,
    shape: Shape,
    blurRadius: Dp = 28.dp,
    tint: Color = GlassTint,
): Modifier = this
    .clip(shape)
    .hazeEffect(
        state = hazeState,
        style = HazeStyle(
            backgroundColor = Midnight,
            tints = listOf(HazeTint(tint)),
            blurRadius = blurRadius,
            noiseFactor = 0.06f,
            fallbackTint = HazeTint(GlassFillFallback),
        ),
    )
    .specularEdge(shape)

/** Hairline that catches the light brightest at the top-left, fading to nothing. */
private fun Modifier.specularEdge(shape: Shape): Modifier = border(
    width = 1.dp,
    brush = Brush.linearGradient(
        colors = listOf(GlassEdgeHigh, GlassEdgeLow),
        start = Offset.Zero,
        end = Offset.Infinite,
    ),
    shape = shape,
)
