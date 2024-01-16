/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telecom.cts.apps;

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.AttributesUtil.getDefaultAttributesForApp;
import static android.telecom.cts.apps.AttributesUtil.getDefaultAttributesForManaged;
import static android.telecom.cts.apps.AttributesUtil.getRandomAttributesForApp;
import static android.telecom.cts.apps.AttributesUtil.getRandomAttributesForManaged;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_CLEANUP_STUCK_CALLS;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_RESET_CAR;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.WaitForInCallService.verifyCallState;
import static android.telecom.cts.apps.WaitForInCallService.waitForInCallServiceBinding;
import static android.telecom.cts.apps.WaitForInCallService.waitUntilExpectCallCount;
import static android.telecom.cts.apps.WaitUntil.DEFAULT_TIMEOUT_MS;
import static android.telecom.cts.apps.WaitUntil.waitUntilConditionIsTrueOrTimeout;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallEndpoint;
import android.telecom.CallException;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements all the methods test classes call into to perform some action on an
 * application that is bound to in the cts/tests/tests/telecom-apps dir.
 */
public class BaseAppVerifierImpl {
    private static final String REGISTER_SIM_SUBSCRIPTION_PERMISSION =
            "android.permission.REGISTER_SIM_SUBSCRIPTION";
    private static final String MODIFY_PHONE_STATE_PERMISSION =
            "android.permission.MODIFY_PHONE_STATE";
    public Context mContext;
    public TelecomManager mTelecomManager;
    private final BindUtils mBindUtils = new BindUtils();
    private final PhoneAccountHandle mManagedHandle;
    private final PhoneAccount mManagedAccount;
    private final Instrumentation mInstrumentation;
    private final InCallServiceMethods mVerifierMethods;
    private final String mCallingPackageName;
    private final AudioManager mAudioManager;
    public String mPreviousDefaultDialer = "";

    public BaseAppVerifierImpl(Instrumentation i, PhoneAccount pa, InCallServiceMethods vm) {
        mInstrumentation = i;
        mContext = i.getContext();
        mTelecomManager = mContext.getSystemService(TelecomManager.class);
        mManagedAccount = pa;
        mManagedHandle = pa.getAccountHandle();
        mVerifierMethods = vm;
        mCallingPackageName = mContext.getPackageName();
        mAudioManager = mContext.getSystemService(AudioManager.class);
    }

    public void setUp() throws Exception {
        ShellCommandExecutor.executeShellCommand(mInstrumentation, COMMAND_RESET_CAR);
        AppOpsManager aom = mContext.getSystemService(AppOpsManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(aom,
                (appOpsMan) -> appOpsMan.setUidMode(AppOpsManager.OPSTR_PROCESS_OUTGOING_CALLS,
                        Process.myUid(), AppOpsManager.MODE_ALLOWED));
        mPreviousDefaultDialer = ShellCommandExecutor.getDefaultDialer(mInstrumentation);
        ShellCommandExecutor.setDefaultDialer(mInstrumentation, mCallingPackageName);
    }

    public void tearDown() throws Exception {
        ShellCommandExecutor.executeShellCommand(mInstrumentation, COMMAND_CLEANUP_STUCK_CALLS);
        if (!mPreviousDefaultDialer.equals("")) {
            ShellCommandExecutor.setDefaultDialer(mInstrumentation, mPreviousDefaultDialer);
        }
        ShellIdentityUtils.dropShellPermissionIdentity();
    }

    public AppControlWrapper bindToApp(TelecomTestApp applicationName) throws Exception {
        AppControlWrapper control = mBindUtils.bindToApplication(mContext, applicationName);
        if (isManagedConnectionService(applicationName)) {
            registerManagedPhoneAccount();
        }
        return control;
    }

    private void registerManagedPhoneAccount() throws Exception {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager,
                tm -> tm.registerPhoneAccount(mManagedAccount),
                MODIFY_PHONE_STATE_PERMISSION,
                REGISTER_SIM_SUBSCRIPTION_PERMISSION);
        ShellCommandExecutor.enablePhoneAccount(mInstrumentation, mManagedHandle);
    }

