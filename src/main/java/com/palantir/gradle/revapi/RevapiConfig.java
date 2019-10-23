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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.palantir.gradle.revapi.config.AcceptedBreak;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.immutables.value.Value;
import org.revapi.API;
import org.revapi.Archive;

@Value.Immutable
abstract class RevapiConfig {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new Jdk8Module());

    protected abstract List<JsonNode> config();

    public String configAsString() {
        return config().toString();
    }

    public RevapiConfig withTextReporter(String templateName, File outputPath) {
        return withExtension("revapi.reporter.text", OBJECT_MAPPER.createObjectNode()
                // .put("minSeverity", "BREAKING")
                .put("template", templateName)
                .put("output", outputPath.getAbsolutePath())
                .put("append", false));
    }

    public RevapiConfig withIgnoredBreaks(Set<AcceptedBreak> acceptedBreaks) {
        return withExtension("revapi.ignore", OBJECT_MAPPER.convertValue(acceptedBreaks, ArrayNode.class));
    }

    private RevapiConfig withExtension(String extensionId, JsonNode configuration) {
        JsonNode extension = OBJECT_MAPPER.createObjectNode()
                .put("extension", extensionId)
                .set("configuration", configuration);

        return fromJsonNodes(ImmutableList.<JsonNode>builder()
                .addAll(config())
                .add(extension)
                .build());
    }

    public RevapiConfig mergeWith(RevapiConfig other) {
        return new Builder()
                .from(this)
                .addAllConfig(other.config())
                .build();
    }

    public static RevapiConfig defaults(API oldApi, API newApi) {
        try {
            String template = Resources.toString(
                    Resources.getResource(
                            "revapi-configuration.json"),
                    StandardCharsets.UTF_8);

            return fromString(template
                    .replace("{{ARCHIVE_INCLUDE_REGEXES}}", Stream.of(newApi, oldApi)
                            .flatMap(api -> StreamSupport.stream(api.getArchives().spliterator(), false))
                            .map(Archive::getName)
                            .collect(Collectors.joining("\", \""))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static RevapiConfig mergeAll(RevapiConfig... revapiConfigs) {
        return Arrays.stream(revapiConfigs)
                .reduce(empty(), RevapiConfig::mergeWith);
    }

    static final class Builder extends ImmutableRevapiConfig.Builder { }

    public static RevapiConfig empty() {
        return fromJsonNodes(Collections.emptyList());
    }

    private static RevapiConfig fromString(String configJson) {
        try {
            return fromJsonNodes(OBJECT_MAPPER.readValue(configJson, new TypeReference<List<JsonNode>>() {}));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RevapiConfig fromJsonNodes(List<JsonNode> jsonNodes) {
        return new Builder()
                .config(jsonNodes)
                .build();
    }
}
