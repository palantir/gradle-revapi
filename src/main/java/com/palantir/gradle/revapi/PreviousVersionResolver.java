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

import com.palantir.gradle.revapi.config.GroupAndName;
import java.util.List;
import java.util.regex.Pattern;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.provider.Provider;

final class PreviousVersionResolver {
    private static final Pattern RELEASE_CANDIDATE =
            Pattern.compile("(?:^|[._-])rc(?:[._-]?\\d+)?(?:$|[._+-])", Pattern.CASE_INSENSITIVE);

    private PreviousVersionResolver() {}

    static Provider<List<String>> previousVersion(Project project, Provider<GroupAndName> groupAndNameProvider) {
        return GradleUtils.memoisedProvider(project, () -> {
            GroupAndName groupAndName = groupAndNameProvider.get();
            String currentVersion = project.getVersion().toString();
            return PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread(
                    project, () -> resolvePreviousVersion(project, groupAndName, currentVersion));
        });
    }

    private static List<String> resolvePreviousVersion(
            Project project, GroupAndName groupAndName, String currentVersion) {
        Configuration configuration = project.getConfigurations().detachedConfiguration();
        configuration.setTransitive(false);
        configuration
                .getDependencies()
                .add(project.getDependencies().create(groupAndName.asString() + ":(," + currentVersion + ")"));
        configuration
                .getResolutionStrategy()
                .componentSelection(componentSelectionRules -> componentSelectionRules.all(componentSelection -> {
                    ModuleComponentIdentifier candidate = componentSelection.getCandidate();
                    if (candidate.getGroup().equals(groupAndName.group())
                            && candidate.getModule().equals(groupAndName.name())
                            && RELEASE_CANDIDATE.matcher(candidate.getVersion()).find()) {
                        componentSelection.reject("Release candidates are not production releases");
                    }
                }));

        return configuration
                .getIncoming()
                .artifactView(viewConfiguration -> viewConfiguration.setLenient(true))
                .getArtifacts()
                .getResolvedArtifacts()
                .get()
                .stream()
                .map(ResolvedArtifactResult::getId)
                .map(artifactIdentifier -> artifactIdentifier.getComponentIdentifier())
                .filter(ModuleComponentIdentifier.class::isInstance)
                .map(ModuleComponentIdentifier.class::cast)
                .map(ModuleComponentIdentifier::getVersion)
                .findFirst()
                .map(List::of)
                .orElseGet(List::of);
    }
}
