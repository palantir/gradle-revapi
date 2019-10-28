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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
@JsonSubTypes({
        @JsonSubTypes.Type(JsonPatch.Replace.class),
        @JsonSubTypes.Type(JsonPatch.Remove.class)
})
public interface JsonPatch {
    JsonPointer path();

    Patch patchFor(Node jsonDocument);

    @Value.Immutable
    @JsonTypeName("replace")
    @JsonDeserialize(as = ImmutableReplace.class)
    interface Replace extends JsonPatch {
        String value();

        @Override
        default Patch patchFor(Node jsonDocument) {
            Node nodeToReplace = path().narrowDownToValueIn(jsonDocument);
            return Patch.builder()
                    .range(Range.builder()
                            .startIndex(nodeToReplace.getStartMark().getIndex())
                            .endIndex(nodeToReplace.getEndMark().getIndex())
                            .build())
                    .replacement(value())
                    .build();
        }
    }

    @Value.Immutable
    @JsonTypeName("remove")
    @JsonDeserialize(as = ImmutableRemove.class)
    interface Remove extends JsonPatch {
        @Override
        default Patch patchFor(Node jsonDocument) {
            NodeTuple nodeTupleToReplace = path().narrowDownToKeyIn(jsonDocument);
            return Patch.builder()
                    .range(Range.builder()
                            .startIndex(nodeTupleToReplace.getKeyNode().getStartMark().getIndex())
                            .endIndex(nodeTupleToReplace.getValueNode().getEndMark().getIndex())
                            .build())
                    .replacement("")
                    .build();
        }
    }
}
