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
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Justification;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class RevapiAcceptAllBreaksTask extends DefaultTask {
    public static final String JUSTIFICATION = "justification";

    private final Property<ConfigManager> configManager = getProject().getObjects().property(ConfigManager.class);
    private final Property<GroupNameVersion> oldGroupNameVersion =
            getProject().getObjects().property(GroupNameVersion.class);

    private final RegularFileProperty resultsFile = getProject().getObjects().fileProperty();
    private final Property<Justification> justification = getProject().getObjects().property(Justification.class);

    @Nested
    final Property<ConfigManager> getConfigManager() {
        return configManager;
    }

    @Input
    final Property<GroupNameVersion> getOldGroupNameVersion() {
        return oldGroupNameVersion;
    }

    @InputFile
    final RegularFileProperty getResultsFile() {
        return resultsFile;
    }

    @Option(option = JUSTIFICATION, description = "Justification for why these breaks are ok")
    public final void setJustification(String justificationString) {
        this.justification.set(Justification.fromString(justificationString));
    }

    @TaskAction
    public final void addVersionOverride() {
        if (!justification.isPresent()) {
            throw new RuntimeException("Please supply the --" + JUSTIFICATION + " param to this task");
        }

        Set<AcceptedBreak> acceptedBreaks = RevapiResult.fromFile(resultsFile.getAsFile().get()).stream()
                .map(result -> result.toAcceptedBreak(justification.get()))
                .collect(Collectors.toSet());

        configManager.get().modifyConfigFile(config ->
                config.addAcceptedBreaks(oldGroupNameVersion.get(), acceptedBreaks));
    }
}
