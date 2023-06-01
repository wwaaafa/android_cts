/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.os.cts;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OsHostTests extends DeviceTestCase implements IBuildReceiver, IAbiReceiver {
    private static final String TEST_APP_PACKAGE = "android.os.app";
    private static final String TEST_NON_EXPORTED_ACTIVITY_CLASS = "TestNonExported";

    private static final String START_NON_EXPORTED_ACTIVITY_COMMAND = String.format(
            "am start -n %s/%s.%s",
            TEST_APP_PACKAGE, TEST_APP_PACKAGE, TEST_NON_EXPORTED_ACTIVITY_CLASS);

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;
    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Get the device, this gives a handle to run commands and install APKs.
        mDevice = getDevice();
    }

    /**
     * Test whether non-exported activities are properly not launchable.
     *
     * @throws Exception
     */
    @AppModeFull(reason = "Error message is different for instant app (Activity does not exist)")
    public void testNonExportedActivities() throws Exception {
        // Run as unroot
        boolean wasRoot = mDevice.isAdbRoot();
        try {
            mDevice.disableAdbRoot();
            // Attempt to launch the non-exported activity in the test app
            CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
            mDevice.executeShellCommand(START_NON_EXPORTED_ACTIVITY_COMMAND, outputReceiver);
            final String output = outputReceiver.getOutput();

            assertTrue(output.contains("Permission Denial") && output.contains(" not exported"));
        } finally {
            // Restore back to original root state
            if (wasRoot) {
                mDevice.enableAdbRoot();
            }
        }
    }


    /*
     * Helper: find a test apk
     */
    private File getTestAppFile(String fileName) throws FileNotFoundException {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        return buildHelper.getTestFile(fileName);
    }
}
