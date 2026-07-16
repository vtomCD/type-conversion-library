package com.candeal.typeconversions.model;

import io.vavr.collection.Map;

public record Options(boolean compile, Unresolvable unresolvable, Map<Enum, Boolean> enums) {}

/**
 *  Unresolvable
 */
record Unresolvable(UnresolvableAction types, UnresolvableAction shortcuts) {}

enum UnresolvableAction {
    IGNORE,
    WARN,
    FAIL,
}

enum Enum {
    BIDIRECTIONAL,
    PARTIAL,
    IGNORE_CASE,
    TRIM,
}
