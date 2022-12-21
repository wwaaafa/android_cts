/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.nene.accounts;

import static com.android.bedstead.nene.permissions.CommonPermissions.GET_ACCOUNTS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.READ_CONTACTS;

import android.accounts.AccountManager;
import android.accounts.RemoteAccountManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entry point to Nene Accounts.
 */
@Experimental
public final class Accounts {

    public static final Accounts sInstance = new Accounts();

    private Accounts() {

    }

    /**
     * Wrap the given {@link RemoteAccountManager} subclass to use Nene APIs.
     */
    public <E extends RemoteAccountManager> com.android.bedstead.nene.accounts.AccountManager<E> wrap(Class<E> clazz, UserReference user, E accountManager) {
        return new com.android.bedstead.nene.accounts.AccountManager<>(
                user, accountManager, accountManager);
    }

    /**
     * Wrap the given {@link android.accounts.AccountManager} to use Nene APIs.
     */
    public LocalAccountManager wrap(android.accounts.AccountManager accountManager) {
        return new LocalAccountManager(accountManager);
    }

    /**
     * Get all accounts of the given type.
     */
    public Set<AccountReference> getByType(String type) {
        return getByType(TestApis.users().instrumented(), type);
    }

    /**
     * Get all accounts of the given type.
     */
    public Set<AccountReference> getByType(UserReference user, String type) {
        // READ_CONTACTS allows access to accounts which manage contacts
        try (PermissionContext p = TestApis.permissions().withPermission(
                GET_ACCOUNTS, READ_CONTACTS, INTERACT_ACROSS_USERS_FULL)) {
            return Arrays.stream(accountManager(user).getAccountsByType(type))
                    .map((a) -> new AccountReference(user, a)).collect(Collectors.toSet());
        }
    }

    private AccountManager accountManager(UserReference user) {
        return TestApis.context().androidContextAsUser(user).getSystemService(AccountManager.class);
    }
}
