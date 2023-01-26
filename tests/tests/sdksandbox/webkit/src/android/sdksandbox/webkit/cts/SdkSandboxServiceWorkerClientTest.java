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
public class SdkSandboxServiceWorkerClientTest {
    @ClassRule
    public static final KeepSdkSandboxAliveRule sSdkTestSuiteSetup =
            new KeepSdkSandboxAliveRule("com.android.emptysdkprovider");

    @Rule
    public final WebViewSandboxTestRule sdkTester =
            new WebViewSandboxTestRule("android.webkit.cts.ServiceWorkerClientTest");

    @Before
    public void setUp() {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
    }

    @Test
    public void testServiceWorkerClientInterceptCallback() throws Exception {
        sdkTester.assertSdkTestRunPasses("testServiceWorkerClientInterceptCallback");
    }

    @Test
    public void testSetNullServiceWorkerClient() throws Exception {
        sdkTester.assertSdkTestRunPasses("testSetNullServiceWorkerClient");
    }
}
