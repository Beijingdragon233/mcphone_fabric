package com.november.mcphone.util;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/** Collection operations whose JDK counterparts require a newer runtime. */
public final class Java8Collections {
    private Java8Collections() {}

    /** An immutable view of the collected elements, including any null entries. */
    public static <T> Collector<T, ?, List<T>> toList() {
        return Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList);
    }

    public static <T, K, V> Collector<T, ?, Map<K, V>> toMap(
            Function<? super T, ? extends K> key, Function<? super T, ? extends V> value) {
        return Collectors.collectingAndThen(
                Collectors.toMap(item -> Objects.requireNonNull(key.apply(item)), value),
                Collections::unmodifiableMap);
    }

    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(Objects.requireNonNull(key), Objects.requireNonNull(value));
    }
}
