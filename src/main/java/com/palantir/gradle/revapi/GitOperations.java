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

import com.palantir.gradle.gitversion.GitExecOutput;
import com.palantir.gradle.gitversion.GitInvoker;
import java.util.List;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;

public abstract class GitOperations {

    private static final int TAGS_TO_RETURN = 3;

    @Nested
    protected abstract GitInvoker getGitInvoker();

    public final Provider<List<String>> previousGitTags() {
        return mergedTagsExcludingHead().zip(zeroZeroZeroHasParent(), GitOperations::filterAndLimit);
    }

    private Provider<List<String>> mergedTagsExcludingHead() {
        return getGitInvoker()
                .invokeWithResult(
                        "for-each-ref",
                        "--merged",
                        "HEAD",
                        "--no-contains",
                        "HEAD",
                        "--count=10",
                        "--sort=-version:refname",
                        "--format=%(refname:short)",
                        "refs/tags")
                .map(GitOperations::parseTags);
    }

    private static List<String> parseTags(GitExecOutput result) {
        if (result.exitCode() != 0) {
            return List.of();
        }
        String stdout = result.uncheckedStandardOut().strip();
        return stdout.isEmpty() ? List.of() : List.of(stdout.split("\\R"));
    }

    private static List<String> filterAndLimit(List<String> tags, boolean hasZeroZeroZeroParent) {
        return tags.stream()
                .filter(tag -> !"0.0.0".equals(tag) || hasZeroZeroZeroParent)
                .limit(TAGS_TO_RETURN)
                .map(GitOperations::stripVFromTag)
                .toList();
    }

    private Provider<Boolean> zeroZeroZeroHasParent() {
        return getGitInvoker()
                .invokeWithResult("cat-file", "-t", "0.0.0^")
                .map(result -> "commit".equals(result.uncheckedStandardOut()));
    }

    private static String stripVFromTag(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }
}
