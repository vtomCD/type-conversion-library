package com.candeal.typeconversion.model;

import java.lang.invoke.MethodHandle;

import org.semver4j.Semver;
import org.springframework.expression.spel.standard.SpelExpression;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.TreeMap;
import io.vavr.collection.TreeSet;
import io.vavr.control.Either;
import io.vavr.control.Option;
import lombok.Builder;

/**
 * Models `convert` spec.
 *
 * @param from        an optional "from" type.
 * @param to          an optional "to" type.
 * @param optional    explicit identity.
 * @param optional    sematic version.
 * @param a           {@link TreeSet} ordered set of {@link Convert}s this converter extends.
 * @param optinal     {@link Options}.
 * @param shortcuts   a shortcuts {@link Map}.
 * @param pre         an orderted {@link TreeMap} of {@link SpelExpression}s to eval before "to" construction.
 * @param constructor a {@link MethodHandle} for a "to" type's constructor.
 * @param properties  a {@link Map} of properties converters.
 * @param post        a {@link List} of side-effect expressions.
 */
@Builder
public record Convert(Option<Class<?>> from,
                      Option<Class<?>> to,
                      Option<String> id,
                      Option<Semver> version,
                      TreeSet<Convert> extendsDefs,
                      Option<Options> options,
                      Map<String, String> shortcuts,
                      TreeMap<String, String> pre,
                      Option<String> constructor,
                      Map<String, Property> properties,
                      List<String> post) {
    public record Property(String name, Either<String, Tuple2<String, String>> expression) {
    }
}
