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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleUtilsTest {
    @TempDir
    File tempDir;

    @Test
    void find_all_jar_tasks_from_all_subprojects() {
        Project rootProject = ProjectBuilder.builder().withProjectDir(tempDir).build();
        Project subsubproject = ProjectBuilder.builder()
                .withName("subsubproject")
                .withParent(rootProject)
                .build();
        Project subproject = ProjectBuilder.builder()
                .withName("subproject")
                .withParent(rootProject)
                .build();

        subsubproject.getPluginManager().apply(JavaPlugin.class);

        subproject.getPluginManager().apply(JavaPlugin.class);
        subproject.getDependencies().add("implementation", subproject.project(":" + subsubproject.getName()));

        rootProject.getPluginManager().apply(JavaPlugin.class);
        rootProject.getDependencies().add("implementation", rootProject.project(":" + subproject.getName()));

        Provider<Set<Jar>> jarTasks = RevapiPlugin.allJarTasksIncludingDependencies(
                rootProject,
                rootProject.getConfigurations().named("runtimeClasspath").get());

        assertThat(jarTasks.get())
                .containsExactlyInAnyOrder(
                        (Jar) rootProject.getTasks().getByName("jar"),
                        (Jar) subproject.getTasks().getByName("jar"),
                        (Jar) subsubproject.getTasks().getByName("jar"));
    }
}
