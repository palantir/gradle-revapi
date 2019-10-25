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

package com.palantir.gradle.revapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.palantir.gradle.revapi.config.GradleRevapiConfig;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Justification;
import com.palantir.gradle.revapi.config.v2.AcceptedBreak;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
    @TempDir
    File tempDir;

    @Test
    void withConfigFile_returns_empty_config_if_there_is_no_file() {
        File nonExistentFile1 = new File(tempDir, "does-not-exist");
        ConfigManager configManager = new ConfigManager(nonExistentFile1);
        UnaryOperator<GradleRevapiConfig> transformer = identityFunction();

        configManager.modifyConfigFile(transformer);

        verify(transformer).apply(GradleRevapiConfig.empty());
    }

    @Test
    void withConfig_reads_the_existing_config_file_and_writes_back_the_transformed_one()
            throws IOException {

        File oldConfigFile = new File(tempDir, "revapi.yml");
        ConfigManager configManager = new ConfigManager(oldConfigFile);

        Files.write(oldConfigFile.toPath(), String.join("\n",
                "versionOverrides:",
                "  foo:bar:3.12: \"1.0\"",
                "acceptedBreaksV2:",
                "- justification: \"I don't care about my users\"",
                "  afterVersion: 1.2.3",
                "  breaks:",
                "    foo:bar:",
                "      - code: blah",
                "        old: old",
                "        new: new")
                .getBytes(StandardCharsets.UTF_8));

        configManager.modifyConfigFile(revapiConfig -> {
            assertThat(revapiConfig.versionOverrideFor(GroupNameVersion.fromString("foo:bar:3.12"))).hasValue("1.0");
            assertThat(revapiConfig.acceptedBreaksFor(GroupNameVersion.fromString("foo:bar:1.2.3"))).containsExactly(
                    AcceptedBreak.builder()
                            .code("blah")
                            .oldElement("old")
                            .newElement("new")
                            .build()
            );
            assertThat(revapiConfig.acceptedBreaksFor(GroupNameVersion.fromString("doesnt:exist:1.2.3"))).isEmpty();
            return revapiConfig
                    .addVersionOverride(GroupNameVersion.fromString("quux:baz:2.0"), "3.6")
                    .addAcceptedBreaks(
                            GroupNameVersion.fromString("foo:bar:1.2.3"),
                            Justification.fromString("I don't care about my users"),
                            ImmutableSet.of(AcceptedBreak
                                    .builder()
                                    .code("something")
                                    .oldElement("old2")
                                    .newElement("new2")
                                    .build()))
                    .addAcceptedBreaks(
                            GroupNameVersion.fromString("quux:baz:0.4.1"),
                            Justification.fromString("j"),
                            ImmutableSet.of(AcceptedBreak
                                    .builder()
                                    .code("c")
                                    .oldElement("o")
                                    .newElement("n")
                                    .build()));
        });

        System.out.println(CharStreams.toString(new FileReader(oldConfigFile)));

        assertThat(oldConfigFile).hasContent(String.join("\n",
                "versionOverrides:",
                "  foo:bar:3.12: \"1.0\"",
                "  quux:baz:2.0: \"3.6\"",
                "acceptedBreaksV2:",
                "- justification: \"I don't care about my users\"",
                "  afterVersion: \"1.2.3\"",
                "  breaks:",
                "    foo:bar:",
                "    - code: \"something\"",
                "      old: \"old2\"",
                "      new: \"new2\"",
                "    - code: \"blah\"",
                "      old: \"old\"",
                "      new: \"new\"",
                "- justification: \"j\"",
                "  afterVersion: \"1.2.3\"",
                "  breaks:",
                "    quux:baz:",
                "    - code: \"c\"",
                "      old: \"o\"",
                "      new: \"n\""));

    }

    @Test
    void read_config_correctly_when_there_are_version_overrides_but_not_accepted_breaks() throws IOException {
        File oldConfigFile = new File(tempDir, "revapi.yml");
        ConfigManager configManager = new ConfigManager(oldConfigFile);

        Files.write(oldConfigFile.toPath(), String.join("\n",
                "versionOverrides:",
                "  foo:bar:3.12: \"1.0\"")
                .getBytes(StandardCharsets.UTF_8));

        GradleRevapiConfig gradleRevapiConfig = configManager.fromFileOrEmptyIfDoesNotExist();

        assertThat(gradleRevapiConfig.versionOverrideFor(GroupNameVersion.fromString("foo:bar:3.12"))).hasValue("1.0");
    }

    private UnaryOperator<GradleRevapiConfig> identityFunction() {
        UnaryOperator<GradleRevapiConfig> transformer = mock(UnaryOperator.class);
        when(transformer.apply(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return transformer;
    }
}
