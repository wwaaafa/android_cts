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

package android.mediav2.cts;

import static android.mediav2.cts.CodecTestBase.SupportClass.CODEC_ALL;
import static android.mediav2.cts.CodecTestBase.SupportClass.CODEC_ANY;
import static android.mediav2.cts.CodecTestBase.SupportClass.CODEC_DEFAULT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The tests accepts multiple test vectors packaged in different way. For instance, Avc elementary
 * stream can be packed in mp4, avi, mkv, ts, 3gp, webm, ... These clips when decoded, are
 * expected to yield same output. Similarly for Vpx, av1, no-show frame can be packaged in-to
 * separate NALs or can be combined with a display frame in to one NAL. Both these scenarios
 * are expected to give same output. The test decodes all the test vectors it is given and
 * compares their outputs against each other. In short, the tests validate extractors, codecs
 * together.
 *
 * Additionally, as the test runs mediacodec in byte buffer mode.
 * 1. For normative codecs we expect the decoded output to be identical to reference decoded
 * output. The reference decoded output is sent to the test as crc32 checksum.
 * 2. For non normative codecs, the decoded output is checked for consistency.
 * 3. For lossless audio codecs, we check if the rms error of the decoded output is 0.
 * 4. For lossy audio codecs, we check if the rms error is within 5% of reference rms error.
 * 5. For video components the test expects the output timestamp list to be identical to input
 * timestamp list.
 * 6. For audio components, the test expect the output timestamps to be strictly increasing.
 * 7. The test also checks correctness of essential keys of output format of mediacodec.
 */
@RunWith(Parameterized.class)
public class CodecDecoderValidationTest extends CodecDecoderTestBase {
    private static final String MEDIA_TYPE_RAW = MediaFormat.MIMETYPE_AUDIO_RAW;
    private static final String MEDIA_TYPE_AMRNB = MediaFormat.MIMETYPE_AUDIO_AMR_NB;
    private static final String MEDIA_TYPE_AMRWB = MediaFormat.MIMETYPE_AUDIO_AMR_WB;
    private static final String MEDIA_TYPE_MP3 = MediaFormat.MIMETYPE_AUDIO_MPEG;
    private static final String MEDIA_TYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final String MEDIA_TYPE_FLAC = MediaFormat.MIMETYPE_AUDIO_FLAC;
    private static final String MEDIA_TYPE_VORBIS = MediaFormat.MIMETYPE_AUDIO_VORBIS;
    private static final String MEDIA_TYPE_OPUS = MediaFormat.MIMETYPE_AUDIO_OPUS;
    private static final String MEDIA_TYPE_MPEG2 = MediaFormat.MIMETYPE_VIDEO_MPEG2;
    private static final String MEDIA_TYPE_MPEG4 = MediaFormat.MIMETYPE_VIDEO_MPEG4;
    private static final String MEDIA_TYPE_AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String MEDIA_TYPE_VP8 = MediaFormat.MIMETYPE_VIDEO_VP8;
    private static final String MEDIA_TYPE_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    private static final String MEDIA_TYPE_VP9 = MediaFormat.MIMETYPE_VIDEO_VP9;
    private static final String MEDIA_TYPE_AV1 = MediaFormat.MIMETYPE_VIDEO_AV1;
    private final String[] mSrcFiles;
    private final String mRefFile;
    private final float mRmsError;
    private final long mRefCRC;
    private final int mSampleRate;
    private final int mChannelCount;
    private final int mWidth;
    private final int mHeight;
    private final SupportClass mSupportRequirements;

