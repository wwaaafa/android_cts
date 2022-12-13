/*
 * Copyright (C) 2020 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "NativeCodecTestBase"
#include <log/log.h>

#include "NativeCodecTestBase.h"

static void onAsyncInputAvailable(AMediaCodec* codec, void* userdata, int32_t index) {
    (void)codec;
    assert(index >= 0);
    auto* aSyncHandle = static_cast<CodecAsyncHandler*>(userdata);
    callbackObject element{index};
    aSyncHandle->pushToInputList(element);
}

static void onAsyncOutputAvailable(AMediaCodec* codec, void* userdata, int32_t index,
                                   AMediaCodecBufferInfo* bufferInfo) {
    (void)codec;
    assert(index >= 0);
    auto* aSyncHandle = static_cast<CodecAsyncHandler*>(userdata);
    callbackObject element{index, bufferInfo};
    aSyncHandle->pushToOutputList(element);
}

static void onAsyncFormatChanged(AMediaCodec* codec, void* userdata, AMediaFormat* format) {
    (void)codec;
    auto* aSyncHandle = static_cast<CodecAsyncHandler*>(userdata);
    aSyncHandle->setOutputFormat(format);
    ALOGI("Output format changed: %s", AMediaFormat_toString(format));
}

static void onAsyncError(AMediaCodec* codec, void* userdata, media_status_t error,
                         int32_t actionCode, const char* detail) {
    (void)codec;
    auto* aSyncHandle = static_cast<CodecAsyncHandler*>(userdata);
    auto msg = StringFormat("###################  Async Error Details  #####################\n "
                            "received media codec error: %s , code : %d , action code: %d \n",
                            detail, error, actionCode);
    aSyncHandle->setError(true, msg);
    ALOGE("received media codec error: %s , code : %d , action code: %d ", detail, error,
          actionCode);
}

static bool arePtsListsIdentical(const std::vector<int64_t>& refArray,
                                 const std::vector<int64_t>& testArray, std::string& tmp) {
    bool isEqual = true;
    if (refArray.size() != testArray.size()) {
        tmp.append("Reference and test timestamps list sizes are not identical \n");
        tmp.append(StringFormat("reference pts list size is %zu \n", refArray.size()));
        tmp.append(StringFormat("test pts list size is %zu \n", testArray.size()));
        isEqual = false;
    }
    if (!isEqual || refArray != testArray) {
        isEqual = false;
        std::vector<int64_t> refArrayDiff;
        std::vector<int64_t> testArrayDiff;
        std::set_difference(refArray.begin(), refArray.end(), testArray.begin(), testArray.end(),
                            std::inserter(refArrayDiff, refArrayDiff.begin()));
        std::set_difference(testArray.begin(), testArray.end(), refArray.begin(), refArray.end(),
                            std::inserter(testArrayDiff, testArrayDiff.begin()));
        if (!refArrayDiff.empty()) {
            tmp.append("Some of the frame/access-units present in ref list are not present in test "
                       "list. Possibly due to frame drops \n");
            tmp.append("List of timestamps that are dropped by the component :- \n");
            tmp.append("pts :- [[ ");
            for (auto pts : refArrayDiff) {
                tmp.append(StringFormat("{ %" PRId64 " us }, ", pts));
            }
            tmp.append(" ]]\n");
        }
        if (!testArrayDiff.empty()) {
            tmp.append("Test list contains frame/access-units that are not present in ref list, "
                       "Possible due to duplicate transmissions \n");
            tmp.append("List of timestamps that are additionally present in test list are :- \n");
            tmp.append("pts :- [[ ");
            for (auto pts : testArrayDiff) {
                tmp.append(StringFormat("{ %" PRId64 " us }, ", pts));
            }
            tmp.append(" ]]\n");
        }
    }
    return isEqual;
}

CodecAsyncHandler::CodecAsyncHandler() {
    mOutFormat = nullptr;
    mSignalledOutFormatChanged = false;
    mSignalledError = false;
}

CodecAsyncHandler::~CodecAsyncHandler() {
    if (mOutFormat) {
        AMediaFormat_delete(mOutFormat);
        mOutFormat = nullptr;
    }
}

void CodecAsyncHandler::pushToInputList(callbackObject element) {
    std::unique_lock<std::mutex> lock{mMutex};
    mCbInputQueue.push_back(element);
    mCondition.notify_all();
}

void CodecAsyncHandler::pushToOutputList(callbackObject element) {
    std::unique_lock<std::mutex> lock{mMutex};
    mCbOutputQueue.push_back(element);
    mCondition.notify_all();
}

callbackObject CodecAsyncHandler::getInput() {
    callbackObject element{-1};
    std::unique_lock<std::mutex> lock{mMutex};
    while (!mSignalledError) {
        if (mCbInputQueue.empty()) {
            mCondition.wait(lock);
        } else {
            element = mCbInputQueue.front();
            mCbInputQueue.pop_front();
            break;
        }
    }
    return element;
}

callbackObject CodecAsyncHandler::getOutput() {
    callbackObject element;
    std::unique_lock<std::mutex> lock{mMutex};
    while (!mSignalledError) {
        if (mCbOutputQueue.empty()) {
            mCondition.wait(lock);
        } else {
            element = mCbOutputQueue.front();
            mCbOutputQueue.pop_front();
            break;
        }
    }
    return element;
}

callbackObject CodecAsyncHandler::getWork() {
    callbackObject element;
    std::unique_lock<std::mutex> lock{mMutex};
    while (!mSignalledError) {
        if (mCbInputQueue.empty() && mCbOutputQueue.empty()) {
            mCondition.wait(lock);
        } else {
            if (!mCbOutputQueue.empty()) {
                element = mCbOutputQueue.front();
                mCbOutputQueue.pop_front();
                break;
            } else {
                element = mCbInputQueue.front();
                mCbInputQueue.pop_front();
                break;
            }
        }
    }
    return element;
}

bool CodecAsyncHandler::isInputQueueEmpty() {
    std::unique_lock<std::mutex> lock{mMutex};
    return mCbInputQueue.empty();
}

void CodecAsyncHandler::clearQueues() {
    std::unique_lock<std::mutex> lock{mMutex};
    mCbInputQueue.clear();
    mCbOutputQueue.clear();
}

void CodecAsyncHandler::setOutputFormat(AMediaFormat* format) {
    std::unique_lock<std::mutex> lock{mMutex};
    assert(format != nullptr);
    if (mOutFormat) {
        AMediaFormat_delete(mOutFormat);
        mOutFormat = nullptr;
    }
    mOutFormat = format;
    mSignalledOutFormatChanged = true;
}

AMediaFormat* CodecAsyncHandler::getOutputFormat() {
    std::unique_lock<std::mutex> lock{mMutex};
    return mOutFormat;
}

bool CodecAsyncHandler::hasOutputFormatChanged() {
    std::unique_lock<std::mutex> lock{mMutex};
    return mSignalledOutFormatChanged;
}

void CodecAsyncHandler::setError(bool status, std::string& msg) {
    std::unique_lock<std::mutex> lock{mMutex};
    mSignalledError = status;
    mErrorMsg.append(msg);
    mCondition.notify_all();
}

bool CodecAsyncHandler::getError() const {
    return mSignalledError;
}

void CodecAsyncHandler::resetContext() {
    clearQueues();
    if (mOutFormat) {
        AMediaFormat_delete(mOutFormat);
        mOutFormat = nullptr;
    }
    mSignalledOutFormatChanged = false;
    mSignalledError = false;
    mErrorMsg.clear();
}

std::string CodecAsyncHandler::getErrorMsg() {
    return mErrorMsg;
}

media_status_t CodecAsyncHandler::setCallBack(AMediaCodec* codec, bool isCodecInAsyncMode) {
    media_status_t status = AMEDIA_OK;
    if (isCodecInAsyncMode) {
        AMediaCodecOnAsyncNotifyCallback callBack = {onAsyncInputAvailable, onAsyncOutputAvailable,
                                                     onAsyncFormatChanged, onAsyncError};
        status = AMediaCodec_setAsyncNotifyCallback(codec, callBack, this);
    }
    return status;
}

bool OutputManager::isPtsStrictlyIncreasing(int64_t lastPts) {
    bool result = true;
    for (auto i = 0; i < outPtsArray.size(); i++) {
        if (lastPts < outPtsArray[i]) {
            lastPts = outPtsArray[i];
        } else {
            mErrorLogs.append("Timestamp values are not strictly increasing. \n");
            mErrorLogs.append("Frame indices around which timestamp values decreased :- \n");
            for (auto j = std::max(0, i - 3); j < std::min((int)outPtsArray.size(), i + 3); j++) {
                if (j == 0) {
                    mErrorLogs.append(
                            StringFormat("pts of frame idx -1 is  %" PRId64 "\n", lastPts));
                }
                mErrorLogs.append(
                        StringFormat("pts of frame idx %d is %" PRId64 "\n", j, outPtsArray[j]));
            }
            result = false;
            break;
        }
    }
    return result;
}

void OutputManager::updateChecksum(uint8_t* buf, AMediaCodecBufferInfo* info, int width, int height,
                                   int stride, int bytesPerSample) {
    uint8_t flattenInfo[16];
    int pos = 0;
    if (width <= 0 || height <= 0 || stride <= 0) {
        flattenField<int32_t>(flattenInfo, &pos, info->size);
    }
    flattenField<int32_t>(flattenInfo, &pos,
                          info->flags & ~AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
    flattenField<int64_t>(flattenInfo, &pos, info->presentationTimeUs);
    crc32value = crc32(crc32value, flattenInfo, pos);
    if (width > 0 && height > 0 && stride > 0 && bytesPerSample > 0) {
        // Only checksum Y plane
        std::vector<uint8_t> tmp(width * height * bytesPerSample, 0u);
        size_t offset = 0;
        for (int i = 0; i < height; ++i) {
            memcpy(tmp.data() + (i * width * bytesPerSample), buf + offset, width * bytesPerSample);
            offset += stride;
        }
        crc32value = crc32(crc32value, tmp.data(), width * height * bytesPerSample);
    } else {
        crc32value = crc32(crc32value, buf, info->size);
    }
}

bool OutputManager::isOutPtsListIdenticalToInpPtsList(bool requireSorting) {
    std::sort(inpPtsArray.begin(), inpPtsArray.end());
    if (requireSorting) {
        std::sort(outPtsArray.begin(), outPtsArray.end());
    }
    return arePtsListsIdentical(inpPtsArray, outPtsArray, mErrorLogs);
}

bool OutputManager::equals(OutputManager* that) {
    if (this == that) return true;
    if (that == nullptr) return false;
    if (!equalsInterlaced(that)) return false;
    if (!arePtsListsIdentical(outPtsArray, that->outPtsArray, that->mErrorLogs)) return false;
    return true;
}

bool OutputManager::equalsInterlaced(OutputManager* that) {
    if (this == that) return true;
    if (that == nullptr) return false;
    if (crc32value != that->crc32value) {
        that->mErrorLogs.append("CRC32 checksums computed for byte buffers received from "
                                "getOutputBuffer() do not match between ref and test runs. \n");
        that->mErrorLogs.append(StringFormat("Ref CRC32 checksum value is %lu \n", crc32value));
        that->mErrorLogs.append(
                StringFormat("Test CRC32 checksum value is %lu \n", that->crc32value));
        if (memory.size() == that->memory.size()) {
            int count = 0;
            for (int i = 0; i < memory.size(); i++) {
                if (memory[i] != that->memory[i]) {
                    count++;
                    that->mErrorLogs.append(StringFormat("At offset %d, ref buffer val is %x and "
                                                         "test buffer val is %x \n",
                                                         i, memory[i], that->memory[i]));
                    if (count == 20) {
                        that->mErrorLogs.append("stopping after 20 mismatches, ...\n");
                        break;
                    }
                }
            }
            if (count != 0) {
                that->mErrorLogs.append("Ref and Test outputs are not identical \n");
            }
        } else {
            that->mErrorLogs.append("CRC32 byte buffer checksums are different because ref and "
                                    "test output sizes are not identical \n");
            that->mErrorLogs.append(StringFormat("Ref output buffer size %d \n", memory.size()));
            that->mErrorLogs.append(
                    StringFormat("Test output buffer size %d \n", that->memory.size()));
        }
        return false;
    }
    return true;
}

float OutputManager::getRmsError(uint8_t* refData, int length) {
    long totalErrorSquared = 0;
    if (length != memory.size()) return MAXFLOAT;
    if ((length % 2) != 0) return MAXFLOAT;
    auto* testData = new uint8_t[length];
    std::copy(memory.begin(), memory.end(), testData);
    auto* testDataReinterpret = reinterpret_cast<int16_t*>(testData);
    auto* refDataReinterpret = reinterpret_cast<int16_t*>(refData);
    for (int i = 0; i < length / 2; i++) {
        int d = testDataReinterpret[i] - refDataReinterpret[i];
        totalErrorSquared += d * d;
    }
    delete[] testData;
    long avgErrorSquared = (totalErrorSquared / (length / 2));
    return (float)sqrt(avgErrorSquared);
}

CodecTestBase::CodecTestBase(const char* mime) {
    mMime = mime;
    mIsAudio = strncmp(mime, "audio/", strlen("audio/")) == 0;
    mIsVideo = strncmp(mime, "video/", strlen("video/")) == 0;
    mIsCodecInAsyncMode = false;
    mSawInputEOS = false;
    mSawOutputEOS = false;
    mSignalEOSWithLastFrame = false;
    mInputCount = 0;
    mOutputCount = 0;
    mPrevOutputPts = INT32_MIN;
    mSignalledOutFormatChanged = false;
    mOutFormat = nullptr;
    mSaveToMem = false;
    mOutputBuff = nullptr;
    mCodec = nullptr;
    mBytesPerSample = mIsAudio ? 2 : 1;
}

CodecTestBase::~CodecTestBase() {
    if (mOutFormat) {
        AMediaFormat_delete(mOutFormat);
        mOutFormat = nullptr;
    }
    if (mCodec) {
        AMediaCodec_delete(mCodec);
        mCodec = nullptr;
    }
}

bool CodecTestBase::configureCodec(AMediaFormat* format, bool isAsync, bool signalEOSWithLastFrame,
                                   bool isEncoder) {
    resetContext(isAsync, signalEOSWithLastFrame);
    mTestEnv = "###################      Test Environment       #####################\n";
    {
        char* name = nullptr;
        media_status_t val = AMediaCodec_getName(mCodec, &name);
        if (AMEDIA_OK != val) {
            mErrorLogs = StringFormat("%s with error %d \n", "AMediaCodec_getName failed", val);
            return false;
        }
        if (!name) {
            mErrorLogs = std::string{"AMediaCodec_getName returned null"};
            return false;
        }
        mTestEnv.append(StringFormat("Component name %s \n", name));
        AMediaCodec_releaseName(mCodec, name);
    }
    mTestEnv.append(StringFormat("Format under test :- %s \n", AMediaFormat_toString(format)));
    mTestEnv.append(StringFormat("Component operating in :- %s mode \n",
                                 (isAsync ? "asynchronous" : "synchronous")));
    mTestEnv.append(
            StringFormat("Component received input eos :- %s \n",
                         (signalEOSWithLastFrame ? "with full buffer" : "with empty buffer")));
    RETURN_IF_FAIL(mAsyncHandle.setCallBack(mCodec, isAsync),
                   "AMediaCodec_setAsyncNotifyCallback failed")
    RETURN_IF_FAIL(AMediaCodec_configure(mCodec, format, nullptr, nullptr,
                                         isEncoder ? AMEDIACODEC_CONFIGURE_FLAG_ENCODE : 0),
                   "AMediaCodec_configure failed")
    return true;
}

bool CodecTestBase::flushCodec() {
    RETURN_IF_FAIL(AMediaCodec_flush(mCodec), "AMediaCodec_flush failed")
    // TODO(b/147576107): is it ok to clearQueues right away or wait for some signal
    mAsyncHandle.clearQueues();
    mSawInputEOS = false;
    mSawOutputEOS = false;
    mInputCount = 0;
    mOutputCount = 0;
    mPrevOutputPts = INT32_MIN;
    return true;
}

bool CodecTestBase::reConfigureCodec(AMediaFormat* format, bool isAsync,
                                     bool signalEOSWithLastFrame, bool isEncoder) {
    RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
    return configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder);
}

void CodecTestBase::resetContext(bool isAsync, bool signalEOSWithLastFrame) {
    mAsyncHandle.resetContext();
    mIsCodecInAsyncMode = isAsync;
    mSawInputEOS = false;
    mSawOutputEOS = false;
    mSignalEOSWithLastFrame = signalEOSWithLastFrame;
    mInputCount = 0;
    mOutputCount = 0;
    mPrevOutputPts = INT32_MIN;
    mSignalledOutFormatChanged = false;
    if (mOutFormat) {
        AMediaFormat_delete(mOutFormat);
        mOutFormat = nullptr;
    }
}

bool CodecTestBase::isTestStateValid() {
    RETURN_IF_TRUE(hasSeenError(),
                   std::string{"Encountered error in async mode. \n"}.append(
                           mAsyncHandle.getErrorMsg()))
    RETURN_IF_TRUE(mInputCount > 0 && mOutputCount <= 0,
                   StringFormat("fed %d input frames, received no output frames \n", mInputCount))
    /*if (mInputCount == 0 && mInputCount != mOutputCount) {
        (void)mOutputBuff->isOutPtsListIdenticalToInpPtsList(true);
        RETURN_IF_TRUE(true,
                       StringFormat("The number of output frames received is not same as number of "
                                    "input frames queued. Output count is %d, Input count is %d \n",
                                    mOutputCount, mInputCount)
                               .append(mOutputBuff->getErrorMsg()))
    }*/
    return true;
}

