package com.candeal.typeconversion.model;

import java.lang.invoke.MethodHandle;

import org.springframework.expression.spel.standard.SpelExpression;

import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.Map;
import io.vavr.collection.LinkedHashMap;
import io.vavr.control.Either;
import lombok.Builder;

/**
 * Fully resolved and merged from all global, family, and leaf 'convert' specs.
 */
@Builder
public record ResolvedConvert(Convert model,
                              Class<?> from,
                              Class<?> to,
                              Map<String, MethodHandle> shortcuts,
                              LinkedHashMap<String, SpelExpression> pre,
                              Either<MethodHandle, SpelExpression> constructor,
                              Map<String, Property> properties,
                              LinkedHashSet<SpelExpression> post) {
}
