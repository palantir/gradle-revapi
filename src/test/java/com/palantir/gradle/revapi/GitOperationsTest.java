/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.revapi;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.git.Git;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class GitOperationsTest {

    @BeforeEach
    void setUp(RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            tasks.register('printOldVersions') {
                doLast {
                    println "OLD_VERSIONS=" + revapi.oldVersions.get()
                }
            }
            """);
    }

    @Test
    void returns_nothing_in_a_repo_with_no_commits(GradleInvoker gradle, Git git) {
        // ensure git is initialised
        git.run("status");
        assertOldVersions(gradle, "[]");
    }

    @Test
    void returns_nothing_in_a_repo_with_just_one_commit_and_no_tags(GradleInvoker gradle, Git git) {
        git.commit("First");
        assertOldVersions(gradle, "[]");
    }

    @Test
    void returns_nothing_in_a_repo_with_just_one_commit_before_the_only_tag(GradleInvoker gradle, Git git) {
        git.commit("First");
        git.commit("Second");
        git.tag("2.0.0");
        assertOldVersions(gradle, "[]");
    }

    @Test
    void returns_one_tag_that_is_behind_head(GradleInvoker gradle, Git git) {
        git.commit("First");
        git.tag("1");
        git.commit("Second");
        git.commit("Third");
        assertOldVersions(gradle, "[1]");
    }

    @Test
    void returns_a_number_of_tags_that_are_behind_a_tag(GradleInvoker gradle, Git git) {
        git.commit("First");
        git.tag("1");
        git.commit("Second");
        git.tag("2");
        git.commit("Third");
        git.tag("3");
        git.commit("Fourth");
        git.tag("4");
        assertOldVersions(gradle, "[3, 2, 1]");
    }

    @Test
    void when_the_initial_commit_is_0_0_0_ignore_it_as_its_the_first_unpublished_release(
            GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.tag("0.0.0");
        git.commit("Another");
        assertOldVersions(gradle, "[]");
    }

    @Test
    void when_a_non_initial_commit_is_0_0_0_return_it(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("AnotherInitial");
        git.tag("0.0.0");
        git.commit("Additional");
        assertOldVersions(gradle, "[0.0.0]");
    }

    @Test
    void strips_tags_of_v_prefixes(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.tag("v1.2.3");
        git.commit("Additional");
        assertOldVersions(gradle, "[1.2.3]");
    }

    private static void assertOldVersions(GradleInvoker gradle, String expected) {
        assertThat(gradle.withArgs("printOldVersions").buildsSuccessfully())
                .output()
                .contains("OLD_VERSIONS=" + expected);
    }
}