bool CodecTestBase::enqueueEOS(size_t bufferIndex) {
    if (!hasSeenError() && !mSawInputEOS) {
        RETURN_IF_FAIL(AMediaCodec_queueInputBuffer(mCodec, bufferIndex, 0, 0, 0,
                                                    AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM),
                       "AMediaCodec_queueInputBuffer failed")
        mSawInputEOS = true;
        ALOGV("Queued End of Stream");
    }
    return !hasSeenError();
}

bool CodecTestBase::doWork(int frameLimit) {
    bool isOk = true;
    int frameCnt = 0;
    if (mIsCodecInAsyncMode) {
        // output processing after queuing EOS is done in waitForAllOutputs()
        while (!hasSeenError() && isOk && !mSawInputEOS && frameCnt < frameLimit) {
            callbackObject element = mAsyncHandle.getWork();
            if (element.bufferIndex >= 0) {
                if (element.isInput) {
                    isOk = enqueueInput(element.bufferIndex);
                    frameCnt++;
                } else {
                    isOk = dequeueOutput(element.bufferIndex, &element.bufferInfo);
                }
            }
        }
    } else {
        AMediaCodecBufferInfo outInfo;
        // output processing after queuing EOS is done in waitForAllOutputs()
        while (isOk && !mSawInputEOS && frameCnt < frameLimit) {
            ssize_t oBufferID = AMediaCodec_dequeueOutputBuffer(mCodec, &outInfo, kQDeQTimeOutUs);
            if (oBufferID >= 0) {
                isOk = dequeueOutput(oBufferID, &outInfo);
            } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                if (mOutFormat) {
                    AMediaFormat_delete(mOutFormat);
                    mOutFormat = nullptr;
                }
                mOutFormat = AMediaCodec_getOutputFormat(mCodec);
                mSignalledOutFormatChanged = true;
            } else if (oBufferID == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            } else {
                auto msg = StringFormat("unexpected return value from "
                                        "AMediaCodec_dequeueOutputBuffer: %zd \n",
                                        oBufferID);
                mErrorLogs.append(msg);
                ALOGE("%s", msg.c_str());
                return false;
            }
            ssize_t iBufferId = AMediaCodec_dequeueInputBuffer(mCodec, kQDeQTimeOutUs);
            if (iBufferId >= 0) {
                isOk = enqueueInput(iBufferId);
                frameCnt++;
            } else if (iBufferId == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else {
                auto msg = StringFormat("unexpected return value from "
                                        "AMediaCodec_dequeueInputBuffer: %zd \n",
                                        iBufferId);
                mErrorLogs.append(msg);
                ALOGE("%s", msg.c_str());
                return false;
            }
        }
    }
    return !hasSeenError() && isOk;
}

