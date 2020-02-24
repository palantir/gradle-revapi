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

public class RevapiVersionOverrideTask extends DefaultTask {
    public static final String REPLACEMENT_VERSION_OPTION = "replacement-version";

    private final Property<ConfigManager> configManager =
            getProject().getObjects().property(ConfigManager.class);
    private final Property<String> replacementVersion =
            getProject().getObjects().property(String.class);

    final Property<ConfigManager> getConfigManager() {
        return configManager;
    }

    @Option(option = REPLACEMENT_VERSION_OPTION, description = "The version to use instead of the default oldVersion")
    public final void setReplacementVersion(String replacementVersionValue) {
        replacementVersion.set(replacementVersionValue);
    }

    @TaskAction
    public final void addVersionOverride() {
        if (!replacementVersion.isPresent()) {
            throw new RuntimeException("Please supply the --" + REPLACEMENT_VERSION_OPTION + " param this task");
        }

        configManager
                .get()
                .modifyConfigFile(config -> config.addVersionOverride(oldGroupNameVersion(), replacementVersion.get()));
    }

    private GroupNameVersion oldGroupNameVersion() {
        return getProject().getExtensions().getByType(RevapiExtension.class).oldGroupNameVersion();
    }
}
