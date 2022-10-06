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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing;
import static android.media.MediaCodecInfo.CodecProfileLevel.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.AudioFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

class CodecAsyncHandler extends MediaCodec.Callback {
    private static final String LOG_TAG = CodecAsyncHandler.class.getSimpleName();
    private final Lock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();
    private final LinkedList<Pair<Integer, MediaCodec.BufferInfo>> mCbInputQueue;
    private final LinkedList<Pair<Integer, MediaCodec.BufferInfo>> mCbOutputQueue;
    private MediaFormat mOutFormat;
    private boolean mSignalledOutFormatChanged;
    private volatile boolean mSignalledError;
    private String mErrorMsg;

    CodecAsyncHandler() {
        mCbInputQueue = new LinkedList<>();
        mCbOutputQueue = new LinkedList<>();
        mSignalledError = false;
        mSignalledOutFormatChanged = false;
        mErrorMsg = "";
    }

    void clearQueues() {
        mLock.lock();
        mCbInputQueue.clear();
        mCbOutputQueue.clear();
        mLock.unlock();
    }

    void resetContext() {
        clearQueues();
        mOutFormat = null;
        mSignalledOutFormatChanged = false;
        mErrorMsg = "";
        mSignalledError = false;
    }

    @Override
    public void onInputBufferAvailable(@NonNull MediaCodec codec, int bufferIndex) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        mCbInputQueue.add(new Pair<>(bufferIndex, null));
        mCondition.signalAll();
        mLock.unlock();
    }

    @Override
    public void onOutputBufferAvailable(@NonNull MediaCodec codec, int bufferIndex,
            @NonNull MediaCodec.BufferInfo info) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        mCbOutputQueue.add(new Pair<>(bufferIndex, info));
        mCondition.signalAll();
        mLock.unlock();
    }

    @Override
    public void onError(@NonNull MediaCodec codec, MediaCodec.CodecException e) {
        mLock.lock();
        mErrorMsg = "###################  Async Error Details  #####################\n";
        mErrorMsg += e.toString() + "\n";
        mSignalledError = true;
        mCondition.signalAll();
        mLock.unlock();
        Log.e(LOG_TAG, "received media codec error : " + e.getMessage());
    }

    @Override
    public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
        mOutFormat = format;
        mSignalledOutFormatChanged = true;
        Log.i(LOG_TAG, "Output format changed: " + format);
    }

    void setCallBack(MediaCodec codec, boolean isCodecInAsyncMode) {
        if (isCodecInAsyncMode) {
            codec.setCallback(this);
        } else {
            codec.setCallback(null);
        }
    }

    Pair<Integer, MediaCodec.BufferInfo> getInput() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        while (!mSignalledError) {
            if (mCbInputQueue.isEmpty()) {
                mCondition.await();
            } else {
                element = mCbInputQueue.remove(0);
                break;
            }
        }
        mLock.unlock();
        return element;
    }

    Pair<Integer, MediaCodec.BufferInfo> getOutput() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        while (!mSignalledError) {
            if (mCbOutputQueue.isEmpty()) {
                mCondition.await();
            } else {
                element = mCbOutputQueue.remove(0);
                break;
            }
        }
        mLock.unlock();
        return element;
    }

    Pair<Integer, MediaCodec.BufferInfo> getWork() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        while (!mSignalledError) {
            if (mCbInputQueue.isEmpty() && mCbOutputQueue.isEmpty()) {
                mCondition.await();
            } else {
                if (!mCbOutputQueue.isEmpty()) {
                    element = mCbOutputQueue.remove(0);
                } else {
                    element = mCbInputQueue.remove(0);
                }
                break;
            }
        }
        mLock.unlock();
        return element;
    }

    boolean isInputQueueEmpty() {
        mLock.lock();
        boolean isEmpty = mCbInputQueue.isEmpty();
        mLock.unlock();
        return isEmpty;
    }

    boolean hasSeenError() {
        return mSignalledError;
    }

    String getErrMsg() {
        return mErrorMsg;
    }

    boolean hasOutputFormatChanged() {
        return mSignalledOutFormatChanged;
    }

    MediaFormat getOutputFormat() {
        return mOutFormat;
    }
}

class OutputManager {
    private static final String LOG_TAG = OutputManager.class.getSimpleName();
    private byte[] memory;
    private int memIndex;
    private CRC32 mCrc32UsingImage;
    private CRC32 mCrc32UsingBuffer;
    private ArrayList<Long> inpPtsList;
    private ArrayList<Long> outPtsList;
    private String mErrorLogs;

    OutputManager() {
        memory = new byte[1024];
        memIndex = 0;
        mCrc32UsingImage = new CRC32();
        mCrc32UsingBuffer = new CRC32();
        inpPtsList = new ArrayList<>();
        outPtsList = new ArrayList<>();
        mErrorLogs = "###################       Error Details         #####################\n";
    }

    void saveInPTS(long pts) {
        // Add only unique timeStamp, discarding any duplicate frame / non-display frame
        if (!inpPtsList.contains(pts)) {
            inpPtsList.add(pts);
        }
    }

    void saveOutPTS(long pts) {
        outPtsList.add(pts);
    }

    boolean isPtsStrictlyIncreasing(long lastPts) {
        boolean res = true;
        for (int i = 0; i < outPtsList.size(); i++) {
            if (lastPts < outPtsList.get(i)) {
                lastPts = outPtsList.get(i);
            } else {
                StringBuilder msg = new StringBuilder(1024);
                msg.append("Frame indices around which timestamp values decreased :- \n");
                for (int j = Math.max(0, i - 3); j < Math.min(outPtsList.size(), i + 3); j++) {
                    if (j == 0) {
                        msg.append(String.format("pts of frame idx -1 is %d \n", lastPts));
                    }
                    msg.append(String.format("pts of frame idx %d is %d \n", j, outPtsList.get(j)));
                }
                mErrorLogs += "Timestamp values are not strictly increasing. \n";
                mErrorLogs += msg.toString();
                res = false;
                break;
            }
        }
        return res;
    }

    boolean arePtsListsIdentical(ArrayList<Long> refList, ArrayList<Long> testList,
            StringBuilder msg) {
        boolean res = true;
        if (refList.size() != testList.size()) {
            msg.append("Reference and test timestamps list sizes are not identical \n");
            msg.append(String.format("reference pts list size is %d \n", refList.size()));
            msg.append(String.format("test pts list size is %d \n", testList.size()));
            res = false;
        }
        if (!res || !refList.equals(testList)) {
            res = false;
            ArrayList<Long> refCopyList = new ArrayList<>(refList);
            ArrayList<Long> testCopyList = new ArrayList<>(testList);
            refCopyList.removeAll(testList);
            testCopyList.removeAll(refList);
            if (refCopyList.size() != 0) {
                msg.append("Some of the frame/access-units present in ref list are not present "
                        + "in test list. Possibly due to frame drops. \n");
                msg.append("List of timestamps that are dropped by the component :- \n");
                msg.append("pts :- [[ ");
                for (int i = 0; i < refCopyList.size(); i++) {
                    msg.append(String.format("{ %d us }, ", refCopyList.get(i)));
                }
                msg.append(" ]]\n");
            }
            if (testCopyList.size() != 0) {
                msg.append("Test list contains frame/access-units that are not present in"
                        + " ref list, Possible due to duplicate transmissions. \n");
                msg.append("List of timestamps that are additionally present in test list"
                        + " are :- \n");
                msg.append("pts :- [[ ");
                for (int i = 0; i < testCopyList.size(); i++) {
                    msg.append(String.format("{ %d us }, ", testCopyList.get(i)));
                }
                msg.append(" ]]\n");
            }
        }
        return res;
    }

    boolean isOutPtsListIdenticalToInpPtsList(boolean requireSorting) {
        boolean res;
        Collections.sort(inpPtsList);
        if (requireSorting) {
            Collections.sort(outPtsList);
        }
        StringBuilder msg = new StringBuilder();
        res = arePtsListsIdentical(inpPtsList, outPtsList, msg);
        mErrorLogs += msg.toString();
        return res;
    }

    int getOutStreamSize() {
        return memIndex;
    }

    void checksum(ByteBuffer buf, int size) {
        checksum(buf, size, 0, 0, 0, 0);
    }

    void checksum(ByteBuffer buf, int size, int width, int height, int stride, int bytesPerSample) {
        int cap = buf.capacity();
        assertTrue("checksum() params are invalid: size = " + size + " cap = " + cap,
                size > 0 && size <= cap);
        if (buf.hasArray()) {
            if (width > 0 && height > 0 && stride > 0 && bytesPerSample > 0) {
                int offset = buf.position() + buf.arrayOffset();
                byte[] bb = new byte[width * height * bytesPerSample];
                for (int i = 0; i < height; ++i) {
                    System.arraycopy(buf.array(), offset, bb, i * width * bytesPerSample,
                            width * bytesPerSample);
                    offset += stride;
                }
                mCrc32UsingBuffer.update(bb, 0, width * height * bytesPerSample);
            } else {
                mCrc32UsingBuffer.update(buf.array(), buf.position() + buf.arrayOffset(), size);
            }
        } else if (width > 0 && height > 0 && stride > 0 && bytesPerSample > 0) {
            // Checksum only the Y plane
            int pos = buf.position();
            int offset = pos;
            byte[] bb = new byte[width * height * bytesPerSample];
            for (int i = 0; i < height; ++i) {
                buf.position(offset);
                buf.get(bb, i * width * bytesPerSample, width * bytesPerSample);
                offset += stride;
            }
            mCrc32UsingBuffer.update(bb, 0, width * height * bytesPerSample);
            buf.position(pos);
        } else {
            int pos = buf.position();
            final int rdsize = Math.min(4096, size);
            byte[] bb = new byte[rdsize];
            int chk;
            for (int i = 0; i < size; i += chk) {
                chk = Math.min(rdsize, size - i);
                buf.get(bb, 0, chk);
                mCrc32UsingBuffer.update(bb, 0, chk);
            }
            buf.position(pos);
        }
    }

