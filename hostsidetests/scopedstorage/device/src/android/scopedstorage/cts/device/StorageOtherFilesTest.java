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

import static android.scopedstorage.cts.device.OwnedFilesRule.RESOURCE_ID_WITH_METADATA;
import static android.scopedstorage.cts.lib.FilePathAccessTestUtils.assertCannotReadOrWrite;
import static android.scopedstorage.cts.lib.FilePathAccessTestUtils.assertFileAccess_listFiles;
import static android.scopedstorage.cts.lib.FilePathAccessTestUtils.assertFileAccess_readOnly;
import static android.scopedstorage.cts.lib.RedactionTestHelper.assertExifMetadataMatch;
import static android.scopedstorage.cts.lib.RedactionTestHelper.assertExifMetadataMismatch;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadataFromFile;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadataFromRawResource;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_canReadThumbnail;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_listFiles;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_noReadNoWrite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_noWrite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_readOnly;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_readWrite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_uriDoesNotExist;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_uriIsFavorite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_uriIsNotFavorite;
import static android.scopedstorage.cts.lib.TestUtils.doEscalation;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.getDcimDir;
import static android.scopedstorage.cts.lib.TestUtils.pollForPermission;
import static android.scopedstorage.cts.lib.TestUtils.revokeAccessMediaLocation;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.scopedstorage.cts.lib.TestUtils;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
public class StorageOtherFilesTest {

    protected static final String TAG = "MediaProviderOtherFilePermissionTest";
    private static final String THIS_PACKAGE_NAME =
            ApplicationProvider.getApplicationContext().getPackageName();
    private static final ContentResolver sContentResolver = getContentResolver();
    @ClassRule
    public static final OtherAppFilesRule sFilesRule = new OtherAppFilesRule(sContentResolver);

    private static final File IMAGE_FILE_READABLE = sFilesRule.getImageFile1();
    private static final File IMAGE_FILE_NO_ACCESS = sFilesRule.getImageFile2();

    private static final File VIDEO_FILE_READABLE = sFilesRule.getVideoFile1();
    private static final File VIDEO_FILE_NO_ACCESS = sFilesRule.getVideoFile2();


    // Cannot be static as the underlying resource isn't
    private final Uri mImageUriReadable = sFilesRule.getImageUri1();
    private final Uri mImageUriNoAccess = sFilesRule.getImageUri2();
    private final Uri mVideoUriReadable = sFilesRule.getVideoUri1();
    private final Uri mVideoUriNoAccess = sFilesRule.getVideoUri2();

