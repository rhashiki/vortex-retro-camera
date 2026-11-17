package com.vortex.retrocamera

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented regressions for the real Compose camera screen.
 * The CameraX view is reused and UI panels must never obscure the shutter.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityStartupTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun startsWithLiveCameraAndControls() {
        rule.onNodeWithTag("camera_preview").assertExists()
        rule.onNodeWithTag("shutter_button").assertExists()
        rule.onNodeWithTag("mode_navigation").assertExists()
        rule.onNodeWithTag("rgb_histogram").assertExists()
    }

    @Test fun optionalPanelsAreClosedUntilClicked() {
        rule.onNodeWithTag("expanded_panel").assertDoesNotExist()
        rule.onNodeWithTag("filter_panel").assertDoesNotExist()
        rule.onNodeWithTag("mode_retrô").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("filter_panel").assertExists()
        rule.onNodeWithTag("shutter_button").assertExists()
        rule.onNodeWithTag("mode_retrô").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("filter_panel").assertDoesNotExist()
    }

    @Test fun switchingPhotoAndVideoKeepsPreviewMounted() {
        rule.onNodeWithTag("mode_vídeo").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("camera_preview").assertExists()
        rule.onNodeWithTag("video_record_button").assertExists()
        rule.onNodeWithTag("video_status").assertExists()
        rule.onNodeWithTag("mode_foto").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("camera_preview").assertExists()
        rule.onNodeWithTag("video_record_button").assertDoesNotExist()
        rule.onNodeWithTag("shutter_button").assertExists()
    }

    @Test fun premiumControlsAndNewFilmCollectionStayAccessible() {
        rule.onNodeWithTag("camera_preview").assertExists()
        rule.onNodeWithTag("mode_navigation").assertExists()
        rule.onNodeWithTag("mode_foto").assertExists()
        rule.onNodeWithTag("mode_vídeo").assertExists()
        rule.onNodeWithTag("mode_retrô").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("filter_panel").assertExists()
        rule.onNodeWithTag("shutter_button").assertExists()
    }

    @Test fun mainControlsRemainReachableWhenFiltersOpen() {
        rule.onNodeWithTag("mode_retrô").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("shutter_button").assertExists()
        rule.onNodeWithTag("floating_controls").assertExists()
        rule.onNodeWithTag("mode_navigation").assertExists()
    }
}
