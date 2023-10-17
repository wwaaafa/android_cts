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

package android.view.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;

import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@MediumTest
@RunWith(AndroidJUnit4.class)
public class AutoHandwritingTest {

    @Rule
    public ActivityTestRule<HandwritingActivity> mActivityRule =
            new ActivityTestRule<>(HandwritingActivity.class);

    @Test
    public void autoHandwriting_setToTrueInXml() {
        Activity activity = mActivityRule.getActivity();
        View view = activity.findViewById(R.id.auto_handwriting_enabled);

        assertTrue(view.isAutoHandwritingEnabled());
    }

    @Test
    public void autoHandwriting_setToFalseInXml() {
        Activity activity = mActivityRule.getActivity();
        View view = activity.findViewById(R.id.auto_handwriting_disabled);

        assertFalse(view.isAutoHandwritingEnabled());
    }

    @Test
    public void autoHandwriting_setToFalse() {
        Activity activity = mActivityRule.getActivity();
        View view = activity.findViewById(R.id.auto_handwriting_enabled);
        assertTrue(view.isAutoHandwritingEnabled());

        view.setAutoHandwritingEnabled(false);
        assertFalse(view.isAutoHandwritingEnabled());
    }

    @Test
    public void autoHandwriting_setToTrue() {
        Activity activity = mActivityRule.getActivity();
        View view = activity.findViewById(R.id.auto_handwriting_disabled);
        assertFalse(view.isAutoHandwritingEnabled());

        view.setAutoHandwritingEnabled(true);
        assertTrue(view.isAutoHandwritingEnabled());
    }

    @Test
    public void autoHandwriting_textView_defaultValueIsTrue() {
        Activity activity = mActivityRule.getActivity();
        View view = activity.findViewById(R.id.default_textview);

        assertTrue(view.isAutoHandwritingEnabled());
    }
}
