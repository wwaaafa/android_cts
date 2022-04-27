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
import android.content.Context;
import android.content.pm.SharedLibraryInfo;
import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.VirtualPath;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Verifies that any shared library provided by this device and for which this test has a
 * corresponding API specific file provides the expected API.
 *
 * <pre>This test relies on the AndroidManifest.xml file for the APK in which this is run having a
 * {@code <uses-library>} entry for every shared library that provides an API that is contained
 * within the shared-libs-all.api.zip supplied to this test.
 */
public class SignatureMultiLibsTest extends AbstractSignatureTest {

    protected static final Supplier<String[]> EXPECTED_API_FILES =
            getSupplierOfAMandatoryCommaSeparatedListArgument(EXPECTED_API_FILES_ARG);

    protected static final Supplier<String[]> PREVIOUS_API_FILES =
            getSupplierOfAMandatoryCommaSeparatedListArgument(PREVIOUS_API_FILES_ARG);

    private static final String TAG = SignatureMultiLibsTest.class.getSimpleName();

    /**
     * A memoized supplier of the list of shared libraries on the device.
     */
    protected static final Supplier<Set<String>> AVAILABLE_SHARED_LIBRARIES =
            Suppliers.memoize(SignatureMultiLibsTest::retrieveActiveSharedLibraries)::get;

    /**
     * Retrieve the names of the shared libraries that are active on the device.
     *
     * @return The set of shared library names.
     */
    private static Set<String> retrieveActiveSharedLibraries() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        List<SharedLibraryInfo> sharedLibraries =
                context.getPackageManager().getSharedLibraries(0);

        Set<String> sharedLibraryNames = new TreeSet<>();
        for (SharedLibraryInfo sharedLibrary : sharedLibraries) {
            String name = sharedLibrary.getName();
            sharedLibraryNames.add(name);
            Log.d(TAG, String.format("Found library: %s%n", name));
        }

        return sharedLibraryNames;
    }

    /**
     * Return a stream of {@link JDiffClassDescription} that are expected to be provided by the
     * shared libraries which are installed on this device.
     *
     * @param apiDocumentParser the parser to use.
     * @param apiResources the list of API resource files.
     * @return a stream of {@link JDiffClassDescription}.
     */
    private Stream<JDiffClassDescription> parseActiveSharedLibraryApis(
            ApiDocumentParser apiDocumentParser, String[] apiResources) {
        return retrieveApiResourcesAsStream(getClass().getClassLoader(), apiResources)
                .filter(this::checkLibrary)
                .flatMap(apiDocumentParser::parseAsStream);
    }

    /**
     * Tests that the device's API matches the expected set defined in xml.
     * <p/>
     * Will check the entire API, and then report the complete list of failures
     */
    @Test
    public void testRuntimeCompatibilityWithCurrentApi() {
        runWithTestResultObserver(mResultObserver -> {
            ApiComplianceChecker complianceChecker =
                    new ApiComplianceChecker(mResultObserver, mClassProvider);

            // Load classes from any API files that form the base which the expected APIs extend.
            loadBaseClasses(complianceChecker);

            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);

            parseActiveSharedLibraryApis(apiDocumentParser, EXPECTED_API_FILES.get())
                    .forEach(complianceChecker::checkSignatureCompliance);

            // After done parsing all expected API files, perform any deferred checks.
            complianceChecker.checkDeferred();
        });
    }

    /**
     * Tests that the device's API matches the previous APIs defined in xml.
     */
    @Test
    public void testRuntimeCompatibilityWithPreviousApis() {
        runWithTestResultObserver(mResultObserver -> {
            ApiComplianceChecker complianceChecker =
                    new ApiComplianceChecker(mResultObserver, mClassProvider);

            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);

            parseActiveSharedLibraryApis(apiDocumentParser, PREVIOUS_API_FILES.get())
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
     * @param path the path of the API file.
     * @return true if the API corresponds to a shared library on the device, false otherwise.
     */
    private boolean checkLibrary (VirtualPath path) {
        String name = path.toString();
        String libraryName = name.substring(name.lastIndexOf('/') + 1).split("-")[0];
        boolean matched = AVAILABLE_SHARED_LIBRARIES.get().contains(libraryName);
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
}
