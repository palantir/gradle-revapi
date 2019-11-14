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

import com.palantir.gradle.revapi.config.GroupAndName;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Version;
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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Purposefully not cached as this task depends on external mutable state (which versions of the old api jar are
 * published to the remote repository), so we want to check it's the same every time.
 */
@SuppressWarnings("Duplicates")
public class RevapiResolveOldApiVersion extends DefaultTask {
    private static final Logger log = LoggerFactory.getLogger(RevapiResolveOldApiVersion.class);

    private final Property<ConfigManager> configManager = getProject().getObjects().property(ConfigManager.class);
    private final ListProperty<String> oldVersions = getProject().getObjects().listProperty(String.class);
    private final Property<GroupAndName> oldGroupAndName = getProject().getObjects().property(GroupAndName.class);
    private final RegularFileProperty resolvedApiVersionFile = getProject().getObjects().fileProperty();

    final Property<ConfigManager> configManager() {
        return configManager;
    }

    public final ListProperty<String> getOldVersions() {
        return oldVersions;
    }

    public final Property<GroupAndName> getOldGroupAndName() {
        return oldGroupAndName;
    }

    @OutputFile
    public final RegularFileProperty getResolvedApiVersionFile() {
        return resolvedApiVersionFile;
    }

    @TaskAction
    public final void resolveOldApi() {
        Version version = resolveOldApiAcrossAllOldVersions();
        resolvedApiVersionFile.getAsFile();
        ResolvedOldApiVersionFile resolvedOldApiVersionFile = ResolvedOldApiVersionFile.fromVersion(version);
    }

    private Version resolveOldApiAcrossAllOldVersions() {
        Map<Version, CouldNotResolveOldApiException> exceptionsPerVersion = new LinkedHashMap<>();
        for (String oldVersionString : oldVersions.get()) {
            Version oldVersion = Version.fromString(oldVersionString);
            try {
                tryResolveOldApiWithVersion(oldVersion);
                if (!exceptionsPerVersion.isEmpty()) {
                    log.warn("{} has successfully resolved. At first we tried to use versions {}, however they all "
                            + "failed to resolve with these errors:\n\n{}",
                            oldVersion,
                            exceptionsPerVersion.keySet().stream().map(Version::asString).collect(Collectors.toList()),
                            ExceptionMessages.joined(exceptionsPerVersion.values()));
                }
                return oldVersion;
            } catch (CouldNotResolveOldApiException e) {
                exceptionsPerVersion.put(oldVersion, e);
            }
        }

        throw new IllegalStateException(ExceptionMessages.failedToResolve(getProject(),
                ExceptionMessages.joined(exceptionsPerVersion.values())));
    }

    private void tryResolveOldApiWithVersion(Version oldVersion) throws CouldNotResolveOldApiException {
        GroupNameVersion groupNameVersion = possiblyReplacedOldVersionFor(GroupNameVersion.builder()
                .groupAndName(oldGroupAndName.get())
                .version(oldVersion)
                .build());

        Dependency oldApiDependency = getProject().getDependencies().create(groupNameVersion.asString());

        Configuration oldApiConfiguration = oldApiConfiguration(oldApiDependency, "revapiOldApi" + oldVersion,
                "Just the previously published version of this project");
        oldApiConfiguration.setTransitive(false);

        // When the version of the local java project is higher than the old published dependency and has the same
        // group and name, gradle silently replaces the published external dependency with the project dependency
        // (see https://discuss.gradle.org/t/fetching-the-previous-version-of-a-projects-jar/8571). This happens on
        // tag builds, and would cause the publish to fail. Instead we, change the group for just this thread
        // while resolving these dependencies so the switching out doesnt happen.
        PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(getProject(), () ->
                tryResolveConfigurationUnlessMissingJars(groupNameVersion.version(), oldApiConfiguration));
    }

    private GroupNameVersion possiblyReplacedOldVersionFor(GroupNameVersion groupNameVersion) {
        Version possiblyReplacedVersion = configManager.get().fromFileOrEmptyIfDoesNotExist()
                .versionOverrideFor(groupNameVersion)
                .orElseGet(groupNameVersion::version);

        return GroupNameVersion.builder()
                .from(groupNameVersion)
                .version(possiblyReplacedVersion)
                .build();
    }

    private void tryResolveConfigurationUnlessMissingJars(Version oldVersion, Configuration configuration)
            throws CouldNotResolveOldApiException {

        Set<? extends DependencyResult> allDependencies = configuration.getIncoming()
                .getResolutionResult()
                .getAllDependencies();

        List<Throwable> resolutionFailures = allDependencies.stream()
                .filter(dependencyResult -> dependencyResult instanceof UnresolvedDependencyResult)
                .map(dependencyResult -> (UnresolvedDependencyResult) dependencyResult)
                .map(UnresolvedDependencyResult::getFailure)
                .collect(Collectors.toList());

        if (resolutionFailures.isEmpty()) {
            return;
        }

        throw new CouldNotResolveOldApiException(oldVersion, resolutionFailures);
    }

    private static final class CouldNotResolveOldApiException extends Exception {
        private final Version version;
        private final List<Throwable> resolutionFailures;

        CouldNotResolveOldApiException(
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
}
