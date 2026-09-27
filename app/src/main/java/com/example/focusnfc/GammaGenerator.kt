package com.example.focusnfc

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

class GammaGenerator {
    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    private var audioThread: Thread? = null

    fun startGamma(baseFreq: Double = 200.0) {
        if (isPlaying) return
        isPlaying = true

        val sampleRate = 44100
        val leftFreq = baseFreq
        val rightFreq = baseFreq + 40.0 // 40Hz difference for Gamma wave

        val minBuffSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffSize)
            .build()

        audioTrack?.play()

        audioThread = Thread {
            val samples = ShortArray(minBuffSize)
            var sampleIndex = 0

            while (isPlaying) {
                for (i in 0 until minBuffSize step 2) {
                    val t = sampleIndex.toDouble() / sampleRate
                    val leftVal = (sin(2.0 * Math.PI * leftFreq * t) * 28000).toInt().toShort()
                    val rightVal = (sin(2.0 * Math.PI * rightFreq * t) * 28000).toInt().toShort()
                    samples[i] = leftVal      // Left Ear
                    samples[i + 1] = rightVal  // Right Ear
                    sampleIndex++
                }
                audioTrack?.write(samples, 0, minBuffSize)
            }
        }
        audioThread?.start()
    }

    fun stopGamma() {
        isPlaying = false
        audioThread?.join(500)
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
