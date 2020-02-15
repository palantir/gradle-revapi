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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.flipkart.zjsonpatch.JsonDiff;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;

public final class YamlPatcher {
    private static final ObjectMapper JSON_PATCH_OBJECT_MAPPER = new ObjectMapper();

    private final ObjectMapper jsonObjectMapper;
    private final ObjectMapper yamlObjectMapper;

    public YamlPatcher(Consumer<ObjectMapper> objectMapperConfigurer) {
        this.jsonObjectMapper = new ObjectMapper();
        this.yamlObjectMapper = new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

        objectMapperConfigurer.accept(jsonObjectMapper);
        objectMapperConfigurer.accept(yamlObjectMapper);
    }

    public <T> String patchYaml(String input, Class<T> clazz, UnaryOperator<T> modifier) {
        try {
            JsonNode inputJsonNode = yamlObjectMapper.readTree(input);
            T inputType = yamlObjectMapper.convertValue(inputJsonNode, clazz);
            T outputType = modifier.apply(inputType);
            JsonNode outputJsonNode = jsonObjectMapper.convertValue(outputType, JsonNode.class);

            JsonNode diff = JsonDiff.asJson(inputJsonNode, outputJsonNode);
            Node jsonDocument = new Yaml().compose(new StringReader(input));

            return patchesFor(input, diff, jsonDocument).applyTo(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Patches patchesFor(String input, JsonNode diff, Node jsonDocument) {
        List<JsonPatch> jsonPatches = JSON_PATCH_OBJECT_MAPPER.convertValue(
                diff,
                new TypeReference<List<JsonPatch>>() {});

        return Patches.of(jsonPatches.stream()
                .map(jsonPatch -> jsonPatch.patchFor(yamlObjectMapper, input, jsonDocument)));
    }
}
