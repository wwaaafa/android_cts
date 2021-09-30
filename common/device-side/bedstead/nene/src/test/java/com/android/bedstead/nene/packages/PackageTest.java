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

package com.android.bedstead.nene.packages;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

@RunWith(JUnit4.class)
public class PackageTest {

    // Controlled by AndroidTest.xml
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.bedstead.nene.testapps.TestApp1";
    private static final File TEST_APP_APK_FILE =
            new File("/data/local/tmp/NeneTestApp1.apk");

    private static final String ACCESS_NETWORK_STATE_PERMISSION =
            "android.permission.ACCESS_NETWORK_STATE";

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final UserReference sUser = TestApis.users().instrumented();

    @Test
    public void installedOnUsers_includesUserWithPackageInstalled() {
        TestApis.packages().install(sUser, TEST_APP_APK_FILE);
        PackageReference packageReference = TestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(packageReference.resolve().installedOnUsers()).contains(sUser);
        } finally {
            packageReference.uninstall(sUser);
        }
    }

    @Test
    public void installedOnUsers_doesNotIncludeUserWithoutPackageInstalled() {
        UserReference user = TestApis.users().createUser().create();
        TestApis.packages().install(sUser, TEST_APP_APK_FILE);
        PackageReference packageReference = TestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(packageReference.resolve().installedOnUsers()).doesNotContain(user);
        } finally {
            packageReference.uninstall(sUser);
            user.remove();
        }
    }

    @Test
    public void grantedPermission_includesKnownInstallPermission() {
        // TODO(scottjonathan): This relies on the fact that the instrumented app declares
        //  ACCESS_NETWORK_STATE - this should be replaced with TestApp with a useful query
        PackageReference packageReference = TestApis.packages().find(sContext.getPackageName());

        assertThat(packageReference.resolve().grantedPermissions(sUser))
                .contains(ACCESS_NETWORK_STATE_PERMISSION);
    }
}
