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

package com.android.bedstead.harrier.annotations;

import static com.android.bedstead.harrier.OptionalBoolean.TRUE;
import static com.android.bedstead.harrier.annotations.AnnotationRunPrecedence.EARLY;

import com.android.bedstead.harrier.OptionalBoolean;
import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test method should run on an additional user.
 *
 * <p>Your test configuration should be such that this test is only run where a secondary user is
 * created, the test is being run on that user, and that user is not the initial user.
 *
 * <p>Optionally, you can guarantee that these methods do not run outside of an additional user by
 * using {@code Devicestate}.
 *
 * <p>This requires the use of {@link com.android.bedstead.harrier.BedsteadJUnit4}. This will
 * replace this annotation with the {@code RequireRunOn} annotation for the additional user.
 *
 * <p>Note that in general this requires the test runs on a secondary user. On headless system user
 * devices, this will require running on a second secondary user.
 *
 * <p>This annotation by default opts a test into multi-user presubmit. New tests should also be
 * annotated {@link Postsubmit} until they are shown to meet the multi-user presubmit
 * requirements.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@RequiresBedsteadJUnit4
public @interface RequireRunOnAdditionalUser {
    /**
     * Should we ensure that we are switched to the given user
     */
    OptionalBoolean switchedToUser() default TRUE;

    /**
     * Weight sets the order that annotations will be resolved.
     *
     * <p>Annotations with a lower weight will be resolved before annotations with a higher weight.
     *
     * <p>If there is an order requirement between annotations, ensure that the weight of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * <p>Weight can be set to a {@link AnnotationRunPrecedence} constant, or to any {@link int}.
     */
    int weight() default EARLY;
}
