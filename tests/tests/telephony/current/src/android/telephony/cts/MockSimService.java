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

package android.telephony.cts;

import android.content.Context;
import android.hardware.radio.sim.AppStatus;
import android.hardware.radio.sim.PinState;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;

public class MockSimService {
    private static final String TAG = "MockSimService";

    /* Support SIM card identify */
    public static final int MOCK_SIM_PROFILE_ID_DEFAULT = 0; // SIM Absent
    public static final int MOCK_SIM_PROFILE_ID_TWN_CHT = 1;
    public static final int MOCK_SIM_PROFILE_ID_MAX = 2;

    /* SIM profile XML TAG definition */
    private static final String MOCK_SIM_TAG = "MockSim";
    private static final String MOCK_SIM_PROFILE_TAG = "MockSimProfile";
    private static final String MOCK_PIN_PROFILE_TAG = "PinProfile";
    private static final String MOCK_PIN1_STATE_TAG = "Pin1State";
    private static final String MOCK_PIN2_STATE_TAG = "Pin2State";
    private static final String MOCK_FACILITY_LOCK_TAG = "FacilityLock";
    private static final String MOCK_FACILITY_LOCK_FD_TAG = "FD";
    private static final String MOCK_FACILITY_LOCK_SC_TAG = "SC";
    private static final String MOCK_MF_TAG = "MF";
    private static final String MOCK_EF_TAG = "EF";
    private static final String MOCK_EF_DIR_TAG = "EFDIR";
    private static final String MOCK_ADF_TAG = "ADF";

    /* Support SIM slot */
    private static final int MOCK_SIM_SLOT_1 = 0;
    private static final int MOCK_SIM_SLOT_2 = 1;
    private static final int MOCK_SIM_SLOT_3 = 2;
    public static final int MOCK_SIM_SLOT_MAX = 3;

    /* Default value definition */
    private static final int MOCK_SIM_DEFAULT_SLOTID = MOCK_SIM_SLOT_1;
    private static final int DEFAULT_NUM_OF_SIM_PORT_INfO = 1;
    private static final int DEFAULT_NUM_OF_SIM_APP = 0;
    private static final int DEFAULT_GSM_APP_IDX = -1;
    private static final int DEFAULT_CDMA_APP_IDX = -1;
    private static final int DEFAULT_IMS_APP_IDX = -1;
    // SIM1 slot status
    private static final int DEFAULT_SIM1_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM1_CARD_PRESENT = false;
    private static final String DEFAULT_SIM1_ATR = "";
    private static final String DEFAULT_SIM1_EID = "";
    private static final String DEFAULT_SIM1_ICCID = "";
    private static final boolean DEFAULT_SIM1_PORT_ACTIVE = true;
    private static final int DEFAULT_SIM1_PORT_ID = 0;
    private static final int DEFAULT_SIM1_LOGICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM1_PHYSICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM1_UNIVERSAL_PIN_STATE = 0;
    // SIM2 slot status
    private static final int DEFAULT_SIM2_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM2_CARD_PRESENT = false;
    private static final String DEFAULT_SIM2_ATR =
            "3B9F97C00A3FC6828031E073FE211F65D002341512810F51";
    private static final String DEFAULT_SIM2_EID = "89033023426200000000005430099507";
    private static final String DEFAULT_SIM2_ICCID = "";
    private static final boolean DEFAULT_SIM2_PORT_ACTIVE = false;
    private static final int DEFAULT_SIM2_PORT_ID = 0;
    private static final int DEFAULT_SIM2_LOGICAL_SLOT_ID = -1;
    private static final int DEFAULT_SIM2_PHYSICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM2_UNIVERSAL_PIN_STATE = 0;
    // SIM3 slot status
    private static final int DEFAULT_SIM3_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM3_CARD_PRESENT = false;
    private static final String DEFAULT_SIM3_ATR = "";
    private static final String DEFAULT_SIM3_EID = "";
    private static final String DEFAULT_SIM3_ICCID = "";
    private static final boolean DEFAULT_SIM3_PORT_ACTIVE = false;
    private static final int DEFAULT_SIM3_PORT_ID = 0;
    private static final int DEFAULT_SIM3_LOGICAL_SLOT_ID = -1;
    private static final int DEFAULT_SIM3_PHYSICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM3_UNIVERSAL_PIN_STATE = 0;