bool CodecTestBase::queueEOS() {
    bool isOk = true;
    if (mIsCodecInAsyncMode) {
        while (!hasSeenError() && isOk && !mSawInputEOS) {
            callbackObject element = mAsyncHandle.getWork();
            if (element.bufferIndex >= 0) {
                if (element.isInput) {
                    isOk = enqueueEOS(element.bufferIndex);
                } else {
                    isOk = dequeueOutput(element.bufferIndex, &element.bufferInfo);
                }
            }
        }
    } else {
        AMediaCodecBufferInfo outInfo;
        while (isOk && !mSawInputEOS) {
            ssize_t oBufferID = AMediaCodec_dequeueOutputBuffer(mCodec, &outInfo, kQDeQTimeOutUs);
            if (oBufferID >= 0) {
                isOk = dequeueOutput(oBufferID, &outInfo);
            } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                if (mOutFormat) {
                    AMediaFormat_delete(mOutFormat);
                    mOutFormat = nullptr;
                }
                mOutFormat = AMediaCodec_getOutputFormat(mCodec);
                mSignalledOutFormatChanged = true;
            } else if (oBufferID == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            } else {
                auto msg = StringFormat("unexpected return value from "
                                        "AMediaCodec_dequeueOutputBuffer: %zd \n",
                                        oBufferID);
                mErrorLogs.append(msg);
                ALOGE("%s", msg.c_str());
                return false;
            }
            ssize_t iBufferId = AMediaCodec_dequeueInputBuffer(mCodec, kQDeQTimeOutUs);
            if (iBufferId >= 0) {
                isOk = enqueueEOS(iBufferId);
            } else if (iBufferId == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else {
                auto msg = StringFormat("unexpected return value from "
                                        "AMediaCodec_dequeueInputBuffer: %zd \n",
                                        iBufferId);
                mErrorLogs.append(msg);
                ALOGE("%s", msg.c_str());
                return false;
            }
        }
    }
    return !hasSeenError() && isOk;
}

