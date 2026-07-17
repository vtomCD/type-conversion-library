package com.candeal.typeconversion;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Patterns.$Left;
import static io.vavr.Patterns.$Right;

import java.lang.invoke.MethodHandle;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.candeal.typeconversion.model.Property;
import com.candeal.typeconversion.model.ResolvedConvert;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import io.vavr.Function0;
import io.vavr.Function1;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.SortedMap;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
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
    private final ConversionService conversionService;

    /**
     * Initializes instances of this type from given convert spec and bean factory.
     *
     * @param spec        a {@link ResolvedConvert} object.
     * @param beanFactory a {@link BeanFactory} object.
     */
    public BetweenTypes(final ResolvedConvert spec, final BeanFactory beanFactory) {

        this.spec = spec;
        this.fromType = TypeDescriptor.valueOf(spec.from());
        this.toType = TypeDescriptor.valueOf(spec.to());
        this.setBeanResolver = ctx -> {
            ctx.setBeanResolver(new BeanFactoryResolver(beanFactory));
            return ctx;
        };
        this.conversionService = beanFactory.getBean(ConversionService.class);
    }

    /**
     * Converts source object to target of specified type. Returns the target or null if source was null. Throws various
     * exceptions if conversion fails.
     *
     * @param source   a source object.
     * @param srcType  source object's {@link TypeDescriptor}.
     * @param destType destination object's {@link TypeDescriptor}.
     * @return converted object or null.
     * @throws RuntimeException when conversion fails.
     */
    @Override
    public @Nullable Object convert(@Nullable final Object source, final TypeDescriptor srcType,
                                    final TypeDescriptor destType) {
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
    public boolean matches(final TypeDescriptor srcType, final TypeDescriptor destType) {
        return srcType.isAssignableTo(fromType) && toType.isAssignableTo(destType);
    }

    private Function<StandardEvaluationContext, StandardEvaluationContext> aliasRootTo(final String... aliases) {
        return ctx -> Option.of(ctx.getRootObject())
                            .map(TypedValue::getValue)
                            .peek(root -> Vector.of(aliases)
                                                .forEach(alias -> ctx.setVariable(alias, root)))
                            .mapTo(ctx)
                            .getOrElse(ctx);
    }

    private Try<StandardEvaluationContext> evaluateShortcuts(final StandardEvaluationContext ctx) {
        // Simply push shortcut method handles to the context under their respective variable names
        spec.shortcuts()
            .forEach((variable, method) -> ctx.setVariable(variable, method));
        return Try.success(ctx);
    }

    private Try<StandardEvaluationContext> evaluatePre(final StandardEvaluationContext ctx) {
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

    private Try<StandardEvaluationContext> instantiateAndBindTo(final StandardEvaluationContext ctx) {
        final Function1<Either<MethodHandle, SpelExpression>, Try<?>> evalConstructor =
            either -> Match(either).of(Case($Left($()), mh -> Try.of(() -> mh.invoke())),
                                       Case($Right($()), spel -> Try.of(() -> spel.getValue(ctx))));
        return Function0.of(spec::constructor)
                        .andThen(evalConstructor)
                        .apply()
                        .onFailure(x -> log.atError()
                                           .setCause(x)
                                           .setMessage("Failed to instantiate 'to' type ({}) in converter: {}")
                                           .addArgument(toType)
                                           .addArgument(spec.model())
                                           .log())
                        .andThen(ctx::setRootObject)
                        .mapTo(ctx);
    }

    private Try<StandardEvaluationContext> copyProperties(final StandardEvaluationContext ctx) {
        spec.properties()
            .filter((_ignored, prop) -> prop instanceof Property.Type)
            .map((_ignored, prop) -> Tuple.of(_ignored, (Property.Type) prop))
            .map((propName, prop) -> Tuple.of(propName, tryEvalProperty(propName, prop, ctx)));
        return Try.failure(null);
    }

    private Try<StandardEvaluationContext> evaluatePost(final StandardEvaluationContext ctx) {
        final var evaluatedPost = spec.post()
                                      .map(expression -> tryEvalExpression(expression, ctx));
        return Try.sequence(evaluatedPost)
                  .mapTo(ctx);
    }

    private Try<?> tryEvalExpression(final SpelExpression expression, final StandardEvaluationContext ctx) {
        return Try.of(() -> expression.getValue(ctx))
                  .onFailure(x -> log.atError()
                                     .setCause(x)
                                     .setMessage("Failed to evaluate SpEL: '{}' in converter: {}")
                                     .addArgument(expression.getExpressionString())
                                     .addArgument(spec.model())
                                     .log());
    }

    private Try<?> tryEvalExpressionAndSet(final String variable, final SpelExpression expression,
                                           final StandardEvaluationContext ctx) {
        return tryEvalExpression(expression, ctx).andThen(result -> ctx.setVariable(variable, result));
    }

    private Try<?> tryEvalProperty(final String propertyName,
                                   final Property.Type property,
                                   final StandardEvaluationContext ctx) {
        final var from = ctx.lookupVariable("from");
        final var to = ctx.lookupVariable("to");

        return Try.of(() -> switch (property) {
            case Property.Type.Method(MethodHandle getter, MethodHandle setter) ->
                Tuple.of(setter, to, getter.invoke(from), getter.type().returnType());
            case Property.Type.Expression(SpelExpression getter, MethodHandle setter) ->
                Tuple.of(setter, to, getter.getValue(ctx), getter.getValueType(ctx));
            // @formatter:off
            case Property.Type.Conditional(SortedMap<SpelExpression, SpelExpression> conditions,
                                           List<SpelExpression> fallThroughs,
                                           MethodHandle setter) -> {
            // @formatter:on
                final Tuple2<Object, Class<?>> fromPropValueAndType =
                    evalConditionalProperty(conditions, fallThroughs, ctx);
                yield Tuple.of(setter, to, fromPropValueAndType._1(), fromPropValueAndType._2());
            }
        }).flatMap(result -> result.apply(this::trySetProperty));
    }

    private Tuple2<Object, Class<?>> evalConditionalProperty(SortedMap<SpelExpression, SpelExpression> conditions,
                                                             List<SpelExpression> fallThroughs,
                                                             StandardEvaluationContext ctx) {
        throw new UnsupportedOperationException("Unimplemented method 'evalConditionalProperty'");
    }

    /**
     * Tries to set given value on target with setter method provided using value type specified. Returns Try.Success or
     * Try.Failure.
     */
    private Try<Void> trySetProperty(MethodHandle setter, Object target, Object value, Class<?> valueType) {
        return Try.run(() -> {
            final var setterArgType = setter.type().parameterType(0);
            final var convertedValue = setterArgType.isAssignableFrom(valueType)
                ? value
                : conversionService.convert(value, setterArgType);
            setter.invoke(target, convertedValue);
        });
    }
}
