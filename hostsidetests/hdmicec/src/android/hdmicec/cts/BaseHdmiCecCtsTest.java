/*
 * Copyright 2020 The Android Open Source Project
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

package android.hdmicec.cts;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.rules.TestRule;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base class for all HDMI CEC CTS tests. */
@OptionClass(alias="hdmi-cec-client-cts-test")
public class BaseHdmiCecCtsTest extends BaseHostJUnit4Test {

    public static final String PROPERTY_LOCALE = "persist.sys.locale";

    /** Enum contains the list of possible address types. */
    private enum AddressType {
        DUMPSYS_LOGICAL_ADDRESS("logicalAddress"),
        DUMPSYS_AS_LOGICAL_ADDRESS("activeSourceLogicalAddress"),
        DUMPSYS_PHYSICAL_ADDRESS("physicalAddress");

        private String address;

        public String getAddressType() {
            return this.address;
        }

        private AddressType(String address) {
            this.address = address;
        }
    }

    public final HdmiCecClientWrapper hdmiCecClient;
    public LogicalAddress mDutLogicalAddress;

    /**
     * Constructor for BaseHdmiCecCtsTest. Uses the DUT logical address for the test.
     */
    public BaseHdmiCecCtsTest() {
        this(LogicalAddress.UNKNOWN);
    }

    /**
     * Constructor for BaseHdmiCecCtsTest. Uses the DUT logical address for the test.
     *
     * @param clientParams Extra parameters to use when launching cec-client
     */
    public BaseHdmiCecCtsTest(String ...clientParams) {
        this(LogicalAddress.UNKNOWN, clientParams);
    }

    /**
     * Constructor for BaseHdmiCecCtsTest.
     *
     * @param dutLogicalAddress The logical address that the DUT will have.
     * @param clientParams Extra parameters to use when launching cec-client
     */
    public BaseHdmiCecCtsTest(LogicalAddress dutLogicalAddress, String ...clientParams) {
        this.hdmiCecClient = new HdmiCecClientWrapper(clientParams);
        mDutLogicalAddress = dutLogicalAddress;
    }

    @Before
    public void setUp() throws Exception {
        setCec14();

        if (mDutLogicalAddress == LogicalAddress.UNKNOWN) {
            mDutLogicalAddress = LogicalAddress.getLogicalAddress(getDumpsysLogicalAddress());
        }
        hdmiCecClient.setTargetLogicalAddress(mDutLogicalAddress);
        boolean startAsTv =
                mDutLogicalAddress.getDeviceType() != HdmiCecConstants.CEC_DEVICE_TYPE_TV;
        hdmiCecClient.init(startAsTv, getDevice());
    }

    /** Class with predefined rules which can be used by HDMI CEC CTS tests. */
    public static class CecRules {

        public static TestRule requiresCec(BaseHostJUnit4Test testPointer) {
            return new RequiredFeatureRule(testPointer, HdmiCecConstants.HDMI_CEC_FEATURE);
        }

        public static TestRule requiresLeanback(BaseHostJUnit4Test testPointer) {
            return new RequiredFeatureRule(testPointer, HdmiCecConstants.LEANBACK_FEATURE);
        }

        public static TestRule requiresDeviceType(BaseHostJUnit4Test testPointer,
                                                  LogicalAddress dutLogicalAddress) {
            return RequiredPropertyRule.asCsvContainsValue(
                        testPointer,
                        HdmiCecConstants.HDMI_DEVICE_TYPE_PROPERTY,
                        dutLogicalAddress.getDeviceTypeString());
        }

        /** This rule will skip the test if the DUT belongs to the HDMI device type deviceType. */
        public static TestRule skipDeviceType(BaseHostJUnit4Test testPointer, int deviceType) {
            return RequiredPropertyRule.asCsvDoesNotContainsValue(
                    testPointer,
                    HdmiCecConstants.HDMI_DEVICE_TYPE_PROPERTY,
                    Integer.toString(deviceType));
        }
    }

    @Option(name = HdmiCecConstants.PHYSICAL_ADDRESS_NAME,
        description = "HDMI CEC physical address of the DUT",
        mandatory = false)
    public static int dutPhysicalAddress = HdmiCecConstants.DEFAULT_PHYSICAL_ADDRESS;

    /** Gets the physical address of the DUT by parsing the dumpsys hdmi_control. */
    public int getDumpsysPhysicalAddress() throws Exception {
        return getDumpsysPhysicalAddress(getDevice());
    }

    /** Gets the physical address of the specified device by parsing the dumpsys hdmi_control. */
    public static int getDumpsysPhysicalAddress(ITestDevice device) throws Exception {
        return parseRequiredAddressFromDumpsys(device, AddressType.DUMPSYS_PHYSICAL_ADDRESS);
    }

    /** Gets the logical address of the DUT by parsing the dumpsys hdmi_control. */
    public int getDumpsysLogicalAddress() throws Exception {
        return getDumpsysLogicalAddress(getDevice());
    }

