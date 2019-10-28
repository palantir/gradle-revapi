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

package com.palantir.yamlpatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class YamlPatcher {
    private final ObjectMapper jsonObjectMapper;
    private final ObjectMapper yamlObjectMapper;

    public YamlPatcher(Consumer<ObjectMapper> objectMapperConfigurer) {
        this.jsonObjectMapper = new ObjectMapper();
        this.yamlObjectMapper = new ObjectMapper(new YAMLFactory());

        objectMapperConfigurer.accept(jsonObjectMapper);
        objectMapperConfigurer.accept(yamlObjectMapper);
    }

    public <T> String patchYaml(String input, Class<T> clazz, UnaryOperator<T> modifier) {
        try {
            T inputType = yamlObjectMapper.readValue(input, clazz);
            T outputType = modifier.apply(inputType);
            return yamlObjectMapper.writeValueAsString(outputType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
