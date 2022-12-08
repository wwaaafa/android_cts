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

package android.wearable.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.app.wearable.WearableSensingManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
 * Test the WearableSensingManager API. Run with "atest CtsWearableSensingServiceTestCases".
 */
@RunWith(AndroidJUnit4.class)
public class WearableSensingManagerTest {
    private static final Executor EXECUTOR = InstrumentationRegistry.getContext().getMainExecutor();

    private Context mContext;
    private WearableSensingManager mWearableSensingManager;
    private ParcelFileDescriptor[] mPipe;

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mWearableSensingManager = (WearableSensingManager) mContext.getSystemService(
                Context.WEARABLE_SENSING_SERVICE);
        mPipe = ParcelFileDescriptor.createPipe();
    }

    @Test
    public void noAccessForNoneSystemComponent() {
        assertEquals(PackageManager.PERMISSION_DENIED, mContext.checkCallingOrSelfPermission(
                Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE));

        // Cts test runner is a non-system apk.
        assertThat(mWearableSensingManager).isNull();
    }
}
