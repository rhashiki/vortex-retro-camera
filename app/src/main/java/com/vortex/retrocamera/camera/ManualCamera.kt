package com.vortex.retrocamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera

/**
 * Combines manual ISO/shutter and optical focus into ONE Camera2 interop request.
 * Never let independently submitted settings silently wipe each other.
 */
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
    val minFocusDistance: Float =
        info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
    val supportsManualFocus: Boolean get() = minFocusDistance > 0f

    private var manualExposure = false
    private var iso = 400
    private var shutterNs = 8_000_000L
    private var focusDiopters: Float? = null

    fun apply(iso: Int, exposureNs: Long) {
        if (!supportsManualSensor) return
        this.iso = isoRange?.let { iso.coerceIn(it.lower, it.upper) } ?: iso
        this.shutterNs = timeRange?.let { exposureNs.coerceIn(it.lower, it.upper) } ?: exposureNs
        manualExposure = true
        updateRequest()
    }

    /**
     * 0 = infinity, 1 = nearest optical focus supported by this lens.
     */
    fun setManualFocus(normalized: Float): Boolean {
        if (!supportsManualFocus) return false
        focusDiopters = normalized.coerceIn(0f, 1f) * minFocusDistance
        updateRequest()
        return true
    }

    fun setAutoFocus() {
        focusDiopters = null
        updateRequest()
    }

    fun clearExposure() {
        manualExposure = false
        updateRequest()
    }

    fun reset() {
        manualExposure = false
        focusDiopters = null
        updateRequest()
    }

    private fun updateRequest() {
        val control = Camera2CameraControl.from(camera.cameraControl)
        if (!manualExposure && focusDiopters == null) {
            control.clearCaptureRequestOptions()
            return
        }
        val builder = CaptureRequestOptions.Builder()
        if (manualExposure && supportsManualSensor) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterNs)
        }
        focusDiopters?.let { diopters ->
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
        }
        control.setCaptureRequestOptions(builder.build())
    }
}
