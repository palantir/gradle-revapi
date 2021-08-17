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

import nebula.test.AbstractProjectSpec
import org.gradle.api.plugins.JavaPlugin

class GradleUtilsSpec extends AbstractProjectSpec {
    def 'find all jar tasks from all subprojects'() {
        when:
        def subsubproject = addSubproject('subsubproject')
        subsubproject.pluginManager.apply(JavaPlugin)

        def subproject = addSubproject('subproject')
        subproject.pluginManager.apply(JavaPlugin)
        subproject.dependencies {
            implementation project.project(":${subsubproject.name}")
        }

        project.pluginManager.apply(JavaPlugin)
        project.dependencies {
            implementation project.project(":${subproject.name}")
        }

        then:
        def jarTasks = RevapiPlugin.allJarTasksIncludingDependencies(project, project.configurations.named('runtimeClasspath').get())
        assert jarTasks.get() == [
                project.getTasks().getByName('jar'),
                subproject.getTasks().getByName('jar'),
                subsubproject.getTasks().getByName('jar'),
        ] as Set
    }
}
