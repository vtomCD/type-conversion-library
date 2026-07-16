package com.candeal.typeconversion.model;

import io.vavr.API;
import io.vavr.collection.Map;
import lombok.Builder;

@Builder
public record Options(boolean compile, Unresolvable unresolvable, Map<Enum, Boolean> enums) {

    public record Unresolvable(UnresolvableAction types, UnresolvableAction shortcuts) {
        public static Unresolvable defaults() {
            return new Unresolvable(UnresolvableAction.FAIL, UnresolvableAction.FAIL);
        }
    }

    public enum UnresolvableAction {
        IGNORE,
        WARN,
        FAIL,
    }

    public enum Enum {
        BIDIRECTIONAL,
        PARTIAL,
        IGNORE_CASE,
        TRIM;

        public static Map<Enum, Boolean> defaults() {
            return API.Map(BIDIRECTIONAL, true, PARTIAL, true, IGNORE_CASE, true, TRIM, true);
        }
    }

    /// @return default options
    public static Options defaults() {
        return Options.builder()
                      .compile(true)
                      .unresolvable(Unresolvable.defaults())
                      .enums(Enum.defaults())
                      .build();
    }
}
