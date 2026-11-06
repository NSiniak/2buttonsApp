package com.example.twobuttonsapp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin
import kotlin.math.PI

object SynthSoundGenerator {
    fun playYesSound(pitchFactor: Float = 1.0f) {
        Thread {
            try {
                val sampleRate = 44100
                val duration1 = 0.1
                val duration2 = 0.2
                val numSamples1 = (duration1 * sampleRate).toInt()
                val numSamples2 = (duration2 * sampleRate).toInt()
                val buffer = ShortArray(numSamples1 + numSamples2)

                val freq1 = 523.25 * pitchFactor // C5
                val freq2 = 659.25 * pitchFactor // E5

                // C5 Note
                for (i in 0 until numSamples1) {
                    val t = i.toDouble() / sampleRate
                    val fade = (numSamples1 - i).toDouble() / numSamples1
                    buffer[i] = (sin(2.0 * PI * freq1 * t) * 32767.0 * 0.4 * fade).toInt().toShort()
                }
                // E5 Note
                for (i in 0 until numSamples2) {
                    val t = i.toDouble() / sampleRate
                    val fade = (numSamples2 - i).toDouble() / numSamples2
                    buffer[numSamples1 + i] = (sin(2.0 * PI * freq2 * t) * 32767.0 * 0.4 * fade).toInt().toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun playNoSound(pitchFactor: Float = 1.0f) {
        Thread {
            try {
                val sampleRate = 44100
                val duration = 0.3
                val numSamples = (duration * sampleRate).toInt()
                val buffer = ShortArray(numSamples)

                val freq1 = 220.0 * pitchFactor // A3
                val freq2 = 225.0 * pitchFactor // Dissonant Bb3-ish buzzer tone

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val fade = (numSamples - i).toDouble() / numSamples
                    val wave1 = sin(2.0 * PI * freq1 * t)
                    val wave2 = sin(2.0 * PI * freq2 * t)
                    val combined = (wave1 + wave2) / 2.0
                    buffer[i] = (combined * 32767.0 * 0.5 * fade).toInt().toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playBuffer(buffer: ShortArray, sampleRate: Int) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()

        val playMs = (buffer.size.toDouble() / sampleRate * 1000).toLong()
        Thread.sleep(playMs + 50)
        track.stop()
        track.release()
    }
}
