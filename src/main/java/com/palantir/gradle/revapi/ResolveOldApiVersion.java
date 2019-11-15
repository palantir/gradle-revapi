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

import com.palantir.gradle.revapi.OldApiConfigurations.CouldNotResolveOldApiException;
import com.palantir.gradle.revapi.config.GradleRevapiConfig;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Version;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("Duplicates")
final class ResolveOldApiVersion {
    private static final Logger log = LoggerFactory.getLogger(ResolveOldApiVersion.class);

    private ResolveOldApiVersion() { }

    public static Provider<Version> resolveOldApiVersionProvider(
            Project project,
            RevapiExtension extension,
            ConfigManager configManager) {

        return GradleUtils.memoisedProvider(project, () ->
                resolveOldApiAcrossAllOldVersions(project, extension, configManager.fromFileOrEmptyIfDoesNotExist()));
    }

    private static Version resolveOldApiAcrossAllOldVersions(
            Project project,
            RevapiExtension extension,
            GradleRevapiConfig config) {

        Map<Version, CouldNotResolveOldApiException> exceptionsPerVersion = new LinkedHashMap<>();
        for (String oldVersionString : extension.getOldVersions().get()) {
            GroupNameVersion oldGroupNameVersion = possiblyReplacedOldVersionFor(
                    config,
                    extension.oldGroupAndName()
                            .get()
                            .withVersion(Version.fromString(oldVersionString)));

            try {
                resolveOldApiWithVersion(project, oldGroupNameVersion);
                if (!exceptionsPerVersion.isEmpty()) {
                    log.warn("{} has successfully resolved. At first we tried to use versions {}, however they all "
                            + "failed to resolve with these errors:\n\n{}",
                            oldGroupNameVersion.asString(),
                            exceptionsPerVersion.keySet().stream().map(Version::asString).collect(Collectors.toList()),
                            ExceptionMessages.joined(exceptionsPerVersion.values()));
                }
                return oldGroupNameVersion.version();
            } catch (CouldNotResolveOldApiException e) {
                exceptionsPerVersion.put(oldGroupNameVersion.version(), e);
            }
        }

        throw new IllegalStateException(ExceptionMessages.failedToResolve(
                project,
                ExceptionMessages.joined(exceptionsPerVersion.values())));
    }

    private static void resolveOldApiWithVersion(Project project, GroupNameVersion groupNameVersion)
            throws CouldNotResolveOldApiException {

        Dependency oldApiDependency = project.getDependencies().create(groupNameVersion.asString());

        Configuration oldApiConfiguration = OldApiConfigurations.configuration(
                project,
                oldApiDependency,
                "revapiResolveOldApi_" + groupNameVersion.version().asString(),
                "Just the previously published version of this project");

        oldApiConfiguration.setTransitive(false);

        PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(project, () ->
                OldApiConfigurations.resolveConfigurationUnlessMissingJars(
                        groupNameVersion.version(),
                        oldApiConfiguration));
    }

    private static GroupNameVersion possiblyReplacedOldVersionFor(
            GradleRevapiConfig config,
            GroupNameVersion groupNameVersion) {

        Version possiblyReplacedVersion = config
                .versionOverrideFor(groupNameVersion)
                .orElseGet(groupNameVersion::version);

        return GroupNameVersion.builder()
                .from(groupNameVersion)
                .version(possiblyReplacedVersion)
                .build();
    }
}
