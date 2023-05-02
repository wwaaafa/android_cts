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

package android.devicepolicy.cts;

import static android.Manifest.permission.READ_CALENDAR;
import static android.app.admin.DevicePolicyIdentifiers.ACCOUNT_MANAGEMENT_DISABLED_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.APPLICATION_RESTRICTIONS_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.AUTO_TIMEZONE_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.KEYGUARD_DISABLED_FEATURES_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.LOCK_TASK_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PACKAGE_UNINSTALL_BLOCKED_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PERMISSION_GRANT_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PERMITTED_INPUT_METHODS_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PERSISTENT_PREFERRED_ACTIVITY_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.RESET_PASSWORD_TOKEN_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.USER_CONTROL_DISABLED_PACKAGES_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.getIdentifierForUserRestriction;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.app.admin.FlagUnion.FLAG_UNION;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;
import static android.app.admin.TargetUser.LOCAL_USER_ID;
import static android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;
import static android.os.UserManager.DISALLOW_WIFI_DIRECT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.Manifest;
import android.app.ActivityManager;
import android.app.admin.AccountTypePolicyKey;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyState;
import android.app.admin.DpcAuthority;
import android.app.admin.EnforcingAdmin;
import android.app.admin.FlagUnion;
import android.app.admin.IntentFilterPolicyKey;
import android.app.admin.LockTaskPolicy;
import android.app.admin.MostRecent;
import android.app.admin.MostRestrictive;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PackagePermissionPolicyKey;
import android.app.admin.PackagePolicyKey;
import android.app.admin.PolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateReceiver;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.RoleAuthority;
import android.app.admin.StringSetUnion;
import android.app.admin.TopPriority;
import android.app.admin.UserRestrictionPolicyKey;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.devicepolicy.cts.utils.BundleUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.EnsureHasAccountAuthenticator;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CoexistenceFlagsOn;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.inputmethods.InputMethod;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
@CoexistenceFlagsOn
public final class DeviceManagementCoexistenceTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String PACKAGE_NAME = "com.android.package.test";

    private static final String GRANTABLE_PERMISSION = READ_CALENDAR;

    private static final byte[] TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();

    private static String FINANCED_DEVICE_CONTROLLER_ROLE =
            "android.app.role.SYSTEM_FINANCED_DEVICE_CONTROLLER";

    private static final List<Boolean> TRUE_MORE_RESTRICTIVE = List.of(true, false);

    private static final String LOCAL_USER_RESTRICTION = DISALLOW_MODIFY_ACCOUNTS;

    private static final String GLOBAL_USER_RESTRICTION = DISALLOW_WIFI_DIRECT;

    private static final int LOCK_TASK_FEATURES = LOCK_TASK_FEATURE_HOME;

    private static final int KEYGUARD_DISABLED_FEATURE = KEYGUARD_DISABLE_SECURE_CAMERA;

    private static final List<String> NON_SYSTEM_INPUT_METHOD_PACKAGES =
            TestApis.inputMethods().installedInputMethods().stream()
                    .map(InputMethod::pkg)
                    .filter(p -> !p.hasSystemFlag())
                    .map(Package::packageName)
                    .collect(Collectors.toList());
    static {
        NON_SYSTEM_INPUT_METHOD_PACKAGES.add("packageName");
    }

    private static final TestApp sTestApp = sDeviceState.testApps().any();

    private static TestAppInstance sTestAppInstance = sTestApp.install();

    @AfterClass
    public static void teardownClass() {
        sTestAppInstance.uninstall();
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_autoTimezoneSet_returnsPolicy() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/278710449")
    public void getDevicePolicyState_permissionGrantStateSet_returnsPolicy() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_lockTaskPolicySet_returnsPolicy() {
        String[] originalLockTaskPackages = sDeviceState.dpc().devicePolicyManager()
                .getLockTaskPackages(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), new String[]{PACKAGE_NAME});
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(sDeviceState.dpc().componentName(), LOCK_TASK_FEATURES);

            PolicyState<LockTaskPolicy> policyState = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy().getPackages())
                    .containsExactly(PACKAGE_NAME);
            assertThat(policyState.getCurrentResolvedPolicy().getFlags())
                    .isEqualTo(LOCK_TASK_FEATURES);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), originalLockTaskPackages);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_userControlDisabledPackagesSet_returnsPolicy() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    Arrays.asList(sTestApp.packageName()));

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).containsExactly(
                    sTestApp.packageName());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_uninstallBlockedSet_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_persistentPreferredActivitySet_returnsPolicy() {
        try {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            sDeviceState.dpc().devicePolicyManager().addPersistentPreferredActivity(
                    sDeviceState.dpc().componentName(),
                    intentFilter,
                    sDeviceState.dpc().componentName());

            PolicyState<ComponentName> policyState = getComponentNamePolicyState(
                    new IntentFilterPolicyKey(
                            PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                            intentFilter),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    sDeviceState.dpc().componentName());
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearPackagePersistentPreferredActivities(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.dpc().packageName());
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_appRestrictionsSet_returnsPolicy() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "getDevicePolicyState_appRestrictionsSet_returnsPolicy");
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            PolicyState<Bundle> policyState = getBundlePolicyState(
                    new PackagePolicyKey(
                            APPLICATION_RESTRICTIONS_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());

            // app restrictions is a non-coexistable policy, so should not have a resolved policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Bundle returnedBundle = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(sDeviceState.dpc().packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            sDeviceState.dpc().user().userHandle()));
            assertThat(returnedBundle).isNotNull();
            BundleUtils.assertEqualToBundle(
                    "getDevicePolicyState_appRestrictionsSet_returnsPolicy",
                    returnedBundle);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Ignore("If ResetPasswordWithTokenTest for managed profile is executed before device owner "
            + "and primary user profile owner tests, password reset token would have been disabled "
            + "for the primary user, disabling this test until this gets fixed.")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_resetPasswordTokenSet_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setResetPasswordToken(
                    sDeviceState.dpc().componentName(), TOKEN);

            PolicyState<Long> policyState = getLongPolicyState(
                    new NoArgsPolicyKey(RESET_PASSWORD_TOKEN_POLICY),
                    sDeviceState.dpc().user().userHandle());

            // reset password token is a non-coexistable policy, so should not have a resolved
            // policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Long token = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(sDeviceState.dpc().packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            sDeviceState.dpc().user().userHandle()));
            assertThat(token).isNotNull();
            assertThat(token).isNotEqualTo(0);
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(sDeviceState.dpc().componentName());
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_addUserRestriction_returnsPolicy() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("addUserRestrictionGlobally is no longer callable from DPCs, should change it to a "
            + "permission based test.")
    public void getDevicePolicyState_addUserRestrictionGlobally_returnsPolicy() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(GLOBAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    GLOBAL_USER_RESTRICTION);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                            GLOBAL_USER_RESTRICTION),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), GLOBAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_setKeyguardDisabledFeatures_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLED_FEATURE);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new NoArgsPolicyKey(
                            KEYGUARD_DISABLED_FEATURES_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(KEYGUARD_DISABLED_FEATURE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @EnsureHasAccountAuthenticator
    public void getDevicePolicyState_setAccountManagementDisabled_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_setPermittedInputMethods_returnsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), NON_SYSTEM_INPUT_METHOD_PACKAGES);

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    new HashSet<>(NON_SYSTEM_INPUT_METHOD_PACKAGES));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packages= */ null);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_autoTimezone_returnsCorrectResolutionMechanism() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);

            assertThat(getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);

        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/278710449")
    public void getDevicePolicyState_permissionGrantState_returnsCorrectResolutionMechanism() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getMostRestrictiveIntegerMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(
                    List.of(PERMISSION_GRANT_STATE_DENIED,
                            PERMISSION_GRANT_STATE_GRANTED,
                            PERMISSION_GRANT_STATE_DEFAULT));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_lockTaskPolicy_returnsCorrectResolutionMechanism() {
        String[] originalLockTaskPackages = sDeviceState.dpc().devicePolicyManager()
                .getLockTaskPackages(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), new String[]{PACKAGE_NAME});

            PolicyState<LockTaskPolicy> policyState = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getTopPriorityMechanism(policyState)
                    .getHighestToLowestPriorityAuthorities()).isEqualTo(
                    List.of(
                            new RoleAuthority(Set.of(FINANCED_DEVICE_CONTROLLER_ROLE)),
                            DpcAuthority.DPC_AUTHORITY));
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), originalLockTaskPackages);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_userControlDisabledPackages_returnsCorrectResolutionMechanism() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    Arrays.asList(sTestApp.packageName()));

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getResolutionMechanism()).isEqualTo(
                    StringSetUnion.STRING_SET_UNION);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_uninstallBlocked_returnsCorrectResolutionMechanism() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false
            );
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_persistentPreferredActivity_returnsCorrectResolutionMechanism() {
        try {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            sDeviceState.dpc().devicePolicyManager().addPersistentPreferredActivity(
                    sDeviceState.dpc().componentName(),
                    intentFilter,
                    sDeviceState.dpc().componentName());

            PolicyState<ComponentName> policyState = getComponentNamePolicyState(
                    new IntentFilterPolicyKey(
                            PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                            intentFilter),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getTopPriorityMechanism(policyState)
                    .getHighestToLowestPriorityAuthorities()).isEqualTo(
                    List.of(
                            new RoleAuthority(Set.of(FINANCED_DEVICE_CONTROLLER_ROLE)),
                            DpcAuthority.DPC_AUTHORITY));
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearPackagePersistentPreferredActivities(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.dpc().packageName());
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_addUserRestriction_returnsCorrectResolutionMechanism() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_setKeyguardDisabledFeatures_returnsCorrectResolutionMechanism() {
        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLED_FEATURE);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new NoArgsPolicyKey(
                            KEYGUARD_DISABLED_FEATURES_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getFlagUnionMechanism(policyState)).isEqualTo(FLAG_UNION);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @EnsureHasAccountAuthenticator
    public void getDevicePolicyState_setAccountManagementDisabled_returnsCorrectResolutionMechanism() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues())
                    .isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_setPermittedInputMethods_returnsCorrectResolutionMechanism() {
        try {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), NON_SYSTEM_INPUT_METHOD_PACKAGES);

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(getMostRecentStringSetMechanism(policyState))
                    .isEqualTo(MostRecent.MOST_RECENT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packages= */ null);
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_autoTimezoneSet_receivedPolicySetBroadcast() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState, AUTO_TIMEZONE_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET,
                    GLOBAL_USER_ID, new Bundle());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/278710449")
    public void policyUpdateReceiver_permissionGrantStateSet_receivedPolicySetBroadcast() {
        Bundle bundle = new Bundle();
        bundle.putString(PolicyUpdateReceiver.EXTRA_PACKAGE_NAME, sTestApp.packageName());
        bundle.putString(PolicyUpdateReceiver.EXTRA_PERMISSION_NAME, GRANTABLE_PERMISSION);
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    PERMISSION_GRANT_POLICY, PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID,
                    bundle);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_lockTaskPolicySet_receivedPolicySetBroadcast() {
        String[] originalLockTaskPackages = sDeviceState.dpc().devicePolicyManager()
                .getLockTaskPackages(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), new String[]{PACKAGE_NAME});

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    LOCK_TASK_POLICY, PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID,
                    new Bundle());
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), originalLockTaskPackages);
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_userControlDisabledPackagesSet_receivedPolicySetBroadcast() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    Arrays.asList(sTestApp.packageName()));

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    USER_CONTROL_DISABLED_PACKAGES_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());

        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    originalDisabledPackages);
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_uninstallBlockedSet_receivedPolicySetBroadcast() {
        Bundle bundle = new Bundle();
        bundle.putString(PolicyUpdateReceiver.EXTRA_PACKAGE_NAME, sTestApp.packageName());
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    PACKAGE_UNINSTALL_BLOCKED_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, bundle);

        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("figure out why it's failing")
    public void policyUpdateReceiver_persistentPreferredActivitySet_receivedPolicySetBroadcast() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        Bundle bundle = new Bundle();
        bundle.putParcelable(PolicyUpdateReceiver.EXTRA_INTENT_FILTER, intentFilter);
        try {
            sDeviceState.dpc().devicePolicyManager().addPersistentPreferredActivity(
                    sDeviceState.dpc().componentName(),
                    intentFilter,
                    sDeviceState.dpc().componentName());

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, bundle);
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearPackagePersistentPreferredActivities(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.dpc().packageName());
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_addUserRestriction_receivedPolicySetBroadcast() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("addUserRestrictionGlobally is no longer callable from DPCs, should change it to a "
            + "permission based test.")
    public void policyUpdateReceiver_addUserRestrictionGlobally_receivedPolicySetBroadcast() {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(GLOBAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    GLOBAL_USER_RESTRICTION);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), GLOBAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void devicePolicyState_getPoliciesForAllUsers_returnsPolicies() {
        boolean originalAutoTimeZoneValue = sDeviceState.dpc().devicePolicyManager()
                .getAutoTimeZoneEnabled(sDeviceState.dpc().componentName());
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);

            try {
                DevicePolicyState state =
                        sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
                Map<UserHandle, Map<PolicyKey, PolicyState<?>>> policies =
                        state.getPoliciesForAllUsers();

                PolicyState<Boolean> autoTimezonePolicy =
                        (PolicyState<Boolean>) (policies.get(UserHandle.ALL)
                                .get(new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY)));
                PolicyState<Boolean> userRestrictionPolicy =
                        (PolicyState<Boolean>) (policies.get(sDeviceState.dpc().user().userHandle())
                                .get(new UserRestrictionPolicyKey(
                                        getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                                        LOCAL_USER_RESTRICTION)));
                assertThat(autoTimezonePolicy.getCurrentResolvedPolicy()).isTrue();
                assertThat(userRestrictionPolicy.getCurrentResolvedPolicy()).isTrue();
            } catch (ClassCastException e) {
                fail("Returned policy is not of type Boolean: " + e);
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), originalAutoTimeZoneValue);
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), GLOBAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void policyUpdateReceiver_setKeyguardDisabledFeatures_receivedPolicySetBroadcast() {
        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLED_FEATURE);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    KEYGUARD_DISABLED_FEATURES_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @EnsureHasAccountAuthenticator
    public void policyUpdateReceiver_setAccountManagementDisabled_receivedPolicySetBroadcast() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void autoTimezoneSet_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void permissionGrantStateSet_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void lockTaskPolicySet_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(
                            sDeviceState.dpc().componentName(), new String[]{PACKAGE_NAME});
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(sDeviceState.dpc().componentName(), LOCK_TASK_FEATURES);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<LockTaskPolicy> policyState = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy().getPackages())
                    .containsExactly(PACKAGE_NAME);
            assertThat(policyState.getCurrentResolvedPolicy().getFlags())
                    .isEqualTo(LOCK_TASK_FEATURES);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), new String[]{});
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void userControlDisabledPackagesSet_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    Arrays.asList(sTestApp.packageName()));

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).containsExactly(
                    sTestApp.packageName());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    new ArrayList<>());
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void uninstallBlockedSet_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void persistentPreferredActivitySet_serialisation_loadsPolicy() {
        try {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            sDeviceState.dpc().devicePolicyManager().addPersistentPreferredActivity(
                    sDeviceState.dpc().componentName(),
                    intentFilter,
                    sDeviceState.dpc().componentName());

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<ComponentName> policyState = getComponentNamePolicyState(
                    new IntentFilterPolicyKey(
                            PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                            intentFilter),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    sDeviceState.dpc().componentName());
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearPackagePersistentPreferredActivities(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.dpc().packageName());
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void appRestrictionsSet_serialisation_loadsPolicy() {
        Bundle bundle = BundleUtils.createBundle(
                "appRestrictionsSet_serialisation_loadsPolicy");
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Bundle> policyState = getBundlePolicyState(
                    new PackagePolicyKey(
                            APPLICATION_RESTRICTIONS_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());

            // app restrictions is a non-coexistable policy, so should not have a resolved policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Bundle returnedBundle = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(sDeviceState.dpc().packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            sDeviceState.dpc().user().userHandle()));
            assertThat(returnedBundle).isNotNull();
            BundleUtils.assertEqualToBundle(
                    "appRestrictionsSet_serialisation_loadsPolicy",
                    returnedBundle);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), new Bundle());
        }
    }

    @Ignore("If ResetPasswordWithTokenTest for managed profile is executed before device owner " +
            "and primary user profile owner tests, password reset token would have been " +
            "disabled for the primary user, disabling this test until this gets fixed.")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void resetPasswordTokenSet_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setResetPasswordToken(
                    sDeviceState.dpc().componentName(), TOKEN);

            PolicyState<Long> policyState = getLongPolicyState(
                    new NoArgsPolicyKey(RESET_PASSWORD_TOKEN_POLICY),
                    sDeviceState.dpc().user().userHandle());

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            // reset password token is a non-coexistable policy, so should not have a resolved
            // policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Long token = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(sDeviceState.dpc().packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            sDeviceState.dpc().user().userHandle()));
            assertThat(token).isNotNull();
            assertThat(token).isNotEqualTo(0);
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(
                    sDeviceState.dpc().componentName());
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void addUserRestriction_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk, also DPCs are no longer "
            + "allowed to call addUserRestrictionGlobally")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void addUserRestrictionGlobally_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    GLOBAL_USER_RESTRICTION);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                            GLOBAL_USER_RESTRICTION),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), GLOBAL_USER_RESTRICTION);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void setKeyguardDisabledFeatures_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLED_FEATURE);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new NoArgsPolicyKey(
                            KEYGUARD_DISABLED_FEATURES_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(KEYGUARD_DISABLED_FEATURE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @EnsureHasAccountAuthenticator
    public void setAccountManagementDisabled_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(true);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    @Ignore
    public void setPermittedInputMethods_serialisation_loadsPolicy() {
        try {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), NON_SYSTEM_INPUT_METHOD_PACKAGES);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    sDeviceState.dpc().user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    new HashSet<>(NON_SYSTEM_INPUT_METHOD_PACKAGES));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packages= */ null);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner
    @Postsubmit(reason = "new test")
    @EnsureHasAccountAuthenticator
    public void multiplePoliciesSet_serialisation_loadsPolicies() {
        try {
            // Policy Setting
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), true);
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(
                            sDeviceState.dpc().componentName(), new String[]{PACKAGE_NAME});
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(sDeviceState.dpc().componentName(), LOCK_TASK_FEATURES);
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    Arrays.asList(sTestApp.packageName()));
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);
            sDeviceState.dpc().devicePolicyManager().setResetPasswordToken(
                    sDeviceState.dpc().componentName(), TOKEN);
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
            // DPCs can no longer call addUserRestrictionGlobally
