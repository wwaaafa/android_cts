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

package android.car.cts;

import static android.os.Build.VERSION.CODENAME;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.PlatformApiVersion;
import android.car.PlatformVersionMismatchException;
import android.car.cts.TestApiRequirements.PlatformVersion;
import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;

/**
 * Rule for running CTS tests against different platform version. If a test have annotation
 * {@link TestApiRequirements}, then tests should be passed for all platform version great than or
 * equal to {@link TestApiRequirements#minPlatformVersion()}, also test should throw
 * {@link PlatformVersionMismatchException} for all platform lower than
 * {@link TestApiRequirements#minPlatformVersion()}.
 */
public final class PlatformApiCheckerRule implements TestRule {

    private static final String TAG = PlatformApiCheckerRule.class.getSimpleName();

    private static final boolean DEBUG = false;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TestApiRequirements apiRequirement = null;
                for (Annotation annotation : description.getAnnotations()) {
                    if (annotation instanceof TestApiRequirements) {
                        apiRequirement = (TestApiRequirements) annotation;
                        break;
                    }
                }

                if (apiRequirement == null) {
                    if (DEBUG) {
                        Log.d(TAG, "Didn't find TestApiRequirements annotation. Running test: "
                                + description.getMethodName());
                    }
                    base.evaluate();
                    return;
                }

                PlatformVersion expectedVersion = apiRequirement.minPlatformVersion();
                PlatformApiVersion currentPlatformApiVersion = Car.getPlatformApiVersion();
                if (isPlatformAtLeast(expectedVersion, currentPlatformApiVersion)) {
                    if (DEBUG) {
                        Log.d(TAG, "Found TestApiRequirements annotation. Expected platform: "
                                + expectedVersion + ", current platform: "
                                + currentPlatformApiVersion + ". Running test: "
                                + description.getMethodName());
                    }
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "Found TestApiRequirements annotation. Expected platform: "
                                + expectedVersion + ", current platform: "
                                + currentPlatformApiVersion + ". Expecting exception while "
                                + "running test: " + description.getMethodName());
                    }
                    assertThrows(PlatformVersionMismatchException.class, () -> base.evaluate());
                }
            }

            private boolean isPlatformAtLeast(PlatformVersion expectedVersion,
                    PlatformApiVersion currentPlatformApiVersion) {
                switch (expectedVersion) {
                    case TIRAMISU_0:
                        return currentPlatformApiVersion
                                .isAtLeast(PlatformApiVersion.TIRAMISU_0);
                    case TIRAMISU_1:
                        return currentPlatformApiVersion
                                .isAtLeast(PlatformApiVersion.TIRAMISU_1);
                    case UPSIDE_DOWN_CAKE_0:
                        return isPlatformVersionU(currentPlatformApiVersion);
                    default:
                        return false;
                }
            }

            // TODO(b/240298497): update this method when we have better handling of the API
            // version
            private boolean isPlatformVersionU(PlatformApiVersion currentPlatformApiVersion) {
                return currentPlatformApiVersion.getMajorVersion() >= 33
                        && isAtLeastPreReleaseCodename("UpsideDownCake");
            }

            private boolean isAtLeastPreReleaseCodename(String codename) {
                // Special case "REL", which means the build is not a pre-release build.
                if ("REL".equals(CODENAME)) {
                    return false;
                }

                // Otherwise lexically compare them. Return true if the build codename is
                // equal to or greater than the requested codename.
                return CODENAME.compareTo(codename) >= 0;
            }

        };
    }
}
