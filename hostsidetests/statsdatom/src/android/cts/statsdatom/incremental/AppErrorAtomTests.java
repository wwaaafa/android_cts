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

package android.cts.statsdatom.incremental;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.server.ErrorSource;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.incfs.install.IncrementalInstallSession;
import com.android.incfs.install.adb.ddmlib.DeviceConnection;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AppErrorAtomTests extends DeviceTestCase implements IBuildReceiver {
    private static final String FEATURE_INCREMENTAL_DELIVERY =
            "android.software.incremental_delivery";
    private static final String IDSIG_SUFFIX = ".idsig";
    private static int INSTALL_TIMEOUT_SECONDS = 10;

    private IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }
    private IncrementalInstallSession mSession;

    @Before
    public void setUp() throws Exception {
        if (!getDevice().hasFeature(FEATURE_INCREMENTAL_DELIVERY)) {
            return;
        }
        super.setUp();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        final File apk = buildHelper.getTestFile(DeviceUtils.STATSD_ATOM_TEST_APK);
        assertNotNull(apk);
        final File v4Signature = buildHelper.getTestFile(DeviceUtils.STATSD_ATOM_TEST_APK + IDSIG_SUFFIX);
        assertNotNull(v4Signature);
        mSession = new IncrementalInstallSession.Builder()
                .addApk(Paths.get(apk.getAbsolutePath()),
                        Paths.get(v4Signature.getAbsolutePath()))
                .addExtraArgs("-g") // grant permissions
                .setBlockFilter(block -> {
                    // block a page from res/raw; does not affect test run
                    return block.getBlockIndex() != 3152;
                })
                .build();
        mSession.start(Executors.newCachedThreadPool(),
                DeviceConnection.getFactory(getDevice().getSerialNumber()));
        mSession.waitForInstallCompleted(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(getDevice().isPackageInstalled(DeviceUtils.STATSD_ATOM_TEST_PKG));
        // Preload most of the pages to make sure the test can run but it also causes pending reads
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testLoadingApks");
    }

    @After
    public void tearDown() throws Exception {
        if (mSession != null) {
            mSession.close();
        }
        getDevice().uninstallPackage(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertFalse(getDevice().isPackageInstalled(DeviceUtils.STATSD_ATOM_TEST_PKG));
        super.tearDown();
    }

    public void testAppCrashOnIncremental() throws Exception {
        if (!getDevice().hasFeature(FEATURE_INCREMENTAL_DELIVERY)) {
            return;
        }
        final int atomTag = AtomsProto.Atom.APP_CRASH_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.crash");

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(1);
        AtomsProto.AppCrashOccurred atom = data.get(0).getAtom().getAppCrashOccurred();
        // UID should belong to the run activity, not any system service.
        assertThat(atom.getUid()).isGreaterThan(10000);
        assertThat(atom.getEventType()).isEqualTo("crash");
        assertThat(atom.getIsInstantApp().getNumber())
                .isEqualTo(AtomsProto.AppCrashOccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
                .isEqualTo(AtomsProto.AppCrashOccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(atom.getErrorSource()).isEqualTo(ErrorSource.DATA_APP);
        assertTrue(atom.getIsIncremental());
        assertFalse((1.0f - atom.getLoadingProgress()) < 0.0000001f);
        assertTrue(atom.getMillisSinceOldestPendingRead() > 0);
        assertEquals(3 /* HEALTH_STATUS_UNHEALTHY */, atom.getStorageHealthCode());
        assertEquals(6 /* DATA_LOADER_IMAGE_READY */, atom.getDataLoaderStatusCode());
        assertFalse(atom.getReadLogsEnabled());
        assertTrue(atom.getMillisSinceLastDataLoaderBind() > 0);
        assertEquals(0, atom.getDataLoaderBindDelayMillis());
        assertTrue(atom.getTotalDelayedReads() > 0);
        assertTrue(atom.getTotalFailedReads() > 0);
        assertTrue(atom.getLastReadErrorMillisSince() > 0);
        assertEquals(-62 /* -ETIME */, atom.getLastReadErrorCode());
        assertTrue(atom.getTotalDelayedReadsDurationMillis() > 0);
    }
}
