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
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class RevapiAcceptAllBreaksTask extends DefaultTask {
    public static final String JUSTIFICATION = "justification";

    private final Property<ConfigManager> configManager =
            getProject().getObjects().property(ConfigManager.class);
    private final Property<GroupNameVersion> oldGroupNameVersion =
            getProject().getObjects().property(GroupNameVersion.class);

    private final RegularFileProperty analysisResultsFile =
            getProject().getObjects().fileProperty();
    private final Property<Justification> justification =
            getProject().getObjects().property(Justification.class);

    final Property<ConfigManager> getConfigManager() {
        return configManager;
    }

    final Property<GroupNameVersion> getOldGroupNameVersion() {
        return oldGroupNameVersion;
    }

    final RegularFileProperty getAnalysisResultsFile() {
        return analysisResultsFile;
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

        Set<AcceptedBreak> acceptedBreaks =
                AnalysisResults.fromFile(analysisResultsFile.getAsFile().get()).toAcceptedBreaks(justification.get());

        configManager
                .get()
                .modifyConfigFile(config -> config.addAcceptedBreaks(oldGroupNameVersion.get(), acceptedBreaks));
    }
}
