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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.palantir.gradle.revapi.config.AcceptedBreak;
import com.palantir.gradle.revapi.config.Justification;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;

@Value.Immutable
@JsonDeserialize(as = ImmutableRevapiResult.class)
public abstract class RevapiResult {
    public abstract String code();
    @Nullable
    public abstract String oldElement();
    @Nullable
    public abstract String newElement();
    @Nullable
    public abstract String description();
    @Nullable
    public abstract String oldArchiveName();
    @Nullable
    public abstract String newArchiveName();
    public abstract Map<CompatibilityType, DifferenceSeverity> classification();

    final AcceptedBreak toAcceptedBreak(Justification justification) {
        return AcceptedBreak.builder()
                .code(code())
                .oldElement(Optional.ofNullable(oldElement()))
                .newElement(Optional.ofNullable(newElement()))
                .justification(justification)
                .build();
    }

    static class Builder extends ImmutableRevapiResult.Builder { }

    public static Builder builder() {
        return new Builder();
    }
}
