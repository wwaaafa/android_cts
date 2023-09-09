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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.analyzers.BaseSineAnalyzer;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;

// MegaAudio
import org.hyphonate.megaaudio.common.Globals;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * CtsVerifier test for audio data paths.
 */
public abstract class AudioDataPathsBaseActivity
        extends AudioMultiApiActivity
        implements View.OnClickListener, AppCallback {
    private static final String TAG = "AudioDataPathsActivity";

    // ReportLog Schema
    private static final String SECTION_AUDIO_DATAPATHS = "audio_datapaths";

    private boolean mHasMic;
    private boolean mHasSpeaker;

    // UI
    private View mStartBtn;
    private View mCancelButton;

    private TextView mRoutesTx;
    private WebView mResultsView;

    private WaveScopeView mWaveView = null;

    private  HtmlFormatter mHtmlFormatter = new HtmlFormatter();

    // Test Manager
    private TestManager mTestManager = new TestManager();
    private boolean mTestCanceled;

    // Audio I/O
    private AudioManager mAudioManager;

    // Analysis
    private BaseSineAnalyzer mAnalyzer = new BaseSineAnalyzer();

    private DuplexAudioManager mDuplexAudioManager;

    protected AppCallback mAnalysisCallbackHandler;

    class HtmlFormatter {
        StringBuilder mSB = new StringBuilder();

        HtmlFormatter clear() {
            mSB = new StringBuilder();
            return this;
        }

        HtmlFormatter openDocument() {
            mSB.append("<!DOCTYPE html><html lang=\"en-US\"><body>");
            return this;
        }

        HtmlFormatter closeDocument() {
            mSB.append("</body>");
            return this;
        }

        HtmlFormatter openParagraph() {
            mSB.append("<p>");
            return this;
        }

        HtmlFormatter closeParagraph() {
            mSB.append("</p>");
            return this;
        }

        HtmlFormatter insertBreak() {
            mSB.append("<br>");
            return this;
        }

        HtmlFormatter openTextColor(String color) {
            mSB.append("<font color=\"" + color + "\">");
            return this;
        }

        HtmlFormatter closeTextColor() {
            mSB.append("</font>");
            return this;
        }

        HtmlFormatter appendText(String text) {
            mSB.append(text);
            return this;
        }

        @Override
        public String toString() {
            return mSB.toString();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_datapaths_activity);

        super.onCreate(savedInstanceState);

        // MegaAudio Initialization
        StreamBase.setup(this);

        mHasMic = AudioSystemFlags.claimsInput(this);
        mHasSpeaker = AudioSystemFlags.claimsOutput(this);

        String yesString = getResources().getString(R.string.audio_general_yes);
        String noString = getResources().getString(R.string.audio_general_no);
        ((TextView) findViewById(R.id.audio_datapaths_mic))
                .setText(mHasMic ? yesString : noString);
        ((TextView) findViewById(R.id.audio_datapaths_speaker))
                .setText(mHasSpeaker ? yesString : noString);

        mStartBtn = findViewById(R.id.audio_datapaths_start);
        mStartBtn.setOnClickListener(this);
        mCancelButton = findViewById(R.id.audio_datapaths_cancel);
        mCancelButton.setOnClickListener(this);
        mCancelButton.setEnabled(false);

        mRoutesTx = (TextView) findViewById(R.id.audio_datapaths_routes);

        mResultsView = (WebView) findViewById(R.id.audio_datapaths_results);

        mWaveView = (WaveScopeView) findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);

        setPassFailButtonClickListeners();

        mAudioManager = getSystemService(AudioManager.class);

        mAnalysisCallbackHandler = this;

        mTestManager.initializeTests();

        mAudioManager.registerAudioDeviceCallback(new AudioDeviceConnectionCallback(), null);

        getPassButton().setEnabled(false);

        DisplayUtils.setKeepScreenOn(this, true);
    }

    @Override
    public void onStop() {
        stopTest();
        super.onStop();
    }

    //
    // UI Helpers
    //
    private void showDeviceView() {
        mRoutesTx.setVisibility(View.VISIBLE);
        mWaveView.setVisibility(View.VISIBLE);

        mResultsView.setVisibility(View.GONE);
    }

    private void showResultsView() {
        mRoutesTx.setVisibility(View.GONE);
        mWaveView.setVisibility(View.GONE);

        mResultsView.setVisibility(View.VISIBLE);
    }

    void enableTestButtons(boolean startEnabled, boolean stopEnabled) {
        mStartBtn.setEnabled(startEnabled);
        mCancelButton.setEnabled(stopEnabled);
    }

    class TestSpec {
        //
        // Stream Attributes
        //
        static final int ATTRIBUTE_DISABLE_MMAP = 0x00000001;

        //
        // Datapath specifications
        //
        // Playback Specification
        final int mOutDeviceType; // TYPE_BUILTIN_SPEAKER for example
        final int mOutSampleRate;
        final int mOutChannelCount;
        //TODO - Add usage and content types to output stream

        // Device for capturing the (played) signal
        final int mInDeviceType;  // TYPE_BUILTIN_MIC for example
        final int mInSampleRate;
        final int mInChannelCount;
        int mInputPreset;

        int mGlobalAttributes;

        AudioDeviceInfo mOutDeviceInfo;
        AudioDeviceInfo mInDeviceInfo;

        public AudioSourceProvider mSourceProvider;
        public AudioSinkProvider mSinkProvider;

        String mSectionTitle = null;
        String mDescription = "";

        int[] mTestState;

        TestResults[] mTestResults;

        // Pass/Fail criteria (with defaults)
        double mMinPassMagnitude = 0.01;
        double mMaxPassJitter = 0.1;

        TestSpec(int outDeviceType, int outSampleRate, int outChannelCount,
                 int inDeviceType, int inSampleRate, int inChannelCount) {
            mOutDeviceType = outDeviceType;
            mOutSampleRate = outSampleRate;
            mOutChannelCount = outChannelCount;

            // Default
            mInDeviceType = inDeviceType;
            mInChannelCount = inChannelCount;
            mInSampleRate = inSampleRate;

            mTestState = new int[NUM_TEST_APIS];
            for (int api = 0; api < NUM_TEST_APIS; api++) {
                mTestState[api] = TestSpec.TESTSTATUS_NOT_RUN;
            }
            mTestResults = new TestResults[NUM_TEST_APIS];
        }

        // Test states that indicate a skipped test are negative
        // Test states that indicate an executed test that failed are positive.
        public static final int TESTSTATUS_BAD_ROUTING = -2;
        public static final int TESTSTATUS_NOT_RUN = -1;
        public static final int TESTSTATUS_OK = 0;
        public static final int TESTSTATUS_BAD_STATE = 1;
        public static final int TESTSTATUS_BAD_START = 2;

        void clearTestState(int api) {
            mTestState[api] = TESTSTATUS_NOT_RUN;
            mTestResults[api] = null;
        }

        int getTestState(int api) {
            return mTestState[api];
        }

        void setTestState(int api, int state) {
            mTestState[api] = state;
        }

        String getOutDeviceName() {
            return AudioDeviceUtils.getDeviceTypeName(mOutDeviceType);
        }

        String getInDeviceName() {
            return AudioDeviceUtils.getDeviceTypeName(mInDeviceType);
        }

        void setSectionTitle(String title) {
            mSectionTitle = title;
        }

        String getSectionTitle() {
            return mSectionTitle;
        }

        void setDescription(String description) {
            mDescription = description;
        }

        String getDescription() {
            return mDescription;
        }

        void setSources(AudioSourceProvider sourceProvider, AudioSinkProvider sinkProvider) {
            mSourceProvider = sourceProvider;
            mSinkProvider = sinkProvider;
        }

        void setInputPreset(int preset) {
            mInputPreset = preset;
        }

        void setGlobalAttributes(int attributes) {
            mGlobalAttributes = attributes;
        }

        int getGlobalAttributes() {
            return mGlobalAttributes;
        }

        boolean canRun() {
            return mInDeviceInfo != null && mOutDeviceInfo != null;
        }

        void setTestResults(int api, TestResults results) {
            mTestResults[api] = results;
        }

        void setPassCriteria(double minPassMagnitude, double maxPassJitter) {
            mMinPassMagnitude = minPassMagnitude;
            mMaxPassJitter = maxPassJitter;
        }

        //
        // Predicates
        //
        // Ran to completion and results supplied
        boolean hasRun(int api) {
            // return mTestState[api] == TESTSTATUS_OK;
            return mTestResults[api] != null;
        }

        // Didn't run because of invalid hardware or bad routing.
        boolean wasSkipped(int api) {
            // either doesn't have the hardware or the routing failed.
            return mTestState[api] < 0;
        }

        // Ran and passed the criteria
        boolean hasPassed(int api) {
            if (hasRun(api)) {
                return mTestResults[api].mMaxMagnitude >= mMinPassMagnitude
                        && mTestResults[api].mPhaseJitter <= mMaxPassJitter;
            } else {
                return false;
            }
        }

        // Ran, but failed the pass criteria
        boolean hasFailed(int api) {
            if (hasRun(api)) {
                return mTestResults[api].mMaxMagnitude < mMinPassMagnitude
                        || mTestResults[api].mPhaseJitter > mMaxPassJitter;
            } else {
                return false;
            }
        }

        // Should've been able to run, but ran into errors opening/starting streams
        boolean hadError(int api) {
            // TESTSTATUS_NOT_RUN && TESTSTATUS_BAD_ROUTING are not considered failures
            return mTestState[api] > 0;
        }

        //
        // CTS VerifierReportLog stuff
        //
        // ReportLog Schema
        private static final String KEY_TESTDESCRIPTION = "test_description";
        // Output Specification
        private static final String KEY_OUT_DEVICE_TYPE = "out_device_type";
        private static final String KEY_OUT_DEVICE_NAME = "out_device_name";
        private static final String KEY_OUT_DEVICE_RATE = "out_device_rate";
        private static final String KEY_OUT_DEVICE_CHANS = "out_device_chans";

        // Input Specification
        private static final String KEY_IN_DEVICE_TYPE = "in_device_type";
        private static final String KEY_IN_DEVICE_NAME = "in_device_name";
        private static final String KEY_IN_DEVICE_RATE = "in_device_rate";
        private static final String KEY_IN_DEVICE_CHANS = "in_device_chans";
        private static final String KEY_IN_PRESET = "in_preset";

        void generateReportLog(int api) {
            if (!canRun() || mTestResults[api] == null) {
                return;
            }

            CtsVerifierReportLog reportLog = newReportLog();

            // Description
            reportLog.addValue(
                    KEY_TESTDESCRIPTION,
                    getDescription(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            // Output Specification
            reportLog.addValue(
                    KEY_OUT_DEVICE_NAME,
                    getOutDeviceName(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUT_DEVICE_TYPE,
                    mOutDeviceType,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUT_DEVICE_RATE,
                    mOutSampleRate,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUT_DEVICE_CHANS,
                    mOutChannelCount,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            // Input Specifications
            reportLog.addValue(
                    KEY_IN_DEVICE_NAME,
                    getInDeviceName(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_DEVICE_TYPE,
                    mInDeviceType,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_DEVICE_RATE,
                    mInSampleRate,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_DEVICE_CHANS,
                    mInChannelCount,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_PRESET,
                    mInputPreset,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            // Results
            mTestResults[api].generateReportLog(reportLog);

            reportLog.submit();
        }
    }

    /*
     * TestResults
     */
    class TestResults {
        int mApi;
        double mMagnitude;
        double mMaxMagnitude;
        double mPhase;
        double mPhaseJitter;

        TestResults(int api, double magnitude, double maxMagnitude, double phase,
                    double phaseJitter) {
            mApi = api;
            mMagnitude = magnitude;
            mMaxMagnitude = maxMagnitude;
            mPhase = phase;
            mPhaseJitter = phaseJitter;
        }

        // ReportLog Schema
        private static final String KEY_TESTAPI = "test_api";
        private static final String KEY_MAXMAGNITUDE = "max_magnitude";
        private static final String KEY_PHASEJITTER = "phase_jitter";

        void generateReportLog(CtsVerifierReportLog reportLog) {
            reportLog.addValue(
                    KEY_TESTAPI,
                    mApi,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_MAXMAGNITUDE,
                    mMaxMagnitude,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_PHASEJITTER,
                    mPhaseJitter,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);
        }
    }

    abstract void gatherTestModules(TestManager testManager);

    /*
     * TestManager
     */
    class TestManager {
        static final String TAG = "TestManager";

        // Audio Device Type ID -> TestProfile
        ArrayList<TestSpec> mTestSpecs = new ArrayList<TestSpec>();

        public int mApi;

        private int    mPhaseCount;

        // which route are we running
        static final int TESTSTEP_NONE = -1;
        private int mTestStep = TESTSTEP_NONE;

        private Timer mTimer;

        public void initializeTests() {
            // Get the test modules from the sub-class
            gatherTestModules(this);

            validateTestDevices();
            displayTestDevices();
        }

        public void clearTestState() {
            for (TestSpec spec: mTestSpecs) {
                spec.clearTestState(mApi);
            }
        }

        public void validateTestDevices() {
            // do we have the output device we need
            AudioDeviceInfo[] outputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (TestSpec testSpec : mTestSpecs) {
                // Check to see if we have a (physical) device of this type
                for (AudioDeviceInfo devInfo : outputDevices) {
                    testSpec.mOutDeviceInfo = null;
                    if (testSpec.mOutDeviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                            && !mHasSpeaker) {
                        break;
                    } else if (testSpec.mOutDeviceType == devInfo.getType()) {
                        testSpec.mOutDeviceInfo = devInfo;
                        break;
                    }
                }
            }

            // do we have the input device we need
            AudioDeviceInfo[] inputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (TestSpec testSpec : mTestSpecs) {
                // Check to see if we have a (physical) device of this type
                for (AudioDeviceInfo devInfo : inputDevices) {
                    testSpec.mInDeviceInfo = null;
                    if (testSpec.mInDeviceType == AudioDeviceInfo.TYPE_BUILTIN_MIC
                            && !mHasMic) {
                        break;
                    } else if (testSpec.mInDeviceType == devInfo.getType()) {
                        testSpec.mInDeviceInfo = devInfo;
                        break;
                    }
                }
            }
        }

        public int countValidTestSpecs() {
            int numValid = 0;
            for (TestSpec testSpec : mTestSpecs) {
                if (testSpec.mOutDeviceInfo != null && testSpec.mInDeviceInfo != null) {
                    numValid++;
                }
            }
            return numValid;
        }

        public void displayTestDevices() {
            StringBuilder sb = new StringBuilder();
            sb.append("Tests:");
            int testStep = 0;
            for (TestSpec testSpec : mTestSpecs) {
                sb.append("\n");
                if (testSpec.getSectionTitle() != null) {
                    sb.append("---" + testSpec.getSectionTitle() + "---\n");
                }
                if (testStep == mTestStep) {
                    sb.append(">>>");
                }
                sb.append(testSpec.getDescription());

                if (testSpec.canRun() && testStep != mTestStep) {
                    sb.append(" *");
                }

                if (testStep == mTestStep) {
                    sb.append("<<<");
                }

                if (testSpec.canRun()) {
                    int status = testSpec.getTestState(mApi);
                    switch (status) {
                        case TestSpec.TESTSTATUS_NOT_RUN:
                            // This doesn't need to be annotated
                            break;
                        case TestSpec.TESTSTATUS_OK:
                            sb.append(testSpec.hasPassed(mApi) ? " - PASS" : " - FAIL");
                            break;
                        case TestSpec.TESTSTATUS_BAD_STATE:
                            sb.append(" - BAD STATE");
                            break;
                        case TestSpec.TESTSTATUS_BAD_START:
                            sb.append(" - BAD START");
                            break;
                        case TestSpec.TESTSTATUS_BAD_ROUTING:
                            sb.append(" - BAD ROUTE");
                            break;
                    }
                }
                testStep++;
            }
            mRoutesTx.setText(sb.toString());

            int numValidSpecs = countValidTestSpecs();
            if (numValidSpecs == 0) {
                completeTest();
            }
        }

        public TestSpec getActiveTestSpec() {
            return mTestStep != TESTSTEP_NONE && mTestStep < mTestSpecs.size()
                    ? mTestSpecs.get(mTestStep)
                    : null;
        }

        private int countFailures(int api) {
            int numFailed = 0;
            for (TestSpec spec : mTestSpecs) {
                if (spec.hadError(api) || spec.hasFailed(api)) {
                    numFailed++;
                }
            }
            return numFailed;
        }

        public int startTest(TestSpec testSpec) {
            if (mTestCanceled) {
                return TestSpec.TESTSTATUS_NOT_RUN;
            }

            AudioDeviceInfo outDevInfo = testSpec.mOutDeviceInfo;
            AudioDeviceInfo inDevInfo = testSpec.mInDeviceInfo;
            if (outDevInfo != null && inDevInfo != null) {
                mAnalyzer.reset();
                mAnalyzer.setSampleRate(testSpec.mInSampleRate);

                // Player
                mDuplexAudioManager.setSources(
                        testSpec.mSourceProvider, testSpec.mSinkProvider);
                mDuplexAudioManager.setPlayerRouteDevice(outDevInfo);
                mDuplexAudioManager.setPlayerSampleRate(testSpec.mOutSampleRate);
                mDuplexAudioManager.setNumPlayerChannels(testSpec.mOutChannelCount);

                // Recorder
                mDuplexAudioManager.setRecorderRouteDevice(inDevInfo);
                mDuplexAudioManager.setInputPreset(testSpec.mInputPreset);
                mDuplexAudioManager.setRecorderSampleRate(testSpec.mInSampleRate);
                mDuplexAudioManager.setNumRecorderChannels(testSpec.mInChannelCount);

                // Set MMAP policy before opening the streams.
                boolean mmapEnabled =
                        ((testSpec.getGlobalAttributes() & TestSpec.ATTRIBUTE_DISABLE_MMAP) != 0)
                        ? false
                        : Globals.isMMapSupported();
                Log.d(TAG, "DataPaths: setMMapEnabled(" + mmapEnabled + ")");
                Globals.setMMapEnabled(mmapEnabled);

                // Open the streams.
                // Note AudioSources and AudioSinks get allocated at this point
                mDuplexAudioManager.setupStreams(mAudioApi, mAudioApi);

                // (potentially) Adjust AudioSource parameters
                AudioSource audioSource = testSpec.mSourceProvider.getActiveSource();

                // Set the sample rate for the source (the sample rate for the player gets
                // set in the DuplexAudioManager.Builder.
                audioSource.setSampleRate(testSpec.mOutSampleRate);

                // Adjust the player frequency to match with the quantized frequency
                // of the analyzer.
                audioSource.setFreq((float) mAnalyzer.getAdjustedFrequency());

                mWaveView.setNumChannels(testSpec.mInChannelCount);

                if (mDuplexAudioManager.start() != StreamBase.OK) {
                    Log.e(TAG, "Couldn't start duplex streams");
                    testSpec.setTestState(mApi, TestSpec.TESTSTATUS_BAD_START);
                    return TestSpec.TESTSTATUS_BAD_START;
                }

                // Validate routing
                if (!mDuplexAudioManager.validateRouting()) {
                    testSpec.setTestState(mApi, TestSpec.TESTSTATUS_BAD_ROUTING);
                    return TestSpec.TESTSTATUS_BAD_ROUTING;
                }
                testSpec.setTestState(mApi, TestSpec.TESTSTATUS_OK);
                return TestSpec.TESTSTATUS_OK;
            } else {
                testSpec.setTestState(mApi, TestSpec.TESTSTATUS_NOT_RUN);
                return TestSpec.TESTSTATUS_NOT_RUN;
            }
        }

        private static final int MS_PER_SEC = 1000;
        private static final int TEST_TIME_IN_SECONDS = 2;
        public void startTest(int api) {
            showDeviceView();

            mApi = api;

            clearTestState();

            mTestStep = TESTSTEP_NONE;
            mTestCanceled = false;

            (mTimer = new Timer()).scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    completeTestStep();
                    advanceTestStep();
                }
            }, 0, TEST_TIME_IN_SECONDS * MS_PER_SEC);
        }

        public void stopTest() {
            if (mTestStep != TESTSTEP_NONE) {
                mTestStep = TESTSTEP_NONE;

                if (mTimer != null) {
                    mTimer.cancel();
                    mTimer = null;
                }
                mDuplexAudioManager.stop();
            }
        }

        public void completeTest() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    enableTestButtons(true, false);

                    mRoutesTx.setVisibility(View.GONE);
                    mWaveView.setVisibility(View.GONE);

                    mHtmlFormatter.clear();
                    mHtmlFormatter.openDocument();
                    if (countValidTestSpecs() == 0) {
                        mRoutesTx.setVisibility(View.VISIBLE);
                        getPassButton().setEnabled(true);
                        mHtmlFormatter.openParagraph()
                                 .appendText("No valid test specs.")
                                 .closeParagraph();
                    } else {
                        mTestManager.generateReport(mHtmlFormatter);
                        int numFailures = countFailures(mApi);
                        getPassButton().setEnabled(!mTestCanceled && numFailures == 0);

                        mHtmlFormatter.openParagraph();
                        if (!mTestCanceled) {
                            mHtmlFormatter.appendText(numFailures == 0
                                    ? "All tests passed."
                                    : ("There were " + numFailures + " failures."));
                            mHtmlFormatter.closeParagraph();
                            mHtmlFormatter.openParagraph();
                        }
                        mHtmlFormatter.closeParagraph();
                        mHtmlFormatter.closeDocument();
                    }

                    mResultsView.loadData(mHtmlFormatter.toString(),
                            "text/html; charset=utf-8", "utf-8");
                    showResultsView();
                }
            });
        }

        public void completeTestStep() {
            if (mTestStep != TESTSTEP_NONE) {
                mDuplexAudioManager.stop();
                // Give the audio system a chance to settle from the previous state
                // It is often the case that the Java API will not route to the specified
                // device if we teardown/startup too quickly. This sleep cirmumvents that.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Log.e(TAG, "sleep failed?");
                }

                TestSpec testSpec = getActiveTestSpec();
                if (testSpec != null && testSpec.canRun()) {
                    testSpec.setTestResults(mApi, new TestResults(mApi,
                            mAnalyzer.getMagnitude(),
                            mAnalyzer.getMaxMagnitude(),
                            mAnalyzer.getPhaseOffset(),
                            mAnalyzer.getPhaseJitter()));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            displayTestDevices();
                            mWaveView.resetPersistentMaxMagnitude();
                        }
                    });
                }
            }
        }

        public void advanceTestStep() {
            if (mTestCanceled) {
                // test shutting down. Bail.
                return;
            }
            while (++mTestStep < mTestSpecs.size()) {
                // update the display to show progress
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayTestDevices();
                    }
                });

                // Scan until we find a TestSpec that starts playing/recording
                TestSpec testSpec = mTestSpecs.get(mTestStep);
                int status = startTest(testSpec);
                testSpec.setTestState(mApi, status);
                if (status == TestSpec.TESTSTATUS_OK || status == TestSpec.TESTSTATUS_BAD_ROUTING) {
                    // these are statuses where playing/recording successfully starts, even if
                    // to the wrong route, so allow this test to run to completion.
                    break;
                }
                // Otherwise, playing/recording failed, look for the next TestSpec
            }

            if (mTestStep >= mTestSpecs.size()) {
                stopTest();
                completeTest();
            }
        }

        HtmlFormatter generateReport(HtmlFormatter htmlFormatter) {
            for (TestSpec spec : mTestSpecs) {
                if (spec.canRun()) {
                    // Description
                    htmlFormatter.openParagraph()
                            .appendText(spec.mDescription);
                    if (spec.hasPassed(mApi)) {
                        htmlFormatter.appendText(" - PASS");
                    } else {
                        int testStatus = spec.getTestState(mApi);
                        switch (testStatus) {
                            case TestSpec.TESTSTATUS_NOT_RUN:
                                htmlFormatter.appendText(" - NOT RUN");
                                break;
                            case TestSpec.TESTSTATUS_BAD_ROUTING:
                                htmlFormatter.appendText(" - BAD ROUTING");
                                break;
                            case TestSpec.TESTSTATUS_BAD_START:
                                htmlFormatter.openTextColor("red");
                                htmlFormatter.appendText(" - BAD START");
                                htmlFormatter.closeTextColor();
                                break;
                            case TestSpec.TESTSTATUS_BAD_STATE:
                                htmlFormatter.openTextColor("red");
                                htmlFormatter.appendText(" - BAD STATE");
                                htmlFormatter.closeTextColor();
                                break;
                            default: {
                                TestResults results = spec.mTestResults[mApi];
                                if (results != null) {
                                    // we can get null here if the test was cancelled
                                    Locale locale = Locale.getDefault();
                                    String maxMagString = String.format(
                                            locale, "mag:%.4f ", results.mMaxMagnitude);
                                    String phaseJitterString = String.format(
                                            locale, "jit:%.4f ", results.mPhaseJitter);

                                    boolean passMagnitude =
                                            results.mMaxMagnitude >= spec.mMinPassMagnitude;
                                    boolean passJitter =
                                            results.mPhaseJitter <= spec.mMaxPassJitter;

                                    // The play/record was OK, but the analysis failed
                                    htmlFormatter.appendText(" - FAIL");

                                    // Values
                                    htmlFormatter.insertBreak();
                                    if (!passMagnitude) {
                                        htmlFormatter.openTextColor("red")
                                                .appendText(maxMagString)
                                                .closeTextColor();
                                    } else {
                                        htmlFormatter.appendText(maxMagString);
                                    }

                                    if (!passJitter) {
                                        htmlFormatter.openTextColor("red")
                                                .appendText(phaseJitterString)
                                                .closeTextColor();
                                    } else {
                                        htmlFormatter.appendText(phaseJitterString);
                                    }

                                    // Criteria
                                    htmlFormatter.insertBreak();
                                    if (!passMagnitude) {
                                        htmlFormatter.openTextColor("blue")
                                                .appendText(maxMagString
                                                        + String.format(locale, " <= %.4f ",
                                                        spec.mMinPassMagnitude))
                                                .closeTextColor();
                                    }

                                    if (!passJitter) {
                                        htmlFormatter.openTextColor("blue")
                                                .appendText(phaseJitterString
                                                        + String.format(locale, " >= %.4f",
                                                        spec.mMaxPassJitter))
                                                .closeTextColor();
                                    }
                                }
                            }
                            break;
                        }
                        htmlFormatter.insertBreak();
                    } // pass/fail
                    htmlFormatter.closeParagraph();
                } // hasRun()
            }

            return htmlFormatter;
        }

        //
        // CTS VerifierReportLog stuff
        //
        void generateReportLog() {
            int testIndex = 0;
            for (TestSpec spec : mTestSpecs) {
                for (int api = TEST_API_NATIVE; api < NUM_TEST_APIS; api++) {
                    spec.generateReportLog(api);
                }
            }
        }
    }

    //
    // Process Handling
    //
    private void startTest(int api) {
        if (mDuplexAudioManager == null) {
            mDuplexAudioManager = new DuplexAudioManager(null, null);
        }

        enableTestButtons(false, true);
        getPassButton().setEnabled(false);

        mTestManager.startTest(api);
    }

    private void stopTest() {
        mTestManager.stopTest();
        mTestManager.displayTestDevices();

        enableTestButtons(true, false);
        Globals.setMMapEnabled(Globals.isMMapSupported());
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public boolean requiresReportLog() {
        return true;
    }

    //
    // CTS VerifierReportLog stuff
    //
    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_AUDIO_DATAPATHS);
    }

    @Override
    public void recordTestResults() {
// TODO Remove all report logging from this file. This is a quick fix.
// This code generates multiple records in the JSON file.
// That duplication is invalid JSON and causes the database
// ingestion to fail.
//        mTestManager.generateReportLog();
    }

    //
    // AudioMultiApiActivity Overrides
    //
    @Override
    public void onApiChange(int api) {
        stopTest();
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.audio_datapaths_start) {
            startTest(mActiveTestAPI);
        } else if (id == R.id.audio_datapaths_cancel) {
            mTestCanceled = true;
            stopTest();
            mTestManager.completeTest();
        } else if (id == R.id.audioJavaApiBtn || id == R.id.audioNativeApiBtn) {
            super.onClick(view);
            mTestCanceled = true;
            stopTest();
            mTestManager.clearTestState();
            showDeviceView();
            mTestManager.displayTestDevices();
        }
    }

    //
    // (MegaAudio) AppCallback overrides
    //
    @Override
    public void onDataReady(float[] audioData, int numFrames) {
        TestSpec testSpec = mTestManager.getActiveTestSpec();
        if (testSpec != null) {
            mAnalyzer.analyzeBuffer(audioData, testSpec.mInChannelCount, numFrames);
            mWaveView.setPCMFloatBuff(audioData, testSpec.mInChannelCount, numFrames);
        }
    }

    //
    // AudioDeviceCallback overrides
    //
    private class AudioDeviceConnectionCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            showDeviceView();
            mTestManager.validateTestDevices();
            mTestManager.displayTestDevices();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            showDeviceView();
            mTestManager.validateTestDevices();
            mTestManager.displayTestDevices();
        }
    }
}
