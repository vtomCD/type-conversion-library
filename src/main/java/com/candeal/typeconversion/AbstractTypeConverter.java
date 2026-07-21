package com.candeal.typeconversion;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.candeal.typeconversion.model.ResolvedConvert;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Defines common type conversion protocol.
 */
@Slf4j
public abstract class AbstractTypeConverter implements ConditionalGenericConverter {

    @Getter
    private final ResolvedConvert spec;
    protected final Function<StandardEvaluationContext, StandardEvaluationContext> setBeanResolver;

    /**
     * Initializes instances of this type from a given spec object.
     *
     * @param spec a {@link ResolvedConvert} object.
     */
    protected AbstractTypeConverter(final ResolvedConvert spec, final BeanFactory beanFactory) {
        this.spec = spec;
        this.setBeanResolver = ctx -> {
            ctx.setBeanResolver(new BeanFactoryResolver(beanFactory));
            return ctx;
        };
    }

    /**
     * Evaluates "shortcuts" convert spec section, binins resulting methods (including enum constant getters) into
     * given context. Returns {@link Try.Success} with the bound context or {@link Try.Failure} with an exception on
     * error.
     *
     * @param ctx a {@link StandardEvaluationContext} object.
     * @return a {@link Try} instance.
     */
    protected Try<StandardEvaluationContext> evaluateShortcuts(final StandardEvaluationContext ctx) {
        // Simply push shortcut method handles to the context under their respective variable names
        spec.shortcuts()
            .forEach((variable, method) -> ctx.setVariable(variable, method));
        return Try.success(ctx);
    }

    /**
     * Evaluates "pre" convert spec block in the specified evaluation context, binding results into it under declared
     * variable names. Returns {@link Try.Success} with the bound context or {@link Try.Failure} with an exception on
     * error.
     *
     * @param ctx a {@link StandardEvaluationContext} object.
     * @return a {@link Try} instance.
     */
    protected Try<StandardEvaluationContext> evaluatePre(final StandardEvaluationContext ctx) {
        final BiFunction<String, SpelExpression, Tuple2<String, Try<?>>> evalAndSet =
            (variable,
             expression) -> tryEvalExpressionAndSet(variable, expression, ctx).transform(t -> Tuple.of(variable, t));
        final var preValuation =
            spec.pre()
                .map(evalAndSet)
                .values();
        return Try.sequence(preValuation)
                  .mapTo(ctx);
    }

    /**
     * Evaluates given SpEL in specified context, binding the result under variable name provided. Returns
     * {@link Try.Success} with the bound context or {@link Try.Failure} with an exception on error.
     *
     * @param variable   variable name.
     * @param expression a {@link SpelExpression}.
     * @param ctx        a {@link StandardEvaluationContext} object.
     * @return a {@link Try} instance.
     */
    protected Try<?> tryEvalExpressionAndSet(final String variable, final SpelExpression expression,
                                             final StandardEvaluationContext ctx) {
        return tryEvalExpression(expression, ctx).andThen(result -> ctx.setVariable(variable, result));
    }

    /**
     * Attempts to evaluate given SpEL in the context specified. Returns {@link Try.Success} with the result or
     * {@link Try.Failure} with an exception on error.
     *
     * @param expression a {@link SpelExpression}.
     * @param ctx        a {@link StandardEvaluationContext} object.
     * @return a {@link Try} instance.
     */
    protected Try<?> tryEvalExpression(final SpelExpression expression, final StandardEvaluationContext ctx) {
        return Try.of(() -> expression.getValue(ctx))
                  .onFailure(x -> log.atError()
                                     .setCause(x)
                                     .setMessage("Failed to evaluate SpEL: '{}' in converter: {}")
                                     .addArgument(expression.getExpressionString())
                                     .addArgument(spec.model())
                                     .log());
    }
}
