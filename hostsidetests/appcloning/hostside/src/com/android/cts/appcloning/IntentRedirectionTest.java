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

package com.android.cts.appcloning;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * Test intent redirection across clone and owner profile
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class IntentRedirectionTest extends AppCloningBaseHostTest {
    private static final String CLONE_PROFILE_APP = "CtsAppCloningIntentRedirectionCloneProfileApp."
            + "apk";
    private static final String OWNER_PROFILE_APP = "CtsAppCloningIntentRedirectionOwnerProfileApp."
            + "apk";
    private static final String INTENT_REDIRECTION_TEST_PACKAGE = "com.android.cts.appcloning."
            + "intentredirectiontest.app";
    private static final String INTENT_REDIRECTION_TEST_CLASS = "IntentRedirectionAppTest";
    private static final String INTENT_REDIRECTION_TEST_APK = "CtsIntentRedirectionTestApp.apk";

    private static final String OWNER_USER_ID = "0";

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        assertThat(testInfo.getDevice()).isNotNull();
        AppCloningBaseHostTest.baseHostSetup(testInfo.getDevice());
        assumeTrue(isAtLeastU());
        switchOnIntentRedirectionFlag();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        switchOffIntentRedirectionFlag();
        AppCloningBaseHostTest.baseHostTeardown();
    }

    @Before
    public void setUp() throws Exception {
        installPackage(INTENT_REDIRECTION_TEST_APK, "--user all");
    }

    /**
     * Intent for Intent.ACTION_VIEW should be resolved across both profiles
     * @throws Exception
     */
    @Test
    public void testActionViewRedirectionInBothProfiles() throws Exception {

        String intentAction = "android.intent.action.VIEW";
        installPackage(CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(OWNER_PROFILE_APP, "--user " + Integer.valueOf(OWNER_USER_ID));

        // Intent in owner profile should be resolved in both profiles
        queryIntentForUser(intentAction, OWNER_USER_ID,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
        // Intent in clone profile should be resolved in both profiles
        queryIntentForUser(intentAction, sCloneUserId,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
    }

    /**
     * With settings_allow_intent_redirection_for_clone_profile flag off,
     * Intent for Intent.ACTION_VIEW should be resolved within current profile
     * @throws Exception
     */
    @Test
    public void testActionViewRedirectionInBothProfilesFlagOff() throws Exception {

        String intentAction = "android.intent.action.VIEW";
        installPackage(CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(OWNER_PROFILE_APP, "--user " + Integer.valueOf(OWNER_USER_ID));

        //Setting flag as false
        switchOffIntentRedirectionFlag();
        try {
            // Intent in owner profile should be resolved in owner profile
            queryIntentForUser(intentAction, OWNER_USER_ID,
                    /* shouldCloneAppBePresent */ false, /* shouldOwnerAppBePresent */ true,
                    /* isMatchCloneProfileFlagSet */ true);
            // Intent in clone profile should be resolved in clone profile
            queryIntentForUser(intentAction, sCloneUserId,
                    /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ false,
                    /* isMatchCloneProfileFlagSet */ true);
        } finally {
            switchOnIntentRedirectionFlag();
        }
    }

    /**
     * Intent for Intent.ACTION_SEND should be resolved across both profiles
     * @throws Exception
     */
    @Test
    public void testActionSendRedirectionInBothProfiles() throws Exception {

        String intentAction = "android.intent.action.SEND";
        installPackage(CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(OWNER_PROFILE_APP, "--user " + Integer.valueOf(OWNER_USER_ID));

        // Intent in owner profile should be resolved in both profiles
        queryIntentForUser(intentAction, OWNER_USER_ID,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
        // Intent in clone profile should be resolved in both profiles
        queryIntentForUser(intentAction, sCloneUserId,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
    }

    /**
     * Intent for Intent.ACTION_SENDTO should be resolved across both profiles
     * @throws Exception
     */
    @Test
    public void testActionSendToRedirectionInBothProfiles() throws Exception {

        String intentAction = "android.intent.action.SENDTO";
        installPackage(INTENT_REDIRECTION_TEST_APK, "--user all");
        installPackage(CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(OWNER_PROFILE_APP, "--user " + Integer.valueOf(OWNER_USER_ID));

        // Intent in owner profile should be resolved in both profiles
        queryIntentForUser(intentAction, OWNER_USER_ID,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
        // Intent in clone profile should be resolved in both profiles
        queryIntentForUser(intentAction, sCloneUserId,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
    }

    /**
     * Intent for Intent.ACTION_SEND_MULTIPLE should be resolved across both profiles
     * @throws Exception
     */
    @Test
    public void testActionSendMultipleRedirectionInBothProfiles() throws Exception {

        String intentAction = "android.intent.action.SEND_MULTIPLE";
        installPackage(CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(OWNER_PROFILE_APP, "--user " + Integer.valueOf(OWNER_USER_ID));

        // Intent in owner profile should be resolved in both profiles
        queryIntentForUser(intentAction, OWNER_USER_ID,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
        // Intent in clone profile should be resolved in both profiles
        queryIntentForUser(intentAction, sCloneUserId,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
    }

    /**
     * Intent for MediaStore.ACTION_IMAGE_CAPTURE should be resolved in owner profiles
     * @throws Exception
     */
    @Test
    public void testActionImageCaptureRedirectionOnlyInOwnerProfile() throws Exception {

        String intentAction = "android.media.action.IMAGE_CAPTURE";
        installPackage(CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(OWNER_PROFILE_APP, "--user " + Integer.valueOf(OWNER_USER_ID));

        // Intent in owner profile should be resolved in owner profile
        queryIntentForUser(intentAction, OWNER_USER_ID,
                /* shouldCloneAppBePresent */ false, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
        // Intent in clone profile should be resolved in owner profile
        queryIntentForUser(intentAction, sCloneUserId,
                /* shouldCloneAppBePresent */ false, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
    }

    /**
     * Intent for MediaStore.ACTION_VIDEO_CAPTURE should be resolved in owner profiles
     * @throws Exception
     */
    @Test
    public void testActionVideoCaptureRedirectionOnlyInOwnerProfile() throws Exception {

        String intentAction = "android.media.action.VIDEO_CAPTURE";
        installPackage(CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(OWNER_PROFILE_APP, "--user " + Integer.valueOf(OWNER_USER_ID));

        // Intent in owner profile should be resolved in owner profile
        queryIntentForUser(intentAction, OWNER_USER_ID,
                /* shouldCloneAppBePresent */ false, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
        // Intent in owner profile should be resolved in owner profile
        queryIntentForUser(intentAction, sCloneUserId,
                /* shouldCloneAppBePresent */ false, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
    }

    /**
     * Intent for Intent.ACTION_VIEW should be resolved across both profiles, but in this case only
     * owner app is installed. Result from both profile should resolve to owner app.
     * @throws Exception
     */
    @Test
    public void testActionViewRedirectionOnlyInOwnerProfile() throws Exception {

        String intentAction = "android.intent.action.VIEW";
        installPackage(OWNER_PROFILE_APP, "--user " + Integer.valueOf(OWNER_USER_ID));

        // Intent in owner profile should be resolved in both profiles but has only owner app
        // installed
        queryIntentForUser(intentAction, OWNER_USER_ID,
                /* shouldCloneAppBePresent */ false, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
        // Intent in clone profile should be resolved in both profiles but has only owner app
        // installed
        queryIntentForUser(intentAction, sCloneUserId,
                /* shouldCloneAppBePresent */ false, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ true);
    }

    /**
     * Intent for Intent.ACTION_VIEW should be resolved across both profiles, but in this case only
     * clone app is installed. Result from both profile should resolve to clone app.
     * @throws Exception
     */
    @Test
    public void testActionViewRedirectionOnlyInCloneProfile() throws Exception {

        String intentAction = "android.intent.action.VIEW";
        installPackage(CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));

        // Intent in owner profile should be resolved in both profiles but has only clone app
        // installed
        queryIntentForUser(intentAction, OWNER_USER_ID,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ false,
                /* isMatchCloneProfileFlagSet */ true);
        // Intent in clone profile should be resolved in both profiles but has only clone app
        // installed
        queryIntentForUser(intentAction, sCloneUserId,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ false,
                /* isMatchCloneProfileFlagSet */ true);
    }

    /**
     * Intent for Intent.ACTION_VIEW query should search only in current profile where flag
     * MATCH_CLONE_PROFILE is not passed.
     * @throws Exception
     */
    @Test
    public void testActionViewQueryWithoutMatchCloneProfileFlag() throws Exception {
        String intentAction = "android.intent.action.VIEW";
        installPackage(CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(OWNER_PROFILE_APP, "--user " + Integer.valueOf(OWNER_USER_ID));

        // Intent in owner profile should be resolved in only owner profile
        queryIntentForUser(intentAction, OWNER_USER_ID,
                /* shouldCloneAppBePresent */ false, /* shouldOwnerAppBePresent */ true,
                /* isMatchCloneProfileFlagSet */ false);
        // Intent in clone profile should be resolved in only clone profile
        queryIntentForUser(intentAction, sCloneUserId,
                /* shouldCloneAppBePresent */ true, /* shouldOwnerAppBePresent */ false,
                /* isMatchCloneProfileFlagSet */ false);
    }

    /**
     * Run device side test which initiate query for intent and verify if the specified apps are
     * in the result.
     * @param intentAction action for the intent request
     * @param userId initiating user
     * @param shouldCloneAppBePresent true if clone app should be present in query result
     * @param shouldOwnerAppBePresent true if owner app should be present in query result
     * @throws Exception
     */
    private void queryIntentForUser(String intentAction, String userId,
            boolean shouldCloneAppBePresent, boolean shouldOwnerAppBePresent,
            boolean isMatchCloneProfileFlagSet) throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put("intent_action", intentAction);
        args.put("user_id", userId);
        args.put("clone_app_present", String.valueOf(shouldCloneAppBePresent));
        args.put("owner_app_present", String.valueOf(shouldOwnerAppBePresent));
        args.put("match_clone_profile_flag", String.valueOf(isMatchCloneProfileFlagSet));

        runDeviceTestAsUser(INTENT_REDIRECTION_TEST_PACKAGE,
                INTENT_REDIRECTION_TEST_PACKAGE + "." + INTENT_REDIRECTION_TEST_CLASS,
                "testIntentResolutionForUser", Integer.valueOf(userId), args);
    }

    /**
     * Sets settings_allow_intent_redirection_for_clone_profile to true
     * @throws Exception
     */
    private static void switchOnIntentRedirectionFlag() throws Exception {
        setFeatureFlagValue("app_cloning", "allow_intent_redirection_for_clone_profile", "true");
    }

    /**
     * Sets settings_allow_intent_redirection_for_clone_profile to false
     * @throws Exception
     */
    private static void switchOffIntentRedirectionFlag() throws Exception {
        setFeatureFlagValue("app_cloning", "allow_intent_redirection_for_clone_profile",
                "false");
    }
}
