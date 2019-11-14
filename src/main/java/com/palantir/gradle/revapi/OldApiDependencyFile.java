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

    public static AsInputOutput forFile(File file) {
        return new Impl(file);
    }

    private static final class Impl implements AsInputOutput {
        private final File file;

        Impl(File file) {
            this.file = file;
        }

        public File file() {
            return file;
        }
    }

    interface AsInputOutput extends AsInput, AsOutput { }

    interface AsInput {
        @InputFile
        File file();

        default Version read() {
            try {
                return Version.fromString(new String(
                        Files.readAllBytes(file().toPath()),
                        StandardCharsets.UTF_8));

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    interface AsOutput {
        @OutputFile
        File file();

        default void write(Version groupNameVersion) {
            try {
                Files.createDirectories(file().toPath().getParent());
                Files.write(file().toPath(), groupNameVersion.asString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
