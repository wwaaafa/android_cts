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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastSubgroupSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastSubgroupSettingsTest {
    // For BluetoothLeAudioContentMetadata
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    private static final String TEST_LANGUAGE = "deu";
    private static final int TEST_QUALITY =
            BluetoothLeBroadcastSubgroupSettings.QUALITY_STANDARD;

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private boolean mIsBroadcastSourceSupported;
    private boolean mIsBroadcastAssistantSupported;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) {
            return;
        }
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mIsBroadcastAssistantSupported =
                mAdapter.isLeAudioBroadcastAssistantSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastAssistantSupported) {
            boolean isBroadcastAssistantEnabledInConfig =
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
            assertTrue("Config must be true when profile is supported",
                    isBroadcastAssistantEnabledInConfig);
        }

        mIsBroadcastSourceSupported =
                mAdapter.isLeAudioBroadcastSourceSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastSourceSupported) {
            boolean isBroadcastSourceEnabledInConfig =
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST);
            assertTrue("Config must be true when profile is supported",
                    isBroadcastSourceEnabledInConfig);
        }
    }

    @After
    public void tearDown() {
        if (mHasBluetooth) {
            if (mAdapter != null) {
                assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            }
            mAdapter = null;
            TestUtils.dropPermissionAsShellUid();
        }
    }

    @Test
    public void testCreateBroadcastSubgroupSettingsFromBuilder() {
        if (shouldSkipTest()) {
            return;
        }
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).setLanguage(TEST_LANGUAGE).build();
        BluetoothLeBroadcastSubgroupSettings.Builder builder =
                new BluetoothLeBroadcastSubgroupSettings.Builder()
                .setPreferredQuality(TEST_QUALITY)
                .setContentMetadata(contentMetadata);

        BluetoothLeBroadcastSubgroupSettings settings = builder.build();
        assertEquals(TEST_QUALITY, settings.getPreferredQuality());
        assertEquals(contentMetadata, settings.getContentMetadata());
        // builder expect at least one channel
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    public void testCreateBroadcastSubgroupFromCopy() {
        if (shouldSkipTest()) {
            return;
        }
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).setLanguage(TEST_LANGUAGE).build();
        BluetoothLeBroadcastSubgroupSettings.Builder builder =
                new BluetoothLeBroadcastSubgroupSettings.Builder()
                .setPreferredQuality(TEST_QUALITY)
                .setContentMetadata(contentMetadata);
        BluetoothLeBroadcastSubgroupSettings settings = builder.build();
        BluetoothLeBroadcastSubgroupSettings settingsCopy =
                new BluetoothLeBroadcastSubgroupSettings.Builder(settings).build();
        assertEquals(TEST_QUALITY, settingsCopy.getPreferredQuality());
        assertEquals(contentMetadata, settingsCopy.getContentMetadata());
        // builder expect at least one channel
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    private boolean shouldSkipTest() {
        return !mHasBluetooth || (!mIsBroadcastSourceSupported && !mIsBroadcastAssistantSupported);
    }
}
