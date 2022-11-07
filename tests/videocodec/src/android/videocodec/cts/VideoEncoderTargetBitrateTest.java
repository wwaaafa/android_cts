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

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.RawResource;

import com.android.compatibility.common.util.ApiTest;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * This test verifies encoders support for bitrate modes VBR and CBR.
 * <p></p>
 * Test Params:
 * <p>Input resolution = 1080p30fps</p>
 * <p>Number of frames = 300</p>
 * <p>Target bitrate = 5, 8, 10 Mbps</p>
 * <p>Bitrate mode = VBR/CBR</p>
 * <p>MaxBFrames = 0/1</p>
 * <p>IFrameInterval = 1 seconds</p>
 * <p></p>
 * For VBR, the encoded size of the frames in sliding window of 1 second should not be greater
 * than (bitrate / 8) * 2 bytes
 * For CBR, the encoded size of the frames in sliding window of 1 second should not be greater
 * than 1.15 * (bitrate / 8) bytes
 */
@RunWith(Parameterized.class)
public class VideoEncoderTargetBitrateTest extends VideoEncoderValidationTestBase {
    private static final int KEY_FRAME_INTERVAL = 1;
    private static final int FRAME_LIMIT = 300;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();
    private static final HashMap<String, RawResource> RES_YUV_MAP = new HashMap<>();

    private final int mBitRateMode;
    private final float mThreshold;
    private final Queue<Integer> mBufferSize = new LinkedList<>();
    private int mTotalOvershoots = 0;
    private final StringBuilder mMsg = new StringBuilder();

    @BeforeClass
    public static void decodeResourcesToYuv() {
        ArrayList<CompressedResource> resources = new ArrayList<>();
        for (Object[] arg : exhaustiveArgsList) {
            resources.add((CompressedResource) arg[6]);
        }
        decodeStreamsToYuv(resources, RES_YUV_MAP);
    }

    @AfterClass
    public static void cleanUpResources() {
        for (RawResource res : RES_YUV_MAP.values()) {
            new File(res.mFileName).delete();
        }
    }

    private static void addParams(int width, int height, CompressedResource res) {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC};
        final int[] bitRates = new int[]{5000000, 8000000, 10000000};
        final int[] maxBFramesPerSubGop = new int[]{0, 1};
        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        for (String mediaType : mediaTypes) {
            for (int bitRate : bitRates) {
                for (int maxBFrames : maxBFramesPerSubGop) {
                    for (int bitRateMode : bitRateModes) {
                        // mediaType, bit-rate, width, height, max b frames, bitrate mode,
                        // resource file, test label
                        String label = String.format("%dkbps_%dx%d_maxb-%d_%s", bitRate / 1000,
                                width, height, maxBFrames, bitRateModeToString(bitRateMode));
                        exhaustiveArgsList.add(new Object[]{mediaType, bitRate, width, height,
                                maxBFrames, bitRateMode, res, label});
                    }
                }
            }
        }
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{8})")
    public static Collection<Object[]> input() {
        addParams(1920, 1080, BIRTHDAY_FULLHD_LANDSCAPE);
        addParams(1080, 1920, SELFIEGROUP_FULLHD_PORTRAIT);
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderTargetBitrateTest(String encoder, String mediaType, int bitRate, int width,
            int height, int maxBFrames, int bitRateMode, CompressedResource res,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, bitRate, width, height,
                RES_YUV_MAP.getOrDefault(res.uniqueLabel(), null), allTestParams);
        mMaxBFrames = maxBFrames;
        mBitRateMode = bitRateMode;
        float sf = 1.f;
        if (mBitRateMode == BITRATE_MODE_VBR) {
            sf = 2.f;
        } else if (mBitRateMode == BITRATE_MODE_CBR) {
            sf = 1.15f;
        }
        mThreshold = bitRate / 8.f * sf;
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0)) {
            mBufferSize.add(mOutputBuff.getOutStreamSize());
        }
        super.dequeueOutput(bufferIndex, info);
        if (mOutputCount >= mFrameRate) {
            int size = mOutputBuff.getOutStreamSize() - mBufferSize.remove();
            if (size > mThreshold) {
                mTotalOvershoots++;
                mMsg.append(String.format("At frame %d, total size of the frames in last 1 second"
                        + " is %d > %f (Threshold) \n", mOutputCount, size, mThreshold));
            }
        }
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_BITRATE_MODE"})
    @Test
    public void testEncoderBitRateModeSupport() throws IOException, InterruptedException {
        setUpParams(1);
        MediaFormat format = mFormats.get(0);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_INTERVAL);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, mBitRateMode);
        Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support format: " + mFormats.get(0),
                areFormatsSupported(mCodecName, mMime, mFormats));
        encodeToMemory(mActiveRawRes.mFileName, mCodecName, FRAME_LIMIT, format, true);
        assertEquals("encoder did not encode the requested number of frames \n"
                + mTestConfig + mTestEnv, FRAME_LIMIT, mOutputCount);
        Assume.assumeTrue(mMsg.toString() + mTestConfig + mTestEnv, mTotalOvershoots == 0);
    }
}