    void checksum(Image image) {
        int format = image.getFormat();
        assertTrue("unexpected image format",
                format == ImageFormat.YUV_420_888 || format == ImageFormat.YCBCR_P010);
        int bytesPerSample = (ImageFormat.getBitsPerPixel(format) * 2) / (8 * 3);  // YUV420

        Rect cropRect = image.getCropRect();
        int imageWidth = cropRect.width();
        int imageHeight = cropRect.height();
        assertTrue("unexpected image dimensions", imageWidth > 0 && imageHeight > 0);

        int imageLeft = cropRect.left;
        int imageTop = cropRect.top;
        Image.Plane[] planes = image.getPlanes();
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();
            int width, height, rowStride, pixelStride, x, y, left, top;
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            if (i == 0) {
                assertEquals(bytesPerSample, pixelStride);
                width = imageWidth;
                height = imageHeight;
                left = imageLeft;
                top = imageTop;
            } else {
                width = imageWidth / 2;
                height = imageHeight / 2;
                left = imageLeft / 2;
                top = imageTop / 2;
            }
            int cropOffset = (left * pixelStride) + top * rowStride;
            // local contiguous pixel buffer
            byte[] bb = new byte[width * height * bytesPerSample];

            if (buf.hasArray()) {
                byte[] b = buf.array();
                int offs = buf.arrayOffset() + cropOffset;
                if (pixelStride == bytesPerSample) {
                    for (y = 0; y < height; ++y) {
                        System.arraycopy(b, offs + y * rowStride, bb, y * width * bytesPerSample,
                                width * bytesPerSample);
                    }
                } else {
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        int lineOffset = offs + y * rowStride;
                        for (x = 0; x < width; ++x) {
                            for (int bytePos = 0; bytePos < bytesPerSample; ++bytePos) {
                                bb[y * width * bytesPerSample + x * bytesPerSample + bytePos] =
                                        b[lineOffset + x * pixelStride + bytePos];
                            }
                        }
                    }
                }
            } else { // almost always ends up here due to direct buffers
                int base = buf.position();
                int pos = base + cropOffset;
                if (pixelStride == bytesPerSample) {
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        buf.get(bb, y * width * bytesPerSample, width * bytesPerSample);
                    }
                } else {
                    // local line buffer
                    byte[] lb = new byte[rowStride];
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        // we're only guaranteed to have pixelStride * (width - 1) +
                        // bytesPerSample bytes
                        buf.get(lb, 0, pixelStride * (width - 1) + bytesPerSample);
                        for (x = 0; x < width; ++x) {
                            for (int bytePos = 0; bytePos < bytesPerSample; ++bytePos) {
                                bb[y * width * bytesPerSample + x * bytesPerSample + bytePos] =
                                        lb[x * pixelStride + bytePos];
                            }
                        }
                    }
                }
                buf.position(base);
            }
            mCrc32UsingImage.update(bb, 0, width * height * bytesPerSample);
        }
    }

    void saveToMemory(ByteBuffer buf, MediaCodec.BufferInfo info) {
        if (memIndex + info.size >= memory.length) {
            memory = Arrays.copyOf(memory, memIndex + info.size);
        }
        buf.position(info.offset);
        buf.get(memory, memIndex, info.size);
        memIndex += info.size;
    }

    void position(int index) {
        if (index < 0 || index >= memory.length) index = 0;
        memIndex = index;
    }

    ByteBuffer getBuffer() {
        return ByteBuffer.wrap(memory);
    }

    void reset() {
        position(0);
        mCrc32UsingImage.reset();
        mCrc32UsingBuffer.reset();
        inpPtsList.clear();
        outPtsList.clear();
        mErrorLogs = "###################       Error Details         #####################\n";
    }

    float getRmsError(Object refObject, int audioFormat) {
        double totalErrorSquared = 0;
        double avgErrorSquared;
        int bytesPerSample = AudioFormat.getBytesPerSample(audioFormat);
        if (refObject instanceof float[]) {
            if (audioFormat != AudioFormat.ENCODING_PCM_FLOAT) return Float.MAX_VALUE;
            float[] refData = (float[]) refObject;
            if (refData.length != memIndex / bytesPerSample) return Float.MAX_VALUE;
            float[] floatData = new float[refData.length];
            ByteBuffer.wrap(memory, 0, memIndex).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    .get(floatData);
            for (int i = 0; i < refData.length; i++) {
                float d = floatData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else if (refObject instanceof int[]) {
            int[] refData = (int[]) refObject;
            int[] intData;
            if (audioFormat == AudioFormat.ENCODING_PCM_24BIT_PACKED) {
                if (refData.length != (memIndex / bytesPerSample)) return Float.MAX_VALUE;
                intData = new int[refData.length];
                for (int i = 0, j = 0; i < memIndex; i += 3, j++) {
                    intData[j] = memory[j] | (memory[j + 1] << 8) | (memory[j + 2] << 16);
                }
            } else if (audioFormat == AudioFormat.ENCODING_PCM_32BIT) {
                if (refData.length != memIndex / bytesPerSample) return Float.MAX_VALUE;
                intData = new int[refData.length];
                ByteBuffer.wrap(memory, 0, memIndex).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                        .get(intData);
            } else {
                return Float.MAX_VALUE;
            }
            for (int i = 0; i < intData.length; i++) {
                float d = intData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else if (refObject instanceof short[]) {
            short[] refData = (short[]) refObject;
            if (refData.length != memIndex / bytesPerSample) return Float.MAX_VALUE;
            if (audioFormat != AudioFormat.ENCODING_PCM_16BIT) return Float.MAX_VALUE;
            short[] shortData = new short[refData.length];
            ByteBuffer.wrap(memory, 0, memIndex).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    .get(shortData);
            for (int i = 0; i < shortData.length; i++) {
                float d = shortData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else if (refObject instanceof byte[]) {
            byte[] refData = (byte[]) refObject;
            if (refData.length != memIndex / bytesPerSample) return Float.MAX_VALUE;
            if (audioFormat != AudioFormat.ENCODING_PCM_8BIT) return Float.MAX_VALUE;
            byte[] byteData = new byte[refData.length];
            ByteBuffer.wrap(memory, 0, memIndex).get(byteData);
            for (int i = 0; i < byteData.length; i++) {
                float d = byteData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else {
            return Float.MAX_VALUE;
        }
        return (float) Math.sqrt(avgErrorSquared);
    }

    long getCheckSumImage() {
        return mCrc32UsingImage.getValue();
    }

    long getCheckSumBuffer() {
        return mCrc32UsingBuffer.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputManager that = (OutputManager) o;

        if (!this.equalsInterlaced(o)) return false;
        StringBuilder msg = new StringBuilder();
        boolean res = arePtsListsIdentical(outPtsList, that.outPtsList, msg);
        that.mErrorLogs += msg.toString();
        return res;
    }

    // TODO: Timestamps for deinterlaced content are under review. (E.g. can decoders
    // produce multiple progressive frames?) For now, do not verify timestamps.
    public boolean equalsInterlaced(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputManager that = (OutputManager) o;
        boolean isEqual = true;
        if (mCrc32UsingImage.getValue() != that.mCrc32UsingImage.getValue()) {
            isEqual = false;
            that.mErrorLogs += "CRC32 checksums computed for image buffers received from "
                    + "getOutputImage() do not match between ref and test runs. \n";
            that.mErrorLogs += String.format("Ref CRC32 checksum value is %d \n",
                    mCrc32UsingImage.getValue());
            that.mErrorLogs += String.format("Test CRC32 checksum value is %d \n",
                    that.mCrc32UsingImage.getValue());
        }
        if (mCrc32UsingBuffer.getValue() != that.mCrc32UsingBuffer.getValue()) {
            isEqual = false;
            that.mErrorLogs += "CRC32 checksums computed for byte buffers received from "
                    + "getOutputBuffer() do not match between ref and test runs. \n";
            that.mErrorLogs += String.format("Ref CRC32 checksum value is %d \n",
                    mCrc32UsingBuffer.getValue());
            that.mErrorLogs += String.format("Test CRC32 checksum value is %d \n",
                    that.mCrc32UsingBuffer.getValue());
            if (memIndex == that.memIndex) {
                int count = 0;
                StringBuilder msg = new StringBuilder();
                for (int i = 0; i < memIndex; i++) {
                    if (memory[i] != that.memory[i]) {
                        count++;
                        msg.append(String.format("At offset %d, ref buffer val is %x and test "
                                + "buffer val is %x \n", i, memory[i], that.memory[i]));
                        if (count == 20) {
                            msg.append("stopping after 20 mismatches, ...\n");
                            break;
                        }
                    }
                }
                if (count != 0) {
                    that.mErrorLogs += "Ref and Test outputs are not identical \n";
                    that.mErrorLogs += msg.toString();
                }
            } else {
                that.mErrorLogs += "CRC32 byte buffer checksums are different because ref and test "
                        + "output sizes are not identical \n";
                that.mErrorLogs += String.format("Ref output buffer size %d \n", memIndex);
                that.mErrorLogs += String.format("Test output buffer size %d \n", that.memIndex);
            }
        }
        return isEqual;
    }

    String getErrMsg() {
        return mErrorLogs;
    }
}

abstract class CodecTestBase {
    public static final boolean IS_Q = ApiLevelUtil.getApiLevel() == Build.VERSION_CODES.Q;
    public static final boolean IS_AT_LEAST_R = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    public static final boolean IS_AT_LEAST_T =
            ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU);
    //TODO(b/248315681) Remove codenameEquals() check once devices return correct version for U
    public static final boolean IS_AT_LEAST_U = ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)
            || ApiLevelUtil.codenameEquals("UpsideDownCake");
    public static final boolean FIRST_SDK_IS_AT_LEAST_T =
            ApiLevelUtil.isFirstApiAtLeast(Build.VERSION_CODES.TIRAMISU);
    public static final boolean VNDK_IS_AT_LEAST_T =
            SystemProperties.getInt("ro.vndk.version", 0) >= Build.VERSION_CODES.TIRAMISU;
    public static final boolean IS_HDR_EDITING_SUPPORTED = isHDREditingSupported();
    private static final String LOG_TAG = CodecTestBase.class.getSimpleName();

    static final ArrayList<String> HDR_INFO_IN_BITSTREAM_CODECS = new ArrayList<>();
    static final String HDR_STATIC_INFO =
            "00 d0 84 80 3e c2 33 c4 86 4c 1d b8 0b 13 3d 42 40 a0 0f 32 00 10 27 df 0d";
    static final String HDR_STATIC_INCORRECT_INFO =
            "00 d0 84 80 3e c2 33 c4 86 10 27 d0 07 13 3d 42 40 a0 0f 32 00 10 27 df 0d";
    static final HashMap<Integer, String> HDR_DYNAMIC_INFO = new HashMap<>();
    static final HashMap<Integer, String> HDR_DYNAMIC_INCORRECT_INFO = new HashMap<>();
    static final String CODEC_PREFIX_KEY = "codec-prefix";
    static final String MEDIA_TYPE_PREFIX_KEY = "media-type-prefix";
    static final String MIME_SEL_KEY = "mime-sel";
    static final Map<String, String> codecSelKeyMimeMap = new HashMap<>();
    static final Map<String, String> mDefaultEncoders = new HashMap<>();
    static final Map<String, String> mDefaultDecoders = new HashMap<>();
    static final HashMap<String, int[]> mProfileMap = new HashMap<>();
    static final HashMap<String, int[]> mProfileSdrMap = new HashMap<>();
    static final HashMap<String, int[]> mProfileHlgMap = new HashMap<>();
    static final HashMap<String, int[]> mProfileHdr10Map = new HashMap<>();
    static final HashMap<String, int[]> mProfileHdr10PlusMap = new HashMap<>();
    static final HashMap<String, int[]> mProfileHdrMap = new HashMap<>();
    static final boolean ENABLE_LOGS = false;
    static final int PER_TEST_TIMEOUT_LARGE_TEST_MS = 300000;
    static final int PER_TEST_TIMEOUT_SMALL_TEST_MS = 60000;
    static final int UNSPECIFIED = 0;
    // Maintain Timeouts in sync with their counterpart in NativeMediaCommon.h
    static final long Q_DEQ_TIMEOUT_US = 5000; // block at most 5ms while looking for io buffers
    static final int RETRY_LIMIT = 100; // max poll counter before test aborts and returns error
    static final String INVALID_CODEC = "unknown.codec_";
    static final int[] MPEG2_PROFILES = new int[]{MPEG2ProfileSimple, MPEG2ProfileMain,
            MPEG2Profile422, MPEG2ProfileSNR, MPEG2ProfileSpatial, MPEG2ProfileHigh};
    static final int[] MPEG4_PROFILES = new int[]{MPEG4ProfileSimple, MPEG4ProfileSimpleScalable,
            MPEG4ProfileCore, MPEG4ProfileMain, MPEG4ProfileNbit, MPEG4ProfileScalableTexture,
            MPEG4ProfileSimpleFace, MPEG4ProfileSimpleFBA, MPEG4ProfileBasicAnimated,
            MPEG4ProfileHybrid, MPEG4ProfileAdvancedRealTime, MPEG4ProfileCoreScalable,
            MPEG4ProfileAdvancedCoding, MPEG4ProfileAdvancedCore, MPEG4ProfileAdvancedScalable,
            MPEG4ProfileAdvancedSimple};
    static final int[] H263_PROFILES = new int[]{H263ProfileBaseline, H263ProfileH320Coding,
            H263ProfileBackwardCompatible, H263ProfileISWV2, H263ProfileISWV3,
            H263ProfileHighCompression, H263ProfileInternet, H263ProfileInterlace,
            H263ProfileHighLatency};
    static final int[] VP8_PROFILES = new int[]{VP8ProfileMain};
    static final int[] AVC_SDR_PROFILES = new int[]{AVCProfileBaseline, AVCProfileMain,
            AVCProfileExtended, AVCProfileHigh, AVCProfileConstrainedBaseline,
            AVCProfileConstrainedHigh};
    static final int[] AVC_HLG_PROFILES = new int[]{AVCProfileHigh10};
    static final int[] AVC_HDR_PROFILES = AVC_HLG_PROFILES;
    static final int[] AVC_PROFILES = combine(AVC_SDR_PROFILES, AVC_HDR_PROFILES);
    static final int[] VP9_SDR_PROFILES = new int[]{VP9Profile0};
    static final int[] VP9_HLG_PROFILES = new int[]{VP9Profile2};
    static final int[] VP9_HDR10_PROFILES = new int[]{VP9Profile2HDR};
    static final int[] VP9_HDR10Plus_PROFILES = new int[]{VP9Profile2HDR10Plus};
    static final int[] VP9_HDR_PROFILES =
            combine(VP9_HLG_PROFILES, combine(VP9_HDR10_PROFILES, VP9_HDR10Plus_PROFILES));
    static final int[] VP9_PROFILES = combine(VP9_SDR_PROFILES, VP9_HDR_PROFILES);
    static final int[] HEVC_SDR_PROFILES = new int[]{HEVCProfileMain, HEVCProfileMainStill};
    static final int[] HEVC_HLG_PROFILES = new int[]{HEVCProfileMain10};
    static final int[] HEVC_HDR10_PROFILES = new int[]{HEVCProfileMain10HDR10};
    static final int[] HEVC_HDR10Plus_PROFILES = new int[]{HEVCProfileMain10HDR10Plus};
    static final int[] HEVC_HDR_PROFILES =
            combine(HEVC_HLG_PROFILES, combine(HEVC_HDR10_PROFILES, HEVC_HDR10Plus_PROFILES));
    static final int[] HEVC_PROFILES = combine(HEVC_SDR_PROFILES, HEVC_HDR_PROFILES);
    static final int[] AV1_SDR_PROFILES = new int[]{AV1ProfileMain8};
    static final int[] AV1_HLG_PROFILES = new int[]{AV1ProfileMain10};
    static final int[] AV1_HDR10_PROFILES = new int[]{AV1ProfileMain10HDR10};
    static final int[] AV1_HDR10Plus_PROFILES = new int[]{AV1ProfileMain10HDR10Plus};
    static final int[] AV1_HDR_PROFILES =
            combine(AV1_HLG_PROFILES, combine(AV1_HDR10_PROFILES, AV1_HDR10Plus_PROFILES));
    static final int[] AV1_PROFILES = combine(AV1_SDR_PROFILES, AV1_HDR_PROFILES);
    static final int[] AAC_PROFILES = new int[]{AACObjectMain, AACObjectLC, AACObjectSSR,
            AACObjectLTP, AACObjectHE, AACObjectScalable, AACObjectERLC, AACObjectERScalable,
            AACObjectLD, AACObjectELD, AACObjectXHE};
    static final String mInpPrefix = WorkDir.getMediaDirString();
    static final Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    static String mimeSelKeys;
    static String codecPrefix;
    static String mediaTypePrefix;

    enum SupportClass {
        CODEC_ALL, // All codecs must support
        CODEC_ANY, // At least one codec must support
        CODEC_DEFAULT, // Default codec must support
        CODEC_OPTIONAL; // Codec support is optional

        public static String toString(SupportClass supportRequirements) {
            switch (supportRequirements) {
                case CODEC_ALL:
                    return "CODEC_ALL";
                case CODEC_ANY:
                    return "CODEC_ANY";
                case CODEC_DEFAULT:
                    return "CODEC_DEFAULT";
                case CODEC_OPTIONAL:
                    return "CODEC_OPTIONAL";
                default:
                    return "Unknown support class";
            }
        }
    }

    CodecAsyncHandler mAsyncHandle;
    boolean mIsCodecInAsyncMode;
    boolean mSawInputEOS;
    boolean mSawOutputEOS;
    boolean mSignalEOSWithLastFrame;
    int mInputCount;
    int mOutputCount;
    long mPrevOutputPts;
    boolean mSignalledOutFormatChanged;
    MediaFormat mOutFormat;
    boolean mIsAudio;
    boolean mIsVideo;

    String mAllTestParams;  // logging
    String mTestConfig;
    String mTestEnv;

    boolean mSaveToMem;
    OutputManager mOutputBuff;

    String mCodecName;
    MediaCodec mCodec;
    Surface mSurface;

    static {
        System.loadLibrary("ctsmediav2codec_jni");

        codecSelKeyMimeMap.put("vp8", MediaFormat.MIMETYPE_VIDEO_VP8);
        codecSelKeyMimeMap.put("vp9", MediaFormat.MIMETYPE_VIDEO_VP9);
        codecSelKeyMimeMap.put("av1", MediaFormat.MIMETYPE_VIDEO_AV1);
        codecSelKeyMimeMap.put("avc", MediaFormat.MIMETYPE_VIDEO_AVC);
        codecSelKeyMimeMap.put("hevc", MediaFormat.MIMETYPE_VIDEO_HEVC);
        codecSelKeyMimeMap.put("mpeg4", MediaFormat.MIMETYPE_VIDEO_MPEG4);
        codecSelKeyMimeMap.put("h263", MediaFormat.MIMETYPE_VIDEO_H263);
        codecSelKeyMimeMap.put("mpeg2", MediaFormat.MIMETYPE_VIDEO_MPEG2);
        codecSelKeyMimeMap.put("vraw", MediaFormat.MIMETYPE_VIDEO_RAW);
        codecSelKeyMimeMap.put("amrnb", MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        codecSelKeyMimeMap.put("amrwb", MediaFormat.MIMETYPE_AUDIO_AMR_WB);
        codecSelKeyMimeMap.put("mp3", MediaFormat.MIMETYPE_AUDIO_MPEG);
        codecSelKeyMimeMap.put("aac", MediaFormat.MIMETYPE_AUDIO_AAC);
        codecSelKeyMimeMap.put("vorbis", MediaFormat.MIMETYPE_AUDIO_VORBIS);
        codecSelKeyMimeMap.put("opus", MediaFormat.MIMETYPE_AUDIO_OPUS);
        codecSelKeyMimeMap.put("g711alaw", MediaFormat.MIMETYPE_AUDIO_G711_ALAW);
        codecSelKeyMimeMap.put("g711mlaw", MediaFormat.MIMETYPE_AUDIO_G711_MLAW);
        codecSelKeyMimeMap.put("araw", MediaFormat.MIMETYPE_AUDIO_RAW);
        codecSelKeyMimeMap.put("flac", MediaFormat.MIMETYPE_AUDIO_FLAC);
        codecSelKeyMimeMap.put("gsm", MediaFormat.MIMETYPE_AUDIO_MSGSM);

        android.os.Bundle args = InstrumentationRegistry.getArguments();
        mimeSelKeys = args.getString(MIME_SEL_KEY);
        codecPrefix = args.getString(CODEC_PREFIX_KEY);
        mediaTypePrefix = args.getString(MEDIA_TYPE_PREFIX_KEY);

        mProfileSdrMap.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_SDR_PROFILES);
        mProfileSdrMap.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_SDR_PROFILES);
        mProfileSdrMap.put(MediaFormat.MIMETYPE_VIDEO_H263, H263_PROFILES);
        mProfileSdrMap.put(MediaFormat.MIMETYPE_VIDEO_MPEG2, MPEG2_PROFILES);
        mProfileSdrMap.put(MediaFormat.MIMETYPE_VIDEO_MPEG4, MPEG4_PROFILES);
        mProfileSdrMap.put(MediaFormat.MIMETYPE_VIDEO_VP8, VP8_PROFILES);
        mProfileSdrMap.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_SDR_PROFILES);
        mProfileSdrMap.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_SDR_PROFILES);
        mProfileSdrMap.put(MediaFormat.MIMETYPE_AUDIO_AAC, AAC_PROFILES);

        mProfileHlgMap.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_HLG_PROFILES);
        mProfileHlgMap.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_HLG_PROFILES);
        mProfileHlgMap.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HLG_PROFILES);
        mProfileHlgMap.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HLG_PROFILES);

        mProfileHdr10Map.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_HDR10_PROFILES);
        mProfileHdr10Map.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR10_PROFILES);
        mProfileHdr10Map.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HDR10_PROFILES);

        mProfileHdr10PlusMap.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_HDR10Plus_PROFILES);
        mProfileHdr10PlusMap.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR10Plus_PROFILES);
        mProfileHdr10PlusMap.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HDR10Plus_PROFILES);

        mProfileHdrMap.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_HDR_PROFILES);
        mProfileHdrMap.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_HDR_PROFILES);
        mProfileHdrMap.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR_PROFILES);
        mProfileHdrMap.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HDR_PROFILES);

        mProfileMap.put(MediaFormat.MIMETYPE_VIDEO_AVC, AVC_PROFILES);
        mProfileMap.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_PROFILES);
        mProfileMap.put(MediaFormat.MIMETYPE_VIDEO_H263, H263_PROFILES);
        mProfileMap.put(MediaFormat.MIMETYPE_VIDEO_MPEG2, MPEG2_PROFILES);
        mProfileMap.put(MediaFormat.MIMETYPE_VIDEO_MPEG4, MPEG4_PROFILES);
        mProfileMap.put(MediaFormat.MIMETYPE_VIDEO_VP8, VP8_PROFILES);
        mProfileMap.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_PROFILES);
        mProfileMap.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_PROFILES);
        mProfileMap.put(MediaFormat.MIMETYPE_AUDIO_AAC, AAC_PROFILES);

        HDR_INFO_IN_BITSTREAM_CODECS.add(MediaFormat.MIMETYPE_VIDEO_AV1);
        HDR_INFO_IN_BITSTREAM_CODECS.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        HDR_INFO_IN_BITSTREAM_CODECS.add(MediaFormat.MIMETYPE_VIDEO_HEVC);

        HDR_DYNAMIC_INFO.put(0, "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
                "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
                "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
                "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00");
        HDR_DYNAMIC_INFO.put(4, "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
                "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
                "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
                "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00");
        HDR_DYNAMIC_INFO.put(12, "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
                "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
                "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
                "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00");
        HDR_DYNAMIC_INFO.put(22, "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
                "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
                "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
                "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00");

        HDR_DYNAMIC_INCORRECT_INFO.put(0, "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
                "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
                "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
                "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00");
        HDR_DYNAMIC_INCORRECT_INFO.put(4, "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
                "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
                "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
                "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 01");
        HDR_DYNAMIC_INCORRECT_INFO.put(12, "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
                "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
                "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
                "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 02");
        HDR_DYNAMIC_INCORRECT_INFO.put(22, "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
                "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
                "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
                "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 03");
    }

    @Rule
    public final TestName mTestName = new TestName();

    @Before
    public void setUpCodecTestBase() {
        mTestConfig = "###################        Test Details         #####################\n";
        mTestConfig += "Test Name :- " + mTestName.getMethodName() + "\n";
        mTestConfig += "Test Parameters :- " + mAllTestParams + "\n";
        if (mCodecName != null && mCodecName.startsWith(INVALID_CODEC)) {
            fail("no valid component available for current test \n" + mTestConfig);
        }
    }

    static int[] combine(int[] first, int[] second) {
        int[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    static boolean isCodecLossless(String mime) {
        return mime.equals(MediaFormat.MIMETYPE_AUDIO_FLAC) ||
                mime.equals(MediaFormat.MIMETYPE_AUDIO_RAW);
    }

    static boolean hasDecoder(String mime) {
        return CodecTestBase.selectCodecs(mime, null, null, false).size() != 0;
    }

    static boolean hasEncoder(String mime) {
        return CodecTestBase.selectCodecs(mime, null, null, true).size() != 0;
    }

    static void checkFormatSupport(String codecName, String mime, boolean isEncoder,
            ArrayList<MediaFormat> formats, String[] features, SupportClass supportRequirements)
            throws IOException {
        if (!areFormatsSupported(codecName, mime, formats)) {
            switch (supportRequirements) {
                case CODEC_ALL:
                    fail("format(s) not supported by codec: " + codecName + " for mime : " + mime
                            + " formats: " + formats);
                    break;
                case CODEC_ANY:
                    if (selectCodecs(mime, formats, features, isEncoder).isEmpty()) {
                        fail("format(s) not supported by any component for mime : " + mime
                                + " formats: " + formats);
                    }
                    break;
                case CODEC_DEFAULT:
                    if (isDefaultCodec(codecName, mime, isEncoder)) {
                        fail("format(s) not supported by default codec : " + codecName
                                + "for mime : " + mime + " formats: " + formats);
                    }
                    break;
                case CODEC_OPTIONAL:
                default:
                    // the later assumeTrue() ensures we skip the test for unsupported codecs
                    break;
            }
            Assume.assumeTrue("format(s) not supported by codec: " + codecName + " for mime : " +
                    mime, false);
        }
    }

    static boolean isFeatureSupported(String name, String mime, String feature) throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(name);
        MediaCodecInfo.CodecCapabilities codecCapabilities =
                codec.getCodecInfo().getCapabilitiesForType(mime);
        boolean isSupported = codecCapabilities.isFeatureSupported(feature);
        codec.release();
        return isSupported;
    }

    static boolean isHDREditingSupported() {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
            if (!codecInfo.isEncoder()) {
                continue;
            }
            for (String mediaType : codecInfo.getSupportedTypes()) {
                CodecCapabilities caps = codecInfo.getCapabilitiesForType(mediaType);
                if (caps != null && caps.isFeatureSupported(FEATURE_HdrEditing)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean doesAnyFormatHaveHDRProfile(String mime, ArrayList<MediaFormat> formats) {
        int[] profileArray = mProfileHdrMap.get(mime);
        if (profileArray != null) {
            for (MediaFormat format : formats) {
                assertEquals(mime, format.getString(MediaFormat.KEY_MIME));
                int profile = format.getInteger(MediaFormat.KEY_PROFILE, -1);
                if (IntStream.of(profileArray).anyMatch(x -> x == profile)) return true;
            }
        }
        return false;
    }

    static boolean doesCodecSupportHDRProfile(String codecName, String mediaType) {
        int[] hdrProfiles = mProfileHdrMap.get(mediaType);
        if (hdrProfiles == null) {
            return false;
        }
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
            if (!codecName.equals(codecInfo.getName())) {
                continue;
            }
            CodecCapabilities caps = codecInfo.getCapabilitiesForType(mediaType);
            if (caps == null) {
                return false;
            }
            for (CodecProfileLevel pl : caps.profileLevels) {
                if (IntStream.of(hdrProfiles).anyMatch(x -> x == pl.profile)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean canDisplaySupportHDRContent() {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY).getHdrCapabilities()
                .getSupportedHdrTypes().length != 0;
    }

    static boolean areFormatsSupported(String name, String mime, ArrayList<MediaFormat> formats)
            throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(name);
        MediaCodecInfo.CodecCapabilities codecCapabilities =
                codec.getCodecInfo().getCapabilitiesForType(mime);
        boolean isSupported = true;
        if (formats != null) {
            for (int i = 0; i < formats.size() && isSupported; i++) {
                isSupported = codecCapabilities.isFormatSupported(formats.get(i));
            }
        }
        codec.release();
        return isSupported;
    }

    static boolean hasSupportForColorFormat(String name, String mime, int colorFormat)
            throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(name);
        MediaCodecInfo.CodecCapabilities cap =
                codec.getCodecInfo().getCapabilitiesForType(mime);
        boolean hasSupport = false;
        for (int c : cap.colorFormats) {
            if (c == colorFormat) {
                hasSupport = true;
                break;
            }
        }
        codec.release();
        return hasSupport;
    }

    static boolean isDefaultCodec(String codecName, String mime, boolean isEncoder)
            throws IOException {
        Map<String, String> mDefaultCodecs = isEncoder ? mDefaultEncoders : mDefaultDecoders;
        if (mDefaultCodecs.containsKey(mime)) {
            return mDefaultCodecs.get(mime).equalsIgnoreCase(codecName);
        }
        MediaCodec codec = isEncoder ? MediaCodec.createEncoderByType(mime)
                : MediaCodec.createDecoderByType(mime);
        boolean isDefault = codec.getName().equalsIgnoreCase(codecName);
        mDefaultCodecs.put(mime, codec.getName());
        codec.release();
        return isDefault;
    }

    static boolean isVendorCodec(String codecName) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
            if (codecName.equals(codecInfo.getName())) {
                return codecInfo.isVendor();
            }
        }
        return false;
    }

    static String paramToString(Object[] param) {
        StringBuilder paramStr = new StringBuilder("[  ");
        for (int j = 0; j < param.length - 1; j++) {
            Object o = param[j];
            if (o == null) {
                paramStr.append("null, ");
            } else if (o instanceof String[]) {
                int length = Math.min(((String[]) o).length, 3);
                paramStr.append("{");
                for (int i = 0; i < length; i++) {
                    paramStr.append(((String[]) o)[i]).append(", ");
                }
                paramStr.delete(paramStr.length() - 2, paramStr.length())
                        .append(length == ((String[]) o).length ? "}, " : ", ... }, ");
            } else if (o instanceof int[]) {
                paramStr.append("{");
                for (int i = 0; i < ((int[]) o).length; i++) {
                    paramStr.append(((int[]) o)[i]).append(", ");
                }
                paramStr.delete(paramStr.length() - 2, paramStr.length()).append("}, ");
            } else if (o instanceof Map) {
                int length = 0;
                paramStr.append("{ ");
                Map map = (Map) o;
                for (Object key : map.keySet()) {
                    paramStr.append(key).append(" = ").append(map.get(key)).append(", ");
                    length++;
                    if (length > 1) break;
                }
                paramStr.delete(paramStr.length() - 2, paramStr.length())
                        .append(length == map.size() ? "}, " : ", ... }, ");
            } else paramStr.append(o).append(", ");
        }
        paramStr.delete(paramStr.length() - 2, paramStr.length()).append("  ]");
        return paramStr.toString();
    }

    static ArrayList<String> compileRequiredMimeList(boolean isEncoder, boolean needAudio,
            boolean needVideo) {
        Set<String> list = new HashSet<>();
        if (!isEncoder) {
            if (MediaUtils.hasAudioOutput() && needAudio) {
                // sec 5.1.2
                list.add(MediaFormat.MIMETYPE_AUDIO_AAC);
                list.add(MediaFormat.MIMETYPE_AUDIO_FLAC);
                list.add(MediaFormat.MIMETYPE_AUDIO_MPEG);
                list.add(MediaFormat.MIMETYPE_AUDIO_VORBIS);
                list.add(MediaFormat.MIMETYPE_AUDIO_RAW);
                list.add(MediaFormat.MIMETYPE_AUDIO_OPUS);
            }
            if (MediaUtils.isHandheld() || MediaUtils.isTablet() || MediaUtils.isTv() ||
                    MediaUtils.isAutomotive()) {
                // sec 2.2.2, 2.3.2, 2.5.2
                if (needAudio) {
                    list.add(MediaFormat.MIMETYPE_AUDIO_AAC);
                }
                if (needVideo) {
                    list.add(MediaFormat.MIMETYPE_VIDEO_AVC);
                    list.add(MediaFormat.MIMETYPE_VIDEO_MPEG4);
                    list.add(MediaFormat.MIMETYPE_VIDEO_H263);
                    list.add(MediaFormat.MIMETYPE_VIDEO_VP8);
                    list.add(MediaFormat.MIMETYPE_VIDEO_VP9);
                }
            }
            if (MediaUtils.isHandheld() || MediaUtils.isTablet()) {
                // sec 2.2.2
                if (needAudio) {
                    list.add(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
                    list.add(MediaFormat.MIMETYPE_AUDIO_AMR_WB);
                }
                if (needVideo) {
                    list.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
                    if (IS_AT_LEAST_U) {
                        list.add(MediaFormat.MIMETYPE_VIDEO_AV1);
                    }
                }
            }
            if (MediaUtils.isTv() && needVideo) {
                // sec 2.3.2
                list.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
                list.add(MediaFormat.MIMETYPE_VIDEO_MPEG2);
            }
        } else {
            if (MediaUtils.hasMicrophone() && needAudio) {
                // sec 5.1.1
                // TODO(b/154423550)
                // list.add(MediaFormat.MIMETYPE_AUDIO_RAW);
                list.add(MediaFormat.MIMETYPE_AUDIO_FLAC);
                list.add(MediaFormat.MIMETYPE_AUDIO_OPUS);
            }
            if (MediaUtils.isHandheld() || MediaUtils.isTablet() || MediaUtils.isTv() ||
                    MediaUtils.isAutomotive()) {
                // sec 2.2.2, 2.3.2, 2.5.2
                if (needAudio) {
                    list.add(MediaFormat.MIMETYPE_AUDIO_AAC);
                }
                if (needVideo) {
                    list.add(MediaFormat.MIMETYPE_VIDEO_AVC);
                    list.add(MediaFormat.MIMETYPE_VIDEO_VP8);
                }
            }
            if ((MediaUtils.isHandheld() || MediaUtils.isTablet()) && needAudio) {
                // sec 2.2.2
                list.add(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
                list.add(MediaFormat.MIMETYPE_AUDIO_AMR_WB);
            }
        }
        return new ArrayList<>(list);
    }

    static ArrayList<String> compileCompleteTestMimeList(boolean isEncoder, boolean needAudio,
            boolean needVideo) {
        ArrayList<String> mimes = new ArrayList<>();
        if (mimeSelKeys == null) {
            ArrayList<String> cddRequiredMimeList =
                    compileRequiredMimeList(isEncoder, needAudio, needVideo);
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
            for (MediaCodecInfo codecInfo : codecInfos) {
                if (codecInfo.isEncoder() != isEncoder) continue;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) continue;
                String[] types = codecInfo.getSupportedTypes();
                for (String type : types) {
                    if (mediaTypePrefix != null && !type.startsWith(mediaTypePrefix)) {
                        continue;
                    }
                    if (!needAudio && type.startsWith("audio/")) continue;
                    if (!needVideo && type.startsWith("video/")) continue;
                    if (!mimes.contains(type)) {
                        mimes.add(type);
                    }
                }
            }
            if (mediaTypePrefix != null) {
                return mimes;
            }
            // feature_video_output is not exposed to package manager. Testing for video output
            // ports, such as VGA, HDMI, DisplayPort, or a wireless port for display is also not
            // direct.
            /* sec 5.2: device implementations include an embedded screen display with the
            diagonal length of at least 2.5 inches or include a video output port or declare the
            support of a camera */
            if (isEncoder && needVideo &&
                    (MediaUtils.hasCamera() || MediaUtils.getScreenSizeInInches() >= 2.5) &&
                    !mimes.contains(MediaFormat.MIMETYPE_VIDEO_AVC) &&
                    !mimes.contains(MediaFormat.MIMETYPE_VIDEO_VP8)) {
                // Add required cdd mimes here so that respective codec tests fail.
                mimes.add(MediaFormat.MIMETYPE_VIDEO_AVC);
                mimes.add(MediaFormat.MIMETYPE_VIDEO_VP8);
                Log.e(LOG_TAG, "device must support at least one of VP8 or AVC video encoders");
            }
            for (String mime : cddRequiredMimeList) {
                if (!mimes.contains(mime)) {
                    // Add required cdd mimes here so that respective codec tests fail.
                    mimes.add(mime);
                    Log.e(LOG_TAG, "no codec found for mime " + mime + " as required by cdd");
                }
            }
        } else {
            for (Map.Entry<String, String> entry : codecSelKeyMimeMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (mimeSelKeys.contains(key) && !mimes.contains(value)) mimes.add(value);
            }
        }
        return mimes;
    }

    static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList, boolean isEncoder,
            boolean needAudio, boolean needVideo, boolean mustTestAllCodecs) {
        ArrayList<String> mimes = compileCompleteTestMimeList(isEncoder, needAudio, needVideo);
        ArrayList<String> cddRequiredMimeList =
                compileRequiredMimeList(isEncoder, needAudio, needVideo);
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (String mime : mimes) {
            ArrayList<String> totalListOfCodecs = selectCodecs(mime, null, null, isEncoder);
            ArrayList<String> listOfCodecs = new ArrayList<>();
            if (codecPrefix != null) {
                for (String codec : totalListOfCodecs) {
                    if (codec.startsWith(codecPrefix)) {
                        listOfCodecs.add(codec);
                    }
                }
            } else {
                listOfCodecs = totalListOfCodecs;
            }
            if (mustTestAllCodecs && listOfCodecs.size() == 0 && codecPrefix == null) {
                listOfCodecs.add(INVALID_CODEC + mime);
            }
            boolean miss = true;
            for (Object[] arg : exhaustiveArgsList) {
                if (mime.equals(arg[0])) {
                    for (String codec : listOfCodecs) {
                        Object[] argUpdate = new Object[argLength + 2];
                        argUpdate[0] = codec;
                        System.arraycopy(arg, 0, argUpdate, 1, argLength);
                        argUpdate[argLength + 1] = paramToString(argUpdate);
                        argsList.add(argUpdate);
                    }
                    miss = false;
                }
            }
            if (miss && mustTestAllCodecs) {
                if (!cddRequiredMimeList.contains(mime)) {
                    Log.w(LOG_TAG, "no test vectors available for optional mime type " + mime);
                    continue;
                }
                for (String codec : listOfCodecs) {
                    Object[] argUpdate = new Object[argLength + 2];
                    argUpdate[0] = codec;
                    argUpdate[1] = mime;
                    System.arraycopy(exhaustiveArgsList.get(0), 1, argUpdate, 2, argLength - 1);
                    argUpdate[argLength + 1] = paramToString(argUpdate);
                    argsList.add(argUpdate);
                }
            }
        }
        return argsList;
    }

    abstract void enqueueInput(int bufferIndex) throws IOException;

    abstract void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info);

    void configureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder) {
        resetContext(isAsync, signalEOSWithLastFrame);
        mAsyncHandle.setCallBack(mCodec, isAsync);
        // signalEOS flag has nothing to do with configure. We are using this flag to try all
        // available configure apis
        if (signalEOSWithLastFrame) {
            mCodec.configure(format, mSurface, null,
                    isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);
        } else {
            mCodec.configure(format, mSurface, isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0,
                    null);
        }
        mTestEnv = "###################      Test Environment       #####################\n";
        mTestEnv += String.format("Component under test :- %s \n", mCodecName);
        mTestEnv += "Format under test :- " + format + "\n";
        mTestEnv += String.format("Component operating in :- %s mode \n",
                (isAsync ? "asynchronous" : "synchronous"));
        mTestEnv += String.format("Component received input eos :- %s \n",
                (signalEOSWithLastFrame ? "with full buffer" : "with empty buffer"));
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    void flushCodec() {
        mCodec.flush();
        // TODO(b/147576107): is it ok to clearQueues right away or wait for some signal
        mAsyncHandle.clearQueues();
        mSawInputEOS = false;
        mSawOutputEOS = false;
        mInputCount = 0;
        mOutputCount = 0;
        mPrevOutputPts = Long.MIN_VALUE;
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec flushed");
        }
    }

    void reConfigureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder) {
        /* TODO(b/147348711) */
        if (false) mCodec.stop();
        else mCodec.reset();
        configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder);
    }

    void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mAsyncHandle.resetContext();
        mIsCodecInAsyncMode = isAsync;
        mSawInputEOS = false;
        mSawOutputEOS = false;
        mSignalEOSWithLastFrame = signalEOSWithLastFrame;
        mInputCount = 0;
        mOutputCount = 0;
        mPrevOutputPts = Long.MIN_VALUE;
        mSignalledOutFormatChanged = false;
    }

    void enqueueEOS(int bufferIndex) {
        if (!mSawInputEOS) {
            mCodec.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mSawInputEOS = true;
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "Queued End of Stream");
            }
        }
    }

    void doWork(int frameLimit) throws InterruptedException, IOException {
        int frameCount = 0;
        if (mIsCodecInAsyncMode) {
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS && frameCount < frameLimit) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        // <id, info> corresponds to output callback. Handle it accordingly
                        dequeueOutput(bufferID, info);
                    } else {
                        // <id, null> corresponds to input callback. Handle it accordingly
                        enqueueInput(bufferID);
                        frameCount++;
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mSawInputEOS && frameCount < frameLimit) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueInput(inputBufferId);
                    frameCount++;
                }
            }
        }
    }

    void queueEOS() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        dequeueOutput(bufferID, info);
                    } else {
                        enqueueEOS(element.first);
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawInputEOS) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueEOS(inputBufferId);
                }
            }
        }
    }

    void waitForAllOutputs() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandle.hasSeenError() && !mSawOutputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
                if (element != null) {
                    dequeueOutput(element.first, element.second);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawOutputEOS) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
            }
        }
        validateTestState();
    }

    void validateTestState() {
        assertFalse("Encountered error in async mode. \n" + mTestConfig + mTestEnv
                + mAsyncHandle.getErrMsg(), mAsyncHandle.hasSeenError());
        if (mInputCount > 0) {
            assertTrue(String.format("fed %d input frames, received no output frames \n",
                    mInputCount) + mTestConfig + mTestEnv, mOutputCount > 0);
        }
        /*if (mInputCount == 0 && mInputCount != mOutputCount) {
            String msg = String.format("The number of output frames received is not same as number "
                            + "of input frames queued. Output count is %d, Input count is %d \n",
                    mOutputCount, mInputCount);
            // check the pts lists to see what frames are dropped, the below call is needed to
            // get useful error messages
            boolean unused = mOutputBuff.isOutPtsListIdenticalToInpPtsList(true);
            fail(msg + mTestConfig + mTestEnv + mOutputBuff.getErrMsg());
        }*/
    }

    static ArrayList<String> selectCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        ArrayList<String> listOfCodecs = new ArrayList<>();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (codecInfo.isEncoder() != isEncoder) continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mime)) {
                    boolean isOk = true;
                    MediaCodecInfo.CodecCapabilities codecCapabilities =
                            codecInfo.getCapabilitiesForType(type);
                    if (formats != null) {
                        for (MediaFormat format : formats) {
                            if (!codecCapabilities.isFormatSupported(format)) {
                                isOk = false;
                                break;
                            }
                        }
                    }
                    if (features != null) {
                        for (String feature : features) {
                            if (!codecCapabilities.isFeatureSupported(feature)) {
                                isOk = false;
                                break;
                            }
                        }
                    }
                    if (isOk) listOfCodecs.add(codecInfo.getName());
                }
            }
        }
        return listOfCodecs;
    }

    static int getWidth(MediaFormat format) {
        int width = format.getInteger(MediaFormat.KEY_WIDTH, -1);
        if (format.containsKey("crop-left") && format.containsKey("crop-right")) {
            width = format.getInteger("crop-right") + 1 - format.getInteger("crop-left");
        }
        return width;
    }

    static int getHeight(MediaFormat format) {
        int height = format.getInteger(MediaFormat.KEY_HEIGHT, -1);
        if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) {
            height = format.getInteger("crop-bottom") + 1 - format.getInteger("crop-top");
        }
        return height;
    }

    byte[] loadByteArrayFromString(final String str) {
        if (str == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("[0-9a-fA-F]{2}");
        Matcher matcher = pattern.matcher(str);
        // allocate a large enough byte array first
        byte[] tempArray = new byte[str.length() / 2];
        int i = 0;
        while (matcher.find()) {
            tempArray[i++] = (byte) Integer.parseInt(matcher.group(), 16);
        }
        return Arrays.copyOfRange(tempArray, 0, i);
    }

    void insertHdrDynamicInfo(byte[] info) {
        final Bundle params = new Bundle();
        params.putByteArray(MediaFormat.KEY_HDR10_PLUS_INFO, info);
        mCodec.setParameters(params);
    }

    boolean isFormatSimilar(MediaFormat inpFormat, MediaFormat outFormat) {
        if (inpFormat == null || outFormat == null) return false;
        String inpMime = inpFormat.getString(MediaFormat.KEY_MIME);
        String outMime = outFormat.getString(MediaFormat.KEY_MIME);
        // not comparing input and output mimes because for a codec, mime is raw on one side and
        // encoded type on the other
        if (outMime.startsWith("audio/")) {
            return inpFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -1) ==
                    outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -2) &&
                    inpFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, -1) ==
                            outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, -2) &&
                    inpMime.startsWith("audio/");
        } else if (outMime.startsWith("video/")) {
            return getWidth(inpFormat) == getWidth(outFormat) &&
                    getHeight(inpFormat) == getHeight(outFormat) && inpMime.startsWith("video/");
        }
        return true;
    }

    PersistableBundle validateMetrics(String codec) {
        PersistableBundle metrics = mCodec.getMetrics();
        assertNotNull("error! MediaCodec.getMetrics() returns null \n" + mTestConfig + mTestEnv,
                metrics);
        assertEquals("error! metrics#MetricsConstants.CODEC is not as expected \n" + mTestConfig
                + mTestEnv, metrics.getString(MediaCodec.MetricsConstants.CODEC), codec);
        assertEquals("error! metrics#MetricsConstants.MODE is not as expected \n" + mTestConfig
                        + mTestEnv, mIsAudio ? MediaCodec.MetricsConstants.MODE_AUDIO :
                        MediaCodec.MetricsConstants.MODE_VIDEO,
                metrics.getString(MediaCodec.MetricsConstants.MODE));
        return metrics;
    }

    PersistableBundle validateMetrics(String codec, MediaFormat format) {
        PersistableBundle metrics = validateMetrics(codec);
        if (mIsVideo) {
            assertEquals("error! metrics#MetricsConstants.WIDTH is not as expected\n" + mTestConfig
                            + mTestEnv, metrics.getInt(MediaCodec.MetricsConstants.WIDTH),
                    getWidth(format));
            assertEquals("error! metrics#MetricsConstants.HEIGHT is not as expected\n" + mTestConfig
                            + mTestEnv, metrics.getInt(MediaCodec.MetricsConstants.HEIGHT),
                    getHeight(format));
        }
        assertEquals("error! metrics#MetricsConstants.SECURE is not as expected\n" + mTestConfig
                + mTestEnv, 0, metrics.getInt(MediaCodec.MetricsConstants.SECURE));
        return metrics;
    }

    void validateColorAspects(MediaFormat fmt, int range, int standard, int transfer) {
        int colorRange = fmt.getInteger(MediaFormat.KEY_COLOR_RANGE, UNSPECIFIED);
        int colorStandard = fmt.getInteger(MediaFormat.KEY_COLOR_STANDARD, UNSPECIFIED);
        int colorTransfer = fmt.getInteger(MediaFormat.KEY_COLOR_TRANSFER, UNSPECIFIED);
        if (range > UNSPECIFIED) {
            assertEquals("error! color range mismatch \n" + mTestConfig + mTestEnv, range,
                    colorRange);
        }
        if (standard > UNSPECIFIED) {
            assertEquals("error! color standard mismatch \n" + mTestConfig + mTestEnv, standard,
                    colorStandard);
        }
        if (transfer > UNSPECIFIED) {
            assertEquals("error! color transfer mismatch \n" + mTestConfig + mTestEnv, transfer,
                    colorTransfer);
        }
    }

    void validateHDRInfo(MediaFormat fmt, String hdrInfoKey, ByteBuffer hdrInfoRef) {
        ByteBuffer hdrInfo = fmt.getByteBuffer(hdrInfoKey, null);
        assertNotNull("error! no " + hdrInfoKey + " present in format : " + fmt + "\n "
                + mTestConfig + mTestEnv, hdrInfo);
        if (!hdrInfoRef.equals(hdrInfo)) {
            StringBuilder msg = new StringBuilder(
                    "###################       Error Details         #####################\n");
            byte[] ref = new byte[hdrInfoRef.capacity()];
            hdrInfoRef.get(ref);
            hdrInfoRef.rewind();
            byte[] test = new byte[hdrInfo.capacity()];
            hdrInfo.get(test);
            hdrInfo.rewind();
            msg.append("ref info :- \n");
            for (byte b : ref) {
                msg.append(String.format("%2x ", b));
            }
            msg.append("\ntest info :- \n");
            for (byte b : test) {
                msg.append(String.format("%2x ", b));
            }
            fail("error! mismatch seen between ref and test info of " + hdrInfoKey + "\n"
                    + mTestConfig + mTestEnv + msg);
        }
    }

    public void setUpSurface(CodecTestActivity activity) throws InterruptedException {
        activity.waitTillSurfaceIsCreated();
        mSurface = activity.getSurface();
        assertNotNull("Surface created is null \n" + mTestConfig + mTestEnv, mSurface);
        assertTrue("Surface created is invalid \n" + mTestConfig + mTestEnv, mSurface.isValid());
    }

    public void tearDownSurface() {
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    @After
    public void tearDown() {
        if (mCodec != null) {
            mCodec.release();
            mCodec = null;
        }
    }
}

