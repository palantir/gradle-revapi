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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.model.MethodElement;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaElement;

public final class ImmutablesFilter implements DifferenceTransform<JavaElement> {
    private static final String EXTENSION_ID = "gradle-revapi.immutables";
    public static final RevapiConfig CONFIG = RevapiConfig.empty().withExtension(EXTENSION_ID);

    private static final Pattern[] DIFFERENCE_CODE_PATTERNS = Stream.of(
                    Code.METHOD_ABSTRACT_METHOD_ADDED,
                    Code.METHOD_RETURN_TYPE_CHANGED,
                    Code.METHOD_VISIBILITY_REDUCED,
                    Code.METHOD_NOW_ABSTRACT,
                    Code.METHOD_REMOVED)
            .map(Code::code)
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
    public void initialize(@Nonnull AnalysisContext _analysisContext) {}

    @Nullable
    @Override
    public Difference transform(
            @Nullable JavaElement oldElement, @Nullable JavaElement newElement, @Nonnull Difference difference) {
        if (shouldIgnore(oldElement, newElement, difference)) {
            return null;
        }

        return difference;
    }

    private static boolean shouldIgnore(
            @Nullable JavaElement oldElement, @Nullable JavaElement newElement, @Nonnull Difference difference) {
        switch (Code.fromCode(difference.code)) {
            case METHOD_ABSTRACT_METHOD_ADDED:
                return inImmutablesClass(newElement);

            case METHOD_RETURN_TYPE_CHANGED:
            case METHOD_VISIBILITY_REDUCED:
                return inImmutablesClass(oldElement) && inImmutablesClass(newElement) && abstractNonPublic(oldElement);

            case METHOD_REMOVED:
                return inImmutablesClass(oldElement) && abstractNonPublic(oldElement);

            case METHOD_NOW_ABSTRACT:
                return inImmutablesClass(oldElement) && inImmutablesClass(newElement);
        }

        return false;
    }

    private static boolean inImmutablesClass(JavaElement javaElement) {
        return methodElementFor(javaElement)
                .map(methodElement ->
                        methodElement.getDeclaringElement().getEnclosingElement().getAnnotationMirrors().stream()
                                .anyMatch(annotationMirror ->
                                        annotationMirror.toString().equals("@org.immutables.value.Value.Immutable")))
                .orElse(false);
    }

    private static Optional<MethodElement> methodElementFor(JavaElement javaElement) {
        if (javaElement == null) {
            return Optional.empty();
        }

        if (!(javaElement instanceof MethodElement)) {
            return Optional.empty();
        }

        return Optional.of((MethodElement) javaElement);
    }

    private static boolean abstractNonPublic(JavaElement javaElement) {
        return methodElementFor(javaElement)
                .map(methodElement -> {
                    Set<Modifier> modifiers =
                            methodElement.getDeclaringElement().getModifiers();
                    return modifiers.contains(Modifier.ABSTRACT) && !modifiers.contains(Modifier.PUBLIC);
                })
                .orElse(false);
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void close() {}
}
