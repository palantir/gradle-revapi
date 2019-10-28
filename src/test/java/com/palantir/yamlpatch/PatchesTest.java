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

import static org.assertj.core.api.Assertions.assertThat;

import groovy.transform.CompileStatic;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@CompileStatic
class PatchesTest {
    @Test
    void overlapping_patches_are_noted() {
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            Patches.of(
                    Patch.of(0, 10, "overlaps"),
                    Patch.of(20, 30, "is_fine"),
                    Patch.of(5, 15, "overlaps"));
        }).withMessageContaining("overlap");
    }

    @Test
    void applies_a_single_patch_correctly() {
        Patches patches = Patches.of(
                Patch.of(4, 6, "heya"));

        assertThat(patches.applyTo("0123456789")).isEqualTo("0123heya6789");
    }
}
