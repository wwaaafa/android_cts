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

package android.car.cts.builtin.util;

import static android.car.cts.builtin.util.LogcatHelper.assertLogcatMessage;

import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.TimingsTraceLog;

import org.junit.Test;

public final class TimingsTraceLogTest {

    private static final String TAG = TimingsTraceLogTest.class.getSimpleName();
    private static final int TIMEOUT_MS = 60_000;

    @Test
    public void testTimingsTraceLog() {
        TimingsTraceLog timingsTraceLog =
                new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        timingsTraceLog.traceBegin("testTimingsTraceLog");
        timingsTraceLog.traceEnd();

        assertLogcatMessage("TimingsTraceLogTest: testTimingsTraceLog took to complete",
                TIMEOUT_MS);
    }

    @Test
    public void testTimingsTraceLogDuration() {
        TimingsTraceLog timingsTraceLog =
                new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        timingsTraceLog.logDuration("testTimingsTraceLogDuration", 159);

        assertLogcatMessage(
                "TimingsTraceLogTest: testTimingsTraceLogDuration took to complete: 159ms",
                TIMEOUT_MS);
    }
}
