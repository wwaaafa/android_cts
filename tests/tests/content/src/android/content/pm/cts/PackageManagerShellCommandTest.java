/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.content.pm.cts;

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.pm.Checksum.TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256;
import static android.content.pm.Checksum.TYPE_WHOLE_MERKLE_ROOT_4K_SHA256;
import static android.content.pm.PackageInstaller.DATA_LOADER_TYPE_INCREMENTAL;
import static android.content.pm.PackageInstaller.DATA_LOADER_TYPE_NONE;
import static android.content.pm.PackageInstaller.DATA_LOADER_TYPE_STREAMING;
import static android.content.pm.PackageInstaller.EXTRA_DATA_LOADER_TYPE;
import static android.content.pm.PackageInstaller.EXTRA_SESSION_ID;
import static android.content.pm.PackageInstaller.LOCATION_DATA_APP;
import static android.content.pm.PackageManager.EXTRA_VERIFICATION_ID;
import static android.content.pm.PackageManager.EXTRA_VERIFICATION_ROOT_HASH;
import static android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.content.pm.PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static android.content.pm.PackageManager.VERIFICATION_ALLOW;
import static android.content.pm.PackageManager.VERIFICATION_REJECT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApkChecksum;
import android.content.pm.ApplicationInfo;
import android.content.pm.DataLoaderParams;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.pm.cts.util.AbandonAllPackageSessionsRule;
import android.os.ConditionVariable;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.util.PackageUtils;

import androidx.test.InstrumentationRegistry;

import com.android.internal.util.ConcurrentUtils;
import com.android.internal.util.HexDump;

import libcore.util.HexEncoding;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
@AppModeFull
public class PackageManagerShellCommandTest {
    private static final String TEST_APP_PACKAGE = "com.example.helloworld";

    private static final String CTS_PACKAGE_NAME = "android.content.cts";

    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String TEST_HW5 = "HelloWorld5.apk";
    private static final String TEST_HW5_SPLIT0 = "HelloWorld5_hdpi-v4.apk";
    private static final String TEST_HW5_SPLIT1 = "HelloWorld5_mdpi-v4.apk";
    private static final String TEST_HW5_SPLIT2 = "HelloWorld5_xhdpi-v4.apk";
    private static final String TEST_HW5_SPLIT3 = "HelloWorld5_xxhdpi-v4.apk";
    private static final String TEST_HW5_SPLIT4 = "HelloWorld5_xxxhdpi-v4.apk";
    private static final String TEST_HW7 = "HelloWorld7.apk";
    private static final String TEST_HW7_SPLIT0 = "HelloWorld7_hdpi-v4.apk";
    private static final String TEST_HW7_SPLIT1 = "HelloWorld7_mdpi-v4.apk";
    private static final String TEST_HW7_SPLIT2 = "HelloWorld7_xhdpi-v4.apk";
    private static final String TEST_HW7_SPLIT3 = "HelloWorld7_xxhdpi-v4.apk";
    private static final String TEST_HW7_SPLIT4 = "HelloWorld7_xxxhdpi-v4.apk";

    private static final String TEST_SDK1_PACKAGE = "com.test.sdk1_1";
    private static final String TEST_SDK1_MAJOR_VERSION2_PACKAGE = "com.test.sdk1_2";
    private static final String TEST_SDK2_PACKAGE = "com.test.sdk2_2";
    private static final String TEST_SDK3_PACKAGE = "com.test.sdk3_3";
    private static final String TEST_SDK_USER_PACKAGE = "com.test.sdk.user";

    private static final String TEST_SDK1_NAME = "com.test.sdk1";
    private static final String TEST_SDK2_NAME = "com.test.sdk2";
    private static final String TEST_SDK3_NAME = "com.test.sdk3";

    private static final String TEST_SDK1 = "HelloWorldSdk1.apk";
    private static final String TEST_SDK1_UPDATED = "HelloWorldSdk1Updated.apk";
    private static final String TEST_SDK1_MAJOR_VERSION2 = "HelloWorldSdk1MajorVersion2.apk";
    private static final String TEST_SDK1_DIFFERENT_SIGNER = "HelloWorldSdk1DifferentSigner.apk";
    private static final String TEST_SDK2 = "HelloWorldSdk2.apk";
    private static final String TEST_SDK2_UPDATED = "HelloWorldSdk2Updated.apk";
    private static final String TEST_USING_SDK1 = "HelloWorldUsingSdk1.apk";
    private static final String TEST_USING_SDK1_AND_SDK2 = "HelloWorldUsingSdk1And2.apk";

    private static final String TEST_SDK3_USING_SDK1 = "HelloWorldSdk3UsingSdk1.apk";
    private static final String TEST_SDK3_USING_SDK1_AND_SDK2 = "HelloWorldSdk3UsingSdk1And2.apk";
    private static final String TEST_USING_SDK3 = "HelloWorldUsingSdk3.apk";

    private static final String TEST_HW_NO_APP_STORAGE = "HelloWorldNoAppStorage.apk";

    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    private static final long DEFAULT_STREAMING_VERIFICATION_TIMEOUT = 3 * 1000;

    @Rule
    public AbandonAllPackageSessionsRule mAbandonSessionsRule = new AbandonAllPackageSessionsRule();

    @Parameter
    public int mDataLoaderType;

    @Parameters
    public static Iterable<Object> initParameters() {
        return Arrays.asList(DATA_LOADER_TYPE_NONE, DATA_LOADER_TYPE_STREAMING,
                             DATA_LOADER_TYPE_INCREMENTAL);
    }

    private boolean mStreaming = false;
    private boolean mIncremental = false;
    private String mInstall = "";
    private String mPackageVerifier = null;
    private String mUnusedStaticSharedLibsMinCachePeriod = null;
    private long mStreamingVerificationTimeoutMs = DEFAULT_STREAMING_VERIFICATION_TIMEOUT;
    private int mSecondUser = -1;

    private static PackageInstaller getPackageInstaller() {
        return getPackageManager().getPackageInstaller();
    }

