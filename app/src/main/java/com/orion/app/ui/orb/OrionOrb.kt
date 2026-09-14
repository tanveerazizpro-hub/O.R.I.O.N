package com.orion.app.ui.orb

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.orion.app.core.OrionState
import com.orion.app.ui.theme.accentFor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A single particle drifting around the orb.
 * Values are in normalized units (0..1 of the canvas), so they scale cleanly.
 */
private data class Particle(
    val angle: Float,          // current angle in radians
    val radiusFactor: Float,   // distance from center, relative to orb radius
    val sizePx: Float,         // pixel size of the dot
    val alpha: Float,          // base opacity
    val driftSpeed: Float      // radians per second of "conceptual" drift
)

/**
 * The reactive orb.
 *
 * STEP 2 scope:
 *  - Renders a glowing, breathing orb with 3 orbital strands and a particle field.
 *  - Reads an [OrionState] and applies the matching accent color.
 *  - Voice/audio reactivity (barge-in, mic volume response) is stubbed for later phases.
 *
 * Rendering is done entirely with Compose Canvas — no image assets, no external
 * dependencies, GPU-friendly on mid-range Android devices.
 */
@Composable
fun OrionOrb(
    state: OrionState,
    modifier: Modifier = Modifier,
    sizeDp: Int = 240
) {
    val accent = accentFor(state)

    val transition = rememberInfiniteTransition(label = "orbIdle")

    // Slow breathing pulse (scale factor)
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Slow orbital rotation of the outer strands (degrees)
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Continuous clock used to advance the particle field
    val particleClock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleClock"
    )

    // Precompute the particle field once. Deterministic random keeps it stable.
    val particles = remember {
        val rng = Random(42)
        List(48) {
            Particle(
                angle = rng.nextFloat() * (2f * Math.PI.toFloat()),
                radiusFactor = 1.05f + rng.nextFloat() * 0.95f, // between 1.05x and 2.0x orb radius
                sizePx = 1.2f + rng.nextFloat() * 1.6f,
                alpha = 0.25f + rng.nextFloat() * 0.45f,
                driftSpeed = (0.15f + rng.nextFloat() * 0.35f) * (if (rng.nextBoolean()) 1f else -1f)
            )
        }
    }

    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) {
            drawOrb(
                accent = accent,
                pulse = pulse,
                rotation = rotation,
                particleClock = particleClock,
                particles = particles
            )
        }
    }
}

private fun DrawScope.drawOrb(
    accent: Color,
    pulse: Float,
    rotation: Float,
    particleClock: Float,
    particles: List<Particle>
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseRadius = size.minDimension / 2f * 0.50f
    val radius = baseRadius * pulse

    // 1. Outer halo — soft glow bleeding outward
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.30f),
                accent.copy(alpha = 0.00f)
            ),
            center = center,
            radius = radius * 2.0f
        ),
        radius = radius * 2.0f,
        center = center
    )

    // 2. Mid glow — denser ring around the core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.85f),
                accent.copy(alpha = 0.10f)
            ),
            center = center,
            radius = radius * 1.15f
        ),
        radius = radius * 1.15f,
        center = center
    )

    // 3. Inner core — bright center with a soft falloff
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                accent,
                accent.copy(alpha = 0.55f)
            ),
            center = center,
            radius = radius * 0.55f
        ),
        radius = radius * 0.55f,
        center = center
    )

    // 4. Particle field — subtle dots drifting around the orb.
    //    Position updates with the slow particleClock, creating an organic drift.
    particles.forEach { p ->
        val currentAngle = p.angle + p.driftSpeed * particleClock * 2f * Math.PI.toFloat()
        val particleRadius = radius * p.radiusFactor
        val x = center.x + cos(currentAngle) * particleRadius
        val y = center.y + sin(currentAngle) * particleRadius

        // Fade with distance so particles far from the orb are dimmer
        val distanceFade = (1f - ((p.radiusFactor - 1.0f) / 1.0f)).coerceIn(0.15f, 1f)
        val finalAlpha = (p.alpha * distanceFade).coerceIn(0f, 0.8f)

        drawCircle(
            color = accent.copy(alpha = finalAlpha),
            radius = p.sizePx,
            center = Offset(x, y)
        )
    }

    // 5. Orbital strands — thin elliptical "energy ropes" rotating around the orb.
    rotate(degrees = rotation, pivot = center) {
        drawOrbitalStrand(center, radius * 3.0f, radius * 0.55f, accent)
    }
    rotate(degrees = -rotation * 0.65f, pivot = center) {
        drawOrbitalStrand(center, radius * 3.4f, radius * 0.35f, accent)
    }
    rotate(degrees = rotation * 0.45f, pivot = center) {
        drawOrbitalStrand(center, radius * 2.6f, radius * 0.75f, accent)
    }

    // NOTE (future phase): audio-reactivity will modulate `radius` from mic volume
    // (LISTENING) and from TTS amplitude (SPEAKING). Hooks for that arrive with
    // the voice system. Nothing here fakes audio today.
}

private fun DrawScope.drawOrbitalStrand(
    center: Offset,
    width: Float,
    height: Float,
    accent: Color
) {
    drawOval(
        color = accent.copy(alpha = 0.35f),
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        style = Stroke(width = 1.6f)
    )
}
