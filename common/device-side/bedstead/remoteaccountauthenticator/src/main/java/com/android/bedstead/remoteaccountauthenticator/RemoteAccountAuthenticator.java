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

package com.android.bedstead.remoteaccountauthenticator;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.accounts.RemoteAccountManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accounts.AccountBuilder;
import com.android.bedstead.nene.accounts.AccountReference;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.bedstead.testapp.TestAppProvider;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Entry point to RemoteAccountAuthenticator which is used to add and remove accounts on different
 * users.
 */
public final class RemoteAccountAuthenticator extends TestAppInstance {

    // TODO: Query account types from xml
    private static final Set<String> ACCOUNT_TYPES = Set.of(
            "com.android.bedstead.remoteaccountauthenticator.account"
    );

    private static final String REMOTE_ACCOUNT_AUTHENTICATOR_PACKAGE_NAME =
            "com.android.RemoteAccountAuthenticator";

    private static final TestAppProvider sTestAppProvider = new TestAppProvider();
    private static final TestApp REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP = sTestAppProvider.query()
            .wherePackageName().isEqualTo(REMOTE_ACCOUNT_AUTHENTICATOR_PACKAGE_NAME)
            .get();

    private RemoteAccountAuthenticator(UserReference user) {
        super(REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP, user);
    }

    /**
     * Install the remote account authenticator on the instrumented user.
     */
    public static RemoteAccountAuthenticator install() {
        return install(TestApis.users().instrumented());
    }

    /**
     * Install the remote account authenticator on the given user.
     */
    public static RemoteAccountAuthenticator install(UserReference user) {
        REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP.install(user);

        return new RemoteAccountAuthenticator(user);
    }

    /**
     * Get all accounts owned by this RemoteAccountAuthenticator.
     */
    public Set<AccountReference> accounts() {
        return ACCOUNT_TYPES.stream()
                .flatMap(t -> Stream.of(accountManager().getAccountsByType(t)))
                .map(a -> AccountReference.of(user(), a))
                .collect(Collectors.toSet());
    }

    /**
     * Add an account.
     *
     * <p>By default, this will use any supported account type.
     */
    public AccountBuilder addAccount() {
        return TestApis.accounts().wrap(RemoteAccountManager.class,
                        user(), accountManager()).addAccount()
                .type(ACCOUNT_TYPES.stream().findFirst().get())
                .removeFunction(this::remove);
    }

    /**
     * Remove this account.
     */
    public void remove(AccountReference account) {
        // TODO: Check result
        try {
            accountManager().removeAccount(
                    account.account(),
                    /* activity= */ null,
                    /* callback= */ null,
                    /* handler= */ null
            ).getResult();
        } catch (AuthenticatorException | IOException | OperationCanceledException e) {
            throw new NeneException("Error removing account " + account, e);
        }
    }
}
