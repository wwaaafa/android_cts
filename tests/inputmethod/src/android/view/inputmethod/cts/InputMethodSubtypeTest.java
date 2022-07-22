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

package android.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class InputMethodSubtypeTest extends EndToEndImeTestBase {

    private static final InputMethodSubtype IMPLICITLY_ENABLED_TEST_SUBTYPE =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x01234567)
                    .setOverridesImplicitlyEnabledSubtype(true)
                    .build();

    @NonNull
    private final InputMethodManager mImm = Objects.requireNonNull(
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(
                    InputMethodManager.class));

    /**
     * Verifies that {@link InputMethodManager#getCurrentInputMethodSubtype()} returns {@code null}
     * if the current IME does not have any {@link InputMethodSubtype}.
     */
    @Test
    public void testGetCurrentInputMethodSubtypeNull() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(), new ImeSettings.Builder())) {

            assertThat(mImm.getCurrentInputMethodSubtype()).isNull();
        }
    }

    /**
     * Verifies that {@link InputMethodManager#getCurrentInputMethodSubtype()} returns an expected
     * {@link InputMethodSubtype}.
     */
    @Test
    public void testGetCurrentInputMethodSubtype() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder().setAdditionalSubtypes(IMPLICITLY_ENABLED_TEST_SUBTYPE))) {

            assertThat(mImm.getCurrentInputMethodSubtype())
                    .isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE);
        }
    }
}
