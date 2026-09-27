package com.example.focusnfc

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin

class CompletionChimePlayer {

    fun playRelaxingChime() {
        Thread {
            val sampleRate = 44100
            val durationSeconds = 3.0 // Plays for 3 seconds
            val numSamples = (sampleRate * durationSeconds).toInt()
            val samples = ShortArray(numSamples)

            // Relaxing Zen Bell harmonic frequencies (A Major chord)
            val freqs = doubleArrayOf(440.0, 554.37, 659.25, 880.0)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Exponential decay envelope for a soothing bell sound
                val envelope = exp(-1.2 * t)
                var sampleVal = 0.0
                for (f in freqs) {
                    sampleVal += sin(2.0 * Math.PI * f * t)
                }
                sampleVal = (sampleVal / freqs.size) * envelope * 24000.0
                samples[i] = sampleVal.toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .build()

            audioTrack.play()
            audioTrack.write(samples, 0, numSamples)
            audioTrack.stop()
            audioTrack.release()
        }.start()
    }
}
