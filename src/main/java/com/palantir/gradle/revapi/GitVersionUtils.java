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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.process.ExecResult;
import org.immutables.value.Value;

final class GitVersionUtils {
    private GitVersionUtils() { }

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

    private static Optional<String> previousGitTagFromRef(Project project, String ref) {
        String beforeLastRef = ref + "^";

        GitResult beforeLastRefTypeResult = execute(project, "git", "cat-file", "-t", beforeLastRef);

        if (!beforeLastRefTypeResult.stdout().equals("commit")) {
            return Optional.empty();
        }

        GitResult describeResult = execute(project, "git", "describe", "--tags", "--abbrev=0", beforeLastRef);

        if (describeResult.stderr().contains("No tags can describe")) {
            return Optional.empty();
        }

        return Optional.of(describeResult.stdoutOfThrowIfNonZero());
    }

    private static GitResult execute(Project project, String... command) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        ExecResult execResult = project.exec(spec -> {
            spec.setCommandLine(Arrays.asList(command));
            spec.setStandardOutput(stdout);
            spec.setErrorOutput(stderr);
            spec.setIgnoreExitValue(true);
        });

        return GitResult.builder()
                .exitCode(execResult.getExitValue())
                .stdout(new String(stdout.toByteArray(), StandardCharsets.UTF_8).trim())
                .stderr(new String(stderr.toByteArray(), StandardCharsets.UTF_8).trim())
                .build();
    }

    @Value.Immutable
    interface GitResult {
        int exitCode();
        String stdout();
        String stderr();
        List<String> command();

        default String stdoutOfThrowIfNonZero() {
            if (exitCode() == 0) {
                return stdout();
            }

            throw new RuntimeException("Failed running command:\n"
                    + "\tCommand:" + command() + "\n"
                    + "\tExit code: " + exitCode() + "\n"
                    + "\tStdout:" + stdout() + "\n"
                    + "\tStderr:" + stderr() + "\n");
        }

        class Builder extends ImmutableGitResult.Builder { }

        static Builder builder() {
            return new Builder();
        }
    }
}
