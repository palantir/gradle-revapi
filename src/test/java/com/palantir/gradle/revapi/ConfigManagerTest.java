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
import com.palantir.gradle.revapi.config.AcceptedBreak;
import com.palantir.gradle.revapi.config.GradleRevapiConfig;
import com.palantir.gradle.revapi.config.GroupAndName;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import java.io.File;
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
                "acceptedBreaks:",
                "  1.2.3:",
                "    foo:bar:",
                "      - code: blah",
                "        old: old",
                "        new: new",
                "        justification: \"I don't care about my users\""
                        )
                .getBytes(StandardCharsets.UTF_8));

        configManager.modifyConfigFile(revapiConfig -> {
            assertThat(revapiConfig.acceptedBreaksFor(GroupAndName.fromString("doesnt:exist"))).isEmpty();
            return revapiConfig
                    .addVersionOverride(GroupNameVersion.fromString("quux:baz:2.0"), "3.6")
                    .addAcceptedBreaks(GroupNameVersion.fromString("quux:baz:1.2.3"), ImmutableSet.of(AcceptedBreak
                            .builder()
                            .code("something")
                            .oldElement("old2")
                            .newElement("new2")
                            .justification("j")
                            .build()));
        });

        assertThat(oldConfigFile).hasContent(String.join("\n",
                "versionOverrides:",
                "  foo:bar:3.12: \"1.0\"",
                "  quux:baz:2.0: \"3.6\"",
                "acceptedBreaks:",
                "  1.2.3:",
                "    foo:bar:",
                "    - code: \"blah\"",
                "      old: \"old\"",
                "      new: \"new\"",
                "      justification: \"I don't care about my users\"",
                "    quux:baz:",
                "    - code: \"something\"",
                "      old: \"old2\"",
                "      new: \"new2\"",
                "      justification: \"j\""));

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
