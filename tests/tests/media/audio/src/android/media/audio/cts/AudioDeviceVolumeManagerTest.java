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

package android.media.audio.cts;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.content.Context;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;
import android.media.VolumeInfo;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.NonMainlineTest;


@NonMainlineTest
public class AudioDeviceVolumeManagerTest extends CtsAndroidTestCase {

    private static final String TAG = "AudioDeviceVolumeManagerTest";
    private AudioDeviceVolumeManager mADVmgr;

    private static final AudioDeviceAttributes BT_DEV = new AudioDeviceAttributes(
            AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "bla");
    /**
     * Constant for maximum acceptable time before which a volume change needs to be propagated
     * between client request and server update
     */
    private static final int VOLUME_UPDATE_TIME_MAX_MS = 100;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING,
                        Manifest.permission.STATUS_BAR_SERVICE);
        mADVmgr = (AudioDeviceVolumeManager) getContext().getSystemService(
                Context.AUDIO_DEVICE_VOLUME_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    /**
     * Verify calling AudioDeviceVolumeManager.setDeviceVolume/getDeviceVolume with null parameters
     * throws an NPE.
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.AudioDeviceVolumeManager#setDeviceVolume",
            "android.media.AudioDeviceVolumeManager#getDeviceVolume"})
    public void testNullability() throws Exception {
        assertThrows("Able to call setDeviceVolume with null VolumeInfo",
                NullPointerException.class,
                () ->mADVmgr.setDeviceVolume(null, BT_DEV));
        assertThrows("Able to call setDeviceVolume with null device",
                NullPointerException.class,
                () ->mADVmgr.setDeviceVolume(VolumeInfo.getDefaultVolumeInfo(), null));
        assertThrows("Able to call getDeviceVolume with null VolumeInfo",
                NullPointerException.class,
                () ->mADVmgr.getDeviceVolume(null, BT_DEV));
        assertThrows("Able to call getDeviceVolume with null device",
                NullPointerException.class,
                () ->mADVmgr.getDeviceVolume(VolumeInfo.getDefaultVolumeInfo(), null));
    }

    /**
     * Verify VolumeInfo used for setting the volume needs to have a volume index
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.AudioDeviceVolumeManager#setDeviceVolume"})
    public void testVolumeInfoArguments() throws Exception {
        VolumeInfo defVolInfo = VolumeInfo.getDefaultVolumeInfo();
        VolumeInfo vi = new VolumeInfo.Builder(defVolInfo).setVolumeIndex(VolumeInfo.INDEX_NOT_SET)
                .build();
        android.util.Log.i(TAG, "testVolumeInfoArguments using VI:" + vi);
        assertThrows("Able to call setDeviceVolume with VolumeInfo without index",
                IllegalArgumentException.class,
                () ->mADVmgr.setDeviceVolume(vi, BT_DEV));
    }

    @ApiTest(apis = {"android.media.AudioDeviceVolumeManager#setDeviceVolume",
            "android.media.AudioDeviceVolumeManager#getDeviceVolume"})
    public void testSetGetVolume() throws Exception {
        AudioManager am = getContext().getSystemService(AudioManager.class);
        final int minIndex = am.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int midIndex = (minIndex + maxIndex) / 2;
        final VolumeInfo volMedia = new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                .setMinVolumeIndex(minIndex)
                .setMaxVolumeIndex(maxIndex)
                .build();
        final VolumeInfo volMin = new VolumeInfo.Builder(volMedia).setVolumeIndex(minIndex).build();
        final VolumeInfo volMid = new VolumeInfo.Builder(volMedia).setVolumeIndex(midIndex).build();

        // safe media can block the raising to volMid, disable it
        am.disableSafeMediaVolume();
        mADVmgr.setDeviceVolume(volMin, BT_DEV);
        Thread.sleep(VOLUME_UPDATE_TIME_MAX_MS);
        VolumeInfo resVI = mADVmgr.getDeviceVolume(volMid, BT_DEV);
        assertEquals(volMin.getVolumeIndex(), resVI.getVolumeIndex());

        mADVmgr.setDeviceVolume(volMid, BT_DEV);
        Thread.sleep(VOLUME_UPDATE_TIME_MAX_MS);
        resVI = mADVmgr.getDeviceVolume(volMid, BT_DEV);
        assertEquals(volMid.getVolumeIndex(), resVI.getVolumeIndex());
    }
}
