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
import java.io.Serializable;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public interface GroupAndName extends Serializable {
    long serialVersionUID = -9100078789756427887L;

    String group();
    String name();

    @JsonValue
    default String asString() {
        return String.join(":", group(), name());
    }

    @JsonCreator
    static GroupAndName fromString(String groupAndName) {
        List<String> split = Splitter.on(':').splitToList(groupAndName);

        Preconditions.checkArgument(split.size() == 2,
                "%s could not be split into group and name", groupAndName);

        return builder()
                .group(split.get(0))
                .name(split.get(1))
                .build();
    }

    class Builder extends ImmutableGroupAndName.Builder { }

    static Builder builder() {
        return new Builder();
    }
}
