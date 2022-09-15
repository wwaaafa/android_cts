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

package android.car.cts.builtin.os;

import static android.Manifest.permission.CREATE_USERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.car.builtin.os.UserManagerHelper;
import android.car.cts.builtin.AbstractCarBuiltinTestCase;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * This class contains {@link UserManagerHelper} tests that rely on "heady" stuff, like creating
 * users - if your test doesn't need that, please use {@link UserManagerHelperLiteTest} instead.
 */
public final class UserManagerHelperHeavyTest extends AbstractCarBuiltinTestCase {

    private static final String TAG = UserManagerHelperHeavyTest.class.getSimpleName();

    private static final int WAIT_TIME_FOR_OPERATION_MS = 60_000;
    private static final int WAIT_TIME_BEFORE_RETRY_MS = 1_000;
    private static final int WAIT_TIME_FOR_NEGATIVE_RESULT_MS = 30_000;

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    private final UserManager mUserManager = mContext.getSystemService(UserManager.class);

    private UserHandle mUserToRemove;

    @Before
    public void setup() {
        removeAnyPreCreatedUser();
    }

    @After
    public void cleanUp() throws Exception {
        try {
            if (mUserToRemove != null) {
                Log.v(TAG, "Removing user created during test. User " + mUserToRemove);
                boolean result = mUserManager.removeUser(mUserToRemove);
                Log.v(TAG, "User: " + mUserToRemove + " Removed: " + result);
            } else {
                Log.v(TAG, "No user to remove");
            }
        } catch (Exception e) {
            Log.v(TAG, "Cannot remove User:" + mUserToRemove + ". Exception: " + e);
        }
    }

    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isSecondaryUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isPreCreatedUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForGuestUser() throws Exception {
        UserHandle guestUser = createGuestUser();

        assertThat(UserManagerHelper.isGuestUser(mUserManager, guestUser)).isTrue();
        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, guestUser)).isTrue();
        assertThat(UserManagerHelper.isSecondaryUser(mUserManager, guestUser)).isTrue();
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, guestUser)).isTrue();
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, guestUser)).isFalse();

        // User should be initialized, but to confirm, we should wait for some time as
        // Initialization flag is set later on. Any better option?
        Thread.sleep(WAIT_TIME_FOR_NEGATIVE_RESULT_MS);
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, guestUser)).isFalse();

        // User should be part of getUserHandles
        assertGetUserHandlesHasUser(guestUser);
    }

    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isSecondaryUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isPreCreatedUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForSecondaryFullUser() throws Exception {
        UserHandle fullUser = createSecondaryUser();

        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, fullUser)).isFalse();
        assertThat(UserManagerHelper.isGuestUser(mUserManager, fullUser)).isFalse();
        assertThat(UserManagerHelper.isSecondaryUser(mUserManager, fullUser)).isTrue();
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, fullUser)).isTrue();
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, fullUser)).isFalse();

        // User should be initialized, but to confirm, we should wait for some time as
        // Initialization flag is set later on. Any better option?
        Thread.sleep(WAIT_TIME_FOR_NEGATIVE_RESULT_MS);
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, fullUser)).isFalse();

        // User should be part of getUserHandles
        assertGetUserHandlesHasUser(fullUser);
    }

    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isSecondaryUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isPreCreatedUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForPreCreatedFullUser() throws Exception {
        UserHandle preCreateUser = preCreateUserTest(UserManager.USER_TYPE_FULL_SECONDARY);

        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, preCreateUser)).isFalse();
        assertThat(UserManagerHelper.isGuestUser(mUserManager, preCreateUser)).isFalse();
        assertThat(UserManagerHelper.isSecondaryUser(mUserManager, preCreateUser)).isTrue();
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, preCreateUser)).isTrue();
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, preCreateUser)).isTrue();

        // User should be initialized, wait for it.
        waitForUserToInitialize(preCreateUser);
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, preCreateUser)).isTrue();

        // User should be part of getUserHandles
        assertGetUserHandlesHasUser(preCreateUser);
    }

    @Ignore("TODO(b/246876816): sometimes it crashes system server")
    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isSecondaryUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isPreCreatedUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForPreCreatedGuestUser() throws Exception {
        UserHandle preCreateUser = preCreateUserTest(UserManager.USER_TYPE_FULL_GUEST);

        // Should not be ephemeral, will be ephemeral after promoted
        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, preCreateUser)).isFalse();

        assertThat(UserManagerHelper.isGuestUser(mUserManager, preCreateUser)).isTrue();
        assertThat(UserManagerHelper.isSecondaryUser(mUserManager, preCreateUser)).isTrue();
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, preCreateUser)).isTrue();
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, preCreateUser)).isTrue();

        // User should be initialized, wait for it.
        waitForUserToInitialize(preCreateUser);
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, preCreateUser)).isTrue();

        // User should be part of getUserHandles
        assertGetUserHandlesHasUser(preCreateUser);
    }

    private void assertGetUserHandlesHasUser(UserHandle user) {
        List<UserHandle> allUsersHandles = UserManagerHelper.getUserHandles(mUserManager,
                /* excludePartial= */ false, /* excludeDying= */ false,
                /* excludePreCreated= */ false);
        assertThat(allUsersHandles).contains(user);
    }

    private void waitForUserToInitialize(UserHandle preCreateUser) throws Exception {
        long startTime = SystemClock.elapsedRealtime();
        long waitTime = SystemClock.elapsedRealtime() - startTime;
        while (!UserManagerHelper.isInitializedUser(mUserManager, preCreateUser)
                && waitTime < WAIT_TIME_FOR_OPERATION_MS) {
            waitTime = SystemClock.elapsedRealtime() - startTime;
            Log.v(TAG, "Waiting for user to initialize. Wait time in MS:" + waitTime);
            Thread.sleep(WAIT_TIME_BEFORE_RETRY_MS);
        }
    }

    private UserHandle createGuestUser() {
        NewUserRequest request = new NewUserRequest.Builder()
                .setUserType(UserManager.USER_TYPE_FULL_GUEST).setEphemeral().build();
        NewUserResponse response = mUserManager.createUser(request);
        if (response.isSuccessful()) {
            mUserToRemove = response.getUser();
            return mUserToRemove;
        }
        fail("Could not create guest User. Response: " + response);
        return null;
    }

    private UserHandle createSecondaryUser() {
        NewUserRequest request = new NewUserRequest.Builder()
                .setUserType(UserManager.USER_TYPE_FULL_SECONDARY).build();
        NewUserResponse response = mUserManager.createUser(request);
        if (response.isSuccessful()) {
            mUserToRemove = response.getUser();
            return mUserToRemove;
        }
        fail("Could not create secondary User. Response: " + response);
        return null;
    }

    private UserHandle createPreCreatedUser(String type) {
        mUserToRemove = UserManagerHelper.preCreateUser(mUserManager, type);
        if (mUserToRemove == null) {
            fail("Could not create precreated User of type:" + type);
        }
        return mUserToRemove;
    }

    private UserHandle preCreateUserTest(String type) {
        UserHandle user = createPreCreatedUser(type);
        assertPrecreatedUserExists(user, type);
        return user;
    }

    private void assertPrecreatedUserExists(UserHandle user, String type) {
        String allUsers = SystemUtil.runShellCommand("cmd user list --all -v");
        String[] result = allUsers.split("\n");
        for (int i = 0; i < result.length; i++) {
            if (result[i].contains("id=" + user.getIdentifier())) {
                assertThat(result[i]).contains("(pre-created)");
                if (type == UserManager.USER_TYPE_FULL_SECONDARY) {
                    assertThat(result[i]).contains("type=full.SECONDARY");
                }
                if (type == UserManager.USER_TYPE_FULL_GUEST) {
                    assertThat(result[i]).contains("type=full.GUEST");
                }
                return;
            }
        }
        fail("User not found. All users: " + allUsers + ". Expected user: " + user);
    }

    private void removeAnyPreCreatedUser() {
        // Existing pre-created user can interfere with the test logic. Remove all existing
        // pre-created Users.
        List<UserHandle> allUsersHandles = UserManagerHelper.getUserHandles(mUserManager,
                /* excludePartial= */ true, /* excludeDying= */ true,
                /* excludePreCreated= */ false);
        Log.v(TAG, "removeAnyPreCreatedUser(): allUsers=" + allUsersHandles);
        for (UserHandle userHandle : allUsersHandles) {
            if (UserManagerHelper.isPreCreatedUser(mUserManager, userHandle)) {
                Log.v(TAG, "Removing pre-created user " + userHandle);
                boolean result = mUserManager.removeUser(userHandle);
                Log.v(TAG, "Pre-created user: " + userHandle + " Removed: " + result);
            }
        }
    }
}
