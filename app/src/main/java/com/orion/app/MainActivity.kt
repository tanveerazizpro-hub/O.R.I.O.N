package com.orion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * O.R.I.O.N. — Entry point.
 *
 * For STEP 1 this activity only shows a splash screen confirming the build works.
 * The reactive orb, conversation UI, and AI systems are added in later steps.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrionTheme {
                OrionSplashScreen()
            }
        }
    }
}

/**
 * Global O.R.I.O.N. theme.
 * Dark-first, deep-space palette defined here so later phases can reuse it.
 */
@Composable
fun OrionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6C8CFF),
            onPrimary = Color(0xFF0A0E1A),
            background = Color(0xFF0A0E1A),
            onBackground = Color(0xFFE6EAF5),
            surface = Color(0xFF11162A),
            onSurface = Color(0xFFE6EAF5),
            error = Color(0xFFFF5C7A)
        ),
        content = content
    )
}

@Composable
fun OrionSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "O.R.I.O.N.",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 42.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 8.sp
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
fun OrionSplashScreenPreview() {
    OrionTheme {
        OrionSplashScreen()
    }
}
