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

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.git.Git;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache("revapi plugin is incompatible with configuration cache")
class GitOperationsTest {

    @BeforeEach
    void setUp(RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            tasks.register('printOldVersions') {
                doLast {
                    println "OLD_VERSIONS=" + revapi.oldVersions.get()
                }
            }
            """);
    }

    @Test
    void returns_nothing_in_a_repo_with_no_commits(GradleInvoker gradle, Git git) {
        // ensure git is initialised
        git.run("status");
        assertOldVersions(gradle, "[]");
    }

    @Test
    void returns_nothing_in_a_repo_with_just_one_commit_and_no_tags(GradleInvoker gradle, Git git) {
        git.commit("First");
        assertOldVersions(gradle, "[]");
    }

    @Test
    void returns_nothing_in_a_repo_with_just_one_commit_before_the_only_tag(GradleInvoker gradle, Git git) {
        git.commit("First");
        git.commit("Second");
        git.tag("2.0.0");
        assertOldVersions(gradle, "[]");
    }

    @Test
    void returns_one_tag_that_is_behind_head(GradleInvoker gradle, Git git) {
        git.commit("First");
        git.tag("1");
        git.commit("Second");
        git.commit("Third");
        assertOldVersions(gradle, "[1]");
    }

    @Test
    void returns_a_number_of_tags_that_are_behind_a_tag(GradleInvoker gradle, Git git) {
        git.commit("First");
        git.tag("1");
        git.commit("Second");
        git.tag("2");
        git.commit("Third");
        git.tag("3");
        git.commit("Fourth");
        git.tag("4");
        assertOldVersions(gradle, "[3, 2, 1]");
    }

    @Test
    void when_the_initial_commit_is_0_0_0_ignore_it_as_its_the_first_unpublished_release(
            GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.tag("0.0.0");
        git.commit("Another");
        assertOldVersions(gradle, "[]");
    }

    @Test
    void when_a_non_initial_commit_is_0_0_0_return_it(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("AnotherInitial");
        git.tag("0.0.0");
        git.commit("Additional");
        assertOldVersions(gradle, "[0.0.0]");
    }

    @Test
    void strips_tags_of_v_prefixes(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.tag("v1.2.3");
        git.commit("Additional");
        assertOldVersions(gradle, "[1.2.3]");
    }

    /*
     *  o ── o (1.0.0-rc0) ── o (1.0.0-rc1) ── o (1.0.0) ── o (HEAD)
     */
    @Test
    void returns_rc_chain_in_reverse_topological_order(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("Pre rc0");
        git.tag("1.0.0-rc0");
        git.commit("Pre rc1");
        git.tag("1.0.0-rc1");
        git.commit("Final release");
        git.tag("1.0.0");
        git.commit("Post release work");
        assertOldVersions(gradle, "[1.0.0, 1.0.0-rc1, 1.0.0-rc0]");
    }

    /*
     *  o ── o (2.0.0) ── o (1.0.0) ── o (HEAD)
     *
     * Tags emerge in commit order; the walker never reshuffles by semver.
     */
    @Test
    void returns_tags_in_topological_order_not_version_string_order(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("Tagged 2.0.0 by mistake");
        git.tag("2.0.0");
        git.commit("Tagged 1.0.0 after");
        git.tag("1.0.0");
        git.commit("HEAD");
        assertOldVersions(gradle, "[1.0.0, 2.0.0]");
    }

    /*
     *   o (committed Jan, tag 2.0.0) ── o (committed Mar, tag 1.0.0) ── o (HEAD)
     *
     * Topology beats author/committer date — guards against a stray `--sort` on describe.
     */
    @Test
    void returns_tags_in_topological_order_not_commit_date_order(GradleInvoker gradle, Git git) {
        Map<String, String> jan = Map.of(
                "GIT_AUTHOR_DATE", "2020-01-01T00:00:00",
                "GIT_COMMITTER_DATE", "2020-01-01T00:00:00");
        Map<String, String> mar = Map.of(
                "GIT_AUTHOR_DATE", "2020-03-01T00:00:00",
                "GIT_COMMITTER_DATE", "2020-03-01T00:00:00");
        git.commit("Initial");
        git.commit("Older commit, newer tag", jan);
        git.tag("2.0.0");
        git.commit("Newer commit, older tag", mar);
        git.tag("1.0.0");
        git.commit("HEAD");
        assertOldVersions(gradle, "[1.0.0, 2.0.0]");
    }

    /*
     *  o ── o ────────── M ── o (HEAD)        (default branch)
     *        \         /
     *         o (1.0.0-rc1)                   (feature)
     *
     * describe walks all parents, not first-parent-only — the `^` in the implementation is not a `^1`.
     */
    @Test
    void side_branch_tag_is_visible_after_merge(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("Pre feature");
        String defaultBranch = git.run("rev-parse", "--abbrev-ref", "HEAD").trim();
        git.run("checkout", "-b", "feature");
        git.commit("Feature work");
        git.tag("1.0.0-rc1");
        git.run("checkout", defaultBranch);
        git.run("merge", "--no-ff", "feature", "-m", "Merge feature");
        git.commit("Post merge");
        assertOldVersions(gradle, "[1.0.0-rc1]");
    }

    /*
     *  o ──────── M (1.0.0) ── o (HEAD)        (default branch)
     *   \      /
     *    o                                     (feature, second parent)
     */
    @Test
    void tag_on_merge_commit_is_returned(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        String defaultBranch = git.run("rev-parse", "--abbrev-ref", "HEAD").trim();
        git.run("checkout", "-b", "feature");
        git.commit("Feature");
        git.run("checkout", defaultBranch);
        git.run("merge", "--no-ff", "feature", "-m", "Merge feature");
        git.tag("1.0.0");
        git.commit("Post release");
        assertOldVersions(gradle, "[1.0.0]");
    }

    /*
     *  o ── o ── S ── o (HEAD)                  (default)
     *        \  ╱
     *         F (1.0.0-rc1)                     (feature, not reachable from S)
     *
     * Squash strips the second parent, so the tag is not in HEAD's ancestry.
     */
    @Test
    void squash_merge_does_not_expose_feature_branch_tag(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("Pre feature");
        String defaultBranch = git.run("rev-parse", "--abbrev-ref", "HEAD").trim();
        git.run("checkout", "-b", "feature");
        git.commit("Feature");
        git.tag("1.0.0-rc1");
        git.run("checkout", defaultBranch);
        git.run("merge", "--squash", "feature");
        git.commit("Squash from feature");
        git.commit("HEAD");
        assertOldVersions(gradle, "[]");
    }

    /*
     *                       ┌── o (2.0.0) ── o (HEAD: default)
     *  o ── o (1.0.0) ──────┤
     *                       └── o (1.0.1) (release/1.x)
     *
     * Hotfix on release/1.x is not an ancestor of develop, so HEAD on develop never sees it.
     */
    @Test
    void release_branch_hotfix_tag_is_invisible_from_develop(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("Pre 1.0.0");
        git.tag("1.0.0");
        String forkSha = git.run("rev-parse", "HEAD").trim();
        String defaultBranch = git.run("rev-parse", "--abbrev-ref", "HEAD").trim();

        git.run("checkout", "-b", "release/1.x", forkSha);
        git.commit("Hotfix on release");
        git.tag("1.0.1");

        git.run("checkout", defaultBranch);
        git.commit("Pre 2.0.0");
        git.tag("2.0.0");
        git.commit("Post 2.0.0");

        assertOldVersions(gradle, "[2.0.0, 1.0.0]");
    }

    /*
     *                       ┌── o (2.0.0) (default)
     *  o ── o (1.0.0) ──────┤
     *                       └── o (1.0.1) ── o (HEAD: release/1.x)
     *
     * Mirror of the previous test with HEAD on release/1.x: walker picks up the pre-fork `1.0.0` but never the
     * sibling-branch `2.0.0`.
     */
    @Test
    void release_branch_sees_pre_fork_develop_tag_but_not_post_fork(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("Pre 1.0.0");
        git.tag("1.0.0");
        String forkSha = git.run("rev-parse", "HEAD").trim();
        String defaultBranch = git.run("rev-parse", "--abbrev-ref", "HEAD").trim();

        git.run("checkout", defaultBranch);
        git.commit("Pre 2.0.0");
        git.tag("2.0.0");

        git.run("checkout", "-b", "release/1.x", forkSha);
        git.commit("Pre 1.0.1");
        git.tag("1.0.1");
        git.commit("Post hotfix");

        assertOldVersions(gradle, "[1.0.1, 1.0.0]");
    }

    /*
     *                       ┌── o (2.0.0) ── o (3.0.0) (default)
     *  o ── o (1.0.0) ──────┤
     *                       └── o (1.0.1-rc1) ── o (1.0.1) ── o (1.0.2) ── o (HEAD: release/1.x)
     *
     * Long-lived release branch with RC + multiple hotfixes. `.limit(3)` in RevapiExtension` hides the pre-fork
     * `1.0.0`; develop tags never appear.
     */
    @Test
    void long_lived_release_branch_walks_its_own_chain_not_develops(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("Pre 1.0.0");
        git.tag("1.0.0");
        String forkSha = git.run("rev-parse", "HEAD").trim();
        String defaultBranch = git.run("rev-parse", "--abbrev-ref", "HEAD").trim();

        git.run("checkout", defaultBranch);
        git.commit("Pre 2.0.0");
        git.tag("2.0.0");
        git.commit("Pre 3.0.0");
        git.tag("3.0.0");

        git.run("checkout", "-b", "release/1.x", forkSha);
        git.commit("Pre 1.0.1-rc1");
        git.tag("1.0.1-rc1");
        git.commit("Pre 1.0.1");
        git.tag("1.0.1");
        git.commit("Pre 1.0.2");
        git.tag("1.0.2");
        git.commit("Post 1.0.2");

        assertOldVersions(gradle, "[1.0.2, 1.0.1, 1.0.1-rc1]");
    }

    /*
     *  o (1.0.0) ── o (2.0.0, HEAD)
     *
     * The walker starts at `HEAD^`, so a tag at HEAD is excluded.
     */
    @Test
    void tag_at_head_is_excluded_but_prior_tags_are_returned(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("Pre 1.0.0");
        git.tag("1.0.0");
        git.commit("Pre 2.0.0");
        git.tag("2.0.0");
        assertOldVersions(gradle, "[1.0.0]");
    }

    /*
     *  o ── o (annotated tag: 1.0.0) ── o (HEAD)
     *
     * Other tests use lightweight tags; annotated tags are a distinct object type.
     */
    @Test
    void annotated_tags_walk_the_same_as_lightweight_tags(GradleInvoker gradle, Git git) {
        git.commit("Initial");
        git.commit("Pre 1.0.0");
        git.run("tag", "-a", "1.0.0", "-m", "Release 1.0.0");
        git.commit("HEAD");
        assertOldVersions(gradle, "[1.0.0]");
    }

    private static void assertOldVersions(GradleInvoker gradle, String expected) {
        assertThat(gradle.withArgs("printOldVersions").buildsSuccessfully())
                .output()
                .contains("OLD_VERSIONS=" + expected);
    }
}
