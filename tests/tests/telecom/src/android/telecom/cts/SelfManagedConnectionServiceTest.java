/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.telecom.cts;

import static android.media.AudioManager.MODE_IN_CALL;
import static android.media.AudioManager.MODE_IN_COMMUNICATION;
import static android.telecom.cts.TestUtils.SELF_MANAGED_ACCOUNT_LABEL;
import static android.telecom.cts.TestUtils.TEST_SELF_MANAGED_HANDLE_1;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;
import static android.telecom.cts.TestUtils.waitOnAllHandlers;

import static org.junit.Assert.assertNotEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.telecom.CallEndpointException;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.InCallService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.cts.selfmanagedcstestapp.ICtsSelfManagedConnectionServiceControl;
import android.telecom.cts.selfmanagedcstestappone.CtsSelfManagedConnectionServiceControlOne;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * CTS tests for the self-managed {@link android.telecom.ConnectionService} APIs.
 * For more information about these APIs, see {@link android.telecom}, and
 * {@link android.telecom.PhoneAccount#CAPABILITY_SELF_MANAGED}.
 */

public class SelfManagedConnectionServiceTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = "SelfManagedConnectionServiceTest";
    private static final long TIMEOUT = 3000L;
    private Uri TEST_ADDRESS_1 = Uri.fromParts("sip", "call1@test.com", null);
    private Uri TEST_ADDRESS_2 = Uri.fromParts("tel", "6505551212", null);
    private Uri TEST_ADDRESS_3 = Uri.fromParts("tel", "6505551213", null);
    private Uri TEST_ADDRESS_4 = Uri.fromParts(TestUtils.TEST_URI_SCHEME, "fizzle_schmozle", null);

    private static final String SELF_MANAGED_CS_CONTROL =
            "android.telecom.cts.selfmanagedcstestapp.ACTION_SELF_MANAGED_CS_CONTROL";

    private static final String SELF_MANAGED_CS_PKG_1 =
            CtsSelfManagedConnectionServiceControlOne.class.getPackage().getName();
    private static final ComponentName SELF_MANAGED_CS_1 = ComponentName.createRelative(
            SELF_MANAGED_CS_PKG_1, CtsSelfManagedConnectionServiceControlOne.class.getName());

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        mContext = getInstrumentation().getContext();
        if (mShouldTestTelecom) {
            // Register and enable the CTS ConnectionService; we want to be able to test a managed
            // ConnectionService alongside a self-managed ConnectionService.
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);

            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1);
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_2);
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_3);
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_4);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        CtsSelfManagedConnectionService connectionService =
                CtsSelfManagedConnectionService.getConnectionService();
        if (connectionService != null) {
            connectionService.tearDown();
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_SELF_MANAGED_HANDLE_1);
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_SELF_MANAGED_HANDLE_2);
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_SELF_MANAGED_HANDLE_3);
        }
    }

    private class TestServiceConnection implements ServiceConnection {
        private IBinder mService;
        private CountDownLatch mLatch = new CountDownLatch(1);
        private boolean mIsConnected;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service Connected: " + componentName);
            mService = service;
            mIsConnected = true;
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }

        public IBinder getService() {
            return mService;
        }

        public boolean waitBind() {
            try {
                mLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
                return mIsConnected;
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    private TestServiceConnection setUpControl(String action, ComponentName componentName) {
        Intent bindIntent = new Intent(action);
        bindIntent.setComponent(componentName);

        TestServiceConnection serviceConnection = new TestServiceConnection();
        mContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!serviceConnection.waitBind()) {
            fail("fail bind to service");
        }
        return serviceConnection;
    }

    /**
     * Tests {@link TelecomManager#getSelfManagedPhoneAccounts()} API to ensure it returns a list of
     * the registered self-managed {@link android.telecom.PhoneAccount}s.
     */
    public void testTelecomManagerGetSelfManagedPhoneAccounts() {
        if (!mShouldTestTelecom) {
            return;
        }

        List<PhoneAccountHandle> phoneAccountHandles =
                mTelecomManager.getSelfManagedPhoneAccounts();

        assertTrue(phoneAccountHandles.contains(TestUtils.TEST_SELF_MANAGED_HANDLE_1));
        assertTrue(phoneAccountHandles.contains(TestUtils.TEST_SELF_MANAGED_HANDLE_2));
        assertTrue(phoneAccountHandles.contains(TestUtils.TEST_SELF_MANAGED_HANDLE_3));
        assertFalse(phoneAccountHandles.contains(TestUtils.TEST_PHONE_ACCOUNT_HANDLE));
    }

    /**
     * Tests the ability to successfully register a self-managed
     * {@link android.telecom.PhoneAccount}.
     * <p>
     * It should be possible to register self-managed Connection Services which suppor the TEL, SIP,
     * or other URI schemes.
     */
    public void testRegisterSelfManagedConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }
        verifyAccountRegistration(TestUtils.TEST_SELF_MANAGED_HANDLE_1,
                TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1);
        verifyAccountRegistration(TestUtils.TEST_SELF_MANAGED_HANDLE_2,
                TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_2);
        verifyAccountRegistration(TestUtils.TEST_SELF_MANAGED_HANDLE_3,
                TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_3);
    }

    public void testSelfManagedConnectionServiceRegistrationUnmodifiable() {
        if (!mShouldTestTelecom) {
            return;
        }

        verifyAccountRegistration(TestUtils.TEST_SELF_MANAGED_HANDLE_1,
                TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1);
        PhoneAccount newPhoneAccount = PhoneAccount.builder(
                        TEST_SELF_MANAGED_HANDLE_1, SELF_MANAGED_ACCOUNT_LABEL)
                .setAddress(Uri.parse("sip:test@test.com"))
                .setSubscriptionAddress(Uri.parse("sip:test@test.com"))
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER
                        | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                        | PhoneAccount.CAPABILITY_VIDEO_CALLING)
                .setHighlightColor(Color.BLUE)
                .setShortDescription(SELF_MANAGED_ACCOUNT_LABEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                .build();
        try {
            mTelecomManager.registerPhoneAccount(newPhoneAccount);
            fail("Self-managed phone account can be replaced to a call provider phone account!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    private void verifyAccountRegistration(PhoneAccountHandle handle, PhoneAccount phoneAccount) {
        // The phone account is registered in the setup method.
        assertPhoneAccountRegistered(handle);
        assertPhoneAccountEnabled(handle);
        PhoneAccount registeredAccount = mTelecomManager.getPhoneAccount(handle);

        // It should exist and be the same as the previously registered one.
        assertNotNull(registeredAccount);

        // We cannot just check for equality of the PhoneAccount since the one we registered is not
        // enabled, and the one we get back after registration is.
        assertPhoneAccountEquals(phoneAccount, registeredAccount);

        // An important assumption is that self-managed PhoneAccounts are automatically
        // enabled by default.
        assertTrue("Self-managed PhoneAccounts must be enabled by default.",
                registeredAccount.isEnabled());
    }

    /**
     * This test ensures that a {@link android.telecom.PhoneAccount} declared as self-managed cannot
     * but is also registered as a call provider is not permitted.
     *
     * A self-managed {@link android.telecom.PhoneAccount} cannot also be a call provider.
     */
    public void testRegisterCallCapableSelfManagedConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // Attempt to register both a call provider and self-managed account.
        PhoneAccount toRegister = TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1.toBuilder()
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        registerAndExpectFailure(toRegister);
    }

    /**
     * This test ensures that a {@link android.telecom.PhoneAccount} declared as self-managed cannot
     * but is also registered as a sim subscription is not permitted.
     *
     * A self-managed {@link android.telecom.PhoneAccount} cannot also be a SIM subscription.
     */
    public void testRegisterSimSelfManagedConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // Attempt to register both a call provider and self-managed account.
        PhoneAccount toRegister = TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1.toBuilder()
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();

        registerAndExpectFailure(toRegister);
    }

    /**
     * This test ensures that a {@link android.telecom.PhoneAccount} declared as self-managed cannot
     * but is also registered as a connection manager is not permitted.
     *
     * A self-managed {@link android.telecom.PhoneAccount} cannot also be a connection manager.
     */
    public void testRegisterConnectionManagerSelfManagedConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // Attempt to register both a call provider and self-managed account.
        PhoneAccount toRegister = TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1.toBuilder()
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                .build();

        registerAndExpectFailure(toRegister);
    }

    /**
     * Attempts to register a {@link android.telecom.PhoneAccount}, expecting a security exception
     * which indicates that invalid capabilities were specified.
     *
     * @param toRegister The PhoneAccount to register.
     */
    private void registerAndExpectFailure(PhoneAccount toRegister) {
        try {
            mTelecomManager.registerPhoneAccount(toRegister);
        } catch (SecurityException se) {
            assertEquals("Self-managed ConnectionServices cannot also be call capable, " +
                    "connection managers, or SIM accounts.", se.getMessage());
            return;
        }
        fail("Expected SecurityException");
    }

    /**
     * Tests ability to add a new self-managed incoming connection.
     */
    public void testAddSelfManagedIncomingConnection() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        addAndVerifyIncomingCall(TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        addAndVerifyIncomingCall(TestUtils.TEST_SELF_MANAGED_HANDLE_2, TEST_ADDRESS_3);
        addAndVerifyIncomingCall(TestUtils.TEST_SELF_MANAGED_HANDLE_3, TEST_ADDRESS_4);
    }

    private void addAndVerifyIncomingCall(PhoneAccountHandle handle, Uri address)
            throws Exception {
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager, handle, address);

        // Ensure Telecom bound to the self managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(address);
        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        // Expect callback indicating that UI should be shown.
        connection.getOnShowIncomingUiInvokeCounter().waitForCount(1);
        setActiveAndVerify(connection);

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(connection.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        // Expect there to be no managed calls at the moment.
        assertFalse(mTelecomManager.isInManagedCall());
        assertTrue(mTelecomManager.isInCall());

        setDisconnectedAndVerify(connection, isLoggedCall(handle), callLogEntryLatch);
    }

    /**
     * Tests ensures that Telecom disallow to place outgoing self-managed call when the ongoing
     * managed call can not be held.
     */
    public void testDisallowOutgoingCallWhileOngoingManagedCallCanNotBeHeld() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // GIVEN an ongoing managed call that can not be held
        addAndVerifyNewIncomingCall(createTestNumber(), null);
        Connection connection = verifyConnectionForIncomingCall();
        int capabilities = connection.getConnectionCapabilities();
        capabilities &= ~Connection.CAPABILITY_HOLD;
        connection.setConnectionCapabilities(capabilities);

        // answer the incoming call
        MockInCallService inCallService = mInCallCallbacks.getService();
        Call call = inCallService.getLastCall();
        call.answer(VideoProfile.STATE_AUDIO_ONLY);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        // WHEN place a self-managed outgoing call
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);

        // THEN the new outgoing call is failed.
        CtsSelfManagedConnectionService.waitForBinding();
        assertTrue(CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                CtsSelfManagedConnectionService.CREATE_OUTGOING_CONNECTION_FAILED_LOCK));

        assertIsOutgoingCallPermitted(false, TestUtils.TEST_SELF_MANAGED_HANDLE_1);
    }

    /**
     * Tests ensures that Telecom update outgoing self-managed call state disconnected when
     * remote side call is rejected.
     */
    public void testOutgoingCallRejectedByRemoteParty() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_2, TEST_ADDRESS_2);

        // Ensure Telecom bound to the self managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_2);
        assertNotNull("Self-Managed Connection should NOT be null.", connection);
        assertTrue("Self-Managed Connection should be outgoing.", !connection.isIncomingCall());

        // The self-managed ConnectionService must NOT have been prompted to show its incoming call
        // UI for an outgoing call.
        assertEquals(connection.getOnShowIncomingUiInvokeCounter().getInvokeCount(), 0);

        // Expect that the new outgoing call broadcast did not fire for the self-managed calls.
        assertFalse(NewOutgoingCallBroadcastReceiver.isNewOutgoingCallBroadcastReceived());

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        assertConnectionState(connection, Connection.STATE_INITIALIZING);
        assertCallState(mInCallCallbacks.getService().getLastCall(), Call.STATE_DIALING);

        connection.setDialing();
        assertConnectionState(connection, Connection.STATE_DIALING);

        connection.setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));

        assertConnectionState(connection, Connection.STATE_DISCONNECTED);
        assertCallState(mInCallCallbacks.getService().getLastCall(), Call.STATE_DISCONNECTED);

        setDisconnectedAndVerify(connection, isLoggedCall(TestUtils.TEST_SELF_MANAGED_HANDLE_2),
                callLogEntryLatch);
    }

    /**
     * Tests ensures that Telecom update self-managed call mute state when user sets mute option.
     */
    public void testSelfManagedCallMuteAndUnmute() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        SelfManagedConnection connection = null;

        try {
            connection = placeSelfManagedCallAndGetConnection(TestUtils.TEST_SELF_MANAGED_HANDLE_2,
                    TEST_ADDRESS_2);

            final MockInCallService inCallService = getInCallService();
            final Call call = inCallService.getLastCall();

            assertMuteState(connection, false);

            // Explicitly call super implementation to enable detection of CTS coverage
            ((InCallService) inCallService).setMuted(true);

            assertMuteState(connection, true);
            assertMuteState(inCallService, true);

            inCallService.setMuted(false);
            assertMuteState(connection, false);
            assertMuteState(inCallService, false);
        } finally {
            if (connection != null) {
                // disconnect call
                connection.disconnectAndDestroy();
                // verify the call was disconnected
                assertIsInCall(false);
                assertIsInManagedCall(false);
            }
        }
    }

    /**
     * Tests ensures that Telecom update outgoing self-managed video call video state to false when
     * remote side call is picked only for audio.
     */
    public void testVideoCallStateDowngradeToAudio() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_2, TEST_ADDRESS_2,
                VideoProfile.STATE_BIDIRECTIONAL);

        // Ensure Telecom bound to the self managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_2);
        assertNotNull("Self-Managed Connection should NOT be null.", connection);
        assertTrue("Self-Managed Connection should be outgoing.", !connection.isIncomingCall());

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        connection.setDialing();
        assertCallState(call, Call.STATE_DIALING);

        assertVideoState(call, VideoProfile.STATE_BIDIRECTIONAL);

        connection.setVideoState(VideoProfile.STATE_AUDIO_ONLY);

        assertEquals(VideoProfile.STATE_AUDIO_ONLY, connection.getVideoState());

        connection.setActive();
        assertCallState(call, Call.STATE_ACTIVE);

        assertVideoState(call, VideoProfile.STATE_AUDIO_ONLY);
        setDisconnectedAndVerify(connection, isLoggedCall(TestUtils.TEST_SELF_MANAGED_HANDLE_2),
                callLogEntryLatch);
    }

    /**
     * Tests ability to add a new self-managed outgoing connection.
     * <p>
     * A self-managed {@link ConnectionService} shall be able to place an outgoing call to tel or
     * sip {@link Uri}s without being interrupted by system UX or other Telephony-related logic.
     */
    public void testAddSelfManagedOutgoingConnection() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        assertIsOutgoingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_1);
        placeAndVerifyOutgoingCall(TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);

        assertIsOutgoingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_2);
        placeAndVerifyOutgoingCall(TestUtils.TEST_SELF_MANAGED_HANDLE_2, TEST_ADDRESS_3);

        assertIsOutgoingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_3);
        placeAndVerifyOutgoingCall(TestUtils.TEST_SELF_MANAGED_HANDLE_3, TEST_ADDRESS_4);
    }

    /**
     * Ensure that a self-managed call which does not declare
     * {@link PhoneAccount#EXTRA_LOG_SELF_MANAGED_CALLS} will NOT be logged in the call log.
     * We do this as a separate case because we don't want on the logging latch used in the other
     * tests if we don't expect a call to be logged (it would make the CTS mighty slow).
     * @throws Exception
     */
    public void testSelfManagedCallNotLogged() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // First, complete the call which should not be logged.
        Uri unloggedAddress = getTestNumber();
        placeAndVerifyOutgoingCall(TestUtils.TEST_SELF_MANAGED_HANDLE_1, unloggedAddress);

        // Next, place a call which we DO expect to be logged.
        Uri loggedAddress = getTestNumber();
        placeAndVerifyOutgoingCall(TestUtils.TEST_SELF_MANAGED_HANDLE_2, loggedAddress);

        // The verification code for un-logged numbers doesn't actually wait on the call log latch
        // since it would cause the tests to all run slow.  However, since we just logged two calls
        // and the second one would have triggered the call log latch, we can assume that the last
        // two entries in the call log should:
        // 1. NOT contain the un-logged call.
        // 2. CONTAIN the logged call.

        // Lets get the last two entries in the log in descending order by ID.  This means that the
        // logged call will be first.
        Cursor callsCursor = mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI, null,
                null, null, CallLog.Calls._ID + " DESC limit 2;");
        int numberIndex = callsCursor.getColumnIndex(CallLog.Calls.NUMBER);

        // Check that we see the expected log call.
        if (callsCursor.moveToNext()) {
            String number = callsCursor.getString(numberIndex);
            assertEquals(loggedAddress.getSchemeSpecificPart(), number);
        } else {
            fail("Expected a logged call.");
        }

        // Now check to ensure the call we DID NOT want to have logged is indeed not logged.
        if (callsCursor.moveToNext()) {
            // Something else was logged; make sure we did not log the call where the PhoneAccount
            // does not indicate calls should be logged.
            String number = callsCursor.getString(numberIndex);
            assertNotEquals(unloggedAddress.getSchemeSpecificPart(), number);
        } else {
            // This is great; there was nothing else in the call log!
        }

    }

    private void placeAndVerifyOutgoingCall(PhoneAccountHandle handle, Uri address) throws Exception {

        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager, handle, address);

        // Ensure Telecom bound to the self managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(address);
        assertNotNull("Self-Managed Connection should NOT be null.", connection);
        assertTrue("Self-Managed Connection should be outgoing.", !connection.isIncomingCall());

        // The self-managed ConnectionService must NOT have been prompted to show its incoming call
        // UI for an outgoing call.
        assertEquals(connection.getOnShowIncomingUiInvokeCounter().getInvokeCount(), 0);

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        setActiveAndVerify(connection);

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(connection.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        // Expect there to be no managed calls at the moment.
        assertFalse(mTelecomManager.isInManagedCall());
        // But there should be a call (including self-managed).
        assertTrue(mTelecomManager.isInCall());

        // Expect that the new outgoing call broadcast did not fire for the self-managed calls.
        assertFalse(NewOutgoingCallBroadcastReceiver.isNewOutgoingCallBroadcastReceived());

        setDisconnectedAndVerify(connection, isLoggedCall(handle), callLogEntryLatch);
    }

    /**
     * Test the scenario where OEM tries to toggle {@link android.telecom.Connection
     * #setAudioModeIsVoip(boolean)} to false. Telecom should restrict this action as it can cause
     * unwanted behavior such as audio issues.
     *
     * @throws Exception ;should not hit exception.
     */
    @ApiTest(apis = {"android.telecom.Connection#setAudioModeIsVoip"})
    public void testAudioModeRemainsVoip() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        SelfManagedConnection connection = null;

        try {
            connection = placeSelfManagedCallAndGetConnection(TEST_SELF_MANAGED_HANDLE_1,
                    TEST_ADDRESS_1);

            // verify audio mode is voip
            assertTrue(connection.getAudioModeIsVoip());

            // send request to change audioModeIsVoip to FALSE
            connection.setAudioModeIsVoip(false);

            // verify audio mode is STILL voip (expected)
            assertTrue(connection.getAudioModeIsVoip());
        } finally {
            if (connection != null) {
                // disconnect call
                connection.disconnectAndDestroy();
                // verify the call was disconnected
                assertIsInCall(false);
                assertIsInManagedCall(false);
            }
        }
    }

    /**
     * Test the scenario where a user starts a self-managed call and while that call is active,
     * starts a sim based call.  This test verifies the audio mode is correct at every point.
     *
     * @throws Exception ;should not hit exception.
     */
    @ApiTest(apis = {"android.telecom.Connection#setAudioModeIsVoip"})
    public void testSelfManagedAndSimBasedCallSwapping() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        SelfManagedConnection selfManagedConnection = null;

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_SIM_PHONE_ACCOUNT.getAccountHandle());

        try {
            // 1. start a self-managed call
            selfManagedConnection = placeSelfManagedCallAndGetConnection(TEST_SELF_MANAGED_HANDLE_1,
                    TEST_ADDRESS_1);

            // 2. assert self-managed call is active
            setActiveAndVerify(selfManagedConnection);

            // 3. assert audio mode is MODE_IN_COMMUNICATION
            AudioManager audioManager = mContext.getSystemService(AudioManager.class);
            assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

            // 4. start an incoming SIM based call
            placeAndVerifyCall(extras);
            Connection simBasedConnection = verifyConnectionForOutgoingCall();

            // 5. assert incoming call is active
            Call outgoingCall = getInCallService().getLastCall();
            simBasedConnection.setActive();
            assertCallState(outgoingCall, Call.STATE_ACTIVE);

            // 6. assert audio mode id MODE_IN_CALL
            assertAudioMode(audioManager, MODE_IN_CALL);

            // 7. end incoming SIM based call
            simBasedConnection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            simBasedConnection.destroy();

            // 8. assert the incoming call is disconnected
            assertCallState(getInCallService().getLastCall(), Call.STATE_DISCONNECTED);

            // 9. un-hold and assert self-managed call becomes active
            setActiveAndVerify(selfManagedConnection);

            // 10. assert audio mode is MODE_IN_COMMUNICATION
            assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        } finally {
            if (selfManagedConnection != null) {
                // disconnect call
                selfManagedConnection.disconnectAndDestroy();
            }
        }
    }

    /**
     * verify TelecomManager#acceptRingingCall does not change the state of a self-managed call from
     * ringing to active. In short, TelecomManager#acceptRingingCall should not change the state
     * of any self-manged connection.
     *
     * @throws Exception; should not throw exception
     */
    @ApiTest(apis = {"android.telecom.TelecomManager#acceptRingingCall"})
    public void testAcceptRingingCallOnSingleSelfManagedCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        SelfManagedConnection selfManagedConnection = null;

        try {
            // verify handle can receive incoming self-manged call on the handle
            assertIsIncomingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_2);

            // start new incoming self-managed call
            TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                    TestUtils.TEST_SELF_MANAGED_HANDLE_2, TEST_ADDRESS_2);
            selfManagedConnection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_2);

            // verify the incoming self-managed call is ringing
            TestUtils.waitOnAllHandlers(getInstrumentation());
            assertEquals(Call.STATE_RINGING, selfManagedConnection.getState());

            // try to accept it but the expectation is Telecom will not answer the ringing call
            mTelecomManager.acceptRingingCall();

            // assert there was no change in the ongoing call
            TestUtils.waitOnAllHandlers(getInstrumentation());
            assertEquals(Call.STATE_RINGING, selfManagedConnection.getState());
        } finally {
            if (selfManagedConnection != null) {
                selfManagedConnection.disconnectAndDestroy();
            }
        }
    }


    /**
     * verify TelecomManager#acceptRingingCall does not change the state of an active self-managed
     * call (by holding it) in favor of a new ringing self-managed call that comes in.  In short,
     * TelecomManager#acceptRingingCall should not change the state of any self-manged connection.
     *
     * @throws Exception; should not throw exception
     */
    @ApiTest(apis = {"android.telecom.TelecomManager#acceptRingingCall"})
    public void testAcceptRingingCallOnMultipleSelfManagedCalls() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        SelfManagedConnection outgoing_SM_connection = null;
        SelfManagedConnection incoming_SM_connection = null;

        try {
            // verify both handles can place calls
            assertIsOutgoingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_1);
            assertIsIncomingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_2);

            // create an outgoing self-managed call
            outgoing_SM_connection = placeSelfManagedCallAndGetConnection(
                    TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
            assertEquals(outgoing_SM_connection.getState(), Call.STATE_ACTIVE);

            // start new incoming call
            TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                    TestUtils.TEST_SELF_MANAGED_HANDLE_2, TEST_ADDRESS_2);
            incoming_SM_connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_2);

            // verify the incoming self-managed call is ringing
            TestUtils.waitOnAllHandlers(getInstrumentation());
            assertEquals(Call.STATE_RINGING, incoming_SM_connection.getState());

            // try to accept it but the expectation is Telecom will not answer the ringing call
            mTelecomManager.acceptRingingCall();

            // assert there was no change in the 2 ongoing calls
            TestUtils.waitOnAllHandlers(getInstrumentation());
            assertEquals(Call.STATE_RINGING, incoming_SM_connection.getState());
            assertEquals(Call.STATE_ACTIVE, outgoing_SM_connection.getState());
        } finally {
            if (outgoing_SM_connection != null) {
                outgoing_SM_connection.disconnectAndDestroy();
            }
            if (incoming_SM_connection != null) {
                incoming_SM_connection.disconnectAndDestroy();
            }
        }
    }

    /**
     * Verify TelecomManager#endCall cannot end an active self-managed call.
     *
     * @throws Exception; should not throw exception
     */
    @ApiTest(apis = {"android.telecom.TelecomManager#endCall"})
    public void testEndCallOnSelfManagedCallOnActiveCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        SelfManagedConnection selfManagedConnection = null;

        try {
            // start a self-managed call
            assertIsOutgoingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_1);
            selfManagedConnection =
                    placeSelfManagedCallAndGetConnection(TEST_SELF_MANAGED_HANDLE_1,
                            TEST_ADDRESS_1);

            // set the self-managed call active and verify
            setActiveAndVerify(selfManagedConnection);

            // try to end it but the expectation is Telecom cannot end the self-managed call
            assertFalse(mTelecomManager.endCall());
            TestUtils.waitOnAllHandlers(getInstrumentation());
            assertEquals(Call.STATE_ACTIVE, selfManagedConnection.getState());

        } finally {
            if (selfManagedConnection != null) {
                // disconnect call
                selfManagedConnection.disconnectAndDestroy();
            }
        }
    }

    /**
     * Verify TelecomManager#endCall cannot end a ringing self-managed call.
     *
     * @throws Exception; should not throw exception
     */
    @ApiTest(apis = {"android.telecom.TelecomManager#endCall"})
    public void testEndCallOnSelfManagedCallOnRingingCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        SelfManagedConnection selfManagedConnection = null;

        try {
            // start a self-managed call
            assertIsIncomingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_1);
            TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                    TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
            selfManagedConnection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);

            // verify the self-managed call is ringing
            TestUtils.waitOnAllHandlers(getInstrumentation());
            assertEquals(selfManagedConnection.getState(), Call.STATE_RINGING);

            // try to end it but the expectation is Telecom cannot end the self-managed call
            assertFalse(mTelecomManager.endCall());

            // verify the self-managed call is still ringing
            TestUtils.waitOnAllHandlers(getInstrumentation());
            assertEquals(Call.STATE_RINGING, selfManagedConnection.getState());

        } finally {
            if (selfManagedConnection != null) {
                // disconnect call
                selfManagedConnection.disconnectAndDestroy();
            }
        }
    }

    /**
     * Tests ability to change the audio route via the
     * {@link android.telecom.Connection#setAudioRoute(int)} API.
     */
    public void testAudioRoute() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        setActiveAndVerify(connection);

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        TestUtils.InvokeCounter counter = connection.getCallAudioStateChangedInvokeCounter();
        counter.waitForCount(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        CallAudioState callAudioState = (CallAudioState) counter.getArgs(0)[0];
        int availableRoutes = callAudioState.getSupportedRouteMask();

        // Both the speaker and either wired or earpiece are required to test changing the audio
        // route. Skip this test if either of these routes is unavailable.
        if ((availableRoutes & CallAudioState.ROUTE_SPEAKER) == 0
                || (availableRoutes & CallAudioState.ROUTE_WIRED_OR_EARPIECE) == 0) {
            return;
        }

        // Determine what the second route after SPEAKER should be, depending on what's supported.
        int secondRoute = (availableRoutes & CallAudioState.ROUTE_EARPIECE) == 0
                ? CallAudioState.ROUTE_WIRED_HEADSET
                : CallAudioState.ROUTE_EARPIECE;

        counter.clearArgs();
        connection.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        counter.waitForPredicate(new Predicate<CallAudioState>() {
                @Override
                public boolean test(CallAudioState cas) {
                    return cas.getRoute() == CallAudioState.ROUTE_SPEAKER;
                }
            }, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

        counter.clearArgs();
        connection.setAudioRoute(secondRoute);
        counter.waitForPredicate(new Predicate<CallAudioState>() {
            @Override
            public boolean test(CallAudioState cas) {
                return cas.getRoute() == secondRoute;
            }
        }, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        if (TestUtils.HAS_BLUETOOTH) {
            // Call requestBluetoothAudio on a device. This will be a noop since no devices are
            // connected.
            connection.requestBluetoothAudio(TestUtils.BLUETOOTH_DEVICE1);
        }
        setDisconnectedAndVerify(connection, isLoggedCall(TestUtils.TEST_SELF_MANAGED_HANDLE_1),
                callLogEntryLatch);
    }
    /**
     * Tests that Telecom will allow the incoming call while the number of self-managed call is not
     * exceed the limit.
     * @throws Exception
     */
    public void testIncomingWhileOngoingWithinLimit() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // Create an ongoing call in the first self-managed PhoneAccount.
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        setActiveAndVerify(connection);

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        assertIsIncomingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_2);
        // Attempt to create a new incoming call for the other PhoneAccount; it should succeed.
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_2, TEST_ADDRESS_2);
        SelfManagedConnection connection2 = TestUtils.waitForAndGetConnection(TEST_ADDRESS_2);

        connection2.disconnectAndDestroy();
        setDisconnectedAndVerify(connection, isLoggedCall(TestUtils.TEST_SELF_MANAGED_HANDLE_1),
                callLogEntryLatch);
    }

    /**
     * Tests the self-managed ConnectionService has gained the focus when it become active.
     */
    public void testSelfManagedConnectionServiceGainedFocus() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        assertIsIncomingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_1);
        // Attempt to create a new Incoming self-managed call
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        setActiveAndVerify(connection);

        // The ConnectionService has gained the focus
        assertTrue(CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                CtsSelfManagedConnectionService.FOCUS_GAINED_LOCK));

        setDisconnectedAndVerify(connection, isLoggedCall(TestUtils.TEST_SELF_MANAGED_HANDLE_1),
                callLogEntryLatch);
    }

    public void testSelfManagedConnectionServiceLostFocus() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // GIVEN an ongoing self-managed call
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        setActiveAndVerify(connection);
        assertTrue(CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                CtsSelfManagedConnectionService.FOCUS_GAINED_LOCK));

        // WHEN place a managed call
        mInCallCallbacks.resetLock();
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall().setActive();
        assertTrue(connectionService.waitForEvent(
                MockConnectionService.EVENT_CONNECTION_SERVICE_FOCUS_GAINED));

        // THEN the self-managed ConnectionService lost the focus

        connection.disconnectAndDestroy();
        assertTrue(CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                CtsSelfManagedConnectionService.FOCUS_LOST_LOCK));
    }

    /**
     * Tests that Telecom will disallow the incoming call while the ringing call is existed.
     */
    public void testRingCallLimitForOnePhoneAccount() {
        if (!mShouldTestTelecom) {
            return;
        }

        // GIVEN a self-managed call which state is ringing
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);

        assertIsIncomingCallPermitted(false, TestUtils.TEST_SELF_MANAGED_HANDLE_1);
        // WHEN create a new incoming call for the the same PhoneAccount
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);

        // THEN the new incoming call is denied
        assertTrue(CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                CtsSelfManagedConnectionService.CREATE_INCOMING_CONNECTION_FAILED_LOCK));
    }

    /**
     * Tests that Telecom enforces a maximum number of calls for a self-managed ConnectionService.
     *
     * @throws Exception
     */
    public void testCallLimit() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        List<SelfManagedConnection> connections = new ArrayList<>();
        // Create 10 calls; they should succeed.
        for (int ix = 0; ix < 10; ix++) {
            Uri address = Uri.fromParts("sip", "test" + ix + "@test.com", null);
            // Create an ongoing call in the first self-managed PhoneAccount.
            assertIsOutgoingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_1);
            TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                    TestUtils.TEST_SELF_MANAGED_HANDLE_1, address);
            SelfManagedConnection connection = TestUtils.waitForAndGetConnection(address);
            setActiveAndVerify(connection);
            connections.add(connection);
        }

        // Try adding an 11th.  It should fail to be created.
        assertIsIncomingCallPermitted(false, TestUtils.TEST_SELF_MANAGED_HANDLE_1);
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_2);
        assertTrue("Expected onCreateIncomingConnectionFailed callback",
                CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                        CtsSelfManagedConnectionService.CREATE_INCOMING_CONNECTION_FAILED_LOCK));

        connections.forEach((selfManagedConnection) ->
                selfManagedConnection.disconnectAndDestroy());

        waitOnAllHandlers(getInstrumentation());
    }

    /**
     * Start a self-managed call and then dial an emergency call and make sure the self-managed
     * call is successfully disconnected.
     */
    public void testDisconnectSelfManagedCallForEmergency() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);

        Uri address = getTestNumber();
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TEST_SELF_MANAGED_HANDLE_1, address);
        // Ensure Telecom bound to the self managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(address);
        assertNotNull("Self-Managed Connection should NOT be null.", connection);
        assertTrue("Self-Managed Connection should be outgoing.", !connection.isIncomingCall());
        // The self-managed ConnectionService must NOT have been prompted to show its incoming call
        // UI for an outgoing call.
        assertEquals(connection.getOnShowIncomingUiInvokeCounter().getInvokeCount(), 0);
        setActiveAndVerify(connection);

        mInCallCallbacks.resetLock();
        placeAndVerifyEmergencyCall(true /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();

        assertIsInCall(true);
        assertIsInManagedCall(true);
        try {
            TestUtils.waitOnAllHandlers(getInstrumentation());
        } catch (Exception e) {
            fail("Failed to wait on handlers " + e);
        }
        assertCallState(eCall, Call.STATE_DIALING);
        // The self-managed Connection should be disconnected!
        assertConnectionState(connection, Connection.STATE_DISCONNECTED);
    }

    /**
     * Start a managed emergency call and then ensure that a subsequent self-managed call fails to
     * be created.
     */
    public void testEmergencyCallOngoingNewOutgoingCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
        placeAndVerifyEmergencyCall(true /*supportsHold*/);
        assertIsInCall(true);
        assertIsInManagedCall(true);
        try {
            TestUtils.waitOnAllHandlers(getInstrumentation());
        } catch (Exception e) {
            fail("Failed to wait on handlers " + e);
        }

        // Try adding a self managed outgoing call.  It should fail to be created.
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        assertTrue("Expected onCreateOutgoingConnectionFailed callback",
                CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                        CtsSelfManagedConnectionService.CREATE_OUTGOING_CONNECTION_FAILED_LOCK));
    }

    /**
     * Start a managed emergency call and then ensure that a subsequent self-managed call fails to
     * be created.
     */
    public void testEmergencyCallOngoingIncomingCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
        placeAndVerifyEmergencyCall(true /*supportsHold*/);
        assertIsInCall(true);
        assertIsInManagedCall(true);
        try {
            TestUtils.waitOnAllHandlers(getInstrumentation());
        } catch (Exception e) {
            fail("Failed to wait on handlers " + e);
        }

        // Try adding a self managed incoming call.  It should fail to be created.
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        assertTrue("Expected onCreateIncomingConnectionFailed callback",
                CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                        CtsSelfManagedConnectionService.CREATE_INCOMING_CONNECTION_FAILED_LOCK));
    }

    public void testCallSwapBetweenTwoSelfManagedConnectionServices() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        //bind to test app selfmanagedcstestappone
        TestServiceConnection control = setUpControl(SELF_MANAGED_CS_CONTROL, SELF_MANAGED_CS_1);

        ICtsSelfManagedConnectionServiceControl appServiceController =
                ICtsSelfManagedConnectionServiceControl.Stub
                        .asInterface(control.getService());

        appServiceController.init();

        // register a self-managed phone account from self-managed CS test app
        appServiceController.registerPhoneAccount(
                TestUtils.TEST_SELF_MANAGED_CS_1_PHONE_ACCOUNT_1);

        //Place self-managed CS first call from test app
        placeSelfManagedCallOnTestApp(appServiceController,
                TestUtils.TEST_SELF_MANAGED_CS_1_HANDLE_1, TEST_ADDRESS_1);

        //Get test app call from inCallService
        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call1 = inCallService.getLastCall();

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(appServiceController.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        //Place self-managed CS second call
        SelfManagedConnection connection =
                placeSelfManagedCallAndGetConnection(TestUtils.TEST_SELF_MANAGED_HANDLE_4,
                    TEST_ADDRESS_2);

        final Call call2 = inCallService.getLastCall();

        //first call on hold after second call is active
        assertCallState(call1, Call.STATE_HOLDING);
        assertEquals(appServiceController.getConnectionState(), Connection.STATE_HOLDING);
        assertCallState(call2, Call.STATE_ACTIVE);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(connection.getAudioModeIsVoip());
        assertTrue(appServiceController.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        //unhold the first call should keep the second call on hold
        call1.unhold();
        assertTrue(appServiceController.waitOnUnHold());
        assertTrue(connection.waitOnHold());
        assertCallState(call2, Call.STATE_HOLDING);
        assertConnectionState(connection, Connection.STATE_HOLDING);
        assertCallState(call1, Call.STATE_ACTIVE);
        assertEquals(appServiceController.getConnectionState(), Connection.STATE_ACTIVE);

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(connection.getAudioModeIsVoip());
        assertTrue(appServiceController.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        //unhold the first call should keep the second call on hold
        call2.unhold();
        assertTrue(appServiceController.waitOnHold());
        assertTrue(connection.waitOnUnHold());
        assertCallState(call1, Call.STATE_HOLDING);
        assertEquals(appServiceController.getConnectionState(), Connection.STATE_HOLDING);
        assertCallState(call2, Call.STATE_ACTIVE);
        assertConnectionState(connection, Connection.STATE_ACTIVE);

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(connection.getAudioModeIsVoip());
        assertTrue(appServiceController.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        // disconnect active 2nd call
        connection.disconnectAndDestroy();

        assertIsInCall(true);
        assertIsInManagedCall(false);

        //first call should be on hold
        assertCallState(call1, Call.STATE_HOLDING);
        assertEquals(appServiceController.getConnectionState(), Connection.STATE_HOLDING);

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(appServiceController.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        //unhold first call
        call1.unhold();
        assertCallState(call1, Call.STATE_ACTIVE);
        assertEquals(appServiceController.getConnectionState(), Connection.STATE_ACTIVE);

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(appServiceController.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        appServiceController.disconnectConnection();

        assertCallState(call1, Call.STATE_DISCONNECTED);

        // unregister a self-managed phone account
        appServiceController.unregisterPhoneAccount(
                TestUtils.TEST_SELF_MANAGED_CS_1_HANDLE_1);

        appServiceController.deInit();

        mContext.unbindService(control);

        assertIsInCall(false);
        assertIsInManagedCall(false);
    }

    /**
     * Start a self-managed no hold capable call on different app and accept incoming managed call
     * should disconnect self-managed call
     */
    public void testManagedCallWhileNoHoldCapabilitySelfMaganedCallActive() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        //bind to test app selfmanagedcstestappone
        TestServiceConnection control = setUpControl(SELF_MANAGED_CS_CONTROL, SELF_MANAGED_CS_1);

        ICtsSelfManagedConnectionServiceControl appServiceController =
                ICtsSelfManagedConnectionServiceControl.Stub
                        .asInterface(control.getService());

        appServiceController.init();

        // register a self-managed phone account from self-managed CS test app
        appServiceController.registerPhoneAccount(
                TestUtils.TEST_SELF_MANAGED_CS_1_PHONE_ACCOUNT_3);

        // place a self-managed call
        appServiceController.initiateIncomingCall(
                TestUtils.TEST_SELF_MANAGED_CS_1_HANDLE_3, TEST_ADDRESS_2.toString());

        assertTrue(appServiceController.waitForBinding());

        appServiceController.setConnectionCapabilityNoHold();

        appServiceController.setConnectionActive();

        assertEquals(Connection.STATE_ACTIVE, appServiceController.getConnectionState());

        // add new managed call
        addAndVerifyNewIncomingCall(createTestNumber(), null);
        Connection connection = verifyConnectionForIncomingCall();

        assertConnectionState(connection, Connection.STATE_RINGING);
        assertEquals(Connection.STATE_ACTIVE, appServiceController.getConnectionState());

        // answer the incoming call
        MockInCallService inCallService = mInCallCallbacks.getService();
        Call call = inCallService.getLastCall();

        call.answer(VideoProfile.STATE_AUDIO_ONLY);

        assertConnectionState(connection, Connection.STATE_ACTIVE);
        assertEquals(Connection.STATE_DISCONNECTED, appServiceController.getConnectionState());

        // unregister a self-managed phone account
        appServiceController.unregisterPhoneAccount(
                TestUtils.TEST_SELF_MANAGED_CS_1_HANDLE_3);

        appServiceController.deInit();

        mContext.unbindService(control);

        call.disconnect();
    }

    /**
     * Tests ability to change the call endpoint via the
     * {@link android.telecom.Connection#requestCallEndpointChange} API.
     */
    public void testCallEndpoint() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        setActiveAndVerify(connection);

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        TestUtils.InvokeCounter currentEndpointCounter =
                connection.getCallEndpointChangedInvokeCounter();
        currentEndpointCounter.waitForCount(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        CallEndpoint currentEndpoint = (CallEndpoint) currentEndpointCounter.getArgs(0)[0];
        int currentEndpointType = currentEndpoint.getEndpointType();

        TestUtils.InvokeCounter availableEndpointsCounter =
                connection.getAvailableEndpointsChangedInvokeCounter();
        availableEndpointsCounter.waitForCount(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        List<CallEndpoint> availableEndpoints =
                (List<CallEndpoint>) availableEndpointsCounter.getArgs(0)[0];

        CallEndpoint anotherEndpoint = null;
        for (CallEndpoint endpoint : availableEndpoints) {
            if (endpoint.getEndpointType() != currentEndpointType) {
                anotherEndpoint = endpoint;
                break;
            }
        }
        if (anotherEndpoint != null) {
            Executor executor = mContext.getMainExecutor();
            final int anotherEndpointType = anotherEndpoint.getEndpointType();
            currentEndpointCounter.clearArgs();
            connection.requestCallEndpointChange(anotherEndpoint, executor,
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {}
                        @Override
                        public void onError(CallEndpointException exception) {}
                    });
            currentEndpointCounter.waitForPredicate(new Predicate<CallEndpoint>() {
                @Override
                public boolean test(CallEndpoint cep) {
                    return cep.getEndpointType() == anotherEndpointType;
                }
            }, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

            currentEndpointCounter.clearArgs();
            connection.requestCallEndpointChange(currentEndpoint, executor,
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {}
                        @Override
                        public void onError(CallEndpointException exception) {}
                    });
            currentEndpointCounter.waitForPredicate(new Predicate<CallEndpoint>() {
                @Override
                public boolean test(CallEndpoint cep) {
                    return cep.getEndpointType() == currentEndpointType;
                }
            }, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

        }
        setDisconnectedAndVerify(connection, isLoggedCall(TestUtils.TEST_SELF_MANAGED_HANDLE_1),
                callLogEntryLatch);
    }

    /**
     * Sets a connection active, and verifies TelecomManager thinks we're in call but not in a
     * managed call.
     * @param connection The connection.
     */
    private void setActiveAndVerify(SelfManagedConnection connection) throws Exception {
        // Set the connection active.
        connection.setActive();

        // Check with Telecom if we're in a call.
        assertIsInCall(true);
        assertIsInManagedCall(false);
    }

    /**
     * Sets a connection to be disconnected, and then waits until the TelecomManager reports it is
     * no longer in a call.
     * @param connection The connection to disconnect/destroy.
     * @param shouldCallBeLogged When {@code true} verify the call was logged; when {@code false}
     * @param callLogLatch
     */
    private void setDisconnectedAndVerify(SelfManagedConnection connection,
            boolean shouldCallBeLogged, CountDownLatch callLogLatch) {
        // Now, disconnect call and clean it up.
        connection.disconnectAndDestroy();

        assertIsInCall(false);
        assertIsInManagedCall(false);

        // Verify that an entry was written to the call log for the call if applicable.
        verifyCallLogging(callLogLatch, shouldCallBeLogged, connection.getAddress());
    }

    /**
     * helper method that creates and returns a self-managed connection with a given handle
     * and address.  Additionally, some checks are made to ensure the self-managed connection was
     * successful.
     */
    private SelfManagedConnection placeSelfManagedCallAndGetConnection(PhoneAccountHandle handle,
            Uri address) throws Exception {
        // place a self-managed call
        assertIsOutgoingCallPermitted(true, TestUtils.TEST_SELF_MANAGED_HANDLE_1);

        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager, handle, address);

        // Ensure Telecom bound to the self managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(address);

        assertNotNull("Self-Managed Connection should NOT be null.", connection);
        assertTrue("Self-Managed Connection should be outgoing.", !connection.isIncomingCall());

        // The self-managed ConnectionService must NOT have been prompted to show its incoming call
        // UI for an outgoing call.
        assertEquals(connection.getOnShowIncomingUiInvokeCounter().getInvokeCount(), 0);

        setActiveAndVerify(connection);

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(connection.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        // Expect there to be no managed calls at the moment.
        assertFalse(mTelecomManager.isInManagedCall());
        // But there should be a call (including self-managed).
        assertTrue(mTelecomManager.isInCall());

        // Expect that the new outgoing call broadcast did not fire for the self-managed calls.
        assertFalse(NewOutgoingCallBroadcastReceiver.isNewOutgoingCallBroadcastReceived());

        return connection;
    }

    /**
     * helper method that creates and returns a self-managed connection with a given handle
     * and address.  Additionally, some checks are made to ensure the self-managed connection was
     * successful.
     */
    private void placeSelfManagedCallOnTestApp(
            ICtsSelfManagedConnectionServiceControl serviceControl,
            PhoneAccountHandle handle, Uri address) throws Exception {
        // place a self-managed call
        assertTrue(serviceControl.placeOutgoingCall(handle, address.toString()));

        // Ensure Telecom bound to the self managed CS
        if (!serviceControl.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        if (!serviceControl.isConnectionAvailable()) {
            fail("Connection not available for Self-Managed ConnectionService");
        }

        serviceControl.setConnectionActive();

        assertTrue("Self-Managed Connection should be outgoing.", !serviceControl.isIncomingCall());

        // The self-managed ConnectionService must NOT have been prompted to show its incoming call
        // UI for an outgoing call.
        assertEquals(serviceControl.getOnShowIncomingUiInvokeCounter(), 0);

        // Ensure that the connection defaulted to voip audio mode.
        assertTrue(serviceControl.getAudioModeIsVoip());
        // Ensure AudioManager has correct voip mode.
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, MODE_IN_COMMUNICATION);

        // Expect there to be no managed calls at the moment.
        assertFalse(mTelecomManager.isInManagedCall());
        // But there should be a call (including self-managed).
        assertTrue(mTelecomManager.isInCall());

        // Expect that the new outgoing call broadcast did not fire for the self-managed calls.
        assertFalse(NewOutgoingCallBroadcastReceiver.isNewOutgoingCallBroadcastReceived());
    }
}
