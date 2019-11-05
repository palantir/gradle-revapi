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

import java.util.concurrent.TimeUnit

final class Git {
    private final File dir

    Git(File dir) {
        this.dir = dir
    }

    void initWithTestUser() {
        command 'git init'
        command 'git config user.name "Test User"'
        command 'git config user.email "test@example.com"'
    }

    public void command(String command) {
        def process = command.execute(Collections.emptyList(), dir)
        process.waitFor(1, TimeUnit.SECONDS)

        if (process.exitValue() != 0) {
            println process.in.text
            println process.err.text
            assert process.exitValue() == 0
        }
    }
}
