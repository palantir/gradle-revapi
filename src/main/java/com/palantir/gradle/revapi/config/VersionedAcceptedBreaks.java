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

import com.google.common.collect.SetMultimap;
import org.immutables.value.Value;

@Value.Immutable
public interface VersionedAcceptedBreaks {
    SetMultimap<GroupAndName, AcceptedBreak> asMultimap();

    default VersionedAcceptedBreaks merge(GroupAndName groupAndName, Iterable<AcceptedBreak> acceptedBreaks) {
        return builder()
                .from(this)
                .putAllAsMultimap(groupAndName, acceptedBreaks)
                .build();
    }

    class Builder extends ImmutableVersionedAcceptedBreaks.Builder { }

    static Builder builder() {
        return new Builder();
    }

    static VersionedAcceptedBreaks empty() {
        return builder().build();
    }
}
