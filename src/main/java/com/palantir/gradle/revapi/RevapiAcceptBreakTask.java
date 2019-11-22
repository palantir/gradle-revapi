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
import java.util.Collections;
import java.util.Optional;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class RevapiAcceptBreakTask extends DefaultTask {
    private static final String CODE_OPTION = "code";
    private static final String OLD_OPTION = "old";
    private static final String NEW_OPTION = "new";
    private static final String JUSTIFICATION_OPTION = "justification";

    private final Property<ConfigManager> configManager = getProject().getObjects().property(ConfigManager.class);
    private final Property<String> code = getProject().getObjects().property(String.class);
    private final Property<String> oldElement = getProject().getObjects().property(String.class);
    private final Property<String> newElement = getProject().getObjects().property(String.class);
    private final Property<Justification> justification = getProject().getObjects().property(Justification.class);

    final Property<ConfigManager> getConfigManager() {
        return configManager;
    }

    @Option(option = CODE_OPTION, description = "Revapi change code")
    public final void setCode(String codeString) {
        this.code.set(codeString);
    }

    @Option(option = OLD_OPTION, description = "Old API element")
    public final void setOldElement(String oldElementString) {
        this.oldElement.set(oldElementString);
    }

    @Option(option = NEW_OPTION, description = "New API element")
    public final void setNewElement(String newElementString) {
        this.newElement.set(newElementString);
    }

    @Option(option = JUSTIFICATION_OPTION, description = "Justification for why these breaks are ok")
    public final void setJustification(String justificationString) {
        this.justification.set(Justification.fromString(justificationString));
    }

    @TaskAction
    public final void addVersionOverride() {
        ensurePresent(code, CODE_OPTION);
        ensurePresent(justification, JUSTIFICATION_OPTION);

        configManager.get().modifyConfigFile(revapiConfig ->
                revapiConfig.addAcceptedBreaks(oldGroupNameVersion(), Collections.singleton(AcceptedBreak.builder()
                        .code(code.get())
                        .oldElement(Optional.ofNullable(oldElement.getOrNull()))
                        .newElement(Optional.ofNullable(newElement.getOrNull()))
                        .justification(justification.get())
                        .build()
        )));
    }

    private void ensurePresent(Property<?> prop, String option) {
        if (!prop.isPresent()) {
            throw new IllegalArgumentException("Please supply the --" + option + " param to this task");
        }
    }

    private GroupNameVersion oldGroupNameVersion() {
        return getProject().getExtensions().getByType(RevapiExtension.class).oldGroupNameVersion();
    }
}
