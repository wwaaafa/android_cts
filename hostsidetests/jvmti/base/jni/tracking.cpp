/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <mutex>

#include "jni.h"
#include "jvmti.h"

#include "android-base/logging.h"
#include "android-base/stringprintf.h"
#include "jni_binder.h"
#include "jvmti_helper.h"
#include "scoped_local_ref.h"
#include "scoped_utf_chars.h"
#include "test_env.h"

namespace art {

static std::string GetClassName(JNIEnv* jni_env, jclass cls) {
  ScopedLocalRef<jclass> class_class(jni_env, jni_env->GetObjectClass(cls));
  jmethodID mid = jni_env->GetMethodID(class_class.get(), "getName", "()Ljava/lang/String;");
  ScopedLocalRef<jstring> str(
      jni_env, reinterpret_cast<jstring>(jni_env->CallObjectMethod(cls, mid)));
  ScopedUtfChars utf_chars(jni_env, str.get());
  return utf_chars.c_str();
}

static std::mutex gLock;
static std::string gCollection;
static jthread gExpectedThread = nullptr;

static void RecordAllocationEvent(JNIEnv* jni_env, jobject object, jclass object_klass,
                                  jlong size) {
  std::string object_klass_descriptor = GetClassName(jni_env, object_klass);
  ScopedLocalRef<jclass> object_klass2(jni_env, jni_env->GetObjectClass(object));
  std::string object_klass_descriptor2 = GetClassName(jni_env, object_klass2.get());
  std::string result = android::base::StringPrintf("ObjectAllocated type %s/%s size %zu",
                                                   object_klass_descriptor.c_str(),
                                                   object_klass_descriptor2.c_str(),
                                                   static_cast<size_t>(size));
  std::unique_lock<std::mutex> mu(gLock);
  gCollection += result + "#";
}

static void JNICALL ObjectAllocatedGlobal(jvmtiEnv* ti_env ATTRIBUTE_UNUSED, JNIEnv* jni_env,
                                          jthread thread, jobject object, jclass object_klass,
                                          jlong size) {
  // Ignore events from threads other than the test thread. It is not possible
  // to make sure that we don't receive any outstanding callbacks after
  // disabling the allocation events. So just ignore events from other threads
  // to make the test stable.
  if (!jni_env->IsSameObject(thread, gExpectedThread)) {
    RecordAllocationEvent(jni_env, object, object_klass, size);
    return;
  }
  RecordAllocationEvent(jni_env, object, object_klass, size);
}

static void JNICALL ObjectAllocatedThread(jvmtiEnv* ti_env ATTRIBUTE_UNUSED, JNIEnv* jni_env,
                                          jthread thread, jobject object, jclass object_klass,
                                          jlong size) {
  CHECK(jni_env->IsSameObject(thread, gExpectedThread));
  RecordAllocationEvent(jni_env, object, object_klass, size);
}

extern "C" JNIEXPORT void JNICALL Java_android_jvmti_cts_JvmtiTrackingTest_setupObjectAllocCallback(
        JNIEnv* env, jclass klass ATTRIBUTE_UNUSED, jboolean enable, jboolean global) {
  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(jvmtiEventCallbacks));
  if (enable) {
    callbacks.VMObjectAlloc = global ? ObjectAllocatedGlobal : ObjectAllocatedThread;
  } else {
    callbacks.VMObjectAlloc = nullptr;
  }

  jvmtiError ret = jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks));
  JvmtiErrorToException(env, jvmti_env, ret);
}

extern "C" JNIEXPORT void JNICALL Java_android_jvmti_cts_JvmtiTrackingTest_enableAllocationTracking(
    JNIEnv* env, jclass klass ATTRIBUTE_UNUSED, jthread thread, jboolean enable) {
  jvmtiError ret = jvmti_env->SetEventNotificationMode(
      enable ? JVMTI_ENABLE : JVMTI_DISABLE,
      JVMTI_EVENT_VM_OBJECT_ALLOC,
      thread);
  if (enable) {
    if (thread == nullptr) {
      // We are enabling the allocation events globally but can only deterministically check the
      // ones on the current thread.
      jthread curr_thread;
      jvmti_env->GetCurrentThread(&curr_thread);
      gExpectedThread = env->NewGlobalRef(thread);
    } else {
      gExpectedThread = env->NewGlobalRef(thread);
    }
  } else if (gExpectedThread != nullptr) {
    env->DeleteGlobalRef(gExpectedThread);
    gExpectedThread = nullptr;
  }
  JvmtiErrorToException(env, jvmti_env, ret);
}

extern "C" JNIEXPORT
jstring JNICALL Java_android_jvmti_cts_JvmtiTrackingTest_getAndResetAllocationTrackingString(
    JNIEnv* env, jclass klass ATTRIBUTE_UNUSED) {
  // We will have a string allocation. So only do the C++ string retrieval under lock.
  std::string result;
  {
    std::unique_lock<std::mutex> mu(gLock);
    result.swap(gCollection);
  }
  // Make sure we give any other threads that might have been waiting to get a last crack time to
  // run. We will ignore their additions however.
  bool is_empty = false;
  do {
    {
      std::unique_lock<std::mutex> mu(gLock);
      is_empty = gCollection.empty();
      gCollection.clear();
    }
    sched_yield();
  } while (!is_empty);

  if (result.empty()) {
    return nullptr;
  }

  return env->NewStringUTF(result.c_str());
}

static JNINativeMethod gMethods[] = {
  { "setupObjectAllocCallback", "(Z;Z)V",
          (void*)Java_android_jvmti_cts_JvmtiTrackingTest_setupObjectAllocCallback},

  { "enableAllocationTracking", "(Ljava/lang/Thread;Z)V",
          (void*)Java_android_jvmti_cts_JvmtiTrackingTest_enableAllocationTracking },

  { "getAndResetAllocationTrackingString", "()Ljava/lang/String;",
          (void*)Java_android_jvmti_cts_JvmtiTrackingTest_getAndResetAllocationTrackingString },
};

void register_android_jvmti_cts_JvmtiTrackingTest(jvmtiEnv* jenv, JNIEnv* env) {
  ScopedLocalRef<jclass> klass(env, GetClass(jenv, env,
          "android/jvmti/cts/JvmtiTrackingTest", nullptr));
  if (klass.get() == nullptr) {
    env->ExceptionClear();
    return;
  }

  env->RegisterNatives(klass.get(), gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    LOG(ERROR) << "Could not register natives for JvmtiTrackingTest class";
  }
}

}  // namespace art
