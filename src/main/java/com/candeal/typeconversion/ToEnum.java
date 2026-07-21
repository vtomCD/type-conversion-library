package com.candeal.typeconversion;

import java.util.Collection;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import com.candeal.typeconversion.model.Property;
import com.candeal.typeconversion.model.ResolvedConvert;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import io.vavr.Function1;
import io.vavr.Function4;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * Converts from an integer or string constant or an array thereof to a target enum type.
 */
public class ToEnum extends AbstractTypeConverter {

    private final TypeDescriptor sourceType;
    private final TypeDescriptor targetType;
    private final TypeDescriptor arraySourceType;
    private final Set<Enum<?>> targetConstants;

    /**
     * Intitializes instances of this type with given seed spec.
     *
     * Intitializes instances of this type with given seed spec.
     *
     * @param spec {@link ResolvedConvert} instance to prime converter with.
     */
    public ToEnum(final ResolvedConvert spec, final BeanFactory beanFactory) {
        super(spec, beanFactory);
        Assert.state(Enum.class.isAssignableFrom(spec.to()),
                     "Target 'to' type must be enum: %s in convert: %s".formatted(spec.to(), spec.model()));
        this.sourceType = TypeDescriptor.valueOf(spec.from());
        this.arraySourceType = TypeDescriptor.array(this.sourceType);
        this.targetType = TypeDescriptor.valueOf(spec.to());
        this.targetConstants = HashSet.of(spec.to().getEnumConstants()).map(Enum.class::cast);
    }

    @Override
    public @Nullable Object convert(@Nullable Object src, TypeDescriptor srcType, TypeDescriptor destType) {
        final Function1<StandardEvaluationContext, Try<?>> converter =
            Function4.of(this::doConvert).apply(src, srcType, destType)::apply;

        return Try.of(StandardEvaluationContext::new)
                  .map(setBeanResolver)
                  .flatMap(this::evaluateShortcuts)
                  .flatMap(this::evaluatePre)
                  .flatMap(converter);


    }

    @Override
    public java.util.Set<ConvertiblePair> getConvertibleTypes() {
        final var toType = getSpec().to();
        return java.util.Set.of(new ConvertiblePair(getSpec().from(), toType),
                                new ConvertiblePair(arraySourceType.getType(), toType));
    }

    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return (this.sourceType.isAssignableTo(sourceType) ||
                isArrayWithElementType(sourceType, this.sourceType)) &&
               this.targetType.isAssignableTo(targetType);
    }

    @SuppressWarnings("unchecked")
    private Try<?> doConvert(@Nullable Object src, TypeDescriptor srcType, TypeDescriptor destType,
                             StandardEvaluationContext ctx) {

        return Option.when(srcType.isAssignableTo(sourceType), src)
                     .map(srcType.getType()::cast)
                     .map(Collections::singleton)
                     .map(Collection.class::cast)
                     .orElse(() -> Option.when(srcType.isAssignableTo(arraySourceType), src)
                                         .map(Collection.class::cast))
                     .filter(Predicate.not(CollectionUtils::isEmpty))
                     .map(c -> (Collection<Object>) c)
                     .map(HashSet::ofAll)
                     .mapTry(consts -> match(consts, ctx))
                     .recover(NoSuchElementException.class, src);
    }

    /**
     * Returns enumeration constant that matches any of values in given collection.
     *
     * @param src incoming {@link Set} of candidates.
     * @return an {@link Enum} contstant that matches one of the candidates.
     * @throws NoSuchElementException if there are no conversions.
     */
    private Object match(Set<Object> src, StandardEvaluationContext ctx) {
        return getSpec().properties()
                        .filter((_ignored, prop) -> Property.Enum.class.isInstance(prop))
                        .map(t -> t.map2(Property.Enum.class::cast))
                        .find(kv -> switch (kv._2()) {
                            case Property.Enum.SingleConstant(Object c) -> src.contains(c);
                            case Property.Enum.SetOfConstants(Set<?> constants) ->
                                constants.filter(sourceType.getType()::isInstance)
                                         .map(Object.class::cast)
                                         .intersect(src)
                                         .nonEmpty();
                            case Property.Enum.Spel(SpelExpression expression) ->
                                Try.of(() -> expression.getValue(ctx))
                                   .filter(sourceType.getType()::isInstance)
                                   .map(src::contains)
                                   .get();
                            default -> false;
                        })
                        .map(Tuple2::_1)
                        .flatMap(this::valueOf) // Find contant by name
                        // Or fallback to ordinal if possible
                        .orElse(() -> src.iterator()
                                         .filter(Integer.class::isInstance)
                                         .map(Integer.class::cast)
                                         .flatMap(this::valueOf)
                                         .headOption())
                        .get();  // TODO: Deal with partials.
    }

    /**
     * @return true if given "possibleArrayTypeDescriptor" is an array of "elementType".
     *
     * @param possibleArrayTypeDescriptor {@link TypeDescriptor} to test for "array-ness".
     * @param elementType                 {@link TypeDescriptor} expected array's element type.
     */
    private static boolean isArrayWithElementType(final TypeDescriptor possibleArrayTypeDescriptor,
                                                  final TypeDescriptor elementType) {
        final var arrayOfElementType = TypeDescriptor.array(elementType);
        return possibleArrayTypeDescriptor.isArray() && possibleArrayTypeDescriptor.isAssignableTo(arrayOfElementType);
    }

    private Option<Enum<?>> valueOf(final String name) {
        return targetConstants.find(e -> name.equalsIgnoreCase(e.name()));
    }

    private Option<Enum<?>> valueOf(int ordinal) {
        return targetConstants.find(e -> e.ordinal() == ordinal);
    }
}
