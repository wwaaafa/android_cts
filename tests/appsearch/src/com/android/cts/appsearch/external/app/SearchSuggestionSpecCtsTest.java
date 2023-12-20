/*
 * Copyright 2022 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import android.app.appsearch.PropertyPath;
import android.app.appsearch.SearchSuggestionSpec;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

public class SearchSuggestionSpecCtsTest {
    @Test
    public void testBuildDefaultSearchSuggestionSpec() throws Exception {
        SearchSuggestionSpec searchSuggestionSpec =
                new SearchSuggestionSpec.Builder(/*totalResultCount=*/ 123).build();
        assertThat(searchSuggestionSpec.getMaximumResultCount()).isEqualTo(123);
        assertThat(searchSuggestionSpec.getFilterNamespaces()).isEmpty();
        assertThat(searchSuggestionSpec.getRankingStrategy())
                .isEqualTo(SearchSuggestionSpec.SUGGESTION_RANKING_STRATEGY_DOCUMENT_COUNT);
    }

    @Test
    public void testBuildSearchSuggestionSpec() throws Exception {
        SearchSuggestionSpec searchSuggestionSpec =
                new SearchSuggestionSpec.Builder(/*totalResultCount=*/ 123)
                        .setRankingStrategy(
                                SearchSuggestionSpec.SUGGESTION_RANKING_STRATEGY_TERM_FREQUENCY)
                        .addFilterNamespaces("namespace1", "namespace2")
                        .addFilterNamespaces(ImmutableList.of("namespace3"))
                        .addFilterDocumentIds("namespace1", ImmutableList.of("doc1", "doc2"))
                        .addFilterDocumentIds("namespace2", "doc3", "doc4")
                        .addFilterSchemas("Person", "Email")
                        .addFilterSchemas(ImmutableList.of("Foo"))
                        .build();

        assertThat(searchSuggestionSpec.getMaximumResultCount()).isEqualTo(123);
        assertThat(searchSuggestionSpec.getRankingStrategy())
                .isEqualTo(SearchSuggestionSpec.SUGGESTION_RANKING_STRATEGY_TERM_FREQUENCY);
        assertThat(searchSuggestionSpec.getFilterNamespaces())
                .containsExactly("namespace1", "namespace2", "namespace3");
        assertThat(searchSuggestionSpec.getFilterDocumentIds())
                .containsExactly(
                        "namespace1",
                        ImmutableList.of("doc1", "doc2"),
                        "namespace2",
                        ImmutableList.of("doc3", "doc4"));
        assertThat(searchSuggestionSpec.getFilterSchemas())
                .containsExactly("Person", "Email", "Foo");
    }

    @Test
    public void testDocumentIdFilterMustMatchNamespaceFilter() throws Exception {
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SearchSuggestionSpec.Builder(/*totalResultCount=*/ 123)
                                        .addFilterNamespaces("namespace1")
                                        .addFilterDocumentIds(
                                                "namespace2", ImmutableList.of("doc1"))
                                        .build());
        assertThat(e)
                .hasMessageThat()
                .contains(
                        "The namespace: namespace2 exists in the "
                                + "document id filter but doesn't exist in the namespace filter.");
    }

    @Test
    public void testBuildSearchSuggestionSpec_withPropertyFilter() throws Exception {
        SearchSuggestionSpec searchSuggestionSpec =
                new SearchSuggestionSpec.Builder(/*totalResultCount=*/ 123)
                        .setRankingStrategy(
                                SearchSuggestionSpec.SUGGESTION_RANKING_STRATEGY_TERM_FREQUENCY)
                        .addFilterSchemas("Person", "Email")
                        .addFilterSchemas(ImmutableList.of("Foo"))
                        .addFilterProperties("Email", ImmutableList.of("Subject", "body"))
                        .addFilterPropertyPaths("Foo", ImmutableList.of(new PropertyPath("Bar")))
                        .build();

        assertThat(searchSuggestionSpec.getMaximumResultCount()).isEqualTo(123);
        assertThat(searchSuggestionSpec.getFilterSchemas())
                .containsExactly("Person", "Email", "Foo");
        assertThat(searchSuggestionSpec.getFilterProperties())
                .containsExactly(
                        "Email",
                        ImmutableList.of("Subject", "body"),
                        "Foo",
                        ImmutableList.of("Bar"));
    }

    @Test
    public void testPropertyFilterMustMatchSchemaFilter() throws Exception {
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SearchSuggestionSpec.Builder(/*totalResultCount=*/ 123)
                                        .addFilterSchemas("Person")
                                        .addFilterProperties(
                                                "Email", ImmutableList.of("Subject", "body"))
                                        .build());
        assertThat(e)
                .hasMessageThat()
                .contains(
                        "The schema: Email exists in the "
                                + "property filter but doesn't exist in the schema filter.");
    }

    @Test
    public void testRebuild() throws Exception {
        SearchSuggestionSpec.Builder builder =
                new SearchSuggestionSpec.Builder(/*totalResultCount=*/ 123)
                        .addFilterNamespaces("namespace1", "namespace2")
                        .addFilterDocumentIds("namespace1", ImmutableList.of("doc1", "doc2"))
                        .addFilterSchemas("Person", "Email");

        SearchSuggestionSpec original = builder.build();

        builder.addFilterNamespaces("namespace3", "namespace4")
                .addFilterDocumentIds("namespace3", ImmutableList.of("doc3", "doc4"))
                .addFilterSchemas("Message", "Foo");
        SearchSuggestionSpec rebuild = builder.build();

        assertThat(original.getMaximumResultCount()).isEqualTo(123);
        assertThat(original.getFilterNamespaces()).containsExactly("namespace1", "namespace2");
        assertThat(original.getFilterDocumentIds())
                .containsExactly("namespace1", ImmutableList.of("doc1", "doc2"));
        assertThat(original.getFilterSchemas()).containsExactly("Person", "Email");

        assertThat(rebuild.getMaximumResultCount()).isEqualTo(123);
        assertThat(rebuild.getFilterNamespaces())
                .containsExactly("namespace1", "namespace2", "namespace3", "namespace4");
        assertThat(rebuild.getFilterDocumentIds())
                .containsExactly(
                        "namespace1",
                        ImmutableList.of("doc1", "doc2"),
                        "namespace3",
                        ImmutableList.of("doc3", "doc4"));
        assertThat(rebuild.getFilterSchemas()).containsExactly("Person", "Email", "Message", "Foo");
    }

    @Test
    public void testRebuild_withPropertyFilter() throws Exception {
        SearchSuggestionSpec.Builder builder =
                new SearchSuggestionSpec.Builder(/*totalResultCount=*/ 123)
                        .addFilterSchemas("Person", "Email")
                        .addFilterProperties("Email", ImmutableList.of("Subject", "body"));

        SearchSuggestionSpec original = builder.build();

        builder.addFilterSchemas("Message", "Foo")
                .addFilterProperties("Foo", ImmutableList.of("Bar"));
        SearchSuggestionSpec rebuild = builder.build();

        assertThat(original.getMaximumResultCount()).isEqualTo(123);
        assertThat(original.getFilterSchemas()).containsExactly("Person", "Email");
        assertThat(original.getFilterProperties())
                .containsExactly("Email", ImmutableList.of("Subject", "body"));

        assertThat(rebuild.getMaximumResultCount()).isEqualTo(123);
        assertThat(rebuild.getFilterSchemas()).containsExactly("Person", "Email", "Message", "Foo");
        assertThat(rebuild.getFilterProperties())
                .containsExactly(
                        "Email",
                        ImmutableList.of("Subject", "body"),
                        "Foo",
                        ImmutableList.of("Bar"));
    }
}