    public CodecDecoderValidationTest(String decoder, String mime, String[] srcFiles,
            String refFile, float rmsError, long refCRC, int sampleRate, int channelCount,
            int width, int height, SupportClass supportRequirements, String allTestParams) {
        super(decoder, mime, null, allTestParams);
        mSrcFiles = srcFiles;
        mRefFile = refFile;
        mRmsError = rmsError;
        mRefCRC = refCRC;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mWidth = width;
        mHeight = height;
        mSupportRequirements = supportRequirements;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = true;
        // mediaType, array list of test files (underlying elementary stream is same, except they
        // are placed in different containers), ref file, rms error, checksum, sample rate,
        // channel count, width, height, SupportClass
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // vp9 test vectors with no-show frames signalled in alternate ways
                {MEDIA_TYPE_VP9, new String[]{"bbb_340x280_768kbps_30fps_vp9.webm",
                        "bbb_340x280_768kbps_30fps_split_non_display_frame_vp9.webm"},
                        null, -1.0f, 4122701060L, -1, -1, 340, 280, CODEC_ALL},
                {MEDIA_TYPE_VP9, new String[]{"bbb_520x390_1mbps_30fps_vp9.webm",
                        "bbb_520x390_1mbps_30fps_split_non_display_frame_vp9.webm"},
                        null, -1.0f, 1201859039L, -1, -1, 520, 390, CODEC_ALL},

                // mpeg2 test vectors with interlaced fields signalled in alternate ways
                {MEDIA_TYPE_MPEG2, new String[]{"bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_1field.ts",
                        "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_2fields.mp4"},
                        null, -1.0f, -1L, -1, -1, 512, 288, CODEC_ALL},

                // misc mp3 test vectors
                {MEDIA_TYPE_MP3, new String[]{"bbb_1ch_16kHz_lame_vbr.mp3"},
                        "bbb_1ch_16kHz_s16le.raw", 119.256073f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_MP3, new String[]{"bbb_2ch_44kHz_lame_vbr.mp3"},
                        "bbb_2ch_44kHz_s16le.raw", 53.069080f, -1L, 44100, 2, -1, -1, CODEC_ALL},

                // mp3 test vectors with CRC
                {MEDIA_TYPE_MP3, new String[]{"bbb_2ch_44kHz_lame_crc.mp3"},
                        "bbb_2ch_44kHz_s16le.raw", 104.089027f, -1L, 44100, 2, -1, -1, CODEC_ALL},

                // vp9 test vectors with AQ mode enabled
                {MEDIA_TYPE_VP9, new String[]{"bbb_1280x720_800kbps_30fps_vp9.webm"},
                        null, -1.0f, 1319105122L, -1, -1, 1280, 720, CODEC_ALL},
                {MEDIA_TYPE_VP9, new String[]{"bbb_1280x720_1200kbps_30fps_vp9.webm"},
                        null, -1.0f, 4128150660L, -1, -1, 1280, 720, CODEC_ALL},
                {MEDIA_TYPE_VP9, new String[]{"bbb_1280x720_1600kbps_30fps_vp9.webm"},
                        null, -1.0f, 156928091L, -1, -1, 1280, 720, CODEC_ALL},
                {MEDIA_TYPE_VP9, new String[]{"bbb_1280x720_2000kbps_30fps_vp9.webm"},
                        null, -1.0f, 3902485256L, -1, -1, 1280, 720, CODEC_ALL},

                // video test vectors of non standard sizes
                {MEDIA_TYPE_MPEG2, new String[]{"bbb_642x642_2mbps_30fps_mpeg2.mp4"},
                        null, -1.0f, -1L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_AVC, new String[]{"bbb_642x642_1mbps_30fps_avc.mp4"},
                        null, -1.0f, 3947092788L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_VP8, new String[]{"bbb_642x642_1mbps_30fps_vp8.webm"},
                        null, -1.0f, 516982978L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_HEVC, new String[]{"bbb_642x642_768kbps_30fps_hevc.mp4"},
                        null, -1.0f, 3018465268L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_VP9, new String[]{"bbb_642x642_768kbps_30fps_vp9.webm"},
                        null, -1.0f, 4032809269L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_AV1, new String[]{"bbb_642x642_768kbps_30fps_av1.mp4"},
                        null, -1.0f, 3684481474L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_MPEG4, new String[]{"bbb_130x130_192kbps_15fps_mpeg4.mp4"},
                        null, -1.0f, -1L, -1, -1, 130, 130, CODEC_ANY},

                // audio test vectors covering cdd sec 5.1.3
                // amrnb
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_12.2kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_10.2kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_7.95kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_7.40kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_6.70kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_5.90kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_5.15kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_4.75kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},