bool CodecTestBase::waitForAllOutputs() {
    bool isOk = true;
    if (mIsCodecInAsyncMode) {
        while (!hasSeenError() && isOk && !mSawOutputEOS) {
            callbackObject element = mAsyncHandle.getOutput();
            if (element.bufferIndex >= 0) {
                isOk = dequeueOutput(element.bufferIndex, &element.bufferInfo);
            }
        }
    } else {
        AMediaCodecBufferInfo outInfo;
        while (!mSawOutputEOS) {
            int bufferID = AMediaCodec_dequeueOutputBuffer(mCodec, &outInfo, kQDeQTimeOutUs);
            if (bufferID >= 0) {
                isOk = dequeueOutput(bufferID, &outInfo);
            } else if (bufferID == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                if (mOutFormat) {
                    AMediaFormat_delete(mOutFormat);
                    mOutFormat = nullptr;
                }
                mOutFormat = AMediaCodec_getOutputFormat(mCodec);
                mSignalledOutFormatChanged = true;
            } else if (bufferID == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else if (bufferID == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            } else {
                auto msg = StringFormat("unexpected return value from "
                                        "AMediaCodec_dequeueOutputBuffer: %d \n",
                                        bufferID);
                mErrorLogs.append(msg);
                ALOGE("%s", msg.c_str());
                return false;
            }
        }
    }
    return isOk && isTestStateValid();
}

int CodecTestBase::getWidth(AMediaFormat* format) {
    int width = -1;
    int cropLeft, cropRight, cropTop, cropBottom;
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH, &width);
    if (AMediaFormat_getRect(format, "crop", &cropLeft, &cropTop, &cropRight, &cropBottom) ||
        (AMediaFormat_getInt32(format, "crop-left", &cropLeft) &&
         AMediaFormat_getInt32(format, "crop-right", &cropRight))) {
        width = cropRight + 1 - cropLeft;
    }
    return width;
}

