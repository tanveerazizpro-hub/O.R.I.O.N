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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.orion.app.core.OrionState
import com.orion.app.ui.theme.accentFor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------- 3D math ----------

private data class V3(val x: Float, val y: Float, val z: Float)

private fun rotateX(p: V3, a: Float): V3 {
    val c = cos(a); val s = sin(a)
    return V3(p.x, p.y * c - p.z * s, p.y * s + p.z * c)
}

private fun rotateY(p: V3, a: Float): V3 {
    val c = cos(a); val s = sin(a)
    return V3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c)
}

private fun rotateZ(p: V3, a: Float): V3 {
    val c = cos(a); val s = sin(a)
    return V3(p.x * c - p.y * s, p.x * s + p.y * c, p.z)
}

/**
 * Simple perspective projection. Positive z is "away from camera".
 * Returns projected screen position and the depth scale factor.
 */
private data class Projected(val x: Float, val y: Float, val scale: Float, val z: Float)

private fun project(p: V3, cx: Float, cy: Float, focal: Float): Projected {
    val depth = focal + p.z
    val k = focal / depth
    return Projected(cx + p.x * k, cy + p.y * k, k, p.z)
}

// ---------- Data ----------

/**
 * One gyroscope ring: fixed tilt (rx, ry, rz) and its own spin phase.
 * The ring is a 3D circle of a given radius, tilted in space.
 */
private data class GyroRing(
    val rx: Float,       // tilt around X (radians)
    val ry: Float,       // tilt around Y
    val rz: Float,       // tilt around Z
    val radius: Float,   // multiplier of the base radius
    val spin: Float,     // radians/sec of self-spin along the ring
    val leaderLength: Float // radians of the bright "leader" arc
)

/**
 * A spark in 3D space. Positioned by polar coords on a sphere shell,
 * then rotated with the global frame.
 */
private data class Spark(
    val r: Float,          // radius in normalized units
    val theta: Float,      // polar angle (radians)
    val phi: Float,        // azimuth (radians)
    val sizePx: Float,
    val alpha: Float,
    val isEmber: Boolean,
    val spinSpeed: Float
)

// ---------- Composable ----------

@Composable
fun OrionOrb(
    state: OrionState,
    modifier: Modifier = Modifier,
    sizeDp: Int = 260
) {
    val accent = accentFor(state)

    val transition = rememberInfiniteTransition(label = "orb")

    // Global Y rotation — full slow spin
    val globalY by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "globalY"
    )

    // Global X tilt — subtle wobble so it never looks flat
    val globalX by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "globalX"
    )

    // Continuous clock for ring self-spins and particle drift
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "clock"
    )

    // Breathing pulse on the core
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Three gyroscope rings at distinct orientations
    val rings = remember {
        listOf(
            GyroRing(rx = (Math.PI / 2).toFloat(), ry = 0f,             rz = 0f,             radius = 1.55f, spin =  0.9f, leaderLength = 1.1f),
            GyroRing(rx = (Math.PI / 3).toFloat(), ry = (Math.PI / 3).toFloat(), rz = 0f,     radius = 1.75f, spin = -0.7f, leaderLength = 0.9f),
            GyroRing(rx = (Math.PI / 5).toFloat(), ry = (2 * Math.PI / 3).toFloat(), rz = (Math.PI / 4).toFloat(), radius = 1.95f, spin = 0.6f, leaderLength = 1.0f)
        )
    }

    // Deterministic spark field
    val sparks = remember {
        val rng = Random(11)
        List(60) {
            val isEmber = rng.nextFloat() < 0.18f
            Spark(
                r = 1.35f + rng.nextFloat() * 1.35f,
                theta = rng.nextFloat() * (Math.PI.toFloat()),
                phi = rng.nextFloat() * (2f * Math.PI.toFloat()),
                sizePx = if (isEmber) 2.6f + rng.nextFloat() * 2.2f
                         else 0.9f + rng.nextFloat() * 1.2f,
                alpha = if (isEmber) 0.55f + rng.nextFloat() * 0.35f
                        else 0.20f + rng.nextFloat() * 0.35f,
                isEmber = isEmber,
                spinSpeed = (0.15f + rng.nextFloat() * 0.55f) *
                        (if (rng.nextBoolean()) 1f else -1f)
            )
        }
    }

    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) {
            drawOrb3D(
                accent = accent,
                globalY = globalY,
                globalX = globalX,
                clock = clock,
                pulse = pulse,
                rings = rings,
                sparks = sparks
            )
        }
    }
}

// ---------- Drawing ----------

