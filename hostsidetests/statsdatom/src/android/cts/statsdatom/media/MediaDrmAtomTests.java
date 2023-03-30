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

package android.cts.statsdatom.media;

import static android.media.drm.Enums.DrmScheme.CLEAR_KEY_DASH_IF;
import static android.media.drm.Enums.IDrmFrontend.IDRM_JNI;
import static android.media.drm.Enums.SecurityLevel.SECURITY_LEVEL_MAX;
import static android.media.drm.Enums.SecurityLevel.SECURITY_LEVEL_SW_SECURE_CRYPTO;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.os.media.MediaDrmAtoms;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MediaDrmAtomTests extends DeviceTestCase implements IBuildReceiver {

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        // Put a delay to give statsd enough time to remove previous configs and
        // reports, as well as install the test app.
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installTestApp(getDevice(), DeviceUtils.STATSD_ATOM_TEST_APK,
                DeviceUtils.STATSD_ATOM_TEST_PKG, mCtsBuild);
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG);
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testMediaDrmAtom() throws Throwable {
        // Upload the config.
        ConfigUtils.uploadConfigForPushedAtoms(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                new int[]{
                        AtomsProto.Atom.MEDIA_DRM_CREATED_FIELD_NUMBER,
                        AtomsProto.Atom.MEDIA_DRM_ERRORED_FIELD_NUMBER,
                        AtomsProto.Atom.MEDIA_DRM_SESSION_OPENED_FIELD_NUMBER
                });
        // Trigger AtomTests.
        DeviceUtils.runDeviceTests(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG, ".AtomTests",
                "testMediaDrmAtoms");
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);

        List<MediaDrmAtoms.MediaDrmCreated> createdList = new ArrayList<>();
        List<MediaDrmAtoms.MediaDrmSessionOpened> openedList = new ArrayList<>();
        List<MediaDrmAtoms.MediaDrmErrored> erroredList = new ArrayList<>();
        int testAppUid = DeviceUtils.getStatsdTestAppUid(getDevice());

        for (StatsLog.EventMetricData event : data) {
            MediaDrmAtoms.MediaDrmCreated created = event.getAtom().getMediaDrmCreated();
            MediaDrmAtoms.MediaDrmSessionOpened opened = event.getAtom().getMediaDrmSessionOpened();
            MediaDrmAtoms.MediaDrmErrored errored = event.getAtom().getMediaDrmErrored();

            if (created != null && created.getUid() == testAppUid) {
                createdList.add(created);
            }

            if (opened != null && opened.getUid() == testAppUid) {
                openedList.add(opened);
            }

            if (errored != null && errored.getUid() == testAppUid) {
                erroredList.add(errored);
            }
        }
        UUID clearKeyUuid = new UUID(0xe2719d58a985b3c9L, 0x781ab030af78d30eL);
        // verify the events
        assertThat(createdList.size()).isEqualTo(1);
        assertThat(createdList.get(0).getScheme()).isEqualTo(CLEAR_KEY_DASH_IF);
        assertThat(createdList.get(0).getUuidMsb())
                .isEqualTo(clearKeyUuid.getMostSignificantBits());
        assertThat(createdList.get(0).getUuidLsb())
                .isEqualTo(clearKeyUuid.getLeastSignificantBits());
        assertThat(createdList.get(0).getFrontend()).isEqualTo(IDRM_JNI);
        assertThat(createdList.get(0).getVersion()).isNotEmpty();
        assertThat(openedList.size()).isEqualTo(2);
        assertThat(openedList.get(0).getObjectNonce()).isNotEmpty();
        assertThat(openedList.get(0).getRequestedSecurityLevel()).isEqualTo(SECURITY_LEVEL_MAX);
        assertThat(openedList.get(0).getOpenedSecurityLevel()).isEqualTo(
                SECURITY_LEVEL_SW_SECURE_CRYPTO);
        // TODO : to add check for erroredlist, cdm_error and error_code
    }
}
