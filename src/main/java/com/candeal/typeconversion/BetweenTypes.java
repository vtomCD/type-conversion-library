package com.candeal.typeconversion;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts between two types as defined by a configured convert spec.
 */
@Slf4j
public class BetweenTypes implements ConditionalGenericConverter {

    private final ResolvedConvert spec;
    private final TypeDescriptor fromType;
    private final TypeDescriptor toType;
    private final Function<StandardEvaluationContext, StandardEvaluationContext> setBeanResolver;

    public BetweenTypes(final ResolvedConvert spec, final BeanFactory beanFactory) {

        this.spec = spec;
        this.fromType = TypeDescriptor.valueOf(spec.from());
        this.toType = TypeDescriptor.valueOf(spec.to());
        this.setBeanResolver = ctx -> {
            ctx.setBeanResolver(new BeanFactoryResolver(beanFactory));
            return ctx;
        };
    }

    @Override
    public @Nullable Object convert(@Nullable Object source, TypeDescriptor srcType, TypeDescriptor destType) {
        return Option.of(source)
                     .map(StandardEvaluationContext::new)
                     .map(aliasRootTo("from"))
                     .map(setBeanResolver)
                     .toTry()
                     .flatMap(this::evaluateShortcuts)
                     .flatMap(this::evaluatePre)
                     .flatMap(this::instantiateAndBindTo)
                     .map(aliasRootTo("to"))
                     .flatMap(this::copyProperties)
                     .flatMap(this::evaluatePost)
                     .map(StandardEvaluationContext::getRootObject)
                     .map(TypedValue::getValue)
                     .recover(NoSuchElementException.class, source) // When source was null
                     .get();
    }

    @Override
    public @Nullable Set<ConvertiblePair> getConvertibleTypes() {
        return Set.of(new ConvertiblePair(fromType.getType(), toType.getType()));
    }

    @Override
    public boolean matches(TypeDescriptor srcType, TypeDescriptor destType) {
        return srcType.isAssignableTo(fromType) && toType.isAssignableTo(destType);
    }

    private Function<StandardEvaluationContext, StandardEvaluationContext> aliasRootTo(final String alias) {
        return ctx -> {
            final var root = ctx.getRootObject().getValue();
            ctx.setVariable(alias, root);
            return ctx;
        };
    }

    private Try<StandardEvaluationContext> evaluateShortcuts(final StandardEvaluationContext ctx) {
        return Try.failure(null);
    }

    private Try<StandardEvaluationContext> evaluatePre(final StandardEvaluationContext ctx) {
        return Try.failure(null);
    }

    private Try<StandardEvaluationContext> instantiateAndBindTo(final StandardEvaluationContext ctx) {
        return Try.of(spec.constructor()::invoke)
                  .onFailure(x -> log.atError()
                                     .setCause(x)
                                     .setMessage("Failed to instantiate 'to' type ({}) with converter: {}")
                                     .addArgument(toType)
                                     .addArgument(spec.model())
                                     .log())
                  .andThen(ctx::setRootObject)
                  .mapTo(ctx);
    }

    private Try<StandardEvaluationContext> copyProperties(final StandardEvaluationContext ctx) {
        return Try.failure(null);
    }

    private Try<StandardEvaluationContext> evaluatePost(final StandardEvaluationContext ctx) {
        return Try.failure(null);
    }
}
