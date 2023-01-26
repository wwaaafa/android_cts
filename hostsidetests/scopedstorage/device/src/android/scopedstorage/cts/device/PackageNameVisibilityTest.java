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

package android.scopedstorage.cts.device;

import static androidx.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertEquals;

import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.scopedstorage.cts.lib.TestUtils;

import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.TestApp;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
public final class PackageNameVisibilityTest {

    private static Uri sQueryableMediaUri;
    private static Uri sNotQueryableMediaUri;

    private static final String TAG = PackageNameVisibilityTest.class.getSimpleName();

    private static final TestApp TEST_APP_WITH_QUERIES_TAG = new TestApp(
            "TestAppWithQueriesTag",
            "android.scopedstorage.cts.testapp.withqueriestag", 1, false,
            "CtsTestAppWithQueriesTag.apk");

    private static final TestApp TEST_APP_WITH_QUERY_ALL_PACKAGES_TAG = new TestApp(
            "TestAppWithQueryAllPackagesPermission",
            "android.scopedstorage.cts.testapp.withqueryallpackagestag", 1, false,
            "CtsTestAppWithQueryAllPackagesPermission.apk");

    private static final TestApp TEST_APP_QUERYABLE = new TestApp("TestAppFileManager",
            "android.scopedstorage.cts.testapp.filemanager", 1, false,
            "CtsScopedStorageTestAppFileManager.apk");

    private static final TestApp TEST_APP_NOT_QUERYABLE = new TestApp("TestAppA",
            "android.scopedstorage.cts.testapp.A.withres", 1, false,
            "CtsScopedStorageTestAppA.apk");

    @BeforeClass
    public static void setUp() throws Exception {
        TestUtils.waitForMountedAndIdleState(getTargetContext().getContentResolver());

        // Creating two media files, one is created by an app that
        // is present in the queries tag of TEST_APP_WITH_QUERIES_TAG
        // and the other one is created by an app that is not
        sQueryableMediaUri = createMediaAs(TEST_APP_QUERYABLE);
        sNotQueryableMediaUri = createMediaAs(TEST_APP_NOT_QUERYABLE);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        TestUtils.deleteFileAs(TEST_APP_QUERYABLE, sQueryableMediaUri.getPath());
        TestUtils.deleteFileAs(TEST_APP_NOT_QUERYABLE, sNotQueryableMediaUri.getPath());
    }

    @Test
    public void testNotQueryableOwnerPackageName() throws Exception {
        final String[] ownerPackageNames = TestUtils.queryForOwnerPackageNamesAs(
                TEST_APP_WITH_QUERIES_TAG, sNotQueryableMediaUri);

        assertEquals(0, ownerPackageNames.length);
    }

    @Test
    public void testQueryableOwnerPackageName() throws Exception {
        final String[] ownerPackageNames = TestUtils.queryForOwnerPackageNamesAs(
                TEST_APP_WITH_QUERIES_TAG, sQueryableMediaUri);

        assertEquals(Arrays.asList(TEST_APP_QUERYABLE.getPackageName()),
                Arrays.asList(ownerPackageNames));
    }

    @Test
    public void testMultipleRows() throws Exception {
        final String[] ownerPackageNames = TestUtils.queryForOwnerPackageNamesAs(
                TEST_APP_WITH_QUERIES_TAG,
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY));

        // Should only get a queryable owner package name and filter out the other one
        assertEquals(Arrays.asList(TEST_APP_QUERYABLE.getPackageName()),
                Arrays.asList(ownerPackageNames));
    }

    @Test
    public void testQueryAllPackagesTag() throws Exception {
        final String[] ownerPackageNames = TestUtils.queryForOwnerPackageNamesAs(
                TEST_APP_WITH_QUERY_ALL_PACKAGES_TAG, sNotQueryableMediaUri);

        assertEquals(Arrays.asList(TEST_APP_NOT_QUERYABLE.getPackageName()),
                Arrays.asList(ownerPackageNames));
    }

    private static Uri createMediaAs(TestApp testApp) {
        try {
            return TestUtils.createImageEntryForUriAs(testApp,
                    "/" + System.nanoTime() + ".jpg");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
