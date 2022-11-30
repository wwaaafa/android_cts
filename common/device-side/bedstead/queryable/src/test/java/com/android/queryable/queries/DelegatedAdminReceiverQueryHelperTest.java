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

package com.android.queryable.queries;

import static com.android.bedstead.nene.utils.ParcelTest.assertParcelsCorrectly;
import static com.android.queryable.queries.DelegatedAdminReceiverQuery.delegatedAdminReceiver;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DelegatedAdminReceiver;

import com.android.queryable.Queryable;
import com.android.queryable.info.DelegatedAdminReceiverInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DelegatedAdminReceiverQueryHelperTest {
    private final Queryable mQuery = null;

    private static final Class<? extends DelegatedAdminReceiver> CLASS_1 =
            DelegatedAdminReceiver.class;

    private static final DelegatedAdminReceiverInfo DELEGATED_ADMIN_RECEIVER_1_INFO =
            new DelegatedAdminReceiverInfo(CLASS_1);
    private static final DelegatedAdminReceiverInfo DELEGATED_ADMIN_RECEIVER_2_INFO =
            new DelegatedAdminReceiverInfo("different.class.name");

    @Test
    public void matches_noRestrictions_returnsTrue() {
        DelegatedAdminReceiverQueryHelper<Queryable> delegatedAdminReceiverQueryHelper =
                new DelegatedAdminReceiverQueryHelper<>(mQuery);

        assertThat(delegatedAdminReceiverQueryHelper.matches(DELEGATED_ADMIN_RECEIVER_1_INFO))
                .isTrue();
    }

    @Test
    public void matches_broadcastReceiver_doesMatch_returnsTrue() {
        DelegatedAdminReceiverQueryHelper<Queryable> delegatedAdminReceiverQueryHelper =
                new DelegatedAdminReceiverQueryHelper<>(mQuery);

        delegatedAdminReceiverQueryHelper.broadcastReceiver().receiverClass().isSameClassAs(CLASS_1);

        assertThat(delegatedAdminReceiverQueryHelper.matches(DELEGATED_ADMIN_RECEIVER_1_INFO))
                .isTrue();
    }

    @Test
    public void matches_broadcastReceiver_doesNotMatch_returnsFalse() {
        DelegatedAdminReceiverQueryHelper<Queryable> delegatedAdminReceiverQueryHelper =
                new DelegatedAdminReceiverQueryHelper<>(mQuery);

        delegatedAdminReceiverQueryHelper.broadcastReceiver().receiverClass().isSameClassAs(CLASS_1);

        assertThat(delegatedAdminReceiverQueryHelper.matches(DELEGATED_ADMIN_RECEIVER_2_INFO))
                .isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        DelegatedAdminReceiverQueryHelper<Queryable> delegatedAdminReceiverQueryHelper =
                new DelegatedAdminReceiverQueryHelper<>(mQuery);

        delegatedAdminReceiverQueryHelper.broadcastReceiver().receiverClass().isSameClassAs(CLASS_1);

        assertParcelsCorrectly(
                DelegatedAdminReceiverQueryHelper.class, delegatedAdminReceiverQueryHelper);
    }

    @Test
    public void delegatedAdminReceiverQueryBase_queries() {
        assertThat(delegatedAdminReceiver()
                .where().broadcastReceiver().receiverClass().isSameClassAs(CLASS_1)
                .matches(DELEGATED_ADMIN_RECEIVER_1_INFO)
        ).isTrue();
    }
}
