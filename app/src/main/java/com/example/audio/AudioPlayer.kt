package com.example.audio

import android.media.MediaPlayer
import java.io.IOException

class AudioPlayer {
    private var player: MediaPlayer? = null
    private var onCompletionListener: (() -> Unit)? = null

    fun play(filePath: String, onCompletion: () -> Unit) {
        stop()
        onCompletionListener = onCompletion
        player = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    onCompletionListener?.invoke()
                    stop()
                }
                start()
            } catch (e: IOException) {
                e.printStackTrace()
                onCompletionListener?.invoke()
            }
        }
    }

    fun stop() {
        player?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        player = null
    }
}
