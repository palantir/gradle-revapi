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
import com.palantir.gradle.revapi.config.GroupAndName;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Revapi;
import org.revapi.java.JavaApiAnalyzer;
import org.revapi.reporter.text.TextReporter;
import org.revapi.simple.FileArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RevapiJavaTask extends DefaultTask {
    private static final Logger log = LoggerFactory.getLogger(RevapiJavaTask.class);

    private final Property<ConfigManager> configManager =
            getProject().getObjects().property(ConfigManager.class);

    private final Property<GroupAndName> oldGroupAndName =
            getProject().getObjects().property(GroupAndName.class);

    private final Property<Configuration> newApiDependencyJars =
            getProject().getObjects().property(Configuration.class);

    private final Property<FileCollection> newApiJars =
            getProject().getObjects().property(FileCollection.class);

    private final Property<File> oldApiFile =
            getProject().getObjects().property(File.class);

    @Nested
    final Property<ConfigManager> configManager() {
        return configManager;
    }

    @Input
    public final Property<GroupAndName> oldGroupAndName() {
        return oldGroupAndName;
    }

    public final Property<Configuration> newApiDependencyJars() {
        return newApiDependencyJars;
    }


    @InputFiles
    public final Property<FileCollection> newApiJars() {
        return newApiJars;
    }

    @InputFile
    public final Property<File> oldApiFile() {
        return oldApiFile;
    }

    protected final void runRevapi(RevapiConfig taskSpecificConfigJson) throws Exception {
        API oldApi = oldApi();
        API newApi = newApi();

        log.info("Old API: {}", oldApi);
        log.info("New API: {}", newApi);

        Revapi revapi = Revapi.builder()
                .withAllExtensionsFromThreadContextClassLoader()
                .withAnalyzers(JavaApiAnalyzer.class)
                .withReporters(TextReporter.class)
                .withTransforms(CheckWhitelist.class)
                .build();

        RevapiConfig revapiConfig = RevapiConfig.mergeAll(
                RevapiConfig.defaults(oldApi, newApi),
                taskSpecificConfigJson,
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
        Set<AcceptedBreak> acceptedBreaks = configManager.get()
                .fromFileOrEmptyIfDoesNotExist()
                .acceptedBreaksFor(oldGroupAndName.get());

        return RevapiConfig.empty()
                .withIgnoredBreaks(acceptedBreaks);
    }

    private API newApi() {
        List<FileArchive> newApiJar = toFileArchives(newApiJars);
        List<FileArchive> newApiDependencies = toFileArchives(newApiDependencyJars.get().resolve()
                .stream()
                .filter(File::isFile)
                .collect(Collectors.toSet()));

        return API.builder()
                .addArchives(newApiJar)
                .addSupportArchives(newApiDependencies)
                .build();
    }

    private API oldApi() {
        ResolvedOldApi resolvedOldApi = ResolvedOldApi.readFromFile(oldApiFile.get());
        return API.builder()
                .addArchives(toFileArchives(resolvedOldApi.directJars()))
                .addSupportArchives(toFileArchives(resolvedOldApi.transitiveJars()))
                .build();
    }

    private static List<FileArchive> toFileArchives(Property<FileCollection> fileCollectionProperty) {
        Set<File> files = fileCollectionProperty.get().getFiles();
        return toFileArchives(files);
    }

    private static List<FileArchive> toFileArchives(Set<File> files) {
        return files.stream()
                .map(FileArchive::new)
                .collect(Collectors.toList());
    }
}
