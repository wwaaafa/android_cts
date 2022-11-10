/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package android.multiuser.cts;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.QUERY_USERS;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.multiuser.cts.TestingUtils.getBooleanProperty;
import static android.os.UserManager.USER_OPERATION_SUCCESS;
import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;
import static android.os.UserManager.USER_TYPE_PROFILE_CLONE;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;

import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.nene.types.OptionalBoolean.FALSE;
import static com.android.bedstead.nene.types.OptionalBoolean.TRUE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.pm.UserProperties;
import android.graphics.Bitmap;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.SystemUserOnly;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireNotMultipleUsersOnMultipleDisplays;
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.concurrent.GuardedBy;

@RunWith(BedsteadJUnit4.class)
public final class UserManagerTest {

    private static final String TAG = UserManagerTest.class.getSimpleName();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private UserManager mUserManager;

    private final String mAccountName = "test_account_name";
    private final String mAccountType = "test_account_type";
    private static final int REMOVE_CHECK_INTERVAL_MILLIS = 500; // 0.5 seconds
    private static final int REMOVE_TIMEOUT_MILLIS = 60 * 1000; // 60 seconds
    private final Object mUserRemoveLock = new Object();

    @Before
    public void setUp() {
        mUserManager = sContext.getSystemService(UserManager.class);
        assertWithMessage("UserManager service").that(mUserManager).isNotNull();

        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_REMOVED);
        sContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (mUserRemoveLock) {
                    mUserRemoveLock.notifyAll();
                }
            }
        }, filter);
    }

    @EnsureHasPermission(CREATE_USERS)
    private void removeUser(UserHandle userHandle) {
        if (userHandle == null) {
            return;
        }

        synchronized (mUserRemoveLock) {
            assertThat(mUserManager.removeUser(userHandle)).isTrue();
        }
    }

    /**
     * Verify that the isUserAGoat() method always returns false for API level 30. This is
     * because apps targeting R no longer have access to package queries by default.
     */
    @Test
    public void testUserGoat_api30() {
        assertWithMessage("isUserAGoat()").that(mUserManager.isUserAGoat()).isFalse();
    }

    @Test
    public void testIsRemoveResultSuccessful() {
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_REMOVED))
                .isTrue();
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_DEFERRED))
                .isTrue();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ALREADY_BEING_REMOVED))
                        .isTrue();
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_UNKNOWN))
                .isFalse();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_USER_RESTRICTION))
                        .isFalse();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_USER_NOT_FOUND))
                        .isFalse();
        assertThat(
                UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_SYSTEM_USER))
                        .isFalse();
    }

    @Test
    public void testIsHeadlessSystemUserMode() throws Exception {
        boolean expected = getBooleanProperty(mInstrumentation,
                "ro.fw.mu.headless_system_user");
        assertWithMessage("isHeadlessSystemUserMode()")
                .that(UserManager.isHeadlessSystemUserMode()).isEqualTo(expected);
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnInitialUser
    public void testIsUserForeground_differentContext_noPermission() throws Exception {
        Context context = getContextForOtherUser();
        UserManager um = context.getSystemService(UserManager.class);

        assertThrows(SecurityException.class, () -> um.isUserForeground());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void testIsUserForeground_differentContext_withPermission() throws Exception {
        Context userContext = getContextForOtherUser();
        UserManager um = userContext.getSystemService(UserManager.class);

        assertWithMessage("isUserForeground() for unknown user").that(um.isUserForeground())
                .isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnInitialUser
    public void testIsUserForeground_currentUser() throws Exception {
        assertWithMessage("isUserForeground() for current user")
                .that(mUserManager.isUserForeground()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnSecondaryUser(switchedToUser = FALSE)
    public void testIsUserForeground_backgroundUser() throws Exception {
        assertWithMessage("isUserForeground() for bg user (%s)", sContext.getUser())
                .that(mUserManager.isUserForeground()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnWorkProfile // TODO(b/239961027): should be @RequireRunOnProfile instead
    public void testIsUserForeground_profileOfCurrentUser() throws Exception {
        assertWithMessage("isUserForeground() for profile(%s) of current user", sContext.getUser())
                .that(mUserManager.isUserForeground()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserRunning"})
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call isUserRunning()
    public void testIsUserRunning_stoppedProfileOfCurrentUser() {
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        Context context = getContextForUser(profile.userHandle().getIdentifier());
        UserManager um = context.getSystemService(UserManager.class);

        assertWithMessage("isUserRunning() for stopped profile (id=%s) of current user",
                profile.id()).that(um.isUserRunning(profile.userHandle()))
                .isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserRunning"})
    @RequireRunOnSecondaryUser(switchedToUser = FALSE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call isUserRunning()
    public void testIsUserRunning_stoppedSecondaryUser() {
        UserReference user = TestApis.users().instrumented();
        Log.d(TAG, "Stopping  user " + user + " (called from " + sContext.getUser() + ")");
        user.stop();

        Context context = getContextForUser(user.userHandle().getIdentifier());
        UserManager um = context.getSystemService(UserManager.class);

        assertWithMessage("isUserRunning() for stopped secondary user (id=%s)",
                user.id()).that(um.isUserRunning(user.userHandle())).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @EnsureDoesNotHavePermission(INTERACT_ACROSS_USERS)
    public void testIsUserVisible_differentContext_noPermission() throws Exception {
        Context context = sContext.createContextAsUser(UserHandle.of(-42), /* flags= */ 0);
        UserManager um = context.getSystemService(UserManager.class);

        assertThrows(SecurityException.class, () -> um.isUserVisible());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void testIsUserVisible_differentContext_withPermission() throws Exception {
        Context context = getContextForOtherUser();
        UserManager um = context.getSystemService(UserManager.class);

        assertWithMessage("isUserVisible() for unknown user").that(um.isUserVisible()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireRunOnInitialUser
    public void testIsUserVisible_currentUser() throws Exception {
        assertWithMessage("isUserVisible() for current user (id=%s)", sContext.getUser())
                .that(mUserManager.isUserVisible()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireRunOnSecondaryUser(switchedToUser = FALSE)
    public void testIsUserVisible_backgroundUser() throws Exception {
        assertWithMessage("isUserVisible() for background user (id=%s)", sContext.getUser())
                .that(mUserManager.isUserVisible()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    // TODO(b/239961027): should be @RequireRunOnProfile instead
    @RequireRunOnWorkProfile(switchedToParentUser = TRUE)
    public void testIsUserVisible_startedProfileOfCurrentUser() throws Exception {
        assertWithMessage("isUserVisible() for profile of current user (%s)",
                sContext.getUser()).that(mUserManager.isUserVisible()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    // Cannot use @RunOnProfile as it will stop the profile
    @RequireRunOnInitialUser
    // TODO(b/239961027): should be @EnsureHasProfile instead of @EnsureHasWorkProfile
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call isUserVisible() on other context
    public void testIsUserVisible_stoppedProfileOfCurrentUser() throws Exception {
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        Context context = getContextForUser(profile.userHandle().getIdentifier());
        UserManager um = context.getSystemService(UserManager.class);

        assertWithMessage("isUserVisible() for stopped profile (id=%s) of current user",
                profile.id()).that(um.isUserVisible()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @EnsureDoesNotHavePermission(INTERACT_ACROSS_USERS)
    public void testGetVisibleUsers_noPermission() throws Exception {
        Context context = getContextForOtherUser();
        UserManager um = context.getSystemService(UserManager.class);

        assertThrows(SecurityException.class, () -> um.getVisibleUsers());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @RequireRunOnInitialUser
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_currentUser() throws Exception {
        List<UserHandle> visibleUsers = mUserManager.getVisibleUsers();

        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .contains(TestApis.users().current().userHandle());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @RequireRunOnSecondaryUser(switchedToUser = FALSE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_backgroundUser() throws Exception {
        List<UserHandle> visibleUsers = mUserManager.getVisibleUsers();

        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .contains(TestApis.users().current().userHandle());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    // TODO(b/239961027): should be @RequireRunOnProfile instead
    @RequireRunOnWorkProfile(switchedToParentUser = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_startedProfileOfCurrentUser() throws Exception {
        UserReference myUser = TestApis.users().instrumented();
        List<UserHandle> visibleUsers = mUserManager.getVisibleUsers();

        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .containsAtLeast(myUser.userHandle(), myUser.parent().userHandle());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    // Cannot use @RunOnProfile as it will stop the profile
    @RequireRunOnInitialUser
    // TODO(b/239961027): should be @EnsureHasProfile instead of @EnsureHasWorkProfile
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_stoppedProfileOfCurrentUser() throws Exception {
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        Context context = getContextForUser(profile.userHandle().getIdentifier());
        UserManager um = context.getSystemService(UserManager.class);

        List<UserHandle> visibleUsers = mUserManager.getVisibleUsers();

        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .contains(TestApis.users().current().userHandle());
        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .doesNotContain(profile.userHandle());
    }

    @Test
    @RequireNotMultipleUsersOnMultipleDisplays(reason = "Because API is not supported")
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundOnSecondaryDisplay"})
    public void testStartUserInBackgroundOnSecondaryDisplay() {
        // ids doen't really matter, as it should throw right away
        assertThrows(UnsupportedOperationException.class,
                () -> tryToStartBackgroundUserOnSecondaryDisplay(42, 108));
    }

    @Test
    @EnsureHasNoWorkProfile
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @EnsureHasPermission(CREATE_USERS)
    public void testCloneProfile() throws Exception {
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle userHandle = null;

        // Need CREATE_USERS permission to create user in test
        try {
            try {
                userHandle = mUserManager.createProfile(
                    "Clone profile", USER_TYPE_PROFILE_CLONE, new HashSet<>());
            } catch (UserManager.UserOperationException e) {
                // Not all devices and user types support these profiles; skip if this one doesn't.
                assumeNoException("Couldn't create clone profile", e);
                return;
            }
            assertThat(userHandle).isNotNull();

            final Context userContext = sContext.createPackageContextAsUser("system", 0,
                    userHandle);
            final UserManager cloneUserManager = userContext.getSystemService(UserManager.class);
            assertThat(cloneUserManager.isMediaSharedWithParent()).isTrue();
            assertThat(cloneUserManager.isCredentialSharableWithParent()).isTrue();
            assertThat(cloneUserManager.isCloneProfile()).isTrue();
            assertThat(cloneUserManager.isProfile()).isTrue();
            assertThat(cloneUserManager.isUserOfType(UserManager.USER_TYPE_PROFILE_CLONE)).isTrue();

            final List<UserInfo> list = mUserManager.getUsers(true, true, true);
            final UserHandle finalUserHandle = userHandle;
            final List<UserInfo> cloneUsers = list.stream().filter(
                    user -> (user.id == finalUserHandle.getIdentifier()
                            && user.isCloneProfile()))
                    .collect(Collectors.toList());
            assertThat(cloneUsers.size()).isEqualTo(1);
        } finally {
            removeUser(userHandle);
        }
    }


    @Test
    @EnsureHasNoWorkProfile
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @AppModeFull
    @EnsureHasPermission(CREATE_USERS)
    public void testAddCloneProfile_shouldSendProfileAddedBroadcast() {
        assumeTrue(mUserManager.supportsMultipleUsers());
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState
                .registerBroadcastReceiver(Intent.ACTION_PROFILE_ADDED, /* checker= */null);
        UserHandle userHandle = null;
            try {
                userHandle = mUserManager.createProfile("Clone profile",
                        USER_TYPE_PROFILE_CLONE, new HashSet<>());
                assumeNotNull(userHandle);
                broadcastReceiver.awaitForBroadcastOrFail();
            } catch (UserManager.UserOperationException e) {
                assumeNoException("Couldn't create clone profile", e);
            } finally {
                removeUser(userHandle);
            }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @AppModeFull
    @EnsureHasNoWorkProfile
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CREATE_USERS)
    public void testCreateManagedProfile_shouldSendProfileAddedBroadcast() {
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState
                .registerBroadcastReceiver(Intent.ACTION_PROFILE_ADDED, /* checker= */null);
        UserHandle userHandle = null;
            try {
                userHandle = mUserManager.createProfile("Managed profile",
                        USER_TYPE_PROFILE_MANAGED, new HashSet<>());
                assumeNotNull(userHandle);
                broadcastReceiver.awaitForBroadcastOrFail();
            } catch (UserManager.UserOperationException e) {
                assumeNoException("Couldn't create managed profile", e);
            } finally {
                removeUser(userHandle);
            }
    }

    @Test
    @EnsureHasNoWorkProfile
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @AppModeFull
    @EnsureHasPermission(CREATE_USERS)
    public void testRemoveCloneProfile_shouldSendProfileRemovedBroadcast() {
        assumeTrue(mUserManager.supportsMultipleUsers());
        BlockingBroadcastReceiver broadcastReceiver = null;
        UserHandle userHandle = null;
            try {
                userHandle = mUserManager.createProfile("Clone profile",
                        USER_TYPE_PROFILE_CLONE, new HashSet<>());
                broadcastReceiver = sDeviceState
                        .registerBroadcastReceiver(
                                Intent.ACTION_PROFILE_REMOVED, userIsEqual(userHandle)
                );
                assumeNotNull(userHandle);
                removeUser(userHandle);
                broadcastReceiver.awaitForBroadcastOrFail();
            } catch (UserManager.UserOperationException e) {
                assumeNoException("Couldn't create clone profile", e);
            }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @AppModeFull
    @EnsureHasNoWorkProfile
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CREATE_USERS)
    public void testRemoveManagedProfile_shouldSendProfileRemovedBroadcast() {
        BlockingBroadcastReceiver broadcastReceiver = null;
        UserHandle userHandle = null;
            try {
                userHandle = mUserManager.createProfile("Managed profile",
                        USER_TYPE_PROFILE_MANAGED, new HashSet<>());
                broadcastReceiver = sDeviceState
                        .registerBroadcastReceiver(
                                Intent.ACTION_PROFILE_REMOVED, userIsEqual(userHandle)
                        );
                assumeNotNull(userHandle);
                removeUser(userHandle);
                broadcastReceiver.awaitForBroadcastOrFail();
            } catch (UserManager.UserOperationException e) {
                assumeNoException("Couldn't create managed profile", e);
            }
    }

    @Test
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasNoWorkProfile
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    @ApiTest(apis = {
            "android.os.UserManager#createProfile",
            "android.os.UserManager#isManagedProfile",
            "android.os.UserManager#isProfile",
            "android.os.UserManager#isUserOfType"})
    public void testManagedProfile() throws Exception {
        UserHandle userHandle = null;

        try {
            try {
                userHandle = mUserManager.createProfile(
                    "Managed profile", UserManager.USER_TYPE_PROFILE_MANAGED, new HashSet<>());
            } catch (UserManager.UserOperationException e) {
                // Not all devices and user types support these profiles; skip if this one doesn't.
                assumeNoException("Couldn't create managed profile", e);
                return;
            }
            assertThat(userHandle).isNotNull();

            final UserManager umOfProfile = sContext
                    .createPackageContextAsUser("android", 0, userHandle)
                    .getSystemService(UserManager.class);

            assertThat(umOfProfile.isManagedProfile()).isTrue();
            assertThat(umOfProfile.isManagedProfile(userHandle.getIdentifier())).isTrue();
            assertThat(umOfProfile.isProfile()).isTrue();
            assertThat(umOfProfile.isUserOfType(UserManager.USER_TYPE_PROFILE_MANAGED)).isTrue();
        } finally {
            removeUser(userHandle);
        }
    }

    @Test
    @RequireHeadlessSystemUserMode(reason = "Secondary user profile is only available on headless")
    @ApiTest(apis = {"android.os.UserManager#removeUser"})
    @EnsureHasAdditionalUser
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(forUser = ADDITIONAL_USER)
    @EnsureHasPermission(CREATE_USERS)
    public void testRemoveParentUser_withProfiles() {
        UserReference workProfile = sDeviceState.workProfile(/* forUser= */ ADDITIONAL_USER);
        UserReference parentUser = workProfile.parent();
        parentUser.remove();

        // Removing parent user will also remove its profile
        assertThat(parentUser.exists()).isFalse();
        assertThat(workProfile.exists()).isFalse();
    }

    @Test
    @RequireHeadlessSystemUserMode(reason = "Secondary user profile is only available on headless")
    @ApiTest(apis = {"android.os.UserManager#removeUser"})
    @EnsureHasAdditionalUser
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(forUser = ADDITIONAL_USER)
    @EnsureHasPermission(CREATE_USERS)
    public void testRemoveUserOnlyProfile_ShouldNotRemoveAnyOtherUserInSameProfileGroupId() {
        UserHandle parentUser = null;

        try {
            UserReference workProfile = sDeviceState.workProfile(/* forUser= */ ADDITIONAL_USER);
            parentUser = workProfile.parent().userHandle();
            UserHandle workProfileUser = workProfile.userHandle();

            removeUser(workProfile.userHandle());
            synchronized (mUserRemoveLock) {
                waitForUserRemovalLocked(workProfileUser.getIdentifier());
            }

            //Removing a profile will only remove the profile and not the parent user
            assertThat(hasUser(workProfileUser.getIdentifier())).isFalse();
            assertThat(hasUser(parentUser.getIdentifier())).isTrue();
        } finally {
            removeUser(parentUser);
        }
    }

    @Test
    @EnsureHasPermission({QUERY_USERS})
    @ApiTest(apis = {
            "android.os.UserManager#isSystemUser",
            "android.os.UserManager#isUserOfType",
            "android.os.UserManager#isProfile",
            "android.os.UserManager#isManagedProfile",
            "android.os.UserManager#isCloneProfile"})
    public void testSystemUser() throws Exception {
        final UserManager umOfSys = sContext
                .createPackageContextAsUser("android", 0, UserHandle.SYSTEM)
                .getSystemService(UserManager.class);

        assertThat(umOfSys.isSystemUser()).isTrue();
        // We cannot demand what type of user SYSTEM is, but we can say some things it isn't.
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_PROFILE_CLONE)).isFalse();
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_PROFILE_MANAGED)).isFalse();
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_FULL_GUEST)).isFalse();

        assertThat(umOfSys.isProfile()).isFalse();
        assertThat(umOfSys.isManagedProfile()).isFalse();
        assertThat(umOfSys.isManagedProfile(UserHandle.USER_SYSTEM)).isFalse();
        assertThat(umOfSys.isCloneProfile()).isFalse();
    }

    @Test
    @EnsureHasNoWorkProfile
    @SystemUserOnly(reason = "Restricted users are only supported on system user.")
    @ApiTest(apis = {
            "android.os.UserManager#isRestrictedProfile",
            "android.os.UserManager#getRestrictedProfileParent",
            "android.os.UserManager#createRestrictedProfile"})
    @EnsureHasPermission(CREATE_USERS)
    public void testRestrictedUser() throws Exception {
        UserHandle user = null;
        try {
            // Check that the SYSTEM user is not restricted.
            assertThat(mUserManager.isRestrictedProfile()).isFalse();
            assertThat(mUserManager.isRestrictedProfile(UserHandle.SYSTEM)).isFalse();
            assertThat(mUserManager.getRestrictedProfileParent()).isNull();

            final UserInfo info = mUserManager.createRestrictedProfile("Restricted user");

            // If the device supports Restricted users, it must report it correctly.
            assumeTrue("Couldn't create a restricted profile", info != null);

            user = UserHandle.of(info.id);
            assertThat(mUserManager.isRestrictedProfile(user)).isTrue();

            final Context userContext = sContext.createPackageContextAsUser("system", 0, user);
            final UserManager userUm = userContext.getSystemService(UserManager.class);

            assertThat(userUm.isRestrictedProfile()).isTrue();
            assertThat(userUm.getRestrictedProfileParent().isSystem()).isTrue();
        } finally {
            removeUser(user);
        }
    }

    private NewUserRequest newUserRequest() {
        final PersistableBundle accountOptions = new PersistableBundle();
        accountOptions.putString("test_account_option_key", "test_account_option_value");

        return new NewUserRequest.Builder()
                .setName("test_user")
                .setUserType(USER_TYPE_FULL_SECONDARY)
                .setUserIcon(Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565))
                .setAccountName(mAccountName)
                .setAccountType(mAccountType)
                .setAccountOptions(accountOptions)
                .build();
    }

    @Test
    @EnsureHasPermission(CREATE_USERS)
    public void testSomeUserHasAccount() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user = null;

        try {
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isFalse();
            user = mUserManager.createUser(newUserRequest()).getUser();
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isTrue();
        } finally {
            removeUser(user);
        }
    }

    @Test
    @EnsureHasPermission(CREATE_USERS)
    public void testSomeUserHasAccount_shouldIgnoreToBeRemovedUsers() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
            final NewUserResponse response = mUserManager.createUser(newUserRequest());
            assertThat(response.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            mUserManager.removeUser(response.getUser());
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isFalse();
    }

    @Test
    @ApiTest(apis = {
            "android.os.UserManager#supportsMultipleUsers",
            "android.os.UserManager#createUser",
            "android.os.UserManager#getUserName",
            "android.os.UserManager#isUserNameSet",
            "android.os.UserManager#getUserType",
            "android.os.UserManager#isUserOfType"})
    @EnsureHasPermission(CREATE_USERS)
    public void testCreateUser_withNewUserRequest_shouldCreateUserWithCorrectProperties()
            throws PackageManager.NameNotFoundException {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user = null;

        try {
            final NewUserRequest request = newUserRequest();
            final NewUserResponse response = mUserManager.createUser(request);
            user = response.getUser();

            assertThat(response.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            assertThat(response.isSuccessful()).isTrue();
            assertThat(user).isNotNull();

            UserManager userManagerOfNewUser = sContext
                    .createPackageContextAsUser("android", 0, user)
                    .getSystemService(UserManager.class);

            assertThat(userManagerOfNewUser.getUserName()).isEqualTo(request.getName());
            assertThat(userManagerOfNewUser.isUserNameSet()).isTrue();
            assertThat(userManagerOfNewUser.getUserType()).isEqualTo(request.getUserType());
            assertThat(userManagerOfNewUser.isUserOfType(request.getUserType())).isEqualTo(true);
            // We can not test userIcon and accountOptions,
            // because getters require MANAGE_USERS permission.
            // And we are already testing accountName and accountType
            // are set correctly in testSomeUserHasAccount method.
        } finally {
            removeUser(user);
        }
    }

    @Test
    @EnsureHasPermission(CREATE_USERS)
    public void testCreateUser_withNewUserRequest_shouldNotAllowDuplicateUserAccounts() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user1 = null;
        UserHandle user2 = null;

        try {
            final NewUserResponse response1 = mUserManager.createUser(newUserRequest());
            user1 = response1.getUser();

            assertThat(response1.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            assertThat(response1.isSuccessful()).isTrue();
            assertThat(user1).isNotNull();

            final NewUserResponse response2 = mUserManager.createUser(newUserRequest());
            user2 = response2.getUser();

            assertThat(response2.getOperationResult()).isEqualTo(
                    UserManager.USER_OPERATION_ERROR_USER_ACCOUNT_ALREADY_EXISTS);
            assertThat(response2.isSuccessful()).isFalse();
            assertThat(user2).isNull();
        } finally {
            removeUser(user1);
            removeUser(user2);
        }
    }

    @Test
    @AppModeFull
    @EnsureHasWorkProfile // TODO(b/239961027): should also check for other profiles
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void getProfileParent_returnsParent() {
        final UserReference parent = TestApis.users().instrumented();
        for (UserHandle profile : mUserManager.getUserProfiles()) {
            if (!profile.equals(parent.userHandle())) {
                assertThat(mUserManager.getProfileParent(profile)).isEqualTo(parent.userHandle());
            }
        }
    }

    @Test
    @AppModeFull
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void getProfileParent_returnsNullForNonProfile() {
        assertThat(mUserManager.getProfileParent(TestApis.users().system().userHandle())).isNull();
    }

    @Test
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetRemainingCreatableUserCount() {
        final int maxAllowedIterations = 15;
        final String userType = USER_TYPE_FULL_SECONDARY;
        final NewUserRequest request = new NewUserRequest.Builder().build();
        final ArrayDeque<UserHandle> usersCreated = new ArrayDeque<>();

        try {
            final int initialRemainingCount = mUserManager.getRemainingCreatableUserCount(userType);
            assertThat(initialRemainingCount).isAtLeast(0);

            final int numUsersToAdd = Math.min(maxAllowedIterations, initialRemainingCount);

            for (int i = 0; i < numUsersToAdd; i++) {
                usersCreated.push(mUserManager.createUser(request).getUser());
                assertThat(mUserManager.getRemainingCreatableUserCount(userType))
                        .isEqualTo(initialRemainingCount - usersCreated.size());
            }
            for (int i = 0; i < numUsersToAdd; i++) {
                mUserManager.removeUser(usersCreated.pop());
                assertThat(mUserManager.getRemainingCreatableUserCount(userType))
                        .isEqualTo(initialRemainingCount - usersCreated.size());
            }
        } finally {
            usersCreated.forEach(this::removeUser);
        }
    }

    @Test
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetRemainingCreatableProfileCount() {
        final int maxAllowedIterations = 15;
        final String type = USER_TYPE_PROFILE_MANAGED;
        final ArrayDeque<UserHandle> profilesCreated = new ArrayDeque<>();
        final Set<String> disallowedPackages = new HashSet<>();
        try {
            final int initialRemainingCount =
                    mUserManager.getRemainingCreatableProfileCount(type);
            assertThat(initialRemainingCount).isAtLeast(0);

            final int numUsersToAdd = Math.min(maxAllowedIterations, initialRemainingCount);

            for (int i = 0; i < numUsersToAdd; i++) {
                profilesCreated.push(mUserManager.createProfile(null, type, disallowedPackages));
                assertThat(mUserManager.getRemainingCreatableProfileCount(type))
                        .isEqualTo(initialRemainingCount - profilesCreated.size());
            }
            for (int i = 0; i < numUsersToAdd; i++) {
                mUserManager.removeUser(profilesCreated.pop());
                assertThat(mUserManager.getRemainingCreatableProfileCount(type))
                        .isEqualTo(initialRemainingCount - profilesCreated.size());
            }
        } finally {
            profilesCreated.forEach(this::removeUser);
        }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getUserProperties"})
    @AppModeFull
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetUserProperties_system() {
        final UserHandle fullUser = TestApis.users().instrumented().userHandle();
        final UserProperties properties = mUserManager.getUserProperties(fullUser);
        assertThat(properties).isNotNull();

        assertThat(properties.getShowInLauncher()).isIn(Arrays.asList(
                UserProperties.SHOW_IN_LAUNCHER_WITH_PARENT));
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getUserProperties"})
    @AppModeFull
    @EnsureHasWorkProfile
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetUserProperties_managedProfile() {
        final UserHandle profile = sDeviceState.workProfile().userHandle();
        final UserProperties properties = mUserManager.getUserProperties(profile);
        assertThat(properties).isNotNull();

        assertThat(properties.getShowInLauncher()).isIn(Arrays.asList(
                UserProperties.SHOW_IN_LAUNCHER_WITH_PARENT,
                UserProperties.SHOW_IN_LAUNCHER_SEPARATE));
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isMainUser"})
    @RequireRunOnInitialUser
    @EnsureHasPermission({QUERY_USERS})
    public void testIsMainUser_initialUser() throws Exception {
        assertWithMessage("isMainUser() for initial user")
                .that(mUserManager.isMainUser()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isFirstFullUser"})
    @RequireRunOnAdditionalUser
    @EnsureHasPermission({QUERY_USERS})
    public void testIsMainUser_additionalUser() throws Exception {
        assertWithMessage("isMainUser() for additional user")
                .that(mUserManager.isMainUser()).isFalse();
    }

    static Context getContextForOtherUser() {
        // TODO(b/240207590): TestApis.context().instrumentedContextForUser(TestApis.users()
        // .nonExisting() doesn't work, it throws:
        // IllegalStateException: Own package not found for user 1: package=android.multiuser.cts
        // There might be some bug (or WAI :-) on ContextImpl that makes it behave different for
        // negative user ids. Anyways, for the purpose of this test, this workaround is fine (i.e.
        // the context user id is passed to the binder call and the service checks if it matches the
        // caller or the caller has the proper permission when it doesn't.
        return getContextForUser(-42);
    }

    static Context getContextForUser(int userId) {
        Log.d(TAG, "Getting context for user " + userId);
        Context context = sContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
        Log.d(TAG, "Got it: " + context);
        return context;
    }

    private Function<Intent, Boolean> userIsEqual(UserHandle userHandle) {
        try {
            return (intent) -> userHandle.equals(intent.getParcelableExtra(intent.EXTRA_USER));
        } catch (NullPointerException e) {
            assumeNoException("User handle is null", e);
        }
        return (intent) -> false;
    }

    // TODO(b/240736142): should be provided by Test Bedstead
    private boolean tryToStartBackgroundUserOnSecondaryDisplay(int userId, int displayId) {
        Log.d(TAG, "tryToStartBackgroundUserOnSecondaryDisplay(): user=" + userId + ", display="
                + displayId);

        try (PermissionContext p = TestApis.permissions().withPermission(CREATE_USERS)) {
            boolean started = sContext.getSystemService(ActivityManager.class)
                    .startUserInBackgroundOnSecondaryDisplay(userId, displayId);

            Log.d(TAG, "Started: " + started);
            return started;
        }
    }

    @GuardedBy("mUserRemoveLock")
    private void waitForUserRemovalLocked(int userId) {
        long startTime = System.currentTimeMillis();
        while (getUser(userId) != null) {
            try {
                mUserRemoveLock.wait(REMOVE_CHECK_INTERVAL_MILLIS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            if (System.currentTimeMillis() - startTime > REMOVE_TIMEOUT_MILLIS) {
                fail("Timeout waiting for removeUser. userId = " + userId);
            }
        }
    }

    @Nullable
    private UserInfo getUser(int id) {
        try (PermissionContext p = TestApis.permissions().withPermission(CREATE_USERS)) {
            return  mUserManager.getUsers(false, false, false)
                    .stream().filter(user -> user.id == id).findFirst()
                    .orElse(null);
        }
    }

    private boolean hasUser(int id) {
        return getUser(id) != null;
    }
}
