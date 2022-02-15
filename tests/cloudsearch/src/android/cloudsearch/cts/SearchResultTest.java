/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.cloudsearch.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.cloudsearch.SearchResult;
import android.os.Bundle;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SearchResult}
 *
 * atest CtsCloudSearchServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SearchResultTest {

    private static final String TAG = "CloudSearchTargetTest";

    @Test
    public void testCreateSearchResult() {
        final String title = "title";
        final String snippet = "Good Snippet";
        final float score = 10;

        Bundle extraInfos = new Bundle();
        extraInfos.putBoolean(SearchResult.EXTRAINFO_APP_CONTAINS_IAP_DISCLAIMER,
                false);
        extraInfos.putString(SearchResult.EXTRAINFO_APP_DEVELOPER_NAME,
                "best_app_developer");

        SearchResult result = new SearchResult.Builder(title, extraInfos)
                .setSnippet(snippet).setTitle(title).setExtraInfos(extraInfos)
                .setScore(score).build();

        /** Checks the original result. */
        assertThat(result.getTitle()).isEqualTo(title);
        assertThat(result.getSnippet()).isEqualTo(snippet);
        assertThat(result.getScore()).isEqualTo(score);
        final Bundle rExtraInfos = result.getExtraInfos();
        assertThat(rExtraInfos
                .getBoolean(SearchResult.EXTRAINFO_APP_CONTAINS_IAP_DISCLAIMER))
                .isEqualTo(false);
        assertThat(rExtraInfos
                .getString(SearchResult.EXTRAINFO_APP_DEVELOPER_NAME))
                .isEqualTo("best_app_developer");

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SearchResult copy = SearchResult.CREATOR.createFromParcel(parcel);
        /** Checks the copied result. */
        assertThat(copy.getTitle()).isEqualTo(title);
        assertThat(copy.getSnippet()).isEqualTo(snippet);
        assertThat(copy.getScore()).isEqualTo(score);
        final Bundle rExtraInfosCopy = copy.getExtraInfos();
        assertThat(rExtraInfosCopy
                .getBoolean(SearchResult.EXTRAINFO_APP_CONTAINS_IAP_DISCLAIMER))
                .isEqualTo(false);
        assertThat(rExtraInfosCopy
                .getString(SearchResult.EXTRAINFO_APP_DEVELOPER_NAME))
                .isEqualTo("best_app_developer");

        parcel.recycle();
    }
}
