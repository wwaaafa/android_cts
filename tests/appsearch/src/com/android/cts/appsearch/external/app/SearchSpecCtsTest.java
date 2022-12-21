/*
 * Copyright 2020 The Android Open Source Project
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

import android.app.appsearch.JoinSpec;
import android.app.appsearch.PropertyPath;
import android.app.appsearch.SearchSpec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SearchSpecCtsTest {
    @Test
    public void testBuildSearchSpecWithoutTermMatch() {
        SearchSpec searchSpec = new SearchSpec.Builder().addFilterSchemas("testSchemaType").build();
        assertThat(searchSpec.getTermMatch()).isEqualTo(SearchSpec.TERM_MATCH_PREFIX);
    }

    @Test
    public void testBuildSearchSpec() {
        List<String> expectedPropertyPaths1 = ImmutableList.of("path1", "path2");
        List<String> expectedPropertyPaths2 = ImmutableList.of("path3", "path4");
        Map<String, Double> expectedPropertyWeights =
                ImmutableMap.of("property1", 1.0, "property2", 2.0);
        Map<PropertyPath, Double> expectedPropertyWeightPaths =
                ImmutableMap.of(new PropertyPath("property1.nested"), 1.0);

        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                        .addFilterNamespaces("namespace1", "namespace2")
                        .addFilterNamespaces(ImmutableList.of("namespace3"))
                        .addFilterSchemas("schemaTypes1", "schemaTypes2")
                        .addFilterSchemas(ImmutableList.of("schemaTypes3"))
                        .addFilterPackageNames("package1", "package2")
                        .addFilterPackageNames(ImmutableList.of("package3"))
                        .setSnippetCount(5)
                        .setSnippetCountPerProperty(10)
                        .setMaxSnippetSize(15)
                        .setResultCountPerPage(42)
                        .setOrder(SearchSpec.ORDER_ASCENDING)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                        .setResultGrouping(
                                SearchSpec.GROUPING_TYPE_PER_NAMESPACE
                                        | SearchSpec.GROUPING_TYPE_PER_PACKAGE,
                                /*limit=*/ 37)
                        .addProjection("schemaType1", expectedPropertyPaths1)
                        .addProjection("schemaType2", expectedPropertyPaths2)
                        .setPropertyWeights("schemaType1", expectedPropertyWeights)
                        .setPropertyWeightPaths("schemaType2", expectedPropertyWeightPaths)
                        .build();

        assertThat(searchSpec.getTermMatch()).isEqualTo(SearchSpec.TERM_MATCH_PREFIX);
        assertThat(searchSpec.getFilterNamespaces())
                .containsExactly("namespace1", "namespace2", "namespace3")
                .inOrder();
        assertThat(searchSpec.getFilterSchemas())
                .containsExactly("schemaTypes1", "schemaTypes2", "schemaTypes3")
                .inOrder();
        assertThat(searchSpec.getFilterPackageNames())
                .containsExactly("package1", "package2", "package3")
                .inOrder();
        assertThat(searchSpec.getSnippetCount()).isEqualTo(5);
        assertThat(searchSpec.getSnippetCountPerProperty()).isEqualTo(10);
        assertThat(searchSpec.getMaxSnippetSize()).isEqualTo(15);
        assertThat(searchSpec.getResultCountPerPage()).isEqualTo(42);
        assertThat(searchSpec.getOrder()).isEqualTo(SearchSpec.ORDER_ASCENDING);
        assertThat(searchSpec.getRankingStrategy())
                .isEqualTo(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE);
        assertThat(searchSpec.getResultGroupingTypeFlags())
                .isEqualTo(
                        SearchSpec.GROUPING_TYPE_PER_NAMESPACE
                                | SearchSpec.GROUPING_TYPE_PER_PACKAGE);
        assertThat(searchSpec.getProjections())
                .containsExactly(
                        "schemaType1",
                        expectedPropertyPaths1,
                        "schemaType2",
                        expectedPropertyPaths2);
        assertThat(searchSpec.getResultGroupingLimit()).isEqualTo(37);
        assertThat(searchSpec.getPropertyWeights().keySet())
                .containsExactly("schemaType1", "schemaType2");
        assertThat(searchSpec.getPropertyWeights().get("schemaType1"))
                .containsExactly("property1", 1.0, "property2", 2.0);
        assertThat(searchSpec.getPropertyWeights().get("schemaType2"))
                .containsExactly("property1.nested", 1.0);
        assertThat(searchSpec.getPropertyWeightPaths().get("schemaType1"))
                .containsExactly(
                        new PropertyPath("property1"), 1.0, new PropertyPath("property2"), 2.0);
        assertThat(searchSpec.getPropertyWeightPaths().get("schemaType2"))
                .containsExactly(new PropertyPath("property1.nested"), 1.0);
    }

    @Test
    public void testGetProjectionTypePropertyMasks() {
        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                        .addProjection("TypeA", ImmutableList.of("field1", "field2.subfield2"))
                        .addProjection("TypeB", ImmutableList.of("field7"))
                        .addProjection("TypeC", ImmutableList.of())
                        .build();

        Map<String, List<String>> typePropertyPathMap = searchSpec.getProjections();
        assertThat(typePropertyPathMap.keySet()).containsExactly("TypeA", "TypeB", "TypeC");
        assertThat(typePropertyPathMap.get("TypeA")).containsExactly("field1", "field2.subfield2");
        assertThat(typePropertyPathMap.get("TypeB")).containsExactly("field7");
        assertThat(typePropertyPathMap.get("TypeC")).isEmpty();
    }

    @Test
    public void testGetTypePropertyWeights() {
        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                        .setPropertyWeights(
                                "TypeA", ImmutableMap.of("property1", 1.0, "property2", 2.0))
                        .setPropertyWeights(
                                "TypeB",
                                ImmutableMap.of("property1", 1.0, "property2" + ".nested", 2.0))
                        .build();

        Map<String, Map<String, Double>> typePropertyWeightsMap = searchSpec.getPropertyWeights();

        assertThat(typePropertyWeightsMap.keySet()).containsExactly("TypeA", "TypeB");
        assertThat(typePropertyWeightsMap.get("TypeA"))
                .containsExactly("property1", 1.0, "property2", 2.0);
        assertThat(typePropertyWeightsMap.get("TypeB"))
                .containsExactly("property1", 1.0, "property2.nested", 2.0);
    }

    @Test
    public void testGetTypePropertyWeightPaths() {
        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                        .setPropertyWeightPaths(
                                "TypeA",
                                ImmutableMap.of(
                                        new PropertyPath("property1"), 1.0,
                                        new PropertyPath("property2"), 2.0))
                        .setPropertyWeightPaths(
                                "TypeB",
                                ImmutableMap.of(
                                        new PropertyPath("property1"), 1.0,
                                        new PropertyPath("property2.nested"), 2.0))
                        .build();

        Map<String, Map<PropertyPath, Double>> typePropertyWeightsMap =
                searchSpec.getPropertyWeightPaths();

        assertThat(typePropertyWeightsMap.keySet()).containsExactly("TypeA", "TypeB");
        assertThat(typePropertyWeightsMap.get("TypeA"))
                .containsExactly(
                        new PropertyPath("property1"), 1.0, new PropertyPath("property2"), 2.0);
        assertThat(typePropertyWeightsMap.get("TypeB"))
                .containsExactly(
                        new PropertyPath("property1"),
                        1.0,
                        new PropertyPath("property2.nested"),
                        2.0);
    }

    @Test
    public void testSetPropertyWeights_nonPositiveWeight() {
        SearchSpec.Builder searchSpecBuilder = new SearchSpec.Builder();
        Map<String, Double> negativePropertyWeight = ImmutableMap.of("property", -1.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> searchSpecBuilder.setPropertyWeights("TypeA", negativePropertyWeight));

        Map<String, Double> zeroPropertyWeight = ImmutableMap.of("property", 0.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> searchSpecBuilder.setPropertyWeights("TypeA", zeroPropertyWeight));
    }

    @Test
    public void testSetPropertyWeightPaths_nonPositiveWeight() {
        SearchSpec.Builder searchSpecBuilder = new SearchSpec.Builder();
        Map<PropertyPath, Double> negativePropertyWeight =
                ImmutableMap.of(new PropertyPath("property"), -1.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> searchSpecBuilder.setPropertyWeightPaths("TypeA", negativePropertyWeight));

        Map<PropertyPath, Double> zeroPropertyWeight =
                ImmutableMap.of(new PropertyPath("property"), 0.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> searchSpecBuilder.setPropertyWeightPaths("TypeA", zeroPropertyWeight));
    }

    @Test
    public void testSetPropertyWeights_queryIndependentRankingStrategy() throws Exception {
        SearchSpec.Builder searchSpecBuilder =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_CREATION_TIMESTAMP)
                        .setPropertyWeights(
                                "TypeA", ImmutableMap.of("property1", 1.0, "property2", 2.0));

        assertThrows(IllegalArgumentException.class, () -> searchSpecBuilder.build());
    }

    @Test
    public void testSetPropertyWeightPaths_queryIndependentRankingStrategy() throws Exception {
        SearchSpec.Builder searchSpecBuilder =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_CREATION_TIMESTAMP)
                        .setPropertyWeightPaths(
                                "TypeA",
                                ImmutableMap.of(
                                        new PropertyPath("property1"), 1.0,
                                        new PropertyPath("property2"), 2.0));

        assertThrows(IllegalArgumentException.class, () -> searchSpecBuilder.build());
    }

    @Test
    public void testBuild_builtObjectsAreImmutable() throws Exception {
        SearchSpec.Builder searchSpecBuilder =
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                        .setPropertyWeights(
                                "TypeA", ImmutableMap.of("property1", 1.0, "property2", 2.0));

        SearchSpec originalSpec = searchSpecBuilder.build();

        // Modify the builder.
        SearchSpec newSpec =
                searchSpecBuilder
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .setPropertyWeights("TypeA", Collections.emptyMap())
                        .build();

        // Verify that 1) the changes took effect on the builder and 2) originalSpec was unaffected.
        assertThat(newSpec.getTermMatch()).isEqualTo(SearchSpec.TERM_MATCH_EXACT_ONLY);
        assertThat(newSpec.getRankingStrategy())
                .isEqualTo(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE);
        assertThat(newSpec.getPropertyWeights().keySet()).containsExactly("TypeA");
        assertThat(newSpec.getPropertyWeights().get("TypeA")).isEmpty();

        assertThat(originalSpec.getTermMatch()).isEqualTo(SearchSpec.TERM_MATCH_PREFIX);
        assertThat(originalSpec.getRankingStrategy())
                .isEqualTo(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE);
        assertThat(originalSpec.getPropertyWeights().keySet()).containsExactly("TypeA");
        assertThat(originalSpec.getPropertyWeights().get("TypeA").keySet())
                .containsExactly("property1", "property2");
    }

    @Test
    public void testGetJoinSpec() {
        JoinSpec joinSpec =
                new JoinSpec.Builder("entityId")
                        .setNestedSearch("joe", new SearchSpec.Builder().build())
                        .setAggregationScoringStrategy(JoinSpec.AGGREGATION_SCORING_RESULT_COUNT)
                        .setMaxJoinedResultCount(20)
                        .build();

        SearchSpec searchSpec = new SearchSpec.Builder().setJoinSpec(joinSpec).build();

        assertThat(searchSpec.getJoinSpec()).isNotNull();
        assertThat(searchSpec.getJoinSpec().getNestedQuery()).isEqualTo("joe");
        assertThat(searchSpec.getJoinSpec().getAggregationScoringStrategy())
                .isEqualTo(JoinSpec.AGGREGATION_SCORING_RESULT_COUNT);
        assertThat(searchSpec.getJoinSpec().getMaxJoinedResultCount()).isEqualTo(20);
        assertThat(searchSpec.getJoinSpec().getChildPropertyExpression()).isEqualTo("entityId");
    }

    @Test
    public void testAdvancedRanking() {
        SearchSpec emptySearchSpec = new SearchSpec.Builder().build();
        assertThat(emptySearchSpec.getAdvancedRankingExpression()).isEmpty();

        SearchSpec searchSpec =
                new SearchSpec.Builder().setRankingStrategy("this.documentScore()").build();
        assertThat(searchSpec.getRankingStrategy())
                .isEqualTo(SearchSpec.RANKING_STRATEGY_ADVANCED_RANKING_EXPRESSION);
        assertThat(searchSpec.getAdvancedRankingExpression()).isEqualTo("this.documentScore()");
    }

    @Test
    public void testOverwriteRankingStrategy() {
        SearchSpec emptySearchSpec = new SearchSpec.Builder().build();
        assertThat(emptySearchSpec.getAdvancedRankingExpression()).isEmpty();

        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .setRankingStrategy("this.documentScore()")
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                        .build();
        assertThat(searchSpec.getRankingStrategy())
                .isEqualTo(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE);
        assertThat(searchSpec.getAdvancedRankingExpression()).isEmpty();

        searchSpec =
                new SearchSpec.Builder()
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                        .setRankingStrategy("this.documentScore()")
                        .build();
        assertThat(searchSpec.getRankingStrategy())
                .isEqualTo(SearchSpec.RANKING_STRATEGY_ADVANCED_RANKING_EXPRESSION);
        assertThat(searchSpec.getAdvancedRankingExpression()).isEqualTo("this.documentScore()");
    }

    @Test
    public void testInvalidAdvancedRanking() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchSpec.Builder().setRankingStrategy(""));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SearchSpec.Builder()
                                .setRankingStrategy(
                                        SearchSpec.RANKING_STRATEGY_ADVANCED_RANKING_EXPRESSION));
    }

    @Test
    public void testInvalidJoinSpecConfig() {
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SearchSpec.Builder()
                                        .setRankingStrategy(
                                                SearchSpec.RANKING_STRATEGY_JOIN_AGGREGATE_SCORE)
                                        .build());

        assertThat(e.getMessage())
                .isEqualTo(
                        "Attempting to rank based on joined documents, but"
                                + " no JoinSpec provided");
    }

    @Test
    public void testRebuild() {
        JoinSpec originalJoinSpec =
                new JoinSpec.Builder("entityId")
                        .setNestedSearch(
                                "joe", new SearchSpec.Builder().addFilterSchemas("Action").build())
                        .build();

        JoinSpec newJoinSpec =
                new JoinSpec.Builder("entitySchema")
                        .setNestedSearch(
                                "", new SearchSpec.Builder().addFilterSchemas("CallAction").build())
                        .build();

        SearchSpec.Builder searchSpecBuilder =
                new SearchSpec.Builder().setJoinSpec(originalJoinSpec);

        SearchSpec original = searchSpecBuilder.build();
        SearchSpec rebuild = searchSpecBuilder.setJoinSpec(newJoinSpec).build();

        assertThat(original.getJoinSpec()).isNotNull();
        assertThat(original.getJoinSpec().getChildPropertyExpression()).isEqualTo("entityId");
        assertThat(original.getJoinSpec().getNestedQuery()).isEqualTo("joe");
        assertThat(original.getJoinSpec().getNestedSearchSpec().getFilterSchemas())
                .containsExactly("Action");

        assertThat(rebuild.getJoinSpec()).isNotNull();
        assertThat(rebuild.getJoinSpec().getChildPropertyExpression()).isEqualTo("entitySchema");
        assertThat(rebuild.getJoinSpec().getNestedQuery()).isEqualTo("");
        assertThat(rebuild.getJoinSpec().getNestedSearchSpec().getFilterSchemas())
                .containsExactly("CallAction");
    }
}
