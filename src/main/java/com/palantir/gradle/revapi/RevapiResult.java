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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.gradle.revapi.config.AcceptedBreak;
import com.palantir.gradle.revapi.config.Justification;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;
import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;

@Value.Immutable
abstract class RevapiResult {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public abstract String code();
    public abstract String oldElement();
    public abstract String newElement();
    public abstract String description();
    public abstract String oldArchiveName();
    public abstract String newArchiveName();
    public abstract Map<CompatibilityType, DifferenceSeverity> statuses();

    public AcceptedBreak toAcceptedBreak(Justification justification) {
        return AcceptedBreak.builder()
                .code(code())
                .oldElement(oldElement())
                .newElement(newElement())
                .justification(justification)
                .build();
    }

    public static List<RevapiResult> fromFile(File file) {
        try {
            return OBJECT_MAPPER.readValue(file, new TypeReference<List<RevapiResult>>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class Builder extends ImmutableRevapiResult.Builder { }

    public static Builder builder() {
        return new Builder();
    }
}