    private Context mContext;

    // SIM Slot status
    private int mPhysicalSlotId;
    private int mLogicalSlotId;
    private int mSlotPortId;
    private boolean mIsSlotPortActive;
    private boolean mIsCardPresent;

    /* SIM profile info */
    private SimProfileInfo[] mSimProfileInfoList;

    // SIM card data
    private int mSimProfileId;
    private String mICCID;
    private String mEID;
    private String mATR;
    private int mUniversalPinState;

    private AppStatus[] mSimApp;
    private ArrayList<SimAppData> mSimAppList;

    public class SimAppData {
        private int mSimAppId;
        private String mAid;
        private boolean mIsCurrentActive;
        private String mPath;
        private int mFdnStatus;
        private int mPin1State;

        public SimAppData(int simappid, String aid, String path) {
            mSimAppId = simappid;
            mAid = aid;
            mIsCurrentActive = false;
            mPath = path;
        }

        public int getSimAppId() {
            return mSimAppId;
        }

        public String getAid() {
            return mAid;
        }

        public boolean isCurrentActive() {
            return mIsCurrentActive;
        }

        public String getPath() {
            return mPath;
        }

        public int getFdnStatus() {
            return mFdnStatus;
        }

        public void setFdnStatus(int status) {
            mFdnStatus = status;
        }

        public int getPin1State() {
            return mPin1State;
        }

        public void setPin1State(int state) {
            mPin1State = state;
        }
    }

    public class SimProfileInfo {
        private int mSimProfileId;
        private int mNumOfSimApp;
        private int mGsmAppIndex;
        private int mCdmaAppIndex;
        private int mImsAppIndex;
        private String mXmlFile;

        public SimProfileInfo(int profileid) {
            mSimProfileId = profileid;
            mNumOfSimApp = DEFAULT_NUM_OF_SIM_APP;
            mGsmAppIndex = DEFAULT_GSM_APP_IDX;
            mCdmaAppIndex = DEFAULT_CDMA_APP_IDX;
            mImsAppIndex = DEFAULT_IMS_APP_IDX;
            mXmlFile = "";
        }

        public int getNumOfSimApp() {
            return mNumOfSimApp;
        }

        public int getGsmAppIndex() {
            return mGsmAppIndex;
        }

        public int getCdmaAppIndex() {
            return mCdmaAppIndex;
        }

        public int getImsAppIndex() {
            return mImsAppIndex;
        }

        public String getXmlFile() {
            return mXmlFile;
        }

        public void setNumOfSimApp(int number) {
            mNumOfSimApp = number;
        }

        public void setGsmAppIndex(int index) {
            mGsmAppIndex = index;
        }

        public void setCdmaAppIndex(int index) {
            mCdmaAppIndex = index;
        }

        public void setImsAppIndex(int index) {
            mImsAppIndex = index;
        }

        public void setXmlFile(String file) {
            mXmlFile = file;
        }
    }

    public MockSimService(Context context, int slotId) {
        mContext = context;
        int simprofile = DEFAULT_SIM1_PROFILE_ID;

        if (slotId >= MOCK_SIM_SLOT_MAX) {
            Log.e(
                    TAG,
                    "Invalid slot id("
                            + slotId
                            + "). Using default slot id("
                            + MOCK_SIM_DEFAULT_SLOTID
                            + ").");
            slotId = MOCK_SIM_DEFAULT_SLOTID;
        }

        // Init default SIM profile id
        switch (slotId) {
            case MOCK_SIM_SLOT_1:
                simprofile = DEFAULT_SIM1_PROFILE_ID;
                break;
            case MOCK_SIM_SLOT_2:
                simprofile = DEFAULT_SIM2_PROFILE_ID;
                break;
            case MOCK_SIM_SLOT_3:
                simprofile = DEFAULT_SIM3_PROFILE_ID;
                break;
        }

        // Initial support SIM profile list
        mSimProfileInfoList = new SimProfileInfo[MOCK_SIM_PROFILE_ID_MAX];
        for (int idx = 0; idx < MOCK_SIM_PROFILE_ID_MAX; idx++) {
            Log.d(TAG, "Create sim profile id = " + idx);
            mSimProfileInfoList[idx] = new SimProfileInfo(idx);
            switch (idx) {
                case MOCK_SIM_PROFILE_ID_TWN_CHT:
                    String filename = "mock_sim_tw_cht.xml";
                    mSimProfileInfoList[idx].setXmlFile(filename);
                    break;
                default:
                    break;
            }
        }

        // Initiate SIM card with default profile
        initMockSimCard(slotId, simprofile);
    }

