package com.example.twobuttonsapp

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File

class AudioManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false

    fun startRecording(outputFile: File): Boolean {
        if (isRecording) return false
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            return false
        }
    }

    fun stopRecording(): Boolean {
        if (!isRecording) return false
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder = null
            isRecording = false
            return false
        }
    }

    fun playSound(
        soundType: String, // "default", "recorded", "custom"
        filePath: String? = null,
        uriString: String? = null,
        pitchFactor: Float = 1.0f,
        isYesButton: Boolean
    ) {
        stopPlayback()

        when (soundType) {
            "recorded" -> {
                if (filePath != null) {
                    val file = File(filePath)
                    if (file.exists()) {
                        playFile(file, pitchFactor, isYesButton)
                    } else {
                        playDefault(isYesButton, pitchFactor)
                    }
                } else {
                    playDefault(isYesButton, pitchFactor)
                }
            }
            "custom" -> {
                if (uriString != null) {
                    playUri(Uri.parse(uriString), pitchFactor, isYesButton)
                } else {
                    playDefault(isYesButton, pitchFactor)
                }
            }
            else -> {
                playDefault(isYesButton, pitchFactor)
            }
        }
    }

    private fun playDefault(isYesButton: Boolean, pitchFactor: Float) {
        if (isYesButton) {
            SynthSoundGenerator.playYesSound(pitchFactor)
        } else {
            SynthSoundGenerator.playNoSound(pitchFactor)
        }
    }

    private fun playFile(file: File, pitchFactor: Float, isYesButton: Boolean) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = playbackParams.setPitch(pitchFactor)
                }
                start()
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playDefault(isYesButton, pitchFactor)
        }
    }

    private fun playUri(uri: Uri, pitchFactor: Float, isYesButton: Boolean) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = playbackParams.setPitch(pitchFactor)
                }
                start()
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playDefault(isYesButton, pitchFactor)
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
