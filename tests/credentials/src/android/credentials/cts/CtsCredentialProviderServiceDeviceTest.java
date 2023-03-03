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

package android.credentials.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.credentials.ClearCredentialStateException;
import android.credentials.ClearCredentialStateRequest;
import android.credentials.CreateCredentialException;
import android.credentials.CreateCredentialRequest;
import android.credentials.CreateCredentialResponse;
import android.credentials.CredentialManager;
import android.credentials.CredentialOption;
import android.credentials.GetCredentialException;
import android.credentials.GetCredentialRequest;
import android.credentials.GetCredentialResponse;
import android.credentials.cts.testcore.DeviceConfigStateRequiredRule;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.SettingsUtils;
import com.android.compatibility.common.util.Timeout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class CtsCredentialProviderServiceDeviceTest {
    public static final String CTS_PACKAGE_NAME =
            CtsNoOpCredentialProviderService.class.getPackage().getName();
    private static final String CTS_SERVICE_NAME = CTS_PACKAGE_NAME + "/" + CTS_PACKAGE_NAME + "."
            + CtsNoOpCredentialProviderService.class.getSimpleName();
    private static final int USER_ID = UserHandle.myUserId();
    private static final String TAG = "CtsCredentialProviderServiceDeviceTest";
    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER =
            "enable_credential_manager";
    private static final String PASSWORD_CREDENTIAL_TYPE =
            "android.credentials.TYPE_PASSWORD_CREDENTIAL";
    private static final String CREDENTIAL_SERVICE = "credential_service";
    private static final Timeout CONNECTION_TIMEOUT = new Timeout(
            "CONNECTION_TIMEOUT", 1500, 2F, 1500);


    private CredentialManager mCredentialManager;
    private final Context mContext = getInstrumentation().getContext();

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(TestCredentialActivity.class);

    // Sets up the feature flag rule and the device flag rule
    @Rule
    public final RequiredFeatureRule mRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_CREDENTIALS);

    @Rule
    public final DeviceConfigStateRequiredRule mDeviceConfigStateRequiredRule =
            new DeviceConfigStateRequiredRule(DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER,
                    DeviceConfig.NAMESPACE_CREDENTIAL, mContext, "true");

    @Before
    public void setUp() {
        Log.i(TAG, "Enabling service from scratch for " + CTS_SERVICE_NAME);
        Log.i(TAG, "Enabling CredentialManager flags as well...");
        enableCredentialManagerDeviceFeature(mContext);
        mCredentialManager = (CredentialManager) mContext.getSystemService(
                Context.CREDENTIAL_SERVICE);
        assumeTrue("VERSION.SDK_INT=" + VERSION.SDK_INT, BuildCompat.isAtLeastU());
        clearAllTestCredentialProviderServices();
        bindToTestService();
    }

    @After
    public void tearDown() {
        Log.i(TAG, "Disabling credman services and device feature flag");
        clearAllTestCredentialProviderServices();
    }

    private void clearAllTestCredentialProviderServices() {
        SettingsUtils.set("secure", CREDENTIAL_SERVICE, null);
    }

    @Test
    public void testGetCredentialManager_shouldSucceed() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);

        activityScenario.onActivity(activity -> {

            assertThat(mCredentialManager).isNotNull();
        });
    }

    // TODO for all 'valid success' cases, mock credential manager the current success case
    // TODO (rightly) flips an error bit since we have test inputs
    @Test
    public void testGetPasswordCredentialRequest_serviceSetUp_onErrorInvokedForEmptyResponse()
            throws InterruptedException {
        AtomicReference<GetCredentialException> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        GetCredentialRequest request = new GetCredentialRequest.Builder(empty)
                .addCredentialOption(new CredentialOption(
                PASSWORD_CREDENTIAL_TYPE, empty, empty, false)).build();
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {
            mCredentialManager.getCredential(request, activity, null,
                    Executors.newSingleThreadExecutor(), callback);
        });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get()).isNotNull();
        assertThat(loadedResult.get().getClass()).isEqualTo(
                GetCredentialException.class);
        assertThat(loadedResult.get().getType()).isEqualTo(
                GetCredentialException.TYPE_NO_CREDENTIAL);
        // TODO add a null check for the case when the feature exists but remains false
    }

    @Test
    public void testGetCredentialRequest_nullRequest_throwsNPE() {
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Do nothing
                    }
                };


        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {

            assertThrows("expect null request to throw NPE", NullPointerException.class,
                    () -> mCredentialManager.getCredential(null, activity, null,
                    Executors.newSingleThreadExecutor(), callback));
        });
    }

    @Test
    public void testCreatePasswordCredentialRequest_serviceSetUp_onErrorInvokedForEmptyResponse()
            throws InterruptedException {
        AtomicReference<CreateCredentialException> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        CreateCredentialRequest request = new CreateCredentialRequest.Builder(
                PASSWORD_CREDENTIAL_TYPE, empty, empty)
                .setIsSystemProviderRequired(false)
                .setAlwaysSendAppInfoToProvider(true)
                .build();
        OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull CreateCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {
            mCredentialManager.createCredential(request, activity, null,
                    Executors.newSingleThreadExecutor(), callback);
        });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get()).isNotNull();
        assertThat(loadedResult.get().getClass()).isEqualTo(
                CreateCredentialException.class);
        assertThat(loadedResult.get().getType()).isEqualTo(
                CreateCredentialException.TYPE_NO_CREATE_OPTIONS);
    }

    @Test
    public void testCreatePasswordCredentialRequest_nullRequest_throwsNPE() {
        OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull CreateCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        // Do nothing
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {

            assertThrows("expect null request to throw NPE", NullPointerException.class,
                    () -> mCredentialManager.createCredential(null, activity, null,
                            Executors.newSingleThreadExecutor(), callback));
        });
    }

    @Test
    public void testClearCredentialRequest_serviceSetUp_onResponseInvoked()
            throws InterruptedException {
        AtomicReference<Boolean> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        ClearCredentialStateRequest request = new ClearCredentialStateRequest(empty);
        OutcomeReceiver<Void, ClearCredentialStateException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Void response) {
                        loadedResult.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialStateException e) {
                        // Do nothing
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {
            mCredentialManager.clearCredentialState(request, null,
                    Executors.newSingleThreadExecutor(), callback);
        });

        latch.await(100L, TimeUnit.MILLISECONDS);
	assertThat(loadedResult.get()).isNotNull();
        assertThat(loadedResult.get()).isTrue();
    }

    @Test
    public void testClearCredentialRequest_nullRequest_throwsNPE() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {

            assertThrows("expect null request to throw NPE", NullPointerException.class,
                    () -> mCredentialManager.clearCredentialState(null, null,
                    Executors.newSingleThreadExecutor(), null));
        });
    }

    /**
     * Enable the main credential manager feature.
     * If this is off, any underlying changes for autofill-credentialManager integrations are off.
     */
    public static void enableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, true);
    }

    public static void disableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, false);
    }

    /**
     * Enable Credential Manager related autofill changes
     */
    public static void setCredentialManagerFeature(@NonNull Context context, boolean enabled) {
        setDeviceConfig(context,
                DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER, enabled);
    }

    /**
     * Set device config to set flag values.
     */
    public static void setDeviceConfig(
            @NonNull Context context, @NonNull String feature, boolean value) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_CREDENTIAL, feature);
        setDeviceConfig(deviceConfigStateManager, String.valueOf(value));
    }

    public static void setDeviceConfig(@NonNull DeviceConfigStateManager deviceConfigStateManager,
            @Nullable String value) {
        final String previousValue = deviceConfigStateManager.get();
        if (TextUtils.isEmpty(value) && TextUtils.isEmpty(previousValue)
                || TextUtils.equals(previousValue, value)) {
            Log.v(TAG, "No changed in config: " + deviceConfigStateManager);
            return;
        }
        deviceConfigStateManager.set(value);
    }

    private void bindToTestService() {
        // On Manager, bind to test service
        setTestableCredentialProviderService(CTS_SERVICE_NAME);
        assertTrue(isCredentialProviderServiceEnabled(CTS_SERVICE_NAME));
    }

    private String getCredentialProviderServiceComponent() {
        return runShellCommand("settings get secure credential_service %d", USER_ID);
    }

    private void setTestableCredentialProviderService(@NonNull String serviceName) {
        if (isCredentialProviderServiceEnabled(serviceName)) return;

        String settingOutput = readCredentialManagerProviderSetting();
        settingOutput = settingOutput == null ? "" : settingOutput;
        if (settingOutput.length() > 0) {
            settingOutput += ";" + serviceName;
        } else {
            settingOutput += serviceName;
        }
        // Guaranteed to not be null now since the NoOp service exists at a minimum
        Log.i(TAG, "Attempting to set services: " + settingOutput);
        SettingsUtils.set("secure", CREDENTIAL_SERVICE, settingOutput);

        // Waits until the service is actually enabled.
        try {
            CONNECTION_TIMEOUT.run("Checking if service enabled", () ->
                    isCredentialProviderServiceEnabled(serviceName));
        } catch (Exception e) {
            Log.i(TAG, "Failure... " + e.getLocalizedMessage());
            throw new AssertionError("Enabling Credman service failed.");
        }
    }

    /**
     * Checks whether the given service is set as the credential service for the default user.
     */
    private boolean isCredentialProviderServiceEnabled(String serviceName) {
        final String actualNames = readCredentialManagerProviderSetting();
        Log.i(TAG, "actual names in setting: " + actualNames + " ,serviceName being "
                + "checked : " + serviceName);
	if (actualNames == null) {
            return false;
        }
        return containsName(actualNames, serviceName);

    }

    private boolean containsName(@NonNull String serviceNames, @NonNull String name) {
        Set<String> services = new LinkedHashSet<>(List.of(serviceNames.split(";")));
        return services.contains(name);
    }

    /**
     * Gets then name of the credential service for the default user.
     */
    private String readCredentialManagerProviderSetting() {
        String serviceNames = SettingsUtils.get(CREDENTIAL_SERVICE);
        return serviceNames;
    }

    /**
     * Uses Settings to disable the given autofill service for the default user, and waits until
     * the setting is deleted.
     */
    private void disableCredentialProviderService(@NonNull Context context, String service) {
        final String currentService = SettingsUtils.get(CREDENTIAL_SERVICE);
        if (currentService == null) {
            return;
        }
        // remove same instance in order, including duplicates
        Set<String> services = new LinkedHashSet<>(List.of(currentService.split(";")));
        services.remove(service);
        String originalString = String.join(";", services);
        SettingsUtils.set("secure", CREDENTIAL_SERVICE, originalString);
    }
}