class CodecDecoderTestBase extends CodecTestBase {
    private static final String LOG_TAG = CodecDecoderTestBase.class.getSimpleName();

    String mMime;
    String mTestFile;
    boolean mIsInterlaced;
    boolean mSkipChecksumVerification;

    ArrayList<ByteBuffer> mCsdBuffers;
    private int mCurrCsdIdx;

    private ByteBuffer flatBuffer = ByteBuffer.allocate(4 * Integer.BYTES);

    MediaExtractor mExtractor;
    CodecTestActivity mActivity;

    CodecDecoderTestBase(String codecName, String mime, String testFile, String allTestParams) {
        mCodecName = codecName;
        mMime = mime;
        mTestFile = testFile;
        mAsyncHandle = new CodecAsyncHandler();
        mCsdBuffers = new ArrayList<>();
        mIsAudio = mMime.startsWith("audio/");
        mIsVideo = mMime.startsWith("video/");
        mAllTestParams = allTestParams;
    }

    @Before
    public void setUpCodecDecoderTestBase() {
        assertTrue("Testing a mime that is neither audio nor video is not supported \n"
                + mTestConfig, mIsAudio || mIsVideo);
    }

    MediaFormat setUpSource(String srcFile) throws IOException {
        Preconditions.assertTestFileExists(srcFile);
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(srcFile);
        for (int trackID = 0; trackID < mExtractor.getTrackCount(); trackID++) {
            MediaFormat format = mExtractor.getTrackFormat(trackID);
            if (mMime.equalsIgnoreCase(format.getString(MediaFormat.KEY_MIME))) {
                mExtractor.selectTrack(trackID);
                if (mIsVideo) {
                    ArrayList<MediaFormat> formatList = new ArrayList<>();
                    formatList.add(format);
                    boolean selectHBD = doesAnyFormatHaveHDRProfile(mMime, formatList) ||
                            srcFile.contains("10bit");
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                            getColorFormat(mCodecName, mMime, mSurface != null, selectHBD));
                    if (selectHBD && (format.getInteger(MediaFormat.KEY_COLOR_FORMAT) !=
                            COLOR_FormatYUVP010)) {
                        mSkipChecksumVerification = true;
                    }
                }
                // TODO: determine this from the extractor format when it becomes exposed.
                mIsInterlaced = srcFile.contains("_interlaced_");
                return format;
            }
        }
        fail("No track with mime: " + mMime + " found in file: " + srcFile + "\n" + mTestConfig
                + mTestEnv);
        return null;
    }

    int getColorFormat(String name, String mediaType, boolean surfaceMode, boolean hbdMode)
            throws IOException {
        if (surfaceMode) return COLOR_FormatSurface;
        if (hbdMode) {
            MediaCodec codec = MediaCodec.createByCodecName(name);
            MediaCodecInfo.CodecCapabilities cap =
                    codec.getCodecInfo().getCapabilitiesForType(mediaType);
            codec.release();
            for (int c : cap.colorFormats) {
                if (c == COLOR_FormatYUVP010) {
                    return c;
                }
            }
        }
        return COLOR_FormatYUV420Flexible;
    }

    boolean hasCSD(MediaFormat format) {
        return format.containsKey("csd-0");
    }

    void flattenBufferInfo(MediaCodec.BufferInfo info, boolean isAudio) {
        if (isAudio) {
            flatBuffer.putInt(info.size);
        }
        flatBuffer.putInt(info.flags & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                .putLong(info.presentationTimeUs);
        flatBuffer.flip();
    }

    void enqueueCodecConfig(int bufferIndex) {
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        ByteBuffer csdBuffer = mCsdBuffers.get(mCurrCsdIdx);
        inputBuffer.put((ByteBuffer) csdBuffer.rewind());
        mCodec.queueInputBuffer(bufferIndex, 0, csdBuffer.limit(), 0,
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "queued csd: id: " + bufferIndex + " size: " + csdBuffer.limit());
        }
    }

    void enqueueInput(int bufferIndex) {
        if (mExtractor.getSampleSize() < 0) {
            enqueueEOS(bufferIndex);
        } else {
            ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
            mExtractor.readSampleData(inputBuffer, 0);
            int size = (int) mExtractor.getSampleSize();
            long pts = mExtractor.getSampleTime();
            int extractorFlags = mExtractor.getSampleFlags();
            int codecFlags = 0;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
            }
            if (!mExtractor.advance() && mSignalEOSWithLastFrame) {
                codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawInputEOS = true;
            }
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts +
                        " flags: " + codecFlags);
            }
            mCodec.queueInputBuffer(bufferIndex, 0, size, pts, codecFlags);
            if (size > 0 && (codecFlags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG |
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)) == 0) {
                mOutputBuff.saveInPTS(pts);
                mInputCount++;
            }
        }
    }

    void enqueueInput(int bufferIndex, ByteBuffer buffer, MediaCodec.BufferInfo info) {
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        buffer.position(info.offset);
        for (int i = 0; i < info.size; i++) {
            inputBuffer.put(buffer.get());
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "input: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        mCodec.queueInputBuffer(bufferIndex, 0, info.size, info.presentationTimeUs,
                info.flags);
        if (info.size > 0 && ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) &&
                ((info.flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) == 0)) {
            mOutputBuff.saveInPTS(info.presentationTimeUs);
            mInputCount++;
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawInputEOS = true;
        }
    }

    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && mSaveToMem) {
            ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
            flattenBufferInfo(info, mIsAudio);
            mOutputBuff.checksum(flatBuffer, flatBuffer.limit());
            if (mIsAudio) {
                mOutputBuff.checksum(buf, info.size);
                mOutputBuff.saveToMemory(buf, info);
            } else {
                // tests both getOutputImage and getOutputBuffer. Can do time division
                // multiplexing but lets allow it for now
                Image img = mCodec.getOutputImage(bufferIndex);
                assertNotNull("CPU-read via ImageReader API is not available", img);
                mOutputBuff.checksum(img);
                int imgFormat = img.getFormat();
                int bytesPerSample = (ImageFormat.getBitsPerPixel(imgFormat) * 2) / (8 * 3);

                MediaFormat format = mCodec.getOutputFormat();
                buf = mCodec.getOutputBuffer(bufferIndex);
                int width = format.getInteger(MediaFormat.KEY_WIDTH);
                int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                int stride = format.getInteger(MediaFormat.KEY_STRIDE);
                mOutputBuff.checksum(buf, info.size, width, height, stride, bytesPerSample);
            }
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    void doWork(ByteBuffer buffer, ArrayList<MediaCodec.BufferInfo> list)
            throws InterruptedException {
        int frameCount = 0;
        if (mIsCodecInAsyncMode) {
            // output processing after queuing EOS is done in waitForAllOutputs()
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS && frameCount < list.size()) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        dequeueOutput(bufferID, info);
                    } else {
                        enqueueInput(bufferID, buffer, list.get(frameCount));
                        frameCount++;
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            // output processing after queuing EOS is done in waitForAllOutputs()
            while (!mSawInputEOS && frameCount < list.size()) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueInput(inputBufferId, buffer, list.get(frameCount));
                    frameCount++;
                }
            }
        }
    }

    void queueCodecConfig() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            for (mCurrCsdIdx = 0; !mAsyncHandle.hasSeenError() && mCurrCsdIdx < mCsdBuffers.size();
                 mCurrCsdIdx++) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getInput();
                if (element != null) {
                    enqueueCodecConfig(element.first);
                }
            }
        } else {
            for (mCurrCsdIdx = 0; mCurrCsdIdx < mCsdBuffers.size(); mCurrCsdIdx++) {
                enqueueCodecConfig(mCodec.dequeueInputBuffer(-1));
            }
        }
    }

    void validateTestState() {
        super.validateTestState();
        if (!mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts)) {
            fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                    + mOutputBuff.getErrMsg());
        }
        if (mIsVideo) {
            // TODO: Timestamps for deinterlaced content are under review. (E.g. can decoders
            // produce multiple progressive frames?) For now, do not verify timestamps.
            if (!mIsInterlaced && !mOutputBuff.isOutPtsListIdenticalToInpPtsList(false)) {
                fail("Input pts list and Output pts list are not identical ]\n" + mTestConfig
                        + mTestEnv + mOutputBuff.getErrMsg());
            }
        }
    }

    void decodeToMemory(String file, String decoder, long pts, int mode, int frameLimit)
            throws IOException, InterruptedException {
        mSaveToMem = true;
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(decoder);
        MediaFormat format = setUpSource(file);
        configureCodec(format, false, true, false);
        mCodec.start();
        mExtractor.seekTo(pts, mode);
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
        mSaveToMem = false;
    }

    @Override
    PersistableBundle validateMetrics(String decoder, MediaFormat format) {
        PersistableBundle metrics = super.validateMetrics(decoder, format);
        assertEquals("error! metrics#MetricsConstants.MIME_TYPE is not as expected \n" + mTestConfig
                + mTestEnv, metrics.getString(MediaCodec.MetricsConstants.MIME_TYPE), mMime);
        assertEquals("error! metrics#MetricsConstants.ENCODER is not as expected \n" + mTestConfig
                + mTestEnv, 0, metrics.getInt(MediaCodec.MetricsConstants.ENCODER));
        return metrics;
    }

    void validateColorAspects(int range, int standard, int transfer, boolean ignoreColorBox)
            throws IOException, InterruptedException {
        Preconditions.assertTestFileExists(mTestFile);
        mOutputBuff = new OutputManager();
        MediaFormat format = setUpSource(mTestFile);
        if (ignoreColorBox) {
            format.removeKey(MediaFormat.KEY_COLOR_RANGE);
            format.removeKey(MediaFormat.KEY_COLOR_STANDARD);
            format.removeKey(MediaFormat.KEY_COLOR_TRANSFER);
        }
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(format, true, true, false);
        mCodec.start();
        doWork(1);
        queueEOS();
        waitForAllOutputs();
        validateColorAspects(mCodec.getOutputFormat(), range, standard, transfer);
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
    }
}