    private boolean isManagedConnectionService(TelecomTestApp applicationName) {
        return applicationName.equals(ManagedConnectionServiceApp);
    }

    public List<AppControlWrapper> bindToApps(List<TelecomTestApp> applicationNames)
            throws Exception {
        ArrayList<AppControlWrapper> controls = new ArrayList<>();
        for (TelecomTestApp name : applicationNames) {
            AppControlWrapper control = bindToApp(name);
            controls.add(control);
        }
        return controls;
    }


    public void tearDownApp(AppControlWrapper appControl) {
        if (appControl != null) {
            mBindUtils.unbindFromApplication(mContext, appControl);
        }
    }

    public void tearDownApps(List<AppControlWrapper> appControls) {
        for (AppControlWrapper control : appControls) {
            tearDownApp(control);
        }
    }

    /***********************************************************
     /                 core methods
     /***********************************************************/

    public CallAttributes getDefaultAttributes(TelecomTestApp name,
            boolean isOutgoing)
            throws Exception {
        if (name.equals(ManagedConnectionServiceApp)) {
            return getDefaultAttributesForManaged(mManagedHandle, isOutgoing);
        }
        return getDefaultAttributesForApp(name, isOutgoing);
    }

    public CallAttributes getRandomAttributes(TelecomTestApp name,
            boolean isOutgoing,
            boolean isHoldable)
            throws Exception {
        if (name.equals(ManagedConnectionServiceApp)) {
            return getRandomAttributesForManaged(mManagedHandle, isOutgoing, isHoldable);
        }
        return getRandomAttributesForApp(name, isOutgoing, isHoldable);
    }

    public String addCallAndVerify(AppControlWrapper appControl, CallAttributes attributes)
            throws Exception {
        int currentCallCount = mVerifierMethods.getCurrentCallCount();
        appControl.addCall(attributes);
        waitForInCallServiceBinding(mVerifierMethods);
        waitUntilExpectCallCount(mVerifierMethods, currentCallCount + 1);
        return mVerifierMethods.getLastAddedCall().getDetails().getId();
    }

    // -- call state
    public void setCallState(AppControlWrapper appControl, String id, int callState)
            throws Exception {
        appControl.setCallState(id, callState, true, new Bundle());
    }

    public void setCallStateAndVerify(AppControlWrapper appControl, String id, int callState)
            throws Exception {
        appControl.setCallState(id, callState, true, new Bundle());
        verifyCallState(mVerifierMethods, id, callState);
    }

    public void setCallStateAndVerify(AppControlWrapper appControl, String id, int targetCallState,
            int arg) throws Exception {
        Bundle extras = new Bundle();
        if (targetCallState == STATE_ACTIVE) {
            verifyCallIsInState(id, STATE_RINGING);
            extras = CallControlExtras.addVideoStateExtra(extras, arg);
        } else if (targetCallState == STATE_DISCONNECTED) {
            extras = CallControlExtras.addDisconnectCauseExtra(extras, arg);
        }
        appControl.setCallState(id, targetCallState, true, extras);
        // verify the call was added in the ICS
        verifyCallState(mVerifierMethods, id, targetCallState);
    }

    public CallException setCallStateButExpectOnError(AppControlWrapper appControl,
            String id,
            int targetCallState)
            throws Exception {
        verifyAppIsTransactional(appControl);
        return appControl.setCallState(id, targetCallState, false, new Bundle());

    }

    private void verifyAppIsTransactional(AppControlWrapper appControlWrapper) throws Exception {
        if (!appControlWrapper.isTransactionalControl()) {
            throw new Exception("This method is only for Transactional Apps");
        }
    }

    public CallException setCallStateButExpectOnError(AppControlWrapper appControl,
            String id,
            int targetCallState,
            int arg) throws Exception {
        verifyAppIsTransactional(appControl);
        Bundle extras = new Bundle();
        if (targetCallState == STATE_ACTIVE) {
            verifyCallIsInState(id, STATE_RINGING);
            extras = CallControlExtras.addVideoStateExtra(extras, arg);
        } else if (targetCallState == STATE_DISCONNECTED) {
            extras = CallControlExtras.addDisconnectCauseExtra(extras, arg);
        }
        return appControl.setCallState(id, targetCallState, false, extras);
    }

