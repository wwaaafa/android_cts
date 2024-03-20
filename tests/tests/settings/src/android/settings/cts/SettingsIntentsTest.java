/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.settings.cts;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * For testing whether newly added Settings intents can be triggered without regression or issue in
 * Settings app.
 */
@RunWith(AndroidJUnit4.class)
public class SettingsIntentsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    static final String TAG = "SettingsIntentsTest";

    private PackageManager mPackageManager;

    @Before
    public void setUp() throws Exception {
        mPackageManager =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ENABLED_SATELLITE_FLAG)
    public void settingActivity_launchSatelliteSettingIntent() {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        final Intent intent = new Intent(Settings.ACTION_SATELLITE_SETTING).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK);
        final ResolveInfo ri = mPackageManager.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull(ri);
        targetContext.startActivity(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
