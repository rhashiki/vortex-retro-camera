package com.vortex.retrocamera.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.max

/**
 * Original synthesized mechanical 35 mm shutter + film-advance sound.
 * No copyrighted recording or external asset. Works fully offline.
 */
class MechanicalShutter {
    private val sampleRate = 22050
    private val pcm: ShortArray = synthesize()
    private var track: AudioTrack? = try {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build().also { it.write(pcm, 0, pcm.size) }
    } catch (_: Exception) { null }

    fun play() {
        try {
            val t = track ?: return
            t.pause()
            t.reloadStaticData()
            t.play()
        } catch (_: Exception) { }
    }

    fun release() {
        try { track?.release() } catch (_: Exception) { }
        track = null
    }

    private fun synthesize(): ShortArray {
        val duration = 0.65
        val size = (sampleRate * duration).toInt()
        val out = ShortArray(size)
        var rng = 0x6A37D94B.toInt()
        fun noise(): Double {
            rng = rng xor (rng shl 13)
            rng = rng xor (rng ushr 17)
            rng = rng xor (rng shl 5)
            return ((rng and 0xffff) / 32767.5) - 1.0
        }
        // Multiple metal curtain clicks, short resonant spring, subdued wind lever.
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            fun click(at: Double, decay: Double, gain: Double): Double {
                val dt = t - at
                if (dt < 0.0 || dt > 0.17) return 0.0
                val metallic = sin(2.0 * PI * (1550.0 * dt + 420.0 * dt * dt))
                return gain * exp(-dt / decay) * (0.70 * noise() + 0.30 * metallic)
            }
            var value = click(0.015, 0.008, 0.83) + click(0.064, 0.013, 0.75)
            value += click(0.113, 0.023, 0.22)
            // The motor/film advance has a mechanical flutter rather than an electronic beep.
            val motorTime = t - 0.205
            if (motorTime in 0.0..0.33) {
                val envelope = sin(PI * motorTime / 0.33).coerceAtLeast(0.0)
                val frequency = 74.0 + 24.0 * sin(PI * motorTime / 0.33)
                val flutter = sin(2.0 * PI * (frequency * motorTime + 7.0 * motorTime * motorTime))
                value += envelope * (0.15 * flutter + 0.105 * noise())
            }
            val clipped = (value * 0.80).coerceIn(-0.94, 0.94)
            out[i] = (clipped * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
