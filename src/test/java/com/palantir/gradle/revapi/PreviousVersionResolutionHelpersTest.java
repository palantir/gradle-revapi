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

import static com.palantir.gradle.revapi.PreviousVersionResolutionHelpers.withRenamedGroupForCurrentThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreviousVersionResolutionHelpersTest {
    private static final String ORIGINAL_GROUP_NAME = "original.group.name";

    @TempDir
    File tempDir;

    private Project project;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().withProjectDir(tempDir).build();
        project.setGroup(ORIGINAL_GROUP_NAME);
    }

    @Test
    void change_group_within_the_same_thread() {
        Object group = withRenamedGroupForCurrentThread(project, project::getGroup);

        assertThat(group).isNotEqualTo(ORIGINAL_GROUP_NAME);
        assertThat(group.toString()).isNotEqualTo(ORIGINAL_GROUP_NAME);
    }

    @Test
    void keep_group_the_same_from_different_thread() throws Exception {
        CountDownLatch threadLocalStarted = new CountDownLatch(1);
        CountDownLatch releaseThreadLocal = new CountDownLatch(1);

        ExecutorService background = Executors.newSingleThreadExecutor();
        Future<?> backgroundResult = background.submit(() -> withRenamedGroupForCurrentThread(project, () -> {
            threadLocalStarted.countDown();
            releaseThreadLocal.await(5, TimeUnit.SECONDS);
            return null;
        }));

        threadLocalStarted.await(5, TimeUnit.SECONDS);
        Object group = project.getGroup();
        releaseThreadLocal.countDown();
        backgroundResult.get(5, TimeUnit.SECONDS);
        background.shutdown();

        assertThat(group).isEqualTo(ORIGINAL_GROUP_NAME);
        assertThat(group.toString()).isEqualTo(ORIGINAL_GROUP_NAME);
        assertThat(group.hashCode()).isEqualTo(ORIGINAL_GROUP_NAME.hashCode());
    }

    @Test
    void reset_the_group_to_the_original_value_afterwards() {
        withRenamedGroupForCurrentThread(project, () -> null);

        assertThat(project.getGroup()).isSameAs(ORIGINAL_GROUP_NAME);
    }

    @Test
    void if_an_exception_is_thrown_it_will_reset_the_group_back_to_the_original_value() {
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> withRenamedGroupForCurrentThread(project, () -> {
                    throw new IOException();
                }));

        assertThat(project.getGroup()).isSameAs(ORIGINAL_GROUP_NAME);
    }
}
