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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
 * STEP 3: replaces the placeholder Canvas orb with a real OpenGL ES 3D orb
 * rendered on the GPU. State-driven accent color drives the shader.
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

    // Convert Color → float RGB for the GLES shader
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

            // --- GLES orb ---
            AndroidView(
                factory = { ctx ->
                    CrimsonOrbView(ctx).apply {
                        setAccent(accentR, accentG, accentB)
                    }
                },
                modifier = Modifier.size(320.dp),
                update = { view ->
                    // Called whenever the state changes (future phases)
                    view.setAccent(accentR, accentG, accentB)
                }
            )

            Spacer(Modifier.height(40.dp))

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
