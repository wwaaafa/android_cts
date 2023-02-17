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

package android.credentials.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import android.app.slice.Slice;
import android.credentials.CredentialDescription;
import android.credentials.CredentialOption;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.service.credentials.Action;
import android.service.credentials.CreateEntry;
import android.service.credentials.CredentialEntry;

import java.util.Random;

public class TestUtils {
    private static final Random sRandom = new Random(SystemClock.uptimeMillis());

    public static void assertEquals(CredentialOption a, CredentialOption b) {
        assertThat(a.getType()).isEqualTo(b.getType());
        assertEquals(a.getCandidateQueryData(), b.getCandidateQueryData());
        assertEquals(a.getCredentialRetrievalData(), b.getCredentialRetrievalData());
        assertThat(a.isSystemProviderRequired()).isEqualTo(b.isSystemProviderRequired());
    }

    public static void assertEquals(CredentialDescription a, CredentialDescription b) {
        assertThat(a.getType()).isEqualTo(b.getType());
        assertThat(a.getFlattenedRequestString()).isEqualTo(b.getFlattenedRequestString());
        assertThat(a.getCredentialEntries()).isSameInstanceAs(b.getCredentialEntries());
    }

    public static void assertEquals(Slice a, Slice b) {
        assertThat(a.getUri()).isEqualTo(b.getUri());
    }

    public static void assertEquals(CredentialEntry a, CredentialEntry b) {
        assertThat(a.getType()).isEqualTo(b.getType());
        assertEquals(a.getSlice(), b.getSlice());
    }

    public static void assertEquals(CreateEntry a, CreateEntry b) {
        assertEquals(a.getSlice(), b.getSlice());
    }

    public static void assertEquals(Action a, Action b) {
        assertEquals(a.getSlice(), b.getSlice());
    }

    public static void assertEquals(Bundle a, Bundle b) {
        assertThat(a.size()).isEqualTo(b.size());

        for (String key : a.keySet()) {
            assertThat(a.get(key)).isEqualTo(b.get(key));
        }

        for (String key : b.keySet()) {
            assertThat(b.get(key)).isEqualTo(a.get(key));
        }
    }

    public static <T extends Parcelable> T cloneParcelable(T obj) {
        final Parcel parcel = Parcel.obtain();
        obj.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        try {
            final Parcelable.Creator<T> creator = (Parcelable.Creator<T>) obj.getClass().getField(
                    "CREATOR").get(null);
            return creator.createFromParcel(parcel);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Parcelable CREATOR field must be public");
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Parcelable must have a static CREATOR field");
        }
    }

    public static Bundle createTestBundle() {
        final Bundle bundle = new Bundle();
        bundle.putInt("key" + sRandom.nextInt(), sRandom.nextInt());
        return bundle;
    }
}
