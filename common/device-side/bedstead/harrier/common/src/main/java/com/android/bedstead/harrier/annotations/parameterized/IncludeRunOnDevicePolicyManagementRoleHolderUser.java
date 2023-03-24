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

package com.android.bedstead.harrier.annotations.parameterized;

import static com.android.bedstead.harrier.UserType.ANY;
import static com.android.bedstead.harrier.annotations.AnnotationRunPrecedence.EARLY;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.ENABLE_DEVICE_POLICY_ENGINE_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;

import com.android.bedstead.harrier.annotations.AnnotationRunPrecedence;
import com.android.bedstead.harrier.annotations.RequireFeatureFlagEnabled;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.nene.types.OptionalBoolean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Parameterize a test so that it runs on the same user as the device policy management role
 * holder and has no other dpcs. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedAnnotation
@RequireRunOnSystemUser(switchedToUser = OptionalBoolean.ANY)
@EnsureHasNoDpc(onUser = ANY)
@EnsureHasDevicePolicyManagerRoleHolder(isPrimary = true)
@RequireFeatureFlagEnabled(
        namespace = NAMESPACE_DEVICE_POLICY_MANAGER,
        key = ENABLE_DEVICE_POLICY_ENGINE_FLAG
)
@RequireFeatureFlagEnabled(
        namespace = NAMESPACE_DEVICE_POLICY_MANAGER,
        key = PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG
)
public @interface IncludeRunOnDevicePolicyManagementRoleHolderUser {
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
