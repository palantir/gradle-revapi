/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.maven.MavenArtifact;
import com.palantir.gradle.testing.maven.MavenRepo;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache("revapi plugin is incompatible with configuration cache")
class PreviousReleaseResolverTest {

    private static final String GROUP = "com.example";
    private static final String NAME = "library";

    @BeforeEach
    void setUp(RootProject rootProject, MavenRepo repo) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().withMavenRepo(repo);
        rootProject.buildGradle().append("""
            revapi {
                oldGroup = '%s'
                oldName = '%s'
            }

            tasks.register('printOldVersions') {
                doLast {
                    println "OLD_VERSIONS=" + revapi.oldVersions.get()
                }
            }
            """, GROUP, NAME);
    }

    @Test
    void returns_empty_when_no_versions_are_published(GradleInvoker gradle) {
        assertOldVersions(gradle, "[]");
    }

    @Test
    void returns_the_single_published_version(GradleInvoker gradle, MavenRepo repo) {
        publish(repo, "1.0.0");
        assertOldVersions(gradle, "[1.0.0]");
    }

    @Test
    void returns_the_two_published_versions_in_descending_order(GradleInvoker gradle, MavenRepo repo) {
        publish(repo, "1.0.0", "2.0.0");
        assertOldVersions(gradle, "[2.0.0, 1.0.0]");
    }

    @Test
    void returns_at_most_the_three_most_recent_versions(GradleInvoker gradle, MavenRepo repo) {
        publish(repo, "1.0.0", "2.0.0", "3.0.0", "4.0.0", "5.0.0");
        assertOldVersions(gradle, "[5.0.0, 4.0.0, 3.0.0]");
    }

    @Test
    void walks_back_through_pre_release_versions_when_no_final_supersedes_them(
            GradleInvoker gradle, MavenRepo repo) {
        publish(repo, "1.0.0-rc0", "1.0.0-rc1", "1.0.0-rc2");
        assertOldVersions(gradle, "[1.0.0-rc2, 1.0.0-rc1, 1.0.0-rc0]");
    }

    // Gradle's "+" selector picks the final 1.0.0 over its RCs, and Gradle's range-matching
    // treats `1.0.0-rc1` as not strictly below `1.0.0`, so once the walk reaches the final
    // release the RCs below it are hidden. This matches what a revapi user typically wants:
    // compare against the latest stable, not its pre-release predecessors.
    @Test
    void pre_releases_are_hidden_once_the_walk_reaches_a_final_release(
            GradleInvoker gradle, MavenRepo repo) {
        publish(repo, "1.0.0-rc0", "1.0.0-rc1", "1.0.0", "1.0.1");
        assertOldVersions(gradle, "[1.0.1, 1.0.0]");
    }

    @Test
    void v_prefixed_versions_are_passed_through_unchanged(GradleInvoker gradle, MavenRepo repo) {
        publish(repo, "v1.2.3");
        assertOldVersions(gradle, "[v1.2.3]");
    }

    private static void publish(MavenRepo repo, String... versions) {
        for (String version : versions) {
            repo.publish(MavenArtifact.of("%s:%s:%s".formatted(GROUP, NAME, version)));
        }
    }

    private static void assertOldVersions(GradleInvoker gradle, String expected) {
        assertThat(gradle.withArgs("printOldVersions").buildsSuccessfully())
                .output()
                .contains("OLD_VERSIONS=" + expected);
    }
}
