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

package android.mediav2.common.cts;

import static android.media.MediaFormat.PICTURE_TYPE_B;
import static android.media.MediaFormat.PICTURE_TYPE_I;
import static android.media.MediaFormat.PICTURE_TYPE_P;
import static android.media.MediaFormat.PICTURE_TYPE_UNKNOWN;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**
 * This class contains utility functions that parse compressed bitstream and returns metadata
 * necessary for validation. This is by no means a thorough parser that is capable of parsing all
 * syntax elements of a bitstream. This is designed to handle only the requirements of mediav2
 * test suite.
 * <p>
 * Currently this class hosts utils that can,
 * <ul>
 *     <li>Return frame type of the access units of avc, hevc.</li>
 * </ul>
 */
public class BitStreamUtils {
    static class ParsableBitArray {
        protected final byte[] mData;
        protected final int mOffset;
        protected final int mLimit;
        protected int mCurrByteOffset;
        protected int mCurrBitOffset;

        ParsableBitArray(byte[] data, int offset, int limit) {
            mData = data;
            mOffset = offset;
            mLimit = limit;
            mCurrByteOffset = offset;
            mCurrBitOffset = 0;
        }

        public boolean readBit() {
            if (mCurrByteOffset >= mLimit) {
                throw new ArrayIndexOutOfBoundsException(
                        String.format("Accessing bytes at offset %d, buffer limit %d",
                                mCurrByteOffset, mLimit));
            }
            boolean returnValue = (mData[mCurrByteOffset] & (0x80 >> mCurrBitOffset)) != 0;
            if (++mCurrBitOffset == 8) {
                mCurrBitOffset = 0;
                mCurrByteOffset++;
            }
            return returnValue;
        }

        public int readBits(int numBits) {
            if (numBits > 31) {
                throw new IllegalArgumentException(
                        "left shift overflow exception, reading too many bits at one go");
            }
            int value = 0;
            for (int i = 0; i < numBits; i++) {
                value <<= 1;
                value |= (readBit() ? 1 : 0);
            }
            return value;
        }
    }

    static class NalParsableBitArray extends ParsableBitArray {
        NalParsableBitArray(byte[] data, int offset, int limit) {
            super(data, offset, limit);
        }

        @Override
        public boolean readBit() {
            // emulation prevention
            if (mCurrBitOffset == 0 && (mCurrByteOffset - 2 > mOffset)
                    && mData[mCurrByteOffset] == (byte) 0x03
                    && mData[mCurrByteOffset - 1] == (byte) 0x00
                    && mData[mCurrByteOffset - 2] == (byte) 0x00) {
                mCurrByteOffset++;
            }
            return super.readBit();
        }

        public int readUEV() {
            int leadingZeros = 0;
            while (!readBit()) {
                leadingZeros++;
            }
            return (1 << leadingZeros) - 1 + (leadingZeros > 0 ? readBits(leadingZeros) : 0);
        }
    }

    public abstract static class ParserBase {
        protected byte[] mData;
        protected int mOffset;
        protected int mLimit;

        protected void set(byte[] data, int offset, int limit) {
            mData = data;
            mOffset = offset;
            mLimit = limit;
        }

        public abstract int getFrameType();
    }

    static class AvcParser extends ParserBase {
        private static final int NO_NAL_UNIT_FOUND = -1;

        private int getNalUnitStartOffset(byte[] dataArray, int start, int limit) {
            for (int pos = start; pos + 3 < limit; pos++) {
                if ((pos + 3) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 1) {
                    return pos + 3;
                } else if ((pos + 4) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 0 && dataArray[pos + 3] == 1) {
                    return pos + 4;
                }
            }
            return NO_NAL_UNIT_FOUND;
        }

        private static int getNalUnitType(byte[] dataArray, int nalUnitOffset) {
            return dataArray[nalUnitOffset] & 0x1F;
        }

