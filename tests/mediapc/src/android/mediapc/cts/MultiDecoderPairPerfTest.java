/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.mediapc.cts;

import static org.junit.Assert.assertTrue;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediapc.cts.common.Utils;
import android.os.Build;
import android.util.Pair;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The following test class calculates the maximum number of concurrent decode sessions that it can
 * support by the two hardware (mime - decoder) pair calculated via the
 * CodecCapabilities.getMaxSupportedInstances() and
 * VideoCapabilities.getSupportedPerformancePoints() methods. Splits the maximum supported instances
 * between the two pairs and ensures that all the supported sessions succeed in decoding
 * with meeting the expected frame rate.
 */
@RunWith(Parameterized.class)
public class MultiDecoderPairPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiDecoderPairPerfTest.class.getSimpleName();
    private static final int REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE = 3;

    private final Pair<String, String> mFirstPair;
    private final Pair<String, String> mSecondPair;

    public MultiDecoderPairPerfTest(Pair<String, String> firstPair, Pair<String, String> secondPair,
            boolean isAsync) {
        super(null, null, isAsync);
        mFirstPair = firstPair;
        mSecondPair = secondPair;
    }

    // Returns the list of params with two hardware (mime - decoder) pairs in both
    // sync and async modes.
    // Parameters {0}_{1}_{2} -- Pair(Mime DecoderName)_Pair(Mime DecoderName)_isAsync
    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        final List<Object[]> argsList = new ArrayList<>();
        ArrayList<Pair<String, String>> mimeTypeDecoderPairs = new ArrayList<>();
        for (String mime : mMimeList) {
            ArrayList<String> listOfDecoders = getHardwareCodecsForMime(mime, false, true);
            for (String decoder : listOfDecoders) {
                mimeTypeDecoderPairs.add(Pair.create(mime, decoder));
            }
        }
        for (int i = 0; i < mimeTypeDecoderPairs.size(); i++) {
            for (int j = i + 1; j < mimeTypeDecoderPairs.size(); j++) {
                Pair<String, String> pair1 = mimeTypeDecoderPairs.get(i);
                Pair<String, String> pair2 = mimeTypeDecoderPairs.get(j);
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{pair1, pair2, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test calculates the number of 720p 30 fps decoder instances that the given two
     * (mime - decoder) pairs can support. Assigns the same number of instances to the two pairs
     * (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-1,H-1-2")
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());
        Assume.assumeFalse("Skipping regular performance tests for secure codecs",
                isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) ||
                        isSecureSupportedCodec(mSecondPair.second, mSecondPair.first));

        boolean hasVP9 = mFirstPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9) ||
                mSecondPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances(hasVP9);
        testCodec(m720pTestFiles, 720, 1280, requiredMinInstances);
    }

    /**
     * This test calculates the number of 1080p 30 fps decoder instances that the given two
     * (mime - decoder) pairs can support. Assigns the same number of instances to the two pairs
     * (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-1,H-1-2")
    public void test1080p() throws Exception {
        Assume.assumeTrue(Utils.isTPerfClass() || !Utils.isPerfClass());
        Assume.assumeFalse("Skipping regular performance tests for secure codecs",
                isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) ||
                        isSecureSupportedCodec(mSecondPair.second, mSecondPair.first));
        testCodec(m1080pTestFiles, 1080, 1920, REQUIRED_MIN_CONCURRENT_INSTANCES);
    }

    /**
     * Validates if hardware decoder pairs where one supports secure decode and required
     * perf are present and tests with concurrent unsecure decoders
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    // TODO(b/218771970) Add @CddTest annotation
    public void testReqSecureWithUnsecureDecodeSupport() throws Exception {
        Assume.assumeTrue(Utils.isTPerfClass() || !Utils.isPerfClass());
        Assume.assumeTrue("Testing if only one of the pair is secure",
                isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) ^
                        isSecureSupportedCodec(mSecondPair.second, mSecondPair.first));

        MediaCodecInfo.VideoCapabilities.PerformancePoint reqSecurePP =
                new MediaCodecInfo.VideoCapabilities.PerformancePoint(1920, 1080, 30);

        MediaCodecInfo.VideoCapabilities.PerformancePoint reqNonSecurePP =
                new MediaCodecInfo.VideoCapabilities.PerformancePoint(1920, 1080,
                        30 * REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE);

        boolean codecSupportsReqPP = codecSupportsPP(mFirstPair.second, mFirstPair.first,
                isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) ? reqSecurePP :
                        reqNonSecurePP);

        codecSupportsReqPP &= codecSupportsPP(mSecondPair.second, mSecondPair.first,
                isSecureSupportedCodec(mSecondPair.second, mSecondPair.first) ? reqSecurePP :
                        reqNonSecurePP);

        testCodec(m1080pTestFiles, 1080, 1920,
                REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE + 1, true);

        if (Utils.isTPerfClass()) {
            assertTrue(
                    "Required Secure Decode Support required for MPC >= Android T, unsupported " +
                            "codec pair: " + mFirstPair.second + "," + mSecondPair.second,
                    codecSupportsReqPP);
        } else {
            DeviceReportLog log =
                    new DeviceReportLog("MediaPerformanceClassLogs", "SecureDecodeSupport");
            log.addValue("Req Secure Decode Support pair: " + mFirstPair.second + "," +
                    mSecondPair.second, codecSupportsReqPP, ResultType.NEUTRAL, ResultUnit.NONE);
            // TODO(b/218771970) Log CDD sections
            log.setSummary("MPC 13: Secure Decode requirements", 0, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }

    /**
     * Validates if hardware decoder pairs where both supports secure decode and required
     * perf is present
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    // TODO(b/218771970) Add @CddTest annotation
    public void testReqMultiSecureDecodeSupport() throws Exception {
        Assume.assumeTrue(Utils.isTPerfClass() || !Utils.isPerfClass());
        Assume.assumeTrue("Run test if both are secure codecs",
                isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) &&
                        isSecureSupportedCodec(mSecondPair.second, mSecondPair.first));

        MediaCodecInfo.VideoCapabilities.PerformancePoint reqSecurePP =
                new MediaCodecInfo.VideoCapabilities.PerformancePoint(1920, 1080, 30);

        boolean codecSupportsReqPP =
                codecSupportsPP(mFirstPair.second, mFirstPair.first, reqSecurePP);
        codecSupportsReqPP &= codecSupportsPP(mSecondPair.second, mSecondPair.first, reqSecurePP);

        testCodec(null, 1080, 1920, REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);

        if (Utils.isTPerfClass()) {
            assertTrue(
                    "Required Secure Decode Support required for MPC >= Android T, unsupported " +
                            "codec pair: " + mFirstPair.second + "," + mSecondPair.second,
                    codecSupportsReqPP);
        } else {
            DeviceReportLog log =
                    new DeviceReportLog("MediaPerformanceClassLogs", "SecureDecodeSupport");
            log.addValue("Req Secure Decode Support pair: " + mFirstPair.second + "," +
                    mSecondPair.second, codecSupportsReqPP, ResultType.NEUTRAL, ResultUnit.NONE);
            // TODO(b/218771970) Log CDD sections
            log.setSummary("MPC 13: Secure Decode requirements", 0, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }

    private void testCodec(Map<String, String> testFiles, int height, int width,
            int requiredMinInstances) throws Exception {
        testCodec(testFiles, height, width, requiredMinInstances, false);
    }

    private void testCodec(Map<String, String> testFiles, int height, int width,
            int requiredMinInstances, boolean secureWithUnsecure) throws Exception {
        mTestFiles = testFiles;
        ArrayList<Pair<String, String>> mimeDecoderPairs = new ArrayList<>();
        mimeDecoderPairs.add(mFirstPair);
        mimeDecoderPairs.add(mSecondPair);
        int maxInstances = checkAndGetMaxSupportedInstancesForCodecCombinations(height, width,
                mimeDecoderPairs);
        double achievedFrameRate = 0.0;
        // secure test should not reach this point if secure codec doesn't support PP
        if (maxInstances >= requiredMinInstances || secureWithUnsecure) {
            int secondPairInstances = maxInstances / 2;
            int firstPairInstances = maxInstances - secondPairInstances;
            if (secureWithUnsecure) {
                firstPairInstances =
                        isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) ? 1 : 3;
                secondPairInstances = requiredMinInstances - firstPairInstances;
                maxInstances = requiredMinInstances;
            }
            List<Decode> testList = new ArrayList<>();
            for (int i = 0; i < firstPairInstances; i++) {
                boolean isSecure = isSecureSupportedCodec(mFirstPair.second, mFirstPair.first);
                String testFile = isSecure ? m1080pWidevineTestFiles.get(mFirstPair.first) :
                        mTestFiles.get(mFirstPair.first);
                Assume.assumeTrue("Add " + (isSecure ? "secure" : "") + " test vector for mime: " +
                        mFirstPair.first, testFile != null);
                testList.add(new Decode(mFirstPair.first, testFile, mFirstPair.second, mIsAsync,
                        isSecure));
            }
            for (int i = 0; i < secondPairInstances; i++) {
                boolean isSecure = isSecureSupportedCodec(mSecondPair.second, mSecondPair.first);
                String testFile = isSecure ? m1080pWidevineTestFiles.get(mSecondPair.first) :
                        mTestFiles.get(mSecondPair.first);
                Assume.assumeTrue("Add " + (isSecure ? "secure" : "") + " test vector for mime: " +
                        mFirstPair.first, testFile != null);
                testList.add(new Decode(mSecondPair.first, testFile, mSecondPair.second, mIsAsync,
                        isSecure));
            }
            ExecutorService pool = Executors.newFixedThreadPool(maxInstances);
            List<Future<Double>> resultList = pool.invokeAll(testList);
            for (Future<Double> result : resultList) {
                achievedFrameRate += result.get();
            }
        }
        if (Utils.isPerfClass()) {
            assertTrue("Decoder pair " + mFirstPair.second + " and " + mSecondPair.second
                            + " unable to support minimum concurrent " +
                            "instances. act/exp: " + maxInstances + "/" + requiredMinInstances,
                    maxInstances >= requiredMinInstances);

            assertTrue("Unable to achieve the maxFrameRate supported. act/exp: " + achievedFrameRate
                            + "/" + mMaxFrameRate + " for " + maxInstances + " instances.",
                    achievedFrameRate >= mMaxFrameRate);
        } else {
            int pc = maxInstances >= requiredMinInstances && achievedFrameRate >= mMaxFrameRate
                    ? Build.VERSION_CODES.R : 0;
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs",
                    "MultiDecoderPairPerf_" + mFirstPair.second);
            log.addValue("decoders",
                    mFirstPair.first + "_" + mFirstPair.second + "_" + mSecondPair.first + "_"
                            + mSecondPair.second, ResultType.NEUTRAL, ResultUnit.NONE);
            log.addValue("achieved_framerate", achievedFrameRate, ResultType.HIGHER_BETTER,
                    ResultUnit.NONE);
            log.addValue("expected_framerate", mMaxFrameRate, ResultType.NEUTRAL, ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.1/5.1/H-1-1,H-1-2 performance_class", pc, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }
}
