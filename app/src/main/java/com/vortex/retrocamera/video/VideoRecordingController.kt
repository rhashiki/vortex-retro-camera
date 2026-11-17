package com.vortex.retrocamera.video

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.DynamicRange
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One recording at a time. State becomes SAVED only after CameraX Finalize succeeds. */
enum class VideoPhase { IDLE, PREPARING, RECORDING, PAUSED, FINALIZING, SAVED, FAILED }

data class VideoRecordingState(
    val phase: VideoPhase = VideoPhase.IDLE,
    val elapsedMs: Long = 0L,
    val outputUri: Uri? = null,
    val errorMessage: String? = null,
    val hasAudio: Boolean = false
)

/**
 * P1 Video Recorder: clean, real SDR MP4s, no visual filters yet.
 *
 * Camera ownership and lifecycle remain with MainActivity. Preview and Recorder
 * are bound together, rather than creating a second camera connection.
 * UI callbacks are dispatched by the main executor.
 */
class VideoRecordingController(
    private val context: Context,
    private val onState: (VideoRecordingState) -> Unit
) {
    var state: VideoRecordingState = VideoRecordingState()
        private set
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    val blocksRebind: Boolean
        get() = state.phase in setOf(
            VideoPhase.PREPARING, VideoPhase.RECORDING, VideoPhase.PAUSED, VideoPhase.FINALIZING
        )

    private fun publish(new: VideoRecordingState) {
        state = new
        onState(new)
    }

    /** HD preferred, CameraX falls back to an available SDR quality if needed. */
    fun createUseCase(): VideoCapture<Recorder> {
        check(!blocksRebind) { "Do not rebind while recording" }
        val quality = QualitySelector.from(
            Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(quality)
            .build()
        return VideoCapture.Builder(recorder)
            .setDynamicRange(DynamicRange.SDR)
            .build()
    }

    fun attach(useCase: VideoCapture<Recorder>) {
        check(!blocksRebind) { "Cannot replace an active recording" }
        videoCapture = useCase
        publish(VideoRecordingState())
    }

    fun detach() {
        if (blocksRebind) return
        videoCapture = null
    }

    @SuppressLint("MissingPermission")
    fun start(withAudio: Boolean) {
        if (blocksRebind) return
        val capture = videoCapture ?: run {
            publish(VideoRecordingState(VideoPhase.FAILED, errorMessage = "Câmera de vídeo ainda inicializando"))
            return
        }
        val hasAudioPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (withAudio && !hasAudioPermission) {
            publish(VideoRecordingState(VideoPhase.FAILED, errorMessage = "Permissão de microfone necessária"))
            return
        }
        publish(VideoRecordingState(VideoPhase.PREPARING, hasAudio = withAudio))
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "Vortex_${stamp}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/Vortex Retro Camera/")
            }
            val options = MediaStoreOutputOptions.Builder(
                context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(values).build()
            var pending = capture.output.prepareRecording(context, options)
            if (withAudio) pending = pending.withAudioEnabled()
            recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> publish(
                        state.copy(phase = VideoPhase.RECORDING)
                    )
                    is VideoRecordEvent.Status -> publish(
                        state.copy(elapsedMs = event.recordingStats.recordedDurationNanos / 1_000_000L)
                    )
                    is VideoRecordEvent.Pause -> publish(
                        state.copy(phase = VideoPhase.PAUSED)
                    )
                    is VideoRecordEvent.Resume -> publish(
                        state.copy(phase = VideoPhase.RECORDING)
                    )
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        val uri = event.outputResults.outputUri
                        if (!event.hasError() && uri != Uri.EMPTY) {
                            publish(state.copy(phase = VideoPhase.SAVED, outputUri = uri))
                        } else {
                            publish(state.copy(
                                phase = VideoPhase.FAILED, outputUri = null,
                                errorMessage = "Falha ao salvar vídeo (código ${event.error})"
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            recording?.close()
            recording = null
            publish(VideoRecordingState(
                VideoPhase.FAILED, errorMessage = e.localizedMessage ?: "Falha ao iniciar vídeo"
            ))
        }
    }

    fun pauseOrResume() {
        try {
            when (state.phase) {
                VideoPhase.RECORDING -> recording?.pause()
                VideoPhase.PAUSED -> recording?.resume()
                else -> Unit
            }
        } catch (e: Exception) {
            publish(state.copy(errorMessage = e.localizedMessage))
        }
    }

    fun stop() {
        if (state.phase !in setOf(VideoPhase.PREPARING, VideoPhase.RECORDING, VideoPhase.PAUSED)) return
        publish(state.copy(phase = VideoPhase.FINALIZING))
        try {
            recording?.stop()
        } catch (e: Exception) {
            recording?.close()
            recording = null
            publish(state.copy(phase = VideoPhase.FAILED,
                errorMessage = e.localizedMessage ?: "Falha ao finalizar vídeo"))
        }
    }

    /** Activity destruction requires ending recording, but success is not claimed before Finalize. */
    fun close() {
        if (blocksRebind && state.phase != VideoPhase.FINALIZING) stop()
        videoCapture = null
    }
}
