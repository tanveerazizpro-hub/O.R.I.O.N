package com.orion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.orion.app.core.OrionState
import com.orion.app.ui.orb.CrimsonOrbView
import com.orion.app.ui.theme.OrionTheme
import com.orion.app.ui.theme.accentFor

/**
 * O.R.I.O.N. — Entry point.
 *
 * STEP 3.2:
 *  - Real GLES plasma orb in the center.
 *  - Compose-drawn radial glow behind it so the orb bleeds light into space.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrionTheme {
                OrionHomeScreen()
            }
        }
    }
}

@Composable
fun OrionHomeScreen() {
    // Fixed to IDLE for now. Later phases drive this from the conversation engine.
    val state = OrionState.IDLE
    val accent = accentFor(state)

    val accentR = accent.red
    val accentG = accent.green
    val accentB = accent.blue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // --- Orb + glow stack ---
            Box(
                modifier = Modifier.size(360.dp),
                contentAlignment = Alignment.Center
            ) {
                // Soft outer glow behind the orb (Compose layer)
                Box(
                    modifier = Modifier
                        .size(360.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.35f),
                                    accent.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                        .blur(48.dp)
                )

                // The GLES orb on top
                AndroidView(
                    factory = { ctx ->
                        CrimsonOrbView(ctx).apply {
                            setAccent(accentR, accentG, accentB)
                            setEnergy(0.5f)
                        }
                    },
                    modifier = Modifier.size(300.dp),
                    update = { view ->
                        view.setAccent(accentR, accentG, accentB)
                        view.setEnergy(0.5f)
                    }
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "O.R.I.O.N.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 14.sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = state.name,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                fontSize = 11.sp,
                letterSpacing = 6.sp
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
fun OrionHomeScreenPreview() {
    OrionTheme {
        OrionHomeScreen()
    }
}
