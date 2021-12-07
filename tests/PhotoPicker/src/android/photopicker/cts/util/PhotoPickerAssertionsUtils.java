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

package android.photopicker.cts.util;

import static android.os.SystemProperties.getBoolean;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.CloudMediaProviderContract;
import android.provider.MediaStore;

import androidx.test.InstrumentationRegistry;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Photo Picker Utility methods for test assertions.
 */
public class PhotoPickerAssertionsUtils {
    private static final String TAG = "PhotoPickerTestAssertions";

    public static void assertPickerUriFormat(Uri uri, int expectedUserId) {
        // content://media/picker/<user-id>/<media-id>
        final int userId = Integer.parseInt(uri.getPathSegments().get(1));
        assertThat(userId).isEqualTo(expectedUserId);

        final String auth = uri.getPathSegments().get(0);
        assertThat(auth).isEqualTo("picker");
    }

    public static void assertMimeType(Uri uri, String expectedMimeType) throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String resultMimeType = context.getContentResolver().getType(uri);
        assertThat(resultMimeType).isEqualTo(expectedMimeType);
    }

    public static void assertRedactedReadOnlyAccess(Uri uri) throws Exception {
        assertThat(uri).isNotNull();
        final String[] projection = new String[]{MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.MEDIA_TYPE};
        final Context context = InstrumentationRegistry.getTargetContext();
        final ContentResolver resolver = context.getContentResolver();
        try (Cursor c = resolver.query(uri, projection, null, null)) {
            assertThat(c).isNotNull();
            assertThat(c.moveToFirst()).isTrue();

            if (getBoolean("sys.photopicker.pickerdb.enabled", false)) {
                final String mimeType = c.getString(c.getColumnIndex(
                                CloudMediaProviderContract.MediaColumns.MIME_TYPE));
                if (mimeType.startsWith("image")) {
                    assertImageRedactedReadOnlyAccess(uri, resolver);
                } else if (mimeType.startsWith("video")) {
                    assertVideoRedactedReadOnlyAccess(uri, resolver);
                } else {
                    fail("The mime type is not as expected: " + mimeType);
                }
            } else {
                final int mediaType = c.getInt(1);
                switch (mediaType) {
                    case MEDIA_TYPE_IMAGE:
                        assertImageRedactedReadOnlyAccess(uri, resolver);
                        break;
                    case MEDIA_TYPE_VIDEO:
                        assertVideoRedactedReadOnlyAccess(uri, resolver);
                        break;
                    default:
                        fail("The media type is not as expected: " + mediaType);
                }
            }
        }
    }

    private static void assertVideoRedactedReadOnlyAccess(Uri uri, ContentResolver resolver)
            throws Exception {
        // The location is redacted
        try (InputStream in = resolver.openInputStream(uri);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            FileUtils.copy(in, out);
            byte[] bytes = out.toByteArray();
            byte[] xmpBytes = Arrays.copyOfRange(bytes, 3269, 3269 + 13197);
            String xmp = new String(xmpBytes);
            assertWithMessage("Failed to redact XMP longitude")
                    .that(xmp.contains("10,41.751000E")).isFalse();
            assertWithMessage("Failed to redact XMP latitude")
                    .that(xmp.contains("53,50.070500N")).isFalse();
            assertWithMessage("Redacted non-location XMP")
                    .that(xmp.contains("13166/7763")).isTrue();
        }

        // assert no write access
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w")) {
            fail("Does not grant write access to uri " + uri.toString());
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }

    private static void assertImageRedactedReadOnlyAccess(Uri uri, ContentResolver resolver)
            throws Exception {
        // The location is redacted
        try (InputStream is = resolver.openInputStream(uri)) {
            final ExifInterface exif = new ExifInterface(is);
            final float[] latLong = new float[2];
            exif.getLatLong(latLong);
            assertWithMessage("Failed to redact latitude")
                    .that(latLong[0]).isWithin(0.001f).of(0);
            assertWithMessage("Failed to redact longitude")
                    .that(latLong[1]).isWithin(0.001f).of(0);

            String xmp = exif.getAttribute(ExifInterface.TAG_XMP);
            assertWithMessage("Failed to redact XMP longitude")
                    .that(xmp.contains("10,41.751000E")).isFalse();
            assertWithMessage("Failed to redact XMP latitude")
                    .that(xmp.contains("53,50.070500N")).isFalse();
            assertWithMessage("Redacted non-location XMP")
                    .that(xmp.contains("LensDefaults")).isTrue();
        }

        // assert no write access
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w")) {
            fail("Does not grant write access to uri " + uri.toString());
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }
}