class CodecEncoderTestBase extends CodecTestBase {
    private static final String LOG_TAG = CodecEncoderTestBase.class.getSimpleName();

    // files are in WorkDir.getMediaDirString();
    protected static final RawResource INPUT_VIDEO_FILE =
            new RawResource.Builder()
                    .setFileName(mInpPrefix + "bbb_cif_yuv420p_30fps.yuv", false)
                    .setDimension(352, 288)
                    .setBytesPerSample(1)
                    .setColorFormat(ImageFormat.YUV_420_888)
                    .build();
    protected static final RawResource INPUT_VIDEO_FILE_HBD =
            new RawResource.Builder()
                    .setFileName(mInpPrefix + "cosmat_cif_24fps_yuv420p16le.yuv", false)
                    .setDimension(352, 288)
                    .setBytesPerSample(2)
                    .setColorFormat(ImageFormat.YCBCR_P010)
                    .build();

    /* Note: The mSampleRate and mChannelCount fields of RawResource are not used by the tests
    the way mWidth and mHeight are used. mWidth and mHeight is used by fillImage() to select a
    portion of the frame or duplicate the frame as tiles depending on testWidth and testHeight.
    Ideally mSampleRate and mChannelCount information should be used to resample and perform
    channel-conversion basing on testSampleRate and testChannelCount. Instead the test considers
    the resource file to be of testSampleRate and testChannelCount. */
    protected static final RawResource INPUT_AUDIO_FILE =
            new RawResource.Builder()
                    .setFileName(mInpPrefix + "bbb_2ch_44kHz_s16le.raw", true)
                    .setSampleRate(44100)
                    .setChannelCount(2)
                    .setBytesPerSample(2)
                    .setAudioEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
    protected static final RawResource INPUT_AUDIO_FILE_HBD =
            new RawResource.Builder()
                    .setFileName(mInpPrefix + "audio/sd_2ch_48kHz_f32le.raw", true)
                    .setSampleRate(48000)
                    .setChannelCount(2)
                    .setBytesPerSample(4)
                    .setAudioEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build();

