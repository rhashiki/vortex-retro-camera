package com.vortex.retrocamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera

/** Opt-in SENSOR_MANUAL controls. Feature detection is mandatory. */
class ManualCamera(private val camera: Camera) {
    private val info = Camera2CameraInfo.from(camera.cameraInfo)
    private val capabilities = info.getCameraCharacteristic(
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
    ) ?: intArrayOf()
    val supportsManualSensor = capabilities.contains(
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
    )
    private val isoRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    private val timeRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

    fun apply(iso: Int, exposureNs: Long) {
        if (!supportsManualSensor) return
        val safeIso = isoRange?.let { iso.coerceIn(it.lower, it.upper) } ?: iso
        val safeTime = timeRange?.let { exposureNs.coerceIn(it.lower, it.upper) } ?: exposureNs
        val request = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, safeIso)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, safeTime)
            .build()
        Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(request)
    }

    fun reset() {
        Camera2CameraControl.from(camera.cameraControl).clearCaptureRequestOptions()
    }
}
