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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Sets;
import com.palantir.gradle.revapi.config.GroupAndName;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Version;
import java.io.File;
import java.io.IOException;
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
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("Duplicates")
public abstract class RevapiResolveOldApiTask extends DefaultTask {
    private static final Logger log = LoggerFactory.getLogger(RevapiResolveOldApiTask.class);

    private final Property<ConfigManager> configManager =
            getProject().getObjects().property(ConfigManager.class);

    private final Property<GroupAndName> oldGroupAndName =
            getProject().getObjects().property(GroupAndName.class);

    private final ListProperty<String> oldVersions =
            getProject().getObjects().listProperty(String.class);

    private final Property<File> outputFile =
            getProject().getObjects().property(File.class);

    @Nested
    final Property<ConfigManager> configManager() {
        return configManager;
    }

    @Input
    final Property<GroupAndName> oldGroupAndName() {
        return oldGroupAndName;
    }

    @Input
    final ListProperty<String> oldVersions() {
        return oldVersions;
    }

    @OutputFile
    final Property<File> outputFile() {
        return outputFile;
    }

    @TaskAction
    public final void resolveAndWriteOutOldApi() {
        resolveOldApiAcrossAllOldVersions().writeToFile(outputFile.get());
    }

    private ResolvedOldApi resolveOldApiAcrossAllOldVersions() {
        List<String> olderVersions = oldVersions.get();

        Map<String, CouldNotResolvedOldApiException> exceptionsPerVersion = new LinkedHashMap<>();
        for (String olderVersion : olderVersions) {
            try {
                ResolvedOldApi oldApi = resolveOldApi(olderVersion);
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

        String allVersionedErrors = exceptionsPerVersion.entrySet().stream()
                .map(entry -> "We tried version " + entry.getKey() + " but it failed with errors:\n\n"
                        + ExceptionMessages.joined(entry.getValue().resolutionFailures()))
                .collect(Collectors.joining("\n\n"));

        throw new IllegalStateException(ExceptionMessages.failedToResolve(getProject(), allVersionedErrors));
    }

    private ResolvedOldApi resolveOldApi(String oldVersion)
            throws CouldNotResolvedOldApiException {

        GroupNameVersion oldGroupNameVersion = possiblyReplacedOldVersionFor(GroupNameVersion.builder()
                .groupAndName(oldGroupAndName.get())
                .version(Version.fromString(oldVersion))
                .build());

        Dependency oldApiDependency = getProject().getDependencies().create(oldGroupNameVersion.asString());

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
        Set<File> oldOnlyJar = PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(
                getProject(), () -> resolveConfigurationUnlessMissingJars(oldApiConfiguration));

        Set<File> oldWithDeps = PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(
                getProject(), oldApiDepsConfiguration::resolve);

        Set<File> oldJustDeps = Sets.difference(oldWithDeps, oldOnlyJar);

        return ResolvedOldApi.builder()
                .directJars(oldOnlyJar)
                .transitiveJars(oldJustDeps)
                .version(oldGroupNameVersion.version())
                .build();
    }

    private Set<File> resolveConfigurationUnlessMissingJars(Configuration configuration)
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

        throw new CouldNotResolvedOldApiException(resolutionFailures);
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

    class CouldNotResolvedOldApiException extends Exception {
        private final List<Throwable> resolutionFailures;

        CouldNotResolvedOldApiException(List<Throwable> resolutionFailures) {
            this.resolutionFailures = resolutionFailures;
        }

        public List<Throwable> resolutionFailures() {
            return resolutionFailures;
        }
    }

    @Value.Immutable
    @JsonDeserialize(as = ImmutableResolvedOldApi.class)
    interface ResolvedOldApi {
        ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        Version version();
        Set<File> directJars();
        Set<File> transitiveJars();

        class Builder extends ImmutableResolvedOldApi.Builder { }

        static Builder builder() {
            return new Builder();
        }

        default void writeToFile(File file) {
            try {
                OBJECT_MAPPER.writeValue(file, this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        static ResolvedOldApi readFromFile(File file) {
            try {
                return OBJECT_MAPPER.readValue(file, ResolvedOldApi.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
