/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.signature.cts.api;

import android.app.Instrumentation;
import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.VirtualPath;
import android.signature.cts.VirtualPath.LocalFilePath;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

/**
 * Verifies that any shared library provided by this device and for which this test has a
 * corresponding API specific file provides the expected API.
 *
 * <pre>This test relies on the AndroidManifest.xml file for the APK in which this is run having a
 * {@code <uses-library>} entry for every shared library that provides an API that is contained
 * within the shared-libs-all.api.zip supplied to this test.
 */
public class SignatureMultiLibsTest extends SignatureTest {

    private static final String TAG = SignatureMultiLibsTest.class.getSimpleName();

    private static Set<String> libraries;

    /**
     * Obtain a list of shared libraries from the device.
     */
    @BeforeClass
    public static void retrieveListOfSharedLibrariesOnDevice() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String result = runShellCommand(instrumentation, "cmd package list libraries");
        libraries = Arrays.stream(result.split("\n")).map(line -> line.split(":")[1])
                .peek(library -> System.out.printf("%s: Found library: %s%n",
                        SignatureMultiLibsTest.class.getSimpleName(), library))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Tests that the device's API matches the expected set defined in xml.
     * <p/>
     * Will check the entire API, and then report the complete list of failures
     */
    @Test
    public void testSignature() {
        runWithTestResultObserver(mResultObserver -> {
            ApiComplianceChecker complianceChecker =
                    new ApiComplianceChecker(mResultObserver, mClassProvider);

            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);

            parseApiResourcesAsStream(apiDocumentParser, expectedApiFiles)
                    .forEach(complianceChecker::checkSignatureCompliance);

            // After done parsing all expected API files, perform any deferred checks.
            complianceChecker.checkDeferred();
        });
    }

    /**
     * Tests that the device's API matches the previous APIs defined in xml.
     */
    @Test
    public void testPreviousSignatures() {
        runWithTestResultObserver(mResultObserver -> {
            ApiComplianceChecker complianceChecker =
                    new ApiComplianceChecker(mResultObserver, mClassProvider);

            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);

            parseApiResourcesAsStream(apiDocumentParser, previousApiFiles)
                    .map(clazz -> clazz.setPreviousApiFlag(true))
                    .forEach(complianceChecker::checkSignatureCompliance);

            // After done parsing all expected API files, perform any deferred checks.
            complianceChecker.checkDeferred();
        });
    }

    /**
     * Check to see if the supplied name is an API file for a shared library that is available on
     * this device.
     *
     * @param name the name of the possible API file for a shared library.
     * @return true if it is, false otherwise.
     */
    private boolean checkLibrary (String name) {
        String libraryName = name.substring(name.lastIndexOf('/') + 1).split("-")[0];
        boolean matched = libraries.contains(libraryName);
        if (matched) {
            System.out.printf("%s: Processing API file %s, from library %s as it does match a"
                            + " shared library on this device%n",
                    getClass().getSimpleName(), name, libraryName);
        } else {
            System.out.printf("%s: Ignoring API file %s, from library %s as it does not match a"
                    + " shared library on this device%n",
                    getClass().getSimpleName(), name, libraryName);
        }
        return matched;
    }

    /**
     * Override the method that gets the files from a supplied zip file to filter out any file that
     * does not correspond to a shared library available on the device.
     *
     * @param path the path to the zip file.
     * @return a stream of paths in the zip file that contain APIs that should be available to this
     * tests.
     * @throws IOException if there was an issue reading the zip file.
     */
    @Override
    protected Stream<VirtualPath> getZipEntryFiles(LocalFilePath path) throws IOException {
        // Only return entries corresponding to shared libraries.
        return super.getZipEntryFiles(path).filter(p -> checkLibrary(p.toString()));
    }
}
