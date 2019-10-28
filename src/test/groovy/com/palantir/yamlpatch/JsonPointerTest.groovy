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

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.CompileStatic
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode

@CompileStatic
class JsonPointerTest {
    @Test
    void can_be_parsed_from_a_json_string() {
        ObjectMapper objectMapper = new ObjectMapper()

        JsonPointer jsonPointer = objectMapper.readValue("\"/foo/bar/1/baz\"", JsonPointer)
        Assertions.assertThat(jsonPointer.parts()).containsExactly("foo", "bar", "1", "baz")
    }

    @Test
    void narrows_down_nested_maps_correctly() {
        Node rootNode = new Yaml().compose(new StringReader("""
            foo:
                bar:
                    baz: quux
        """.stripIndent()))

        Node narrowedDown = JsonPointer.fromString("/foo/bar/baz").narrowDown(rootNode)

        Assertions.assertThat(narrowedDown).isInstanceOf(ScalarNode)
        Assertions.assertThat(((ScalarNode) narrowedDown).value).isEqualTo("quux")
    }

}