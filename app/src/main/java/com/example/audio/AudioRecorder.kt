package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    fun startRecording(): String? {
        val outputDir = context.cacheDir
        val outputFile = File.createTempFile("audio_note_", ".mp4", outputDir)
        currentOutputFile = outputFile

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val attributionContext = context.createAttributionContext("audio_recording")
            MediaRecorder(attributionContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)

            try {
                prepare()
                start()
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }
        return outputFile.absolutePath
    }

    fun stopRecording(): String? {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: RuntimeException) {
            // Can happen if stopped immediately after starting
            currentOutputFile?.delete()
            currentOutputFile = null
        }
        recorder = null
        return currentOutputFile?.absolutePath
    }
}
