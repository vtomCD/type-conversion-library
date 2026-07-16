package com.candeal.typeconversion;

import java.lang.invoke.MethodHandle;

import com.candeal.typeconversion.model.Convert;
import com.candeal.typeconversion.model.Options;

import org.springframework.expression.spel.standard.SpelExpression;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.SortedMap;
import io.vavr.control.Either;
import lombok.Builder;

/**
 * Fully resolved and merged from all global, family, and leaf 'convert' specs.
 */
@Builder
public record ResolvedConvert(Convert model,
                              Class<?> from,
                              Class<?> to,
                              Options options,
                              Map<String, MethodHandle> shortcuts,
                              SortedMap<String, SpelExpression> pre,
                              MethodHandle constructor,
                              Map<String, Property> properties,
                              List<SpelExpression> post) {
    public record Property(Either<SpelExpression, Tuple2<SpelExpression, SpelExpression>> expression) {
    }
}
