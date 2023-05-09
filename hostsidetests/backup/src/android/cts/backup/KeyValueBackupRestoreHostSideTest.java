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

package android.cts.backup;

import static com.android.compatibility.common.util.BackupUtils.LOCAL_TRANSPORT_TOKEN;

import static org.junit.Assert.assertNull;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for checking that key/value backup and restore works correctly.
 * It interacts with the app that saves values in different shared preferences and files.
 * The app uses BackupAgentHelper to do key/value backup of those values.
 *
 * NB: The tests use "bmgr backupnow" for backup, which works on N+ devices.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class KeyValueBackupRestoreHostSideTest extends BaseBackupHostSideTest {

    /** The name of the package of the app under test */
    static final String KEY_VALUE_RESTORE_APP_PACKAGE =
            "android.cts.backup.keyvaluerestoreapp";

    /** The name of the package with the activity testing shared preference restore. */
    private static final String SHARED_PREFERENCES_RESTORE_APP_PACKAGE =
            "android.cts.backup.sharedprefrestoreapp";

    /** The name of the device side test class */
    static final String KEY_VALUE_RESTORE_DEVICE_TEST_NAME =
            KEY_VALUE_RESTORE_APP_PACKAGE + ".KeyValueBackupRestoreTest";

    /** The name of the apk of the app under test */
    static final String KEY_VALUE_RESTORE_APP_APK = "CtsKeyValueBackupRestoreApp.apk";

    /** The name of the apk with the activity testing shared preference restore. */
    private static final String SHARED_PREFERENCES_RESTORE_APP_APK =
            "CtsSharedPreferencesRestoreApp.apk";


    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        installPackageAsUser(KEY_VALUE_RESTORE_APP_APK, mDefaultBackupUserId);
        clearPackageDataAsUser(KEY_VALUE_RESTORE_APP_PACKAGE, mDefaultBackupUserId);

        installPackageAsUser(SHARED_PREFERENCES_RESTORE_APP_APK, mDefaultBackupUserId);
        clearPackageDataAsUser(SHARED_PREFERENCES_RESTORE_APP_APK, mDefaultBackupUserId);
    }

    @After
    public void tearDown() throws Exception {
        // Clear backup data and uninstall the package (in that order!)
        clearBackupDataInLocalTransport(KEY_VALUE_RESTORE_APP_PACKAGE);
        assertNull(uninstallPackageAsUser(KEY_VALUE_RESTORE_APP_PACKAGE, mDefaultBackupUserId));

        clearBackupDataInLocalTransport(SHARED_PREFERENCES_RESTORE_APP_PACKAGE);
        assertNull(uninstallPackageAsUser(SHARED_PREFERENCES_RESTORE_APP_PACKAGE,
                mDefaultBackupUserId));
    }

    /**
     * Test that verifies key/value backup and restore.
     *
     * The flow of the test:
     * 1. Check that app has no saved data
     * 2. App saves the predefined values to shared preferences and files.
     * 3. Backup the app's data
     * 4. Uninstall the app
     * 5. Install the app back
     * 6. Check that all the shared preferences and files were restored.
     */
    @Test
    public void testKeyValueBackupAndRestore() throws Exception {
        checkDeviceTest("checkSharedPrefIsEmpty");

        checkDeviceTest("saveSharedPreferencesAndNotifyBackupManager");

        getBackupUtils().backupNowForUserAndAssertSuccess(KEY_VALUE_RESTORE_APP_PACKAGE,
                mDefaultBackupUserId);

        assertNull(uninstallPackageAsUser(KEY_VALUE_RESTORE_APP_PACKAGE, mDefaultBackupUserId));

        installPackageAsUser(KEY_VALUE_RESTORE_APP_APK, mDefaultBackupUserId);

        // Shared preference should be restored
        checkDeviceTest("checkSharedPreferencesAreRestored");
    }

    /**
     * Test that verifies SharedPreference restore behavior.
     *
     * The tests uses device-side test routines and a test activity in *another* package, since
     * the app containing the instrumented tests is killed after each test.
     *
     * Test logic:
     *   1. The activity is launched; it creates a new SharedPreferences instance and writes
     *       a known value to the INT_PREF element's via that instance.  The instance is
     *       kept live.
     *   2. The app is backed up, storing this known value in the backup dataset.
     *   3. Next, the activity is instructed to write a different value to the INT_PREF
     *       shared preferences element.  At this point, the app's current on-disk state
     *       and the live shared preferences instance are in agreement, holding a value
     *       different from that in the backup.
     *   4. The runner triggers a restore for this app.  This will rewrite the shared prefs
     *       file itself with the backed-up content (i.e. different from what was just
     *       committed from this activity).
     *   5. Finally, the runner instructs the activity to compare the value of its existing
     *       shared prefs instance's INT_PREF element with what was previously written.
     *       The test passes if these differ, i.e. if the live shared prefs instance picked
     *       up the newly-restored data.
     */
    @Test
    public void testSharedPreferencesRestore() throws Exception {
        checkDeviceTest("launchSharedPrefActivity");

        getBackupUtils().backupNowForUserAndAssertSuccess(SHARED_PREFERENCES_RESTORE_APP_PACKAGE,
                mDefaultBackupUserId);

        checkDeviceTest("updateSharedPrefActivity");

        getBackupUtils().restoreForUserAndAssertSuccess(LOCAL_TRANSPORT_TOKEN,
                SHARED_PREFERENCES_RESTORE_APP_PACKAGE, mDefaultBackupUserId);

        checkDeviceTest("checkSharedPrefActivity");
    }

    private void checkDeviceTest(String methodName)
            throws DeviceNotAvailableException {
        super.checkDeviceTestAsUser(KEY_VALUE_RESTORE_APP_PACKAGE,
                KEY_VALUE_RESTORE_DEVICE_TEST_NAME,
                methodName, mDefaultBackupUserId);
    }
}
