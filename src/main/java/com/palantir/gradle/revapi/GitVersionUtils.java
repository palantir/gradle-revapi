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

import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.process.ExecSpec;

final class GitVersionUtils {
    private GitVersionUtils() { }

    private static Optional<String> previousGitTagFromRef(Project project, String ref) {
        String beforeLastRef = ref + "^";

        String refType = execute(project, spec -> {
            spec.setCommandLine("git", "cat-file", "-t", beforeLastRef);
            spec.setIgnoreExitValue(true);
        });

        if (!refType.equals("commit")) {
            return Optional.empty();
        }

        return Optional.of(execute(project, spec ->
                spec.setCommandLine("git", "describe", "--tags", "--always", "--abbrev=0", beforeLastRef)));
    }

    private static String execute(Project project, Consumer<ExecSpec> specAction) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        project.exec(spec -> {
            specAction.accept(spec);
            spec.setStandardOutput(baos);
            spec.setErrorOutput(ByteStreams.nullOutputStream());
        });

        return new String(baos.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    public static Stream<String> previousGitTags(Project project) {
        final AtomicReference<Optional<String>> lastSeenRef = new AtomicReference<>(Optional.of("HEAD"));

        Stream<Optional<String>> previousTags = Stream.generate(() -> {
            Optional<String> tag = lastSeenRef.get().flatMap(ref -> previousGitTagFromRef(project, ref));
            lastSeenRef.set(tag);
            return tag;
        });

        return Java9Streams.takeWhile(previousTags, Optional::isPresent)
                .map(Optional::get);
    }
}
