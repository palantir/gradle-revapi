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

package com.palantir.yamlpatch

import static org.assertj.core.api.Assertions.assertThat

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.ScalarNode

@CompileStatic
class JsonPointerTest {
    @Test
    void 'can be parsed from a json string'() {
        ObjectMapper objectMapper = new ObjectMapper()

        JsonPointer jsonPointer = objectMapper.readValue("\"/foo/bar/1/baz\"", JsonPointer)
        assertThat(jsonPointer.parts()).containsExactly("foo", "bar", "1", "baz")
    }

    @Test
    void 'narrows down nested maps to find value correctly'() {
        // language=yaml
        Node rootNode = new Yaml().compose(new StringReader("""
            foo:
                bar:
                    baz: quux
        """.stripIndent()))

        Node narrowedDown = JsonPointer.fromString("/foo/bar/baz").narrowDownToValueIn(rootNode)

        assertThat(narrowedDown).isInstanceOf(ScalarNode)
        assertThat(((ScalarNode) narrowedDown).value).isEqualTo("quux")
    }

    @Test
    void 'narrows down nested maps to find key correctly'() {
        // language=yaml
        Node rootNode = new Yaml().compose(new StringReader("""
            foo:
                bar:
                    baz: quux
        """.stripIndent()))

        NodeTuple narrowedDown = JsonPointer.fromString("/foo/bar/baz").narrowDownToKeyIn(rootNode)

        assertThat(narrowedDown.getKeyNode()).isInstanceOf(ScalarNode)
        assertThat(((ScalarNode) narrowedDown.getKeyNode()).value).isEqualTo("baz")

        assertThat(narrowedDown.getValueNode()).isInstanceOf(ScalarNode)
        assertThat(((ScalarNode) narrowedDown.getValueNode()).value).isEqualTo("quux")

    }

    @Test
    void 'returns parent node when it exists'() {
        Optional<JsonPointer> parent = JsonPointer.fromString("/foo/bar/baz").parent()
        assertThat(parent).hasValue(JsonPointer.fromString("/foo/bar"));
    }

    @Test
    void 'returns empty when parent node does not exist'() {
        Optional<JsonPointer> parent = JsonPointer.fromString("/").parent()
        assertThat(parent).isEmpty();
    }

    @Test
    void 'returns most specific part when not the root node'() {
        Optional<String> mostSpecific = JsonPointer.fromString("/a/b/c/d").mostSpecificPart();
        assertThat(mostSpecific).hasValue("d");
    }

    @Test
    void 'returns empty for most specific part when the root node'() {
        Optional<String> mostSpecific = JsonPointer.fromString("/").mostSpecificPart();
        assertThat(mostSpecific).isEmpty();
    }

}