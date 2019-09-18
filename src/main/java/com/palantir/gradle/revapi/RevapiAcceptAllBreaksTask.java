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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.gradle.revapi.config.AcceptedBreak;
import com.palantir.gradle.revapi.config.GradleRevapiConfig;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Justification;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class RevapiAcceptAllBreaksTask extends RevapiJavaTask {
    private static final ObjectMapper OBJECT_MAPPER = GradleRevapiConfig.newRecommendedObjectMapper();
    public static final String JUSTIFICATION = "justification";

    private final Property<GroupNameVersion> oldGroupNameVersion =
            getProject().getObjects().property(GroupNameVersion.class);

    private final Property<Justification> justification = getProject().getObjects().property(Justification.class);

    @Input
    final Property<GroupNameVersion> getOldGroupNameVersion() {
        return oldGroupNameVersion;
    }

    @Option(option = JUSTIFICATION, description = "Justification for why these breaks are ok")
    public final void setJustification(String justificationString) {
        this.justification.set(Justification.fromString(justificationString));
    }

    @TaskAction
    public final void addVersionOverride() throws Exception {
        if (!justification.isPresent()) {
            throw new RuntimeException("Please supply the --" + JUSTIFICATION + " param to this task");
        }

        File breaksPath = Files.createTempFile("reavpi-breaks", ".yml").toFile();

        runRevapi(RevapiJsonConfig.empty()
                .withTextReporter("gradle-revapi-accept-breaks.ftl", breaksPath));

        List<AcceptedBreak> rawAcceptedBreaks =
                OBJECT_MAPPER.readValue(breaksPath, new TypeReference<List<AcceptedBreak>>() {});

        Set<AcceptedBreak> acceptedBreaks = rawAcceptedBreaks.stream()
                .map(rawAcceptedBreak -> AcceptedBreak.builder()
                        .from(rawAcceptedBreak)
                        .justification(justification.get())
                        .build())
                .collect(Collectors.toSet());

        configManager().get().modifyConfigFile(config ->
                config.addAcceptedBreaks(oldGroupNameVersion.get(), acceptedBreaks));
    }
}
