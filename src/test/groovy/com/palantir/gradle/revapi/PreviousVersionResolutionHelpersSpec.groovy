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

import static com.palantir.gradle.revapi.PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import nebula.test.AbstractProjectSpec

class PreviousVersionResolutionHelpersSpec extends AbstractProjectSpec {
    def originalGroupName = 'original.group.name'

    def setup() {
        project.setGroup(originalGroupName)
    }

    def 'change group within the same thread'() {

        when:
        def group = withRenamedGroupForCurrentThread(project, { project.getGroup() })

        then:
        assert group != originalGroupName
        assert group.toString() != originalGroupName.toString()
    }

    def 'keep group the same from different thread'() {
        when:
        CountDownLatch threadLocalStarted = new CountDownLatch(1)
        CountDownLatch releaseThreadLocal = new CountDownLatch(1)

        ExecutorService background = Executors.newSingleThreadExecutor()
        background.submit({
            withRenamedGroupForCurrentThread(project, {
                threadLocalStarted.countDown()
                releaseThreadLocal.await(5, TimeUnit.SECONDS)
            })
        })

        threadLocalStarted.await(5, TimeUnit.SECONDS)
        def group = project.group
        releaseThreadLocal.countDown()

        then:
        assert group == originalGroupName
        assert group.toString() == originalGroupName
        assert group.hashCode() == originalGroupName.hashCode()
    }

    def 'reset the group to the original value afterwards'() {
        when:
        withRenamedGroupForCurrentThread(project, { })

        then:
        assert project.group.is(originalGroupName)
    }
}
