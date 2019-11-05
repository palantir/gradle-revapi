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

import java.util.Comparator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Copy pasted from java 9 source code with modifications to make it work with checkstyle. */
final class Java9Streams {
    private Java9Streams() { }

    //CHECKSTYLE:OFF
    static <T> Stream<T> takeWhile(Stream<T> stream, Predicate<? super T> predicate) {
        class Taking extends Spliterators.AbstractSpliterator<T> implements Consumer<T> {
            private static final int CANCEL_CHECK_COUNT = 63;
            private final Spliterator<T> spliterator;
            private int count;
            private T value;
            private final AtomicBoolean cancel = new AtomicBoolean();
            private boolean takeOrDrop = true;

            Taking(Spliterator<T> spliterator) {
                super(spliterator.estimateSize(), spliterator.characteristics() & ~(Spliterator.SIZED | Spliterator.SUBSIZED));
                this.spliterator = spliterator;
            }

            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                boolean test = true;
                if (takeOrDrop &&               // If can take
                        (count != 0 || !cancel.get()) && // and if not cancelled
                        spliterator.tryAdvance(this) &&   // and if advanced one element
                        (test = predicate.test(value))) {   // and test on element passes
                    action.accept(value);           // then accept element
                    return true;
                } else {
                    // Taking is finished
                    takeOrDrop = false;
                    // Cancel all further traversal and splitting operations
                    // only if test of element failed (short-circuited)
                    if (!test) {
                        cancel.set(true);
                    }
                    return false;
                }
            }

            @Override
            public Comparator<? super T> getComparator() {
                return spliterator.getComparator();
            }

            @Override
            public void accept(T newValue) {
                count = (count + 1) & CANCEL_CHECK_COUNT;
                this.value = newValue;
            }

            @Override
            public Spliterator<T> trySplit() {
                return null;
            }
        }
        return StreamSupport.stream(new Taking(stream.spliterator()), stream.isParallel()).onClose(stream::close);
    }
    //CHECKSTYLE:ON

}

