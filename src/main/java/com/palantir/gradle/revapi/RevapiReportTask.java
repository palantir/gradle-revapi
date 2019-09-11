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

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.gradle.api.tasks.TaskAction;
import org.revapi.API;
import org.revapi.Archive;

public class RevapiReportTask extends RevapiJavaTask {
    @TaskAction
    public final void runRevapi() throws Exception {
        Path textOutputPath = Files.createTempFile("revapi-text-output", ".txt");

        runRevapi((oldApi, newApi) -> templateJsonConfig(oldApi, newApi, textOutputPath));

        String textOutput = new String(Files.readAllBytes(textOutputPath), StandardCharsets.UTF_8);
        if (!textOutput.trim().isEmpty()) {
            throw new RuntimeException("There were Java public API/ABI breaks reported by revapi:\n\n" + textOutput);
        }
    }

    private String templateJsonConfig(API oldApi, API newApi, Path textOutputPath) {
        String template = configFromResources();

        return template
                .replace("{{JUNIT_OUTPUT}}", junitOutput().getAbsolutePath())
                .replace("{{TEXT_OUTPUT}}", textOutputPath.toAbsolutePath().toString())
                .replace("{{ARCHIVE_INCLUDE_REGEXES}}", Stream.of(newApi, oldApi)
                        .flatMap(api -> StreamSupport.stream(api.getArchives().spliterator(), false))
                        .map(Archive::getName)
                        .collect(Collectors.joining("\", \"")));
    }

    private String configFromResources() {
        try {
            return Resources.toString(Resources.getResource(
                    "revapi-configuration.json"),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File junitOutput() {
        Optional<String> circleReportsDir = Optional.ofNullable(System.getenv("CIRCLE_TEST_REPORTS"));
        File reportsDir = circleReportsDir
                .map(File::new)
                .orElseGet(() -> getProject().getBuildDir());
        return new File(reportsDir, "junit-reports/revapi/revapi-" + getProject().getName() + ".xml");
    }
}
