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

import com.palantir.gradle.revapi.config.GroupAndName;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Version;
import java.util.Collections;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

@SuppressWarnings("DesignForExtension")
public class RevapiExtension {
    private final Property<String> oldGroup;
    private final Property<String> oldName;
    private final ListProperty<String> olderVersions;

    public RevapiExtension(Project project) {
        this.oldGroup = project.getObjects().property(String.class);
        this.oldGroup.set(project.getProviders().provider(() -> project.getGroup().toString()));

        this.oldName = project.getObjects().property(String.class);
        this.oldName.set(project.getProviders().provider(project::getName));

        this.olderVersions = project.getObjects().listProperty(String.class);
        this.olderVersions.set(project.getProviders().provider(() ->
                GitVersionUtils.previousGitTags(project)
                        .limit(3)
                        .collect(Collectors.toList())));
    }

    public Property<String> getOldGroup() {
        return oldGroup;
    }

    public Property<String> getOldName() {
        return oldName;
    }

    public ListProperty<String> getOlderVersions() {
        return olderVersions;
    }

    public void setOldVersion(String oldVersionValue) {
        olderVersions.set(Collections.singletonList(oldVersionValue));
    }

    GroupNameVersion oldGroupNameVersion() {
        return GroupNameVersion.builder()
                .groupAndName(oldGroupAndName())
                .version(Version.fromString(olderVersions.get().get(0)))
                .build();
    }

    GroupAndName oldGroupAndName() {
        return GroupAndName.builder()
                .group(oldGroup.get())
                .name(oldName.get())
                .build();
    }
}
