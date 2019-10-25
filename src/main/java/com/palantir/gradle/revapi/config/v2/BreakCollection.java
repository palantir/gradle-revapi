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

package com.palantir.gradle.revapi.config.v2;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.palantir.gradle.revapi.config.GroupAndName;
import com.palantir.gradle.revapi.config.GroupNameVersion;
import com.palantir.gradle.revapi.config.Justification;
import com.palantir.gradle.revapi.config.PerProject;
import com.palantir.gradle.revapi.config.Version;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableBreakCollection.class)
public interface BreakCollection {
    Justification justification();
    Version afterVersion();
    PerProject<AcceptedBreak> breaks();

    default Set<AcceptedBreak> acceptedBreaksFor(GroupAndName groupAndName) {
        return breaks().forGroupAndName(groupAndName);
    }

    default BreakCollection addAcceptedBreaksIf(
            Justification justification,
            GroupNameVersion groupNameVersion,
            Set<AcceptedBreak> breaks) {

        if (!(justification().equals(justification) && afterVersion().equals(groupNameVersion.version()))) {
            return this;
        }

        return builder()
                .from(this)
                .breaks(breaks().withAdded(groupNameVersion.groupAndName(), breaks))
                .build();
    }

    class Builder extends ImmutableBreakCollection.Builder { }

    static Builder builder() {
        return new Builder();
    }
}
