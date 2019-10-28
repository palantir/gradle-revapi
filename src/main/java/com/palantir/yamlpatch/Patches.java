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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Patches {
    private final List<Patch> orderedPatches;

    private Patches(List<Patch> orderedPatches) {
        this.orderedPatches = orderedPatches;
    }

    public String applyTo(String input) {
        StringBuilder builder = new StringBuilder();
        int lastIndex = 0;
        for (Patch patch : orderedPatches) {
            builder.append(input, lastIndex, patch.range().startIndex());
            builder.append(patch.replacement());
            lastIndex = patch.range().endIndex();
        }
        builder.append(input, lastIndex, input.length());
        return builder.toString();
    }

    public static Patches of(Patch... patches) {
        return of(Arrays.stream(patches));
    }

    public static Patches of(Stream<Patch> patches) {
        List<Patch> orderedPatches = patches
                .sorted(Comparator.comparing(patch -> patch.range().startIndex()))
                .collect(Collectors.toList());

        ensureNoOverlaps(orderedPatches);

        return new Patches(orderedPatches);
    }

    private static void ensureNoOverlaps(List<Patch> inOrderPatches) {
        for (int i = 0; i < inOrderPatches.size() - 1; i++) {
            Patch left = inOrderPatches.get(i);
            Patch right = inOrderPatches.get(i + 1);
            if (left.range().overlaps(right.range())) {
                throw new IllegalArgumentException(left + " and " + right + " overlap");
            }
        }
    }
}
