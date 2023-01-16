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

package com.android.bedstead.remotedpc;

import static android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;

import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.ProvisioningException;
import android.content.ComponentName;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.DevicePolicyController;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Versions;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppProvider;

/** Entry point to RemoteDPC. */
public class RemoteDpc extends RemotePolicyManager {

    public static final ComponentName DPC_COMPONENT_NAME = new ComponentName(
            "com.android.cts.RemoteDPC",
            "com.android.bedstead.testapp.BaseTestAppDeviceAdminReceiver"
    );

    private static final DevicePolicyManager sDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);
    private static final TestAppProvider sTestAppProvider = new TestAppProvider();
    public static final TestApp REMOTE_DPC_TEST_APP = sTestAppProvider.query()
            .wherePackageName().isEqualTo(DPC_COMPONENT_NAME.getPackageName())
            .get();

    private boolean mShouldRemoveUserWhenRemoved = false;

    /**
     * Get the {@link RemoteDpc} instance for the Device Owner.
     *
     * <p>This will return {@code null} if there is no Device Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc deviceOwner() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
        if (deviceOwner == null || !deviceOwner.componentName().equals(DPC_COMPONENT_NAME)) {
            return null;
        }

        return new RemoteDpc(deviceOwner);
    }

    /**
     * Get the {@link RemoteDpc} instance for the Profile Owner of the current user.
     *
     * <p>This will return null if there is no Profile Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc profileOwner() {
        return profileOwner(TestApis.users().instrumented());
    }

    /**
     * Get the {@link RemoteDpc} instance for the Profile Owner of the given {@code profile}.
     *
     * <p>This will return null if there is no Profile Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc profileOwner(UserHandle profile) {
        if (profile == null) {
            throw new NullPointerException();
        }

        return profileOwner(TestApis.users().find(profile));
    }

    /**
     * Get the {@link RemoteDpc} instance for the Profile Owner of the given {@code profile}.
     *
     * <p>This will return null if there is no Profile Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc profileOwner(UserReference profile) {
        if (profile == null) {
            throw new NullPointerException();
        }

        ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner(profile);
        if (profileOwner == null || !profileOwner.componentName().equals(DPC_COMPONENT_NAME)) {
            return null;
        }

        return new RemoteDpc(profileOwner);
    }

    /**
     * Get the most specific {@link RemoteDpc} instance for the current user.
     *
     * <p>If the user has a RemoteDPC Profile Owner, this will refer to that. If it does not but
     * has a RemoteDPC Device Owner it will refer to that. Otherwise it will return null.
     */
    @Nullable
    public static RemoteDpc any() {
        return any(TestApis.users().instrumented());
    }

    /**
     * Get the most specific {@link RemoteDpc} instance for the current user.
     *
     * <p>If the user has a RemoteDPC Profile Owner, this will refer to that. If it does not but
     * has a RemoteDPC Device Owner it will refer to that. Otherwise it will return null.
     */
    @Nullable
    public static RemoteDpc any(UserHandle user) {
        if (user == null) {
            throw new NullPointerException();
        }

        return any(TestApis.users().find(user));
    }

    /**
     * Get the most specific {@link RemoteDpc} instance for the current user.
     *
     * <p>If the user has a RemoteDPC Profile Owner, this will refer to that. If it does not but
     * has a RemoteDPC Device Owner it will refer to that. Otherwise it will return null.
     */
    @Nullable
    public static RemoteDpc any(UserReference user) {
        RemoteDpc remoteDPC = profileOwner(user);
        if (remoteDPC != null) {
            return remoteDPC;
        }
        return deviceOwner();
    }

    /**
     * Get the {@link RemoteDpc} controller for the given {@link DevicePolicyController}.
     */
    public static RemoteDpc forDevicePolicyController(DevicePolicyController controller) {
        if (controller == null) {
            throw new NullPointerException();
        }
        if (!controller.componentName().equals(DPC_COMPONENT_NAME)) {
            throw new IllegalStateException("DevicePolicyController is not a RemoteDPC: "
                    + controller);
        }

        return new RemoteDpc(controller);
    }

    /**
     * Set RemoteDPC as the Device Owner.
     */
    public static RemoteDpc setAsDeviceOwner() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
        if (deviceOwner != null) {
            if (deviceOwner.componentName().equals(DPC_COMPONENT_NAME)) {
                return new RemoteDpc(deviceOwner); // Already set
            }
            deviceOwner.remove();
        }

        ensureInstalled(TestApis.users().system());
        RemoteDpc remoteDpc = new RemoteDpc(
                TestApis.devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME));
        return remoteDpc;
    }

    /**
     * Set RemoteDPC as the Profile Owner.
     */
    public static RemoteDpc setAsProfileOwner(UserHandle user) {
        if (user == null) {
            throw new NullPointerException();
        }
        return setAsProfileOwner(TestApis.users().find(user));
    }

    /**
     * Set RemoteDPC as the Profile Owner.
     *
     * <p>If called for Android versions prior to Q, an exception will be thrown if the user is not
     * the instrumented user.
     */
    public static RemoteDpc setAsProfileOwner(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }

        if (!user.equals(TestApis.users().instrumented())) {
            if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.Q)) {
                throw new NeneException("Cannot use RemoteDPC across users prior to Q");
            }
        }

        ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner(user);
        if (profileOwner != null) {
            if (profileOwner.componentName().equals(DPC_COMPONENT_NAME)) {
                return new RemoteDpc(profileOwner); // Already set
            }
            profileOwner.remove();
        }

        ensureInstalled(user);
        RemoteDpc remoteDpc = new RemoteDpc(
                TestApis.devicePolicy().setProfileOwner(user, DPC_COMPONENT_NAME));

        // DISALLOW_INSTALL_UNKNOWN_SOURCES causes verification failures in work profiles
        remoteDpc.devicePolicyManager()
                .clearUserRestriction(remoteDpc.componentName(), DISALLOW_INSTALL_UNKNOWN_SOURCES);

        return remoteDpc;
    }

    /**
     * Create a work profile of the instrumented user with RemoteDpc as the profile owner.
     *
     * <p>If autoclosed, the user will be removed along with the dpc.
     *
     * <p>If called for Android versions prior to Q an exception will be thrown
     */
    @Experimental
    public static RemoteDpc createWorkProfile() {
        return createWorkProfile(TestApis.users().instrumented());
    }

    /**
     * Create a work profile with RemoteDpc as the profile owner.
     *
     * <p>If autoclosed, the user will be removed along with the dpc.
     *
     * <p>If called for Android versions prior to Q an exception will be thrown
     */
    @Experimental
    public static RemoteDpc createWorkProfile(UserReference parent) {
        // It'd be ideal if this method could be in TestApis.devicePolicy() but the dependency
        // direction wouldn't allow it
        if (parent == null) {
            throw new NullPointerException();
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.Q)) {
            throw new NeneException("Cannot use RemoteDPC across users prior to Q");
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            UserReference profile = TestApis.users().createUser()
                .type(TestApis.users().supportedType(MANAGED_PROFILE_TYPE_NAME))
                .parent(parent)
                .createAndStart();

            return setAsProfileOwner(profile);
        }
        ensureInstalled(parent);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            RemoteDpc dpc = forDevicePolicyController(TestApis.devicePolicy().getProfileOwner(
                    sDevicePolicyManager.createAndProvisionManagedProfile(
                            new ManagedProfileProvisioningParams.Builder(
                                    DPC_COMPONENT_NAME, "RemoteDPC").build())));
            dpc.mShouldRemoveUserWhenRemoved = true;
            return dpc;

        } catch (ProvisioningException e) {
            throw new NeneException("Error provisioning work profile", e);
        }
    }

    private static void ensureInstalled(UserReference user) {
        REMOTE_DPC_TEST_APP.install(user);
    }

    private final DevicePolicyController mDevicePolicyController;

    RemoteDpc(DevicePolicyController devicePolicyController) {
        super(REMOTE_DPC_TEST_APP, devicePolicyController == null ? null
                : devicePolicyController.user());
        mDevicePolicyController = devicePolicyController;
    }

    /**
     * Get the {@link DevicePolicyController} for this instance of RemoteDPC.
     */
    public DevicePolicyController devicePolicyController() {
        return mDevicePolicyController;
    }

    /**
     * Remove RemoteDPC as Device Owner or Profile Owner and uninstall the APK from the user.
     */
    public void remove() {
        if (mShouldRemoveUserWhenRemoved) {
            mDevicePolicyController.user().remove();
        } else {
            mDevicePolicyController.remove();
            TestApis.packages().find(DPC_COMPONENT_NAME.getPackageName())
                    .uninstall(mDevicePolicyController.user());
        }
    }

    @Override
    public void close() {
        remove();
    }

    /**
     * Get the {@link ComponentName} of the DPC.
     */
    @Override
    public ComponentName componentName() {
        return DPC_COMPONENT_NAME;
    }

    @Override
    public int hashCode() {
        return mDevicePolicyController.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RemoteDpc)) {
            return false;
        }

        RemoteDpc other = (RemoteDpc) obj;
        return other.mDevicePolicyController.equals(mDevicePolicyController);
    }

    @Override
    public String toString() {
        return "RemoteDpc{"
                + "devicePolicyController=" + mDevicePolicyController
                + ", testApp=" + super.toString()
                + '}';
    }
}
