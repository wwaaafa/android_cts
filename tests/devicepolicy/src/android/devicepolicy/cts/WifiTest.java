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

package android.devicepolicy.cts;

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ADD_WIFI_CONFIG;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CHANGE_WIFI_STATE;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_TETHERING;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_WIFI;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_WIFI_DIRECT;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_WIFI_TETHERING;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowAddWifiConfig;
import com.android.bedstead.harrier.policies.DisallowChangeWifiState;
import com.android.bedstead.harrier.policies.DisallowConfigTethering;
import com.android.bedstead.harrier.policies.DisallowConfigWifi;
import com.android.bedstead.harrier.policies.DisallowWifiDirect;
import com.android.bedstead.harrier.policies.DisallowWifiTethering;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalTetheringSettingsStep;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalWifiDirectSettingsStep;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalWifiSettingsStep;
import com.android.interactive.steps.settings.CanYouAddWifiConfigStep;
import com.android.interactive.steps.settings.CanYouChangeWifiSettingsStep;
import com.android.interactive.steps.settings.CanYouEnableWifiTetheringStep;
import com.android.interactive.steps.settings.CanYouSwitchWifiOffStep;
import com.android.interactive.steps.settings.CanYouUseWifiDirectStep;
import com.android.interactive.steps.settings.IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep;
import com.android.interactive.steps.settings.SwitchWifiOffStep;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class WifiTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = DisallowConfigWifi.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void setUserRestriction_disallowConfigWifi_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_WIFI));
    }

    @PolicyAppliesTest(policy = DisallowConfigWifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void setUserRestriction_disallowConfigWifi_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_WIFI);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_WIFI))
                    .isTrue();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_WIFI);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigWifi.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void setUserRestriction_disallowConfigWifi_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_WIFI);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_WIFI))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_WIFI);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void disallowConfigWifiIsNotSet_canConfigWifi() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouChangeWifiSettingsStep.class)).isTrue();
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_WIFI)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_WIFI")
    public void disallowConfigWifiIsSet_canNotConfigWifi() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouChangeWifiSettingsStep.class)).isFalse();
        assertThat(
                Step.execute(IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
                .isTrue();
    }

    @CannotSetPolicyTest(policy = DisallowChangeWifiState.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void setUserRestriction_disallowChangeWifiState_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CHANGE_WIFI_STATE));
    }

    @PolicyAppliesTest(policy = DisallowChangeWifiState.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void setUserRestriction_disallowChangeWifiState_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CHANGE_WIFI_STATE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CHANGE_WIFI_STATE))
                    .isTrue();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CHANGE_WIFI_STATE);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowChangeWifiState.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void setUserRestriction_disallowChangeWifiState_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CHANGE_WIFI_STATE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CHANGE_WIFI_STATE))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CHANGE_WIFI_STATE);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CHANGE_WIFI_STATE)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @EnsureWifiEnabled
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void disallowChangeWifiStateIsNotSet_canChangeWifiState() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        Step.execute(SwitchWifiOffStep.class);
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureHasUserRestriction(DISALLOW_CHANGE_WIFI_STATE)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @EnsureWifiEnabled
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE")
    public void disallowChangeWifiStateIsSet_canNotChangeWifiState() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouSwitchWifiOffStep.class)).isFalse();
        assertThat(
                Step.execute(IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
                .isTrue();
    }

    @CannotSetPolicyTest(policy = DisallowWifiTethering.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    public void setUserRestriction_disallowWifiTethering_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_WIFI_TETHERING));
    }

    @PolicyAppliesTest(policy = DisallowWifiTethering.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    public void setUserRestriction_disallowWifiTethering_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_WIFI_TETHERING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_WIFI_TETHERING))
                    .isTrue();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_WIFI_TETHERING);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowWifiTethering.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    public void setUserRestriction_disallowWifiTethering_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_WIFI_TETHERING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_WIFI_TETHERING))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_WIFI_TETHERING);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_WIFI_TETHERING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    @NotFullyAutomated(reason = "CanYouEnableWifiTetheringStep") // TODO: Automate
    public void disallowWifiTetheringIsNotSet_canEnableWifiTethering() throws Exception {
        Step.execute(NavigateToPersonalTetheringSettingsStep.class);

        assertThat(
                Step.execute(CanYouEnableWifiTetheringStep.class))
                .isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureHasUserRestriction(DISALLOW_WIFI_TETHERING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_TETHERING")
    @NotFullyAutomated(reason = "CanYouEnableWifiTetheringStep") // TODO: Automate
    public void disallowWifiTetheringIsSet_canNotEnableWifiTethering() throws Exception {
        Step.execute(NavigateToPersonalTetheringSettingsStep.class);

        assertThat(
                Step.execute(CanYouEnableWifiTetheringStep.class))
                .isFalse();
        assertThat(
                Step.execute(IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
                .isTrue();
    }

    @CannotSetPolicyTest(policy = DisallowConfigTethering.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void setUserRestriction_disallowConfigTethering_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_TETHERING));
    }

    @PolicyAppliesTest(policy = DisallowConfigTethering.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void setUserRestriction_disallowConfigTethering_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_TETHERING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_TETHERING))
                    .isTrue();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_TETHERING);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigTethering.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void setUserRestriction_disallowConfigTethering_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_TETHERING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_TETHERING))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_TETHERING);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_TETHERING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void disallowConfigTetheringIsNotSet_todo() throws Exception {
//        Step.execute(NavigateToPersonalTetheringSettingsStep.class);
//
//        assertThat(
//                Step.execute(CanYouEnableWifiTetheringStep.class))
//                .isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_WIFI_TETHERING)
    @EnsureHasUserRestriction(DISALLOW_CONFIG_TETHERING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_TETHERING")
    public void disallowConfigTetheringIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowWifiDirect.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    public void setUserRestriction_disallowWifiDirect_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_WIFI_DIRECT));
    }

    @PolicyAppliesTest(policy = DisallowWifiDirect.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    public void setUserRestriction_disallowWifiDirect_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_WIFI_DIRECT);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_WIFI_DIRECT))
                    .isTrue();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_WIFI_DIRECT);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowWifiDirect.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    public void setUserRestriction_disallowWifiDirect_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_WIFI_DIRECT);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_WIFI_DIRECT))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_WIFI_DIRECT);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_WIFI_DIRECT)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    @NotFullyAutomated(reason = "NavigateToPersonalWifiDirectSettingsStep") // TODO: Automate
    public void disallowWifiDirectIsNotSet_canUseWifiDirect() throws Exception {
        Step.execute(NavigateToPersonalWifiDirectSettingsStep.class);

        assertThat(Step.execute(CanYouUseWifiDirectStep.class)).isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureHasUserRestriction(DISALLOW_WIFI_DIRECT)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_WIFI_DIRECT")
    @NotFullyAutomated(reason = "NavigateToPersonalWifiDirectSettingsStep") // TODO: Automate
    public void disallowWifiDirectIsSet_canNotUseWifiDirect() throws Exception {
        Step.execute(NavigateToPersonalWifiDirectSettingsStep.class);

        assertThat(Step.execute(CanYouUseWifiDirectStep.class)).isFalse();
        assertThat(
                Step.execute(IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
                .isTrue();
    }

    @CannotSetPolicyTest(policy = DisallowAddWifiConfig.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    public void setUserRestriction_disallowAddWifiConfig_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_ADD_WIFI_CONFIG));
    }

    @PolicyAppliesTest(policy = DisallowAddWifiConfig.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    public void setUserRestriction_disallowAddWifiConfig_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ADD_WIFI_CONFIG);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_WIFI_CONFIG))
                    .isTrue();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ADD_WIFI_CONFIG);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowAddWifiConfig.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    public void setUserRestriction_disallowAddWifiConfig_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ADD_WIFI_CONFIG);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_WIFI_CONFIG))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ADD_WIFI_CONFIG);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_ADD_WIFI_CONFIG)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    @NotFullyAutomated(reason = "CanYouAddWifiConfigStep") // TODO: Automate
    public void disallowAddWifiConfigIsNotSet_canAddWifiConfig() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouAddWifiConfigStep.class)).isTrue();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_WIFI)
    @EnsureHasUserRestriction(DISALLOW_ADD_WIFI_CONFIG)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG")
    @NotFullyAutomated(reason = "CanYouAddWifiConfigStep") // TODO: Automate
    public void disallowAddWifiConfigIsSet_canNotAddWifiConfig() throws Exception {
        Step.execute(NavigateToPersonalWifiSettingsStep.class);

        assertThat(Step.execute(CanYouAddWifiConfigStep.class)).isFalse();
    }

}
