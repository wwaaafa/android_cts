/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.input

import com.android.compatibility.common.util.SystemUtil
import org.junit.AssumptionViolatedException
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A test rule that allows for additional debugging of the input pipeline to be enabled for tests.
 *
 * To enable additional debugging, add this test rule to the test class and annotate the desired
 * test methods with [DebugInput].
 *
 * Note: This will only work on debuggable builds (e.g. eng, userdebug).
 */
class DebugInputRule : TestWatcher() {

    private companion object {
        // The list of log tags to enable when additional debugging of the input pipeline is
        // required. These are a special set of log tags that can be dynamically toggled on
        // debuggable builds.
        val debugInputTags = listOf(
                "InputReaderRawEvents",
                "InputDispatcherInboundEvent",
                "InputTransportPublisher",
        )
    }

    /**
     * Annotation for a [org.junit.Test] that enables additional debugging in the input pipeline for
     * the duration of the test. The test class must use the [DebugInputRule] for annotation to be
     * functional.
     */
    annotation class DebugInput(val bug: Long)

    val initialValues = mutableMapOf<String, String>()

    override fun starting(description: Description?) {
        if (!shouldEnableInputDebugging(description!!)) return

        for (tag in debugInputTags) {
            initialValues[tag] =
                    SystemUtil.runShellCommandOrThrow("getprop log.tag.$tag")!!.trim()
            SystemUtil.runShellCommandOrThrow("setprop log.tag.$tag DEBUG")
        }
    }

    override fun finished(description: Description?) {
        if (!shouldEnableInputDebugging(description!!)) return

        for (tag in debugInputTags) {
            SystemUtil.runShellCommandOrThrow(
                    "setprop log.tag.$tag \"${initialValues.remove(tag)!!}\"")
        }
    }

    override fun skipped(e: AssumptionViolatedException?, description: Description?) {
        finished(description)
    }
}

private fun shouldEnableInputDebugging(description: Description): Boolean {
    return description.isTest &&
            description.getAnnotation(DebugInputRule.DebugInput::class.java) != null
}
