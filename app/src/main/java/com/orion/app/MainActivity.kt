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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orion.app.core.OrionState
import com.orion.app.ui.orb.OrionOrb
import com.orion.app.ui.theme.OrionTheme

/**
 * O.R.I.O.N. — Entry point.
 *
 * STEP 2: shows the reactive orb (idle state) plus the O.R.I.O.N. wordmark.
 * No AI, no voice, no permissions yet — those arrive in later phases.
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
    // For now the state is fixed at IDLE.
    // Later phases will drive this from the conversation/voice systems.
    val state = OrionState.IDLE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OrionOrb(state = state, sizeDp = 260)
            Spacer(Modifier.height(48.dp))
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