//            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
//                    GLOBAL_USER_RESTRICTION);
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLED_FEATURE);
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            sDeviceState.dpc().devicePolicyManager().addPersistentPreferredActivity(
                    sDeviceState.dpc().componentName(),
                    intentFilter,
                    sDeviceState.dpc().componentName());
            Bundle bundle = BundleUtils.createBundle(
                    "appRestrictionsSet_serialisation_loadsPolicy");
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ true);

            // Reloading policies from disk
            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            // Getting policy states
            PolicyState<Boolean> autoTimezonePolicy = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);
            PolicyState<Integer> permissionGrantStatePolicy = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    sDeviceState.dpc().user().userHandle());
            PolicyState<LockTaskPolicy> lockTaskPolicy = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    sDeviceState.dpc().user().userHandle());
            PolicyState<Set<String>> userControlDisabledPackagesPolicy = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);
            PolicyState<Boolean> packageUninstallBlockedPolicy = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());
            PolicyState<Long> resetPasswordTokenPolicy = getLongPolicyState(
                    new NoArgsPolicyKey(RESET_PASSWORD_TOKEN_POLICY),
                    sDeviceState.dpc().user().userHandle());
            PolicyState<Boolean> userRestrictionPolicy = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    sDeviceState.dpc().user().userHandle());
            PolicyState<Boolean> globalUserRestrictionPolicy = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                            GLOBAL_USER_RESTRICTION),
                    UserHandle.ALL);
            PolicyState<Integer> keyguardDisabledPolicy = getIntegerPolicyState(
                    new NoArgsPolicyKey(
                            KEYGUARD_DISABLED_FEATURES_POLICY),
                    sDeviceState.dpc().user().userHandle());
            PolicyState<ComponentName> persistentPreferredActivityPolicy =
                    getComponentNamePolicyState(
                            new IntentFilterPolicyKey(
                                    PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                    intentFilter),
                            sDeviceState.dpc().user().userHandle());
            PolicyState<Bundle> applicationRestrictionsPolicy = getBundlePolicyState(
                    new PackagePolicyKey(
                            APPLICATION_RESTRICTIONS_POLICY,
                            sTestApp.packageName()),
                    sDeviceState.dpc().user().userHandle());
            PolicyState<Boolean> accountManagementDisabledPolicy = getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            sDeviceState.accounts().accountType()),
                    sDeviceState.dpc().user().userHandle());
            // Asserting policies loaded correctly
            assertThat(autoTimezonePolicy.getCurrentResolvedPolicy()).isTrue();
            // TODO(b/278710449): uncomment once bug is fixed
