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

package com.palantir.gradle.revapi.config.v2;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableSet;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.ImmutablesStyle;
import com.palantir.gradle.revapi.config.Justification;
import com.palantir.gradle.revapi.config.PerProject;
import java.util.Set;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@ImmutablesStyle
@JsonDeserialize(as = ImmutableAllAcceptedBreaks.class)
public abstract class AllAcceptedBreaks {
    @JsonValue
    protected abstract Set<BreakCollection> breakCollections();

    public final AllAcceptedBreaks addAcceptedBreaks(
            GroupNameVersion groupNameVersion,
            Justification justification,
            Set<AcceptedBreak> newAcceptedBreaks) {

        return fromBreakCollections(mergeInBreaks(groupNameVersion, justification, newAcceptedBreaks));
    }

    private Set<BreakCollection> mergeInBreaks(
            GroupNameVersion groupNameVersion,
            Justification justification,
            Set<AcceptedBreak> newAcceptedBreaks) {

        Set<BreakCollection> possiblyAddedToExistedBreakCollection = breakCollections().stream()
                .map(breaks -> breaks.addAcceptedBreaksIf(justification, groupNameVersion, newAcceptedBreaks))
                .collect(Collectors.toSet());

        boolean breaksWereAddedToExistingCollection = !possiblyAddedToExistedBreakCollection.equals(breakCollections());

        if (breaksWereAddedToExistingCollection) {
            return possiblyAddedToExistedBreakCollection;
        }

        return appendBreaksInNewCollection(groupNameVersion, justification, newAcceptedBreaks);
    }

    private Set<BreakCollection> appendBreaksInNewCollection(
            GroupNameVersion groupNameVersion,
            Justification justification,
            Set<AcceptedBreak> newAcceptedBreaks) {

        return ImmutableSet.<BreakCollection>builder()
                .addAll(breakCollections())
                .add(BreakCollection.builder()
                        .justification(justification)
                        .afterVersion(groupNameVersion.version())
                        .breaks(PerProject.<AcceptedBreak>builder()
                                .putPerProjectItems(groupNameVersion.groupAndName(), newAcceptedBreaks)
                                .build())
                        .build())
                .build();
    }

    public final AllAcceptedBreaks andAlso(AllAcceptedBreaks otherBreaks) {
        return builder()
                .from(this)
                .addAllBreakCollections(otherBreaks.breakCollections())
                .build();
    }

    public final Set<AcceptedBreak> acceptedBreaksFor(GroupNameVersion groupNameVersion) {
        return breakCollections().stream()
                .flatMap(breakCollection -> breakCollection.acceptedBreaksFor(groupNameVersion.groupAndName()).stream())
                .collect(Collectors.toSet());
    }

    public static class Builder extends ImmutableAllAcceptedBreaks.Builder {}

    public static Builder builder() {
        return new Builder();
    }

    public static AllAcceptedBreaks empty() {
        return builder().build();
    }

    public static AllAcceptedBreaks fromBreakCollections(Iterable<BreakCollection> breakCollections) {
        return builder()
                .breakCollections(breakCollections)
                .build();
    }
}
