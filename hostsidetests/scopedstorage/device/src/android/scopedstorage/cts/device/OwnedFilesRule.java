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

import static android.scopedstorage.cts.device.FileCreationUtils.createContentFromResource;
import static android.scopedstorage.cts.lib.TestUtils.getDcimDir;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.MediaStore;

import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;

public class OwnedFilesRule extends ExternalResource {

    private static final String TAG = "OwnedFilesRule";
    public static final int RESOURCE_ID = R.raw.scenery;
    public static final int RESOURCE_ID_WITH_METADATA = R.raw.img_with_metadata;
    private static final String NONCE = String.valueOf(System.nanoTime());
    private static final String IMAGE_1 = TAG + "_image1_" + NONCE + ".jpg";
    private static final String IMAGE_2 = TAG + "_image2_" + NONCE + ".jpg";
    private static final String VIDEO_1 = TAG + "_video1_" + NONCE + ".mp4";

    private final ContentResolver mContentResolver;

    private final File mImageFile1 = new File(getDcimDir(), IMAGE_1);
    private final File mImageFile2Metadata = new File(getDcimDir(), IMAGE_2);

    private final File mVideoFile1 = new File(getDcimDir(), VIDEO_1);
    private Uri mImageUri1;
    private Uri mImageUri2;
    private Uri mVideoUri1;

    public OwnedFilesRule(ContentResolver contentResolver) {
        this.mContentResolver = contentResolver;
    }

    @Override
    protected void before() throws IOException {
        mImageUri1 = createFile(RESOURCE_ID, mImageFile1);
        mImageUri2 = createFile(RESOURCE_ID_WITH_METADATA, mImageFile2Metadata);
        mVideoUri1 = createFile(mVideoFile1);

    }

    public Uri createFile(int resourceId, File imageFile) throws IOException {
        assertThat(imageFile.createNewFile()).isTrue();
        Uri uri = MediaStore.scanFile(mContentResolver, imageFile);
        createContentFromResource(resourceId, imageFile);
        assertThat(uri).isNotNull();
        return uri;
    }

    public Uri createFile(File file) throws IOException {
        assertThat(file.createNewFile()).isTrue();
        Uri uri = MediaStore.scanFile(mContentResolver, file);
        assertThat(uri).isNotNull();
        return uri;
    }

    @Override
    protected void after() {
        mImageFile1.delete();
        mImageFile2Metadata.delete();
        mVideoFile1.delete();
    }

    public File getImageFile1() {
        return mImageFile1;
    }

    public File getImageFile2Metadata() {
        return mImageFile2Metadata;
    }

    public File getVideoFile1() {
        return mVideoFile1;
    }


    public Uri getImageUri1() {
        return mImageUri1;
    }

    public Uri getImageUri2_Metadata() {
        return mImageUri2;
    }

    public Uri getVideoUri1() {
        return mVideoUri1;
    }
}
