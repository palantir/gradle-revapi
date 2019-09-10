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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.palantir.gradle.revapi.config.AcceptedBreak;
import com.palantir.gradle.revapi.config.RevapiConfig;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

public class JacksonSucksTest {
    @Test
    public void test() throws IOException {
        ObjectMapper objectMapper = RevapiConfig.newRecommendedObjectMapper();

        SetMultimap<String, AcceptedBreak> multimap = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);
        multimap.put("foo", AcceptedBreak.builder()
                .code("lol")
                .newElement("new")
                .oldElement("old")
                .justification("j")
                .build());

        String yaml = objectMapper.writeValueAsString(multimap);

        System.out.println(yaml);

        SetMultimap<String, AcceptedBreak> deser = objectMapper.readValue(yaml, new TypeReference<SetMultimap<String, AcceptedBreak>>() {});

        assertThat(deser).isEqualTo(multimap);
    }
}
