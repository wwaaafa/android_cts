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

package android.compilation.cts.statuscheckerapp;

import static dalvik.system.DexFile.OptimizationInfo;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import dalvik.system.ApplicationRuntime;
import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;

import com.google.common.io.ByteStreams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * An instrumentation test that checks optimization status.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class StatusCheckerAppTest {
    private static final String TAG = "StatusCheckerAppTest";
    private static final String SECONDARY_DEX_RES = "/StatusCheckerApp_Secondary.jar";

    @Test
    public void checkStatus() throws Exception {
        Bundle bundle = InstrumentationRegistry.getArguments();
        OptimizationInfo info = ApplicationRuntime.getBaseApkOptimizationInfo();
        assertThat(info.getStatus()).isEqualTo(bundle.getString("compiler-filter"));
        assertThat(info.getReason()).isEqualTo(bundle.getString("compilation-reason"));
        assertThat(info.isVerified()).isEqualTo(bundle.getString("is-verified").equals("true"));
        assertThat(info.isOptimized()).isEqualTo(bundle.getString("is-optimized").equals("true"));
        assertThat(info.isFullyCompiled())
                .isEqualTo(bundle.getString("is-fully-compiled").equals("true"));
    }

    @Test
    public void createAndLoadSecondaryDex() throws Exception {
        Bundle bundle = InstrumentationRegistry.getArguments();
        String secondaryDexFilename = bundle.getString("secondary-dex-filename");
        createAndLoadSecondaryDex(secondaryDexFilename, PathClassLoader::new);
    }

    @Test
    public void createAndLoadSecondaryDexUnsupportedClassLoader() throws Exception {
        Bundle bundle = InstrumentationRegistry.getArguments();
        String secondaryDexFilename = bundle.getString("secondary-dex-filename");
        createAndLoadSecondaryDex(secondaryDexFilename, CustomClassLoader::new);
    }

    private String createAndLoadSecondaryDex(String secondaryDexFilename,
            BiFunction<String, ClassLoader, ClassLoader> classLoaderCtor) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String dataDir = context.getApplicationInfo().dataDir;
        File secondaryDexFile = Paths.get(dataDir, secondaryDexFilename).toFile();
        if (secondaryDexFile.exists()) {
            secondaryDexFile.delete();
        }
        copyResourceToFile(SECONDARY_DEX_RES, secondaryDexFile);
        assertThat(secondaryDexFile.setReadOnly()).isTrue();
        classLoaderCtor.apply(secondaryDexFile.getAbsolutePath(), this.getClass().getClassLoader());
        return secondaryDexFile.getAbsolutePath();
    }

    @Test
    public void testSecondaryDexReporting() throws Exception {
        String fooPath = createAndLoadSecondaryDex("foo.jar", PathClassLoader::new);
        createAndLoadSecondaryDex("bar.jar", PathClassLoader::new);

        var reporter =
                (BaseDexClassLoader.Reporter) BaseDexClassLoader.class.getMethod("getReporter")
                        .invoke(null);

        // Invalid dex paths. The binder calls should be rejected, though we won't see any failure
        // on the client side because the calls are oneway.
        reporter.report(Map.of("relative/path.apk", "PCL[]"));
        reporter.report(Map.of("/non-normal/./path.apk", "PCL[]"));

        // Invalid class loader contexts. The binder calls should be rejected too.
        reporter.report(Map.of(fooPath, "ABC"));
        reporter.report(Map.of(fooPath, "PCL[./bar.jar]"));

        // Valid paths and class loader contexts.
        reporter.report(Map.of("/absolute/path.apk", "PCL[]"));
        reporter.report(Map.of(fooPath, "PCL[bar.jar]"));
        reporter.report(Map.of(fooPath, "=UnsupportedClassLoaderContext="));
    }

    public File copyResourceToFile(String resourceName, File file) throws Exception {
        try (OutputStream outputStream = new FileOutputStream(file);
                InputStream inputStream = getClass().getResourceAsStream(resourceName)) {
            assertThat(ByteStreams.copy(inputStream, outputStream)).isGreaterThan(0);
        }
        return file;
    }

    // A custom class loader that is unsupported by CLC encoding.
    public class CustomClassLoader extends PathClassLoader {
        public CustomClassLoader(String dexPath, ClassLoader parent) {
            super(dexPath, parent);
        }
    }
}