    private void initMockSimCard(int slotId, int simProfileId) {
        if (slotId > MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT) {
            Log.e(
                    TAG,
                    "Physical slot id("
                            + slotId
                            + ") is invalid. Using default slot id("
                            + MOCK_SIM_DEFAULT_SLOTID
                            + ").");
            mPhysicalSlotId = MOCK_SIM_DEFAULT_SLOTID;
        } else {
            mPhysicalSlotId = slotId;
        }
        if (simProfileId >= 0 && simProfileId < MOCK_SIM_PROFILE_ID_MAX) {
            mSimProfileId = simProfileId;
            Log.i(
                    TAG,
                    "Load SIM profile ID: "
                            + mSimProfileId
                            + " into physical slot["
                            + mPhysicalSlotId
                            + "]");
        } else {
            mSimProfileId = MOCK_SIM_PROFILE_ID_DEFAULT;
            Log.e(
                    TAG,
                    "SIM Absent on physical slot["
                            + mPhysicalSlotId
                            + "]. Not support SIM card ID: "
                            + mSimProfileId);
        }

        // Initiate slot status
        initMockSimSlot();

        // Load SIM profile data
        loadMockSimCard();
    }

    private void initMockSimSlot() {
        switch (mPhysicalSlotId) {
            case MOCK_SIM_SLOT_1:
                mLogicalSlotId = DEFAULT_SIM1_LOGICAL_SLOT_ID;
                mSlotPortId = DEFAULT_SIM1_PORT_ID;
                mIsSlotPortActive = DEFAULT_SIM1_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM1_CARD_PRESENT;
                break;
            case MOCK_SIM_SLOT_2:
                mLogicalSlotId = DEFAULT_SIM2_LOGICAL_SLOT_ID;
                mSlotPortId = DEFAULT_SIM2_PORT_ID;
                mIsSlotPortActive = DEFAULT_SIM2_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM2_CARD_PRESENT;
                break;
            case MOCK_SIM_SLOT_3:
                mLogicalSlotId = DEFAULT_SIM3_LOGICAL_SLOT_ID;
                mSlotPortId = DEFAULT_SIM3_PORT_ID;
                mIsSlotPortActive = DEFAULT_SIM3_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM3_CARD_PRESENT;
                break;
        }
    }

    private int convertMockSimPinState(String pinstate) {
        int mocksim_pinstate = PinState.UNKNOWN;
        switch (pinstate) {
            case "PINSTATE_UNKNOWN":
                mocksim_pinstate = PinState.UNKNOWN;
                break;
            case "PINSTATE_ENABLED_NOT_VERIFIED":
                mocksim_pinstate = PinState.ENABLED_NOT_VERIFIED;
                break;
            case "PINSTATE_ENABLED_VERIFIED":
                mocksim_pinstate = PinState.ENABLED_VERIFIED;
                break;
            case "PINSTATE_DISABLED":
                mocksim_pinstate = PinState.DISABLED;
                break;
            case "PINSTATE_ENABLED_BLOCKED":
                mocksim_pinstate = PinState.ENABLED_BLOCKED;
                break;
            case "PINSTATE_ENABLED_PERM_BLOCKED":
                mocksim_pinstate = PinState.ENABLED_PERM_BLOCKED;
                break;
        }

        return mocksim_pinstate;
    }

    private int convertMockSimAppType(String apptype) {
        int mocksim_apptype = AppStatus.APP_TYPE_UNKNOWN;
        switch (apptype) {
            case "APPTYPE_UNKNOWN":
                mocksim_apptype = AppStatus.APP_TYPE_UNKNOWN;
                break;
            case "APPTYPE_SIM":
                mocksim_apptype = AppStatus.APP_TYPE_SIM;
                break;
            case "APPTYPE_USIM":
                mocksim_apptype = AppStatus.APP_TYPE_USIM;
                break;
            case "APPTYPE_RUIM":
                mocksim_apptype = AppStatus.APP_TYPE_RUIM;
                break;
            case "APPTYPE_CSIM":
                mocksim_apptype = AppStatus.APP_TYPE_CSIM;
                break;
            case "APPTYPE_ISIM":
                mocksim_apptype = AppStatus.APP_TYPE_ISIM;
                break;
        }

        return mocksim_apptype;
    }

