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

import com.palantir.gradle.revapi.config.GroupNameVersion;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class RevapiAcceptBreaksTask extends DefaultTask {
    public static final String JUSTIFICATION = "justification";

    private final Property<ConfigManager> configManager = getProject().getObjects().property(ConfigManager.class);
    private final Property<String> justification = getProject().getObjects().property(String.class);

    public final Property<ConfigManager> configManager() {
        return configManager;
    }

    @Option(option = JUSTIFICATION, description = "Justification for why this breaks are ok")
    public final void setJustification(String justificationString) {
        this.justification.set(justificationString);
    }

    @TaskAction
    public final void addVersionOverride() {
        if (!justification.isPresent()) {
            throw new RuntimeException("Please supply the --" + JUSTIFICATION + " param this task");
        }

        configManager.get().modifyConfigFile(config ->
                config.addVersionOverride(oldGroupNameVersion(), justification.get()));
    }

    private GroupNameVersion oldGroupNameVersion() {
        return getProject().getExtensions().getByType(RevapiExtension.class).oldGroupNameVersion();
    }
}
