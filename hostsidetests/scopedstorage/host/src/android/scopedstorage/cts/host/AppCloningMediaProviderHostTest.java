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

package android.scopedstorage.cts.host;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.CommandResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class AppCloningMediaProviderHostTest extends BaseHostTestCase{

    protected static final String DEVICE_TEST_APP_PACKAGE = "android.scopedstorage.cts";
    protected static final String DEVICE_TEST_APP = "AppCloningDeviceTest.apk";
    private static final String DEVICE_TEST_CLASS = DEVICE_TEST_APP_PACKAGE
            + ".AppCloningDeviceTest";
    // This app performs the File Creation and Read operations from the Device.
    protected static final String SCOPED_STORAGE_TEST_APP_B_APK = "CtsScopedStorageTestAppB.apk";

    private static final int CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS = 20000;
    private static final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000;
    private static final String EXTERNAL_STORAGE_PATH = "/storage/emulated/%d/";
    private static final String CURRENT_USER_ID = "currentUserId";
    private static final String FILE_TO_BE_CREATED = "fileToBeCreated";
    /**
     * Provide different name to Files being created, on each execution of the test, so that
     * flakiness from previously existing files can be avoided.
     */
    private static final String NONCE = String.valueOf(System.nanoTime());
    private static String sCloneUserId;

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        final ITestDevice sDevice = testInfo.getDevice();
        assertThat(sDevice).isNotNull();

        setDevice(sDevice);

        assumeTrue("Device doesn't support multiple users", supportsMultipleUsers());
        assumeFalse("Device is in headless system user mode", isHeadlessSystemUserMode());
        assumeTrue(isAtLeastS());
        assumeFalse("Device uses sdcardfs", usesSdcardFs());

        // create clone user
        String output = sDevice.executeShellCommand(
                "pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE "
                        + "testUser");
        sCloneUserId = output.substring(output.lastIndexOf(' ') + 1).replaceAll("[^0-9]",
                "");
        assertThat(sCloneUserId).isNotEmpty();
        // start clone user
        CommandResult out = sDevice.executeShellV2Command("am start-user -w " + sCloneUserId);
        assertThat(isSuccessful(out)).isTrue();

        Integer mCloneUserIdInt = Integer.parseInt(sCloneUserId);
        String sCloneUserStoragePath = String.format(EXTERNAL_STORAGE_PATH,
                Integer.parseInt(sCloneUserId));
        // Check that the clone user directories have been created
        eventually(() -> sDevice.doesFileExist(sCloneUserStoragePath, mCloneUserIdInt),
                CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS);
    }

    @AfterClassWithInfo
    public static void afterClass(TestInformation testInfo) throws Exception {
        if (!supportsMultipleUsers() || isHeadlessSystemUserMode() || !isAtLeastS()
                || usesSdcardFs()) return;
        testInfo.getDevice().executeShellCommand("pm remove-user " + sCloneUserId);
    }

    @Test
    public void testGetFilesInDirectoryViaMediaProvider() throws Exception {
        // Install the Device Test App in both the user spaces.
        installPackage(DEVICE_TEST_APP, "--user all");
        // Install the Scoped Storage Test App in both the user spaces.
        installPackage(SCOPED_STORAGE_TEST_APP_B_APK, "--user all");

        int currentUserId = getCurrentUserId();
        final String fileName = "tmpFileToPush" + NONCE + ".png";

        // We add the file in DCIM directory of User 0.
        Map<String, String> ownerArgs = new HashMap<>();
        ownerArgs.put(CURRENT_USER_ID, String.valueOf(currentUserId));
        ownerArgs.put(FILE_TO_BE_CREATED, fileName);
        runDeviceTestAsUserInPkgA("testInsertFilesInDirectoryViaMediaProvider",
                currentUserId, ownerArgs);

        // We add the file in DCIM directory of Cloned User.
        Map<String, String> cloneArgs = new HashMap<>();
        cloneArgs.put(CURRENT_USER_ID, sCloneUserId);
        cloneArgs.put(FILE_TO_BE_CREATED, fileName);
        runDeviceTestAsUserInPkgA("testInsertFilesInDirectoryViaMediaProvider",
                Integer.parseInt(sCloneUserId), cloneArgs);

        // Querying as user 0 should enlist a single file (file created by user 0).
        runDeviceTestAsUserInPkgA("testGetFilesInDirectoryViaMediaProviderRespectsUserId",
                currentUserId, ownerArgs);

        // Querying as cloned user should enlist a single file (file created by cloned user).
        runDeviceTestAsUserInPkgA("testGetFilesInDirectoryViaMediaProviderRespectsUserId",
                Integer.parseInt(sCloneUserId), cloneArgs);
    }

    protected void runDeviceTestAsUserInPkgA(@Nonnull String testMethod, int userId,
            @Nonnull Map<String, String> args) throws Exception {
        DeviceTestRunOptions deviceTestRunOptions =
                new DeviceTestRunOptions(DEVICE_TEST_APP_PACKAGE)
                .setDevice(getDevice())
                .setTestClassName(DEVICE_TEST_CLASS)
                .setTestMethodName(testMethod)
                .setMaxInstrumentationTimeoutMs(DEFAULT_INSTRUMENTATION_TIMEOUT_MS)
                .setUserId(userId);
        for (Map.Entry<String, String> entry : args.entrySet()) {
            deviceTestRunOptions.addInstrumentationArg(entry.getKey(), entry.getValue());
        }
        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(deviceTestRunOptions)).isTrue();
    }
}