//            assertThat(permissionGrantStatePolicy.getCurrentResolvedPolicy()).isEqualTo(
//                    PERMISSION_GRANT_STATE_GRANTED);
            assertThat(lockTaskPolicy.getCurrentResolvedPolicy().getPackages())
                    .containsExactly(PACKAGE_NAME);
            assertThat(lockTaskPolicy.getCurrentResolvedPolicy().getFlags())
                    .isEqualTo(LOCK_TASK_FEATURES);
            assertThat(userControlDisabledPackagesPolicy.getCurrentResolvedPolicy())
                    .containsExactly(sTestApp.packageName());
            assertThat(packageUninstallBlockedPolicy.getCurrentResolvedPolicy()).isTrue();
            // reset password token is a non-coexistable policy, so should not have a resolved
            // policy.
            assertThat(resetPasswordTokenPolicy.getCurrentResolvedPolicy()).isNull();
            Long token = resetPasswordTokenPolicy.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(sDeviceState.dpc().packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            sDeviceState.dpc().user().userHandle()));
            assertThat(token).isNotNull();
            assertThat(token).isNotEqualTo(0);
            assertThat(userRestrictionPolicy.getCurrentResolvedPolicy()).isTrue();
//            assertThat(globalUserRestrictionPolicy.getCurrentResolvedPolicy()).isTrue();
            assertThat(keyguardDisabledPolicy.getCurrentResolvedPolicy()).isEqualTo(
                    KEYGUARD_DISABLED_FEATURE);
            assertThat(persistentPreferredActivityPolicy.getCurrentResolvedPolicy()).isEqualTo(
                    sDeviceState.dpc().componentName());
            // app restrictions is a non-coexistable policy, so should not have a resolved policy.
            assertThat(applicationRestrictionsPolicy.getCurrentResolvedPolicy()).isNull();
            Bundle returnedBundle = applicationRestrictionsPolicy.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(sDeviceState.dpc().packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            sDeviceState.dpc().user().userHandle()));
            assertThat(returnedBundle).isNotNull();
            BundleUtils.assertEqualToBundle(
                    "appRestrictionsSet_serialisation_loadsPolicy",
                    returnedBundle);
            assertThat(accountManagementDisabledPolicy.getCurrentResolvedPolicy()).isTrue();

        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeZoneEnabled(
                    sDeviceState.dpc().componentName(), false);
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(sDeviceState.dpc().componentName(), new String[]{});
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    sDeviceState.dpc().componentName(),
                    new ArrayList<>());
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
            sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(
                    sDeviceState.dpc().componentName());
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), GLOBAL_USER_RESTRICTION);
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
            sDeviceState.dpc().devicePolicyManager().clearPackagePersistentPreferredActivities(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.dpc().packageName());
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), new Bundle());
            sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                    sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasPermission(value = Manifest.permission.FORCE_STOP_PACKAGES)
    @EnsureHasAccountAuthenticator
    public void multiplePoliciesSet_dpcRemoved_removesPolicies() throws Exception {
        // Set policies
        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(
                        sDeviceState.dpc().componentName(), new String[]{PACKAGE_NAME});
        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskFeatures(sDeviceState.dpc().componentName(), LOCK_TASK_FEATURES);
        sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                sDeviceState.dpc().componentName(),
                Arrays.asList(sTestApp.packageName()));
        sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                sDeviceState.dpc().componentName(),
                sTestApp.packageName(), /* uninstallBlocked= */ true);
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), LOCAL_USER_RESTRICTION);
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        sDeviceState.dpc().devicePolicyManager().addPersistentPreferredActivity(
                sDeviceState.dpc().componentName(),
                intentFilter,
                sDeviceState.dpc().componentName());
        sDeviceState.dpc().devicePolicyManager().setAccountManagementDisabled(
                sDeviceState.dpc().componentName(), sDeviceState.accounts().accountType(),
                /* disabled= */ true);


       // Remove DPC
        sDeviceState.dpc().devicePolicyManager().clearDeviceOwnerApp(
                sDeviceState.dpc().packageName());

        // Get policies from policy engine
        PolicyState<LockTaskPolicy> lockTaskPolicy = getLockTaskPolicyState(
                new NoArgsPolicyKey(LOCK_TASK_POLICY),
                sDeviceState.dpc().user().userHandle());
        PolicyState<Set<String>> userControlDisabledPackagesPolicy = getStringSetPolicyState(
                new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                UserHandle.ALL);
        PolicyState<Boolean> packageUninstallBlockedPolicy = getBooleanPolicyState(
                new PackagePolicyKey(
                        PACKAGE_UNINSTALL_BLOCKED_POLICY,
                        sTestApp.packageName()),
                sDeviceState.dpc().user().userHandle());
        PolicyState<Boolean> userRestrictionPolicy = getBooleanPolicyState(
                new UserRestrictionPolicyKey(
                        getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                        LOCAL_USER_RESTRICTION),
                sDeviceState.dpc().user().userHandle());
        PolicyState<ComponentName> persistentPreferredActivityPolicy =
                getComponentNamePolicyState(
                        new IntentFilterPolicyKey(
                                PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                intentFilter),
                        sDeviceState.dpc().user().userHandle());
        PolicyState<Boolean> accountManagementDisabledPolicy = getBooleanPolicyState(
                new AccountTypePolicyKey(
                        ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                        sDeviceState.accounts().accountType()),
                sDeviceState.dpc().user().userHandle());
        // Assert policies removed from policy engine
        assertThat(lockTaskPolicy).isNull();
        assertThat(userControlDisabledPackagesPolicy).isNull();
        assertThat(packageUninstallBlockedPolicy).isNull();
        assertThat(userRestrictionPolicy).isNull();
        assertThat(persistentPreferredActivityPolicy).isNull();
        assertThat(accountManagementDisabledPolicy).isNull();
        // Assert policies not enforced
        assertThat(TestApis.context().instrumentedContext()
                .getSystemService(DevicePolicyManager.class)
                .isLockTaskPermitted(PACKAGE_NAME)).isFalse();
        sTestAppInstance.activities().any().start();
        int processIdBeforeStopping = sTestAppInstance.process().pid();
        TestApis.context().instrumentedContext().getSystemService(ActivityManager.class)
                .forceStopPackage(sTestAppInstance.packageName());
        assertPackageStopped(
                sTestApp.pkg(), processIdBeforeStopping);
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(LOCAL_USER_RESTRICTION))
                .isFalse();
    }

    private void assertPackageStopped(Package pkg, int processIdBeforeStopping) throws Exception {
        Poll.forValue("Package " + pkg + " stopped",
                        () -> isProcessRunning(pkg, processIdBeforeStopping))
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    private boolean isProcessRunning(Package pkg, int processIdBeforeStopping)
            throws Exception {
        return pkg.runningProcesses().stream().anyMatch(p -> p.pid() == processIdBeforeStopping);
    }

    private PolicyState<Long> getLongPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Long>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Long: " + e);
            return null;
        }
    }

    private PolicyState<Bundle> getBundlePolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Bundle>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Bundle: " + e);
            return null;
        }
    }

    private PolicyState<Boolean> getBooleanPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Boolean>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Boolean: " + e);
            return null;
        }
    }

    static PolicyState<Set<String>> getStringSetPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Set<String>>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Set<String>: " + e);
            return null;
        }
    }

    private PolicyState<LockTaskPolicy> getLockTaskPolicyState(
            PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<LockTaskPolicy>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type LockTaskPolicy: " + e);
            return null;
        }
    }

    private PolicyState<Integer> getIntegerPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Integer>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Integer: " + e);
            return null;
        }
    }

    private PolicyState<ComponentName> getComponentNamePolicyState(
            PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    sDeviceState.dpmRoleHolder().devicePolicyManager().getDevicePolicyState();
            return (PolicyState<ComponentName>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type ComponentName: " + e);
            return null;
        }
    }

    private MostRestrictive<Boolean> getMostRestrictiveBooleanMechanism(
            PolicyState<Boolean> policyState) {
        try {
            return (MostRestrictive<Boolean>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRestrictive<Boolean>: " + e);
            return null;
        }
    }

    private MostRestrictive<Integer> getMostRestrictiveIntegerMechanism(
            PolicyState<Integer> policyState) {
        try {
            return (MostRestrictive<Integer>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRestrictive<Integer>: " + e);
            return null;
        }
    }

    private TopPriority<?> getTopPriorityMechanism(PolicyState<?> policyState) {
        try {
            return (TopPriority<?>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type TopPriority<>: " + e);
            return null;
        }
    }

    private FlagUnion getFlagUnionMechanism(
            PolicyState<Integer> policyState) {
        try {
            return (FlagUnion) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type FlagUnion: " + e);
            return null;
        }
    }

    private MostRecent<Set<String>> getMostRecentStringSetMechanism(
            PolicyState<Set<String>> policyState) {
        try {
            return (MostRecent<Set<String>>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRecent<Set<String>>: " + e);
            return null;
        }
    }
}
