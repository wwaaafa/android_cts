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

package com.android.bedstead.harrier.policies;

import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_FINANCED_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PARENT_INSTANCE_OF_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_PARENT;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.INHERITABLE;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_KEYGUARD;

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

/**
 * Policy used for {@code DevicePolicyManager#setKeyguardDisabledFeatures} with
 * {@code DevicePolicyManager#KEYGUARD_DISABLE_TRUST_AGENTS}
 */
@EnterprisePolicy(dpc = { // APPLIED_BY_DPM_ROLE_HOLDER
        APPLIED_BY_DEVICE_OWNER | APPLIED_BY_FINANCED_DEVICE_OWNER | APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO | APPLIED_BY_PARENT_INSTANCE_OF_PROFILE_OWNER_PROFILE | CANNOT_BE_APPLIED_BY_ROLE_HOLDER | APPLIES_TO_OWN_USER,
        APPLIED_BY_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT | CANNOT_BE_APPLIED_BY_ROLE_HOLDER | INHERITABLE // only if there is no separate work challenge
}, permissions = {
        @EnterprisePolicy.Permission(appliedWith = MANAGE_DEVICE_POLICY_KEYGUARD, appliesTo = APPLIES_TO_OWN_USER)
})
// TODO: Add USES_POLICY_DISABLE_KEYGUARD_FEATURES device admin
public final class KeyguardDisableTrustAgents {
}