    private int convertMockSimAppState(String appstate) {
        int mocksim_appstate = AppStatus.APP_STATE_UNKNOWN;
        switch (appstate) {
            case "APPSTATE_UNKNOWN":
                mocksim_appstate = AppStatus.APP_STATE_UNKNOWN;
                break;
            case "APPSTATE_DETECTED":
                mocksim_appstate = AppStatus.APP_STATE_DETECTED;
                break;
            case "APPSTATE_PIN":
                mocksim_appstate = AppStatus.APP_STATE_PIN;
                break;
            case "APPSTATE_PUK":
                mocksim_appstate = AppStatus.APP_STATE_PUK;
                break;
            case "APPSTATE_SUBSCRIPTION_PERSO":
                mocksim_appstate = AppStatus.APP_STATE_SUBSCRIPTION_PERSO;
                break;
            case "APPSTATE_READY":
                mocksim_appstate = AppStatus.APP_STATE_READY;
                break;
        }
        return mocksim_appstate;
    }

    private int convertMockSimFacilityLock(String lock) {
        int facilitylock = 0;
        switch (lock) {
            case "LOCK_ENABLED":
                facilitylock = 1;
                break;
            case "LOCK_DISABLED":
                facilitylock = 0;
                break;
        }
        return facilitylock;
    }

