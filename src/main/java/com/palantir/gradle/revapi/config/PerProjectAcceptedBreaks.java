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

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutablePerProjectAcceptedBreaks.class)
abstract class PerProjectAcceptedBreaks {
    @JsonValue
    @Value.NaturalOrder
    protected abstract SortedMap<GroupAndName, SortedSet<AcceptedBreak>> acceptedBreaks();

    public Set<AcceptedBreak> acceptedBreaksFor(GroupAndName groupAndName) {
        return acceptedBreaks().getOrDefault(groupAndName, Collections.emptySortedSet());
    }

    public PerProjectAcceptedBreaks merge(GroupAndName groupAndName, Set<AcceptedBreak> acceptedBreaks) {
        SortedMap<GroupAndName, SortedSet<AcceptedBreak>> newAcceptedBreaks = new TreeMap<>(acceptedBreaks());
        newAcceptedBreaks.put(groupAndName, ImmutableSortedSet.<AcceptedBreak>naturalOrder()
                .addAll(acceptedBreaks().getOrDefault(groupAndName, ImmutableSortedSet.of()))
                .addAll(acceptedBreaks)
                .build());

        return builder()
                .putAllAcceptedBreaks(newAcceptedBreaks)
                .build();
    }

    static final class Builder extends ImmutablePerProjectAcceptedBreaks.Builder { }

    public static Builder builder() {
        return new Builder();
    }

    public static PerProjectAcceptedBreaks empty() {
        return builder().build();
    }
}