                // amrwb
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_6.6kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_8.85kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_12.65kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_14.25kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_15.85kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_18.25kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_19.85kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_23.05kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_23.85kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},

                // opus
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_8kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_12kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_16kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_24kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_32kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_48kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_8kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_12kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_16kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_24kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_32kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_48kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_8kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_12kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_16kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_24kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_32kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_48kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_8kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_12kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_16kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_24kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_32kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_48kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},

                // vorbis
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_8kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_12kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 12000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_16kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_24kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 24000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_32kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 32000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_48kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_8kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 8000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_12kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 12000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_16kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 16000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_24kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 24000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_32kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 32000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_48kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/highres_1ch_96kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 96000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/highres_2ch_96kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 96000, 2, -1, -1, CODEC_ALL},

                // flac
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_8kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_8kHz_s16le_3s.raw", 0.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_12kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_12kHz_s16le_3s.raw", 0.0f, -1L, 12000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_16kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_16kHz_s16le_3s.raw", 0.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_22kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_22kHz_s16le_3s.raw", 0.0f, -1L, 22050, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_24kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_24kHz_s16le_3s.raw", 0.0f, -1L, 24000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_32kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_32kHz_s16le_3s.raw", 0.0f, -1L, 32000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_44kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_44kHz_s16le_3s.raw", 0.0f, -1L, 44100, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_48kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_48kHz_s16le_3s.raw", 0.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_8kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_8kHz_s16le_3s.raw", 0.0f, -1L, 8000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_12kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_12kHz_s16le_3s.raw", 0.0f, -1L, 12000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_16kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_16kHz_s16le_3s.raw", 0.0f, -1L, 16000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_22kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_22kHz_s16le_3s.raw", 0.0f, -1L, 22050, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_24kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_24kHz_s16le_3s.raw", 0.0f, -1L, 24000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_32kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_32kHz_s16le_3s.raw", 0.0f, -1L, 32000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_44kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_44kHz_s16le_3s.raw", 0.0f, -1L, 44100, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_48kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_48kHz_s16le_3s.raw", 0.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_1ch_96kHz_lvl4_flac.mka"},
                        "audio/highres_1ch_96kHz_s16le_5s.raw", 0.0f, -1L, 96000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_1ch_176kHz_lvl4_flac.mka"},
                        "audio/highres_1ch_176kHz_s16le_5s.raw", 0.0f, -1L, 176400, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_1ch_192kHz_lvl4_flac.mka"},
                        "audio/highres_1ch_192kHz_s16le_5s.raw", 0.0f, -1L, 192000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_2ch_96kHz_lvl4_flac.mka"},
                        "audio/highres_2ch_96kHz_s16le_5s.raw", 0.0f, -1L, 96000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_2ch_176kHz_lvl4_flac.mka"},
                        "audio/highres_2ch_176kHz_s16le_5s.raw", 0.0f, -1L, 176400, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_2ch_192kHz_lvl4_flac.mka"},
                        "audio/highres_2ch_192kHz_s16le_5s.raw", 0.0f, -1L, 192000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/sd_2ch_48kHz_lvl4_flac.mka"},
                        "audio/sd_2ch_48kHz_f32le.raw", 3.446394f, -1L, 48000, 2, -1, -1,
                        CODEC_ALL},

                // raw
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_8kHz.wav"},
                        "audio/bbb_1ch_8kHz_s16le_3s.raw", 0.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_16kHz.wav"},
                        "audio/bbb_1ch_16kHz_s16le_3s.raw", 0.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_22kHz.wav"},
                        "audio/bbb_1ch_22kHz_s16le_3s.raw", 0.0f, -1L, 22050, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_24kHz.wav"},
                        "audio/bbb_1ch_24kHz_s16le_3s.raw", 0.0f, -1L, 24000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_32kHz.wav"},
                        "audio/bbb_1ch_32kHz_s16le_3s.raw", 0.0f, -1L, 32000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_44kHz.wav"},
                        "audio/bbb_1ch_44kHz_s16le_3s.raw", 0.0f, -1L, 44100, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_48kHz.wav"},
                        "audio/bbb_1ch_48kHz_s16le_3s.raw", 0.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_8kHz.wav"},
                        "audio/bbb_2ch_8kHz_s16le_3s.raw", 0.0f, -1L, 8000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_16kHz.wav"},
                        "audio/bbb_2ch_16kHz_s16le_3s.raw", 0.0f, -1L, 16000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_22kHz.wav"},
                        "audio/bbb_2ch_22kHz_s16le_3s.raw", 0.0f, -1L, 22050, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_24kHz.wav"},
                        "audio/bbb_2ch_24kHz_s16le_3s.raw", 0.0f, -1L, 24000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_32kHz.wav"},
                        "audio/bbb_2ch_32kHz_s16le_3s.raw", 0.0f, -1L, 32000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_44kHz.wav"},
                        "audio/bbb_2ch_44kHz_s16le_3s.raw", 0.0f, -1L, 44100, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_48kHz.wav"},
                        "audio/bbb_2ch_48kHz_s16le_3s.raw", 0.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/highres_1ch_96kHz.wav"},
                        "audio/highres_1ch_96kHz_s16le_5s.raw", 0.0f, -1L, 96000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/highres_2ch_96kHz.wav"},
                        "audio/highres_2ch_96kHz_s16le_5s.raw", 0.0f, -1L, 96000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/sd_2ch_48kHz.wav"},
                        "audio/sd_2ch_48kHz_f32le.raw", 0.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bellezza_2ch_48kHz_s32le.wav"},
                        "audio/bellezza_2ch_48kHz_s32le.raw", 0.0f, -1L, 48000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bellezza_2ch_48kHz_s24le.wav"},
                        "audio/bellezza_2ch_48kHz_s24le.raw", 0.0f, -1L, 48000, 2, -1, -1,
                        CODEC_ALL},
                // aac-lc
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_8kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_8kHz_s16le_3s.raw", 26.910906f, -1L, 8000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_12kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_12kHz_s16le_3s.raw", 23.380817f, -1L, 12000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_16kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_16kHz_s16le_3s.raw", 21.368309f, -1L, 16000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_22kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_22kHz_s16le_3s.raw", 25.995440f, -1L, 22050, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_24kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_24kHz_s16le_3s.raw", 26.373266f, -1L, 24000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_32kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_32kHz_s16le_3s.raw", 28.642658f, -1L, 32000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_44kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_44kHz_s16le_3s.raw", 29.294861f, -1L, 44100, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_48kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_48kHz_s16le_3s.raw", 29.335669f, -1L, 48000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_8kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_8kHz_s16le_3s.raw", 26.381552f, -1L, 8000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_12kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_12kHz_s16le_3s.raw", 21.934900f, -1L, 12000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_16kHz_s16le_3s.raw", 22.072184f, -1L, 16000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_22kHz_s16le_3s.raw", 25.334206f, -1L, 22050, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_24kHz_s16le_3s.raw", 25.653538f, -1L, 24000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_32kHz_s16le_3s.raw", 27.312286f, -1L, 32000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_44kHz_s16le_3s.raw", 27.316111f, -1L, 44100, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_48kHz_s16le_3s.raw", 27.684767f, -1L, 48000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_8kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_8kHz_s16le_3s.raw", 43.121964f, -1L, 8000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_12kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_12kHz_s16le_3s.raw", 35.983891f, -1L, 12000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_16kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_16kHz_s16le_3s.raw", 32.720196f, -1L, 16000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_22kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_22kHz_s16le_3s.raw", 39.286514f, -1L, 22050, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_24kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_24kHz_s16le_3s.raw", 40.963005f, -1L, 24000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_32kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_32kHz_s16le_3s.raw", 49.437782f, -1L, 32000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_44kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_44kHz_s16le_3s.raw", 43.891609f, -1L, 44100, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_48kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_48kHz_s16le_3s.raw", 44.275997f, -1L, 48000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_8kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_8kHz_s16le_3s.raw", 39.666485f, -1L, 8000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_12kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_12kHz_s16le_3s.raw", 34.979305f, -1L, 12000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_16kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_16kHz_s16le_3s.raw", 29.069729f, -1L, 16000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_22kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_22kHz_s16le_3s.raw", 29.440094f, -1L, 22050, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_24kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_24kHz_s16le_3s.raw", 30.333755f, -1L, 24000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_32kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_32kHz_s16le_3s.raw", 33.927166f, -1L, 32000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_44kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_44kHz_s16le_3s.raw", 31.733339f, -1L, 44100, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_48kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_48kHz_s16le_3s.raw", 31.033596f, -1L, 48000, 6, -1, -1,
                        CODEC_ALL},

                // aac-he
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 16000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 22050, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 24000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 32000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 44100, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_16kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 16000, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_22kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 22050, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_24kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 24000, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_32kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 32000, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_44kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 44100, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_48kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_16kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 16000, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_22kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 22050, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_24kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 24000, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_32kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 32000, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_44kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 44100, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_48kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_DEFAULT},

                // aac-hev2
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 16000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 22050, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 24000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 32000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 44100, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_DEFAULT},

                // aac-eld
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_16kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_16kHz_s16le_3s.raw", -1.000000f, -1L, 16000, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_22kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_22kHz_s16le_3s.raw", 24.969662f, -1L, 22050, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_24kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_24kHz_s16le_3s.raw", 26.498655f, -1L, 24000, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_32kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_32kHz_s16le_3s.raw", 31.468872f, -1L, 32000, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_44kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_44kHz_s16le_3s.raw", 33.866409f, -1L, 44100, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_48kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_48kHz_s16le_3s.raw", 33.148113f, -1L, 48000, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_16kHz_s16le_3s.raw", -1.000000f, -1L, 16000, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_22kHz_s16le_3s.raw", 24.979313f, -1L, 22050, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_24kHz_s16le_3s.raw", 26.977774f, -1L, 24000, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_32kHz_s16le_3s.raw", 27.790754f, -1L, 32000, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_44kHz_s16le_3s.raw", 29.236626f, -1L, 44100, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_48kHz_s16le_3s.raw", 29.183796f, -1L, 48000, 2, -1, -1,
                        CODEC_DEFAULT},

                // aac-usac
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_8kHz_usac.m4a"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_16kHz_usac.m4a"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_22kHz_usac.m4a"},
                        null, -1.0f, -1L, 22050, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_24kHz_usac.m4a"},
                        null, -1.0f, -1L, 24000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_32kHz_usac.m4a"},
                        null, -1.0f, -1L, 32000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_44kHz_usac.m4a"},
                        null, -1.0f, -1L, 44100, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_48kHz_usac.m4a"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_8kHz_usac.m4a"},
                        null, -1.0f, -1L, 8000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_usac.m4a"},
                        null, -1.0f, -1L, 16000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_usac.m4a"},
                        null, -1.0f, -1L, 22050, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_usac.m4a"},
                        null, -1.0f, -1L, 24000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_usac.m4a"},
                        null, -1.0f, -1L, 32000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_usac.m4a"},
                        null, -1.0f, -1L, 44100, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_usac.m4a"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_DEFAULT},
        }));
        if (IS_AT_LEAST_U) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    // Support for 176kHz and 192kHz for c2.android.raw.decoder was added in
                    // Android U
                    {MEDIA_TYPE_RAW, new String[]{"audio/highres_1ch_176kHz.wav"},
                            "audio/highres_1ch_176kHz_s16le_5s.raw", 0.0f, -1L, 176400, 1, -1, -1,
                            CODEC_ALL},
                    {MEDIA_TYPE_RAW, new String[]{"audio/highres_1ch_192kHz.wav"},
                            "audio/highres_1ch_192kHz_s16le_5s.raw", 0.0f, -1L, 192000, 1, -1, -1,
                            CODEC_ALL},
                    {MEDIA_TYPE_RAW, new String[]{"audio/highres_2ch_176kHz.wav"},
                            "audio/highres_2ch_176kHz_s16le_5s.raw", 0.0f, -1L, 176400, 2, -1, -1,
                            CODEC_ALL},
                    {MEDIA_TYPE_RAW, new String[]{"audio/highres_2ch_192kHz.wav"},
                            "audio/highres_2ch_192kHz_s16le_5s.raw", 0.0f, -1L, 192000, 2, -1, -1,
                            CODEC_ALL},
            }));
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    /**
     * Extract, Decode and Validate
     */
    @ApiTest(apis = {"MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
                     "MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010",
                     "android.media.AudioFormat#ENCODING_PCM_16BIT"})
    @CddTest(requirements = "5.1.3")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecodeAndValidate() throws IOException, InterruptedException {
        ArrayList<MediaFormat> formats = new ArrayList<>();
        for (String file : mSrcFiles) {
            formats.add(setUpSource(file));
            mExtractor.release();
        }
        checkFormatSupport(mCodecName, mMime, false, formats, null, mSupportRequirements);
        {
            OutputManager ref = null;
            mSaveToMem = true;
            int audioEncoding = AudioFormat.ENCODING_INVALID;
            for (String file : mSrcFiles) {
                mOutputBuff = new OutputManager();
                mCodec = MediaCodec.createByCodecName(mCodecName);
                MediaFormat format = setUpSource(file);
                if (mIsAudio) {
                    audioEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT);
                }
                configureCodec(format, false, true, false);
                mCodec.start();
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                mOutFormat = mCodec.getOutputFormat();
                if (mIsAudio) {
                    assertEquals(mOutFormat.getInteger(MediaFormat.KEY_PCM_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT), audioEncoding);
                }
                mCodec.stop();
                mCodec.release();
                mExtractor.release();
                if (ref == null) ref = mOutputBuff;
                if (!(mIsInterlaced ? ref.equalsInterlaced(mOutputBuff) :
                        ref.equals(mOutputBuff))) {
                    fail("Decoder output received for file " + mSrcFiles[0]
                            + " is not identical to the output received for file " + file + "\n"
                            + mTestConfig + mTestEnv + mOutputBuff.getErrMsg());
                }
                assertEquals("Output sample rate is different from configured sample rate \n"
                                + mTestConfig + mTestEnv, mSampleRate,
                        mOutFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, -1));
                assertEquals("Output channel count is different from configured channel count \n"
                                + mTestConfig + mTestEnv, mChannelCount,
                        mOutFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -1));
                assertEquals("Output width is different from configured width \n" + mTestConfig
                        + mTestEnv, mWidth, getWidth(mOutFormat));
                assertEquals("Output height is different from configured height \n" + mTestConfig
                        + mTestEnv, mHeight, getHeight(mOutFormat));
            }
            Assume.assumeFalse("skip checksum verification due to tone mapping",
                    mSkipChecksumVerification);
            CodecDecoderTest.verify(ref, mRefFile, mRmsError, audioEncoding, mRefCRC,
                    mTestConfig + mTestEnv);
        }
    }
}
