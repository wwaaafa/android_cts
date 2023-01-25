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

package android.sdksandbox.webkit.cts;

import android.app.sdksandbox.testutils.testscenario.KeepSdkSandboxAliveRule;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CookieTest {
    @ClassRule
    public static final KeepSdkSandboxAliveRule sSdkTestSuiteSetup =
            new KeepSdkSandboxAliveRule("com.android.emptysdkprovider");

    @Before
    public void setUp() {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
    }

    @Rule
    public final WebViewSandboxTestRule sdkTester =
            new WebViewSandboxTestRule("android.webkit.cts.CookieTest");

    @Presubmit
    @Test
    public void testDomain() throws Exception {
        sdkTester.assertSdkTestRunPasses("testDomain");
    }

    @Test
    public void testSubDomain() throws Exception {
        sdkTester.assertSdkTestRunPasses("testSubDomain");
    }

    @Test
    public void testInvalidDomain() throws Exception {
        sdkTester.assertSdkTestRunPasses("testInvalidDomain");
    }

    @Test
    public void testPath() throws Exception {
        sdkTester.assertSdkTestRunPasses("testPath");
    }

    @Test
    public void testEmptyValue() throws Exception {
        sdkTester.assertSdkTestRunPasses("testEmptyValue");
    }
}
