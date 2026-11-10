package com.vortex.retrocamera

import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: the 0.5.0 neon UI crashed on launch because setMode() accessed
 * the old proPanel lateinit property before buildInterface() initialized it.
 *
 * No camera permission needed: this must pass before any camera hardware bind.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityStartupTest {
    @Test fun opensCameraScreenBeforeCameraPermission() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                assertNotNull(content)
                assertTrue("Camera root view should be mounted", content.childCount > 0)
            }
        }
    }
}
