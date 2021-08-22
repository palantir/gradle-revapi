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

package com.palantir.gradle.revapi

import java.util.stream.Collectors
import nebula.test.AbstractProjectSpec

class GitVersionUtilsSpec extends AbstractProjectSpec {
    Git git

    def setup() {
        git = new Git(ourProjectDir)
        git.initWithTestUser()
    }

    def 'return nothing in a repo with no commits'() {
        when:
        // nothing
        1 == 1

        then:
        assert previousGitTags() == []
    }

    def 'return nothing in a repo with just one commit and no tags'() {
        when:
        git.command 'git commit --allow-empty -m "First"'

        then:
        assert previousGitTags() == []
    }

    def 'return nothing in a repo with just one commit before the only tag'() {
        when:
        git.command 'git commit --allow-empty -m "First"'
        git.command 'git commit --allow-empty -m "Second"'
        git.command 'git tag 2.0.0'

        then:
        assert previousGitTags() == []
    }

    def 'return one tag that is behind head'() {
        when:
        git.command 'git commit --allow-empty -m "First"'
        git.command 'git tag 1'
        git.command 'git commit --allow-empty -m "Second"'
        git.command 'git commit --allow-empty -m "Third"'

        then:
        assert previousGitTags() == ["1"]
    }

    def 'return a number of tags that are behind a tag'() {
        when:
        git.command 'git commit --allow-empty -m "First"'
        git.command 'git tag 1'
        git.command 'git commit --allow-empty -m "Second"'
        git.command 'git tag 2'
        git.command 'git commit --allow-empty -m "Third"'
        git.command 'git tag 3'

        git.command 'git commit --allow-empty -m "Fourth"'
        git.command 'git tag 4'

        then:
        assert previousGitTags() == ["3", "2", "1"]
    }

    def 'when the initial commit is 0.0.0, ignore it as its the first, unpublished release'() {
        when:
        git.command 'git commit --allow-empty -m "Initial"'
        git.command 'git tag 0.0.0'
        git.command 'git commit --allow-empty -m "Another"'

        then:
        assert previousGitTags().isEmpty()
    }

    def 'when a non-initial commit is 0.0.0, return it'() {
        when:
        git.command 'git commit --allow-empty -m "Initial"'
        git.command 'git commit --allow-empty -m "AnotherInitial"'
        git.command 'git tag 0.0.0'
        git.command 'git commit --allow-empty -m "Additional"'

        then:
        assert previousGitTags() == ['0.0.0']
    }

    def 'strips tags of v prefixes'() {
        when:
        git.command 'git commit --allow-empty -m "Initial"'
        git.command 'git tag v1.2.3'
        git.command 'git commit --allow-empty -m "Additional"'

        then:
        assert previousGitTags() == ['1.2.3']
    }

    def 'strips tags of configurable prefix'() {
        given: 'a tag prefix'
        def prefix = 'proj-'

        when:
        git.command 'git commit --allow-empty -m "Initial"'
        git.command 'git tag proj-1.2.3'
        git.command 'git commit --allow-empty -m "Additional"'

        then:
        assert previousGitTags(prefix) == ['1.2.3']
    }

    private List<String> previousGitTags() {
        GitVersionUtils.previousGitTags(getProject()).collect(Collectors.toList())
    }

    private List<String> previousGitTags(String tagPrefix) {
        GitVersionUtils.previousGitTags(getProject(), tagPrefix).collect(Collectors.toList())
    }
}
