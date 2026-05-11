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
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.revapi.utils.GitUtils;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.java.JavaFile;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.maven.MavenRepo;
import com.palantir.gradle.testing.project.GradleProject;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache("revapi plugin is incompatible with configuration cache")
class RevapiTest {

    @Test
    void fails_when_comparing_produced_jar_versus_some_random_other_jar(GradleInvoker gradle, RootProject rootProject) {
        rootProject.settingsGradle().rootProjectName("root-project");
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            dependencies {
                implementation 'one.util:streamex:0.7.0'
            }

            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = '0.11.1'
            }
            """);

        rootProject.mainSourceSet().java().fileByPath("foo/Foo.java").overwrite("""
            import one.util.streamex.StreamEx;

            public interface Foo {
                StreamEx<String> lol();
            }
            """);

        runRevapiExpectingToFindDifferences(gradle, rootProject, "root-project");
    }

    @Test
    void revapi_task_succeeds_when_there_are_no_breaking_changes(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""

            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
            """);

        gradle.withArgs("revapi").buildsSuccessfully();
    }

    @Test
    void doesnt_explode_when_project_code_depends_on_compileOnly_dependency(
            GradleInvoker gradle, RootProject rootProject, MavenRepo repo) {
        rootProject
                .buildGradle()
                .plugins()
                .add(TestConstants.PLUGIN_NAME)
                .add("java-library")
                .add("maven-publish");
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            allprojects {
                group = 'revapi.test'
                repositories {
                    maven { url uri('%s') }
                }
            }

            dependencies {
                implementation 'junit:junit:4.13'
            }

            revapi {
                oldVersion = project.version
            }
            """, repo.path());
        rootProject.buildGradle().append(testMavenPublication(repo));

        rootProject.mainSourceSet().java().fileByPath("foo/Foo.java").overwrite("""
            public class Foo extends org.junit.rules.ExternalResource { }
            """);

        gradle.withArgs("publish").buildsSuccessfully();

        rootProject.buildGradle().edit(text -> text.replace("implementation", "compileOnly"));

        gradle.withArgs("revapi").buildsSuccessfully();
    }

    @Test
    void revapiAcceptAllBreaks_succeeds_when_there_are_no_breaking_changes(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
            """);

        gradle.withArgs("revapiAcceptAllBreaks", "--justification", "fight me").buildsSuccessfully();
    }

    @Test
    void does_not_error_out_when_project_has_a_version_greater_than_the_old_version(
            GradleInvoker gradle, RootProject rootProject) {
        String revApiProjectName = "revapi";

        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            group = 'org.revapi'
            version = '0.12.0'

            revapi {
                oldVersion = '0.11.1'
            }
            """);

        rootProject.settingsGradle().rootProjectName(revapi);

        rootProject.mainSourceSet().java().fileByPath("foo/Foo.java").overwrite("""
            public interface Foo {
                String lol();
            }
            """);

        runRevapiExpectingToFindDifferences(gradle, rootProject, revapi);
    }

    @Test
    void errors_out_when_old_api_does_not_exist_but_works_after_version_override(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = 'does-not-exist'
            }
            """);

        rootProject.settingsGradle().rootProjectName("root-project");

        gradle.withArgs("revapiVersionOverride", "--replacement-version", "0.11.1")
                .buildsSuccessfully();

        runRevapiExpectingToFindDifferences(gradle, rootProject, "root-project");
    }

    @Test
    void errors_out_when_the_target_dependency_does_not_exist_and_we_do_not_give_an_version_override(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = 'does-not-exist'
            }
            """);

        rootProject.settingsGradle().rootProjectName("root-project");

        InvocationResult result = gradle.withArgs("revapi").buildsWithFailure();
        assertThat(result).output().contains("Failed to resolve old API");
    }

    @Test
    void skips_revapi_tasks_when_the_versions_to_check_is_empty_list(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersions = []
            }
            """);

        InvocationResult result = gradle.withArgs("revapi").buildsSuccessfully();
        assertThat(result).task(":revapiAnalyze").skipped();
        assertThat(result).task(":revapi").skipped();
    }

    @Test
    void when_the_previous_git_tag_has_failed_to_publish_it_will_look_back_up_to_a_further_git_tag(
            GradleInvoker gradle, RootProject rootProject, MavenRepo repo) throws IOException, InterruptedException {
        File projectDir = rootProject.path().toFile();
        GitUtils.gitInit(projectDir);

        rootProject.file(".gitignore").overwrite("""
            .gradle*/
            build/
            mavenRepo/
            """);

        rootProject.settingsGradle().rootProjectName("name");

        rootProject
                .buildGradle()
                .plugins()
                .add("com.palantir.git-version")
                .add(TestConstants.PLUGIN_NAME)
                .add("java-library")
                .add("maven-publish");
        rootProject.buildGradle().append("""
            group = 'group'
            version = gitVersion()
            """);
        rootProject.buildGradle().withMavenRepo(repo);
        rootProject.buildGradle().append(testMavenPublication(repo));

        rootProject.mainSourceSet().java().fileByPath("foo/Foo.java").overwrite("""
            public interface Foo {
                String willBeRemoved();
            }
            """);

        GitUtils.runCommands(projectDir, "add", ".");
        GitUtils.runCommands(projectDir, "commit", "-m", "0.1.0");
        GitUtils.runCommands(projectDir, "tag", "0.1.0");

        gradle.withArgs("publish").buildsSuccessfully();

        GitUtils.runCommands(projectDir, "commit", "--allow-empty", "-m", "publish-failed");
        GitUtils.runCommands(projectDir, "tag", "0.2.0");

        rootProject.mainSourceSet().java().fileByPath("foo/Foo.java").overwrite("""
            public interface Foo { }
            """);

        GitUtils.runCommands(projectDir, "commit", "-am", "new-work");

        InvocationResult result = gradle.withArgs("revapi").buildsWithFailure();
        assertThat(result).output().contains("willBeRemoved");
    }

    @Test
    void if_there_are_no_published_versions_of_the_library_at_all_revapi_doesnt_fail(
            GradleInvoker gradle, RootProject rootProject) {
        setupUnpublishedLibrary(rootProject);
        writeHelloWorld(rootProject);

        InvocationResult result = gradle.withArgs("revapi").buildsSuccessfully();
        assertThat(result).task(":revapiAnalyze").skipped();
        assertThat(result).task(":revapi").skipped();
    }

    @Test
    void if_there_are_no_published_versions_of_the_library_at_all_revapiAcceptAllBreaks_is_a_no_op(
            GradleInvoker gradle, RootProject rootProject) {
        setupUnpublishedLibrary(rootProject);
        writeHelloWorld(rootProject);

        InvocationResult result = gradle.withArgs("revapiAcceptAllBreaks").buildsSuccessfully();
        assertThat(result).task(":revapiAnalyze").skipped();
        assertThat(result).task(":revapiAcceptAllBreaks").skipped();
    }

    @Test
    void if_there_are_no_published_versions_of_the_library_at_all_revapiAcceptBreak_doesnt_fail(
            GradleInvoker gradle, RootProject rootProject) {
        setupUnpublishedLibrary(rootProject);
        writeHelloWorld(rootProject);

        InvocationResult result = gradle.withArgs(
                        "revapiAcceptBreak", "--justification", "foo", "--code", "bar", "--old", "old", "--new", "new")
                .buildsSuccessfully();
        assertThat(result).task(":revapiAcceptBreak").succeeded();
    }

    @Test
    void handles_the_output_of_extra_source_sets_being_added_to_compile_configuration(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            sourceSets {
                extraStuff
            }

            dependencies {
                implementation sourceSets.extraStuff.output
            }

            repositories {
                mavenCentral()
            }

            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = '0.11.1'
            }
            """);

        rootProject.settingsGradle().rootProjectName("root-project");

        runRevapiExpectingToFindDifferences(gradle, rootProject, "root-project");
    }

    @Test
    void errors_out_when_there_are_breaks_but_then_is_fine_when_breaks_are_accepted(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            revapi {
                oldGroup = 'junit'
                oldName = 'junit'
                oldVersion = '4.12'
            }
            """);

        rootProject.settingsGradle().rootProjectName("root-project");

        rootProject
                .yamlFile(".palantir/revapi.yml")
                .assertThat()
                .as("revapi.yml should not exist yet")
                .doesNotExist();
        gradle.withArgs("revapiAcceptAllBreaks", "--justification", "it's all good :)")
                .buildsSuccessfully();
        rootProject.yamlFile(".palantir/revapi.yml").assertThat().content().contains("java.class.removed");

        gradle.withArgs("revapi").buildsSuccessfully();
    }

    @Test
    void accepting_breaks_individually_should_work(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.settingsGradle().rootProjectName("root-project");

        gradle.withArgs(
                        "revapiAcceptBreak",
                        "--code",
                        "code1",
                        "--old",
                        "old1",
                        "--new",
                        "new1",
                        "--justification",
                        "j1")
                .buildsSuccessfully();

        gradle.withArgs("revapiAcceptBreak", "--code", "code2", "--old", "old2", "--justification", "j2")
                .buildsSuccessfully();

        gradle.withArgs("revapiAcceptBreak", "--code", "code3", "--new", "new3", "--justification", "j3")
                .buildsSuccessfully();

        String revapiYml = rootProject.yamlFile(".palantir/revapi.yml").text();
        assertThat(revapiYml)
                .contains("code: \"code1\"")
                .contains("old: \"old1\"")
                .contains("new: \"new1\"")
                .contains("justification: \"j1\"")
                .contains("code: \"code2\"")
                .contains("old: \"old2\"")
                .contains("justification: \"j2\"")
                .contains("code: \"code3\"")
                .contains("new: \"new3\"")
                .contains("justification: \"j3\"");
    }

    @Test
    void moving_a_class_from_one_project_to_a_dependent_project_is_not_a_break(
            GradleInvoker gradle, RootProject rootProject, MavenRepo repo, SubProject one, SubProject two)
            throws IOException {
        rootProject.buildGradle().append(sharedAllProjectsBlock(repo));
        configurePublishingProject(one, repo);
        configurePublishingProject(two, repo);

        one.buildGradle().plugins().add(TestConstants.PLUGIN_NAME);
        one.buildGradle().append("""
            dependencies {
                api project(':two')
            }

            revapi {
                oldVersion = project.version
            }
            """);

        JavaFile oneFoo = one.mainSourceSet().java().fileByPath("foo/Foo.java");
        oneFoo.overwrite("""
            package foo;
            public interface Foo {}
            """);

        gradle.withArgs("publish").buildsSuccessfully();

        two.mainSourceSet().java().fileByPath("foo/Foo.java").overwrite(oneFoo.text());
        Files.delete(oneFoo.path());

        gradle.withArgs("revapi").buildsSuccessfully();

        one.buildGradle().edit(text -> text.replace("api project", "implementation project"));

        InvocationResult result = gradle.withArgs("revapi").buildsWithFailure();
        assertThat(result).output().contains("java.class.removed");
    }

    @Test
    void ignores_breaks_in_dependent_projects(
            GradleInvoker gradle, RootProject rootProject, MavenRepo repo, SubProject one, SubProject two) {
        rootProject.buildGradle().append(sharedAllProjectsBlock(repo));
        configurePublishingProject(one, repo);
        configurePublishingProject(two, repo);

        one.buildGradle().plugins().add(TestConstants.PLUGIN_NAME);
        one.buildGradle().append("""
            dependencies {
                api project(':two')
            }

            revapi {
                oldVersion = project.version
            }
            """);

        one.mainSourceSet().java().fileByPath("foo/Bar.java").overwrite("""
            package foo;
            public interface Bar {
                Foo bar();
            }
            """);

        two.mainSourceSet().java().fileByPath("foo/Foo.java").overwrite("""
            package foo;
            public interface Foo {
            }
            """);

        gradle.withArgs("publish").buildsSuccessfully();

        two.mainSourceSet().java().fileByPath("foo/Foo.java").edit(text -> text.replace("}", "void foo();\n}"));

        gradle.withArgs("revapi").buildsSuccessfully();
    }

    @Test
    void should_not_say_there_are_breaks_in_api_dependencies_when_nothing_has_changed(
            GradleInvoker gradle, RootProject rootProject, MavenRepo repo) {
        rootProject.settingsGradle().rootProjectName("test");

        rootProject
                .buildGradle()
                .plugins()
                .add(TestConstants.PLUGIN_NAME)
                .add("java-library")
                .add("maven-publish");
        rootProject.buildGradle().append("""
            group = 'revapi'
            version = '1.0.0'

            repositories {
                mavenCentral()
            }

            dependencies {
                api 'junit:junit:4.12'
            }

            revapi {
                oldVersion = project.version
            }
            """);
        rootProject.buildGradle().withMavenRepo(repo);
        rootProject.buildGradle().append(testMavenPublication(repo));

        rootProject.mainSourceSet().java().fileByPath("foo/Foo.java").overwrite("""
            package foo;
            // Use an junit interface in our public api so revapi cares about it
            public interface Foo extends org.junit.rules.TestRule { }
            """);

        gradle.withArgs("publish").buildsSuccessfully();

        gradle.withArgs("revapi").buildsSuccessfully();
    }

    @Test
    void ignores_scala_classes(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            revapi {
                oldGroup = 'com.twitter'
                oldName = 'chill-avro_2.12'
                oldVersion = '0.9.3'
            }
            """);

        gradle.withArgs("revapi").buildsSuccessfully();
    }

    @Test
    void ignores_magic_methods_added_by_groovy_when_comparing_the_same_groovy_class(
            GradleInvoker gradle, RootProject rootProject, MavenRepo repo) {
        rootProject
                .buildGradle()
                .plugins()
                .add(TestConstants.PLUGIN_NAME)
                .add("groovy")
                .add("maven-publish");
        rootProject.buildGradle().append("""
            allprojects {
                group = 'revapi.test'
                repositories {
                    maven { url uri('%s') }
                }
            }

            version = '1.0.0'

            dependencies {
                 implementation localGroovy()
            }

            revapi {
                oldVersion = project.version
            }
            """, repo.path());
        rootProject.buildGradle().append(testMavenPublication(repo));

        rootProject.file("src/main/groovy/foo/Foo.groovy").overwrite("""
            package foo
            class Foo {}
            """);

        gradle.withArgs("publish").buildsSuccessfully();

        gradle.withArgs("revapi").buildsSuccessfully();
    }

    @Test
    void detects_breaks_in_groovy_code(GradleInvoker gradle, RootProject rootProject, MavenRepo repo) {
        rootProject
                .buildGradle()
                .plugins()
                .add(TestConstants.PLUGIN_NAME)
                .add("groovy")
                .add("maven-publish");
        rootProject.buildGradle().append("""
            allprojects {
                group = 'revapi.test'
                repositories {
                    maven { url uri('%s') }
                }
            }

            version = '1.0.0'

            dependencies {
                 implementation localGroovy()
            }

            revapi {
                oldVersion = project.version
            }
            """, repo.path());
        rootProject.buildGradle().append(testMavenPublication(repo));

        String groovyFile = "src/main/groovy/foo/Foo.groovy";

        rootProject.file(groovyFile).overwrite("""
            package foo
            class Foo {
                String someProperty
            }
            """);

        gradle.withArgs("publish").buildsSuccessfully();

        rootProject.file(groovyFile).overwrite("""
            package foo
            class Foo { }
            """);

        InvocationResult result = gradle.withArgs("revapi").buildsWithFailure();
        assertThat(result)
                .output()
                .contains("java.method.removed")
                .contains("method java.lang.String foo.Foo::getSomeProperty()")
                .contains("method void foo.Foo::setSomeProperty(java.lang.String)");
    }

    @Test
    void does_not_throw_exception_when_baseline_circleci_is_applied_before_this_plugin(
            GradleInvoker gradle, RootProject rootProject, SubProject subproject) {
        subproject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME);

        rootProject.buildGradle().plugins().add("com.palantir.baseline").add("com.palantir.baseline-circleci");

        gradle.withArgs("tasks").buildsSuccessfully();
    }

    @Test
    void is_up_to_date_when_nothing_has_changed_after_running_once(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
            """);

        assertThat(gradle.withArgs("revapi").buildsSuccessfully())
                .task(":revapiAnalyze")
                .succeeded();
        assertThat(gradle.withArgs("revapi").buildsSuccessfully())
                .task(":revapiAnalyze")
                .upToDate();
    }

    @Test
    void is_not_up_to_date_when_public_api_has_changed(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
            """);

        String javaFile = "foo/Foo.java";
        rootProject.mainSourceSet().java().fileByPath(javaFile).overwrite("""
            public class Foo {
                public void publicMethod() {}
                private void privateMethod() {}
            }
            """);

        assertThat(gradle.withArgs("revapi").buildsSuccessfully())
                .task(":revapiAnalyze")
                .succeeded();

        rootProject.mainSourceSet().java().fileByPath(javaFile).overwrite("""
            public class Foo {
                public void publicMethod() {}
                private void privateMethodRenamed() {}
            }
            """);

        assertThat(gradle.withArgs("revapi").buildsSuccessfully())
                .task(":revapiAnalyze")
                .upToDate();

        rootProject.mainSourceSet().java().fileByPath(javaFile).overwrite("""
            public class Foo {
                public void publicMethodRenamed() {}
                private void privateMethodRenamed() {}
            }
            """);

        assertThat(gradle.withArgs("revapi").buildsSuccessfully())
                .task(":revapiAnalyze")
                .succeeded();
    }

    @Test
    void compatible_with_gradle_shadow_jar(GradleInvoker gradle, RootProject rootProject, MavenRepo repo)
            throws IOException {
        rootProject.settingsGradle().rootProjectName("root");

        rootProject
                .buildGradle()
                .plugins()
                .add(TestConstants.PLUGIN_NAME)
                .add("java-library")
                .add("maven-publish");
        rootProject.buildGradle().append("""
            allprojects {
                group = 'revapi.test'
                repositories {
                    maven { url uri('%s') }
                }
            }

            version = '1.0.0'

            revapi {
                oldVersion = project.version
            }
            """, repo.path());
        rootProject.buildGradle().append(testMavenPublication(repo));

        String shadowedClass = "src/main/java/shadow/com/palantir/foo/Bar.java";
        rootProject.file(shadowedClass).overwrite("""
            package shadow.com.palantir.foo;
            public class Bar {}
            """);

        gradle.withArgs("publish").buildsSuccessfully();

        Files.delete(rootProject.file(shadowedClass).path());

        gradle.withArgs("revapi").buildsSuccessfully();
    }

    @Test
    void changing_a_protected_method_in_an_immutables_class_is_not_a_break(
            GradleInvoker gradle, RootProject rootProject, MavenRepo repo) {
        rootProject.settingsGradle().rootProjectName("root");

        rootProject
                .buildGradle()
                .plugins()
                .add(TestConstants.PLUGIN_NAME)
                .add("java-library")
                .add("maven-publish");
        rootProject.buildGradle().append("""
            allprojects {
                group = 'revapi.test'
                repositories {
                    maven { url uri('%s') }
                }
            }

            version = '1.0.0'

            revapi {
                oldVersion = project.version
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                annotationProcessor "org.immutables:value:2.8.8"
                compileOnly "org.immutables:value:2.8.8:annotations"
            }
            """, repo.path());
        rootProject.buildGradle().append(testMavenPublication(repo));

        List<MethodChange> methodChanges = List.of(
                new MethodChange(
                        "protected abstract String returnTypeChangedProtectedParam();",
                        "protected abstract int returnTypeChangedProtectedParam();",
                        false),
                new MethodChange(
                        "public abstract String returnTypeChangedPublicParam();",
                        "public abstract int returnTypeChangedPublicParam();",
                        true),
                new MethodChange("", "public abstract String newPublicParam();", false),
                new MethodChange("public abstract long removedPublicParam();", "", true),
                new MethodChange(
                        "public abstract long reducedVisibilityPublicParam();",
                        "protected abstract long reducedVisibilityPublicParam();",
                        true),
                new MethodChange(
                        "protected abstract long reducedVisibilityProtectedParam();",
                        "abstract long reducedVisibilityProtectedParam();",
                        false),
                new MethodChange(
                        "public abstract long noLongerAbstractPublicParam();",
                        "public long noLongerAbstractPublicParam() { return 3; }",
                        false),
                new MethodChange("protected abstract long removedProtectedParam();", "", false),
                new MethodChange(
                        "protected abstract long increasedVisibilityProtectedParam();",
                        "public abstract long increasedVisibilityProtectedParam();",
                        false),
                new MethodChange(
                        "public long nowAbstractPublicMethod() { return 3; }",
                        "public abstract long nowAbstractPublicMethod();",
                        false),
                new MethodChange(
                        "public String returnTypeChangedPublicMethod() { return \"foo\"; }",
                        "public int returnTypeChangedPublicMethod() { return 3; }",
                        true),
                new MethodChange("public void removedPublicMethod() {}", "", true));

        Function<MethodChange, String> selectOld = MethodChange::oldText;
        Function<MethodChange, String> selectNew = MethodChange::newText;

        writeImmutablesClass(rootProject, methodChanges, selectOld);

        gradle.withArgs("publish").buildsSuccessfully();

        writeImmutablesClass(rootProject, methodChanges, selectNew);

        InvocationResult result = gradle.withArgs("revapi").buildsWithFailure();
        assertThat(result).output().contains("There were Java public API/ABI breaks reported by revapi:");

        for (MethodChange methodChange : methodChanges) {
            if (methodChange.shouldBreak()) {
                assertThat(result)
                        .output()
                        .as("expected break to be reported for method %s", methodChange.findMethodName())
                        .contains(methodChange.findMethodName());
            } else {
                assertThat(result)
                        .output()
                        .as("expected no break to be reported for method %s", methodChange.findMethodName())
                        .doesNotContain(methodChange.findMethodName());
            }
        }
    }

    @Test
    void breaks_detected_in_conjure_projects_should_be_limited_to_those_which_break_java(
            GradleInvoker gradle, RootProject rootProject, MavenRepo repo) {
        rootProject.settingsGradle().rootProjectName("api");

        rootProject.buildGradle().plugins().add("com.palantir.conjure");
        rootProject.buildGradle().append("""
            allprojects {
                group = 'revapi.test'
                version = '1.0.0'

                repositories {
                    mavenCentral()
                    maven { url uri('%s') }
                }
            }

            dependencies {
                conjureCompiler 'com.palantir.conjure:conjure:4.49.0'
                conjureJava 'com.palantir.conjure.java:conjure-java:8.28.0'
            }
            """, repo.path());

        SubProject apiObjects = rootProject.subproject("api-objects");
        SubProject apiJersey = rootProject.subproject("api-jersey");
        SubProject apiUndertow = rootProject.subproject("api-undertow");
        for (SubProject sub : List.of(apiObjects, apiJersey, apiUndertow)) {
            sub.buildGradle()
                    .plugins()
                    .add("java-library")
                    .add(TestConstants.PLUGIN_NAME)
                    .add("maven-publish");
            sub.buildGradle().append("""
                revapi {
                    oldVersion = project.version
                }

                dependencies {
                    api 'com.palantir.conjure.java:conjure-lib:8.28.0'
                    api 'com.palantir.conjure.java:conjure-undertow-lib:8.28.0'
                }

                publishing {
                    publications {
                        publication(MavenPublication) {
                            from components.java
                        }
                    }
                    repositories {
                        maven { url uri('%s') }
                    }
                }
                """, repo.path());
        }

        String conjureYml = "src/main/conjure/conjure.yml";
        rootProject.yamlFile(conjureYml).overwrite("""
            services:
              RenamedService:
                name: RenamedService
                package: services
              TestService:
                name: TestService
                package: services
                endpoints:
                  renamed:
                    http: GET /renamed
                  swappedArgs:
                    http: GET /swappedArgs/{one}/{two}
                    args:
                      one: string
                      two: boolean
            """);

        gradle.withArgs("compileConjure", "publish").buildsSuccessfully();

        rootProject.yamlFile(conjureYml).overwrite("""
            services:
              RenamedToSomethingElseService:
                name: RenamedToSomethingElseService
                package: services
              TestService:
                name: TestService
                package: services
                endpoints:
                  added:
                    http: GET /added
                  renamedToSomethingElse:
                    http: GET /existing
                  swappedArgs:
                    http: GET /swappedArgs/{one}/{two}
                    args:
                      two: boolean
                      one: string
            """);

        gradle.withArgs("compileConjure").buildsSuccessfully();

        gradle.withArgs(":api-jersey:revapi").buildsWithFailure();

        apiJersey
                .buildDir()
                .file("junit-reports/revapi/revapi-api-jersey.xml")
                .assertThat()
                .content()
                .contains("java.class.removed-interface services.RenamedService")
                .contains("java.method.removed-method void services.TestService::renamed()")
                .contains("java.method.parameterTypeChanged-parameter void"
                        + " services.TestService::swappedArgs(===java.lang.String===, boolean)")
                .contains("java.method.parameterTypeChanged-parameter void"
                        + " services.TestService::swappedArgs(java.lang.String, ===boolean===)")
                .doesNotContain("services.TestService::added()")
                .doesNotContain("services.TestService::renamedToSomethingElse()")
                .doesNotContain("java.annotation.attributeValueChanged");
    }

    private static String testMavenPublication(MavenRepo repo) {
        return """
            publishing {
                publications {
                    publication(MavenPublication) {
                        from components.java
                    }
                }
                repositories {
                    maven { url uri('%s') }
                }
            }
            """.formatted(repo.path());
    }

    private static String sharedAllProjectsBlock(MavenRepo repo) {
        return """
            allprojects {
                group = 'revapi.test'
                version = '1.0.0'
                repositories {
                    maven { url uri('%s') }
                }
            }
            """.formatted(repo.path());
    }

    private static void configurePublishingProject(GradleProject project, MavenRepo repo) {
        project.buildGradle().plugins().add("java-library").add("maven-publish");
        project.buildGradle().append(testMavenPublication(repo));
    }

    private static void setupUnpublishedLibrary(RootProject rootProject) {
        rootProject.buildGradle().plugins().add(TestConstants.PLUGIN_NAME).add("java-library");
        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            revapi {
                oldGroup = 'does.not'
                oldName = 'exist'
                oldVersion = '1.0.0'
            }
            """);
    }

    private static void writeHelloWorld(RootProject rootProject) {
        rootProject.mainSourceSet().java().writeClass("""
            package hello;
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
            """);
    }

    private static void writeImmutablesClass(
            RootProject rootProject, List<MethodChange> methodChanges, Function<MethodChange, String> selector) {
        StringBuilder immutablesClassText = new StringBuilder();
        immutablesClassText.append("""
            package foo;

            import org.immutables.value.Value;

            @Value.Immutable
            @Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
            public abstract class Foo {
            """);

        for (MethodChange methodChange : methodChanges) {
            immutablesClassText
                    .append("    ")
                    .append(selector.apply(methodChange))
                    .append('\n');
        }

        immutablesClassText.append("}\n");

        rootProject.mainSourceSet().java().fileByPath("foo/Foo.java").overwrite(immutablesClassText.toString());
    }

    private static void runRevapiExpectingToFindDifferences(
            GradleInvoker gradle, RootProject rootProject, String projectName) {
        gradle.withArgs("revapi").buildsWithFailure().assertThat().output().contains("java.class.removed");
        rootProject
                .buildDir()
                .file("junit-reports/revapi/revapi-" + projectName + ".xml")
                .assertThat()
                .content()
                .contains("java.class.removed");
    }

    record MethodChange(String oldText, String newText, boolean shouldBreak) {
        private static final Pattern METHOD_NAME_PATTERN = Pattern.compile("(\\w+\\(\\))");

        String findMethodName() {
            String text = oldText.isEmpty() ? newText : oldText;
            Matcher matcher = METHOD_NAME_PATTERN.matcher(text);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Couldn't find method name in " + text);
            }
            return matcher.group(1);
        }
    }
}
