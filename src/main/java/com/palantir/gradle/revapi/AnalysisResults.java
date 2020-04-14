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

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.palantir.gradle.revapi.config.AcceptedBreak;
import com.palantir.gradle.revapi.config.Justification;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = ImmutableAnalysisResults.class)
public abstract class AnalysisResults {
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());

    public abstract String archiveNames();

    public abstract List<AnalysisResult> results();

    final Set<AcceptedBreak> toAcceptedBreaks(Justification justification) {
        return results().stream()
                .map(result -> result.toAcceptedBreak(justification))
                .collect(Collectors.toSet());
    }

    public static AnalysisResults fromFile(File file) {
        try {
            return OBJECT_MAPPER.readValue(file, AnalysisResults.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class Builder extends ImmutableAnalysisResults.Builder {}

    public static Builder builder() {
        return new Builder();
    }
}
