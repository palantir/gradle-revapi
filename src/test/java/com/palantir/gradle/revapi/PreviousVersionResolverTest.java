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
import com.palantir.gradle.testing.maven.MavenRepo;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache("previous-version resolution temporarily changes the project group")
class PreviousVersionResolverTest {

    @BeforeEach
    void beforeEach(RootProject rootProject, MavenRepo mavenRepo) {
        rootProject.settingsGradle().rootProjectName("library");
        rootProject
                .buildGradle()
                .plugins()
                .add(TestConstants.PLUGIN_NAME)
                .add("java-library")
                .add("maven-publish");
        rootProject.buildGradle().append("""
            group = 'com.palantir.test'
            version = '1.0.0'

            repositories {
                maven { url uri('%s') }
            }

            publishing {
                publications {
                    publication(MavenPublication) {
                        from components.java
                    }
                }
                repositories {
                    maven { url uri('%s') }
                }
            }

            tasks.register('printOldVersions') {
                doLast {
                    println "OLD_VERSIONS=" + revapi.oldVersions.get()
                }
            }
            """, mavenRepo.path(), mavenRepo.path());
    }

    @Test
    void resolves_latest_published_version_before_current_version(GradleInvoker gradle, RootProject rootProject) {
        publishVersion(gradle, rootProject, "1.0.0");
        publishVersion(gradle, rootProject, "1.1.0");
        publishVersion(gradle, rootProject, "1.2.0");

        assertThat(gradle.withArgs("printOldVersions").buildsSuccessfully())
                .output()
                .contains("OLD_VERSIONS=[1.1.0]");
    }

    @Test
    void returns_nothing_when_there_are_no_previous_versions(GradleInvoker gradle) {
        assertThat(gradle.withArgs("printOldVersions").buildsSuccessfully())
                .output()
                .contains("OLD_VERSIONS=[]");
    }

    @Test
    void ignores_release_candidates_when_current_version_is_a_release_candidate(
            GradleInvoker gradle, RootProject rootProject) {
        publishVersion(gradle, rootProject, "1.0.0");
        publishVersion(gradle, rootProject, "1.1.0-rc2");
        setVersion(rootProject, "1.1.0-rc3");

        assertThat(gradle.withArgs("printOldVersions").buildsSuccessfully())
                .output()
                .contains("OLD_VERSIONS=[1.0.0]");
    }

    private static void publishVersion(GradleInvoker gradle, RootProject rootProject, String version) {
        setVersion(rootProject, version);
        gradle.withArgs("publish").buildsSuccessfully();
    }

    private static void setVersion(RootProject rootProject, String version) {
        rootProject.buildGradle().edit(text -> text.replaceFirst("version = '[^']+'", "version = '" + version + "'"));
    }
}
