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
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ResolveOldApi {
    private static final Logger log = LoggerFactory.getLogger(ResolveOldApi.class);

    private ResolveOldApi() {}

    public static Provider<Optional<OldApi>> oldApiProvider(
            Project project, RevapiExtension extension, ConfigManager configManager) {

        return GradleUtils.memoisedProvider(
                project,
                () -> resolveOldApiAcrossAllOldVersions(
                        project, extension, configManager.fromFileOrEmptyIfDoesNotExist()));
    }

    private static Optional<OldApi> resolveOldApiAcrossAllOldVersions(
            Project project, RevapiExtension extension, GradleRevapiConfig config) {

        List<String> oldVersionStrings = extension.getOldVersions().get();

        if (oldVersionStrings.isEmpty()) {
            return Optional.empty();
        }

        Map<Version, CouldNotResolveOldApiException> exceptionsPerVersion = new LinkedHashMap<>();
        for (String oldVersionString : oldVersionStrings) {
            GroupNameVersion oldGroupNameVersion = possiblyReplacedOldVersionFor(
                    config, extension.oldGroupAndName().get().withVersion(Version.fromString(oldVersionString)));

            try {
                OldApi oldApi = resolveOldApiWithVersion(project, oldGroupNameVersion);
                if (!exceptionsPerVersion.isEmpty()) {
                    log.warn(
                            "{} has successfully resolved. At first we tried to use versions {}, however they all "
                                    + "failed to resolve with these errors:\n\n{}",
                            oldGroupNameVersion.asString(),
                            exceptionsPerVersion.keySet().stream()
                                    .map(Version::asString)
                                    .collect(Collectors.toList()),
                            ExceptionMessages.joined(exceptionsPerVersion.values()));
                }
                return Optional.of(oldApi);
            } catch (CouldNotResolveOldApiException e) {
                exceptionsPerVersion.put(oldGroupNameVersion.version(), e);
            }
        }

        throw new IllegalStateException(
                ExceptionMessages.failedToResolve(project, ExceptionMessages.joined(exceptionsPerVersion.values())));
    }

    private static OldApi resolveOldApiWithVersion(Project project, GroupNameVersion groupNameVersion)
            throws CouldNotResolveOldApiException {

        Set<File> oldOnlyJar = OldApiConfigurations.resolveOldConfiguration(project, groupNameVersion, false);
        Set<File> oldWithDeps = OldApiConfigurations.resolveOldConfiguration(project, groupNameVersion, true);

        Set<File> oldJustDeps = new HashSet<>(oldWithDeps);
        oldJustDeps.removeAll(oldWithDeps);

        return OldApi.builder().jars(oldOnlyJar).dependencyJars(oldJustDeps).build();
    }

    private static GroupNameVersion possiblyReplacedOldVersionFor(
            GradleRevapiConfig config, GroupNameVersion groupNameVersion) {

        Version possiblyReplacedVersion =
                config.versionOverrideFor(groupNameVersion).orElseGet(groupNameVersion::version);

        return GroupNameVersion.builder()
                .from(groupNameVersion)
                .version(possiblyReplacedVersion)
                .build();
    }

    @Value.Immutable
    interface OldApi {
        Set<File> jars();

        Set<File> dependencyJars();

        class Builder extends ImmutableOldApi.Builder {}

        static Builder builder() {
            return new Builder();
        }
    }
}
