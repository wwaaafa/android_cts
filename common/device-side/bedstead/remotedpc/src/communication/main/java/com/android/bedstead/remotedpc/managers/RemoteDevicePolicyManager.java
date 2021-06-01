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

package com.android.bedstead.remotedpc.managers;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

import androidx.annotation.NonNull;

import com.android.bedstead.remotedpc.processor.annotations.RemoteDpcAutomaticAdmin;
import com.android.bedstead.remotedpc.processor.annotations.RemoteDpcManager;

/**
 * Wrapper of {@link DevicePolicyManager} methods for use with Remote DPC
 *
 * <p>Methods called on this interface will behave as if they were called directly by the
 * RemoteDPC instance. Return values and exceptions will behave as expected.
 *
 * <p>Methods on this interface must match exactly the methods declared by
 * {@link DevicePolicyManager}, or else must be identical to a method declared by
 * {@link DevicePolicyManager} except that it excludes a {@code ComponentName admin} first argument
 * and must be annotated {@link RemoteDpcAutomaticAdmin}.
 *
 * <p>When using {@link RemoteDpcAutomaticAdmin}, there must also exist an identical method on the
 * interface which includes the {@code ComponentName admin} argument. The RemoteDPC component name
 * will be automatically provided when the {@link RemoteDpcAutomaticAdmin} annotated method is
 * called.
 */
@RemoteDpcManager(managerClass = DevicePolicyManager.class)
public interface RemoteDevicePolicyManager {

    /** See {@link DevicePolicyManager#isUsingUnifiedPassword(ComponentName)}. */
    boolean isUsingUnifiedPassword(@NonNull ComponentName admin);
    /** See {@link DevicePolicyManager#isUsingUnifiedPassword(ComponentName)}. */
    @RemoteDpcAutomaticAdmin boolean isUsingUnifiedPassword();

    /** See {@link DevicePolicyManager#getCurrentFailedPasswordAttempts()}. */
    int getCurrentFailedPasswordAttempts();


    /** See {@link DevicePolicyManager#setLockTaskPackages(ComponentName, String[])}. */
    void setLockTaskPackages(@NonNull ComponentName admin, @NonNull String[] packages);
    /** See {@link DevicePolicyManager#setLockTaskPackages(ComponentName, String[])}. */
    @RemoteDpcAutomaticAdmin void setLockTaskPackages(@NonNull String[] packages);

    /** See {@link DevicePolicyManager#getLockTaskPackages(ComponentName)}. */
    @NonNull String[] getLockTaskPackages(@NonNull ComponentName admin);
    /** See {@link DevicePolicyManager#getLockTaskPackages(ComponentName)}. */
    @RemoteDpcAutomaticAdmin @NonNull String[] getLockTaskPackages();

    /** See {@link DevicePolicyManager#setLockTaskFeatures(ComponentName, int)}. */
    void setLockTaskFeatures(
            @NonNull ComponentName admin, int flags);
    /** See {@link DevicePolicyManager#setLockTaskFeatures(ComponentName, int)}. */
    @RemoteDpcAutomaticAdmin void setLockTaskFeatures(int flags);

    /** See {@link DevicePolicyManager#getLockTaskFeatures(Component)}. */
    int getLockTaskFeatures(@NonNull ComponentName admin);
    /** See {@link DevicePolicyManager#getLockTaskFeatures(Component)}. */
    @RemoteDpcAutomaticAdmin int getLockTaskFeatures();

    /** See {@link DevicePolicyManager#addUserRestriction(ComponentName, String)}. */
    void addUserRestriction(@NonNull ComponentName admin, String key);
    /** See {@link DevicePolicyManager#addUserRestriction(ComponentName, String)}. */
    @RemoteDpcAutomaticAdmin void addUserRestriction(String key);

    /** See {@link DevicePolicyManager#clearUserRestriction(ComponentName, String)}. */
    void clearUserRestriction(@NonNull ComponentName admin, String key);
    /** See {@link DevicePolicyManager#clearUserRestriction(ComponentName, String)}. */
    @RemoteDpcAutomaticAdmin void clearUserRestriction(String key);
}
