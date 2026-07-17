package com.candeal.typeconversion.evaluation;

import org.jspecify.annotations.Nullable;

import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * Protocol for evaluating subjects of type "T" with a result of type "R"
 */
@FunctionalInterface
public interface Evaluator<T, R> {
    /**
     * Evaluates given subject, returning the result or throwing a checked exception if unable.
     *
     * @param subject evaluation subject.
     * @return result of the evaluation.
     */
    @Nullable
    R evaluate(@Nullable T subject, Object... args) throws Exception;

    /**
     * Tries to evaluate given subject, returns success or falure based on the outcome.
     *
     * @param subject sobject to evaluate.
     * @return {@link Try.Success} on success or {@link Try.Failure} otherwise.
     */
    default Try<@Nullable ? extends R> tryEvaluate(@Nullable T subject, Object... args) {
        return Try.of(() -> evaluate(subject, args));
    }
}
