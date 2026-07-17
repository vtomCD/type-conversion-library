package com.candeal.typeconversion.model;

import io.vavr.API;
import io.vavr.collection.Map;
import lombok.Builder;

/**
 * Conversion options for bootstraping and evaluation control.
 *
 * @param compile whether to compile SpEL (non-deterministic, will fallback to interpreted if failed to bytecode
 *                expressions).
 * @param unresolvable what to do wiht unresolvables.
 * @param enums enum conversion options.
 */
@Builder
public record Options(boolean compile, Unresolvable unresolvable, Map<Enum, Boolean> enums) {

    /**
     * Holds options on how to deal with unresolvables.
     *
     * @param types an {@link UnresolvableAction} to take for types.
     * @param shortcuts an {@link UnresolvableAction} to take for shortcuts.
     */
    public record Unresolvable(UnresolvableAction types, UnresolvableAction shortcuts) {
        /// @return the default: fail on both counts.
        public static Unresolvable defaults() {
            return new Unresolvable(UnresolvableAction.FAIL, UnresolvableAction.FAIL);
        }
    }

    /**
     * Action to take when unable to resolve things.
     */
    public enum UnresolvableAction {
        IGNORE,
        WARN,
        FAIL,
    }

    /**
     * Enumeration conversion options.
     */
    public enum Enum {
        /// Generate reverse enum conversion.
        BIDIRECTIONAL,
        /// Mapping is incomplete.
        PARTIAL,
        /// Ignore case when matching.
        IGNORE_CASE,
        /// Use SpEL expressions instead of/in addition to constants.
        USE_SPEL,
        /// Trim springs before matching.
        TRIM;

        /**
         * @return default enum options: directional, partial, ignore case, trim, no SpEL.
         */
        public static Map<Enum, Boolean> defaults() {
            return API.Map(BIDIRECTIONAL, true, PARTIAL, true, IGNORE_CASE, true, TRIM, true, USE_SPEL, false);
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
