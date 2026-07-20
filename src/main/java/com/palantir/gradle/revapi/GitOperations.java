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
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;

/**
 * Walks back through git history to find the most recent release tags, used as candidate "previous versions" for
 * revapi to compare against.
 *
 * <p>Starting from {@code HEAD}:
 * <ol>
 *   <li>Move to the first parent commit ({@code ref^}) and verify it exists.
 *   <li>Run {@code git describe --tags --abbrev=0} to find the nearest reachable tag from that commit.
 *   <li>If the tag is the sentinel {@code 0.0.0}, only accept it when the commit has a parent (filters out the
 *       synthetic root-commit tag used when no real releases exist yet).
 *   <li>Feed that tag back in as the next ref and repeat.
 *   <li>Strip a leading {@code v}, filter the tags using the configured version pattern, and return up to
 *       {@link #TAGS_TO_RETURN} matches.
 * </ol>
 */
public abstract class GitOperations {

    private static final int TAGS_TO_RETURN = 3;

    @Nested
    protected abstract GitInvoker getGitInvoker();

    public final Provider<List<String>> previousGitTags() {
        return previousGitTags(Pattern.compile(".*"));
    }

    final Provider<List<String>> previousGitTags(Provider<String> versionPattern) {
        return versionPattern.flatMap(patternString -> previousGitTags(Pattern.compile(patternString)));
    }

    private Provider<List<String>> previousGitTags(Pattern versionPattern) {
        return previousGitTagFromRef("HEAD")
                .map(seed -> Stream.iterate(
                                seed,
                                Objects::nonNull,
                                ref -> previousGitTagFromRef(ref).getOrNull())
                        .map(GitOperations::stripVFromTag)
                        .filter(tag -> versionPattern.matcher(tag).matches())
                        .limit(TAGS_TO_RETURN)
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
                .map(result -> {
                    String stderr = result.standardError();
                    if (stderr.contains("No tags can describe")
                            || stderr.contains("No names found, cannot describe anything")
                            || stderr.contains("Not a valid object name")) {
                        return null;
                    }
                    return result.standardOutputOfSuccessfulCommand();
                });
    }

    private static String stripVFromTag(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }
}
