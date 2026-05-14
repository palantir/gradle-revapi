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
import com.palantir.gradle.utils.providers.Zipper;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;

public abstract class GitOperations {

    private static final int TAGS_TO_RETURN = 3;

    @Nested
    protected abstract GitInvoker getGitInvoker();

    @Nested
    protected abstract Zipper getZipper();

    public final Provider<List<String>> previousGitTags() {
        return getZipper().zip3(nonRootTags(), rootTag(), zeroZeroZeroHasParent(), GitOperations::filterAndLimit);
    }

    private Provider<List<String>> nonRootTags() {
        return getGitInvoker()
                .invokeWithResult(
                        "log",
                        "--topo-order",
                        "--simplify-by-decoration",
                        "--decorate-refs=refs/tags",
                        "--pretty=format:%D",
                        "--max-count=" + TAGS_TO_RETURN,
                        "HEAD^")
                .map(GitOperations::parseDecoratedTags);
    }

    // `--simplify-by-decoration` never emits the root commit, so we look it up separately
    private Provider<List<String>> rootTag() {
        return getGitInvoker()
                .invokeWithResult(
                        "log",
                        "--first-parent",
                        "--max-parents=0",
                        "--max-count=1",
                        "--decorate-refs=refs/tags",
                        "--pretty=format:%D",
                        "HEAD^")
                .map(GitOperations::parseDecoratedTags);
    }

    private static List<String> parseDecoratedTags(GitExecOutput result) {
        if (result.exitCode() != 0) {
            return List.of();
        }
        String stdout = result.uncheckedStandardOut().strip();
        if (stdout.isEmpty()) {
            return List.of();
        }
        return stdout.lines()
                .map(GitOperations::firstTagFromDecoration)
                .flatMap(Optional::stream)
                .toList();
    }

    // If a commit has multiple tags (e.g. `1.0.0-rc1` + `1.0.0`), any one is fine — same commit, same API.
    private static Optional<String> firstTagFromDecoration(String decoration) {
        return Arrays.stream(decoration.split(", "))
                .filter(part -> part.startsWith("tag: "))
                .map(part -> part.substring(5))
                .findFirst();
    }

    private static List<String> filterAndLimit(
            List<String> nonRootTags, List<String> rootTag, boolean hasZeroZeroZeroParent) {
        return Stream.concat(nonRootTags.stream(), rootTag.stream())
                .filter(tag -> !"0.0.0".equals(tag) || hasZeroZeroZeroParent)
                .limit(TAGS_TO_RETURN)
                .map(GitOperations::stripVFromTag)
                .toList();
    }

    private Provider<Boolean> zeroZeroZeroHasParent() {
        return getGitInvoker()
                .invokeWithResult("cat-file", "-t", "0.0.0^")
                .map(result -> "commit".equals(result.uncheckedStandardOut().strip()));
    }

    private static String stripVFromTag(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }
}
