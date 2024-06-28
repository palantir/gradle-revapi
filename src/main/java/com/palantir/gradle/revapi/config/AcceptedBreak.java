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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.palantir.gradle.revapi.ImmutableStyle;
import java.util.Comparator;
import java.util.Optional;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@Serial.Structural
@JsonDeserialize(as = ImmutableAcceptedBreak.class)
public interface AcceptedBreak extends Comparable<AcceptedBreak> {
    @JsonProperty("code")
    String code();

    @JsonProperty("old")
    Optional<String> oldElement();

    @JsonProperty("new")
    Optional<String> newElement();

    @JsonProperty("justification")
    Justification justification();

    @Value.Lazy
    default Comparator<AcceptedBreak> comparator() {
        Comparator<Optional<String>> comparingEmptiesFirst =
                Comparator.comparing(element -> element.orElse(null), Comparator.nullsFirst(Comparator.naturalOrder()));

        return Comparator.comparing(AcceptedBreak::code)
                .thenComparing(AcceptedBreak::oldElement, comparingEmptiesFirst)
                .thenComparing(AcceptedBreak::newElement, comparingEmptiesFirst);
    }

    @Override
    default int compareTo(AcceptedBreak other) {
        return comparator().compare(this, other);
    }

    @SuppressWarnings("DesignForExtension")
    class Builder extends ImmutableAcceptedBreak.Builder {
        public Builder justification(String justification) {
            justification(Justification.fromString(justification));
            return this;
        }
    }

    static Builder builder() {
        return new Builder();
    }
}
