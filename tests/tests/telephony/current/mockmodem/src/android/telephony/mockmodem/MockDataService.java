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

package android.telephony.mockmodem;

import android.content.Context;
import android.hardware.radio.RadioError;
import android.hardware.radio.data.DataCallFailCause;
import android.hardware.radio.data.DataProfileInfo;
import android.hardware.radio.data.LinkAddress;
import android.hardware.radio.data.PdpProtocolType;
import android.hardware.radio.data.Qos;
import android.hardware.radio.data.QosSession;
import android.hardware.radio.data.SetupDataCallResult;
import android.hardware.radio.data.SliceInfo;
import android.hardware.radio.data.TrafficDescriptor;
import android.os.SystemProperties;
import android.telephony.cts.util.TelephonyUtils;
import android.util.Log;
import android.util.Xml;

import androidx.test.platform.app.InstrumentationRegistry;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class MockDataService {
    private static final String TAG = "MockDataService";
    private Context mContext;

    // Data Profile
    List<DataProfileInfo> mDataProfileInfo = new ArrayList<>();
    DataProfileInfo mInitialAttachProfile;
    int mInitialAttachProfileId;

    // setup data call
    public static final int APN_TYPE_IMS = 0;
    public static final int APN_TYPE_DEFAULT = 1;
    public static final int CID_IMS = 0;
    public static final int CID_DEFAULT = 1;
    SetupDataCallResult mSetupDataCallResultIms = new SetupDataCallResult();
    SetupDataCallResult mSetupDataCallResultDefault = new SetupDataCallResult();

    // data call list
    static List<SetupDataCallResult> sDataCallLists = new ArrayList<>();

    /* Mock Data Config XML TAG definition */
    private static final String MOCK_DATA_CONFIG_TAG = "MockDataConfig";
    private static final String MOCK_CUTTLEFISH_CONFIG_TAG = "CuttlefishConfig";
    private static final String MOCK_CUTTLEFISH_TYPE = "Cuttlefish";
    private static final String MOCK_INTERFACE_NAME_TAG = "InterfaceName";
    private static final String MOCK_IP_ADDRESS_TAG = "IpAddress";
    private static final String MOCK_DNS_ADDRESS_TAG = "DnsAddress";
    private static final String MOCK_GATEWAY_ADDRESS_TAG = "GatewayAddress";
    private static final String MOCK_MTU_V4_TAG = "MtuV4";
    private static String sQueryNetworkAgentCommand = "dumpsys connectivity";

    // Setup data call result parameteres
    int mDataCallFailCause;
    int mSuggestedRetryTime;
    int mImsType;
    String mImsIfname;
    String mImsAddress;
    String[] mImsGateways;
    String[] mImsPcscf;
    int mImsMtuV4;
    int mImsMtuV6;
    int mInternetType;
    String mInternetIfname;
    String mInternetAddress;
    String[] mInternetDnses;
    String[] mInternetGateways;
    int mInternetMtuV4;
    int mInternetMtuV6;
    int mAddressProperties;
    long mLaDeprecationTime;
    long mLaExpirationTime;
    Qos mDefaultQos;
    QosSession[] mQosSessions;
    byte mHandoverFailureMode;
    int mPduSessionId;
    SliceInfo mSliceInfo;
    TrafficDescriptor[] mTrafficDescriptors;
    String mCuttlefishInterfaceName;
    String mCuttlefishIpAddress;
    String mCuttlefishDnsAddress;
    String mCuttlefishGatewayAddress;
    int mCuttlefishMtuV4;
    private final Object mDataCallListLock = new Object();
    LinkAddress[] mImsLinkAddress;
    LinkAddress[] mDefaultLinkAddress;

    public MockDataService(Context context) {
        mContext = context;
        initializeParameter();

        try {
            setDataCallListFromNetworkAgent();
        } catch (Exception e) {
            Log.e(TAG, "Exception error: " + e);
        }
    }

    private void setDataCallListFromNetworkAgent() throws Exception {
        String result =
                TelephonyUtils.executeShellCommand(
                        InstrumentationRegistry.getInstrumentation(), sQueryNetworkAgentCommand);
        setBridgeTheDataConnection(result);
    }

    /* Default value definition */
    private void initializeParameter() {
        loadCuttlefishData();
        this.mDataCallFailCause = DataCallFailCause.NONE;
        this.mSuggestedRetryTime = -1;

        this.mImsType = PdpProtocolType.IP;
        this.mImsIfname = "mock_network0";
        this.mImsAddress = "192.168.66.2";
        this.mImsGateways = new String[] {"0.0.0.0"};
        this.mImsPcscf = new String[] {"192.168.66.100", "192.168.66.101"};
        this.mImsMtuV4 = 1280;
        this.mImsMtuV6 = 0;
        this.mImsLinkAddress = new LinkAddress[1];

        this.mInternetType = PdpProtocolType.IP;
        this.mInternetIfname = "mock_network1";
        this.mInternetAddress = "192.168.66.1";
        this.mInternetDnses = new String[] {"8.8.8.8"};
        this.mInternetGateways = new String[] {"0.0.0.0"};
        this.mInternetMtuV4 = 1280;
        this.mInternetMtuV6 = 0;
        this.mDefaultLinkAddress = new LinkAddress[1];

        this.mAddressProperties = LinkAddress.ADDRESS_PROPERTY_NONE;
        this.mLaDeprecationTime = 0x7FFFFFFF;
        this.mLaExpirationTime = 0x7FFFFFFF;

        this.mDefaultQos = new Qos();
        this.mQosSessions = new QosSession[0];
        this.mHandoverFailureMode =
                SetupDataCallResult.HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER;
        this.mPduSessionId = 0;
        this.mSliceInfo = null;
        this.mTrafficDescriptors = new TrafficDescriptor[0];
    }

    public int setDataProfileInfo(DataProfileInfo[] dataProfilesInfo) {
        int result = RadioError.NONE;
        if (dataProfilesInfo != null) {
            mDataProfileInfo.clear();
            for (DataProfileInfo dp : dataProfilesInfo) {
                mDataProfileInfo.add(dp);
                Log.d(TAG, "setDataProfileInfo: profileId=" + dp.profileId + ", " + dp.apn);
            }
            return result;
        }
        return RadioError.INVALID_ARGUMENTS;
    }

    public int setInitialAttachProfile(DataProfileInfo dataProfileInfo) {
        int result = RadioError.NONE;
        if (dataProfileInfo != null) {
            Log.d(TAG, "setInitialAttachProfile: profileId=" + dataProfileInfo.profileId);
            mInitialAttachProfile = dataProfileInfo;
            mInitialAttachProfileId = dataProfileInfo.profileId;
            return result;
        }
        return RadioError.INVALID_ARGUMENTS;
    }

    SetupDataCallResult setupDataCall(int apnType) {
        Log.d(TAG, "getNetwork: apnType= " + apnType);

        checkExistDataCall(apnType);

        SetupDataCallResult dc = new SetupDataCallResult();
        dc.cause = this.mDataCallFailCause;
        dc.suggestedRetryTime = this.mSuggestedRetryTime;

        dc.active = SetupDataCallResult.DATA_CONNECTION_STATUS_ACTIVE;
        LinkAddress[] arrayLinkAddress = new LinkAddress[1];
        LinkAddress linkAddress = new LinkAddress();

        switch (apnType) {
            case APN_TYPE_IMS:
                dc.cid = CID_IMS;
                dc.type = this.mImsType;
                dc.ifname = this.mImsIfname;
                linkAddress.address = this.mImsAddress;
                linkAddress.addressProperties = this.mAddressProperties;
                linkAddress.deprecationTime = this.mLaDeprecationTime;
                linkAddress.expirationTime = this.mLaExpirationTime;
                arrayLinkAddress[0] = linkAddress;
                dc.addresses = this.mImsLinkAddress;
                dc.gateways = this.mImsGateways;
                dc.pcscf = this.mImsPcscf;
                dc.mtuV4 = this.mImsMtuV4;
                dc.mtuV6 = this.mImsMtuV6;
                break;
            case APN_TYPE_DEFAULT:
                dc.cid = CID_DEFAULT;
                dc.type = this.mInternetType;
                dc.ifname = this.mInternetIfname;
                linkAddress.address = this.mInternetAddress;
                linkAddress.addressProperties = this.mAddressProperties;
                linkAddress.deprecationTime = this.mLaDeprecationTime;
                linkAddress.expirationTime = this.mLaExpirationTime;
                arrayLinkAddress[0] = linkAddress;
                dc.addresses = this.mDefaultLinkAddress;
                dc.dnses = this.mInternetDnses;
                dc.gateways = this.mInternetGateways;
                dc.mtuV4 = this.mInternetMtuV4;
                dc.mtuV6 = this.mInternetMtuV6;
                // We have two use cases, one is Cuttlefish instance and second one is real device.
                // Since the Cuttlefish interface name/ipaddress/dns/gateway/mtu are the fixed
                // value, so we write it in the mock_data_service.xml and initialize when
                // constructor.
                if (isCuttlefishInstance()) {
                    dc.ifname = getCuttlefishInterfaceName();
                    arrayLinkAddress = new LinkAddress[1];
                    linkAddress = new LinkAddress();
                    linkAddress.address = getCuttlefishIpAddress();
                    dc.dnses = new String[] {getCuttlefishDnsAddress()};
                    dc.gateways = new String[] {getCuttlefishGatewayAddress()};
                    dc.mtuV4 = getCuttlefishMtuV4();
                    linkAddress.addressProperties = this.mAddressProperties;
                    linkAddress.deprecationTime = this.mLaDeprecationTime;
                    linkAddress.expirationTime = this.mLaExpirationTime;
                    arrayLinkAddress[0] = linkAddress;
                    dc.addresses = arrayLinkAddress;
                }
                break;
            default:
                Log.d(TAG, "Unexpected APN type: " + apnType);
                return new SetupDataCallResult();
        }
        dc.defaultQos = this.mDefaultQos;
        dc.qosSessions = this.mQosSessions;
        dc.handoverFailureMode = this.mHandoverFailureMode;
        dc.pduSessionId = this.mPduSessionId;
        dc.sliceInfo = this.mSliceInfo;
        dc.trafficDescriptors = this.mTrafficDescriptors;
        synchronized (mDataCallListLock) {
            sDataCallLists.add(dc);
        }
        return dc;
    }

    /**
     * Get data call list changed
     *
     * @return The SetupDataCallResult array list.
     */
    List<SetupDataCallResult> getDataCallListChanged() {
        List<SetupDataCallResult> dataCallLists;
        synchronized (mDataCallListLock) {
            dataCallLists = getDataCallList();
        }
        return dataCallLists;
    }

    List<SetupDataCallResult> getDataCallList() {
        return sDataCallLists;
    }

    void deactivateDataCall(int cid, int reason) {
        synchronized (mDataCallListLock) {
            Iterator<SetupDataCallResult> it = sDataCallLists.iterator();
            while (it.hasNext()) {
                SetupDataCallResult dc = it.next();
                if (dc.cid == cid) {
                    it.remove();
                }
            }
        }
    }

    void checkExistDataCall(int apnType) {
        synchronized (mDataCallListLock) {
            Iterator<SetupDataCallResult> it = sDataCallLists.iterator();
            while (it.hasNext()) {
                SetupDataCallResult dc = it.next();
                if (dc.cid == apnType) {
                    it.remove();
                }
            }
        }
    }

    /* check if the instance is Cuttlefish */
    boolean isCuttlefishInstance() {
        String vendorManufacturer = SystemProperties.get("ro.product.vendor.manufacturer");
        String vendorModel = SystemProperties.get("ro.product.vendor.model");
        String vendorProduct = SystemProperties.get("ro.product.vendor.name");
        // Return true for cuttlefish instances
        if ((vendorManufacturer.equals("Android") || vendorManufacturer.equals("Google"))
                && (vendorProduct.startsWith("cf_")
                        || vendorProduct.startsWith("aosp_cf_")
                        || vendorModel.startsWith("Cuttlefish "))) {
            Log.d(TAG, "CuttlefishInstance");
            return true;
        }
        return false;
    }

    private boolean loadCuttlefishData() {
        boolean result = true;
        try {
            String file = "mock_data_service.xml";
            int event;
            XmlPullParser parser = Xml.newPullParser();
            InputStream input;
            input = mContext.getAssets().open(file);
            parser.setInput(input, null);

            while (result && (event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (MOCK_DATA_CONFIG_TAG.equals(parser.getName())) {
                            Log.d(TAG, "Enter XML file.");
                        } else if (MOCK_CUTTLEFISH_CONFIG_TAG.equals(parser.getName())) {
                            String type = parser.getAttributeValue(0);
                            if (!MOCK_CUTTLEFISH_TYPE.equals(type)) {
                                result = false;
                                break;
                            }
                        } else if (MOCK_INTERFACE_NAME_TAG.equals(parser.getName())) {
                            String interfacename = parser.nextText();
                            this.mCuttlefishInterfaceName = interfacename;
                        } else if (MOCK_IP_ADDRESS_TAG.equals(parser.getName())) {
                            String ipaddress = parser.nextText();
                            this.mCuttlefishIpAddress = ipaddress;
                        } else if (MOCK_DNS_ADDRESS_TAG.equals(parser.getName())) {
                            String dnsaddress = parser.nextText();
                            this.mCuttlefishDnsAddress = dnsaddress;
                        } else if (MOCK_GATEWAY_ADDRESS_TAG.equals(parser.getName())) {
                            String gatewayaddress = parser.nextText();
                            this.mCuttlefishGatewayAddress = gatewayaddress;
                        } else if (MOCK_MTU_V4_TAG.equals(parser.getName())) {
                            String mtuv4 = parser.nextText();
                            this.mCuttlefishMtuV4 = convertToMtuV4(mtuv4);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                }
            }
            input.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception error: " + e);
            result = false;
        }
        return result;
    }

    private String getCuttlefishInterfaceName() {
        return this.mCuttlefishInterfaceName;
    }

    private String getCuttlefishIpAddress() {
        return this.mCuttlefishIpAddress;
    }

    private String getCuttlefishDnsAddress() {
        return this.mCuttlefishDnsAddress;
    }

    private String getCuttlefishGatewayAddress() {
        return this.mCuttlefishGatewayAddress;
    }

    private int getCuttlefishMtuV4() {
        return this.mCuttlefishMtuV4;
    }

    private int convertToMtuV4(String mtuv4) {
        int value = 0;
        try {
            value = Integer.parseInt(mtuv4);
        } catch (NumberFormatException ex) {
            Log.e(TAG, "Exception error: " + ex);
        }
        return value;
    }

    private String getInterfaceName(String string) {
        String interfaceName = "";
        try {
            interfaceName = string.split("InterfaceName: ")[1].split(" LinkAddresses")[0];
            Log.d(TAG, "getInterfaceName: " + interfaceName);
        } catch (Exception e) {
            Log.e(TAG, "Exception error: " + e);
        }
        return interfaceName;
    }

    private LinkAddress[] getIpAddress(String string) {
        String[] ipaddress = new String[] {};
        LinkAddress[] arrayLinkAddress = new LinkAddress[0];
        try {
            ipaddress =
                    string.split("LinkAddresses: \\[ ")[1].split(" ] DnsAddresses")[0].split(",");
            arrayLinkAddress = new LinkAddress[ipaddress.length];
            for (int idx = 0; idx < ipaddress.length; idx++) {
                if (ipaddress[idx].equals("LinkAddresses: [ ]")) {
                    throw new Exception("Not valid ip address");
                }
                LinkAddress linkAddress = new LinkAddress();
                linkAddress.address = ipaddress[idx];
                linkAddress.addressProperties = this.mAddressProperties;
                linkAddress.deprecationTime = this.mLaDeprecationTime;
                linkAddress.expirationTime = this.mLaExpirationTime;
                arrayLinkAddress[idx] = linkAddress;
                Log.d(TAG, "getIpAddress:" + linkAddress.address);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception error: " + e);
        }
        return arrayLinkAddress;
    }

    private String[] getDnses(String string) {
        String[] dnses = new String[] {};
        try {
            dnses =
                    string.split("DnsAddresses: \\[ ")[1]
                            .split(" ] Domains:")[0]
                            .replace("/", "")
                            .split(",");
            Log.d(TAG, "getDnses: " + Arrays.toString(dnses));
        } catch (Exception e) {
            Log.e(TAG, "Exception error: " + e);
        }
        return dnses;
    }

    private String[] getGateways() {
        return new String[] {"0.0.0.0"};
    }

    private int getMtu(String string) {
        String mtu = "";
        try {
            mtu = string.split(" MTU: ")[1].split(" TcpBufferSizes:")[0];
            Log.d(TAG, "getMtu: " + mtu);
        } catch (Exception e) {
            Log.e(TAG, "Exception error: " + e);
        }
        return Integer.valueOf(mtu);
    }

    private String[] getPcscf(String string) {
        String[] pcscf = new String[] {};
        try {
            pcscf =
                    string.split(" PcscfAddresses: \\[ ")[1]
                            .split(" ] Domains:")[0]
                            .replace("/", "")
                            .split(",");
            Log.e(TAG, "getPcscf: " + Arrays.toString(pcscf));
        } catch (Exception e) {
            Log.e(TAG, "Exception error: " + e);
        }
        return pcscf;
    }

    private String getCapabilities(String string) {
        String capabilities = "";
        try {
            capabilities = string.trim().split("Capabilities:")[1].split("LinkUpBandwidth")[0];
        } catch (Exception e) {
            Log.e(TAG, "getCapabilities(): Exception error: " + e);
        }
        return capabilities;
    }

    public synchronized void setBridgeTheDataConnection(String string) {
        try {
            String[] lines = string.split("NetworkAgentInfo\\{network\\{");
            for (String str : lines) {
                String capabilities = getCapabilities(str);
                Log.e(TAG, "capabilities: " + capabilities);
                if (capabilities.contains("INTERNET")) {
                    Log.d(TAG, "[internet]:" + str);
                    this.mInternetIfname = getInterfaceName(str);
                    this.mDefaultLinkAddress = getIpAddress(str);
                    this.mInternetDnses = getDnses(str);
                    this.mInternetGateways = getGateways();
                    this.mInternetMtuV4 = getMtu(str);
                    this.mInternetMtuV6 = getMtu(str);
                } else if (capabilities.contains("IMS")) {
                    Log.d(TAG, "[ims]:" + str);
                    this.mImsIfname = getInterfaceName(str);
                    this.mImsLinkAddress = getIpAddress(str);
                    this.mImsGateways = getGateways();
                    this.mImsPcscf = getPcscf(str);
                    this.mImsMtuV4 = getMtu(str);
                    this.mImsMtuV6 = getMtu(str);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception error: [No NetworkAgentInfo]" + e);
        }
    }

    public synchronized void setDataCallFailCause(int failcause) {
        this.mDataCallFailCause = failcause;
    }

    public synchronized void setSuggestedRetryTime(int retrytime) {
        this.mSuggestedRetryTime = retrytime;
    }

    public synchronized void setImsMtuV4(int mtusize) {
        this.mImsMtuV4 = mtusize;
    }

    public synchronized void setImsMtuV6(int mtusize) {
        this.mImsMtuV6 = mtusize;
    }

    public synchronized void setInternetMtuV4(int mtusize) {
        this.mInternetMtuV4 = mtusize;
    }

    public synchronized void setInternetMtuV6(int mtusize) {
        this.mInternetMtuV6 = mtusize;
    }
}