        @Override
        public int getFrameType() {
            for (int pos = mOffset; pos < mLimit; ) {
                int offset = getNalUnitStartOffset(mData, pos, mLimit);
                if (offset == NO_NAL_UNIT_FOUND) return PICTURE_TYPE_UNKNOWN;
                int nalUnitType = getNalUnitType(mData, offset);
                if (nalUnitType == 1 || nalUnitType == 2 || nalUnitType == 5) {  // coded slice
                    NalParsableBitArray bitArray = new NalParsableBitArray(mData, offset, mLimit);
                    bitArray.readBits(8); // forbidden zero bit + nal_ref_idc + nal_unit_type
                    bitArray.readUEV(); // first_mb_in_slice
                    int sliceType = bitArray.readUEV();
                    if (sliceType % 5 == 0) {
                        return PICTURE_TYPE_P;
                    } else if (sliceType % 5 == 1) {
                        return PICTURE_TYPE_B;
                    } else if (sliceType % 5 == 2) {
                        return PICTURE_TYPE_I;
                    } else {
                        return PICTURE_TYPE_UNKNOWN;
                    }
                }
                pos = offset;
            }
            return PICTURE_TYPE_UNKNOWN;
        }
    }

    static class HevcParser extends ParserBase {
        private static final int NO_NAL_UNIT_FOUND = -1;
        private static final int TRAIL_N = 0;
        private static final int RASL_R = 9;
        private static final int BLA_W_LP = 16;
        private static final int RSV_IRAP_VCL23 = 23;

        private int getNalUnitStartOffset(byte[] dataArray, int start, int limit) {
            for (int pos = start; pos + 3 < limit; pos++) {
                if ((pos + 3) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 1) {
                    return pos + 3;
                } else if ((pos + 4) < limit && dataArray[pos] == 0 && dataArray[pos + 1] == 0
                        && dataArray[pos + 2] == 0 && dataArray[pos + 3] == 1) {
                    return pos + 4;
                }
            }
            return NO_NAL_UNIT_FOUND;
        }

        private static int getNalUnitType(byte[] dataArray, int nalUnitOffset) {
            return (dataArray[nalUnitOffset] & 0x7E) >> 1;
        }

        @Override
        public int getFrameType() {
            for (int pos = mOffset; pos < mLimit; ) {
                int offset = getNalUnitStartOffset(mData, pos, mLimit);
                if (offset == NO_NAL_UNIT_FOUND) return PICTURE_TYPE_UNKNOWN;
                int nalUnitType = getNalUnitType(mData, offset);
                if ((nalUnitType >= TRAIL_N && nalUnitType <= RASL_R) || (nalUnitType >= BLA_W_LP
                        && nalUnitType <= RSV_IRAP_VCL23)) { // codec slice
                    NalParsableBitArray bitArray = new NalParsableBitArray(mData, offset, mLimit);
                    bitArray.readBits(16); // nal_unit_header

                    // Parsing slice_segment_header values from H.265/HEVC Table 7.3.6.1
                    boolean firstSliceSegmentInPicFlag = bitArray.readBit();
                    if (!firstSliceSegmentInPicFlag) return PICTURE_TYPE_UNKNOWN;
                    if (nalUnitType >= BLA_W_LP && nalUnitType <= RSV_IRAP_VCL23) {
                        bitArray.readBit();  // no_output_of_prior_pics_flag
                    }
                    bitArray.readUEV(); // slice_pic_parameter_set_id
                    // FIXME: Assumes num_extra_slice_header_bits element of PPS data to be 0
                    int sliceType = bitArray.readUEV();
                    if (sliceType == 0) {
                        return PICTURE_TYPE_B;
                    } else if (sliceType == 1) {
                        return PICTURE_TYPE_P;
                    } else if (sliceType == 2) {
                        return PICTURE_TYPE_I;
                    } else {
                        return PICTURE_TYPE_UNKNOWN;
                    }
                }
                pos = offset;
            }
            return PICTURE_TYPE_UNKNOWN;
        }
    }

    public static ParserBase getParserObject(String mediaType) {
        switch (mediaType) {
            case MediaFormat.MIMETYPE_VIDEO_AVC:
                return new AvcParser();
            case MediaFormat.MIMETYPE_VIDEO_HEVC:
                return new HevcParser();
        }
        return null;
    }

    public static int getFrameTypeFromBitStream(ByteBuffer buf, MediaCodec.BufferInfo info,
            ParserBase o) {
        byte[] dataArray = new byte[info.size];
        buf.position(info.offset);
        buf.get(dataArray);
        o.set(dataArray, 0, info.size);
        return o.getFrameType();
    }
}
