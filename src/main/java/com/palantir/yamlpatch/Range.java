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

package com.palantir.yamlpatch;

import org.immutables.value.Value;

@Value.Immutable
abstract class Range {
    public abstract int startIndex();
    public abstract int endIndex();

    @Value.Check
    protected void check() {
        if (startIndex() > endIndex()) {
            throw new IllegalArgumentException("startIndex must be greater than endIndex");
        }

        if (startIndex() < 0) {
            throw new IllegalArgumentException("startIndex must be >= 0");
        }

        if (endIndex() < 0) {
            throw new IllegalArgumentException("endIndex must be >= 0");
        }
    }

    public boolean overlaps(Range other) {
        boolean ourRangeIsLess   = startIndex() < other.startIndex() && endIndex() < other.startIndex();
        boolean theirRangeIsLess = other.startIndex() < startIndex() && other.endIndex() < startIndex();
        return !(ourRangeIsLess || theirRangeIsLess);
    }

    public static class Builder extends ImmutableRange.Builder { }

    public static Builder builder() {
        return new Builder();
    }

    public static Range of(int startIndex, int endIndex) {
        return builder()
                .startIndex(startIndex)
                .endIndex(endIndex)
                .build();
    }
}
