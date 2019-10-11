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

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class RevapiSpec extends IntegrationSpec {

    def 'fails when comparing produced jar versus some random other jar'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                compile 'one.util:streamex:0.7.0'
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = '0.11.1'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        def file = new File(projectDir, "src/main/java/foo/Foo.java")
        file.getParentFile().mkdirs()
        file << '''
            import one.util.streamex.StreamEx;

            public interface Foo {
                StreamEx<String> lol();
            }
        '''.stripIndent()

        then:
        runRevapiExpectingToFindDifferences("root-project")
    }

    def 'revapi task succeeds when there are no breaking changes'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
        """.stripIndent()

        then:
        runTasksSuccessfully("revapi")
    }

    def 'revapiAcceptAllBreaks succeeds when there are no breaking changes'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }

            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
        """.stripIndent()

        then:
        runTasksSuccessfully("revapiAcceptAllBreaks", "--justification", "fight me")
    }

    def 'does not error out when project has a version greater than the "old version"'() {
        def revapi = 'revapi'

        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            group = 'org.revapi'
            version = '0.12.0'
            
            revapi {
                oldVersion = '0.11.1'
            }
        """.stripIndent()

        rootProjectNameIs(revapi)

        def file = new File(projectDir, "src/main/java/foo/Foo.java")
        file.getParentFile().mkdirs()
        file << '''
            public interface Foo {
                String lol();
            }
        '''.stripIndent()

        then:
        runRevapiExpectingToFindDifferences(revapi)
    }

    def 'errors out when the old api dependency does not exist but then works once you run the version override task'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = 'does-not-exist'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        and:
        runTasksSuccessfully("revapiVersionOverride", "--replacement-version", "0.11.1")

        then:
        runRevapiExpectingToFindDifferences("root-project")
    }

    def 'errors out when the target dependency does not exist and we do not give an version override'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = 'does-not-exist'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        then:
        runRevapiExpectingResolutionFailure("root-project")
    }

    def 'handles the output of extra source sets being added to compile configuration'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            sourceSets {
                extraStuff
            }
            
            dependencies {
                compile sourceSets.extraStuff.output
            }
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = '0.11.1'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        then:
        runRevapiExpectingToFindDifferences("root-project")
    }

    def 'errors out when there are breaks but then is fine when breaks are accepted'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'junit'
                oldName = 'junit'
                oldVersion = '4.12'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")
        File revapiYml = new File(getProjectDir(), ".palantir/revapi.yml")

        and:
        !revapiYml.exists()
        runTasksSuccessfully("revapiAcceptAllBreaks", "--justification", "it's all good :)")
        revapiYml.text.contains('java.class.removed')

        then:
        runTasksSuccessfully("revapi")
    }

    def 'accepting breaks individually should work'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        and:
        runTasksSuccessfully("revapiAcceptBreak",
                "--code", "code1",
                "--old", "old1",
                "--new", "new1",
                "--justification", "j1")

        runTasksSuccessfully("revapiAcceptBreak",
                "--code", "code2",
                "--old", "old2",
                "--justification", "j2")

        runTasksSuccessfully("revapiAcceptBreak",
                "--code", "code3",
                "--new", "new3",
                "--justification", "j3")

        then:
        def revapiYml = file('.palantir/revapi.yml').text
        assert revapiYml.contains('code: "code1"')
        assert revapiYml.contains('old: "old1"')
        assert revapiYml.contains('new: "new1"')
        assert revapiYml.contains('justification: "j1"')

        assert revapiYml.contains('code: "code2"')
        assert revapiYml.contains('old: "old2"')
        assert revapiYml.contains('justification: "j2"')

        assert revapiYml.contains('code: "code3"')
        assert revapiYml.contains('new: "new3"')
        assert revapiYml.contains('justification: "j3"')

    }

    def 'ignores scala classes'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'com.twitter'
                oldName = 'chill-avro_2.12'
                oldVersion = '0.9.3'
            }
        """.stripIndent()

        then:
        runTasksSuccessfully("revapi")
    }

    def 'ignores magic methods added by groovy when comparing the same groovy class'() {
        when:
        String version = UUID.randomUUID().toString()

        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'groovy'
            apply plugin: 'maven-publish'
            
            allprojects {
                group = 'revapi.test'
                repositories {
                    mavenLocal()
                }
            }
            
            version = '${version}'
            
            dependencies {
                 compile localGroovy()
            }
            
            revapi {
                oldVersion = '${version}'
            }
            
            publishing {
              publications {
                publication(MavenPublication) {
                    from components.java
                }
              }
              repositories {
                  mavenLocal()
              }
            }
        """.stripIndent()

        def groovyClassPath = 'src/main/groovy/foo/Foo.groovy'
        def groovyClassSource = '''
            package foo
            class Foo {
                String someProperty
            }
        '''.stripIndent()

        writeToFile projectDir, groovyClassPath, groovyClassSource

        println runTasksSuccessfully("publishToMavenLocal").standardOutput

        then:
        println runTasksSuccessfully("revapi").standardOutput
    }

    public void writeToFile(File dir, String filename, String content) {
        def file = new File(dir, filename)
        file.getParentFile().mkdirs()
        file << content
    }

    private File rootProjectNameIs(String projectName) {
        settingsFile << "rootProject.name = '${projectName}'"
    }

    private void runRevapiExpectingToFindDifferences(String projectName) {
        runRevapiExpectingStderrToContain("java.class.removed")
        andJunitXmlToHaveBeenProduced(projectName)
    }

    private void runRevapiExpectingResolutionFailure(String projectName) {
        runRevapiExpectingStderrToContain("Failed to resolve old API")
    }

    private void runRevapiExpectingStderrToContain(String stderrContains) {
        ExecutionResult executionResult = runTasksWithFailure("revapi")
        println executionResult.standardOutput
        println executionResult.standardError
        assert executionResult.standardError.contains(stderrContains)
    }

    private void andJunitXmlToHaveBeenProduced(String projectName) {
        File junitOutput = new File(projectDir, "build/junit-reports/revapi/revapi-${projectName}.xml")
        new XmlParser().parse(junitOutput)
        assert junitOutput.text.contains("java.class.removed")
    }
}