    private boolean loadSimProfileFromXml() {
        boolean result = true;

        if (mSimProfileInfoList == null) {
            Log.e(TAG, "No support SIM profile list.");
            return false;
        }

        try {
            String file = mSimProfileInfoList[mSimProfileId].getXmlFile();
            int event;
            XmlPullParser parser = Xml.newPullParser();
            InputStream input;
            boolean mocksim_validation = false;
            boolean mocksim_mf_validation = false;
            int appidx = 0;
            int fd_lock = 0;
            int sc_lock = 0;

            input = mContext.getAssets().open(file);
            parser.setInput(input, null);
            while (result && (event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (MOCK_SIM_TAG.equals(parser.getName())) {
                            int numofapp = Integer.parseInt(parser.getAttributeValue(0));
                            String iccid = parser.getAttributeValue(1);
                            Log.d(
                                    TAG,
                                    "Found "
                                            + MOCK_SIM_TAG
                                            + ": numofapp = "
                                            + numofapp
                                            + " iccid = "
                                            + iccid);
                            mICCID = iccid;
                            mSimApp = new AppStatus[numofapp];
                            if (mSimApp == null) {
                                Log.e(TAG, "Create SIM app failed!");
                                result = false;
                                break;
                            }
                            mocksim_validation = true;
                        } else if (mocksim_validation
                                && MOCK_SIM_PROFILE_TAG.equals(parser.getName())) {
                            int id = Integer.parseInt(parser.getAttributeValue(0));
                            int type = convertMockSimAppType(parser.getAttributeValue(1));
                            mSimApp[appidx] = new AppStatus();
                            mSimApp[appidx].appType = type;
                            switch (type) {
                                case AppStatus.APP_TYPE_SIM:
                                case AppStatus.APP_TYPE_USIM:
                                    mSimProfileInfoList[mSimProfileId].setGsmAppIndex(id);
                                    break;
                                case AppStatus.APP_TYPE_CSIM:
                                case AppStatus.APP_TYPE_RUIM:
                                    mSimProfileInfoList[mSimProfileId].setCdmaAppIndex(id);
                                    break;
                                case AppStatus.APP_TYPE_ISIM:
                                    mSimProfileInfoList[mSimProfileId].setImsAppIndex(id);
                                    break;
                            }
                            Log.d(
                                    TAG,
                                    "Found "
                                            + MOCK_SIM_PROFILE_TAG
                                            + ": id = "
                                            + id
                                            + " type = "
                                            + parser.getAttributeValue(1)
                                            + " ("
                                            + type
                                            + ")");
                        } else if (mocksim_validation
                                && MOCK_PIN_PROFILE_TAG.equals(parser.getName())) {
                            int appstate = convertMockSimAppState(parser.getAttributeValue(0));
                            mSimApp[appidx].appState = appstate;
                            Log.d(
                                    TAG,
                                    "Found "
                                            + MOCK_PIN_PROFILE_TAG
                                            + ": appstate = "
                                            + parser.getAttributeValue(0)
                                            + " ("
                                            + appstate
                                            + ")");
                        } else if (mocksim_validation
                                && MOCK_PIN1_STATE_TAG.equals(parser.getName())) {
                            String state = parser.nextText();
                            int pin1state = convertMockSimPinState(state);
                            mSimApp[appidx].pin1 = pin1state;
                            Log.d(
                                    TAG,
                                    "Found "
                                            + MOCK_PIN1_STATE_TAG
                                            + " = "
                                            + state
                                            + " ("
                                            + pin1state
                                            + ")");
                        } else if (mocksim_validation
                                && MOCK_PIN2_STATE_TAG.equals(parser.getName())) {
                            String state = parser.nextText();
                            int pin2state = convertMockSimPinState(state);
                            Log.d(
                                    TAG,
                                    "Found "
                                            + MOCK_PIN2_STATE_TAG
                                            + " = "
                                            + state
                                            + " ("
                                            + pin2state
                                            + ")");
                        } else if (mocksim_validation
                                && MOCK_FACILITY_LOCK_FD_TAG.equals(parser.getName())) {
                            fd_lock = convertMockSimFacilityLock(parser.nextText());
                            Log.d(
                                    TAG,
                                    "Found "
                                            + MOCK_FACILITY_LOCK_FD_TAG
                                            + ": fd lock = "
                                            + fd_lock);
                        } else if (mocksim_validation
                                && MOCK_FACILITY_LOCK_SC_TAG.equals(parser.getName())) {
                            sc_lock = convertMockSimFacilityLock(parser.nextText());
                            Log.d(
                                    TAG,
                                    "Found "
                                            + MOCK_FACILITY_LOCK_SC_TAG
                                            + ": sc lock = "
                                            + sc_lock);
                        } else if (mocksim_validation && MOCK_MF_TAG.equals(parser.getName())) {
                            SimAppData simAppData;
                            String name = parser.getAttributeValue(0);
                            String path = parser.getAttributeValue(1);
                            simAppData = new SimAppData(appidx, name, path);
                            if (simAppData == null) {
                                Log.e(TAG, "Create SIM app data failed!");
                                result = false;
                                break;
                            }
                            mSimAppList.add(simAppData);
                            Log.d(
                                    TAG,
                                    "Found "
                                            + MOCK_MF_TAG
                                            + ": name = "
                                            + name
                                            + " path = "
                                            + path);
                        } else if (mocksim_validation
                                && !mocksim_mf_validation
                                && MOCK_EF_DIR_TAG.equals(parser.getName())) {
                            SimAppData simAppData;
                            String name = parser.getAttributeValue(0);
                            boolean curr_active = Boolean.parseBoolean(parser.getAttributeValue(1));
                            String aid = parser.nextText();
                            simAppData = new SimAppData(appidx, aid, name);
                            if (simAppData == null) {
                                Log.e(TAG, "Create SIM app data failed!");
                                result = false;
                                break;
                            }
                            simAppData.setFdnStatus(fd_lock);
                            simAppData.setPin1State(sc_lock);
                            mSimAppList.add(simAppData);
                            if (curr_active) {
                                mSimApp[appidx].aidPtr = aid;
                            }
                            Log.d(
                                    TAG,
                                    "Found "
                                            + MOCK_EF_DIR_TAG
                                            + ": name = "
                                            + name
                                            + ": curr_active = "
                                            + curr_active
                                            + " aid = "
                                            + aid);
                            mocksim_mf_validation = true;
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (mocksim_validation && MOCK_SIM_PROFILE_TAG.equals(parser.getName())) {
                            appidx++;
                            mocksim_mf_validation = false;
                        }
                        break;
                }
            }
            Log.d(TAG, "Totally create " + appidx + " SIM profiles");
            mSimProfileInfoList[mSimProfileId].setNumOfSimApp(appidx);
            input.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception error: " + e);
            result = false;
        }

        return result;
    }

    private boolean loadSimApp() {
        boolean result = true;

        if (mSimAppList == null) {
            mSimAppList = new ArrayList<SimAppData>();
        } else {
            mSimAppList.clear();
        }

        if (mSimProfileId == MOCK_SIM_PROFILE_ID_DEFAULT
                || mSimProfileInfoList[mSimProfileId].getXmlFile().length() == 0) {
            Log.d(TAG, "SIM absent case");
            mSimApp = new AppStatus[0];
            if (mSimApp == null) {
                Log.e(TAG, "Create SIM app failed!");
                result = false;
            }
        } else {
            result = loadSimProfileFromXml();
        }

        return result;
    }

    private boolean loadMockSimCard() {
        if (mSimProfileId != MOCK_SIM_PROFILE_ID_DEFAULT) {
            switch (mPhysicalSlotId) {
                case MOCK_SIM_SLOT_1:
                    mATR = "3B9F96801FC78031E073FE2111634082918307900099";
                    mEID = DEFAULT_SIM1_EID;
                    break;
                case MOCK_SIM_SLOT_2:
                    mATR = DEFAULT_SIM2_ATR;
                    mEID = DEFAULT_SIM2_EID;
                    break;
                case MOCK_SIM_SLOT_3:
                    mATR = DEFAULT_SIM3_ATR;
                    mEID = DEFAULT_SIM3_EID;
                    break;
            }
            mUniversalPinState = PinState.DISABLED;
            mIsCardPresent = true;
        } else {
            switch (mPhysicalSlotId) {
                case MOCK_SIM_SLOT_1:
                    mATR = DEFAULT_SIM1_ATR;
                    mEID = DEFAULT_SIM1_EID;
                    mICCID = DEFAULT_SIM1_ICCID;
                    mUniversalPinState = DEFAULT_SIM1_UNIVERSAL_PIN_STATE;
                    break;
                case MOCK_SIM_SLOT_2:
                    mATR = DEFAULT_SIM2_ATR;
                    mEID = DEFAULT_SIM2_EID;
                    mICCID = DEFAULT_SIM2_ICCID;
                    mUniversalPinState = DEFAULT_SIM2_UNIVERSAL_PIN_STATE;
                    break;
                case MOCK_SIM_SLOT_3:
                    mATR = DEFAULT_SIM3_ATR;
                    mEID = DEFAULT_SIM3_EID;
                    mICCID = DEFAULT_SIM3_ICCID;
                    mUniversalPinState = DEFAULT_SIM3_UNIVERSAL_PIN_STATE;
                    break;
            }
            mIsCardPresent = false;
        }
        return loadSimApp();
    }

    public boolean loadSimCard(int simprofileid) {
        boolean result = true;
        mSimProfileId = simprofileid;
        if (result) {
            result = loadMockSimCard();
        }
        return result;
    }

    public boolean isSlotPortActive() {
        return mIsSlotPortActive;
    }

    public boolean isCardPresent() {
        return mIsCardPresent;
    }

    public int getNumOfSimPortInfo() {
        return DEFAULT_NUM_OF_SIM_PORT_INfO;
    }

    public int getPhysicalSlotId() {
        return mPhysicalSlotId;
    }

    public int getLogicalSlotId() {
        return mLogicalSlotId;
    }

    public int getSlotPortId() {
        return mSlotPortId;
    }

    public String getEID() {
        return mEID;
    }

    public String getATR() {
        return mATR;
    }

    public String getICCID() {
        return mICCID;
    }

    public int getUniversalPinState() {
        return mUniversalPinState;
    }

    public int getGsmAppIndex() {
        return mSimProfileInfoList[mSimProfileId].getGsmAppIndex();
    }

    public int getCdmaAppIndex() {
        return mSimProfileInfoList[mSimProfileId].getCdmaAppIndex();
    }

    public int getImsAppIndex() {
        return mSimProfileInfoList[mSimProfileId].getImsAppIndex();
    }

    public int getNumOfSimApp() {
        return mSimProfileInfoList[mSimProfileId].getNumOfSimApp();
    }

    public AppStatus[] getSimApp() {
        return mSimApp;
    }

    public ArrayList<SimAppData> getSimAppList() {
        return mSimAppList;
    }
}
