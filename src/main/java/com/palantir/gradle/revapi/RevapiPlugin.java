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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.gradle.revapi.ResolveOldApi.OldApi;
import com.palantir.gradle.revapi.config.AcceptedBreak;
import com.palantir.gradle.revapi.config.GroupAndName;
import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

public final class RevapiPlugin implements Plugin<Project> {
    public static final String VERSION_OVERRIDE_TASK_NAME = "revapiVersionOverride";
    public static final String ACCEPT_BREAK_TASK_NAME = "revapiAcceptBreak";
    public static final String ACCEPT_ALL_BREAKS_TASK_NAME = "revapiAcceptAllBreaks";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(JavaPlugin.class);

        RevapiExtension extension = project.getExtensions().create("revapi", RevapiExtension.class, project);

        ConfigManager configManager = new ConfigManager(configFile(project));

        Provider<Optional<OldApi>> maybeOldApi = ResolveOldApi.oldApiProvider(project, extension, configManager);
        Spec<Task> oldApiIsPresent = _task -> maybeOldApi.get().isPresent();

        TaskProvider<RevapiAnalyzeTask> analyzeTask = project.getTasks()
                .register("revapiAnalyze", RevapiAnalyzeTask.class, task -> {
                    Configuration revapiNewApi = project.getConfigurations().create("revapiNewApi", conf -> {
                        conf.extendsFrom(
                                project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
                        conf.setCanBeConsumed(false);
                    });

                    Configuration revapiNewApiElements = project.getConfigurations()
                            .create("revapiNewApiElements", conf -> {
                                conf.extendsFrom(project.getConfigurations()
                                        .getByName(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME));
                                conf.setCanBeConsumed(false);
                            });

                    task.getAcceptedBreaks().set(acceptedBreaks(project, configManager, extension.oldGroupAndName()));

                    // TODO(dsanduleac): probably not necessary to have provider anymore
                    // Note: this should propagate the dependency on the necessary tasks to build the other projects
                    task.getNewApiJars().set(GradleUtils.memoisedProvider(project, () -> {
                        // TODO(dsanduleac): don't necessarily want jar, could also get classes dir (cheaper)
                        FileCollection thisJarFile = project.getTasks()
                                .withType(Jar.class)
                                .getByName(JavaPlugin.JAR_TASK_NAME)
                                .getOutputs()
                                .getFiles();

                        FileCollection otherProjectsOutputs = revapiNewApiElements
                                .getIncoming()
                                .artifactView(vc -> vc.componentFilter(ci -> ci instanceof ProjectComponentIdentifier))
                                .getFiles();
                        return thisJarFile.plus(otherProjectsOutputs);
                    }));
                    task.getNewApiDependencyJars()
                            .set(revapiNewApi.minus(task.getNewApiJars().get()));
                    task.getOldApiJars()
                            .set(maybeOldApi.map(oldApi ->
                                    oldApi.map(OldApi::jars).map(project::files).orElseGet(project::files)));
                    task.getOldApiDependencyJars().set(maybeOldApi.map(oldApi -> oldApi.map(OldApi::dependencyJars)
                            .map(project::files)
                            .orElseGet(project::files)));

                    task.getAnalysisResultsFile().set(new File(project.getBuildDir(), "revapi/revapi-results.json"));

                    task.onlyIf(oldApiIsPresent);
                });

        TaskProvider<RevapiReportTask> reportTask = project.getTasks()
                .register("revapi", RevapiReportTask.class, task -> {
                    task.dependsOn(analyzeTask);
                    task.getAnalysisResultsFile().set(analyzeTask.flatMap(RevapiAnalyzeTask::getAnalysisResultsFile));
                    task.getJunitOutputFile().set(junitOutput(project));

                    task.onlyIf(oldApiIsPresent);
                });

        project.getTasks().findByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(reportTask);

        project.getTasks().register(ACCEPT_ALL_BREAKS_TASK_NAME, RevapiAcceptAllBreaksTask.class, task -> {
            task.dependsOn(analyzeTask);

            task.getOldGroupNameVersion().set(project.getProviders().provider(extension::oldGroupNameVersion));
            task.getConfigManager().set(configManager);
            task.getAnalysisResultsFile().set(analyzeTask.flatMap(RevapiAnalyzeTask::getAnalysisResultsFile));
        });

        project.getTasks().register(VERSION_OVERRIDE_TASK_NAME, RevapiVersionOverrideTask.class, task -> {
            task.getConfigManager().set(configManager);
        });

        project.getTasks().register(ACCEPT_BREAK_TASK_NAME, RevapiAcceptBreakTask.class, task -> {
            task.getConfigManager().set(configManager);
        });
    }

    private Provider<Set<AcceptedBreak>> acceptedBreaks(
            Project project, ConfigManager configManager, Provider<GroupAndName> oldGroupAndNameProvider) {

        return GradleUtils.memoisedProvider(
                project,
                () -> configManager.fromFileOrEmptyIfDoesNotExist().acceptedBreaksFor(oldGroupAndNameProvider.get()));
    }

    @VisibleForTesting
    static Provider<Set<Jar>> allJarTasksIncludingDependencies(Project project, Configuration configuration) {
        // Provider so that we don't resolve the configuration at compile time, which is bad for gradle performance
        return GradleUtils.memoisedProvider(
                project, () -> configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                        .map(ComponentResult::getId)
                        .filter(resolvedComponentResult ->
                                resolvedComponentResult instanceof ProjectComponentIdentifier)
                        .map(resolvedComponentResult -> (ProjectComponentIdentifier) resolvedComponentResult)
                        .map(ProjectComponentIdentifier::getProjectPath)
                        .map(project.getRootProject()::project)
                        .flatMap(dependentProject -> dependentProject
                                .getTasks()
                                .withType(Jar.class)
                                .matching(jar -> jar.getName().equals(JavaPlugin.JAR_TASK_NAME))
                                .stream())
                        .collect(Collectors.toSet()));
    }

    private static File configFile(Project project) {
        return new File(project.getRootDir(), ".palantir/revapi.yml");
    }

    private File junitOutput(Project project) {
        Optional<String> circleReportsDir = Optional.ofNullable(System.getenv("CIRCLE_TEST_REPORTS"));
        File reportsDir = circleReportsDir.map(File::new).orElseGet(project::getBuildDir);
        return new File(reportsDir, "junit-reports/revapi/revapi-" + project.getName() + ".xml");
    }
}
