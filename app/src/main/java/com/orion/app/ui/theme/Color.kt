package com.orion.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.orion.app.core.OrionState

// --- Core palette ---
val DeepSpace        = Color(0xFF0A0E1A)
val Surface1         = Color(0xFF11162A)
val Surface2         = Color(0xFF181E36)

val OrionBlue        = Color(0xFF6C8CFF)
val OrionBlueDark    = Color(0xFF2A3A8C)
val OrionBlueLight   = Color(0xFFB9C8FF)

val OnBackground       = Color(0xFFE6EAF5)
val OnBackgroundMuted  = Color(0xFF8891B0)

// --- Per-state accent colors ---
val StateIdle       = Color(0xFF6C8CFF)
val StateListening  = Color(0xFF5FE3B6)
val StateThinking   = Color(0xFFB98CFF)
val StateSpeaking   = Color(0xFF6C8CFF)
val StateExecuting  = Color(0xFFFFB86C)
val StateSuccess    = Color(0xFF6CE39A)
val StateError      = Color(0xFFFF5C7A)

/**
 * Maps an OrionState to its accent color.
 * Kept here so the state enum stays UI-agnostic.
 */
fun accentFor(state: OrionState): Color = when (state) {
    OrionState.IDLE      -> StateIdle
    OrionState.LISTENING -> StateListening
    OrionState.THINKING  -> StateThinking
    OrionState.SPEAKING  -> StateSpeaking
    OrionState.EXECUTING -> StateExecuting
    OrionState.SUCCESS   -> StateSuccess
    OrionState.ERROR     -> StateError
}
