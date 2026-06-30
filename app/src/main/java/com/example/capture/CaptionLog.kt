package com.example.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide buffer shared between [CaptionAccessibilityService] (writer) and
 * the diagnostics UI (reader). The service runs in the same process as the app,
 * so a singleton [StateFlow] is enough — no IPC needed.
 *
 * PHASE 1 use: the service appends candidate caption nodes here; the user opens
 * the "Canlı Yakalama" screen after a real meeting and copies [dump] so we can
 * identify the caption container's class/viewId and tune the selectors.
 */
object CaptionLog {

    private const val MAX_LINES = 400

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    /** Whether the accessibility service is currently connected (best-effort). */
    val serviceConnected = MutableStateFlow(false)

    @Synchronized
    fun append(line: String) {
        val next = (_lines.value + line).takeLast(MAX_LINES)
        _lines.value = next
    }

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
    }

    /** The buffer as a single copyable string. */
    fun dump(): String = _lines.value.joinToString("\n")
}
