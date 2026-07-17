package com.candeal.typeconversion.model;

import java.lang.invoke.MethodHandle;

import org.springframework.expression.spel.standard.SpelExpression;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.collection.SortedMap;

/**
 * Property ADT (algebraic data type)
 */
public sealed interface Property permits Property.Type, Property.Enum {

    static Property of(MethodHandle getter, MethodHandle setter) {
        return new Type.Method(getter, setter);
    }

    static Property of(SpelExpression getter, MethodHandle setter) {
        return new Type.Expression(getter, setter);
    }

    static Property of(SortedMap<SpelExpression, SpelExpression> conditions,
                       List<SpelExpression> fallThroughs,
                       MethodHandle setter) {
        return new Type.Conditional(conditions, fallThroughs, setter);
    }

    static <T> Property of(T constant) {
        return new Enum.SingleConstant<>(constant);
    }

    static Property of(SpelExpression expression) {
        return new Enum.Spel(expression);
    }

    static <T> Property of(Set<T> constants) {
        return new Enum.SetOfConstants<>(constants);
    }

    /**
     * Bean type property ADT.
     *
     * Auto-discovered properties on a source side will be represented by respective value getter method handle whereas
     * explicitly defined - by a property expression, which is either a simple SpEL or conditional.
     */
    sealed interface Type extends Property permits Type.Method, Type.Expression, Type.Conditional {

        MethodHandle setter();

        record Method(MethodHandle getter, MethodHandle setter) implements Type {
        }

        record Expression(SpelExpression getter, MethodHandle setter) implements Type {
        }

        record Conditional(SortedMap<SpelExpression, SpelExpression> conditions,
                           List<SpelExpression> fallThroughs,
                           MethodHandle setter) implements Type {
        }
    }

    /**
     * Enum property ADT, mapped value either:
     *
     * <ol>
     * <li>A single constant, or
     * <li>SpEL, or
     * <li>A set of constants
     * </ol>
     */
    sealed interface Enum extends Property permits Enum.SingleConstant, Enum.Spel, Enum.SetOfConstants {
        record SingleConstant<T>(T constant) implements Enum {
        }

        record Spel(SpelExpression spel) implements Enum {
        }

        record SetOfConstants<T>(Set<T> constants) implements Enum {
        }
    }
}
