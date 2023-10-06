/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.input.cts

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.server.wm.CtsWindowInfoUtils
import android.server.wm.WindowManagerStateHelper
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource

/**
 * A test rule that sets up a virtual display, and launches the [CaptureEventActivity] on that
 * display.
 */
class VirtualDisplayActivityScenarioRule : ExternalResource() {

    companion object {
        const val VIRTUAL_DISPLAY_NAME = "CtsTouchScreenTestVirtualDisplay"
        const val WIDTH = 480
        const val HEIGHT = 800
        const val DENSITY = 160
        const val ORIENTATION_0 = Surface.ROTATION_0
        const val ORIENTATION_90 = Surface.ROTATION_90
        const val ORIENTATION_180 = Surface.ROTATION_180
        const val ORIENTATION_270 = Surface.ROTATION_270

        /** See [DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH].  */
        const val VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 shl 6

        /** See [DisplayManager.VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT].  */
        const val VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 1 shl 7
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var reader: ImageReader

    lateinit var virtualDisplay: VirtualDisplay
    lateinit var activity: CaptureEventActivity
    val displayId: Int get() = virtualDisplay.display.displayId

    /**
     * Before the test starts, set up the virtual display and start the [CtsEventActivity] on that
     * display.
     */
    override fun before() {
        assumeTrue(supportsMultiDisplay())
        createDisplay()

        val bundle =
            ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                .toBundle()
        val intent = Intent(Intent.ACTION_VIEW)
            .setClass(instrumentation.targetContext, CaptureEventActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        SystemUtil.runWithShellPermissionIdentity({
            activity = instrumentation.startActivitySync(intent, bundle) as CaptureEventActivity
        }, Manifest.permission.INTERNAL_SYSTEM_WINDOW)
        waitUntilActivityReadyForInput()
    }

    /**
     * Clean up after the test completes.
     */
    override fun after() {
        if (!supportsMultiDisplay()) {
            return
        }
        releaseDisplay()
        activity.finish()
    }

    /**
     * This is a helper methods for tests to make assertions with the display rotated to the given
     * orientation.
     *
     * @param orientation The orientation to which the display should be rotated.
     * @param runnable The function to run with the display is in the given orientation.
     */
    fun runInDisplayOrientation(orientation: Int, runnable: () -> Unit) {
        val initialUserRotation =
            SystemUtil.runShellCommandOrThrow("wm user-rotation -d $displayId")!!
        SystemUtil.runShellCommandOrThrow("wm user-rotation -d $displayId lock $orientation")
        waitUntilActivityReadyForInput()

        try {
            runnable()
        } finally {
            SystemUtil.runShellCommandOrThrow(
                "wm user-rotation -d $displayId $initialUserRotation")
        }
    }

    private fun supportsMultiDisplay(): Boolean {
        return instrumentation.targetContext.packageManager
            .hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)
    }

    private fun createDisplay() {
        val displayManager =
            instrumentation.targetContext.getSystemService(DisplayManager::class.java)

        val displayCreated = CountDownLatch(1)
        displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                displayCreated.countDown()
                displayManager.unregisterDisplayListener(this)
            }
        }, Handler(Looper.getMainLooper()))
        reader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2)
        virtualDisplay = displayManager.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME, WIDTH, HEIGHT, DENSITY, reader.surface,
            VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH or VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT)

        assertTrue(displayCreated.await(5, TimeUnit.SECONDS))
    }

    private fun releaseDisplay() {
        virtualDisplay.release()
        reader.close()
    }

    private fun waitUntilActivityReadyForInput() {
        // If we requested an orientation change, just waiting for the window to be visible is not
        // sufficient. We should first wait for the transitions to stop, and the for app's UI thread
        // to process them before making sure the window is visible.
        WindowManagerStateHelper().waitForAppTransitionIdleOnDisplay(displayId)
        instrumentation.uiAutomation.syncInputTransactions()
        instrumentation.waitForIdleSync()
        assertTrue("Window did not become visible",
            CtsWindowInfoUtils.waitForWindowOnTop(activity.window!!))
    }
}
