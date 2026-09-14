package com.orion.app.core

/**
 * Global O.R.I.O.N. states.
 * The orb, voice system, and UI will all react to this single source of truth.
 */
enum class OrionState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    EXECUTING,
    SUCCESS,
    ERROR
}
