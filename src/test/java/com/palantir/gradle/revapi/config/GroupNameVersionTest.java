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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class GroupNameVersionTest {
    @Test
    void parses_from_string_properly() {
        GroupNameVersion groupNameVersion = GroupNameVersion.fromString("foo:bar:4.5.6");
        assertThat(groupNameVersion.group()).isEqualTo("foo");
        assertThat(groupNameVersion.name()).isEqualTo("bar");
        assertThat(groupNameVersion.version()).isEqualTo("4.5.6");
    }

    @Test
    void throws_when_not_in_the_correct_format() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> GroupNameVersion.fromString("foo:bar"));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> GroupNameVersion.fromString("eeep"));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> GroupNameVersion.fromString("too:many:colons:yo"));
    }
}
