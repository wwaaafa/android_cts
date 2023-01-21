/*
 * Copyright 2021 The Android Open Source Project
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

package android.app.appsearch.cts.app;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.PropertyPath;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class GetByDocumentIdRequestCtsTest {
    @Test
    public void testBuildRequest() {
        List<String> expectedPropertyPaths1 = Arrays.asList("path1", "path2");
        List<String> expectedPropertyPaths2 = Arrays.asList("path3", "path4");

        List<PropertyPath> expectedPropertyPathObjects1 =
                Arrays.asList(new PropertyPath("path1"), new PropertyPath("path2"));
        List<PropertyPath> expectedPropertyPathObjects2 =
                Arrays.asList(new PropertyPath("path3"), new PropertyPath("path4"));

        GetByDocumentIdRequest getByDocumentIdRequest =
                new GetByDocumentIdRequest.Builder("namespace")
                        .addIds("uri1", "uri2")
                        .addIds(Arrays.asList("uri3", "uri4"))
                        .addProjection("schemaType1", expectedPropertyPaths1)
                        .addProjectionPaths("schemaType2", expectedPropertyPathObjects2)
                        .build();

        assertThat(getByDocumentIdRequest.getIds()).containsExactly("uri1", "uri2", "uri3", "uri4");
        assertThat(getByDocumentIdRequest.getNamespace()).isEqualTo("namespace");
        assertThat(getByDocumentIdRequest.getProjections())
                .containsExactly(
                        "schemaType1",
                        expectedPropertyPaths1,
                        "schemaType2",
                        expectedPropertyPaths2);

        assertThat(getByDocumentIdRequest.getProjectionPaths())
                .containsExactly(
                        "schemaType1",
                        expectedPropertyPathObjects1,
                        "schemaType2",
                        expectedPropertyPathObjects2);
    }
}
