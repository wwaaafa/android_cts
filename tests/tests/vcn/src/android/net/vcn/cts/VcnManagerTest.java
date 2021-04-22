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

package android.net.vcn.cts;

import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_2048_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.ipsec.ike.ChildSaProposal;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.IkeTunnelConnectionParams;
import android.net.ipsec.ike.SaProposal;
import android.net.ipsec.ike.TunnelModeChildSessionParams;
import android.net.vcn.VcnConfig;
import android.net.vcn.VcnGatewayConnectionConfig;
import android.net.vcn.VcnManager;
import android.os.ParcelUuid;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.CarrierPrivilegeUtils;
import android.telephony.cts.util.SubscriptionGroupUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class VcnManagerTest {
    private static final String TAG = VcnManagerTest.class.getSimpleName();

    private static final int TIMEOUT_MS = 500;

    private static final Executor INLINE_EXECUTOR = Runnable::run;

    private static final String VCN_GATEWAY_CONNECTION_NAME = "test-vcn-gateway-connection";

    private final Context mContext;
    private final VcnManager mVcnManager;
    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyManager mTelephonyManager;
    private final ConnectivityManager mConnectivityManager;

    public VcnManagerTest() {
        mContext = InstrumentationRegistry.getContext();
        mVcnManager = mContext.getSystemService(VcnManager.class);
        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY));
    }

    private VcnConfig buildVcnConfig() {
        final IkeSaProposal ikeProposal =
                new IkeSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                ENCRYPTION_ALGORITHM_AES_GCM_12, SaProposal.KEY_LEN_AES_128)
                        .addDhGroup(DH_GROUP_2048_BIT_MODP)
                        .addPseudorandomFunction(PSEUDORANDOM_FUNCTION_AES128_XCBC)
                        .build();

        final String serverHostname = "2001:db8:1::100";
        final String testLocalId = "test.client.com";
        final String testRemoteId = "test.server.com";
        final byte[] psk = "psk".getBytes();

        final IkeSessionParams ikeParams =
                new IkeSessionParams.Builder()
                        .setServerHostname(serverHostname)
                        .addSaProposal(ikeProposal)
                        .setLocalIdentification(new IkeFqdnIdentification(testLocalId))
                        .setRemoteIdentification(new IkeFqdnIdentification(testRemoteId))
                        .setAuthPsk(psk)
                        .build();

        final ChildSaProposal childProposal =
                new ChildSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                ENCRYPTION_ALGORITHM_AES_GCM_12, SaProposal.KEY_LEN_AES_128)
                        .build();
        final TunnelModeChildSessionParams childParams =
                new TunnelModeChildSessionParams.Builder().addSaProposal(childProposal).build();

        final IkeTunnelConnectionParams tunnelParams =
                new IkeTunnelConnectionParams(ikeParams, childParams);

        final VcnGatewayConnectionConfig gatewayConnConfig =
                new VcnGatewayConnectionConfig.Builder(VCN_GATEWAY_CONNECTION_NAME, tunnelParams)
                        .addExposedCapability(NET_CAPABILITY_INTERNET)
                        .addRequiredUnderlyingCapability(NET_CAPABILITY_INTERNET)
                        .setRetryIntervalsMs(
                                new long[] {
                                    TimeUnit.SECONDS.toMillis(1),
                                    TimeUnit.MINUTES.toMillis(1),
                                    TimeUnit.HOURS.toMillis(1)
                                })
                        .setMaxMtu(1360)
                        .build();

        return new VcnConfig.Builder(mContext)
                .addGatewayConnectionConfig(gatewayConnConfig)
                .build();
    }

    @Test(expected = SecurityException.class)
    public void testSetVcnConfig_noCarrierPrivileges() throws Exception {
        mVcnManager.setVcnConfig(new ParcelUuid(UUID.randomUUID()), buildVcnConfig());
    }

    @Test
    public void testSetVcnConfig_withCarrierPrivileges() throws Exception {
        final int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        CarrierPrivilegeUtils.withCarrierPrivileges(mContext, dataSubId, () -> {
            SubscriptionGroupUtils.withEphemeralSubscriptionGroup(mContext, dataSubId, (subGrp) -> {
                mVcnManager.setVcnConfig(subGrp, buildVcnConfig());
            });
        });

        assertFalse(mTelephonyManager.createForSubscriptionId(dataSubId).hasCarrierPrivileges());
    }

    @Test(expected = SecurityException.class)
    public void testClearVcnConfig_noCarrierPrivileges() throws Exception {
        mVcnManager.clearVcnConfig(new ParcelUuid(UUID.randomUUID()));
    }

    @Test
    public void testClearVcnConfig_withCarrierPrivileges() throws Exception {
        final int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        CarrierPrivilegeUtils.withCarrierPrivileges(mContext, dataSubId, () -> {
            SubscriptionGroupUtils.withEphemeralSubscriptionGroup(mContext, dataSubId, (subGrp) -> {
                mVcnManager.clearVcnConfig(subGrp);
            });
        });
    }

    /** Test implementation of VcnNetworkPolicyChangeListener for verification purposes. */
    private static class TestVcnNetworkPolicyChangeListener
            implements VcnManager.VcnNetworkPolicyChangeListener {
        private final CompletableFuture<Void> mFutureOnPolicyChanged = new CompletableFuture<>();

        @Override
        public void onPolicyChanged() {
            mFutureOnPolicyChanged.complete(null /* unused */);
        }

        public void awaitOnPolicyChanged() throws Exception {
            mFutureOnPolicyChanged.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    @Test(expected = SecurityException.class)
    public void testAddVcnNetworkPolicyChangeListener_noNetworkFactoryPermission()
            throws Exception {
        final TestVcnNetworkPolicyChangeListener listener =
                new TestVcnNetworkPolicyChangeListener();

        try {
            mVcnManager.addVcnNetworkPolicyChangeListener(INLINE_EXECUTOR, listener);
        } finally {
            mVcnManager.removeVcnNetworkPolicyChangeListener(listener);
        }
    }

    @Test
    public void testRemoveVcnNetworkPolicyChangeListener_noNetworkFactoryPermission() {
        final TestVcnNetworkPolicyChangeListener listener =
                new TestVcnNetworkPolicyChangeListener();

        mVcnManager.removeVcnNetworkPolicyChangeListener(listener);
    }

    @Test(expected = SecurityException.class)
    public void testApplyVcnNetworkPolicy_noNetworkFactoryPermission() throws Exception {
        final NetworkCapabilities nc = new NetworkCapabilities.Builder().build();
        final LinkProperties lp = new LinkProperties();

        mVcnManager.applyVcnNetworkPolicy(nc, lp);
    }

    /** Test implementation of VcnStatusCallback for verification purposes. */
    private static class TestVcnStatusCallback extends VcnManager.VcnStatusCallback {
        private final CompletableFuture<Integer> mFutureOnStatusChanged =
                new CompletableFuture<>();
        private final CompletableFuture<GatewayConnectionError> mFutureOnGatewayConnectionError =
                new CompletableFuture<>();

        @Override
        public void onStatusChanged(int statusCode) {
            mFutureOnStatusChanged.complete(statusCode);
        }

        @Override
        public void onGatewayConnectionError(
                @NonNull String gatewayConnectionName, int errorCode, @Nullable Throwable detail) {
            mFutureOnGatewayConnectionError.complete(
                    new GatewayConnectionError(gatewayConnectionName, errorCode, detail));
        }

        public int awaitOnStatusChanged() throws Exception {
            return mFutureOnStatusChanged.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        public GatewayConnectionError awaitOnGatewayConnectionError() throws Exception {
            return mFutureOnGatewayConnectionError.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** Info class for organizing VcnStatusCallback#onGatewayConnectionError response data. */
    private static class GatewayConnectionError {
        @NonNull public final String gatewayConnectionName;
        public final int errorCode;
        @Nullable public final Throwable detail;

        public GatewayConnectionError(
                @NonNull String gatewayConnectionName, int errorCode, @Nullable Throwable detail) {
            this.gatewayConnectionName = gatewayConnectionName;
            this.errorCode = errorCode;
            this.detail = detail;
        }
    }

    private void registerVcnStatusCallbackForSubId(
            @NonNull TestVcnStatusCallback callback, int subId) throws Exception {
        CarrierPrivilegeUtils.withCarrierPrivileges(mContext, subId, () -> {
            SubscriptionGroupUtils.withEphemeralSubscriptionGroup(mContext, subId, (subGrp) -> {
                mVcnManager.registerVcnStatusCallback(subGrp, INLINE_EXECUTOR, callback);
            });
        });
    }

    @Test
    public void testRegisterVcnStatusCallback() throws Exception {
        final TestVcnStatusCallback callback = new TestVcnStatusCallback();
        final int subId = SubscriptionManager.getDefaultSubscriptionId();

        try {
            registerVcnStatusCallbackForSubId(callback, subId);

            final int statusCode = callback.awaitOnStatusChanged();
            assertEquals(VcnManager.VCN_STATUS_CODE_NOT_CONFIGURED, statusCode);
        } finally {
            mVcnManager.unregisterVcnStatusCallback(callback);
        }
    }

    @Test
    public void testRegisterVcnStatusCallback_reuseUnregisteredCallback() throws Exception {
        final TestVcnStatusCallback callback = new TestVcnStatusCallback();
        final int subId = SubscriptionManager.getDefaultSubscriptionId();

        try {
            registerVcnStatusCallbackForSubId(callback, subId);
            mVcnManager.unregisterVcnStatusCallback(callback);
            registerVcnStatusCallbackForSubId(callback, subId);
        } finally {
            mVcnManager.unregisterVcnStatusCallback(callback);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testRegisterVcnStatusCallback_duplicateRegister() throws Exception {
        final TestVcnStatusCallback callback = new TestVcnStatusCallback();
        final int subId = SubscriptionManager.getDefaultSubscriptionId();

        try {
            registerVcnStatusCallbackForSubId(callback, subId);
            registerVcnStatusCallbackForSubId(callback, subId);
        } finally {
            mVcnManager.unregisterVcnStatusCallback(callback);
        }
    }

    @Test
    public void testUnregisterVcnStatusCallback() throws Exception {
        final TestVcnStatusCallback callback = new TestVcnStatusCallback();

        mVcnManager.unregisterVcnStatusCallback(callback);
    }
}