    /** Gets the logical address of the specified device by parsing the dumpsys hdmi_control. */
    public static int getDumpsysLogicalAddress(ITestDevice device) throws Exception {
        return parseRequiredAddressFromDumpsys(device, AddressType.DUMPSYS_LOGICAL_ADDRESS);
    }

    /**
     * Parses the dumpsys hdmi_control to get the logical address of the current device registered
     * as active source.
     */
    public LogicalAddress getDumpsysActiveSourceLogicalAddress() throws Exception {
        ITestDevice device = getDevice();
        int address =
                parseRequiredAddressFromDumpsys(device, AddressType.DUMPSYS_AS_LOGICAL_ADDRESS);
        return LogicalAddress.getLogicalAddress(address);
    }

    private static int parseRequiredAddressFromDumpsys(ITestDevice device, AddressType addressType)
            throws Exception {
        Matcher m;
        String line;
        String pattern;
        switch (addressType) {
            case DUMPSYS_LOGICAL_ADDRESS:
                pattern =
                        "(.*?)"
                                + "(mAddress: )"
                                + "(?<"
                                + addressType.getAddressType()
                                + ">\\p{XDigit}{1})"
                                + "(.*?)";
                break;
            case DUMPSYS_PHYSICAL_ADDRESS:
                pattern =
                        "(.*?)"
                                + "(physical_address: )"
                                + "(?<"
                                + addressType.getAddressType()
                                + ">0x\\p{XDigit}{4})"
                                + "(.*?)";
                break;
            case DUMPSYS_AS_LOGICAL_ADDRESS:
                pattern =
                        "(.*?)"
                                + "(mActiveSource: )"
                                + "(\\(0x)"
                                + "(?<"
                                + addressType.getAddressType()
                                + ">\\d+)"
                                + "(, )"
                                + "(0x)"
                                + "(?<physicalAddress>\\d+)"
                                + "(\\))"
                                + "(.*?)";
                break;
            default:
                throw new IllegalArgumentException("Incorrect parameters");
        }

        try {
            Pattern p = Pattern.compile(pattern);
            String dumpsys = device.executeShellCommand("dumpsys hdmi_control");
            BufferedReader reader = new BufferedReader(new StringReader(dumpsys));
            while ((line = reader.readLine()) != null) {
                m = p.matcher(line);
                if (m.matches()) {
                    int address = Integer.decode(m.group(addressType.getAddressType()));
                    return address;
                }
            }
        } catch (Exception e) {
            throw new Exception(
                    "Parsing dumpsys for " + addressType.getAddressType() + " failed.", e);
        }
        throw new Exception("Could not parse " + addressType.getAddressType() + " from dumpsys.");
    }

    private static void setCecVersion(ITestDevice device, int cecVersion) throws Exception {
        device.executeShellCommand("cmd hdmi_control cec_setting set hdmi_cec_version " +
                cecVersion);

        TimeUnit.SECONDS.sleep(HdmiCecConstants.TIMEOUT_CEC_REINIT_SECONDS);
    }

    /**
     * Configures the device to use CEC 2.0. Skips the test if the device does not support CEC 2.0.
     * @throws Exception
     */
    public void setCec20() throws Exception {
        setCecVersion(getDevice(), HdmiCecConstants.CEC_VERSION_2_0);
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), mDutLogicalAddress,
                CecOperand.GET_CEC_VERSION);
        String reportCecVersion = hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.CEC_VERSION);
        boolean supportsCec2 = CecMessage.getParams(reportCecVersion)
                >= HdmiCecConstants.CEC_VERSION_2_0;

        // Device still reports a CEC version < 2.0.
        assumeTrue(supportsCec2);
    }

    public void setCec14() throws Exception {
        setCecVersion(getDevice(), HdmiCecConstants.CEC_VERSION_1_4);
    }

    public String getSystemLocale() throws Exception {
        ITestDevice device = getDevice();
        return device.executeShellCommand("getprop " + PROPERTY_LOCALE).trim();
    }

    public static String extractLanguage(String locale) {
        return locale.split("[^a-zA-Z]")[0];
    }

    public void setSystemLocale(String locale) throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("setprop " + PROPERTY_LOCALE + " " + locale);
    }

    public boolean isLanguageEditable() throws Exception {
        String val = getDevice().executeShellCommand(
                "getprop ro.hdmi.set_menu_language");
        return val.trim().equals("true") ? true : false;
    }

    public static String getSettingsValue(ITestDevice device, String setting) throws Exception {
        return device.executeShellCommand("cmd hdmi_control cec_setting get " + setting)
                .replace(setting + " = ", "").trim();
    }

    public String getSettingsValue(String setting) throws Exception {
        return getSettingsValue(getDevice(), setting);
    }

    public static void setSettingsValue(ITestDevice device, String setting, String value)
            throws Exception {
        device.executeShellCommand("cmd hdmi_control cec_setting set " + setting + " " +
                value);
    }

    public void setSettingsValue(String setting, String value) throws Exception {
        setSettingsValue(getDevice(), setting, value);
    }
}
