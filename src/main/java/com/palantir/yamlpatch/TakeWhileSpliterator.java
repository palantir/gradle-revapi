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

import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class TakeWhileSpliterator<T> implements Spliterator<T> {
    static <T> TakeWhileSpliterator<T> over(Spliterator<T> source, Predicate<T> condition) {
        return new TakeWhileSpliterator<>(source, condition, false);
    }

    static <T> TakeWhileSpliterator<T> overInclusive(Spliterator<T> source, Predicate<T> condition) {
        return new TakeWhileSpliterator<>(source, condition, true);
    }

    private final Spliterator<T> source;
    private final Predicate<T> condition;
    private final boolean inclusive;
    private boolean conditionHeldSoFar = true;

    private TakeWhileSpliterator(Spliterator<T> source, Predicate<T> condition, boolean inclusive) {
        this.source = source;
        this.condition = condition;
        this.inclusive = inclusive;
    }


    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        return conditionHeldSoFar && source.tryAdvance(e -> {
            if (condition.test(e)) {
                action.accept(e);
            } else {
                if (inclusive && conditionHeldSoFar) {
                    action.accept(e);
                }
                conditionHeldSoFar = false;
            }
        });
    }

    @Override
    public Spliterator<T> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return conditionHeldSoFar ? source.estimateSize() : 0;
    }

    @Override
    public int characteristics() {
        return source.characteristics() & ~Spliterator.SIZED;
    }

    @Override
    public Comparator<? super T> getComparator() {
        return source.getComparator();
    }
}
