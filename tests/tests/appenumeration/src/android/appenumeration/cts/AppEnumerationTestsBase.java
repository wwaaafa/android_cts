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

package android.appenumeration.cts;

import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_TEST;
import static android.appenumeration.cts.Constants.EXTRA_DATA;
import static android.appenumeration.cts.Constants.EXTRA_ERROR;
import static android.appenumeration.cts.Constants.EXTRA_ID;
import static android.appenumeration.cts.Constants.EXTRA_PENDING_INTENT;
import static android.appenumeration.cts.Constants.EXTRA_REMOTE_CALLBACK;
import static android.appenumeration.cts.Constants.EXTRA_REMOTE_READY_CALLBACK;
import static android.appenumeration.cts.Utils.Result;
import static android.os.Process.INVALID_UID;

import android.accounts.AccountManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;
import android.os.RemoteCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class AppEnumerationTestsBase {
    private static Handler sResponseHandler;
    private static HandlerThread sResponseThread;

    static Context sContext;
    static PackageManager sPm;
    static AccountManager sAccountManager;

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void setup() {
        sResponseThread = new HandlerThread("response");
        sResponseThread.start();
        sResponseHandler = new Handler(sResponseThread.getLooper());

        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        sPm = sContext.getPackageManager();
        sAccountManager = AccountManager.get(sContext);
    }

    @AfterClass
    public static void tearDown() {
        sResponseThread.quit();
    }

    Result sendCommand(@NonNull String sourcePackageName, @Nullable String targetPackageName,
            int targetUid, @Nullable Parcelable intentExtra, String action, boolean waitForReady)
            throws Exception {
        final Intent intent = new Intent(action)
                .setComponent(new ComponentName(sourcePackageName, ACTIVITY_CLASS_TEST))
                // data uri unique to each activity start to ensure actual launch and not just
                // redisplay
                .setData(Uri.parse("test://" + UUID.randomUUID().toString()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        if (targetPackageName != null) {
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, targetPackageName);
        }
        if (targetUid > INVALID_UID) {
            intent.putExtra(Intent.EXTRA_UID, targetUid);
        }
        if (intentExtra != null) {
            if (intentExtra instanceof Intent) {
                intent.putExtra(Intent.EXTRA_INTENT, intentExtra);
            } else if (intentExtra instanceof PendingIntent) {
                intent.putExtra(EXTRA_PENDING_INTENT, intentExtra);
            } else if (intentExtra instanceof Bundle) {
                intent.putExtra(EXTRA_DATA, intentExtra);
            }
        }

        final ConditionVariable latch = new ConditionVariable();
        final AtomicReference<Bundle> resultReference = new AtomicReference<>();
        final RemoteCallback callback = new RemoteCallback(
                bundle -> {
                    resultReference.set(bundle);
                    latch.open();
                },
                sResponseHandler);
        intent.putExtra(EXTRA_REMOTE_CALLBACK, callback);
        if (waitForReady) {
            AmUtils.waitForBroadcastIdle();
            startAndWaitForCommandReady(intent);
        } else {
            sContext.startActivity(intent);
        }
        return () -> {
            if (!latch.block(TimeUnit.SECONDS.toMillis(10))) {
                throw new TimeoutException(
                        "Latch timed out while awaiting a response from " + sourcePackageName);
            }
            final Bundle bundle = resultReference.get();
            if (bundle != null && bundle.containsKey(EXTRA_ERROR)) {
                throw Objects.requireNonNull(bundle.getSerializable(EXTRA_ERROR, Exception.class));
            }
            return bundle;
        };
    }

    private void startAndWaitForCommandReady(Intent intent) throws Exception {
        final ConditionVariable latchForReady = new ConditionVariable();
        final RemoteCallback readyCallback = new RemoteCallback(bundle -> latchForReady.open(),
                sResponseHandler);
        intent.putExtra(EXTRA_REMOTE_READY_CALLBACK, readyCallback);
        sContext.startActivity(intent);
        if (!latchForReady.block(TimeUnit.SECONDS.toMillis(10))) {
            throw new TimeoutException(
                    "Latch timed out while awaiting a response from command " + intent.getAction());
        }
    }

    Bundle sendCommandBlocking(@NonNull String sourcePackageName,
            @Nullable String targetPackageName, @Nullable Parcelable intentExtra, String action)
            throws Exception {
        final Result result = sendCommand(sourcePackageName, targetPackageName,
                INVALID_UID /* targetUid */, intentExtra, action, false /* waitForReady */);
        return result.await();
    }

    Bundle sendCommandBlocking(@NonNull String sourcePackageName, int targetUid,
            @Nullable Parcelable intentExtra, String action)
            throws Exception {
        final Result result = sendCommand(sourcePackageName, null /* targetPackageName */,
                targetUid, intentExtra, action, false /* waitForReady */);
        return result.await();
    }

    Integer[] getSessionInfos(String action, String sourcePackageName, int sessionId)
            throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putInt(EXTRA_ID, sessionId);
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, action);
        final List<PackageInstaller.SessionInfo> infos = response.getParcelableArrayList(
                Intent.EXTRA_RETURN_RESULT, PackageInstaller.SessionInfo.class);
        return infos.stream()
                .map(i -> (i == null ? PackageInstaller.SessionInfo.INVALID_ID : i.getSessionId()))
                .distinct()
                .toArray(Integer[]::new);
    }
}
