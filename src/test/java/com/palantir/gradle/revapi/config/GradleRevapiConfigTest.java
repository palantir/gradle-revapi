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

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

class GradleRevapiConfigTest {
    private static final GroupNameVersion FOO_BAR_312 = GroupNameVersion.fromString("foo:bar:3.12");
    private static final GroupNameVersion QUUX_BAZ_10 = GroupNameVersion.fromString("quux:baz:1.0");

    @Test
    void can_get_accepted_breaks_properly() {
        AcceptedBreak acceptedBreak1 = acceptedBreak("1");
        AcceptedBreak acceptedBreak2 = acceptedBreak("2");
        AcceptedBreak acceptedBreak3 = acceptedBreak("3");
        AcceptedBreak acceptedBreak4 = acceptedBreak("4");

        GradleRevapiConfig gradleRevapiConfig = GradleRevapiConfig.empty()
                .addAcceptedBreaks(FOO_BAR_312, ImmutableSet.of(acceptedBreak1, acceptedBreak2))
                .addAcceptedBreaks(FOO_BAR_312, ImmutableSet.of(acceptedBreak3))
                .addAcceptedBreaks(QUUX_BAZ_10, ImmutableSet.of(acceptedBreak4));

        assertThat(gradleRevapiConfig.acceptedBreaksFor(FOO_BAR_312.groupAndName())).containsOnly(
                acceptedBreak1, acceptedBreak2, acceptedBreak3);

        assertThat(gradleRevapiConfig.acceptedBreaksFor(QUUX_BAZ_10.groupAndName())).containsOnly(acceptedBreak4);
        assertThat(gradleRevapiConfig.acceptedBreaksFor(GroupAndName.fromString("doesnot:exist"))).isEmpty();
    }

    @Test
    void can_get_version_override_for() {
        GradleRevapiConfig gradleRevapiConfig = GradleRevapiConfig.empty()
                .addVersionOverride(FOO_BAR_312, "some_version_override");

        assertThat(gradleRevapiConfig.versionOverrideFor(FOO_BAR_312))
                .hasValue(Version.fromString("some_version_override"));
        assertThat(gradleRevapiConfig.versionOverrideFor(QUUX_BAZ_10)).isEmpty();
    }

    private AcceptedBreak acceptedBreak(String suffix) {
        return AcceptedBreak.builder()
                .code("code" + suffix)
                .oldElement("old" + suffix)
                .newElement("new" + suffix)
                .justification("j" + suffix)
                .build();
    }
}
