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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.Streams;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableRevapiConfig.class)
public abstract class RevapiConfig {
    protected abstract Map<GroupNameVersion, String> versionOverrides();
    protected abstract Map<Version, PerProjectAcceptedBreaks> acceptedBreaks();

    public final Optional<String> versionOverrideFor(GroupNameVersion groupNameVersion) {
        return Optional.ofNullable(versionOverrides().get(groupNameVersion));
    }

    public final RevapiConfig addVersionOverride(GroupNameVersion groupNameVersion, String versionOverride) {
        return ImmutableRevapiConfig.builder()
                .from(this)
                .putVersionOverrides(groupNameVersion, versionOverride)
                .build();
    }

    public final Set<AcceptedBreak> acceptedBreaksFor(GroupNameVersion groupNameVersion) {
        return Streams.stream(Optional.ofNullable(acceptedBreaks().get(groupNameVersion.version())))
                .flatMap(versionedAcceptedBreaks -> versionedAcceptedBreaks.acceptedBreaks()
                        .get(groupNameVersion.groupAndName())
                        .stream())
                .collect(Collectors.toSet());
    }

    public final RevapiConfig addAcceptedBreak(
            GroupNameVersion groupNameVersion,
            Set<AcceptedBreak> acceptedBreaks) {

        PerProjectAcceptedBreaks existingAcceptedBreaks =
                acceptedBreaks().getOrDefault(groupNameVersion.version(), PerProjectAcceptedBreaks.empty());

        PerProjectAcceptedBreaks newPerProjectAcceptedBreaks = existingAcceptedBreaks.merge(
                groupNameVersion.groupAndName(),
                acceptedBreaks);

        Map<Version, PerProjectAcceptedBreaks> blah = new HashMap<>(acceptedBreaks());
        blah.put(groupNameVersion.version(), newPerProjectAcceptedBreaks);

        return ImmutableRevapiConfig.builder()
                .putAllVersionOverrides(versionOverrides())
                .putAllAcceptedBreaks(blah)
                .build();
    }

    public static RevapiConfig empty() {
        return ImmutableRevapiConfig.builder().build();
    }

    public static ObjectMapper newRecommendedObjectMapper() {
        return new ObjectMapper(
                new YAMLFactory()
                        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        ).registerModule(new GuavaModule());
    }
}
