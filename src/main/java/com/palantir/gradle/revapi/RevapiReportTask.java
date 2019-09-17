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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.palantir.gradle.revapi.config.Justification;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.gradle.api.tasks.TaskAction;

public class RevapiReportTask extends RevapiJavaTask {
    @TaskAction
    public final void runRevapi() throws Exception {
        Path textOutputPath = Files.createTempFile("revapi-text-output", ".txt");

        String differenceTemplate = templateResource("gradle-revapi-difference-template.ftl", ImmutableMap.of(
                "acceptBreakTask", getProject().getTasks()
                        .withType(RevapiAcceptBreakTask.class)
                        .getByName(RevapiPlugin.ACCEPT_BREAK_TASK_NAME)
                        .getPath(),
                "acceptAllBreaksProjectTask", getProject().getTasks()
                        .withType(RevapiAcceptAllBreaksTask.class)
                        .getByName(RevapiPlugin.ACCEPT_ALL_BREAKS_TASK_NAME)
                        .getPath(),
                "acceptAllBreaksEverywhereTask", RevapiPlugin.ACCEPT_ALL_BREAKS_TASK_NAME,
                "explainWhy", Justification.YOU_MUST_ENTER_JUSTIFICATION
        ));

        Path junitTemplate = templateWithDifferenceTemplateAndWriteToFile(
                "gradle-revapi-junit-template.ftl", differenceTemplate);

        Path textOutputTemplate = templateWithDifferenceTemplateAndWriteToFile(
                "gradle-revapi-text-template.ftl", differenceTemplate);

        runRevapi(RevapiJsonConfig.empty()
                .withTextReporter(junitTemplate.toString(), junitOutput())
                .withTextReporter(textOutputTemplate.toString(), textOutputPath.toFile()));

        String textOutput = new String(Files.readAllBytes(textOutputPath), StandardCharsets.UTF_8);
        if (!textOutput.trim().isEmpty()) {
            throw new RuntimeException("There were Java public API/ABI breaks reported by revapi:\n\n" + textOutput);
        }
    }

    private String templateResource(String resourceName, Map<String, String> args) throws IOException {
        String template = Resources.toString(Resources.getResource("META-INF/" + resourceName), StandardCharsets.UTF_8);
        return args.entrySet().stream().reduce(template, (partiallyRendered, arg) ->
                partiallyRendered.replace("{{" + arg.getKey() + "}}", arg.getValue()),
                (a, b) -> a);
    }

    private Path templateWithDifferenceTemplateAndWriteToFile(String resourceName, String differenceTemplate) throws IOException {
        Path templateOutput = Files.createTempFile(resourceName, "template");
        Files.write(templateOutput, templateResource(resourceName, ImmutableMap.of(
                "differenceTemplate", differenceTemplate
        )).getBytes(StandardCharsets.UTF_8));
        return templateOutput.toAbsolutePath();
    }

    private File junitOutput() {
        Optional<String> circleReportsDir = Optional.ofNullable(System.getenv("CIRCLE_TEST_REPORTS"));
        File reportsDir = circleReportsDir
                .map(File::new)
                .orElseGet(() -> getProject().getBuildDir());
        return new File(reportsDir, "junit-reports/revapi/revapi-" + getProject().getName() + ".xml");
    }
}
