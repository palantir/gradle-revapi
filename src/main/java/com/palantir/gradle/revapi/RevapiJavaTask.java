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
import com.palantir.gradle.revapi.config.AcceptedBreak;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Version;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    final Property<ConfigManager> configManager() {
        return configManager;
    }

    public final Property<Configuration> newApiDependencyJars() {
        return newApiDependencyJars;
    }

    public final Property<FileCollection> newApiJars() {
        return newApiJars;
    }

    protected final void runRevapi(RevapiConfig taskSpecificConfigJson) throws Exception {
        API oldApi = resolveOldApiAcrossAllOldVersions();
        API newApi = newApi();

        log.info("Old API: {}", oldApi);
        log.info("New API: {}", newApi);

        Revapi revapi = Revapi.builder()
                .withAllExtensionsFromThreadContextClassLoader()
                .withAnalyzers(JavaApiAnalyzer.class)
                .withReporters(TextReporter.class)
                .withTransforms(CheckWhitelist.class)
                .build();

        RevapiConfig revapiConfig = RevapiConfig.mergeAll(
                RevapiConfig.defaults(oldApi, newApi),
                taskSpecificConfigJson,
                revapiIgnores(),
                ConjureProjectFilters.forProject(getProject()));

        log.info("revapi config:\n{}", revapiConfig.configAsString());

        try (AnalysisResult analysisResult = revapi.analyze(AnalysisContext.builder()
                .withOldAPI(oldApi)
                .withNewAPI(newApi)
                // https://revapi.org/modules/revapi-java/extensions/java.html
                .withConfigurationFromJSON(revapiConfig.configAsString())
                .build())) {
            analysisResult.throwIfFailed();
        }
    }

    private RevapiConfig revapiIgnores() {
        Set<AcceptedBreak> acceptedBreaks = configManager.get()
                .fromFileOrEmptyIfDoesNotExist()
                .acceptedBreaksFor(getExtension().oldGroupAndName());

        return RevapiConfig.empty()
                .withIgnoredBreaks(acceptedBreaks);
    }

    private API newApi() {
        List<FileArchive> newApiJar = toFileArchives(newApiJars);
        List<FileArchive> newApiDependencies = toFileArchives(newApiDependencyJars.get().resolve()
                .stream()
                .filter(File::isFile)
                .collect(Collectors.toSet()));

        return API.builder()
                .addArchives(newApiJar)
                .addSupportArchives(newApiDependencies)
                .build();
    }

    private API resolveOldApiAcrossAllOldVersions() {
        RevapiExtension revapiExtension = getExtension();

        List<String> olderVersions = revapiExtension.getOlderVersions().get();

        Map<String, CouldNotResolvedOldApiException> exceptionsPerVersion = new LinkedHashMap<>();
        for (String olderVersion : olderVersions) {
            try {
                API oldApi = resolveOldApi(revapiExtension, olderVersion);
                if (!exceptionsPerVersion.isEmpty()) {
                    log.warn(olderVersion + " has successfully resolved. At first we tried to use versions "
                            + exceptionsPerVersion.keySet() + ", however they all failed to resolve, with these "
                            + "errors:\n\n" + ExceptionMessages.joined(exceptionsPerVersion.values()));
                }
                return oldApi;
            } catch (CouldNotResolvedOldApiException e) {
                exceptionsPerVersion.put(olderVersion, e);
            }
        }

        throw new IllegalStateException(ExceptionMessages.failedToResolve(getProject(),
                ExceptionMessages.joined(exceptionsPerVersion.values())));
    }

    private API resolveOldApi(RevapiExtension revapiExtension, String oldVersion)
            throws CouldNotResolvedOldApiException {

        GroupNameVersion groupNameVersion = possiblyReplacedOldVersionFor(GroupNameVersion.builder()
                .groupAndName(revapiExtension.oldGroupAndName())
                .version(Version.fromString(oldVersion))
                .build());
        Dependency oldApiDependency = getProject().getDependencies().create(groupNameVersion.asString());

        Configuration oldApiDepsConfiguration = oldApiConfiguration(oldApiDependency, "revapiOldApiDeps" + oldVersion,
                "The dependencies of the previously published version of this project");

        Configuration oldApiConfiguration = oldApiConfiguration(oldApiDependency, "revapiOldApi" + oldVersion,
                "Just the previously published version of this project");
        oldApiConfiguration.setTransitive(false);

        try {
            // When the version of the local java project is higher than the old published dependency and has the same
            // group and name, gradle silently replaces the published external dependency with the project dependency
            // (see https://discuss.gradle.org/t/fetching-the-previous-version-of-a-projects-jar/8571). This happens on
            // tag builds, and would cause the publish to fail. Instead we, change the group for just this thread
            // while resolving these dependencies so the switching out doesnt happen.
            Set<File> oldOnlyJar = PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(getProject(), () ->
                    resolveConfigurationUnlessMissingJars(groupNameVersion.version(), oldApiConfiguration));

            Set<File> oldWithDeps = PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(
                    getProject(),
                    oldApiDepsConfiguration::resolve);

            Set<File> oldJustDeps = Sets.difference(oldWithDeps, oldOnlyJar);

            return API.builder()
                    .addArchives(toFileArchives(oldOnlyJar))
                    .addSupportArchives(toFileArchives(oldJustDeps))
                    .build();
        } finally {
            getProject().getConfigurations().remove(oldApiDepsConfiguration);
            getProject().getConfigurations().remove(oldApiConfiguration);
        }

    }

    private GroupNameVersion possiblyReplacedOldVersionFor(GroupNameVersion groupNameVersion) {
        Version possiblyReplacedVersion = configManager.get().fromFileOrEmptyIfDoesNotExist()
                .versionOverrideFor(groupNameVersion)
                .orElse(groupNameVersion.version());
        return GroupNameVersion.builder()
                .from(groupNameVersion)
                .version(possiblyReplacedVersion)
                .build();
    }

    private Set<File> resolveConfigurationUnlessMissingJars(Version oldVersion, Configuration configuration)
            throws CouldNotResolvedOldApiException {

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

        throw new CouldNotResolvedOldApiException(oldVersion, resolutionFailures);
    }

    private static final class CouldNotResolvedOldApiException extends Exception {
        private final Version version;
        private final List<Throwable> resolutionFailures;

        CouldNotResolvedOldApiException(
                Version version,
                List<Throwable> resolutionFailures) {
            this.version = version;
            this.resolutionFailures = resolutionFailures;
        }

        @Override
        public String getMessage() {
            return "We tried version " + version.asString() + " but it failed with errors:\n\n"
                    + ExceptionMessages.joined(resolutionFailures);
        }
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
