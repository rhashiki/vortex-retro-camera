package com.vortex.retrocamera

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
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
    private fun findWithDescription(root: View, description: String): View? {
        if (root.contentDescription?.toString() == description) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findWithDescription(root.getChildAt(i), description)?.let { return it }
            }
        }
        return null
    }

    @Test fun filtersStayHiddenUntilExplicitlyOpened() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                val drawer = findWithDescription(root, "Carrossel de filtros e efeitos")
                val toggle = findWithDescription(root, "Selecionar película fotográfica")
                assertNotNull("Filter drawer must exist", drawer)
                assertNotNull("Filter toggle must exist", toggle)
                assertEquals("Filter thumbnails must be hidden at launch", View.GONE, drawer!!.visibility)
                toggle!!.performClick()
                assertEquals("Filter thumbnails should appear on opening", View.VISIBLE, drawer.visibility)
                toggle.performClick()
                assertEquals("Filter thumbnails must hide after closing", View.GONE, drawer.visibility)
            }
        }
    }

    @Test fun visualLayoutKeepsModesAndShutterUnderViewfinder() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val visor = findWithDescription(content, "Visor da câmera")
                val modes = findWithDescription(content, "Navegação de modos")
                val shutter = findWithDescription(content, "Área de disparo")
                val films = findWithDescription(content, "Selecionar película fotográfica")
                assertNotNull("Visor should be visible", visor)
                assertNotNull("Mode navigation should be visible", modes)
                assertNotNull("Shutter controls should be visible", shutter)
                assertNotNull("Collapsed filters should be visible", films)
                val p1 = IntArray(2)
                val p2 = IntArray(2)
                val p3 = IntArray(2)
                val p4 = IntArray(2)
                visor!!.getLocationOnScreen(p1)
                modes!!.getLocationOnScreen(p2)
                shutter!!.getLocationOnScreen(p3)
                films!!.getLocationOnScreen(p4)
                assertTrue("Visor must be above modes", p1[1] < p2[1])
                assertTrue("Modes must be above shutter", p2[1] < p3[1])
                assertTrue("Shutter must be above filters", p3[1] < p4[1])
                assertEquals("Filters remain collapsed on launch",
                    View.GONE, findWithDescription(content, "Carrossel de filtros e efeitos")!!.visibility)
            }
        }
    }

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
