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

import com.google.common.collect.Sets;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Revapi;
import org.revapi.java.JavaApiAnalyzer;
import org.revapi.reporter.text.TextReporter;
import org.revapi.simple.FileArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RevapiJavaTask extends DefaultTask {
    private static final Logger log = LoggerFactory.getLogger(RevapiJavaTask.class);

    private final Property<ConfigManager> configManager =
            getProject().getObjects().property(ConfigManager.class);

    private final Property<Configuration> newApiDependencyJars =
            getProject().getObjects().property(Configuration.class);

    private final Property<FileCollection> newApiJars =
            getProject().getObjects().property(FileCollection.class);

    public final Property<ConfigManager> configManager() {
        return configManager;
    }

    public final Property<Configuration> newApiDependencyJars() {
        return newApiDependencyJars;
    }

    public final Property<FileCollection> newApiJars() {
        return newApiJars;
    }

    protected final void runRevapi(RevapiJsonConfig taskSpecificConfigJson) throws Exception {
        API oldApi = oldApi();
        API newApi = newApi();

        log.info("Old API: {}", oldApi);
        log.info("New API: {}", newApi);

        Revapi revapi = Revapi.builder()
                .withAllExtensionsFromThreadContextClassLoader()
                .withAnalyzers(JavaApiAnalyzer.class)
                .withReporters(TextReporter.class)
                .build();

        String revapiJsonConfig = RevapiJsonConfig.mergeAll(
                RevapiJsonConfig.defaults(oldApi, newApi),
                taskSpecificConfigJson).configAsString();

        try (AnalysisResult analysisResult = revapi.analyze(AnalysisContext.builder()
                .withOldAPI(oldApi)
                .withNewAPI(newApi)
                // https://revapi.org/modules/revapi-java/extensions/java.html
                .withConfigurationFromJSON(revapiJsonConfig)
                .build())) {
            analysisResult.throwIfFailed();
        }
    }

    private API newApi() {
        List<FileArchive> newApiJar = toFileArchives(newApiJars);
        List<FileArchive> newApiDependencies = toFileArchives(newApiDependencyJars.get().resolve());

        return API.builder()
                .addArchives(newApiJar)
                .addSupportArchives(newApiDependencies)
                .build();
    }

    private API oldApi() {
        RevapiExtension revapiExtension = getExtension();

        Dependency oldApiDependency = getProject().getDependencies().create(String.format(
                "%s:%s:%s",
                revapiExtension.getOldGroup().get(),
                revapiExtension.getOldName().get(),
                oldVersion()));

        Configuration oldApiDepsConfiguration = oldApiConfiguration(oldApiDependency, "revapiOldApiDeps",
                "The dependencies of the previously published version of this project");

        Configuration oldApiConfiguration = oldApiConfiguration(oldApiDependency, "revapiOldApi",
                "Just the previously published version of this project");
        oldApiConfiguration.setTransitive(false);

        // When the version of the local java project is higher than the old published dependency and has the same
        // group and name, gradle silently replaces the published external dependency with the project dependency
        // (see https://discuss.gradle.org/t/fetching-the-previous-version-of-a-projects-jar/8571). This happens on
        // tag builds, and would cause the publish to fail. Instead we, change the group for just this thread
        // while resolving these dependencies so the switching out doesnt happen.
        Set<File> oldOnlyJar  = PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(
                getProject(), () -> resolveConfigurationUnlessMissingJars(oldApiConfiguration));

        Set<File> oldWithDeps = PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(
                getProject(), oldApiDepsConfiguration::resolve);

        Set<File> oldJustDeps = Sets.difference(oldWithDeps, oldOnlyJar);

        return API.builder()
                .addArchives(toFileArchives(oldOnlyJar))
                .addSupportArchives(toFileArchives(oldJustDeps))
                .build();

    }

    private String oldVersion() {
        RevapiExtension revapiExtension = getExtension();

        String oldVersion = revapiExtension.getOldVersion().get();
        Optional<String> replacementVersion = configManager.get().fromFileOrEmptyIfDoesNotExist()
                .versionOverrideFor(revapiExtension.oldGroupNameVersion());

        replacementVersion.ifPresent(newVersion -> {
            log.info("Using replacement version {} instead of {} for {}",
                    newVersion,
                    oldVersion,
                    revapiExtension.oldGroupNameVersion());
        });

        return replacementVersion.orElse(oldVersion);
    }

    private Set<File> resolveConfigurationUnlessMissingJars(Configuration configuration) {
        Set<? extends DependencyResult> allDependencies = configuration.getIncoming()
                .getResolutionResult()
                .getAllDependencies();

        List<Throwable> resolutionFailures = allDependencies.stream()
                .filter(dependencyResult -> dependencyResult instanceof UnresolvedDependencyResult)
                .map(dependencyResult -> (UnresolvedDependencyResult) dependencyResult)
                .map(UnresolvedDependencyResult::getFailure)
                .collect(Collectors.toList());

        if (resolutionFailures.isEmpty()) {
            return configuration.resolve();
        }

        String allResolutionFailures = resolutionFailures.stream()
                .map(Throwable::getMessage)
                .collect(Collectors.joining("\n\n"));

        throw new IllegalStateException(ExceptionMessages.failedToResolve(getProject(), allResolutionFailures));
    }

    private Configuration oldApiConfiguration(
            Dependency oldApiDependency,
            String name,
            String description) {
        return getProject().getConfigurations().create(name, conf -> {
            conf.setDescription(description);
            conf.getDependencies().add(oldApiDependency);
            conf.setCanBeConsumed(false);
            conf.setVisible(false);
        });
    }

    private RevapiExtension getExtension() {
        return getProject().getExtensions().getByType(RevapiExtension.class);
    }

    private static List<FileArchive> toFileArchives(Property<FileCollection> fileCollectionProperty) {
        Set<File> files = fileCollectionProperty.get().getFiles();
        return toFileArchives(files);
    }

    private static List<FileArchive> toFileArchives(Set<File> files) {
        return files.stream()
                .map(FileArchive::new)
                .collect(Collectors.toList());
    }
}
