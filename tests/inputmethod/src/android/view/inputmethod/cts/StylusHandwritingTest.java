/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestUtils;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IMF and end-to-end Stylus handwriting tests.
 */
public class StylusHandwritingTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);
    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.StylusHandwritingTest";

    @Test
    public void testHandwritingStartAndFinish() throws Exception {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);
            imm.startStylusHandwriting(editText);
            // keyboard shouldn't show up.
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);

            // Handwriting should start
            expectEvent(
                    stream,
                    event -> "onPrepareStylusHandwriting".equals(event.getEventName()),
                    TIMEOUT);
            expectEvent(
                    stream,
                    event -> "onStartStylusHandwriting".equals(event.getEventName()),
                    TIMEOUT);

            // Verify Stylus Handwriting window is shown
            assertTrue(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                            .getReturnBooleanValue());

            // Verify calling finishStylusHandwriting() calls onFinishStylusHandwriting().
            imeSession.callFinishStylusHandwriting();
            expectEvent(
                    stream,
                    event -> "onFinishStylusHandwriting".equals(event.getEventName()),
                    TIMEOUT);
        }
    }

    /**
     * Call {@link InputMethodManager#startStylusHandwriting(View)} and inject Stylus touch events
     * on screen. Make sure InkWindow receives those events via Spy window surface.
     * @throws Exception
     */
    @Test
    public void testHandwritingStylusEvents() throws Exception {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);
            imm.startStylusHandwriting(editText);

            // Handwriting should start
            expectEvent(
                    stream,
                    event -> "onStartStylusHandwriting".equals(event.getEventName()),
                    TIMEOUT);

            // Verify Stylus Handwriting window is shown
            assertTrue(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                    .getReturnBooleanValue());

            // Verify IME stylus window receives the motion Event.
            assertTrue(expectCommand(
                    stream, imeSession.callSetStylusHandwritingWindowTouchListener(), TIMEOUT)
                    .getReturnBooleanValue());

            // inject Stylus touch events to screen
            List<MotionEvent> injectedEvents =
                    TestUtils.getStylusMoveEventsOnDisplay(0 /* displayId */);
            TestUtils.injectAll(injectedEvents);

            // get Stylus events from InkWindow.
            ArrayList<MotionEvent> capturedEvents =  expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowEvents(), TIMEOUT)
                    .getReturnParcelableArrayListValue();
            assertNotNull(capturedEvents);

            // captured events should be same as injected.
            assertEquals(injectedEvents.size(), capturedEvents.size());

            // Verify MotionEvents as well.
            // Note: we cannot just use equals() since some MotionEvent fields can change after
            // dispatch.
            Iterator<MotionEvent> capturedIt = capturedEvents.iterator();
            Iterator<MotionEvent> injectedIt = injectedEvents.iterator();
            while (injectedIt.hasNext() && capturedIt.hasNext()) {
                MotionEvent injected = injectedIt.next();
                MotionEvent captured = capturedIt.next();
                assertEquals("X should be same for MotionEvent", injected.getX(), captured.getX(),
                        5.0f);
                assertEquals("Y should be same for MotionEvent", injected.getY(), captured.getY(),
                        5.0f);
                assertEquals("Action should be same for MotionEvent",
                        injected.getAction(), captured.getAction());
            }
        }
    }

    @FlakyTest(bugId = 210039666)
    @Test
    /**
     * Inject Stylus events on top of focused editor and verify Handwriting is started and InkWindow
     * is displayed.
     */
    public void testHandwritingEndToEnd() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);

            TestUtils.injectStylusEvents(editText);
            // keyboard shouldn't show up.
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);
            // Handwriting should start
            expectEvent(
                    stream,
                    event -> "onStartStylusHandwriting".equals(event.getEventName()),
                    TIMEOUT);

            // Verify Stylus Handwriting window is shown
            assertTrue(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                            .getReturnBooleanValue());
        }
    }

    private EditText launchTestActivity(@NonNull String marker) {
        return launchTestActivity(marker, getTestMarker()).first;
    }

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private Pair<EditText, EditText> launchTestActivity(@NonNull String focusedMarker,
            @NonNull String nonFocusedMarker) {
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();
        final AtomicReference<EditText> nonFocusedEditTextRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText focusedEditText = new EditText(activity);
            focusedEditText.setHint("focused editText");
            focusedEditText.setPrivateImeOptions(focusedMarker);
            focusedEditText.requestFocus();
            focusedEditTextRef.set(focusedEditText);
            layout.addView(focusedEditText);

            final EditText nonFocusedEditText = new EditText(activity);
            nonFocusedEditText.setPrivateImeOptions(nonFocusedMarker);
            nonFocusedEditText.setHint("target editText");
            nonFocusedEditTextRef.set(nonFocusedEditText);
            layout.addView(nonFocusedEditText);
            return layout;
        });
        return new Pair<>(focusedEditTextRef.get(), nonFocusedEditTextRef.get());
    }
}
