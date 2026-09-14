package com.orion.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Global O.R.I.O.N. theme.
 * Dark-first, deep-space palette.
 * Later phases can extend this with typography and shapes.
 */
@Composable
fun OrionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary      = OrionBlue,
            onPrimary    = DeepSpace,
            background   = DeepSpace,
            onBackground = OnBackground,
            surface      = Surface1,
            onSurface    = OnBackground,
            error        = StateError
        ),
        content = content
    )
}
