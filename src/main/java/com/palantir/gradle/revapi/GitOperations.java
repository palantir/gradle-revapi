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
            return getProviderFactory().provider(List::of);
        }
        return previousGitTagFromRef(ref)
                .flatMap(tag -> previousGitTagsFromRef(tag, remaining - 1)
                        .map(previousTags -> Stream.concat(Stream.of(stripVFromTag(tag)), previousTags.stream())
                                .toList()))
                .orElse(List.of());
    }

    private Provider<String> previousGitTagFromRef(String ref) {
        String beforeLastRef = ref + "^";
        return commitExists(beforeLastRef).flatMap(exists -> {
            if (!exists) {
                return getProviderFactory().provider(() -> null);
            }
            Provider<String> tag = describeTagAt(beforeLastRef);
            return tag.flatMap(rawTag -> "0.0.0".equals(rawTag) ? keepTagIfHasParent("0.0.0") : tag);
        });
    }

    private Provider<String> keepTagIfHasParent(String tag) {
        return getGitInvoker()
                .invokeWithResult("rev-parse", "--verify", "--quiet", tag + "^")
                .map(result -> result.exitCode() == 0 ? tag : null);
    }

    private Provider<Boolean> commitExists(String ref) {
        return getGitInvoker()
                .invokeWithResult("cat-file", "-t", ref)
                .map(result -> result.uncheckedStandardOut().equals("commit"));
    }

    private Provider<String> describeTagAt(String ref) {
        return getGitInvoker()
                .invokeWithResult("describe", "--tags", "--abbrev=0", ref)
                .map(result -> {
                    String stderr = result.standardError();
                    if (stderr.contains("No tags can describe")
                            || stderr.contains("No names found, cannot describe anything")) {
                        return null;
                    }
                    return result.standardOutputOfSuccessfulCommand();
                });
    }

    private static String stripVFromTag(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }
}
