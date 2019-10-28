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

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.List;
import org.immutables.value.Value;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

@SuppressWarnings("Duplicates")
@Value.Immutable
abstract class JsonPointer {
    protected abstract List<String> parts();

    @JsonCreator
    public static JsonPointer fromString(String path) {
        Builder builder = builder();

        Arrays.stream(path.split("/"))
                .filter(part -> !part.isEmpty())
                .forEach(builder::addParts);

        return builder.build();
    }

    public NodeTuple narrowDownToKeyIn(Node rootNode) {
        Node currentNode = rootNode;
        NodeTuple narrowedDownToTuple = null;

        for (String part : parts()) {
            if (!(rootNode instanceof MappingNode)) {
                throw new UnsupportedOperationException();
            }

            NodeTuple matched = ((MappingNode) currentNode).getValue().stream()
                    .filter(nodeTuple -> nodeTuple.getKeyNode() instanceof ScalarNode
                            && ((ScalarNode) nodeTuple.getKeyNode()).getValue().equals(part))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Could not find '" + part + "' somewhere in '" + rootNode + "'"));

            narrowedDownToTuple = matched;
            currentNode = matched.getValueNode();
        }

        return narrowedDownToTuple;
    }

    public Node narrowDownToValueIn(Node rootNode) {
        return narrowDownToKeyIn(rootNode).getValueNode();
    }

    public static class Builder extends ImmutableJsonPointer.Builder { }

    public static Builder builder() {
        return new Builder();
    }
}
