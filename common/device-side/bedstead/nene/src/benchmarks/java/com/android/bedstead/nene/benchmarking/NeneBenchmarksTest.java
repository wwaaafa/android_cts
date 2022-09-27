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

package com.android.bedstead.nene.benchmarking;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BenchmarkTestRunner.class)
public class NeneBenchmarksTest {
    @Rule public BenchmarkRule mBenchmarkRule = new BenchmarkRule();

    @Test
    public void runBenchmark(Benchmark benchmark) {
        BenchmarkState state = mBenchmarkRule.getState();

        benchmark.beforeBenchmark();
        while (state.keepRunning()) {
            state.pauseTiming();
            benchmark.beforeIteration();
            state.resumeTiming();
            benchmark.run();
            state.pauseTiming();
            benchmark.afterIteration();
            state.resumeTiming();
        }
        benchmark.afterBenchmark();
    }
}
