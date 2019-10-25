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
import com.palantir.gradle.revapi.config.GradleRevapiConfig;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Justification;
import com.palantir.gradle.revapi.config.v2.AcceptedBreak;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class RevapiAcceptAllBreaksTask extends RevapiJavaTask {
    private static final ObjectMapper OBJECT_MAPPER = GradleRevapiConfig.newJsonObjectMapper();
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

        Path tempDir = getProject().getLayout().getBuildDirectory().dir("tmp").get().getAsFile().toPath();

        File breaksPath = Files.createTempFile(tempDir, "revapi-breaks", ".json").toFile();

        runRevapi(RevapiConfig.empty()
                .withTextReporter("gradle-revapi-accept-breaks.ftl", breaksPath));

        Set<AcceptedBreak> acceptedBreaks =
                OBJECT_MAPPER.readValue(breaksPath, new TypeReference<Set<AcceptedBreak>>() {});

        configManager().get().modifyConfigFile(config ->
                config.addAcceptedBreaks(oldGroupNameVersion.get(), justification.get(), acceptedBreaks));
    }
}
