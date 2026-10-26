package com.satmis.catalog.seed;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Loads existing rows once so seeding does one lookup per table instead of one per row
final class SeedIndexes {

    private SeedIndexes() {
    }

    static <T> Map<String, T> byName(List<T> rows, Function<T, String> nameGetter) {
        return rows.stream().collect(Collectors.toMap(nameGetter, Function.identity()));
    }

    static <T> Map<Long, Set<String>> namesByParentId(List<T> rows, Function<T, Long> parentIdGetter,
                                                      Function<T, String> nameGetter) {
        return rows.stream().collect(Collectors.groupingBy(parentIdGetter,
                Collectors.mapping(nameGetter, Collectors.toCollection(HashSet::new))));
    }
}
