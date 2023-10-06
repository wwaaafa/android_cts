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

package com.android.bedstead.harrier.policies;

import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_FINANCED_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_GLOBALLY;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_USERS;

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

/**
 * Policy related to setting {@code DISALLOW_ADD_USER}
 */
@EnterprisePolicy(dpc = {
        APPLIED_BY_DEVICE_OWNER | APPLIED_BY_FINANCED_DEVICE_OWNER | CANNOT_BE_APPLIED_BY_ROLE_HOLDER
                | APPLIES_GLOBALLY,
        APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO | APPLIES_TO_OWN_USER
        }, permissions = @EnterprisePolicy.Permission(
        appliedWith = MANAGE_DEVICE_POLICY_USERS, appliesTo = APPLIES_TO_OWN_USER))
public final class DisallowAddUser {
}
