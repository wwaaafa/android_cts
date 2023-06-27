/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless requied by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <thread>

#include <android-base/file.h>
#include <android-base/format.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/binder_status.h>
#include <android/multinetwork.h>
#include <android-base/unique_fd.h>
#include <bpf/BpfUtils.h>
#include <gtest/gtest.h>
#include <nettestutils/DumpService.h>

using android::base::unique_fd;
using android::base::ReadFdToString;
using android::bpf::getSocketCookie;
using android::bpf::NONEXISTENT_COOKIE;
using android::sp;
using android::String16;
using android::Vector;

class TagSocketTest : public ::testing::Test {
 public:
  TagSocketTest() {
    mBinder = ndk::SpAIBinder(AServiceManager_waitForService("connectivity"));
  }

 protected:
  ndk::SpAIBinder mBinder;
};

namespace {

constexpr uid_t TEST_UID = 10086;
constexpr uint32_t TEST_TAG = 42;

android::status_t dumpService(const ndk::SpAIBinder& binder,
                              const char** args,
                              uint32_t num_args,
                              std::vector<std::string>& outputLines) {
  unique_fd localFd, remoteFd;
  bool success = Pipe(&localFd, &remoteFd);
  EXPECT_TRUE(success) << "Failed to open pipe for dumping: " << strerror(errno);
  if (!success) return STATUS_UNKNOWN_ERROR;

  // dump() blocks until another thread has consumed all its output.
  std::thread dumpThread = std::thread([binder, remoteFd{std::move(remoteFd)}, args, num_args]() {
    EXPECT_EQ(android::OK, AIBinder_dump(binder.get(), remoteFd, args, num_args));
  });

  std::string dumpContent;

  EXPECT_TRUE(ReadFdToString(localFd.get(), &dumpContent))
      << "Error during dump: " << strerror(errno);
  dumpThread.join();

  std::stringstream dumpStream(dumpContent);
  std::string line;
  while (std::getline(dumpStream, line)) {
    outputLines.push_back(std::move(line));
  }

  return android::OK;
}

[[maybe_unused]] void dumpBpfMaps(const ndk::SpAIBinder& binder,
                                  std::vector<std::string>& output) {
  Vector<String16> vec;
  const char* arg = "trafficcontroller";
  android::status_t ret = dumpService(binder, &arg, 1, output);
  ASSERT_EQ(android::OK, ret)
      << "Error dumping service: " << android::statusToString(ret);
}

[[maybe_unused]] bool socketIsTagged(const ndk::SpAIBinder& binder, uint64_t cookie,
                                     uid_t uid, uint32_t tag) {
  std::string match =
      fmt::format("cookie={} tag={:#x} uid={}", cookie, tag, uid);
  std::vector<std::string> lines = {};
  dumpBpfMaps(binder, lines);
  for (const auto& line : lines) {
    if (std::string::npos != line.find(match)) return true;
  }
  return false;
}

[[maybe_unused]] bool socketIsNotTagged(const ndk::SpAIBinder& binder,
                                        uint64_t cookie) {
  std::string match = fmt::format("cookie={}", cookie);
  std::vector<std::string> lines = {};
  dumpBpfMaps(binder, lines);
  for (const auto& line : lines) {
    if (std::string::npos != line.find(match)) return false;
  }
  return true;
}

bool waitSocketIsNotTagged(const sp<IBinder>& binder, uint64_t cookie,
                           int maxTries) {
    for (int i = 0; i < maxTries; ++i) {
        if (socketIsNotTagged(binder, cookie)) return true;
        usleep(50 * 1000);
    }
    return false;
}

}  // namespace

TEST_F(TagSocketTest, TagSocket) {
  int sock = socket(AF_INET6, SOCK_STREAM | SOCK_CLOEXEC, 0);
  ASSERT_LE(0, sock);
  uint64_t cookie = getSocketCookie(sock);
  EXPECT_NE(NONEXISTENT_COOKIE, cookie);

  EXPECT_TRUE(socketIsNotTagged(mBinder, cookie));

  EXPECT_EQ(0, android_tag_socket(sock, TEST_TAG));
  EXPECT_TRUE(socketIsTagged(mBinder, cookie, geteuid(), TEST_TAG));
  EXPECT_EQ(0, android_untag_socket(sock));
  EXPECT_TRUE(socketIsNotTagged(mBinder, cookie));

  EXPECT_EQ(0, android_tag_socket_with_uid(sock, TEST_TAG, TEST_UID));
  EXPECT_TRUE(socketIsTagged(mBinder, cookie, TEST_UID, TEST_TAG));
  EXPECT_EQ(0, android_untag_socket(sock));
  EXPECT_TRUE(socketIsNotTagged(mBinder, cookie));

  EXPECT_EQ(0, android_tag_socket(sock, TEST_TAG));
  EXPECT_TRUE(socketIsTagged(mBinder, cookie, geteuid(), TEST_TAG));
  EXPECT_EQ(0, close(sock));
  EXPECT_TRUE(waitSocketIsNotTagged(mBinder, cookie, 100 /* maxTries */));
}

TEST_F(TagSocketTest, TagSocketErrors) {
  int sock = socket(AF_INET6, SOCK_STREAM | SOCK_CLOEXEC, 0);
  ASSERT_LE(0, sock);
  uint64_t cookie = getSocketCookie(sock);
  EXPECT_NE(NONEXISTENT_COOKIE, cookie);

  // Untag an untagged socket.
  EXPECT_EQ(-ENOENT, android_untag_socket(sock));
  EXPECT_TRUE(socketIsNotTagged(mBinder, cookie));

  // Untag a closed socket.
  close(sock);
  EXPECT_EQ(-EBADF, android_untag_socket(sock));
}
