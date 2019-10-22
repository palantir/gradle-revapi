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

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Optional;
import org.gradle.api.Project;

final class ConjureProjectFilters {
    private static final ArrayNode CHECKS_FOR_CLIENT_PROJECTS = RevapiConfig.createArrayNode()
            .add("java.class.removed")
            .add("java.method.removed")
            .add("java.method.parameterTypeChanged");
    private static final ArrayNode SKIP_ALL_CHECKS = RevapiConfig.createArrayNode();

    private ConjureProjectFilters() { }

    public static RevapiConfig forProject(Project project) {
        boolean isConjure = Optional.ofNullable(project.getParent())
                .map(parentProject -> parentProject.getPluginManager().hasPlugin("com.palantir.conjure"))
                .orElse(false);

        if (!isConjure) {
            return RevapiConfig.empty();
        }

        return checksForProjectName(project.getName())
                .map(checks -> RevapiConfig.empty().withExtension(DifferenceCodeWhitelist.EXTENSION_ID, checks))
                .orElseGet(RevapiConfig::empty);
    }

    private static Optional<ArrayNode> checksForProjectName(String projectName) {
        if (projectName.endsWith("-jersey") || projectName.endsWith("-retrofit")) {
            return Optional.of(CHECKS_FOR_CLIENT_PROJECTS);
        }

        if (projectName.endsWith("-undertow")) {
            return Optional.of(SKIP_ALL_CHECKS);
        }

        return Optional.empty();
    }
}
