/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.car.Car;
import android.car.test.CarTestManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class OemCarServiceImplTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final Car mCar = Car.createCar(mContext);
    private CarTestManager mManager;
    private String mOemServiceName;
    private UiAutomation mUiAutomation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation();

    @Before
    public void setUp() throws Exception {
        assumeOemServiceImplemented();
        mUiAutomation.adoptShellPermissionIdentity(
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    @After
    public void cleanUp() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    // Test should run for TIRAMISU_0. As it is not testing API or CDD, using
    // android.car.oem.OemCarService#getSupportedCarVersion so that test run for TIRAMISU_0.
    @Test
    @ApiTest(apis = "android.car.oem.OemCarService#getSupportedCarVersion")
    public void testOemServicePermissionInManifest() throws Exception {
        Intent intent = (new Intent())
                .setComponent(ComponentName.unflattenFromString(mOemServiceName));

        assertThrows(SecurityException.class,
                () -> mContext.bindServiceAsUser(intent, mCarOemServiceConnection,
                        Context.BIND_AUTO_CREATE, UserHandle.SYSTEM));
    }

    private void assumeOemServiceImplemented() throws Exception {
        mManager = (CarTestManager) mCar.getCarManager(Car.TEST_SERVICE);
        assertThat(mManager).isNotNull();
        mOemServiceName = mManager.getOemServiceName();
        assumeTrue("OEM service not enabled.",
                mOemServiceName != null && !mOemServiceName.isEmpty());
    }

    private final ServiceConnection mCarOemServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

}