    @BeforeClass
    public static void init() throws Exception {
        pollForPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, true);
        // creating grants only for one
        grantReadAccess(IMAGE_FILE_READABLE);
        grantReadAccess(VIDEO_FILE_READABLE);
    }

    @Test
    public void other_listMediaFiles() throws Exception {
        Set<File> expectedValues = Set.of(IMAGE_FILE_READABLE, VIDEO_FILE_READABLE);
        Set<File> notExpected = Set.of(IMAGE_FILE_NO_ACCESS, VIDEO_FILE_NO_ACCESS);
        // File access
        assertFileAccess_listFiles(IMAGE_FILE_READABLE.getParentFile(), expectedValues,
                notExpected);
        // Query DCIM
        assertResolver_listFiles(Environment.DIRECTORY_DCIM, expectedValues, notExpected,
                sContentResolver);
    }

    @Test
    public void other_readVisualMediaFiles() throws Exception {
        assertResolver_readOnly(mImageUriReadable, sContentResolver);
        assertResolver_readOnly(mVideoUriReadable, sContentResolver);
        assertResolver_noReadNoWrite(mImageUriNoAccess, sContentResolver);
        assertResolver_noReadNoWrite(mVideoUriNoAccess, sContentResolver);

        assertFileAccess_readOnly(IMAGE_FILE_READABLE);
        assertFileAccess_readOnly(VIDEO_FILE_READABLE);
        assertCannotReadOrWrite(IMAGE_FILE_NO_ACCESS);
        assertCannotReadOrWrite(VIDEO_FILE_NO_ACCESS);
    }

    @Test
    public void other_readThumbnails() throws Exception {
        assertResolver_canReadThumbnail(mImageUriReadable, sContentResolver);
        // TODO b/216249186 check file permissions for thumbnails
        // It is currently not working MediaProvider#8285
        // assertResolver_cannotReadThumbnail(mImageUriNoAccess, sContentResolver);
        // TODO Video thumbnails
    }

    @Test
    public void other_createWriteRequest() throws Exception {
        doEscalation(
                MediaStore.createWriteRequest(sContentResolver, Collections.singletonList(
                        mImageUriReadable)));
        assertResolver_readWrite(mImageUriReadable, sContentResolver);
        assertResolver_noReadNoWrite(mImageUriNoAccess, sContentResolver);

        InstrumentationRegistry.getInstrumentation().getContext().revokeUriPermission(
                mImageUriReadable,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertResolver_noWrite(mImageUriReadable, sContentResolver);
    }

    @Test
    public void other_createFavoriteRequest() throws Exception {
        doEscalation(MediaStore.createFavoriteRequest(sContentResolver, Arrays.asList(
                        mImageUriReadable,
                        mImageUriNoAccess),
                true));
        assertResolver_uriIsFavorite(mImageUriReadable, sContentResolver);
        // We still don't have access to uri 2 to be able to check if it is favorite
        assertResolver_noReadNoWrite(mImageUriNoAccess, sContentResolver);

        doEscalation(MediaStore.createFavoriteRequest(sContentResolver,
                Arrays.asList(mImageUriReadable, mImageUriNoAccess),
                false));
        assertResolver_uriIsNotFavorite(mImageUriReadable, sContentResolver);
        assertResolver_noReadNoWrite(mImageUriNoAccess, sContentResolver);
    }

    @Test
    public void other_deleteRequest() throws Exception {
        File fileToBeDeleted1 = new File(getDcimDir(), TAG + "_delete_1.jpg");
        File fileToBeDeleted2 = new File(getDcimDir(), TAG + "_delete_2.jpg");
        try {
            Uri uriToBeDeleted1 = sFilesRule.createEmptyFileAsOther(fileToBeDeleted1);
            Uri uriToBeDeleted2 = sFilesRule.createEmptyFileAsOther(fileToBeDeleted2);
            grantReadAccess(fileToBeDeleted1);

            doEscalation(
                    MediaStore.createDeleteRequest(sContentResolver,
                            Arrays.asList(uriToBeDeleted1, uriToBeDeleted2)));
            assertResolver_uriDoesNotExist(uriToBeDeleted1, sContentResolver);
            assertResolver_uriDoesNotExist(uriToBeDeleted2, sContentResolver);
        } finally {
            fileToBeDeleted1.delete();
            fileToBeDeleted2.delete();
        }
    }

    @Test
    public void other_accessLocationMetadata() throws Exception {
        HashMap<String, String> originalExif = getExifMetadataFromRawResource(
                RESOURCE_ID_WITH_METADATA);

        pollForPermission(Manifest.permission.ACCESS_MEDIA_LOCATION, true);
        assertExifMetadataMatch(getExifMetadataFromFile(IMAGE_FILE_READABLE), originalExif);

        // Revoke A_M_L
        revokeAccessMediaLocation();
        assertExifMetadataMismatch(getExifMetadataFromFile(IMAGE_FILE_READABLE), originalExif);
    }


    private static void grantReadAccess(File imageFile) throws IOException {
        final String pickerUri1 = buildPhotopickerUriWithStringEscaping(imageFile);
        String adbCommand = "content call "
                + " --method grant_media_read_for_package"
                + " --uri content://media/external/file"
                + " --extra uri:s:" + pickerUri1
                + " --extra " + Intent.EXTRA_PACKAGE_NAME + ":s:" + THIS_PACKAGE_NAME;
        TestUtils.executeShellCommand(adbCommand);
    }

    private static String buildPhotopickerUriWithStringEscaping(File imageFile) {
        /*
        adb shell content call  --method 'grant_media_read_for_package'
        --uri content://media/external/file
        --extra uri:s:content\\://media/picker/0/com.android.providers.media
        .photopicker/media/1000000089
        --extra android.intent.extra.PACKAGE_NAME:s:android.scopedstorage.cts.device
         */
        final Uri originalUri = MediaStore.scanFile(getContentResolver(), imageFile);
        long fileId = ContentUris.parseId(originalUri);

        // We are forced to build the URI string this way due to various layers of string escaping
        // we are hitting when using uris in adb shell commands from tests.
        return "content\\://" + MediaStore.AUTHORITY
                + Uri.EMPTY.buildUpon()
                .appendPath("picker") // PickerUriResolver.PICKER_SEGMENT
                .appendPath(String.valueOf(UserHandle.myUserId()))
                .appendPath("com.android.providers.media.photopicker")//
                .appendPath(MediaStore.AUTHORITY)
                .appendPath(Long.toString(fileId))
                .build();
    }

}
