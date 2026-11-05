/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini;

import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Represents an immutable pair consisting of two elements.
 *
 * @param <T1> the type of the first value
 * @param <T2> the type of the second value
 * @param v1   the first value
 * @param v2   the second value
 */
public record Tuple2<T1, T2>(T1 v1, T2 v2) {

    /**
     * Returns a function to map the first value of a Tuple2.
     *
     * @param mapper the mapper function
     * @param <T1>   the original type of the first value
     * @param <T2>   the type of the second value
     * @param <R>    the new type of the first value
     * @return the mapping function
     */
    public static <T1, T2, R> Function<Tuple2<T1, T2>, Tuple2<R, T2>> map1(Function<T1, R> mapper) {
        return t -> t.v1(mapper.apply(t.v1));
    }

    /**
     * Returns a function to map the second value of a Tuple2.
     *
     * @param mapper the mapper function
     * @param <T1>   the type of the first value
     * @param <T2>   the original type of the second value
     * @param <R>    the new type of the second value
     * @return the mapping function
     */
    public static <T1, T2, R> Function<Tuple2<T1, T2>, Tuple2<T1, R>> map2(Function<T2, R> mapper) {
        return t -> t.v2(mapper.apply(t.v2));
    }

    /**
     * Returns a new Tuple2 consisting of the specified values.
     *
     * @param v1   the first value
     * @param v2   the second value
     * @param <T1> the type of the first value
     * @param <T2> the type of the second value
     * @return the created Tuple2 instance
     */
    public static <T1, T2> Tuple2<T1, T2> of(T1 v1, T2 v2) {
        return new Tuple2<>(v1, v2);
    }

    /**
     * Returns a function that swaps the elements of a Tuple2.
     *
     * @param <T1> the original type of the first value
     * @param <T2> the original type of the second value
     * @return the swapping function
     */
    public static <T1, T2> Function<Tuple2<T1, T2>, Tuple2<T2, T1>> swap() {
        return t -> Tuple2.of(t.v2, t.v1);
    }

    /**
     * Returns a collector that accumulates Tuple2 elements into a Map.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @return the collector instance
     */
    public static <K, V> Collector<Tuple2<K, V>, ?, Map<K, V>> toMap() {
        return Collectors.toMap(Tuple2::v1, Tuple2::v2);
    }

    /**
     * Creates a Tuple2 instance and validates that both values are non-null.
     *
     * @param v1 the first value
     * @param v2 the second value
     * @throws NullPointerException if either v1 or v2 is null
     */
    public Tuple2 {
        requireNonNull(v1);
        requireNonNull(v2);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "(", ")")
                .add(String.valueOf(v1))
                .add(String.valueOf(v2))
                .toString();
    }

    /**
     * Returns a new tuple with a modified first value.
     *
     * @param v1  the new first value
     * @param <R> the new type of the first value
     * @return a new Tuple2 containing the updated value
     */
    public <R> Tuple2<R, T2> v1(R v1) {
        return new Tuple2<>(v1, v2);
    }

    /**
     * Returns a new tuple with a modified second value.
     *
     * @param v2  the new second value
     * @param <R> the new type of the second value
     * @return a new Tuple2 containing the updated value
     */
    public <R> Tuple2<T1, R> v2(R v2) {
        return new Tuple2<>(this.v1, v2);
    }
}