    public void verifyCallIsInState(String id, int state) throws Exception {
        waitForInCallServiceBinding(mVerifierMethods);
        verifyCallState(mVerifierMethods, id, state);
    }

    public void answerViaInCallServiceAndVerify(String id, int videoState) throws Exception {
        waitForInCallServiceBinding(mVerifierMethods);
        List<Call> calls = mVerifierMethods.getOngoingCalls();
        Call targetCall = null;
        for (Call call : calls) {
            if (call.getDetails().getId().equals(id)) {
                targetCall = call;
                break;
            }
        }
        if (targetCall == null) {
            fail("answerViaInCallServiceAndVerify: failed to find target call id=" + id);
        }
        targetCall.answer(videoState);
        verifyCallIsInState(id, STATE_ACTIVE);
    }

    // -- audio state
    public CallEndpoint getAnotherCallEndpoint(AppControlWrapper appControl, String id)
            throws Exception {
        CallEndpoint currentCallEndpoint = getCurrentCallEndpoint(appControl, id);
        List<CallEndpoint> endpoints = getAvailableCallEndpoints(appControl, id);

        if (currentCallEndpoint == null) {
            fail("currentCallEndpoint is NULL");
        }
        if (endpoints == null) {
            fail("available endpoints list is NULL");
        }
        if (endpoints.size() == 1) {
            return null;
        }
        for (CallEndpoint endpoint : endpoints) {
            if (endpoint.getEndpointType() != currentCallEndpoint.getEndpointType()) {
                return endpoint;
            }
        }
        return null;
    }

    public CallEndpoint getCurrentCallEndpoint(AppControlWrapper appControl, String id)
            throws Exception {
        return appControl.getCurrentCallEndpoint(id);
    }

    public List<CallEndpoint> getAvailableCallEndpoints(AppControlWrapper appControl, String id)
            throws Exception {
        return appControl.getAvailableCallEndpoints(id);
    }


    public void setAudioRouteStateAndVerify(AppControlWrapper appControl, String id,
            CallEndpoint newCallEndpoint) throws Exception {
        appControl.setAudioRouteStateAndVerify(id, newCallEndpoint);
    }

    public boolean isMuted(AppControlWrapper appControl, String id) throws RemoteException {
        return appControl.isMuted(id);
    }

    public void setMuteState(AppControlWrapper appControl, String id, boolean isMuted)
            throws RemoteException {
        appControl.setMuteState(id, isMuted);
    }

    // -- phone accounts
    public void registerDefaultPhoneAccount(AppControlWrapper appControl) throws RemoteException {
        appControl.registerDefaultPhoneAccount();
        if (appControl.isManagedAppControl()) {
            assertTrue(isPhoneAccountRegistered(mManagedHandle));
        } else {
            PhoneAccount account = appControl.getDefaultPhoneAccount();
            assertTrue(isPhoneAccountRegistered(account.getAccountHandle()));
        }
    }

    public void registerCustomPhoneAccount(AppControlWrapper appControl, PhoneAccount account)
            throws RemoteException {
        appControl.registerCustomPhoneAccount(account);
        assertTrue(isPhoneAccountRegistered(account.getAccountHandle()));
    }

    public void unregisterPhoneAccountWithHandle(AppControlWrapper appControl,
            PhoneAccountHandle handle) throws RemoteException {
        appControl.unregisterPhoneAccountWithHandle(handle);
        assertFalse(isPhoneAccountRegistered(handle));
    }

    public List<PhoneAccountHandle> getAccountHandlesForApp(AppControlWrapper appControl)
            throws RemoteException {
        return appControl.getAccountHandlesForApp();
    }

    public boolean isPhoneAccountRegistered(PhoneAccountHandle handle) {
        return mTelecomManager.getPhoneAccount(handle) != null;
    }

    public void assertAudioMode(final int expectedMode) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return mAudioManager.getMode() == expectedMode;
                    }
                },
                DEFAULT_TIMEOUT_MS,
                "Audio mode was expected to be " + expectedMode
        );
    }

}
