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

import com.palantir.gradle.revapi.config.Version;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

final class OldApiDependencyFile {
    private OldApiDependencyFile() { }

    public static AsInput asInput(File file) {
        return new AsInput(file);
    }

    public static AsOutput asOutput(File file) {
        return new AsOutput(file);
    }

    static final class AsInput {
        private final File file;

        AsInput(File file) {
            this.file = file;
        }

        @InputFile
        public File getFile() {
            return file;
        }

        public Version read() {
            try {
                return Version.fromString(new String(
                        Files.readAllBytes(getFile().toPath()),
                        StandardCharsets.UTF_8));

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static final class AsOutput {
        private final File file;

        AsOutput(File file) {
            this.file = file;
        }

        @OutputFile
        public File getFile() {
            return file;
        }

        public void write(Version groupNameVersion) {
            try {
                Files.createDirectories(getFile().toPath().getParent());
                Files.write(getFile().toPath(), groupNameVersion.asString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
