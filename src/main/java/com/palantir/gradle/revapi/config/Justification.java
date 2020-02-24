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

package com.palantir.gradle.revapi.config;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

@Value.Immutable
@Serial.Structural
@JsonDeserialize(as = ImmutableJustification.class)
public interface Justification {
    String YOU_MUST_ENTER_JUSTIFICATION = "{why this break is ok}";

    @JsonValue
    String asString();

    @Value.Check
    default void check() {
        Preconditions.checkArgument(
                !asString().equals(YOU_MUST_ENTER_JUSTIFICATION),
                "You must enter a justification other than " + YOU_MUST_ENTER_JUSTIFICATION);
    }

    class Builder extends ImmutableJustification.Builder {}

    static Builder builder() {
        return new Builder();
    }

    static Justification fromString(String justification) {
        return builder().asString(justification).build();
    }
}
