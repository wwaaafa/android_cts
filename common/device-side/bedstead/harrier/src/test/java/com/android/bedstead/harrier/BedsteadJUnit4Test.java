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

package com.android.bedstead.harrier;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerPrimaryUser;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RunWith(BedsteadJUnit4.class)
public class BedsteadJUnit4Test {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @StringTestParameter({"A", "B"})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface TwoValuesStringTestParameter {

    }

    private static int sSimpleParameterizedCalls = 0;
    private static int sMultipleSimpleParameterizedCalls = 0;
    private static int sBedsteadParameterizedCalls = 0;
    private static int sBedsteadPlusSimpleParameterizedCalls = 0;
    private static int sIndirectParameterizedCalls = 0;
    private static int sIntParameterizedCalls = 0;

    @AfterClass
    public static void afterClass() {
        assertThat(sSimpleParameterizedCalls).isEqualTo(2);
        assertThat(sMultipleSimpleParameterizedCalls).isEqualTo(4);
        assertThat(sBedsteadParameterizedCalls).isEqualTo(2);
        assertThat(sBedsteadPlusSimpleParameterizedCalls).isEqualTo(4);
        assertThat(sIndirectParameterizedCalls).isEqualTo(2);
        assertThat(sIntParameterizedCalls).isEqualTo(2);
    }

    @Test
    @IncludeRunOnDeviceOwnerUser
    @IncludeRunOnProfileOwnerPrimaryUser
    public void bedsteadParameterized() {
        sBedsteadParameterizedCalls += 1;
    }

    @Test
    @IncludeRunOnDeviceOwnerUser
    @IncludeRunOnProfileOwnerPrimaryUser
    public void bedsteadPlusSimpleParameterized(@StringTestParameter({"A", "B"}) String argument) {
        sBedsteadPlusSimpleParameterizedCalls += 1;
    }

    @Test
    public void simpleParameterized(@StringTestParameter({"A", "B"}) String argument) {
        sSimpleParameterizedCalls += 1;
    }

    @Test
    public void multipleSimpleParameterized(
            @StringTestParameter({"A", "B"}) String argument1,
            @StringTestParameter({"C", "D"}) String argument2) {
        sMultipleSimpleParameterizedCalls += 1;
    }

    @Test
    public void indirectParameterized(@TwoValuesStringTestParameter String argument) {
        sIndirectParameterizedCalls += 1;
    }

    @Test
    public void intParameterized(@IntTestParameter({1, 2}) int argument) {
        sIntParameterizedCalls += 1;
    }
}
