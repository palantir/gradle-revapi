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
import com.palantir.gradle.revapi.config.GroupAndName;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Version;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
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

    private final Property<OldApiDependencyFile.AsOutput> oldApiVersionFile =
            getProject().getObjects().property(OldApiDependencyFile.AsOutput.class);

    final Property<ConfigManager> configManager() {
        return configManager;
    }

    public final ListProperty<String> getOldVersions() {
        return oldVersions;
    }

    public final Property<GroupAndName> getOldGroupAndName() {
        return oldGroupAndName;
    }

    @Nested
    public final Property<OldApiDependencyFile.AsOutput> getOldApiVersionFile() {
        return oldApiVersionFile;
    }

    @TaskAction
    public final void resolveOldApi() {
        oldApiVersionFile.get().write(resolveOldApiAcrossAllOldVersions());
    }

    private GroupNameVersion resolveOldApiAcrossAllOldVersions() {
        Map<Version, CouldNotResolveOldApiException> exceptionsPerVersion = new LinkedHashMap<>();
        for (String oldVersionString : oldVersions.get()) {
            Version oldVersion = Version.fromString(oldVersionString);
            try {
                GroupNameVersion groupNameVersion = resolveOldApiWithVersion(oldVersion);
                if (!exceptionsPerVersion.isEmpty()) {
                    log.warn("{} has successfully resolved. At first we tried to use versions {}, however they all "
                            + "failed to resolve with these errors:\n\n{}",
                            oldVersion,
                            exceptionsPerVersion.keySet().stream().map(Version::asString).collect(Collectors.toList()),
                            ExceptionMessages.joined(exceptionsPerVersion.values()));
                }
                return groupNameVersion;
            } catch (CouldNotResolveOldApiException e) {
                exceptionsPerVersion.put(oldVersion, e);
            }
        }

        throw new IllegalStateException(ExceptionMessages.failedToResolve(getProject(),
                ExceptionMessages.joined(exceptionsPerVersion.values())));
    }

    private GroupNameVersion resolveOldApiWithVersion(Version oldVersion) throws CouldNotResolveOldApiException {
        GroupNameVersion groupNameVersion =
                possiblyReplacedOldVersionFor(oldGroupAndName.get().withVersion(oldVersion));

        Dependency oldApiDependency = getProject().getDependencies().create(groupNameVersion.asString());

        Configuration oldApiConfiguration = OldApiConfigurations.configuration(
                getProject(),
                oldApiDependency,
                "revapiResolveOldApi" + oldVersion,
                "Just the previously published version of this project");

        oldApiConfiguration.setTransitive(false);

        PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(getProject(), () ->
                OldApiConfigurations.resolveConfigurationUnlessMissingJars(
                        groupNameVersion.version(),
                        oldApiConfiguration));

        return groupNameVersion;
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
}
