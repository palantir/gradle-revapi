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
import com.palantir.gradle.revapi.config.Justification;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.TaskAction;

public class RevapiReportTask extends DefaultTask {
    private final RegularFileProperty analysisResultsFile = getProject().getObjects().fileProperty();
    private final RegularFileProperty junitOutputFile = getProject().getObjects().fileProperty();

    public final RegularFileProperty getAnalysisResultsFile() {
        return analysisResultsFile;
    }

    public final RegularFileProperty getJunitOutputFile() {
        return junitOutputFile;
    }

    @TaskAction
    public final void reportBreaks() throws Exception {
        RevapiResults results = RevapiResults.fromFile(resultsFile.getAsFile().get());

        Configuration freeMarkerConfiguration = createFreeMarkerConfiguration();
        Map<String, Object> templateData = ImmutableMap.of(
                "results", results,
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
        );

        Template junitTemplate = freeMarkerConfiguration.getTemplate("gradle-revapi-junit-template.ftl");
        junitTemplate.process(templateData, Files.newBufferedWriter(
                junitOutputFile.getAsFile().get().toPath(), StandardCharsets.UTF_8));

        Template textTemplate = freeMarkerConfiguration.getTemplate("gradle-revapi-text-template.ftl");
        StringWriter textOutputWriter = new StringWriter();
        textTemplate.process(templateData, textOutputWriter);

        String textOutput = textOutputWriter.toString();

        if (!textOutput.trim().isEmpty()) {
            throw new RuntimeException("There were Java public API/ABI breaks reported by revapi:\n\n" + textOutput);
        }
    }

    private Configuration createFreeMarkerConfiguration() {
        DefaultObjectWrapperBuilder objectWrapper = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_23);
        Configuration freeMarker = new Configuration(Configuration.VERSION_2_3_23);

        freeMarker.setObjectWrapper(objectWrapper.build());
        freeMarker.setAPIBuiltinEnabled(true);
        freeMarker.setTemplateLoader(new ClassTemplateLoader(getClass(), "/META-INF"));

        return freeMarker;
    }
}
