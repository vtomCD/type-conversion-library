package com.candeal.typeconversion.util;

import java.util.Comparator;

import org.springframework.expression.spel.standard.SpelExpression;

/**
 * Aid for comparing stuff.
 */
public interface Comparators {
    /**
     * @return a {@link Comparator} that matches on {@link SpelExpression} expression definition strings.
     */
    static Comparator<SpelExpression> spelExpressionComparator() {
        return Comparator.comparing(SpelExpression::getExpressionString);
    }
}