int CodecTestBase::getHeight(AMediaFormat* format) {
    int height = -1;
    int cropLeft, cropRight, cropTop, cropBottom;
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_HEIGHT, &height);
    if (AMediaFormat_getRect(format, "crop", &cropLeft, &cropTop, &cropRight, &cropBottom) ||
        (AMediaFormat_getInt32(format, "crop-top", &cropTop) &&
         AMediaFormat_getInt32(format, "crop-bottom", &cropBottom))) {
        height = cropBottom + 1 - cropTop;
    }
    return height;
}

bool CodecTestBase::isFormatSimilar(AMediaFormat* inpFormat, AMediaFormat* outFormat) {
    const char *refMime = nullptr, *testMime = nullptr;
    bool hasRefMime = AMediaFormat_getString(inpFormat, AMEDIAFORMAT_KEY_MIME, &refMime);
    bool hasTestMime = AMediaFormat_getString(outFormat, AMEDIAFORMAT_KEY_MIME, &testMime);

    if (!hasRefMime || !hasTestMime) return false;
    if (!strncmp(refMime, "audio/", strlen("audio/"))) {
        int32_t refSampleRate = -1;
        int32_t testSampleRate = -2;
        int32_t refNumChannels = -1;
        int32_t testNumChannels = -2;
        AMediaFormat_getInt32(inpFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &refSampleRate);
        AMediaFormat_getInt32(outFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &testSampleRate);
        AMediaFormat_getInt32(inpFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &refNumChannels);
        AMediaFormat_getInt32(outFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &testNumChannels);
        return refNumChannels == testNumChannels && refSampleRate == testSampleRate &&
               (strncmp(testMime, "audio/", strlen("audio/")) == 0);
    } else if (!strncmp(refMime, "video/", strlen("video/"))) {
        int32_t refWidth = getWidth(inpFormat);
        int32_t testWidth = getWidth(outFormat);
        int32_t refHeight = getHeight(inpFormat);
        int32_t testHeight = getHeight(outFormat);
        return refWidth != -1 && refHeight != -1 && refWidth == testWidth &&
               refHeight == testHeight && (strncmp(testMime, "video/", strlen("video/")) == 0);
    }
    return true;
}
