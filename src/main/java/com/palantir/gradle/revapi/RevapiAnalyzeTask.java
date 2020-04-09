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

import com.palantir.gradle.revapi.config.AcceptedBreak;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Revapi;
import org.revapi.java.JavaApiAnalyzer;
import org.revapi.reporter.text.TextReporter;
import org.revapi.simple.FileArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CacheableTask
public class RevapiAnalyzeTask extends DefaultTask {
    private static final Logger log = LoggerFactory.getLogger(RevapiAnalyzeTask.class);

    private final SetProperty<AcceptedBreak> acceptedBreaks =
            getProject().getObjects().setProperty(AcceptedBreak.class);
    private final Property<FileCollection> newApiJars =
            getProject().getObjects().property(FileCollection.class);
    private final Property<FileCollection> newApiDependencyJars =
            getProject().getObjects().property(FileCollection.class);
    private final Property<FileCollection> jarsToReportBreaks =
            getProject().getObjects().property(FileCollection.class);
    private final Property<FileCollection> oldApiJars =
            getProject().getObjects().property(FileCollection.class);
    private final Property<FileCollection> oldApiDependencyJars =
            getProject().getObjects().property(FileCollection.class);
    private final RegularFileProperty analysisResultsFile =
            getProject().getObjects().fileProperty();

    @Input
    public final SetProperty<AcceptedBreak> getAcceptedBreaks() {
        return acceptedBreaks;
    }

    @CompileClasspath
    public final Property<FileCollection> getNewApiJars() {
        return newApiJars;
    }

    @CompileClasspath
    public final Property<FileCollection> getNewApiDependencyJars() {
        return newApiDependencyJars;
    }

    @CompileClasspath
    public final Property<FileCollection> getJarsToReportBreaks() {
        return jarsToReportBreaks;
    }

    @CompileClasspath
    public final Property<FileCollection> getOldApiJars() {
        return oldApiJars;
    }

    @CompileClasspath
    public final Property<FileCollection> getOldApiDependencyJars() {
        return oldApiDependencyJars;
    }

    @OutputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public final RegularFileProperty getAnalysisResultsFile() {
        return analysisResultsFile;
    }

    @TaskAction
    protected final void runRevapi() throws Exception {
        API oldApi = api(oldApiJars, oldApiDependencyJars);
        API newApi = api(newApiJars, newApiDependencyJars);

        log.info("Old API: {}", oldApi);
        log.info("New API: {}", newApi);

        Revapi revapi = Revapi.builder()
                .withAllExtensionsFromThreadContextClassLoader()
                .withAnalyzers(JavaApiAnalyzer.class)
                .withReporters(TextReporter.class)
                .withTransforms(CheckWhitelist.class)
                .build();

        RevapiConfig revapiConfig = RevapiConfig.mergeAll(
                RevapiConfig.defaults(jarsToReportBreaks.get()),
                RevapiConfig.empty()
                        .withTextReporter(
                                "gradle-revapi-results.ftl",
                                analysisResultsFile.getAsFile().get()),
                revapiIgnores(),
                ConjureProjectFilters.forProject(getProject()));

        log.info("revapi config:\n{}", revapiConfig.configAsString());

        try (AnalysisResult analysisResult = revapi.analyze(AnalysisContext.builder()
                .withOldAPI(oldApi)
                .withNewAPI(newApi)
                // https://revapi.org/modules/revapi-java/extensions/java.html
                .withConfigurationFromJSON(revapiConfig.configAsString())
                .build())) {
            analysisResult.throwIfFailed();
        }
    }

    private RevapiConfig revapiIgnores() {
        return RevapiConfig.empty().withIgnoredBreaks(acceptedBreaks.get());
    }

    private API api(Provider<FileCollection> apiJars, Provider<FileCollection> dependencyJars) {
        return API.builder()
                .addArchives(toFileArchives(apiJars))
                .addSupportArchives(toFileArchives(dependencyJars))
                .build();
    }

    private static List<FileArchive> toFileArchives(Provider<FileCollection> property) {
        return property.get().filter(File::isFile).getFiles().stream()
                .map(FileArchive::new)
                .collect(Collectors.toList());
    }
}
