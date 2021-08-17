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
import com.google.common.base.Splitter;
import com.palantir.gradle.revapi.ImmutableStyle;
import java.util.List;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@Serial.Structural
public interface GroupAndName extends Comparable<GroupAndName> {
    String group();

    String name();

    @JsonValue
    default String asString() {
        return String.join(":", group(), name());
    }

    @JsonCreator
    static GroupAndName fromString(String groupAndName) {
        List<String> split = Splitter.on(":").splitToList(groupAndName);

        if (split.size() != 2) {
            throw new IllegalArgumentException(
                    String.format("%s could not be split into group and name", groupAndName));
        }

        return builder().group(split.get(0)).name(split.get(1)).build();
    }

    @Override
    default int compareTo(GroupAndName other) {
        return this.asString().compareTo(other.asString());
    }

    default GroupNameVersion withVersion(Version version) {
        return GroupNameVersion.builder().groupAndName(this).version(version).build();
    }

    class Builder extends ImmutableGroupAndName.Builder {}

    static Builder builder() {
        return new Builder();
    }
}
