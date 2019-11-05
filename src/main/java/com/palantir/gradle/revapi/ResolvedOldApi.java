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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.palantir.gradle.revapi.config.Version;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableResolvedOldApi.class)
interface ResolvedOldApi {
    ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new GuavaModule());

    Version version();
    Set<File> directJars();
    Set<File> transitiveJars();

    class Builder extends ImmutableResolvedOldApi.Builder { }

    static Builder builder() {
        return new Builder();
    }

    default void writeToFile(File file) {
        file.getParentFile().mkdirs();
        try {
            OBJECT_MAPPER.writeValue(file, this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static ResolvedOldApi readFromFile(File file) {
        try {
            return OBJECT_MAPPER.readValue(file, ResolvedOldApi.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
