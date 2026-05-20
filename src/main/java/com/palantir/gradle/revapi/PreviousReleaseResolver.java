/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.provider.Provider;

final class PreviousReleaseResolver {

    private static final int VERSIONS_TO_RETURN = 3;

    private PreviousReleaseResolver() {}

    static Provider<List<String>> resolve(Project project, Provider<String> group, Provider<String> name) {
        return group.zip(name, (groupValue, nameValue) -> Stream.iterate(
                        resolveOne(project, groupValue, nameValue, "+"),
                        Optional::isPresent,
                        previous -> resolveOne(project, groupValue, nameValue, "(," + previous.get() + ")"))
                .limit(VERSIONS_TO_RETURN)
                .map(Optional::get)
                .toList());
    }

    private static Optional<String> resolveOne(Project project, String group, String name, String selector) {
        Dependency dependency = project.getDependencies().create(group + ":" + name + ":" + selector);
        Configuration probe = project.getConfigurations().detachedConfiguration(dependency);
        probe.setTransitive(false);
        probe.setCanBeConsumed(false);
        try {
            return probe.getIncoming().getResolutionResult().getAllComponents().stream()
                    .map(ResolvedComponentResult::getModuleVersion)
                    .filter(moduleVersionIdentifier -> moduleVersionIdentifier != null
                            && group.equals(moduleVersionIdentifier.getGroup())
                            && name.equals(moduleVersionIdentifier.getName()))
                    .map(ModuleVersionIdentifier::getVersion)
                    .findFirst();
        } catch (RuntimeException _e) {
            return Optional.empty();
        }
    }
}
