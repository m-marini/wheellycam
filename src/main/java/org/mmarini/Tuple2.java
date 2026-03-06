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

public record Tuple2<T1, T2>(T1 _1, T2 _2) {

    /**
     * Returns the function to map the second value
     *
     * @param mapper the mapper function
     * @param <T1>   the value 1 type
     * @param <T2>   the value 2 type
     * @param <R>    the return value 1 type
     */
    public static <T1, T2, R> Function<Tuple2<T1, T2>, Tuple2<R, T2>> map1(Function<T1, R> mapper) {
        return t -> t.setV1(mapper.apply(t._1));
    }

    /**
     * Returns the function to map the second value
     *
     * @param mapper the mapper function
     * @param <T1>   the value 1 type
     * @param <T2>   the value 2 type
     * @param <R>    the return value 2 type
     */
    public static <T1, T2, R> Function<Tuple2<T1, T2>, Tuple2<T1, R>> map2(Function<T2, R> mapper) {
        return t -> t.setV2(mapper.apply(t._2));
    }

    /**
     * Returns the pair of value
     *
     * @param v1   first value
     * @param v2   second value
     * @param <T1> first value type
     * @param <T2> second value type
     */
    public static <T1, T2> Tuple2<T1, T2> of(T1 v1, T2 v2) {
        return new Tuple2<>(v1, v2);
    }

    /**
     * Returns the function swapping the value of tuple2
     *
     * @param <T1> the type of first parameter
     * @param <T2> the type of second parameter
     */
    public static <T1, T2> Function<Tuple2<T1, T2>, Tuple2<T2, T1>> swap() {
        return t -> Tuple2.of(t._2, t._1);
    }

    /**
     * Returns the collector to Map
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> Collector<Tuple2<K, V>, ?, Map<K, V>> toMap() {
        return Collectors.toMap(Tuple2<K, V>::getV1, Tuple2<K, V>::getV2);
    }

    /**
     * Creates a Tuple2
     *
     * @param _1 first value
     * @param _2 second value
     */
    public Tuple2(T1 _1, T2 _2) {
        this._1 = requireNonNull(_1);
        this._2 = requireNonNull(_2);
    }

    /**
     * Returns first value
     */
    public T1 getV1() {
        return _1;
    }

    /**
     * Returns a tuple with changed first value
     *
     * @param v1  the first value
     * @param <R> the type of first value
     */
    public <R> Tuple2<R, T2> setV1(R v1) {
        return new Tuple2<>(v1, _2);
    }

    /**
     * Returns second value
     */
    public T2 getV2() {
        return _2;
    }

    /**
     * Returns a tuple with changed second value
     *
     * @param v2  the second value
     * @param <R> the type of second value
     */
    public <R> Tuple2<T1, R> setV2(R v2) {
        return new Tuple2<>(_1, v2);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "(", ")")
                .add(String.valueOf(_1))
                .add(String.valueOf(_2))
                .toString();
    }
}
