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

import com.google.auto.service.AutoService;
import java.io.Reader;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.spi.JavaElement;

@AutoService(DifferenceTransform.class)
public final class CheckWhitelist implements DifferenceTransform<JavaElement> {
    public static final String EXTENSION_ID = "gradle-revapi.check.whitelist";

    private static final Pattern[] EVERYTHING = {Pattern.compile(".*") };

    private boolean enabled = false;
    private Set<String> whitelistedChecks;

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        this.enabled = analysisContext.getConfiguration().isDefined();

        if (!this.enabled) {
            return;
        }

        this.whitelistedChecks = analysisContext.getConfiguration().asList().stream()
                .map(ModelNode::asString)
                .collect(Collectors.toSet());
    }

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
            @Nullable JavaElement _oldElement,
            @Nullable JavaElement _newElement,
            @Nonnull Difference difference) {

        if (!enabled) {
            return difference;
        }

        if (whitelistedChecks.contains(difference.code)) {
            return difference;
        }

        return null;
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void close() { }
}
