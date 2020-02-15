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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.immutables.value.Value;

final class TestObjects {
    private TestObjects() { }

    @Value.Style(overshadowImplementation = true)
    @interface ImmutableStyle {}

    @Value.Immutable
    @ImmutableStyle
    @JsonDeserialize(as = ImmutableFoo.class)
    interface Foo {
        Optional<Bar> foo();

        static UnaryOperator<Foo> withValues(
                UnaryOperator<String> bazMapper,
                UnaryOperator<Optional<String>> quuxMapper) {
            return foo -> ImmutableFoo.builder()
                    .foo(foo.foo().map(bar -> ImmutableBar.builder()
                            .bar(bazMapper.apply(bar.bar()))
                            .quux(quuxMapper.apply(bar.quux()))
                            .build()))
                    .build();
        }
    }

    @Value.Immutable
    @ImmutableStyle
    @JsonDeserialize(as = ImmutableBar.class)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    interface Bar {
        String bar();
        Optional<String> quux();
    }
}
