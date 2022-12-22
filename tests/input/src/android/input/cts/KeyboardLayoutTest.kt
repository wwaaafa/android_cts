/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.input.UinputDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read and parse <keyboard-layout> attributes into KeyboardLayout
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class KeyboardLayoutTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val rule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var inputManager: InputManager
    private lateinit var virtualDevice: UinputDevice
    private lateinit var keyboard: InputDevice

    @Before
    fun setUp() {
        rule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }

        virtualDevice = UinputDevice.create(
                instrumentation, R.raw.test_keyboard_register,
                InputDevice.SOURCE_KEYBOARD
        )

        // Wait for device to be added
        PollingCheck.waitFor { inputManager.getInputDevice(virtualDevice.deviceId) != null }
        keyboard = inputManager.getInputDevice(virtualDevice.deviceId)
    }

    @After
    fun tearDown() {
        virtualDevice.close()
    }

    @Test
    fun testKeyboardLayoutType_CorrectlyInitialized() {
        val englishQwertyLayoutId = getKeyboardLayoutId(keyboard, "english_us_qwerty")
        val englishUndefinedLayoutId = getKeyboardLayoutId(keyboard, "english_us_undefined")

        assertNotEquals("English qwerty layout should not be empty", "", englishQwertyLayoutId)
        assertNotEquals(
                "English undefined layout should not be empty",
                "",
                englishUndefinedLayoutId
        )

        assertEquals(
                "Layout type should be qwerty",
                "qwerty",
                englishQwertyLayoutId?.let {
                    inputManager.getKeyboardLayoutTypeForLayoutDescriptor(it)
                }
        )
        assertEquals(
                "Layout type should be undefined",
                "undefined",
                englishUndefinedLayoutId?.let {
                    inputManager.getKeyboardLayoutTypeForLayoutDescriptor(it)
                }
        )
    }

    /**
     * Returns the first matching keyboard layout id that is supported by the provided input device
     * and matches the provided language.
     *
     * @param device The input device for which to query the keyboard layouts.
     * @param language The language to query for.
     * @return The first matching keyboard layout descriptor or an empty string if none was found.
     */
    private fun getKeyboardLayoutId(device: InputDevice, language: String): String? {
        return SystemUtil.runWithShellPermissionIdentity<String>({
            for (kl in inputManager.getKeyboardLayoutDescriptorsForInputDevice(device)) {
                if (kl.endsWith(language)) {
                    return@runWithShellPermissionIdentity kl
                }
            }
            fail("Failed to get keyboard layout for language $language")
            ""
        }, Manifest.permission.INTERACT_ACROSS_USERS)
    }

    class CtsKeyboardLayoutProvider : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Nothing to do at this time.
        }
    }
}
