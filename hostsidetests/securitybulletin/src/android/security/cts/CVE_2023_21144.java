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

package android.security.cts;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21144 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 252766417)
    @Test
    public void testPocCVE_2023_21144() {
        ITestDevice device = null;
        try {
            device = getDevice();
            final String testPkg = "android.security.cts.CVE_2023_21144";
            installPackage("CVE-2023-21144.apk", "-g");

            // Allowing notification listener service for PocListenerService
            device.executeShellCommand(
                    "cmd notification allow_listener " + testPkg + "/.PocListenerService");

            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testPocCVE_2023_21144");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            // To undo the expanded notification panel
            try {
                device.executeShellCommand("input keyevent KEYCODE_HOME");
            } catch (Exception ignore) {
                // Ignore
            }
        }
    }
}
