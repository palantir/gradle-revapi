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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@ImmutablesStyle
@JsonDeserialize(as = ImmutableAcceptedBreaks.class)
abstract class AcceptedBreaks {
    @JsonValue
    protected abstract List<BreakCollection> breakCollections();

    public final Set<FlattenedBreak> flattenedBreaksFor(GroupAndName groupAndName) {
        return breakCollections().stream()
                .flatMap(breakCollection -> breakCollection.flattenedBreaksFor(groupAndName))
                .collect(ImmutableSet.toImmutableSet());
    }

    public final AcceptedBreaks addAcceptedBreaks(
            GroupNameVersion groupNameVersion,
            Justification justification,
            Set<Break> newAcceptedBreaks) {

        return fromBreakCollections(mergeInBreaks(groupNameVersion, justification, newAcceptedBreaks));
    }

    private List<BreakCollection> mergeInBreaks(
            GroupNameVersion groupNameVersion,
            Justification justification,
            Set<Break> newAcceptedBreaks) {

        List<BreakCollection> possiblyAddedToExistedBreakCollection = breakCollections().stream()
                .map(breaks -> breaks.addAcceptedBreaksIfMatching(justification, groupNameVersion, newAcceptedBreaks))
                .collect(Collectors.toList());

        boolean breaksWereAddedToExistingCollection = !possiblyAddedToExistedBreakCollection.equals(breakCollections());

        if (breaksWereAddedToExistingCollection) {
            return possiblyAddedToExistedBreakCollection;
        }

        return appendBreaksInNewCollection(groupNameVersion, justification, newAcceptedBreaks);
    }

    private List<BreakCollection> appendBreaksInNewCollection(
            GroupNameVersion groupNameVersion,
            Justification justification,
            Set<Break> newAcceptedBreaks) {

        return ImmutableList.<BreakCollection>builder()
                .addAll(breakCollections())
                .add(BreakCollection.builder()
                        .justification(justification)
                        .afterVersion(groupNameVersion.version())
                        .breaks(PerProject.<Break>builder()
                                .putPerProjectItems(groupNameVersion.groupAndName(), newAcceptedBreaks)
                                .build())
                        .build())
                .build();
    }

    public final AcceptedBreaks andAlso(AcceptedBreaks otherBreaks) {
        return builder()
                .from(this)
                .addAllBreakCollections(otherBreaks.breakCollections())
                .build();
    }

    public static class Builder extends ImmutableAcceptedBreaks.Builder {}

    public static Builder builder() {
        return new Builder();
    }

    public static AcceptedBreaks empty() {
        return builder().build();
    }

    public static AcceptedBreaks fromBreakCollections(Iterable<BreakCollection> breakCollections) {
        return builder()
                .breakCollections(breakCollections)
                .build();
    }
}
