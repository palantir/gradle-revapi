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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Set;
import java.util.stream.Stream;
import org.immutables.value.Value;

@Value.Immutable
@ImmutablesStyle
@JsonDeserialize(as = ImmutableBreakCollection.class)
interface BreakCollection {
    Justification justification();
    Version afterVersion();
    PerProject<Break> breaks();

    default BreakCollection addAcceptedBreaksIfMatching(
            Justification justification,
            GroupNameVersion groupNameVersion,
            Set<Break> breaks) {

        if (!(justification().equals(justification) && afterVersion().equals(groupNameVersion.version()))) {
            return this;
        }

        return builder()
                .from(this)
                .breaks(breaks().withAdded(groupNameVersion.groupAndName(), breaks))
                .build();
    }

    default Stream<FlattenedBreak> flattenedBreaksFor(GroupAndName groupAndName) {
        return breaks().forGroupAndName(groupAndName)
                .stream()
                .map(acceptedBreak -> FlattenedBreak.builder()
                        .justificationAndVersion(JustificationAndVersion.builder()
                                .justification(justification())
                                .version(afterVersion())
                                .build())
                        .groupAndName(groupAndName)
                        .acceptedBreak(acceptedBreak)
                        .build());
    }

    class Builder extends ImmutableBreakCollection.Builder { }

    static Builder builder() {
        return new Builder();
    }
}
