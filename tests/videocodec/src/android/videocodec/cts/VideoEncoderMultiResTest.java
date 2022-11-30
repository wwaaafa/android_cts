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

package android.videocodec.cts;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
import static android.mediav2.common.cts.CodecTestBase.ComponentClass.HARDWARE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.mediav2.common.cts.CompareStreams;
import android.mediav2.common.cts.RawResource;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * This test is to validate the encoder's support for standard resolutions
 * <p></p>
 * Test Params:
 * <p>Input resolution = standard resolutions of social media apps</p>
 * <p>Number of frames = 8</p>
 * <p>Target bitrate = 5 mbps</p>
 * <p>bitrate mode = cbr/vbr</p>
 * <p>max b frames = 0/1</p>
 * <p>IFrameInterval = 0/1 second</p>
 * <p></p>
 * For the chosen clip and above encoder configuration, the test expects the encoded clip to be
 * decodable and the decoded resolution is as expected
 */
@RunWith(Parameterized.class)
public class VideoEncoderMultiResTest extends VideoEncoderValidationTestBase {
    private static final float ACCEPTABLE_WIRELESS_TX_QUALITY = 20.0f;  // psnr in dB
    private static final int FRAME_LIMIT = 30;
    private static final int BIT_RATE = 5000000;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();
    private static final HashMap<String, RawResource> RES_YUV_MAP = new HashMap<>();

    private final int mFps;
    private final int mBitRateMode;
    private final int mIntraInterval;

    private MediaMuxer mMuxer;
    private int mTrackID = -1;

    @BeforeClass
    public static void decodeResourcesToYuv() {
        ArrayList<CompressedResource> resources = new ArrayList<>();
        for (Object[] arg : exhaustiveArgsList) {
            resources.add((CompressedResource) arg[7]);
        }
        decodeStreamsToYuv(resources, RES_YUV_MAP, FRAME_LIMIT);
    }

    @AfterClass
    public static void cleanUpResources() {
        for (RawResource res : RES_YUV_MAP.values()) {
            new File(res.mFileName).delete();
        }
    }

    private static void addParams(int width, int height, int frameRate) {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC};
        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        final int[] maxBFramesPerSubGop = new int[]{0, 1};
        final int[] intraIntervals = new int[]{0, 1};
        for (String mediaType : mediaTypes) {
            for (int maxBFrames : maxBFramesPerSubGop) {
                for (int bitRateMode : bitRateModes) {
                    for (int intraInterval : intraIntervals) {
                        // mediaType, wd, ht, frame rate, max b frames, bitrate mode, I-interval,
                        // res, label
                        String label = String.format("%dx%d_%dfps_maxb-%d_%s_i-dist-%d", width,
                                height, frameRate, maxBFrames, bitRateModeToString(bitRateMode),
                                intraInterval);
                        exhaustiveArgsList.add(new Object[]{mediaType, width, height, frameRate,
                                maxBFrames, bitRateMode, intraInterval, BIRTHDAY_FULLHD_LANDSCAPE,
                                label});
                    }
                }
            }
        }
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{9})")
    public static Collection<Object[]> input() {
        addParams(1080, 1920, 30);
        addParams(720, 1280, 30);
        addParams(720, 720, 30);
        addParams(512, 896, 30);
        addParams(1920, 960, 30);
        addParams(544, 1088, 30);
        addParams(360, 640, 24);
        addParams(720, 1280, 30);
        addParams(512, 896, 30);
        addParams(544, 1104, 30);

        addParams(544, 1120, 30);
        addParams(1552, 688, 30);
        addParams(576, 1024, 30);
        addParams(720, 1472, 30);
        addParams(1920, 864, 30);

        addParams(426, 240, 30);
        addParams(640, 360, 30);
        addParams(854, 480, 30);
        addParams(2560, 1440, 30);
        addParams(3840, 2160, 30);

        addParams(240, 426, 30);
        addParams(360, 640, 30);
        addParams(480, 854, 30);
        addParams(1440, 2560, 30);
        addParams(2160, 3840, 30);

        addParams(360, 360, 30);
        addParams(1920, 1920, 30);

        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderMultiResTest(String encoder, String mediaType, int width, int height,
            int frameRate, int maxBFrames, int bitRateMode, int intraInterval,
            CompressedResource res, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, BIT_RATE, width, height,
                RES_YUV_MAP.getOrDefault(res.uniqueLabel(), null), allTestParams);
        mFps = frameRate;
        mMaxBFrames = maxBFrames;
        mBitRateMode = bitRateMode;
        mIntraInterval = intraInterval;
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
    }

    @After
    public void tearDown() {
        if (mMuxer != null) {
            mMuxer.release();
            mMuxer = null;
        }
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0) {
            ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
            if (mMuxer != null) {
                if (mTrackID == -1) {
                    mTrackID = mMuxer.addTrack(mCodec.getOutputFormat());
                    mMuxer.start();
                }
                mMuxer.writeSampleData(mTrackID, buf, info);
            }
        }
        super.dequeueOutput(bufferIndex, info);
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_WIDTH",
            "android.media.MediaFormat#KEY_HEIGHT"})
    @Test
    public void testMultiRes() throws IOException, InterruptedException {
        setUpParams(1);
        MediaFormat format = mFormats.get(0);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIntraInterval);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, mBitRateMode);
        Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support format: " + mFormats.get(0),
                areFormatsSupported(mCodecName, mMime, mFormats));
        String tmpPath = File.createTempFile("tmp", ".bin").getAbsolutePath();
        mMuxer = new MediaMuxer(tmpPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        encodeToMemory(mActiveRawRes.mFileName, mCodecName, FRAME_LIMIT, format, true);
        if (mTrackID != -1) {
            mMuxer.stop();
            mTrackID = -1;
        }
        if (mMuxer != null) {
            mMuxer.release();
            mMuxer = null;
        }
        assertEquals("Output width is different from configured width \n" + mTestConfig
                + mTestEnv, mWidth, getWidth(getOutputFormat()));
        assertEquals("Output height is different from configured height \n" + mTestConfig
                + mTestEnv, mHeight, getHeight(getOutputFormat()));
        CompareStreams cs = null;
        StringBuilder msg = new StringBuilder();
        boolean isOk = true;
        try {
            cs = new CompareStreams(mActiveRawRes, mMime, tmpPath, true, mIsLoopBack);
            final double[] minPSNR = cs.getMinimumPSNR();
            for (int i = 0; i < minPSNR.length; i++) {
                if (minPSNR[i] < ACCEPTABLE_WIRELESS_TX_QUALITY) {
                    msg.append(String.format(
                            "For %d plane, minPSNR is less than tolerance threshold, Got %f, "
                                    + "Threshold %f",
                            i, minPSNR[i], ACCEPTABLE_WIRELESS_TX_QUALITY));
                    isOk = false;
                    break;
                }
            }
        } finally {
            if (cs != null) cs.cleanUp();
        }
        new File(tmpPath).delete();
        assertEquals("encoder did not encode the requested number of frames \n"
                + mTestConfig + mTestEnv, FRAME_LIMIT, mOutputCount);
        assertTrue("Encountered frames with PSNR less than configured threshold "
                + ACCEPTABLE_WIRELESS_TX_QUALITY + "dB \n" + msg + mTestConfig + mTestEnv, isOk);
    }
}
