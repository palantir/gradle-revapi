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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import one.util.streamex.EntryStream;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutablePerProject.class)
public abstract class PerProject<T> {
    @JsonValue
    protected abstract Map<GroupAndName, Set<T>> perProjectItems();

    public final Set<T> forGroupAndName(GroupAndName groupAndName) {
        return perProjectItems().getOrDefault(groupAndName, Collections.emptySet());
    }

    public final PerProject<T> merge(GroupAndName groupAndName, Set<T> items) {
        Map<GroupAndName, Set<T>> newPerProjectItems = new HashMap<>(perProjectItems());
        newPerProjectItems.put(groupAndName, Sets.union(
                items,
                this.perProjectItems().getOrDefault(groupAndName, ImmutableSet.of())));

        return PerProject.<T>builder()
                .putAllItems(newPerProjectItems)
                .build();
    }

    public final <R> Stream<R> flatten(BiFunction<GroupAndName, T, R> flattener) {
        return EntryStream.of(perProjectItems())
                .flatMapKeyValue((groupAndName, items) -> items.stream()
                        .map(item -> flattener.apply(groupAndName, item)));
    }

    public final <R> PerProject<R> map(Function<T, R> function) {
        return fromEntryStream(EntryStream.of(perProjectItems())
                .mapValues(items -> items.stream()
                        .map(function)
                        .collect(ImmutableSet.toImmutableSet())));
    }

    public static final class Builder<T> extends ImmutablePerProject.Builder<T> {}

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static <T> PerProject<T> empty() {
        return PerProject.<T>builder().build();
    }

    public static <T> PerProject<T> fromEntryStream(EntryStream<GroupAndName, Set<T>> perProjectItems) {
        return PerProject.<T>builder()
                .items(perProjectItems.toImmutableMap())
                .build();
    }

    public static <T> PerProject<T> groupingBy(Collection<T> items, Function<T, GroupAndName> keyFunction) {
        Map<GroupAndName, List<T>> groupedByName = items.stream()
                .collect(Collectors.groupingBy(keyFunction));

        return fromEntryStream(EntryStream.of(groupedByName)
                .mapValues(perProjectItems -> (Set<T>) ImmutableSet.copyOf(perProjectItems)));
    }
}
