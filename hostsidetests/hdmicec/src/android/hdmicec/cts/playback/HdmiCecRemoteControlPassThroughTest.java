/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.hdmicec.cts.playback;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.RemoteControlPassthrough;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * HDMI CEC tests to ensure that the remote control passthrough to TV works as expected (Section
 * 11.2.13)
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecRemoteControlPassThroughTest extends BaseHdmiCecCtsTest {

    private static int DUT_DEVICE_TYPE = HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE;

    public HdmiCecRemoteControlPassThroughTest() {
        super(DUT_DEVICE_TYPE);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(
                            CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);

    /**
     * Test 11.2.13-1
     *
     * <p>Tests that the device responds correctly to a {@code <USER_CONTROL_PRESSED>} message
     * followed immediately by a {@code <USER_CONTROL_RELEASED>} message.
     */
    @Test
    public void cect_11_2_13_1_UserControlPressAndRelease() throws Exception {
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndRelease(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }

    /**
     * Test 11.2.13-2
     * Tests that the device responds correctly to a <USER_CONTROL_PRESSED> message for press and
     * hold operations.
     */
    @Test
    public void cect_11_2_13_2_UserControlPressAndHold() throws Exception {
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndHold(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }

    /**
     * HF 4-8-4
     *
     * <p>Verify that the device that support cec version 2.0 accepts {@code <USER_CONTROL_PRESSED>}
     * messages and maps to appropriate internal action.
     *
     * No Android keycode defined for {@code <CEC_KEYCODE_FAVORITE_MENU>},
     * {@code <CEC_KEYCODE_STOP_RECORD>} and {@code <CEC_KEYCODE_PAUSE_RECORD>}
     *
     * The UI commands Audio Description, internet and 3D mode are introduced in CEC 2.0 devices but
     * they haven't been implemented yet.
     * TODO: Add these UI commands once they are implemented.
     */
    @Test
    public void cect_4_8_4_UserControlPressAndRelease_20() throws Exception {
        setCec20();
        LogicalAddress dutLogicalAddress = getTargetLogicalAddress(getDevice(), DUT_DEVICE_TYPE);
        RemoteControlPassthrough.checkUserControlPressAndRelease_20(
                hdmiCecClient, getDevice(), LogicalAddress.TV, dutLogicalAddress);
    }
}
