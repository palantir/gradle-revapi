/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import java.io.Reader;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.model.MethodElement;
import org.revapi.java.spi.JavaElement;

public final class ImmutablesFilter implements DifferenceTransform<JavaElement> {
    private static final String EXTENSION_ID = "gradle-revapi.immutables";
    public static final RevapiConfig CONFIG = RevapiConfig.empty().withExtension(EXTENSION_ID);

    private static final String ABSTRACT_METHOD_ADDED = "java.method.abstractMethodAdded";
    private static final String RETURN_TYPE_CHANGED = "java.method.returnTypeChanged";

    private static final Pattern[] DIFFERENCE_CODE_PATTERNS = Stream.of(ABSTRACT_METHOD_ADDED, RETURN_TYPE_CHANGED)
            .map(Pattern::compile)
            .toArray(Pattern[]::new);

    @Override
    public String getExtensionId() {
        return EXTENSION_ID;
    }

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return DIFFERENCE_CODE_PATTERNS;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {}

    @Nullable
    @Override
    public Difference transform(
            @Nullable JavaElement oldElement, @Nullable JavaElement newElement, @Nonnull Difference difference) {
        switch (difference.code) {
            case ABSTRACT_METHOD_ADDED:
                if (isMethodInImmutablesClass(oldElement) || isMethodInImmutablesClass(newElement)) {
                    // if the element is an immutables class, ignore the difference
                    return null;
                }

                // otherwise return it as is
                return difference;
        }

        return difference;
    }

    private static boolean isMethodInImmutablesClass(JavaElement javaElement) {
        if (javaElement == null) {
            return false;
        }

        if (!(javaElement instanceof MethodElement)) {
            return false;
        }

        MethodElement methodElement = (MethodElement) javaElement;

        return methodElement.getDeclaringElement().getEnclosingElement().getAnnotationMirrors().stream()
                .anyMatch(annotationMirror ->
                        annotationMirror.toString().equals("@org.immutables.value.Value.Immutable"));
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void close() {}
}
