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

import android.os.Bundle;

import com.android.bedstead.nene.utils.Poll;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Class used set properties for adding an account.
 */
public final class AccountBuilder {

    private final AccountManager<?> mAccountManager;
    private String mType;
    private String mName = UUID.randomUUID().toString();
    private Consumer<AccountReference> mRemoveFunction;

    AccountBuilder(AccountManager<?> accountManager) {
        mAccountManager = accountManager;
    }

    /**
     * Set the type of the account.
     */
    public AccountBuilder type(String type) {
        mType = type;
        return this;
    }

    /**
     * Set the function to be called when a test tries to remove this account.
     */
    public AccountBuilder removeFunction(Consumer<AccountReference> removeFunction) {
        mRemoveFunction = removeFunction;
        return this;
    }

    /**
     * Set the name of the account. Defaults to a random name.
     */
    public AccountBuilder name(String name) {
        mName = name;
        return this;
    }

    /**
     * Add this account.
     */
    public AccountReference add() {
        return AccountReference.fromAddResult(
                mAccountManager.user(), addAccount(), mRemoveFunction);
    }

    /**
     * Blocks until an account of {@code type} is added.
     */
    // TODO(b/199077745): Remove poll once AccountManager race condition is fixed
    private Bundle addAccount() {
        return Poll.forValue("created account bundle", this::addAccountOnce)
                .toNotBeNull()
                .errorOnFail()
                .await();
    }

    private Bundle addAccountOnce() throws Exception {
        Bundle options = new Bundle();
        options.putString("name", mName);
        return mAccountManager.accountManager().addAccount(
                mType,
                /* authTokenType= */ null,
                /* requiredFeatures= */ null,
                /* addAccountOptions= */ options,
                /* activity= */ null,
                /* callback= */ null,
                /* handler= */ null).getResult();
    }
}
