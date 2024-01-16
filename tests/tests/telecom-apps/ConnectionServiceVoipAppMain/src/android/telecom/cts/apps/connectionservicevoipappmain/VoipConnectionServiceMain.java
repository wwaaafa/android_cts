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
package android.telecom.cts.apps.connectionservicevoipappmain;

import android.content.Intent;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.apps.TestAppConnection;
import android.util.Log;

public class VoipConnectionServiceMain extends ConnectionService {
    private static final String TAG = VoipConnectionServiceMain.class.getSimpleName();
    public static VoipConnectionServiceMain sConnectionService;
    public static TestAppConnection sLastConnection = null;

    @Override
    public void onBindClient(Intent intent) {
        Log.i(TAG, String.format("onBindClient: intent=[%s]", intent));
        sConnectionService = this;
        sLastConnection = null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, String.format("onUnbind: intent=[%s]", intent));
        sConnectionService = null;
        sLastConnection = null;
        return super.onUnbind(intent);
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(TAG, String.format("onCreateOutgoingConnection: account=[%s], request=[%s]",
                connectionManagerPhoneAccount, request));
        return createConnection(request, true);
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(TAG, String.format("onCreateOutgoingConnectionFailed: account=[%s], request=[%s]",
                connectionManagerPhoneAccount, request));
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request);
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(TAG, String.format("onCreateIncomingConnection: account=[%s], request=[%s]",
                connectionManagerPhoneAccount, request));
        return createConnection(request, false);
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(TAG, String.format("onCreateIncomingConnectionFailed: account=[%s], request=[%s]",
                connectionManagerPhoneAccount, request));
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request);
    }

    private Connection createConnection(ConnectionRequest request, boolean isOutgoing) {
        TestAppConnection connection = new TestAppConnection();
        sLastConnection = connection;

        if (isOutgoing) {
            connection.setDialing();
        } else {
            connection.setRinging();
        }

        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setConnectionCapabilities(
                Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD
        );
        connection.setAudioModeIsVoip(true);
        return connection;
    }
}
