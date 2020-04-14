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

import java.util.Collection;
import java.util.stream.Collectors;
import org.gradle.api.Project;

final class ExceptionMessages {
    private static final String OLD_API_FAILED_TO_RESOLVE = "revapi-old-api-failed-to-resolve.txt";

    private ExceptionMessages() {}

    public static String failedToResolve(Project project, String errors) {
        String errorTemplate = Utils.resourceToString(ExceptionMessages.class, OLD_API_FAILED_TO_RESOLVE);

        return errorTemplate
                .replace("{{versionOverrideTaskName}}", RevapiPlugin.VERSION_OVERRIDE_TASK_NAME)
                .replace("{{replacementVersionOption}}", RevapiVersionOverrideTask.REPLACEMENT_VERSION_OPTION)
                .replace(
                        "{{taskPath}}",
                        project.getTasks()
                                .withType(RevapiVersionOverrideTask.class)
                                .getByName(RevapiPlugin.VERSION_OVERRIDE_TASK_NAME)
                                .getPath())
                .replace("{{projectDisplayName}}", project.getDisplayName())
                .replace("{{errors}}", errors);
    }

    public static String joined(Collection<? extends Throwable> throwables) {
        return throwables.stream().map(Throwable::getMessage).collect(Collectors.joining("\n\n"));
    }
}