    final String mMime;
    final int[] mBitrates;
    final int[] mEncParamList1;
    final int[] mEncParamList2;

    RawResource mActiveRawRes;
    byte[] mInputData;
    int mNumBytesSubmitted;
    long mInputOffsetPts;

    ArrayList<MediaFormat> mFormats;
    ArrayList<MediaCodec.BufferInfo> mInfoList;

    int mWidth, mHeight;
    int mBytesPerSample;
    int mFrameRate;
    int mMaxBFrames;
    int mChannels;
    int mSampleRate;

    CodecEncoderTestBase(String encoder, String mime, int[] bitrates, int[] encoderInfo1,
            int[] encoderInfo2, String allTestParams) {
        mMime = mime;
        mCodecName = encoder;
        mBitrates = bitrates;
        mEncParamList1 = encoderInfo1;
        mEncParamList2 = encoderInfo2;
        mAllTestParams = allTestParams;
        mFormats = new ArrayList<>();
        mInfoList = new ArrayList<>();
        mWidth = 0;
        mHeight = 0;
        if (mime.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)) mFrameRate = 12;
        else if (mime.equals(MediaFormat.MIMETYPE_VIDEO_H263)) mFrameRate = 12;
        else mFrameRate = 30;
        mMaxBFrames = 0;
        mChannels = 0;
        mSampleRate = 0;
        mAsyncHandle = new CodecAsyncHandler();
        mIsAudio = mMime.startsWith("audio/");
        mIsVideo = mMime.startsWith("video/");
        mActiveRawRes = mIsAudio ? INPUT_AUDIO_FILE : INPUT_VIDEO_FILE;
        mBytesPerSample = mActiveRawRes.mBytesPerSample;
    }

    @Before
    public void setUpCodecEncoderTestBase() {
        assertTrue("Testing a mime that is neither audio nor video is not supported \n"
                + mTestConfig, mIsAudio || mIsVideo);
    }

    /**
     * Selects encoder input color format in byte buffer mode. As of now ndk tests support only
     * 420p, 420sp. COLOR_FormatYUV420Flexible although can represent any form of yuv, it doesn't
     * work in ndk due to lack of AMediaCodec_GetInputImage()
     */
    static int findByteBufferColorFormat(String encoder, String mime) throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(encoder);
        MediaCodecInfo.CodecCapabilities cap = codec.getCodecInfo().getCapabilitiesForType(mime);
        int colorFormat = -1;
        for (int c : cap.colorFormats) {
            if (c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ||
                    c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                Log.v(LOG_TAG, "selecting color format: " + c);
                colorFormat = c;
                break;
            }
        }
        codec.release();
        return colorFormat;
    }

    @Override
    void configureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder) {
        super.configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder);
        if (mIsAudio) {
            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } else {
            mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        }
    }

    @Override
    void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mNumBytesSubmitted = 0;
        mInputOffsetPts = 0;
    }

    @Override
    void flushCodec() {
        super.flushCodec();
        if (mIsAudio) {
            mInputOffsetPts = (mNumBytesSubmitted + 1024) * 1000000L /
                    ((long) mBytesPerSample * mChannels * mSampleRate);
        } else {
            mInputOffsetPts = (mInputCount + 5) * 1000000L / mFrameRate;
        }
        mPrevOutputPts = mInputOffsetPts - 1;
        mNumBytesSubmitted = 0;
    }

    void setUpSource(String inpPath) throws IOException {
        Preconditions.assertTestFileExists(inpPath);
        try (FileInputStream fInp = new FileInputStream(inpPath)) {
            int size = (int) new File(inpPath).length();
            mInputData = new byte[size];
            fInp.read(mInputData, 0, size);
        }
    }

    void fillImage(Image image) {
        int format = image.getFormat();
        assertTrue("unexpected image format \n" + mTestConfig + mTestEnv,
                format == ImageFormat.YUV_420_888 || format == ImageFormat.YCBCR_P010);
        int bytesPerSample = (ImageFormat.getBitsPerPixel(format) * 2) / (8 * 3);  // YUV420
        assertEquals("Invalid bytes per sample \n" + mTestConfig + mTestEnv, bytesPerSample,
                mBytesPerSample);

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        int offset = mNumBytesSubmitted;
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();
            int width = imageWidth;
            int height = imageHeight;
            int tileWidth = mActiveRawRes.mWidth;
            int tileHeight = mActiveRawRes.mHeight;
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (i != 0) {
                width = imageWidth / 2;
                height = imageHeight / 2;
                tileWidth = mActiveRawRes.mWidth / 2;
                tileHeight = mActiveRawRes.mHeight / 2;
            }
            if (pixelStride == bytesPerSample) {
                if (width == rowStride && width == tileWidth && height == tileHeight) {
                    buf.put(mInputData, offset, width * height * bytesPerSample);
                } else {
                    for (int z = 0; z < height; z += tileHeight) {
                        int rowsToCopy = Math.min(height - z, tileHeight);
                        for (int y = 0; y < rowsToCopy; y++) {
                            for (int x = 0; x < width; x += tileWidth) {
                                int colsToCopy = Math.min(width - x, tileWidth);
                                buf.position((z + y) * rowStride + x * bytesPerSample);
                                buf.put(mInputData, offset + y * tileWidth * bytesPerSample,
                                        colsToCopy * bytesPerSample);
                            }
                        }
                    }
                }
            } else {
                // do it pixel-by-pixel
                for (int z = 0; z < height; z += tileHeight) {
                    int rowsToCopy = Math.min(height - z, tileHeight);
                    for (int y = 0; y < rowsToCopy; y++) {
                        int lineOffset = (z + y) * rowStride;
                        for (int x = 0; x < width; x += tileWidth) {
                            int colsToCopy = Math.min(width - x, tileWidth);
                            for (int w = 0; w < colsToCopy; w++) {
                                for (int bytePos = 0; bytePos < bytesPerSample; bytePos++) {
                                    buf.position(lineOffset + (x + w) * pixelStride + bytePos);
                                    buf.put(mInputData[offset + y * tileWidth * bytesPerSample +
                                            w * bytesPerSample + bytePos]);
                                }
                            }
                        }
                    }
                }
            }
            offset += tileWidth * tileHeight * bytesPerSample;
        }
    }

    void fillByteBuffer(ByteBuffer inputBuffer) {
        int offset = 0, frmOffset = mNumBytesSubmitted;
        for (int plane = 0; plane < 3; plane++) {
            int width = mWidth;
            int height = mHeight;
            int tileWidth = mActiveRawRes.mWidth;
            int tileHeight = mActiveRawRes.mHeight;
            if (plane != 0) {
                width = mWidth / 2;
                height = mHeight / 2;
                tileWidth = mActiveRawRes.mWidth / 2;
                tileHeight = mActiveRawRes.mHeight / 2;
            }
            for (int k = 0; k < height; k += tileHeight) {
                int rowsToCopy = Math.min(height - k, tileHeight);
                for (int j = 0; j < rowsToCopy; j++) {
                    for (int i = 0; i < width; i += tileWidth) {
                        int colsToCopy = Math.min(width - i, tileWidth);
                        inputBuffer.position(
                                offset + (k + j) * width * mBytesPerSample + i * mBytesPerSample);
                        inputBuffer.put(mInputData, frmOffset + j * tileWidth * mBytesPerSample,
                                colsToCopy * mBytesPerSample);
                    }
                }
            }
            offset += width * height * mBytesPerSample;
            frmOffset += tileWidth * tileHeight * mBytesPerSample;
        }
    }

    void enqueueInput(int bufferIndex) {
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        if (mNumBytesSubmitted >= mInputData.length) {
            enqueueEOS(bufferIndex);
        } else {
            int size;
            int flags = 0;
            long pts = mInputOffsetPts;
            if (mIsAudio) {
                pts += mNumBytesSubmitted * 1000000L / ((long) mBytesPerSample * mChannels
                        * mSampleRate);
                size = Math.min(inputBuffer.capacity(), mInputData.length - mNumBytesSubmitted);
                assertEquals(0, size % ((long) mBytesPerSample * mChannels));
                inputBuffer.put(mInputData, mNumBytesSubmitted, size);
                if (mNumBytesSubmitted + size >= mInputData.length && mSignalEOSWithLastFrame) {
                    flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    mSawInputEOS = true;
                }
                mNumBytesSubmitted += size;
            } else {
                pts += mInputCount * 1000000L / mFrameRate;
                size = mBytesPerSample * mWidth * mHeight * 3 / 2;
                int frmSize = mActiveRawRes.mBytesPerSample * mActiveRawRes.mWidth
                                * mActiveRawRes.mHeight * 3 / 2;
                if (mNumBytesSubmitted + frmSize > mInputData.length) {
                    fail("received partial frame to encode \n" + mTestConfig + mTestEnv);
                } else {
                    Image img = mCodec.getInputImage(bufferIndex);
                    if (img != null) {
                        fillImage(img);
                    } else {
                        if (mWidth == mActiveRawRes.mWidth && mHeight == mActiveRawRes.mHeight) {
                            inputBuffer.put(mInputData, mNumBytesSubmitted, size);
                        } else {
                            fillByteBuffer(inputBuffer);
                        }
                    }
                }
                if (mNumBytesSubmitted + frmSize >= mInputData.length && mSignalEOSWithLastFrame) {
                    flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    mSawInputEOS = true;
                }
                mNumBytesSubmitted += frmSize;
            }
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts +
                        " flags: " + flags);
            }
            mCodec.queueInputBuffer(bufferIndex, 0, size, pts, flags);
            mOutputBuff.saveInPTS(pts);
            mInputCount++;
        }
    }

    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0) {
            if (mSaveToMem) {
                MediaCodec.BufferInfo copy = new MediaCodec.BufferInfo();
                copy.set(mOutputBuff.getOutStreamSize(), info.size, info.presentationTimeUs,
                        info.flags);
                mInfoList.add(copy);

                ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
                mOutputBuff.saveToMemory(buf, info);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mOutputBuff.saveOutPTS(info.presentationTimeUs);
                mOutputCount++;
            }
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    @Override
    PersistableBundle validateMetrics(String codec, MediaFormat format) {
        PersistableBundle metrics = super.validateMetrics(codec, format);
        assertEquals("error! metrics#MetricsConstants.MIME_TYPE is not as expected \n" + mTestConfig
                + mTestEnv, metrics.getString(MediaCodec.MetricsConstants.MIME_TYPE), mMime);
        assertEquals("error! metrics#MetricsConstants.ENCODER is not as expected \n" + mTestConfig
                + mTestEnv, 1, metrics.getInt(MediaCodec.MetricsConstants.ENCODER));
        return metrics;
    }

    void setUpParams(int limit) {
        int count = 0;
        for (int bitrate : mBitrates) {
            if (mIsAudio) {
                for (int rate : mEncParamList1) {
                    for (int channels : mEncParamList2) {
                        MediaFormat format = new MediaFormat();
                        format.setString(MediaFormat.KEY_MIME, mMime);
                        if (mMime.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                            format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, bitrate);
                        } else {
                            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                        }
                        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, rate);
                        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
                        mFormats.add(format);
                        count++;
                        if (count >= limit) return;
                    }
                }
            } else {
                assertEquals("Wrong number of height, width parameters \n" + mTestConfig + mTestEnv,
                        mEncParamList1.length, mEncParamList2.length);
                for (int i = 0; i < mEncParamList1.length; i++) {
                    MediaFormat format = new MediaFormat();
                    format.setString(MediaFormat.KEY_MIME, mMime);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                    format.setInteger(MediaFormat.KEY_WIDTH, mEncParamList1[i]);
                    format.setInteger(MediaFormat.KEY_HEIGHT, mEncParamList2[i]);
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
                    format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, mMaxBFrames);
                    format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                    mFormats.add(format);
                    count++;
                    if (count >= limit) return;
                }
            }
        }
    }

    void encodeToMemory(String file, String encoder, int frameLimit, MediaFormat format,
            boolean saveToMem) throws IOException, InterruptedException {
        mSaveToMem = saveToMem;
        mOutputBuff = new OutputManager();
        mInfoList.clear();
        mCodec = MediaCodec.createByCodecName(encoder);
        setUpSource(file);
        configureCodec(format, false, true, true);
        mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mSaveToMem = false;
    }

    ByteBuffer decodeElementaryStream(String decoder, MediaFormat format,
            ByteBuffer elementaryStream, ArrayList<MediaCodec.BufferInfo> infos)
            throws IOException, InterruptedException {
        String mime = format.getString(MediaFormat.KEY_MIME);
        CodecDecoderTestBase cdtb = new CodecDecoderTestBase(decoder, mime, null, mAllTestParams);
        cdtb.mOutputBuff = new OutputManager();
        cdtb.mSaveToMem = true;
        cdtb.mCodec = MediaCodec.createByCodecName(decoder);
        cdtb.mCodec.configure(format, null, null, 0);
        cdtb.mCodec.start();
        cdtb.doWork(elementaryStream, infos);
        cdtb.queueEOS();
        cdtb.waitForAllOutputs();
        cdtb.mCodec.stop();
        cdtb.mCodec.release();
        return cdtb.mOutputBuff.getBuffer();
    }

    void validateTestState() {
        super.validateTestState();
        if ((mIsAudio || (mIsVideo && mMaxBFrames == 0))
                && !mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts)) {
            fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                    + mOutputBuff.getErrMsg());
        }
        if (mIsVideo) {
            if (!mOutputBuff.isOutPtsListIdenticalToInpPtsList((mMaxBFrames != 0))) {
                fail("Input pts list and Output pts list are not identical \n" + mTestConfig
                        + mTestEnv + mOutputBuff.getErrMsg());
            }
        }
    }
}

