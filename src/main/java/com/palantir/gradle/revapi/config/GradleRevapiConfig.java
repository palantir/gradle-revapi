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

package com.palantir.gradle.revapi.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableGradleRevapiConfig.class)
public abstract class GradleRevapiConfig {
    protected abstract Map<GroupNameVersion, String> versionOverrides();
    protected abstract Map<Version, PerProjectAcceptedBreaks> acceptedBreaks();

    public final Optional<String> versionOverrideFor(GroupNameVersion groupNameVersion) {
        return Optional.ofNullable(versionOverrides().get(groupNameVersion));
    }

    public final GradleRevapiConfig addVersionOverride(GroupNameVersion groupNameVersion, String versionOverride) {
        return ImmutableGradleRevapiConfig.builder()
                .from(this)
                .putVersionOverrides(groupNameVersion, versionOverride)
                .build();
    }

    public final Set<AcceptedBreak> acceptedBreaksFor(GroupNameVersion groupNameVersion) {
        return Optional.ofNullable(acceptedBreaks().get(groupNameVersion.version()))
                .map(projectBreaks -> projectBreaks.acceptedBreaksFor(groupNameVersion.groupAndName()))
                .orElseGet(Collections::emptySet);
    }

    public final GradleRevapiConfig addAcceptedBreaks(
            GroupNameVersion groupNameVersion,
            Set<AcceptedBreak> acceptedBreaks) {

        PerProjectAcceptedBreaks existingAcceptedBreaks =
                acceptedBreaks().getOrDefault(groupNameVersion.version(), PerProjectAcceptedBreaks.empty());

        PerProjectAcceptedBreaks newPerProjectAcceptedBreaks = existingAcceptedBreaks.merge(
                groupNameVersion.groupAndName(),
                acceptedBreaks);

        Map<Version, PerProjectAcceptedBreaks> newAcceptedBreaks = new HashMap<>(acceptedBreaks());
        newAcceptedBreaks.put(groupNameVersion.version(), newPerProjectAcceptedBreaks);

        return ImmutableGradleRevapiConfig.builder()
                .from(this)
                .acceptedBreaks(newAcceptedBreaks)
                .build();
    }

    public static GradleRevapiConfig empty() {
        return ImmutableGradleRevapiConfig.builder().build();
    }

    public static ObjectMapper newYamlObjectMapper() {
        return configureObjectMapper(new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)));
    }

    public static ObjectMapper newJsonObjectMapper() {
        return configureObjectMapper(new ObjectMapper())
                .enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
    }

    private static ObjectMapper configureObjectMapper(ObjectMapper objectMapper) {
        return objectMapper
                .registerModule(new GuavaModule())
                .registerModule(new Jdk8Module());
    }
}