    private static PackageManager getPackageManager() {
        return InstrumentationRegistry.getContext().getPackageManager();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    private static String executeShellCommand(String command) throws IOException {
        final ParcelFileDescriptor stdout = getUiAutomation().executeShellCommand(command);
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private static String executeShellCommand(String command, File input)
            throws IOException {
        return executeShellCommand(command, new File[]{input});
    }

    private static String executeShellCommand(String command, File[] inputs)
            throws IOException {
        final ParcelFileDescriptor[] pfds = getUiAutomation().executeShellCommandRw(command);
        ParcelFileDescriptor stdout = pfds[0];
        ParcelFileDescriptor stdin = pfds[1];
        try (FileOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(
                stdin)) {
            for (File input : inputs) {
                try (FileInputStream inputStream = new FileInputStream(input)) {
                    writeFullStream(inputStream, outputStream, input.length());
                }
            }
        }
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private static String readFullStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeFullStream(inputStream, result, -1);
        return result.toString("UTF-8");
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream,
            long expected)
            throws IOException {
        byte[] buffer = new byte[1024];
        long total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
            total += length;
        }
        if (expected > 0) {
            assertEquals(expected, total);
        }
    }

    @Before
    public void onBefore() throws Exception {
        // Check if Incremental is allowed and revert to non-dataloader installation.
        if (mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL && !checkIncrementalDeliveryFeature()) {
            mDataLoaderType = DATA_LOADER_TYPE_NONE;
        }

        mStreaming = mDataLoaderType != DATA_LOADER_TYPE_NONE;
        mIncremental = mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL;
        mInstall = mDataLoaderType == DATA_LOADER_TYPE_NONE ? " install " :
                mDataLoaderType == DATA_LOADER_TYPE_STREAMING ? " install-streaming " :
                        " install-incremental ";

        uninstallPackageSilently(TEST_APP_PACKAGE);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));

        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);
        uninstallPackageSilently(TEST_SDK3_PACKAGE);
        uninstallPackageSilently(TEST_SDK2_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_MAJOR_VERSION2_PACKAGE);

        mPackageVerifier = executeShellCommand("settings get global verifier_verify_adb_installs");
        // Disable the package verifier for non-incremental installations to avoid the dialog
        // when installing an app.
        executeShellCommand("settings put global verifier_verify_adb_installs 0");

        mUnusedStaticSharedLibsMinCachePeriod = executeShellCommand(
                "settings get global unused_static_shared_lib_min_cache_period");

        try {
            mStreamingVerificationTimeoutMs = Long.parseUnsignedLong(
                    executeShellCommand("settings get global streaming_verifier_timeout"));
        } catch (NumberFormatException ignore) {
        }
    }

    @After
    public void onAfter() throws Exception {
        uninstallPackageSilently(TEST_APP_PACKAGE);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals(null, getSplits(TEST_APP_PACKAGE));

        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);
        uninstallPackageSilently(TEST_SDK3_PACKAGE);
        uninstallPackageSilently(TEST_SDK2_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_MAJOR_VERSION2_PACKAGE);

        // Reset the global settings to their original values.
        executeShellCommand("settings put global verifier_verify_adb_installs " + mPackageVerifier);
        executeShellCommand("settings put global unused_static_shared_lib_min_cache_period "
                + mUnusedStaticSharedLibsMinCachePeriod);

        // Set the test override to invalid.
        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest", "invalid");
        setSystemProperty("debug.pm.prune_unused_shared_libraries_delay", "invalid");
        setSystemProperty("debug.pm.adb_verifier_override_package", "invalid");

        if (mSecondUser != -1) {
            stopUser(mSecondUser);
            removeUser(mSecondUser);
        }
    }

    private boolean checkIncrementalDeliveryFeature() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INCREMENTAL_DELIVERY);
    }

    @Test
    public void testAppInstall() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstallErr() throws Exception {
        if (!mStreaming) {
            return;
        }
        File file = new File(createApkPath(TEST_HW5));
        String command = "pm " + mInstall + " -t -g " + file.getPath() + (new Random()).nextLong();
        String commandResult = executeShellCommand(command);
        assertEquals("Failure [failed to add file(s)]\n", commandResult);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstallStdIn() throws Exception {
        installPackageStdIn(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstallStdInErr() throws Exception {
        File file = new File(createApkPath(TEST_HW5));
        String commandResult = executeShellCommand("pm " + mInstall + " -t -g -S " + file.length(),
                new File[]{});
        if (mIncremental) {
            assertTrue(commandResult, commandResult.startsWith("Failure ["));
        } else {
            assertTrue(commandResult,
                    commandResult.startsWith("Failure [INSTALL_PARSE_FAILED_NOT_APK"));
        }
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdate() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        updatePackage(TEST_APP_PACKAGE, TEST_HW7);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdateSameApk() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        updatePackage(TEST_APP_PACKAGE, TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdateStdIn() throws Exception {
        installPackageStdIn(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        updatePackageStdIn(TEST_APP_PACKAGE, TEST_HW7);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdateStdInSameApk() throws Exception {
        installPackageStdIn(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        updatePackageStdIn(TEST_APP_PACKAGE, TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsInstall() throws Exception {
        installSplits(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsInstallStdIn() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsInstallDash() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "-");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchInstall() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUpdate() throws Exception {
        installSplits(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        updateSplits(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }


    @Test
    public void testSplitsAdd() throws Exception {
        installSplits(new String[]{TEST_HW5});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base", getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT0});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi", getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT1});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT2});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi",
                getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT3});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi",
                getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUpdateStdIn() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        installSplitsStdIn(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4}, "");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUpdateDash() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "-");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        installSplitsStdIn(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4}, "-");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchUpdate() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        updateSplitsBatch(
                new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                        TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchAdd() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base", getSplits(TEST_APP_PACKAGE));

        updateSplitsBatch(new String[]{TEST_HW5_SPLIT0, TEST_HW5_SPLIT1});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));

        updateSplitsBatch(new String[]{TEST_HW5_SPLIT2, TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUninstall() throws Exception {
        installSplits(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplits(TEST_APP_PACKAGE, new String[]{"config.hdpi"});
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplits(TEST_APP_PACKAGE, new String[]{"config.xxxhdpi", "config.xhdpi"});
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchUninstall() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplitsBatch(TEST_APP_PACKAGE, new String[]{"config.hdpi"});
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplitsBatch(TEST_APP_PACKAGE, new String[]{"config.xxxhdpi", "config.xhdpi"});
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsRemove() throws Exception {
        installSplits(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        String sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplits(sessionId, new String[]{"config.hdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplits(sessionId, new String[]{"config.xxxhdpi", "config.xhdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchRemove() throws Exception {
        installSplitsBatch(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        String sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplitsBatch(sessionId, new String[]{"config.hdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplitsBatch(sessionId, new String[]{"config.xxxhdpi", "config.xhdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstallErrDuplicate() throws Exception {
        if (!mStreaming) {
            return;
        }
        String split = createApkPath(TEST_HW5);
        String commandResult = executeShellCommand(
                "pm " + mInstall + " -t -g " + split + " " + split);
        assertEquals("Failure [failed to add file(s)]\n", commandResult);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testDataLoaderParamsApiV1() throws Exception {
        if (!mStreaming) {
            return;
        }

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();

            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            assertEquals(null, session.getDataLoaderParams());

            installer.abandonSession(sessionId);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testDataLoaderParamsApiV2() throws Exception {
        if (!mStreaming) {
            return;
        }

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();

            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            final ComponentName componentName = new ComponentName("foo", "bar");
            final String args = "args";
            params.setDataLoaderParams(
                    mIncremental ? DataLoaderParams.forIncremental(componentName, args)
                            : DataLoaderParams.forStreaming(componentName, args));

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            DataLoaderParams dataLoaderParams = session.getDataLoaderParams();
            assertEquals(mIncremental ? DATA_LOADER_TYPE_INCREMENTAL : DATA_LOADER_TYPE_STREAMING,
                    dataLoaderParams.getType());
            assertEquals("foo", dataLoaderParams.getComponentName().getPackageName());
            assertEquals("bar", dataLoaderParams.getComponentName().getClassName());
            assertEquals("args", dataLoaderParams.getArguments());

            installer.abandonSession(sessionId);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testRemoveFileApiV2() throws Exception {
        if (!mStreaming) {
            return;
        }

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();

            final SessionParams params = new SessionParams(SessionParams.MODE_INHERIT_EXISTING);
            params.setAppPackageName("com.package.name");
            final ComponentName componentName = new ComponentName("foo", "bar");
            final String args = "args";
            params.setDataLoaderParams(
                    mIncremental ? DataLoaderParams.forIncremental(componentName, args)
                            : DataLoaderParams.forStreaming(componentName, args));

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            session.addFile(LOCATION_DATA_APP, "base.apk", 123, "123".getBytes(), null);
            String[] files = session.getNames();
            assertEquals(1, files.length);
            assertEquals("base.apk", files[0]);

            session.removeFile(LOCATION_DATA_APP, "base.apk");
            files = session.getNames();
            assertEquals(2, files.length);
            assertEquals("base.apk", files[0]);
            assertEquals("base.apk.removed", files[1]);

            installer.abandonSession(sessionId);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSdkInstallAndUpdate() throws Exception {
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Same APK.
        installPackage(TEST_SDK1);

        // Updated APK.
        installPackage(TEST_SDK1_UPDATED);

        // Reverted APK.
        installPackage(TEST_SDK1);

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
    }

    @Test
    public void testSdkInstallMultipleMajorVersions() throws Exception {
        // Major version 1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Major version 2.
        installPackage(TEST_SDK1_MAJOR_VERSION2);

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 2));
    }

    @Test
    public void testSdkInstallMultipleMinorVersionsWrongSignature() throws Exception {
        // Major version 1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Major version 1, different signer.
        installPackage(TEST_SDK1_DIFFERENT_SIGNER,
                "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.test.sdk1_1 "
                        + "signatures do not match newer version");
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
    }

    @Test
    public void testSdkInstallMultipleMajorVersionsWrongSignature() throws Exception {
        // Major version 1.
        installPackage(TEST_SDK1_DIFFERENT_SIGNER);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Major version 2.
        installPackage(TEST_SDK1_MAJOR_VERSION2,
                "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.test.sdk1_1 "
                        + "signatures do not match newer version");

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
    }

    @Test
    public void testSdkInstallAndUpdateTwoMajorVersions() throws Exception {
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        installPackage(TEST_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Same APK.
        installPackage(TEST_SDK1);
        installPackage(TEST_SDK2);

        // Updated APK.
        installPackage(TEST_SDK1_UPDATED);
        installPackage(TEST_SDK2_UPDATED);

        // Reverted APK.
        installPackage(TEST_SDK1);
        installPackage(TEST_SDK2);

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));
    }

    @Test
    public void testAppUsingSdkInstallAndUpdate() throws Exception {
        // Try to install without required SDK1.
        installPackage(TEST_USING_SDK1, "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isAppInstalled(TEST_SDK_USER_PACKAGE));

        // Now install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest",
                getPackageCertDigest(TEST_SDK1_PACKAGE));

        // Install and uninstall.
        installPackage(TEST_USING_SDK1);
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Update SDK1.
        installPackage(TEST_SDK1_UPDATED);

        // Install again.
        installPackage(TEST_USING_SDK1);

        // Check resolution API.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK_USER_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));
            assertEquals(1, appInfo.sharedLibraryInfos.size());
            SharedLibraryInfo libInfo = appInfo.sharedLibraryInfos.get(0);
            assertEquals("com.test.sdk1", libInfo.getName());
            assertEquals(1, libInfo.getLongVersion());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }

        // Try to install without required SDK2.
        installPackage(TEST_USING_SDK1_AND_SDK2, "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");

        // Now install the required SDK2.
        installPackage(TEST_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install and uninstall.
        installPackage(TEST_USING_SDK1_AND_SDK2);
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Update both SDKs.
        installPackage(TEST_SDK1_UPDATED);
        installPackage(TEST_SDK2_UPDATED);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install again.
        installPackage(TEST_USING_SDK1_AND_SDK2);

        // Check resolution API.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK_USER_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));
            assertEquals(2, appInfo.sharedLibraryInfos.size());
            assertEquals("com.test.sdk1", appInfo.sharedLibraryInfos.get(0).getName());
            assertEquals(1, appInfo.sharedLibraryInfos.get(0).getLongVersion());
            assertEquals("com.test.sdk2", appInfo.sharedLibraryInfos.get(1).getName());
            assertEquals(2, appInfo.sharedLibraryInfos.get(1).getLongVersion());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testInstallFailsMismatchingCertificate() throws Exception {
        // Install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Try to install the package with empty digest.
        installPackage(TEST_USING_SDK1, "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
    }

    @Test
    public void testUninstallSdkWhileAppUsing() throws Exception {
        // Install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest",
                getPackageCertDigest(TEST_SDK1_PACKAGE));

        // Install the package.
        installPackage(TEST_USING_SDK1);

        uninstallPackage(TEST_SDK1_PACKAGE, "Failure [DELETE_FAILED_USED_SHARED_LIBRARY]");
    }

    @Test
    public void testGetSharedLibraries() throws Exception {
        // Install the SDK1.
        installPackage(TEST_SDK1);
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNull(sdk2);
        }

        // Install the SDK2.
        installPackage(TEST_SDK2);
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);
        }

        // Install and uninstall the user package.
        {
            setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest",
                    getPackageCertDigest(TEST_SDK1_PACKAGE));

            installPackage(TEST_USING_SDK1_AND_SDK2);

            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);

            assertEquals(TEST_SDK_USER_PACKAGE,
                    sdk1.getDependentPackages().get(0).getPackageName());
            assertEquals(TEST_SDK_USER_PACKAGE,
                    sdk2.getDependentPackages().get(0).getPackageName());

            uninstallPackageSilently(TEST_SDK_USER_PACKAGE);
        }

        // Uninstall the SDK1.
        uninstallPackageSilently(TEST_SDK1_PACKAGE);
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);
        }

        // Uninstall the SDK2.
        uninstallPackageSilently(TEST_SDK2_PACKAGE);
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNull(sdk2);
        }
    }

    @Test
    public void testUninstallUnusedSdks() throws Exception {
        installPackage(TEST_SDK1);
        installPackage(TEST_SDK2);

        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest",
                getPackageCertDigest(TEST_SDK1_PACKAGE));
        installPackage(TEST_USING_SDK1_AND_SDK2);

        setSystemProperty("debug.pm.prune_unused_shared_libraries_delay", "0");
        executeShellCommand("settings put global unused_static_shared_lib_min_cache_period 0");
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Wait for 3secs max.
        for (int i = 0; i < 30; ++i) {
            if (!isSdkInstalled(TEST_SDK1_NAME, 1) && !isSdkInstalled(TEST_SDK2_NAME, 2)) {
                break;
            }
            final int beforeRetryDelayMs = 100;
            Thread.currentThread().sleep(beforeRetryDelayMs);
        }
        assertFalse(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertFalse(isSdkInstalled(TEST_SDK2_NAME, 2));
    }

    @Test
    public void testAppUsingSdkUsingSdkInstallAndUpdate() throws Exception {
        // Try to install without required SDK1.
        installPackage(TEST_USING_SDK3, "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isAppInstalled(TEST_SDK_USER_PACKAGE));

        // Try to install SDK3 without required SDK1.
        installPackage(TEST_SDK3_USING_SDK1, "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isSdkInstalled(TEST_SDK3_NAME, 3));

        // Now install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest",
                getPackageCertDigest(TEST_SDK1_PACKAGE));

        // Now install the required SDK3.
        installPackage(TEST_SDK3_USING_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK3_NAME, 3));

        // Install and uninstall.
        installPackage(TEST_USING_SDK3);
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Update SDK1.
        installPackage(TEST_SDK1_UPDATED);

        // Install again.
        installPackage(TEST_USING_SDK3);

        // Check resolution API.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK_USER_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));
            assertEquals(1, appInfo.sharedLibraryInfos.size());
            SharedLibraryInfo libInfo = appInfo.sharedLibraryInfos.get(0);
            assertEquals("com.test.sdk3", libInfo.getName());
            assertEquals(3, libInfo.getLongVersion());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }

        // Try to install updated SDK3 without required SDK2.
        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");

        // Now install the required SDK2.
        installPackage(TEST_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK3_NAME, 3));

        // Install and uninstall.
        installPackage(TEST_USING_SDK3);
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Update both SDKs.
        installPackage(TEST_SDK1_UPDATED);
        installPackage(TEST_SDK2_UPDATED);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install again.
        installPackage(TEST_USING_SDK3);

        // Check resolution API.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK_USER_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));
            assertEquals(1, appInfo.sharedLibraryInfos.size());
            assertEquals("com.test.sdk3", appInfo.sharedLibraryInfos.get(0).getName());
            assertEquals(3, appInfo.sharedLibraryInfos.get(0).getLongVersion());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSdkUsingSdkInstallAndUpdate() throws Exception {
        // Try to install without required SDK1.
        installPackage(TEST_SDK3_USING_SDK1, "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isSdkInstalled(TEST_SDK3_NAME, 3));

        // Now install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest",
                getPackageCertDigest(TEST_SDK1_PACKAGE));

        // Install and uninstall.
        installPackage(TEST_SDK3_USING_SDK1);
        uninstallPackageSilently(TEST_SDK3_PACKAGE);

        // Update SDK1.
        installPackage(TEST_SDK1_UPDATED);

        // Install again.
        installPackage(TEST_SDK3_USING_SDK1);

        // Check resolution API.
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk3 = findLibrary(libs, "com.test.sdk3", 3);
            assertNotNull(sdk3);
            List<SharedLibraryInfo> deps = sdk3.getDependencies();
            assertEquals(1, deps.size());
            SharedLibraryInfo libInfo = deps.get(0);
            assertEquals("com.test.sdk1", libInfo.getName());
            assertEquals(1, libInfo.getLongVersion());
        }

        // Try to install without required SDK2.
        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");

        // Now install the required SDK2.
        installPackage(TEST_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install and uninstall.
        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2);
        uninstallPackageSilently(TEST_SDK3_PACKAGE);

        // Update both SDKs.
        installPackage(TEST_SDK1_UPDATED);
        installPackage(TEST_SDK2_UPDATED);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install again.
        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2);

        // Check resolution API.
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk3 = findLibrary(libs, "com.test.sdk3", 3);
            assertNotNull(sdk3);
            List<SharedLibraryInfo> deps = sdk3.getDependencies();
            assertEquals(2, deps.size());
            assertEquals("com.test.sdk1", deps.get(0).getName());
            assertEquals(1, deps.get(0).getLongVersion());
            assertEquals("com.test.sdk2", deps.get(1).getName());
            assertEquals(2, deps.get(1).getLongVersion());
        }
    }

    private void runPackageVerifierTest(BiConsumer<Context, Intent> onBroadcast)
            throws Exception {
        runPackageVerifierTest("Success", onBroadcast);
    }

    private void runPackageVerifierTest(String expectedResultStartsWith,
            BiConsumer<Context, Intent> onBroadcast) throws Exception {
        AtomicReference<Thread> onBroadcastThread = new AtomicReference<>();

        runPackageVerifierTestSync(expectedResultStartsWith, (context, intent) -> {
            Thread thread = new Thread(() -> onBroadcast.accept(context, intent));
            thread.start();
            onBroadcastThread.set(thread);
        });

        onBroadcastThread.get().join();
    }

    private void runPackageVerifierTestSync(String expectedResultStartsWith,
            BiConsumer<Context, Intent> onBroadcast) throws Exception {
        // Install a package.
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));

        getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.PACKAGE_VERIFICATION_AGENT);

        // Create a single-use broadcast receiver
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                onBroadcast.accept(context, intent);
            }
        };
        // Create an intent-filter and register the receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);
        intentFilter.addDataType(PACKAGE_MIME_TYPE);
        getContext().registerReceiver(broadcastReceiver, intentFilter, RECEIVER_EXPORTED);

        // Enable verification.
        executeShellCommand("settings put global verifier_verify_adb_installs 1");
        // Override verifier for updates of debuggable apps.
        setSystemProperty("debug.pm.adb_verifier_override_package", CTS_PACKAGE_NAME);

        // Update the package, should trigger verifier override.
        installPackage(TEST_HW7, expectedResultStartsWith);
    }

    @Test
    public void testPackageVerifierAllow() throws Exception {
        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest((context, intent) -> {
            int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
            assertNotEquals(-1, verificationId);

            dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            assertNotEquals(-1, sessionId);

            getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
        });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    public void testPackageVerifierReject() throws Exception {
        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest("Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed]",
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_REJECT);
                });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    public void testPackageVerifierRejectAfterTimeout() throws Exception {
        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTestSync("Success", (context, intent) -> {
            int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
            assertNotEquals(-1, verificationId);

            dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            assertNotEquals(-1, sessionId);

            try {
                if (mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL) {
                    // For streaming installations, the timeout is fixed at 3secs and always
                    // allow the install. Try to extend the timeout and then reject after
                    // much shorter time.
                    getPackageManager().extendVerificationTimeout(verificationId,
                            VERIFICATION_REJECT, mStreamingVerificationTimeoutMs * 3);
                    Thread.sleep(mStreamingVerificationTimeoutMs * 2);
                    getPackageManager().verifyPendingInstall(verificationId,
                            VERIFICATION_REJECT);
                } else {
                    getPackageManager().verifyPendingInstall(verificationId,
                            VERIFICATION_ALLOW);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    public void testPackageVerifierWithExtensionAndTimeout() throws Exception {
        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest((context, intent) -> {
            int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
            assertNotEquals(-1, verificationId);

            dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            assertNotEquals(-1, sessionId);

            try {
                if (mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL) {
                    // For streaming installations, the timeout is fixed at 3secs and always
                    // allow the install. Try to extend the timeout and then reject after
                    // much shorter time.
                    getPackageManager().extendVerificationTimeout(verificationId,
                            VERIFICATION_REJECT, mStreamingVerificationTimeoutMs * 3);
                    Thread.sleep(mStreamingVerificationTimeoutMs * 2);
                    getPackageManager().verifyPendingInstall(verificationId,
                            VERIFICATION_REJECT);
                } else {
                    getPackageManager().verifyPendingInstall(verificationId,
                            VERIFICATION_ALLOW);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    public void testPackageVerifierWithChecksums() throws Exception {
        AtomicInteger dataLoaderType = new AtomicInteger(-1);
        List<ApkChecksum> checksums = new ArrayList<>();
        StringBuilder rootHash = new StringBuilder();

        runPackageVerifierTest((context, intent) -> {
            int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
            assertNotEquals(-1, verificationId);

            dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            assertNotEquals(-1, sessionId);

            try {
                PackageInstaller.Session session = getPackageInstaller().openSession(sessionId);
                assertNotNull(session);

                rootHash.append(intent.getStringExtra(EXTRA_VERIFICATION_ROOT_HASH));

                String[] names = session.getNames();
                assertEquals(1, names.length);
                session.requestChecksums(names[0], 0, PackageManager.TRUST_ALL,
                        ConcurrentUtils.DIRECT_EXECUTOR,
                        result -> checksums.addAll(result));
            } catch (IOException | CertificateEncodingException e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(mDataLoaderType, dataLoaderType.get());

        assertEquals(1, checksums.size());

        if (mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL) {
            assertEquals(TYPE_WHOLE_MERKLE_ROOT_4K_SHA256, checksums.get(0).getType());
            assertEquals(rootHash.toString(),
                    "base.apk:" + HexDump.toHexString(checksums.get(0).getValue()));
        } else {
            assertEquals(TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256, checksums.get(0).getType());
        }
    }

    @Ignore
    @Test
    public void testGetFirstInstallTime() throws Exception {
        final int currentUser = getContext().getUserId();
        final long startTimeMillisForCurrentUser = System.currentTimeMillis();
        installPackage(TEST_HW5);
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, currentUser));
        final long origFirstInstallTimeForCurrentUser = getFirstInstallTimeAsUser(
                TEST_APP_PACKAGE, currentUser);
        // Validate the timestamp
        assertTrue(origFirstInstallTimeForCurrentUser > 0);
        assertTrue(startTimeMillisForCurrentUser < origFirstInstallTimeForCurrentUser);
        assertTrue(System.currentTimeMillis() > origFirstInstallTimeForCurrentUser);

        // Install again with replace and the firstInstallTime should remain the same
        installPackage(TEST_HW5);
        long firstInstallTimeForCurrentUser = getFirstInstallTimeAsUser(
                TEST_APP_PACKAGE, currentUser);
        assertEquals(origFirstInstallTimeForCurrentUser, firstInstallTimeForCurrentUser);

        // Start another user and install this test itself for that user
        mSecondUser = createUser("Another User");
        assertTrue(startUser(mSecondUser));
        long startTimeMillisForSecondUser = System.currentTimeMillis();
        installExistingPackageAsUser(getContext().getPackageName(), mSecondUser);
        assertTrue(isAppInstalledForUser(getContext().getPackageName(), mSecondUser));
        // Install test package with replace
        installPackageAsUser(TEST_HW5, mSecondUser);
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, mSecondUser));
        firstInstallTimeForCurrentUser = getFirstInstallTimeAsUser(
                TEST_APP_PACKAGE, currentUser);
        // firstInstallTime should remain unchanged for the current user
        assertEquals(origFirstInstallTimeForCurrentUser, firstInstallTimeForCurrentUser);

        long firstInstallTimeForSecondUser = getFirstInstallTimeAsUser(
                TEST_APP_PACKAGE, mSecondUser);
        // firstInstallTime for the other user should be different
        assertNotEquals(firstInstallTimeForCurrentUser, firstInstallTimeForSecondUser);
        assertTrue(startTimeMillisForSecondUser < firstInstallTimeForSecondUser);
        assertTrue(System.currentTimeMillis() > firstInstallTimeForSecondUser);

        // Uninstall for the other user
        uninstallPackageAsUser(TEST_APP_PACKAGE, mSecondUser);
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, mSecondUser));
        // Install test package as an existing package
        startTimeMillisForSecondUser = System.currentTimeMillis();
        installExistingPackageAsUser(TEST_APP_PACKAGE, mSecondUser);
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, mSecondUser));

        firstInstallTimeForCurrentUser = getFirstInstallTimeAsUser(
                TEST_APP_PACKAGE, currentUser);
        // firstInstallTime still remains unchanged for the current user
        assertEquals(origFirstInstallTimeForCurrentUser, firstInstallTimeForCurrentUser);
        firstInstallTimeForSecondUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, mSecondUser);
        // firstInstallTime for the other user should be different
        assertNotEquals(firstInstallTimeForCurrentUser, firstInstallTimeForSecondUser);
        assertTrue(startTimeMillisForSecondUser < firstInstallTimeForSecondUser);
        assertTrue(System.currentTimeMillis() > firstInstallTimeForSecondUser);

        // Uninstall for all users
        uninstallPackageSilently(TEST_APP_PACKAGE);
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, currentUser));
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, mSecondUser));
        // Reinstall for all users
        installPackage(TEST_HW5);
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, currentUser));
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, mSecondUser));
        firstInstallTimeForCurrentUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, currentUser);
        // First install time is now different because the package was fully uninstalled
        assertNotEquals(origFirstInstallTimeForCurrentUser, firstInstallTimeForCurrentUser);
        firstInstallTimeForSecondUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, mSecondUser);
        // Same firstInstallTime because package was installed for both users at the same time
        assertEquals(firstInstallTimeForCurrentUser, firstInstallTimeForSecondUser);
    }

    private long getFirstInstallTimeAsUser(String packageName, int userId)
            throws PackageManager.NameNotFoundException {
        final Context contextAsUser = getContext().createContextAsUser(UserHandle.of(userId), 0);
        final PackageManager packageManager = contextAsUser.getPackageManager();
        final PackageInfo packageInfo = packageManager.getPackageInfo(packageName,
                PackageManager.PackageInfoFlags.of(0));
        return packageInfo.firstInstallTime;
    }

    @Test
    public void testAppWithNoAppStorageUpdateSuccess() throws Exception {
        installPackage(TEST_HW_NO_APP_STORAGE);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        // Updates that don't change value of NO_APP_DATA_STORAGE property are allowed.
        installPackage(TEST_HW_NO_APP_STORAGE);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdateAddsNoAppDataStorageProperty() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        installPackage(
                TEST_HW_NO_APP_STORAGE,
                "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Update "
                        + "attempted to change value of "
                        + "android.internal.PROPERTY_NO_APP_DATA_STORAGE");
    }

    @Test
    public void testAppUpdateRemovesNoAppDataStorageProperty() throws Exception {
        installPackage(TEST_HW_NO_APP_STORAGE);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        installPackage(
                TEST_HW5,
                "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Update "
                        + "attempted to change value of "
                        + "android.internal.PROPERTY_NO_APP_DATA_STORAGE");
    }

    @Test
    public void testNoAppDataStoragePropertyCanChangeAfterUninstall() throws Exception {
        installPackage(TEST_HW_NO_APP_STORAGE);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        uninstallPackageSilently(TEST_APP_PACKAGE);
        // After app is uninstalled new install can change the value of the property.
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Ignore
    @Test
    public void testPackageFullyRemovedBroadcastAfterUninstall() throws IOException {
        final int currentUser = getContext().getUserId();
        // Start another user and install this test itself for that user
        mSecondUser = createUser("Another User");
        assertTrue(startUser(mSecondUser));
        installExistingPackageAsUser(getContext().getPackageName(), mSecondUser);
        installPackage(TEST_HW5);
        assertTrue(isAppInstalledForUser(getContext().getPackageName(), currentUser));
        assertTrue(isAppInstalledForUser(getContext().getPackageName(), mSecondUser));
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, currentUser));
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, mSecondUser));
        final FullyRemovedBroadcastReceiver broadcastReceiverForCurrentUser =
                new FullyRemovedBroadcastReceiver(TEST_APP_PACKAGE, currentUser);
        final FullyRemovedBroadcastReceiver broadcastReceiverForSecondUser =
                new FullyRemovedBroadcastReceiver(TEST_APP_PACKAGE, mSecondUser);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        intentFilter.addDataScheme("package");
        getContext().registerReceiver(
                broadcastReceiverForCurrentUser, intentFilter, RECEIVER_EXPORTED);
        getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.INTERACT_ACROSS_USERS,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            getContext().createContextAsUser(UserHandle.of(mSecondUser), 0).registerReceiver(
                    broadcastReceiverForSecondUser, intentFilter, RECEIVER_EXPORTED);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
        // Verify that uninstall with "keep data" doesn't send the broadcast
        uninstallPackageWithKeepData(TEST_APP_PACKAGE, mSecondUser);
        assertFalse(broadcastReceiverForSecondUser.isBroadcastReceived());
        installExistingPackageAsUser(TEST_APP_PACKAGE, mSecondUser);
        // Verify that uninstall on a specific user only sends the broadcast to the user
        uninstallPackageAsUser(TEST_APP_PACKAGE, mSecondUser);
        assertTrue(broadcastReceiverForSecondUser.isBroadcastReceived());
        assertFalse(broadcastReceiverForCurrentUser.isBroadcastReceived());
        uninstallPackageSilently(TEST_APP_PACKAGE);
        assertTrue(broadcastReceiverForCurrentUser.isBroadcastReceived());
    }

    @Test
    public void testQuerySdkSandboxPackageName() throws Exception {
        final PackageManager pm = getPackageManager();
        final String name = pm.getSdkSandboxPackageName();
        assertNotNull(name);
        final ApplicationInfo info = pm.getApplicationInfo(
                name, PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY));
        assertEquals(ApplicationInfo.FLAG_SYSTEM, info.flags & ApplicationInfo.FLAG_SYSTEM);
        assertTrue(info.sourceDir.startsWith("/apex/com.android.adservices"));
    }

    @Test
    public void testGetPackagesForUid_sdkSandboxUid() throws Exception {
        final PackageManager pm = getPackageManager();
        final String[] pkgs = pm.getPackagesForUid(Process.toSdkSandboxUid(10239));
        assertEquals(1, pkgs.length);
        assertEquals(pm.getSdkSandboxPackageName(), pkgs[0]);
    }

    @Test
    public void testGetNameForUid_sdkSandboxUid() throws Exception {
        final PackageManager pm = getPackageManager();
        final String pkgName = pm.getNameForUid(Process.toSdkSandboxUid(11543));
        assertEquals(pm.getSdkSandboxPackageName(), pkgName);
    }

    @Test
    public void testGetNamesForUids_sdkSandboxUids() throws Exception {
        final PackageManager pm = getPackageManager();
        final int[] uids = new int[]{Process.toSdkSandboxUid(10101)};
        final String[] names = pm.getNamesForUids(uids);
        assertEquals(1, names.length);
        assertEquals(pm.getSdkSandboxPackageName(), names[0]);
    }

    private static class FullyRemovedBroadcastReceiver extends BroadcastReceiver {
        private final String mTargetPackage;
        private final int mTargetUserId;
        private final ConditionVariable mUserReceivedBroadcast;
        FullyRemovedBroadcastReceiver(String packageName, int targetUserId) {
            mTargetPackage = packageName;
            mTargetUserId = targetUserId;
            mUserReceivedBroadcast = new ConditionVariable();
            mUserReceivedBroadcast.close();
        }
        @Override
        public void onReceive(Context context, Intent intent) {
            context.unregisterReceiver(this);
            final String packageName = intent.getData().getEncodedSchemeSpecificPart();
            final int userId = context.getUserId();
            if (intent.getAction().equals(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                    && packageName.equals(mTargetPackage) && userId == mTargetUserId) {
                mUserReceivedBroadcast.open();
            }
        }
        public boolean isBroadcastReceived() {
            return mUserReceivedBroadcast.block(2000);
        }
    }

    private List<SharedLibraryInfo> getSharedLibraries() {
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            return getPackageManager().getSharedLibraries(PackageManager.PackageInfoFlags.of(0));
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private SharedLibraryInfo findLibrary(List<SharedLibraryInfo> libs, String name, long version) {
        for (int i = 0, size = libs.size(); i < size; ++i) {
            SharedLibraryInfo lib = libs.get(i);
            if (name.equals(lib.getName()) && version == lib.getLongVersion()) {
                return lib;
            }
        }
        return null;
    }

    private String createUpdateSession(String packageName) throws IOException {
        return createSession("-p " + packageName);
    }

    private String createSession(String arg) throws IOException {
        final String prefix = "Success: created install session [";
        final String suffix = "]\n";
        final String commandResult = executeShellCommand("pm install-create " + arg);
        assertTrue(commandResult, commandResult.startsWith(prefix));
        assertTrue(commandResult, commandResult.endsWith(suffix));
        return commandResult.substring(prefix.length(), commandResult.length() - suffix.length());
    }

    private void addSplits(String sessionId, String[] splitNames) throws IOException {
        for (String splitName : splitNames) {
            File file = new File(splitName);
            assertEquals(
                    "Success: streamed " + file.length() + " bytes\n",
                    executeShellCommand("pm install-write " + sessionId + " " + file.getName() + " "
                            + splitName));
        }
    }

    private void addSplitsStdIn(String sessionId, String[] splitNames, String args)
            throws IOException {
        for (String splitName : splitNames) {
            File file = new File(splitName);
            assertEquals("Success: streamed " + file.length() + " bytes\n", executeShellCommand(
                    "pm install-write -S " + file.length() + " " + sessionId + " " + file.getName()
                            + " " + args, file));
        }
    }

    private void removeSplits(String sessionId, String[] splitNames) throws IOException {
        for (String splitName : splitNames) {
            assertEquals("Success\n",
                    executeShellCommand("pm install-remove " + sessionId + " " + splitName));
        }
    }

    private void removeSplitsBatch(String sessionId, String[] splitNames) throws IOException {
        assertEquals("Success\n", executeShellCommand(
                "pm install-remove " + sessionId + " " + String.join(" ", splitNames)));
    }

    private void commitSession(String sessionId) throws IOException {
        assertEquals("Success\n", executeShellCommand("pm install-commit " + sessionId));
    }

    private boolean isAppInstalled(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm list packages");
        final int prefixLength = "package:".length();
        return Arrays.stream(commandResult.split("\\r?\\n")).anyMatch(
                line -> line.length() > prefixLength && line.substring(prefixLength).equals(
                        packageName));
    }

    private boolean isAppInstalledForUser(String packageName, int userId) throws IOException {
        final String commandResult = executeShellCommand(
                String.format("pm list packages --user %d %s", userId, packageName)
        );
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.equals("package:" + packageName));
    }

    private boolean isSdkInstalled(String name, int versionMajor) throws IOException {
        final String sdkString = name + ":" + versionMajor;
        final String commandResult = executeShellCommand("pm list sdks");
        final int prefixLength = "sdk:".length();
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.length() > prefixLength && line.substring(
                        prefixLength).equals(sdkString));
    }

    private String getPackageCertDigest(String packageName) throws Exception {
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            PackageInfo sdkPackageInfo = getPackageManager().getPackageInfo(packageName,
                    PackageManager.PackageInfoFlags.of(
                            GET_SIGNING_CERTIFICATES | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));
            SigningInfo signingInfo = sdkPackageInfo.signingInfo;
            Signature[] signatures =
                    signingInfo != null ? signingInfo.getSigningCertificateHistory() : null;
            byte[] digest = PackageUtils.computeSha256DigestBytes(signatures[0].toByteArray());
            return new String(HexEncoding.encode(digest));
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private String getSplits(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm dump " + packageName);
        final String prefix = "    splits=[";
        final int prefixLength = prefix.length();
        Optional<String> maybeSplits = Arrays.stream(commandResult.split("\\r?\\n"))
                .filter(line -> line.startsWith(prefix)).findFirst();
        if (!maybeSplits.isPresent()) {
            return null;
        }
        String splits = maybeSplits.get();
        return splits.substring(prefixLength, splits.length() - 1);
    }

    private static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName;
    }

    /* Install for all the users */
    private void installPackage(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " -t -g " + file.getPath()));
    }

    private void installPackage(String baseName, String expectedResultStartsWith)
            throws IOException {
        File file = new File(createApkPath(baseName));
        String result = executeShellCommand("pm " + mInstall + " -t -g " + file.getPath());
        assertTrue(result, result.startsWith(expectedResultStartsWith));
    }

    /* Install a package for a new user; this would replace the old package */
    private void installPackageAsUser(String baseName, int userId) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " -t -g --user " + userId + " " + file.getPath()));
    }

    /* Install an existing package for a new user */
    private void installExistingPackageAsUser(String packageName, int userId) throws IOException {
        String result = executeShellCommand(
                String.format("pm install-existing --user %d %s", userId, packageName));
        assertEquals("Package " + packageName + " installed for user: " + userId + "\n", result);
    }

    private void updatePackage(String packageName, String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " -t -p " + packageName + " -g " + file.getPath()));
    }

    private void installPackageStdIn(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n",
                executeShellCommand("pm " + mInstall + " -t -g -S " + file.length(), file));
    }

    private void updatePackageStdIn(String packageName, String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " -t -p " + packageName + " -g -S " + file.length(), file));
    }

    private void installSplits(String[] baseNames) throws IOException {
        if (mStreaming) {
            installSplitsBatch(baseNames);
            return;
        }
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        String sessionId = createSession(TEST_APP_PACKAGE);
        addSplits(sessionId, splits);
        commitSession(sessionId);
    }

    private void updateSplits(String[] baseNames) throws IOException {
        if (mStreaming) {
            updateSplitsBatch(baseNames);
            return;
        }
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        String sessionId = createSession("-p " + TEST_APP_PACKAGE);
        addSplits(sessionId, splits);
        commitSession(sessionId);
    }

    private void installSplitsStdInStreaming(String[] splits) throws IOException {
        File[] files = Arrays.stream(splits).map(split -> new File(split)).toArray(File[]::new);
        String param = Arrays.stream(files).map(
                file -> file.getName() + ":" + file.length()).collect(Collectors.joining(" "));
        assertEquals("Success\n", executeShellCommand("pm" + mInstall + param, files));
    }

    private void installSplitsStdIn(String[] baseNames, String args) throws IOException {
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        if (mStreaming) {
            installSplitsStdInStreaming(splits);
            return;
        }
        String sessionId = createSession(TEST_APP_PACKAGE);
        addSplitsStdIn(sessionId, splits, args);
        commitSession(sessionId);
    }

    private void installSplitsBatch(String[] baseNames) throws IOException {
        final String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        assertEquals("Success\n",
                executeShellCommand("pm " + mInstall + " -t -g " + String.join(" ", splits)));
    }

    private void updateSplitsBatch(String[] baseNames) throws IOException {
        final String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " -p " + TEST_APP_PACKAGE + " -t -g " + String.join(" ",
                        splits)));
    }

    private void uninstallPackage(String packageName, String expectedResultStartsWith)
            throws IOException {
        String result = uninstallPackageSilently(packageName);
        assertTrue(result, result.startsWith(expectedResultStartsWith));
    }

    private String uninstallPackageSilently(String packageName) throws IOException {
        return executeShellCommand("pm uninstall " + packageName);
    }

    /* Uninstall for one user */
    private void uninstallPackageAsUser(String packageName, int userId) throws IOException {
        executeShellCommand(String.format("pm uninstall --user %d %s", userId, packageName));
    }

    private void uninstallPackageWithKeepData(String packageName, int userId) throws IOException {
        executeShellCommand(String.format("pm uninstall -k --user %d %s", userId, packageName));
    }

    private void uninstallSplits(String packageName, String[] splitNames) throws IOException {
        for (String splitName : splitNames) {
            assertEquals("Success\n",
                    executeShellCommand("pm uninstall " + packageName + " " + splitName));
        }
    }

    private void uninstallSplitsBatch(String packageName, String[] splitNames) throws IOException {
        assertEquals("Success\n", executeShellCommand(
                "pm uninstall " + packageName + " " + String.join(" ", splitNames)));
    }

    private void setSystemProperty(String name, String value) throws Exception {
        assertEquals("", executeShellCommand("setprop " + name + " " + value));
    }

    private int createUser(String name) throws IOException {
        final String output = executeShellCommand("pm create-user " + name);
        if (output.startsWith("Success")) {
            return Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
        }
        throw new IllegalStateException(String.format("Failed to create user: %s", output));
    }

    private void removeUser(int userId) throws IOException {
        executeShellCommand("pm remove-user " + userId);
    }

    private boolean startUser(int userId) throws IOException {
        String cmd = "am start-user -w " + userId;
        final String output = executeShellCommand(cmd);
        if (output.startsWith("Error")) {
            return false;
        }
        String state = executeShellCommand("am get-started-user-state " + userId);
        return state.contains("RUNNING_UNLOCKED");
    }

    private void stopUser(int userId) throws IOException {
        executeShellCommand("am stop-user -w -f " + userId);
    }
}