class HDRDecoderTestBase extends CodecDecoderTestBase {
    private static final String LOG_TAG = HDRDecoderTestBase.class.getSimpleName();

    private ByteBuffer mHdrStaticInfoRef;
    private ByteBuffer mHdrStaticInfoStream;
    private ByteBuffer mHdrStaticInfoContainer;
    private Map<Integer, String> mHdrDynamicInfoRef;
    private Map<Integer, String> mHdrDynamicInfoStream;
    private Map<Integer, String> mHdrDynamicInfoContainer;
    private String mHdrDynamicInfoCurrent;

    HDRDecoderTestBase(String decoder, String mime, String testFile, String allTestParams) {
        super(decoder, mime, testFile, allTestParams);
    }

    void enqueueInput(int bufferIndex) {
        if (mHdrDynamicInfoContainer != null && mHdrDynamicInfoContainer.containsKey(mInputCount) &&
                mExtractor.getSampleSize() != -1) {
            insertHdrDynamicInfo(
                    loadByteArrayFromString(mHdrDynamicInfoContainer.get(mInputCount)));
        }
        super.enqueueInput(bufferIndex);
    }

    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && mHdrDynamicInfoRef != null) {
            MediaFormat format = mCodec.getOutputFormat(bufferIndex);
            if (mHdrDynamicInfoRef.containsKey(mOutputCount)) {
                mHdrDynamicInfoCurrent = mHdrDynamicInfoRef.get(mOutputCount);
            }
            validateHDRInfo(format, MediaFormat.KEY_HDR10_PLUS_INFO,
                    ByteBuffer.wrap(loadByteArrayFromString(mHdrDynamicInfoCurrent)));
        }
        super.dequeueOutput(bufferIndex, info);
    }

    void validateHDRInfo(String hdrStaticInfoStream, String hdrStaticInfoContainer,
            Map<Integer, String> hdrDynamicInfoStream, Map<Integer, String> hdrDynamicInfoContainer)
            throws IOException, InterruptedException {
        mHdrStaticInfoStream = hdrStaticInfoStream != null ?
                ByteBuffer.wrap(loadByteArrayFromString(hdrStaticInfoStream)) : null;
        mHdrStaticInfoContainer = hdrStaticInfoContainer != null ?
                ByteBuffer.wrap(loadByteArrayFromString(hdrStaticInfoContainer)) : null;
        mHdrStaticInfoRef = mHdrStaticInfoStream == null ? mHdrStaticInfoContainer :
                mHdrStaticInfoStream;
        mHdrDynamicInfoStream = hdrDynamicInfoStream;
        mHdrDynamicInfoContainer = hdrDynamicInfoContainer;
        mHdrDynamicInfoRef = hdrDynamicInfoStream == null ? hdrDynamicInfoContainer :
                hdrDynamicInfoStream;

        assertTrue("reference hdr10/hdr10+ info is not supplied for validation \n" + mTestConfig
                + mTestEnv, mHdrDynamicInfoRef != null || mHdrStaticInfoRef != null);

        if (mHdrDynamicInfoStream != null || mHdrDynamicInfoContainer != null) {
            Assume.assumeNotNull("Test is only applicable to codecs that have HDR10+ profiles",
                    mProfileHdr10PlusMap.get(mMime));
        }
        if (mHdrStaticInfoStream != null || mHdrStaticInfoContainer != null) {
            Assume.assumeNotNull("Test is only applicable to codecs that have HDR10 profiles",
                    mProfileHdr10Map.get(mMime));
        }

        Preconditions.assertTestFileExists(mTestFile);
        // For decoders, if you intend to supply hdr10+ info using external means like json, make
        // sure that info that is being supplied is in sync with SEI info
        if (mHdrDynamicInfoStream != null && mHdrDynamicInfoContainer != null) {
            assertEquals("Container hdr10+ info size and elementary stream SEI hdr10+ info"
                    + " size are unequal \n" + mTestConfig + mTestEnv, mHdrDynamicInfoStream.size(),
                    mHdrDynamicInfoContainer.size());
            for (Map.Entry<Integer, String> element : mHdrDynamicInfoStream.entrySet()) {
                assertTrue("Container hdr10+ info and elementary stream SEI hdr10+ info "
                        + "frame positions are not in sync \n" + mTestConfig + mTestEnv,
                        mHdrDynamicInfoContainer.containsKey(element.getKey()));
            }
        }
        mOutputBuff = new OutputManager();
        MediaFormat format = setUpSource(mTestFile);
        if (mHdrDynamicInfoStream != null || mHdrDynamicInfoContainer != null) {
            format.setInteger(MediaFormat.KEY_PROFILE, mProfileHdr10PlusMap.get(mMime)[0]);
        } else {
            format.setInteger(MediaFormat.KEY_PROFILE, mProfileHdr10Map.get(mMime)[0]);
        }
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        Assume.assumeTrue(mCodecName + " does not support HDR10/HDR10+ profile",
                areFormatsSupported(mCodecName, mMime, formatList));
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(format, false, true, false);
        mCodec.start();
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        if (mHdrStaticInfoRef != null) {
            validateHDRInfo(mCodec.getOutputFormat(), MediaFormat.KEY_HDR_STATIC_INFO,
                    mHdrStaticInfoRef);
        }
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
    }
}

