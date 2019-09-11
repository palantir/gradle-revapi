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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.gradle.api.tasks.TaskAction;

public class RevapiReportTask extends RevapiJavaTask {
    @TaskAction
    public final void runRevapi() throws Exception {
        Path textOutputPath = Files.createTempFile("revapi-text-output", ".txt");

        runRevapi(RevapiJsonConfig.empty()
                .withTextReporter("gradle-revapi-junit-template.ftlx", junitOutput())
                .withTextReporter("gradle-revapi-text-template.ftl", textOutputPath.toFile()));

        String textOutput = new String(Files.readAllBytes(textOutputPath), StandardCharsets.UTF_8);
        if (!textOutput.trim().isEmpty()) {
            throw new RuntimeException("There were Java public API/ABI breaks reported by revapi:\n\n" + textOutput);
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
