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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.gradle.api.Project;

final class GitVersionUtils {
    private GitVersionUtils() { }

    public static String previousGitTag(Project project) {
        return previousGitTagFromRef(project, "HEAD");
    }

    private static String previousGitTagFromRef(Project project, String ref) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        project.exec(spec -> {
            // this matches how gradle-git-version works, just with 'HEAD^' to avoid getting the current tag
            spec.setCommandLine("git", "describe", "--tags", "--always", "--abbrev=0", ref + "^");
            spec.setStandardOutput(baos);
        }).assertNormalExitValue();

        return new String(baos.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    public static Stream<String> previousGitTags(Project project) {
        final AtomicReference<String> lastSeenRef = new AtomicReference<>("HEAD");

        return Stream.generate(() -> {
            String tag = previousGitTagFromRef(project, lastSeenRef.get());
            lastSeenRef.set(tag);
            return tag;
        });
    }
}