class HDREncoderTestBase extends CodecEncoderTestBase {
    private static final String LOG_TAG = HDREncoderTestBase.class.getSimpleName();

    private ByteBuffer mHdrStaticInfo;
    private Map<Integer, String> mHdrDynamicInfo;

    private MediaMuxer mMuxer;
    private int mTrackID = -1;

    public HDREncoderTestBase(String encoderName, String mediaType, int bitrate, int width,
            int height, String allTestParams) {
        super(encoderName, mediaType, new int[]{bitrate}, new int[]{width}, new int[]{height},
                allTestParams);
    }

    void enqueueInput(int bufferIndex) {
        if (mHdrDynamicInfo != null && mHdrDynamicInfo.containsKey(mInputCount)) {
            insertHdrDynamicInfo(loadByteArrayFromString(mHdrDynamicInfo.get(mInputCount)));
        }
        super.enqueueInput(bufferIndex);
    }

    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        MediaFormat bufferFormat = mCodec.getOutputFormat(bufferIndex);
        if (info.size > 0) {
            ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
            if (mMuxer != null) {
                if (mTrackID == -1) {
                    mTrackID = mMuxer.addTrack(bufferFormat);
                    mMuxer.start();
                }
                mMuxer.writeSampleData(mTrackID, buf, info);
            }
        }
        super.dequeueOutput(bufferIndex, info);
    }

    void validateHDRInfo(String hdrStaticInfo, Map<Integer, String> hdrDynamicInfo)
            throws IOException, InterruptedException {
        mHdrStaticInfo = hdrStaticInfo != null ?
                ByteBuffer.wrap(loadByteArrayFromString(hdrStaticInfo)) : null;
        mHdrDynamicInfo = hdrDynamicInfo;

        setUpParams(1);

        MediaFormat format = mFormats.get(0);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUVP010);
        format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020);
        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_ST2084);
        int profile = (mHdrDynamicInfo != null) ? mProfileHdr10PlusMap.get(mMime)[0] :
                mProfileHdr10Map.get(mMime)[0];
        format.setInteger(MediaFormat.KEY_PROFILE, profile);

        if (mHdrStaticInfo != null) {
            format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, mHdrStaticInfo);
        }
        Assume.assumeTrue(mCodecName + " does not support HDR10/HDR10+ profile " + profile,
                areFormatsSupported(mCodecName, mMime, mFormats));
        Assume.assumeTrue(mCodecName + " does not support color format COLOR_FormatYUVP010",
                hasSupportForColorFormat(mCodecName, mMime, COLOR_FormatYUVP010));

        setUpSource(mActiveRawRes.mFileName);

        int frameLimit = 4;
        if (mHdrDynamicInfo != null) {
            Integer lastHdr10PlusFrame =
                    Collections.max(HDR_DYNAMIC_INFO.entrySet(), Map.Entry.comparingByKey())
                            .getKey();
            frameLimit = lastHdr10PlusFrame + 10;
        }
        int maxNumFrames = mInputData.length
                / (mActiveRawRes.mWidth * mActiveRawRes.mHeight * mActiveRawRes.mBytesPerSample);
        assertTrue("HDR info tests require input file with at least " + frameLimit + " frames. "
                + mActiveRawRes.mFileName + " has " + maxNumFrames + " frames. \n" + mTestConfig
                + mTestEnv, frameLimit <= maxNumFrames);

        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        File tmpFile;
        int muxerFormat;
        if (mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
            muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
            tmpFile = File.createTempFile("tmp10bit", ".webm");
        } else {
            muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            tmpFile = File.createTempFile("tmp10bit", ".mp4");
        }
        mMuxer = new MediaMuxer(tmpFile.getAbsolutePath(), muxerFormat);
        configureCodec(format, true, true, true);
        mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        if (mTrackID != -1) {
            mMuxer.stop();
            mTrackID = -1;
        }
        if (mMuxer != null) {
            mMuxer.release();
            mMuxer = null;
        }

        MediaFormat fmt = mCodec.getOutputFormat();

        mCodec.stop();
        mCodec.release();
        if (mHdrStaticInfo != null) {
            // verify if the out fmt contains HDR Static info as expected
            validateHDRInfo(fmt, MediaFormat.KEY_HDR_STATIC_INFO, mHdrStaticInfo);
        }

        // verify if the muxed file contains HDR Dynamic info as expected
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String decoder = codecList.findDecoderForFormat(format);
        assertNotNull("Device advertises support for encoding " + format + " but not decoding it \n"
                + mTestConfig + mTestEnv, decoder);

        HDRDecoderTestBase decoderTest =
                new HDRDecoderTestBase(decoder, mMime, tmpFile.getAbsolutePath(), mAllTestParams);
        decoderTest.validateHDRInfo(hdrStaticInfo, hdrStaticInfo, mHdrDynamicInfo, mHdrDynamicInfo);
        if (HDR_INFO_IN_BITSTREAM_CODECS.contains(mMime)) {
            decoderTest.validateHDRInfo(hdrStaticInfo, null, mHdrDynamicInfo, null);
        }
        tmpFile.delete();
    }
}
