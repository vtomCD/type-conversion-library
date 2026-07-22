package com.candeal.typeconversion.model;

import java.lang.invoke.MethodHandle;

import org.semver4j.Semver;
import org.springframework.expression.spel.standard.SpelExpression;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import lombok.Builder;

/**
 * Models `convert` spec.
 *
 * @param from        an optional "from" type.
 * @param to          an optional "to" type.
 * @param optional    explicit identity.
 * @param optional    sematic version.
 * @param a           {@link LinkedHashSet} ordered set of {@link Convert}s this converter extends.
 * @param optinal     {@link Options}.
 * @param shortcuts   a shortcuts {@link Map}.
 * @param pre         an ordered {@link LinkedHashMap} of {@link SpelExpression}s to eval before "to" construction.
 * @param constructor a {@link MethodHandle} for a "to" type's constructor.
 * @param properties  a {@link Map} of properties converters.
 * @param post        a {@link LinkedHashSet} of side-effect expressions.
 */
@Builder
public record Convert(Option<Class<?>> from,
                      Option<Class<?>> to,
                      Option<String> id,
                      Option<Semver> version,
                      LinkedHashSet<Convert> extendsDefs,
                      Option<Options> options,
                      Map<String, String> shortcuts,
                      LinkedHashMap<String, String> pre,
                      Option<String> constructor,
                      Map<String, Property> properties,
                      LinkedHashSet<String> post) {
    /// Property ADT (Algebraic Data Type)
    public sealed interface Property permits Property.Spel, Property.Conditional, Property.Enum {
        /// When property is a SpEL
        record Spel(String expression) implements Property {
        }

        /// When property is a conditional w/fall-throughs
        record Conditional(LinkedHashMap<String, String> conditions,
                           LinkedHashSet<String> fallThroughs) implements Property {
        }

        /// When property is an enumeration of constants
        record Enum(LinkedHashSet<String> constants) implements Property {
        }
    }
}
