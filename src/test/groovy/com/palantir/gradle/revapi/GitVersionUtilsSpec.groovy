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

    private List<String> previousGitTags() {
        GitVersionUtils.previousGitTags(getProject()).collect(Collectors.toList())
    }
}
