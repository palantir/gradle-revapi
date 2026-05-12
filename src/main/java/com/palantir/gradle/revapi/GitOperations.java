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
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Nested;

public abstract class GitOperations {

    private static final int TAGS_TO_RETURN = 3;

    @Nested
    protected abstract GitInvoker getGitInvoker();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    public final Provider<List<String>> previousGitTags() {
        return previousGitTagsFromRef("HEAD", TAGS_TO_RETURN);
    }

    private Provider<List<String>> previousGitTagsFromRef(String ref, int remaining) {
        if (remaining == 0) {
            return emptyList();
        }
        return previousGitTagFromRef(ref)
                .flatMap(rawTag -> rawTag.map(tag -> previousGitTagsFromRef(tag, remaining - 1)
                                .map(previousTags -> Stream.concat(Stream.of(stripVFromTag(tag)), previousTags.stream())
                                        .toList()))
                        .orElseGet(this::emptyList));
    }

    private Provider<Optional<String>> previousGitTagFromRef(String ref) {
        String beforeLastRef = ref + "^";
        return commitExists(beforeLastRef).flatMap(exists -> {
            if (!exists) {
                return emptyOptional();
            }
            Provider<Optional<String>> describedTag = describeTagAt(beforeLastRef);
            return describedTag.flatMap(rawTag ->
                    rawTag.filter("0.0.0"::equals).map(this::keepTagIfHasParent).orElse(describedTag));
        });
    }

    private Provider<Optional<String>> keepTagIfHasParent(String tag) {
        return getGitInvoker()
                .invokeWithResult("rev-parse", "--verify", "--quiet", tag + "^")
                .filter(parentResult -> parentResult.exitCode() == 0)
                .map(_parentResult -> Optional.of(tag))
                .orElse(Optional.empty());
    }

    private Provider<Boolean> commitExists(String ref) {
        return getGitInvoker()
                .invokeWithResult("cat-file", "-t", ref)
                .map(result -> result.uncheckedStandardOut().equals("commit"));
    }

    private Provider<Optional<String>> describeTagAt(String ref) {
        return getGitInvoker()
                .invokeWithResult("describe", "--tags", "--abbrev=0", ref)
                .map(result -> {
                    String stderr = result.standardError();
                    if (stderr.contains("No tags can describe")
                            || stderr.contains("No names found, cannot describe anything")) {
                        return Optional.empty();
                    }
                    return Optional.of(result.standardOutputOfSuccessfulCommand());
                });
    }

    private Provider<Optional<String>> emptyOptional() {
        return getProviderFactory().provider(Optional::empty);
    }

    private Provider<List<String>> emptyList() {
        return getProviderFactory().provider(List::of);
    }

    private static String stripVFromTag(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }
}