private fun DrawScope.drawOrb3D(
    accent: Color,
    globalY: Float,
    globalX: Float,
    clock: Float,
    pulse: Float,
    rings: List<GyroRing>,
    sparks: List<Spark>
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val baseRadius = size.minDimension / 2f * 0.30f
    val coreRadius = baseRadius * pulse

    // Camera focal length in pixels. Bigger = flatter, smaller = more dramatic.
    val focal = size.minDimension * 0.55f

    // --- 1. Ambient bloom (soft wide glow, always drawn behind) ---
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(cx, cy),
            radius = baseRadius * 3.2f
        ),
        radius = baseRadius * 3.2f,
        center = Offset(cx, cy)
    )

    // --- 2. Collect all ring segments with their 3D depth so we can sort ---
    data class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val z: Float, val ringIdx: Int, val onLeader: Boolean)

    val segments = ArrayList<Seg>(3 * 72)

    rings.forEachIndexed { idx, ring ->
        val segCount = 72
        val selfSpin = clock * (2f * Math.PI).toFloat() * ring.spin

        // Build ring points in 3D
        val pts = Array(segCount) { i ->
            val t = (i.toFloat() / segCount) * (2f * Math.PI).toFloat()
            var p = V3(
                x = cos(t) * (baseRadius * ring.radius),
                y = sin(t) * (baseRadius * ring.radius),
                z = 0f
            )
            // Ring's own tilt
            p = rotateX(p, ring.rx)
            p = rotateY(p, ring.ry)
            p = rotateZ(p, ring.rz)
            // Global frame rotation
            p = rotateY(p, globalY)
            p = rotateX(p, globalX)
            p
        }

        // Leader arc range in ring-parameter space
        val leaderCenter = selfSpin
        val leaderHalf = ring.leaderLength / 2f

        for (i in 0 until segCount) {
            val a = pts[i]
            val b = pts[(i + 1) % segCount]
            val pa = project(a, cx, cy, focal)
            val pb = project(b, cx, cy, focal)

            // Is this segment inside the leader arc?
            val tA = (i.toFloat() / segCount) * (2f * Math.PI).toFloat()
            val delta = angleDelta(tA, leaderCenter)
            val onLeader = kotlin.math.abs(delta) < leaderHalf

            segments.add(
                Seg(pa.x, pa.y, pb.x, pb.y, (pa.z + pb.z) / 2f, idx, onLeader)
            )
        }
    }

    // Sort so farthest (largest z) drawn first
    segments.sortByDescending { it.z }

    // Depth range for normalizing alpha/thickness
    val zMax = baseRadius * 2.0f
    val zMin = -baseRadius * 2.0f

    // --- 3. Draw ring segments ---
    for (seg in segments) {
        val zNorm = ((seg.z - zMin) / (zMax - zMin)).coerceIn(0f, 1f) // 0=far, 1=near
        val depthAlpha = 0.25f + zNorm * 0.75f
        val depthWidth = 1.2f + zNorm * 3.2f

        // Base ring stroke
        drawLine(
            color = accent.copy(alpha = 0.85f * depthAlpha),
            start = Offset(seg.x1, seg.y1),
            end = Offset(seg.x2, seg.y2),
            strokeWidth = depthWidth,
            cap = StrokeCap.Round
        )

        // Soft outer glow on near-half segments
        if (zNorm > 0.55f) {
            drawLine(
                color = accent.copy(alpha = 0.18f * depthAlpha),
                start = Offset(seg.x1, seg.y1),
                end = Offset(seg.x2, seg.y2),
                strokeWidth = depthWidth * 4.5f,
                cap = StrokeCap.Round
            )
        }

        // Bright leader segment
        if (seg.onLeader) {
            drawLine(
                color = Color.White.copy(alpha = 0.85f * depthAlpha),
                start = Offset(seg.x1, seg.y1),
                end = Offset(seg.x2, seg.y2),
                strokeWidth = depthWidth * 1.6f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accent.copy(alpha = 0.35f * depthAlpha),
                start = Offset(seg.x1, seg.y1),
                end = Offset(seg.x2, seg.y2),
                strokeWidth = depthWidth * 5.0f,
                cap = StrokeCap.Round
            )
        }
    }

    // --- 4. Sparks in 3D ---
    data class PS(val x: Float, val y: Float, val z: Float, val alpha: Float, val size: Float, val isEmber: Boolean)
    val projectedSparks = ArrayList<PS>(sparks.size)
    sparks.forEach { s ->
        val phi = s.phi + s.spinSpeed * clock * (2f * Math.PI).toFloat()
        var p = V3(
            x = s.r * baseRadius * sin(s.theta) * cos(phi),
            y = s.r * baseRadius * sin(s.theta) * sin(phi),
            z = s.r * baseRadius * cos(s.theta)
        )
        p = rotateY(p, globalY)
        p = rotateX(p, globalX)
        val pr = project(p, cx, cy, focal)
        val zNorm = ((pr.z - zMin) / (zMax - zMin)).coerceIn(0f, 1f)
        val alpha = (s.alpha * (0.25f + zNorm * 0.75f)).coerceIn(0f, 0.9f)
        val sz = s.sizePx * (0.6f + zNorm * 0.8f)
        projectedSparks.add(PS(pr.x, pr.y, pr.z, alpha, sz, s.isEmber))
    }
    projectedSparks.sortByDescending { it.z }

    projectedSparks.forEach { ps ->
        if (ps.isEmber) {
            drawCircle(
                color = accent.copy(alpha = ps.alpha * 0.35f),
                radius = ps.size * 3.4f,
                center = Offset(ps.x, ps.y)
            )
            drawCircle(
                color = Color.White.copy(alpha = ps.alpha),
                radius = ps.size * 0.9f,
                center = Offset(ps.x, ps.y)
            )
        } else {
            drawCircle(
                color = accent.copy(alpha = ps.alpha),
                radius = ps.size,
                center = Offset(ps.x, ps.y)
            )
        }
    }

    // --- 5. Hot core (billboarded at center, drawn last so it stays on top) ---
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.95f),
                accent.copy(alpha = 0.20f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = coreRadius * 1.6f
        ),
        radius = coreRadius * 1.6f,
        center = Offset(cx, cy)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                Color.White.copy(alpha = 0.85f),
                accent,
                accent.copy(alpha = 0.20f)
            ),
            center = Offset(cx, cy),
            radius = coreRadius
        ),
        radius = coreRadius,
        center = Offset(cx, cy)
    )
}

/**
 * Signed shortest angular distance from a to b (both in radians).
 * Result is in [-PI, PI].
 */
private fun angleDelta(a: Float, b: Float): Float {
    var d = a - b
    while (d > Math.PI) d -= (2 * Math.PI).toFloat()
    while (d < -Math.PI) d += (2 * Math.PI).toFloat()
    return d
}
