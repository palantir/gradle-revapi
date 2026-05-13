/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.gitversion.GitInvoker;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;

public abstract class GitOperations {

    private static final int TAGS_TO_RETURN = 3;

    @Nested
    protected abstract GitInvoker getGitInvoker();

    public final Provider<List<String>> previousGitTags() {
        return previousGitTagFromRef("HEAD")
                .map(seed -> Stream.iterate(
                                seed,
                                Objects::nonNull,
                                ref -> previousGitTagFromRef(ref).getOrNull())
                        .limit(TAGS_TO_RETURN)
                        .map(GitOperations::stripVFromTag)
                        .toList())
                .orElse(List.of());
    }

    private Provider<String> previousGitTagFromRef(String ref) {
        String beforeLastRef = ref + "^";
        return commitExistsAt(beforeLastRef)
                .zip(describeReleaseTagAt(beforeLastRef), (exists, releaseTag) -> exists ? releaseTag : null);
    }

    private Provider<String> describeReleaseTagAt(String ref) {
        Provider<String> tag = describeTagAt(ref);
        return tag.flatMap(rawTag -> {
            if (!"0.0.0".equals(rawTag)) {
                return tag;
            }
            return commitExistsAt("0.0.0^").map(hasParent -> hasParent ? "0.0.0" : null);
        });
    }

    private Provider<Boolean> commitExistsAt(String ref) {
        return getGitInvoker()
                .invokeWithResult("cat-file", "-t", ref)
                .map(result -> "commit".equals(result.uncheckedStandardOut()));
    }

    private Provider<String> describeTagAt(String ref) {
        return getGitInvoker()
                .invokeWithResult("describe", "--tags", "--abbrev=0", ref)
                .map(result -> result.exitCode() == 0 ? result.standardOutputOfSuccessfulCommand() : null);
    }

    private static String stripVFromTag(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }
}
