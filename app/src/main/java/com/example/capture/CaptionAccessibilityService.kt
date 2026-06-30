package com.example.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads on-screen meeting captions (Google Meet / Teams) and — once selectors
 * are tuned — streams them as transcript segments. This is the mobile
 * counterpart of the web extension's caption-primary mode: it works even with
 * headphones, because it reads the rendered caption TEXT rather than capturing
 * audio (which Android forbids for other apps' VoIP streams).
 *
 * PHASE 1 (current): DIAGNOSTIC mode. On every caption-ish event it walks the
 * active window and records each text-bearing node (class#viewId + text) into
 * [CaptionLog]. The user runs a real Meet call, then copies the log so we can
 * identify the caption container and tune [extractCaptions] — same workflow we
 * used for the Teams web scraper. No Firestore writes yet.
 */
class CaptionAccessibilityService : AccessibilityService() {

    private var lastEmitMs = 0L
    private val recentOrder = ArrayDeque<String>()
    private val recentSet = HashSet<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        CaptionLog.serviceConnected.value = true
        CaptionLog.append("[bağlandı] Servis aktif. Meet/Teams'te altyazıyı (CC) aç ve konuş — düğümler burada görünecek.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // Throttle: caption updates fire rapidly; one sweep every ~120ms is plenty.
        val now = System.currentTimeMillis()
        if (now - lastEmitMs < 120) return

        val root = rootInActiveWindow ?: event.source ?: return
        val collected = ArrayList<String>()
        try {
            traverse(root, 0, collected)
        } catch (e: Exception) {
            // Node tree can become stale mid-walk; ignore and wait for next event.
        }
        if (collected.isEmpty()) return
        lastEmitMs = now

        val stamp = android.text.format.DateFormat.format("HH:mm:ss", now).toString()
        for (line in collected) {
            if (recentSet.contains(line)) continue
            recentSet.add(line)
            recentOrder.addLast(line)
            if (recentOrder.size > 80) {
                val old = recentOrder.removeFirst()
                recentSet.remove(old)
            }
            CaptionLog.append("$stamp [$pkg] $line")
        }
    }

    /** Collect text-bearing nodes as "<ClassName#viewId> text" for diagnostics. */
    private fun traverse(node: AccessibilityNodeInfo?, depth: Int, out: MutableList<String>) {
        node ?: return
        if (depth > 25 || out.size > 200) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text.length in 1..400) {
            val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
            val vid = node.viewIdResourceName?.substringAfterLast('/') ?: "-"
            out.add("<$cls#$vid> $text")
        }
        val count = node.childCount
        for (i in 0 until count) {
            traverse(node.getChild(i), depth + 1, out)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        CaptionLog.serviceConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        CaptionLog.serviceConnected.value = false
    }
}
