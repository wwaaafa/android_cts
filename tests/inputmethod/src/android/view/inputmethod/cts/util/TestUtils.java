/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.inputmethod.cts.util;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertFalse;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CommonTestUtils;
import com.android.compatibility.common.util.SystemUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class TestUtils {
    private static final long TIME_SLICE = 100;  // msec
    /**
     * Executes a call on the application's main thread, blocking until it is complete.
     *
     * <p>A simple wrapper for {@link Instrumentation#runOnMainSync(Runnable)}.</p>
     *
     * @param task task to be called on the UI thread
     */
    public static void runOnMainSync(@NonNull Runnable task) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
    }

    /**
     * Retrieves a value that needs to be obtained on the main thread.
     *
     * <p>A simple utility method that helps to return an object from the UI thread.</p>
     *
     * @param supplier callback to be called on the UI thread to return a value
     * @param <T> Type of the value to be returned
     * @return Value returned from {@code supplier}
     */
    public static <T> T getOnMainSync(@NonNull Supplier<T> supplier) {
        final AtomicReference<T> result = new AtomicReference<>();
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> result.set(supplier.get()));
        return result.get();
    }

    /**
     * Does polling loop on the UI thread to wait until the given condition is met.
     *
     * @param condition Condition to be satisfied. This is guaranteed to run on the UI thread.
     * @param timeout timeout in millisecond
     * @param message message to display when timeout occurs.
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    public static void waitOnMainUntil(
            @NonNull BooleanSupplier condition, long timeout, String message)
            throws TimeoutException {
        final AtomicBoolean result = new AtomicBoolean();

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        while (!result.get()) {
            if (timeout < 0) {
                throw new TimeoutException(message);
            }
            instrumentation.runOnMainSync(() -> {
                if (condition.getAsBoolean()) {
                    result.set(true);
                }
            });
            try {
                Thread.sleep(TIME_SLICE);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            timeout -= TIME_SLICE;
        }
    }

    /**
     * Does polling loop on the UI thread to wait until the given condition is met.
     *
     * @param condition Condition to be satisfied. This is guaranteed to run on the UI thread.
     * @param timeout timeout in millisecond
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    public static void waitOnMainUntil(@NonNull BooleanSupplier condition, long timeout)
            throws TimeoutException {
        waitOnMainUntil(condition, timeout, "");
    }

    /**
     * Call a command to turn screen On.
     *
     * This method will wait until the power state is interactive with {@link
     * PowerManager#isInteractive()}.
     */
    public static void turnScreenOn() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final PowerManager pm = context.getSystemService(PowerManager.class);
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        CommonTestUtils.waitUntil("Device does not wake up after 5 seconds", 5,
                () -> pm != null && pm.isInteractive());
    }

    /**
     * Call a command to turn screen off.
     *
     * This method will wait until the power state is *NOT* interactive with
     * {@link PowerManager#isInteractive()}.
     * Note that {@link PowerManager#isInteractive()} may not return {@code true} when the device
     * enables Aod mode, recommend to add (@link DisableScreenDozeRule} in the test to disable Aod
     * for making power state reliable.
     */
    public static void turnScreenOff() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final PowerManager pm = context.getSystemService(PowerManager.class);
        runShellCommand("input keyevent KEYCODE_SLEEP");
        CommonTestUtils.waitUntil("Device does not sleep after 5 seconds", 5,
                () -> pm != null && !pm.isInteractive());
    }

    /**
     * Simulates a {@link KeyEvent#KEYCODE_MENU} event to unlock screen.
     *
     * This method will retry until {@link KeyguardManager#isKeyguardLocked()} return {@code false}
     * in given timeout.
     *
     * Note that {@link KeyguardManager} is not accessible in instant mode due to security concern,
     * so this method always throw exception with instant app.
     */
    public static void unlockScreen() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getContext();
        final KeyguardManager kgm = context.getSystemService(KeyguardManager.class);

        assertFalse("This method is currently not supported in instant apps.",
                context.getPackageManager().isInstantApp());
        CommonTestUtils.waitUntil("Device does not unlock after 3 seconds", 3,
                () -> {
                    SystemUtil.runWithShellPermissionIdentity(
                            () -> instrumentation.sendKeyDownUpSync((KeyEvent.KEYCODE_MENU)));
                    return kgm != null && !kgm.isKeyguardLocked();
                });
    }

    /**
     * Call a command to force stop the given application package.
     *
     * @param pkg The name of the package to be stopped.
     */
    public static void forceStopPackage(@NonNull String pkg) {
        runWithShellPermissionIdentity(() -> {
            runShellCommandOrThrow("am force-stop " + pkg);
        });
    }

    /**
     * Inject Stylus move on the Display inside view coordinates so that initiation can happen.
     * @param view view on which stylus events should be overlapped.
     */
    public static void injectStylusEvents(@NonNull View view) {
        int[] xy = new int[2];
        view.getLocationOnScreen(xy);
        int x = xy[0] + 150; // add some padding to avoid back gesture.
        int y = xy[1] + 50;

        final int moveCount = 10;
        final float increment = (getTouchSlop(view.getContext()) * 5) / moveCount;
        float j = increment;

        // Inject stylus ACTION_DOWN
        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = getMotionEvent(downTime, downTime, MotionEvent.ACTION_DOWN,
                x + j, y);
        injectMotionEvent(downEvent, true /* sync */);

        // Inject stylus ACTION_MOVE
        // should be greater than touch slop.
        MotionEvent moveEvent;
        for (int i = 0; i < moveCount; i++) {
            long time = SystemClock.uptimeMillis();
            j += increment;
            moveEvent = getMotionEvent(time, time, MotionEvent.ACTION_MOVE,
                    x + j, y);
            injectMotionEvent(moveEvent, true /* sync */);
        }

        // Inject stylus ACTION_UP
        long upTime = SystemClock.uptimeMillis();
        MotionEvent upEvent = getMotionEvent(upTime, upTime, MotionEvent.ACTION_UP, x + j, y);
        injectMotionEvent(upEvent, true /* sync */);
    }

    public static List<MotionEvent> getStylusMoveEventsOnDisplay(int displayId) {
        // TODO(b/210039666): use screen center.
        // TODO(b/210039666): try combining with #injectStylusEvents().
        int x = 500;
        int y = 500;
        List<MotionEvent> events = new ArrayList<>();
        final long downTime = SystemClock.uptimeMillis();
        events.add(getMotionEvent(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, displayId));

        int j = 0;
        for (int i = 0; i < 10; i++) {
            final long moveTime = SystemClock.uptimeMillis();
            j = i * 5;
            events.add(getMotionEvent(
                    moveTime, moveTime, MotionEvent.ACTION_MOVE, x + j, y + j, displayId));
        }

        final long upTime = SystemClock.uptimeMillis();
        events.add(getMotionEvent(
                upTime, upTime, MotionEvent.ACTION_UP, x + j, y + j, displayId));

        return events;
    }

    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action,
            float x, float y) {
        return getMotionEvent(downTime, eventTime, action, (int) x, (int) y, 0);
    }

    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action,
            int x, int y, int displayId) {
        // Stylus related properties.
        MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[] { new MotionEvent.PointerProperties() };
        properties[0].toolType = MotionEvent.TOOL_TYPE_STYLUS;
        properties[0].id = 1;
        MotionEvent.PointerCoords[] coords =
                new MotionEvent.PointerCoords[] { new MotionEvent.PointerCoords() };
        coords[0].x = x;
        coords[0].y = y;
        coords[0].pressure = 1;

        final MotionEvent event = MotionEvent.obtain(downTime, eventTime, action,
                1 /* pointerCount */, properties, coords, 0 /* metaState */,
                0 /* buttonState */, 1 /* xPrecision */, 1 /* yPrecision */, 0 /* deviceId */,
                0 /* edgeFlags */, InputDevice.SOURCE_STYLUS, 0 /* flags */);
        event.setDisplayId(displayId);
        return event;
    }

    private static void injectMotionEvent(MotionEvent event, boolean sync) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(
                event, sync, false /* waitAnimations */);
    }

    public static void injectAll(List<MotionEvent> events) {
        for (MotionEvent event : events) {
            injectMotionEvent(event, true /* sync */);
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation().syncInputTransactions(false);
    }
    private static int getTouchSlop(Context context) {
        return ViewConfiguration.get(context).getScaledTouchSlop();
    }
}
