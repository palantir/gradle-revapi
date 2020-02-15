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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.immutables.value.Value;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
@JsonSubTypes({
        @JsonSubTypes.Type(JsonPatch.Replace.class),
        @JsonSubTypes.Type(JsonPatch.Add.class),
        @JsonSubTypes.Type(JsonPatch.Remove.class)
})
public interface JsonPatch {
    JsonPointer path();

    Patch patchFor(ObjectMapper yamlObjectMapper, String input, Node jsonDocument);

    @Value.Immutable
    @JsonTypeName("replace")
    @JsonDeserialize(as = ImmutableReplace.class)
    interface Replace extends JsonPatch {
        JsonNode value();

        @Override
        default Patch patchFor(
                ObjectMapper yamlObjectMapper,
                String _input,
                Node jsonDocument) {
            Node nodeToReplace = path().narrowDownToValueIn(jsonDocument);

            String replacementAsYaml = replacementAsYaml(yamlObjectMapper);
            String newlinePrefix = value().isObject() ? "\n  " : "";

            return Patch.builder()
                    .range(Range.builder()
                            .startIndex(nodeToReplace.getStartMark().getIndex())
                            .endIndex(nodeToReplace.getEndMark().getIndex())
                            .build())
                    .replacement(newlinePrefix + replacementAsYaml)
                    .build();
        }

        default String replacementAsYaml(ObjectMapper yamlObjectMapper) {
            try {
                return yamlObjectMapper.writeValueAsString(value()).trim();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Value.Immutable
    @JsonTypeName("add")
    @JsonDeserialize(as = ImmutableAdd.class)
    interface Add extends JsonPatch {
        String value();

        @Override
        default Patch patchFor(
                ObjectMapper yamlObjectMapper,
                String input,
                Node jsonDocument) {
            JsonPointer parent =
                    path().parent().orElseThrow(() -> new IllegalArgumentException("Cannot add item at root node"));
            Node nodeToInsertInto = parent.narrowDownToValueIn(jsonDocument);

            int whitespaceDepth = nodeToInsertInto.getStartMark().getColumn();
            String whitespace = String.format("%1$" + whitespaceDepth + "s", "");
            String replacement = whitespace + path().mostSpecificPart().get() + ": " + value() + "\n";

            int endIndex = nodeToInsertInto.getEndMark().getIndex();
            return Patch.of(endIndex, endIndex, replacement);
        }
    }

    @Value.Immutable
    @JsonTypeName("remove")
    @JsonDeserialize(as = ImmutableRemove.class)
    interface Remove extends JsonPatch {
        Pattern JUST_WHITESPACE_AND_COMMENT_AFTER_LINE = Pattern.compile("\\s*(#.*)?\n?");
        Pattern ONLY_WHITESPACE = Pattern.compile("\\s*");

        @Override
        default Patch patchFor(
                ObjectMapper yamlObjectMapper,
                String input,
                Node jsonDocument) {
            NodeTuple nodeTupleToReplace = path().narrowDownToKeyIn(jsonDocument);

            int startIndex = nodeTupleToReplace.getKeyNode().getStartMark().getIndex();
            int endIndex = nodeTupleToReplace.getValueNode().getEndMark().getIndex();

            try {
                String restOfLine = lineAfter(input, endIndex);
                if (JUST_WHITESPACE_AND_COMMENT_AFTER_LINE.matcher(restOfLine).matches()) {
                    endIndex += restOfLine.length();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            int whitespaceDepth = nodeTupleToReplace.getKeyNode().getStartMark().getColumn();
            int startOfLine = startIndex - whitespaceDepth;
            String prevPartOfLine = input.substring(startOfLine, startIndex);
            if (ONLY_WHITESPACE.matcher(prevPartOfLine).matches()) {
                String prefix = prevPartOfLine + "#";
                int howFar = StreamUtils.takeWhile(linesBefore(input, startOfLine - 1), line -> line.startsWith(prefix))
                        .mapToInt(String::length)
                        .sum();
                startIndex -= whitespaceDepth + howFar;
            }

            return Patch.builder()
                    .range(Range.builder()
                            .startIndex(startIndex)
                            .endIndex(endIndex)
                            .build())
                    .replacement("")
                    .build();
        }

        default String lineAfter(String input, int endIndex) throws IOException {
            StringReader stringReader = new StringReader(input);
            stringReader.skip(endIndex);
            BufferedReader bufferedReader = new BufferedReader(stringReader);
            return bufferedReader.readLine() + "\n";
        }

        static Stream<String> linesBefore(String str, int startingIndex) {
            AtomicInteger index = new AtomicInteger(startingIndex);
            AtomicInteger prevIndex = new AtomicInteger(startingIndex);

            Stream<Optional<String>> previousLines = Stream.generate(() -> {
                while (index.decrementAndGet() >= 0) {
                    int currentIndex = index.get();
                    char character = str.charAt(currentIndex);
                    if (character == '\n') {
                        return Optional.of(str.substring(currentIndex + 1, prevIndex.getAndSet(currentIndex) + 1));
                    }
                }

                return Optional.empty();
            });

            return StreamUtils.takeWhile(previousLines, Optional::isPresent)
                    .map(Optional::get);
        }
    }
}
