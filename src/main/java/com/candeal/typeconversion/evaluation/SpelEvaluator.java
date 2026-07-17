package com.candeal.typeconversion.evaluation;

import org.jspecify.annotations.Nullable;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * SpEL context backed abstract evaluator.
 */
public abstract class SpelEvaluator<T, R> implements Evaluator<T, R> {
    private final StandardEvaluationContext ctx;

    /**
     * Initializes instances of this type with given SpEL context.
     *
     * @param ctx a {@link StandardEvaluationContext} object.
     */
    public SpelEvaluator(StandardEvaluationContext ctx) {
        this.ctx = ctx;
    }


    /**
     * Evaluates given subject in SpEL context.
     */
    @Override
    public @Nullable R evaluate(@Nullable T subject, Object... args) throws Exception {
        return evaluateInContext(subject, ctx);
    }

    /**
     * @param subject nullable subject of type {@code T}
     * @param ctx a {@link StandardEvaluationContext} SpEL context.
     * @return result of evaluation of type {@code R}
     */
    protected abstract R evaluateInContext(@Nullable T subject, final StandardEvaluationContext ctx, Object... args);
}
