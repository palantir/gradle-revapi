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

package com.palantir.gradle.revapi.config.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.palantir.gradle.revapi.config.ImmutableAcceptedBreakV1;
import com.palantir.gradle.revapi.config.Justification;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableAcceptedBreakV1.class)
public interface AcceptedBreakV1 {
    String code();

    @JsonProperty("old")
    Optional<String> oldElement();

    @JsonProperty("new")
    Optional<String> newElement();

    Justification justification();

    class Builder extends ImmutableAcceptedBreakV1.Builder {
        public Builder justification(String justification) {
            justification(Justification.fromString(justification));
            return this;
        }
    }

    static Builder builder() {
        return new Builder();
    }
}
