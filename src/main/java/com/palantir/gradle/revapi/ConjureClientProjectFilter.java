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

import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.collect.ImmutableSet;
import java.io.Reader;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.spi.JavaElement;

public final class ConjureClientProjectFilter implements DifferenceTransform<JavaElement> {
    private static final String EXTENSION_ID = "gradle-revapi.conjure.filter";

    private static final Pattern[] EVERYTHING = {Pattern.compile(".*") };
    private static final Set<String> ALLOWED_CODES = ImmutableSet.of(
            "java.class.removed",
            "java.method.removed",
            "java.method.parameterTypeChanged");

    @Override
    public String getExtensionId() {
        return EXTENSION_ID;
    }

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return EVERYTHING;
    }

    @Nullable
    @Override
    public Difference transform(
            @Nullable JavaElement oldElement,
            @Nullable JavaElement newElement,
            @Nonnull Difference difference) {

        if (ALLOWED_CODES.contains(difference.code)) {
            return difference;
        }

        return null;
    }

    public static RevapiConfig forProject(Project project) {
        boolean isConjure = Optional.ofNullable(project.getParent())
                .map(parentProject -> parentProject.getPluginManager().hasPlugin("com.palantir.conjure"))
                .orElse(false);

        boolean isClientProject = project.getName().endsWith("-jersey") || project.getName().endsWith("-retrofit");

        if (isConjure && isClientProject) {
            return RevapiConfig.empty().withExtension(EXTENSION_ID, NullNode.getInstance());
        }

        return RevapiConfig.empty();
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) { }

    @Override
    public void close() { }
}
