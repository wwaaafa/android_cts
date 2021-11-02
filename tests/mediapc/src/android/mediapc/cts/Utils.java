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

package android.mediapc.cts;

import static android.util.DisplayMetrics.DENSITY_400;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import static org.junit.Assume.assumeTrue;

import androidx.test.platform.app.InstrumentationRegistry;


/**
 * Test utilities.
 */
/* package private */ class Utils {
    private static final int sPc = Build.VERSION.MEDIA_PERFORMANCE_CLASS;

    private static final String TAG = "PerformanceClassTestUtils";

    public static final int DISPLAY_DPI;
    public static final int MIN_DISPLAY_CANDIDATE_DPI = DENSITY_400;
    public static final int DISPLAY_LONG_PIXELS;
    public static final int MIN_DISPLAY_LONG_CANDIDATE_PIXELS = 1920;
    public static final int DISPLAY_SHORT_PIXELS;
    public static final int MIN_DISPLAY_SHORT_CANDIDATE_PIXELS = 1080;

    public static final long TOTAL_MEMORY_MB;
    // Media performance requires 6 GB minimum RAM, but keeping the following to 5 GB
    // as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB device.
    public static final long MIN_MEMORY_PERF_CLASS_CANDIDATE_MB = 5 * 1024;

    static {
        Log.d(TAG, "performance class is " + sPc);

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = context.getSystemService(WindowManager.class);
        windowManager.getDefaultDisplay().getMetrics(metrics);
        DISPLAY_DPI = metrics.densityDpi;
        DISPLAY_LONG_PIXELS = Math.max(metrics.widthPixels, metrics.heightPixels);
        DISPLAY_SHORT_PIXELS = Math.min(metrics.widthPixels, metrics.heightPixels);

        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        TOTAL_MEMORY_MB = memoryInfo.totalMem / 1024 / 1024;
    }

    /**
     * First defined media performance class.
     */
    private static final int FIRST_PERFORMANCE_CLASS = Build.VERSION_CODES.R;

    public static boolean isRPerfClass() {
        return sPc == Build.VERSION_CODES.R;
    }

    public static boolean isSPerfClass() {
        return sPc == Build.VERSION_CODES.S;
    }

    /**
     * Latest defined media performance class.
     */
    private static final int LAST_PERFORMANCE_CLASS = Build.VERSION_CODES.S;

    public static boolean isHandheld() {
        // handheld nature is not exposed to package manager, for now
        // we check for touchscreen and NOT watch and NOT tv
        PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_TOUCHSCREEN)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_TELEVISION)
                && !pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }


    public static int getPerfClass() {
        return sPc;
    }

    public static boolean isPerfClass() {
        return sPc >= FIRST_PERFORMANCE_CLASS &&
               sPc <= LAST_PERFORMANCE_CLASS;
    }

    public static boolean meetsPerformanceClassPreconditions() {
        if (isPerfClass()) {
            return true;
        }

        // If device doesn't advertise performance class, check if this can be ruled out as a
        // candidate for performance class tests.
        if (!isHandheld() ||
                TOTAL_MEMORY_MB < MIN_MEMORY_PERF_CLASS_CANDIDATE_MB ||
                DISPLAY_DPI < MIN_DISPLAY_CANDIDATE_DPI ||
                DISPLAY_LONG_PIXELS < MIN_DISPLAY_LONG_CANDIDATE_PIXELS ||
                DISPLAY_SHORT_PIXELS < MIN_DISPLAY_SHORT_CANDIDATE_PIXELS) {
            return false;
        }
        return true;
    }

    public static void assumeDeviceMeetsPerformanceClassPreconditions() {
        assumeTrue(
                "Test skipped because the device does not meet the hardware requirements for "
                        + "performance class.",
                meetsPerformanceClassPreconditions());
    }
}
