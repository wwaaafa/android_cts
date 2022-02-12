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

package android.accessibilityservice.cts;

import static android.accessibility.cts.common.InstrumentedAccessibilityService.enableService;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.InputMethod;
import android.accessibilityservice.cts.activities.AccessibilityEndToEndActivity;
import android.accessibilityservice.cts.utils.AsyncUtils;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.LargeTest;
import android.widget.EditText;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

/**
 * Test one a11y service requiring ime capabilities and one doesn't.
 */
@LargeTest
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class AccessibilityImeTest {
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private static StubImeAccessibilityService sStubImeAccessibilityService;
    private static StubNonImeAccessibilityService sStubNonImeAccessibilityService;

    private AccessibilityEndToEndActivity mActivity;

    private ActivityTestRule<AccessibilityEndToEndActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityEndToEndActivity.class, false, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private EditText mEditText;
    private String mInitialText;

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        sInstrumentation
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        sStubImeAccessibilityService = enableService(StubImeAccessibilityService.class);
        sStubNonImeAccessibilityService = enableService(StubNonImeAccessibilityService.class);
    }

    @AfterClass
    public static void postTestTearDown() {
        sStubImeAccessibilityService.disableSelfAndRemove();
        sStubNonImeAccessibilityService.disableSelfAndRemove();
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
        // focus the edit text
        mEditText = mActivity.findViewById(R.id.edittext);
        // initial text
        mInitialText = mActivity.getString(R.string.text_input_blah);
    }

    @Test
    public void testInputConnection_requestIme() throws InterruptedException {
        CountDownLatch startInputLatch = new CountDownLatch(1);
        sStubImeAccessibilityService.setStartInputCountDownLatch(startInputLatch);

        mActivity.runOnUiThread(() -> {
            mEditText.requestFocus();
            mEditText.setSelection(mInitialText.length(), mInitialText.length());
        });

        assertTrue("time out waiting for input to start",
                startInputLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));
        assertNotNull(sStubImeAccessibilityService.getInputMethod());
        InputMethod.AccessibilityInputConnection connection =
                sStubImeAccessibilityService.getInputMethod().getCurrentInputConnection();
        assertNotNull(connection);

        CountDownLatch selectionChangeLatch = new CountDownLatch(1);
        sStubImeAccessibilityService.setSelectionChangeLatch(selectionChangeLatch);
        sStubImeAccessibilityService.setSelectionTarget(mInitialText.length() * 2);

        connection.commitText(mInitialText, 1, null);

        assertTrue("time out waiting for selection change",
                selectionChangeLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));
        assertEquals(mInitialText + mInitialText, mEditText.getText().toString());
    }

    @Test
    public void testInputConnection_notRequestIme() throws InterruptedException {
        CountDownLatch startInputLatch = new CountDownLatch(1);
        sStubNonImeAccessibilityService.setStartInputCountDownLatch(startInputLatch);

        mActivity.runOnUiThread(() -> {
            mEditText.requestFocus();
            mEditText.setSelection(mInitialText.length(), mInitialText.length());
        });

        assertFalse("should time out waiting for input to start",
                startInputLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));
        assertNull(sStubNonImeAccessibilityService.getInputMethod());
    }

    @Test
    public void testSelectionChange_requestIme() throws InterruptedException {
        CountDownLatch startInputLatch = new CountDownLatch(1);
        sStubImeAccessibilityService.setStartInputCountDownLatch(startInputLatch);

        mActivity.runOnUiThread(() -> {
            mEditText.requestFocus();
            mEditText.setSelection(mInitialText.length(), mInitialText.length());
        });

        final int targetPos = mInitialText.length() - 1;
        assertTrue("time out waiting for input to start",
                startInputLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));
        assertNotNull(sStubImeAccessibilityService.getInputMethod());
        InputMethod.AccessibilityInputConnection connection =
                sStubImeAccessibilityService.getInputMethod().getCurrentInputConnection();
        assertNotNull(connection);

        CountDownLatch selectionChangeLatch = new CountDownLatch(1);
        sStubImeAccessibilityService.setSelectionChangeLatch(selectionChangeLatch);
        sStubImeAccessibilityService.setSelectionTarget(targetPos);

        connection.setSelection(targetPos, targetPos);
        assertTrue("time out waiting for selection change",
                selectionChangeLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));

        assertEquals(targetPos, mEditText.getSelectionStart());
        assertEquals(targetPos, mEditText.getSelectionEnd());

        assertEquals(targetPos, sStubImeAccessibilityService.selStart);
        assertEquals(targetPos, sStubImeAccessibilityService.selEnd);
    }

    @Test
    public void testSelectionChange_notRequestIme() throws InterruptedException {
        mActivity.runOnUiThread(() -> {
            mEditText.requestFocus();
            mEditText.setSelection(mInitialText.length(), mInitialText.length());
        });

        final int targetPos = mInitialText.length() - 1;
        CountDownLatch selectionChangeLatch = new CountDownLatch(1);
        sStubNonImeAccessibilityService.setSelectionChangeLatch(selectionChangeLatch);
        sStubNonImeAccessibilityService.setSelectionTarget(targetPos);

        mActivity.runOnUiThread(() -> {
            mEditText.setSelection(targetPos, targetPos);
        });
        assertFalse("should time out waiting for selection change",
                selectionChangeLatch.await(AsyncUtils.DEFAULT_TIMEOUT_MS, MILLISECONDS));

        assertEquals(targetPos, mEditText.getSelectionStart());
        assertEquals(targetPos, mEditText.getSelectionEnd());

        assertEquals(-1, sStubNonImeAccessibilityService.oldSelStart);
        assertEquals(-1, sStubNonImeAccessibilityService.oldSelEnd);
        assertEquals(-1, sStubNonImeAccessibilityService.selStart);
        assertEquals(-1, sStubNonImeAccessibilityService.selEnd);
    }
}
