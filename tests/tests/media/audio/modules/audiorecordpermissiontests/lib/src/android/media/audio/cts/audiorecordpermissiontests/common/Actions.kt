/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiorecordpermissiontests.common

// Inbound messages
const val ACTION_START_RECORD = ".ACTION_START_RECORD"
const val ACTION_STOP_RECORD = ".ACTION_STOP_RECORD"
const val ACTION_FINISH_RECORD = ".ACTION_FINISH_RECORD"
const val ACTION_GO_FOREGROUND = ".ACTION_GO_FOREGROUND"
const val ACTION_GO_BACKGROUND = ".ACTION_GO_BACKGROUND"
const val ACTION_ACTIVITY_DO_FINISH = ".ACTION_ACTIVITY_DO_FINISH"
const val ACTION_TEARDOWN = ".ACTION_TEARDOWN"
// Outbound messages
const val ACTION_STARTED_RECORD = ".ACTION_STARTED_RECORD"
const val ACTION_STOPPED_RECORD = ".ACTION_STOPPED_RECORD"
const val ACTION_BEGAN_RECEIVE_AUDIO = ".ACTION_BEGAN_RECEIVE_AUDIO"
const val ACTION_BEGAN_RECEIVE_SILENCE = ".ACTION_BEGAN_RECEIVE_SILENCE"
const val ACTION_FINISHED_RECORD = ".ACTION_FINISHED_RECORD"
const val ACTION_FINISHED_TEARDOWN = ".ACTION_FINISHED_TEARDOWN"
const val ACTION_ACTIVITY_STARTED = ".ACTION_ACTIVITY_STARTED"
const val ACTION_ACTIVITY_FINISHED = ".ACTION_ACTIVITY_FINISHED"
const val EXTRA_RECORD_ID = "EXTRA_RECORD_ID"
// Test instrumentation package
const val TARGET_PACKAGE = "android.media.audio.cts.audiorecordpermissiontests"
