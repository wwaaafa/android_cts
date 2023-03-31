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

import android.app.StatusBarManager
import android.graphics.Point
import android.input.cts.VirtualDisplayActivityScenarioRule.Companion.HEIGHT
import android.input.cts.VirtualDisplayActivityScenarioRule.Companion.WIDTH
import android.util.Size
import android.view.InputDevice
import android.view.InputDevice.SOURCE_KEYBOARD
import android.view.InputDevice.SOURCE_STYLUS
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.input.UinputDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create a virtual device that supports stylus buttons, and ensure that interactions with those
 * stylus buttons are sent to apps as [MotionEvent]s or system as [KeyEvent]s when stylus buttons
 * are enabled.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class StylusButtonInputEventTest {
    private companion object {
        // The settings namespace and key for enabling stylus button interactions.
        const val SETTING_NAMESPACE_KEY = "secure stylus_buttons_enabled"

        const val EV_SYN = 0
        const val SYN_REPORT = 0
        const val EV_KEY = 1
        const val KEY_DOWN = 1
        const val KEY_UP = 0

        val INITIAL_SYSTEM_KEY = KeyEvent.KEYCODE_UNKNOWN
        val LINUX_TO_ANDROID_KEYCODE_MAP =
            mapOf<Int /* Linux keycode */, Int /* Android keycode */>(
                0x14b to KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY, // BTN_STYLUS
                0x14c to KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY, // BTN_STYLUS2
                0x149 to KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY, // BTN_STYLUS3
            )
        val LINUX_KEYCODE_TO_MOTIONEVENT_BUTTON =
            mapOf<Int, Int>(
                0x14b to MotionEvent.BUTTON_STYLUS_PRIMARY, // BTN_STYLUS
                0x14c to MotionEvent.BUTTON_STYLUS_SECONDARY, // BTN_STYLUS2
            )
    }

    @get:Rule val virtualDisplayRule = VirtualDisplayActivityScenarioRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var statusBarManager: StatusBarManager
    private lateinit var initialStylusButtonsEnabledSetting: String

    @Before
    fun setUp() {
        initialStylusButtonsEnabledSetting =
            SystemUtil.runShellCommandOrThrow("settings get $SETTING_NAMESPACE_KEY")
        statusBarManager =
            instrumentation.targetContext.getSystemService(StatusBarManager::class.java)

        // Send an unrelated system key to the status bar so last stylus system key history is not
        // preserved between tests.
        SystemUtil.runWithShellPermissionIdentity {
            statusBarManager.handleSystemKey(KeyEvent(KeyEvent.ACTION_DOWN, INITIAL_SYSTEM_KEY))
        }
    }

    @After
    fun tearDown() {
        SystemUtil.runShellCommandOrThrow(
            "settings put $SETTING_NAMESPACE_KEY $initialStylusButtonsEnabledSetting"
        )
    }
    @Test
    fun testStylusButtonsEnabledKeyEvents() {
        enableStylusButtons()
        val bluetoothStylus =
            UinputDevice.create(
                instrumentation,
                R.raw.test_bluetooth_stylus_register,
                SOURCE_KEYBOARD and SOURCE_STYLUS
            )

        for (button in LINUX_TO_ANDROID_KEYCODE_MAP.entries.iterator()) {
            bluetoothStylus.injectEvents(
                makeEvents(EV_KEY, button.key, KEY_DOWN, EV_SYN, SYN_REPORT, 0)
            )
            // The stylus button is expected to be sent to the status bar as a system key on the
            // down press.
            assertReceivedSystemKey(button.value)

            bluetoothStylus.injectEvents(
                makeEvents(EV_KEY, button.key, KEY_UP, EV_SYN, SYN_REPORT, 0)
            )
        }
        bluetoothStylus.close()
    }

    @Test
    fun testStylusButtonsDisabledKeyEvents() {
        disableStylusButtons()
        val bluetoothStylus =
            UinputDevice.create(
                instrumentation,
                R.raw.test_bluetooth_stylus_register,
                SOURCE_KEYBOARD and SOURCE_STYLUS
            )

        for (button in LINUX_TO_ANDROID_KEYCODE_MAP.entries.iterator()) {
            bluetoothStylus.injectEvents(
                makeEvents(EV_KEY, button.key, KEY_DOWN, EV_SYN, SYN_REPORT, 0)
            )
            bluetoothStylus.injectEvents(
                makeEvents(EV_KEY, button.key, KEY_UP, EV_SYN, SYN_REPORT, 0)
            )

            // Stylus buttons should not be sent to the status bar as a system key when stylus
            // buttons are disabled.
            assertNoSystemKey()
        }

        bluetoothStylus.close()
    }

    @Test
    fun testStylusButtonsEnabledMotionEvents() {
        enableStylusButtons()
        val uinputStylus =
            UinputTouchDevice(
                instrumentation,
                virtualDisplayRule.virtualDisplay.display,
                Size(WIDTH, HEIGHT),
                R.raw.test_capacitive_stylus_register
            )

        val pointer = Point(100, 100)
        for (button in LINUX_KEYCODE_TO_MOTIONEVENT_BUTTON.entries.iterator()) {
            pointer.offset(1, 1)

            uinputStylus.sendBtnTouch(true)
            uinputStylus.sendBtn(button.key, true)
            uinputStylus.sendDown(0, pointer, UinputTouchDevice.MT_TOOL_PEN)

            assertNextMotionEventEquals(
                MotionEvent.ACTION_DOWN,
                MotionEvent.TOOL_TYPE_STYLUS,
                button.value,
                0,
                InputDevice.SOURCE_STYLUS,
            )
            assertNextMotionEventEquals(
                MotionEvent.ACTION_BUTTON_PRESS,
                MotionEvent.TOOL_TYPE_STYLUS,
                button.value,
                button.value,
                InputDevice.SOURCE_STYLUS,
            )

            uinputStylus.sendBtnTouch(false)
            uinputStylus.sendBtn(button.key, false)
            uinputStylus.sendUp(0)

            assertNextMotionEventEquals(
                MotionEvent.ACTION_BUTTON_RELEASE,
                MotionEvent.TOOL_TYPE_STYLUS,
                0,
                button.value,
                InputDevice.SOURCE_STYLUS,
            )
            assertNextMotionEventEquals(
                MotionEvent.ACTION_UP,
                MotionEvent.TOOL_TYPE_STYLUS,
                0,
                0,
                InputDevice.SOURCE_STYLUS,
            )
        }

        uinputStylus.close()
    }

    @Test
    fun testStylusButtonsDisabledMotionEvents() {
        disableStylusButtons()
        val uinputStylus =
            UinputTouchDevice(
                instrumentation,
                virtualDisplayRule.virtualDisplay.display,
                Size(WIDTH, HEIGHT),
                R.raw.test_capacitive_stylus_register
            )

        val pointer = Point(100, 100)
        for (button in LINUX_KEYCODE_TO_MOTIONEVENT_BUTTON.entries.iterator()) {
            pointer.offset(1, 1)

            uinputStylus.sendBtnTouch(true)
            uinputStylus.sendBtn(button.key, true)
            uinputStylus.sendDown(0, pointer, UinputTouchDevice.MT_TOOL_PEN)

            assertNextMotionEventEquals(
                MotionEvent.ACTION_DOWN,
                MotionEvent.TOOL_TYPE_STYLUS,
                0,
                0,
                InputDevice.SOURCE_STYLUS,
            )

            uinputStylus.sendBtnTouch(false)
            uinputStylus.sendBtn(button.key, false)
            uinputStylus.sendUp(0)

            assertNextMotionEventEquals(
                MotionEvent.ACTION_UP,
                MotionEvent.TOOL_TYPE_STYLUS,
                0,
                0,
                InputDevice.SOURCE_STYLUS,
            )
        }

        uinputStylus.close()
    }

    private fun assertReceivedSystemKey(keycode: Int) {
        SystemUtil.runWithShellPermissionIdentity {
            PollingCheck.waitFor { statusBarManager.getLastSystemKey() == keycode }
        }
    }

    private fun assertNoSystemKey() {
        // Wait for the system to process the event.
        Thread.sleep(100)
        SystemUtil.runWithShellPermissionIdentity {
            assertEquals(INITIAL_SYSTEM_KEY, statusBarManager.getLastSystemKey())
        }
    }

    private fun assertNextMotionEventEquals(
        action: Int,
        toolType: Int,
        buttonState: Int,
        actionButton: Int,
        source: Int,
    ) {
        val event = virtualDisplayRule.activity.getInputEvent() as MotionEvent

        assertEquals(action, event.action)
        assertEquals(toolType, event.getToolType(0))
        assertEquals(buttonState, event.buttonState)
        assertEquals(actionButton, event.actionButton)
        assertTrue((source and event.source) == source)
    }

    private fun enableStylusButtons() {
        SystemUtil.runShellCommandOrThrow("settings put $SETTING_NAMESPACE_KEY 1")
    }

    private fun disableStylusButtons() {
        SystemUtil.runShellCommandOrThrow("settings put $SETTING_NAMESPACE_KEY 0")
    }
}

private fun makeEvents(vararg codes: Int): String {
    return codes.joinToString(prefix = "[", postfix = "]", separator = ",")
}
