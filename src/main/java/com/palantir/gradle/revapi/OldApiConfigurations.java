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

import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Version;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;

final class OldApiConfigurations {
    private OldApiConfigurations() {}

    static Set<File> resolveOldConfiguration(Project project, GroupNameVersion groupNameVersion, boolean transitive)
            throws CouldNotResolveOldApiException {

        Dependency oldApiDependency = project.getDependencies().create(groupNameVersion.asString());

        Configuration oldApiConfiguration = project.getConfigurations().detachedConfiguration();
        oldApiConfiguration.getDependencies().add(oldApiDependency);
        oldApiConfiguration.setTransitive(transitive);

        return PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(
                project, () -> resolveConfigurationUnlessMissingJars(groupNameVersion.version(), oldApiConfiguration));
    }

    private static Set<File> resolveConfigurationUnlessMissingJars(Version oldVersion, Configuration configuration)
            throws CouldNotResolveOldApiException {

        Set<? extends DependencyResult> allDependencies =
                configuration.getIncoming().getResolutionResult().getAllDependencies();

        List<Throwable> resolutionFailures = allDependencies.stream()
                .filter(dependencyResult -> dependencyResult instanceof UnresolvedDependencyResult)
                .map(dependencyResult -> (UnresolvedDependencyResult) dependencyResult)
                .map(UnresolvedDependencyResult::getFailure)
                .collect(Collectors.toList());

        if (resolutionFailures.isEmpty()) {
            return configuration.resolve();
        }

        throw new CouldNotResolveOldApiException(oldVersion, resolutionFailures);
    }

    static final class CouldNotResolveOldApiException extends Exception {
        private final Version version;
        private final List<Throwable> resolutionFailures;

        CouldNotResolveOldApiException(Version version, List<Throwable> resolutionFailures) {
            this.version = version;
            this.resolutionFailures = resolutionFailures;
        }

        @Override
        public String getMessage() {
            return "We tried version " + version.asString() + " but it failed with errors:\n\n"
                    + ExceptionMessages.joined(resolutionFailures);
        }
    }
}
