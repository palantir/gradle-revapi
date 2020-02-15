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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public interface GroupNameVersion extends Comparable<GroupNameVersion> {
    GroupAndName groupAndName();
    Version version();

    @JsonValue
    default String asString() {
        return String.join(":", groupAndName().group(), groupAndName().name(), version().asString());
    }

    @JsonCreator
    static GroupNameVersion fromString(String groupNameVersionString) {
        List<String> split = Splitter.on(':').splitToList(groupNameVersionString);

        Preconditions.checkArgument(split.size() == 3,
                "%s could not be split into group name and version", groupNameVersionString);

        return builder()
                .groupAndName(GroupAndName.builder()
                        .group(split.get(0))
                        .name(split.get(1))
                        .build())
                .version(Version.fromString(split.get(2)))
                .build();
    }

    @Override
    default int compareTo(GroupNameVersion other) {
        return this.asString().compareTo(other.asString());
    }

    class Builder extends ImmutableGroupNameVersion.Builder { }

    static Builder builder() {
        return new Builder();
    }
}
