package com.candeal.typeconversion;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.candeal.typeconversion.model.Property;
import com.candeal.typeconversion.model.ResolvedConvert;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import io.vavr.control.Option;

/**
 * Converts from an integer constant or an array thereof to a target enum type.
 */
public class FromIntegerToEnum implements ConditionalGenericConverter {

    private final ResolvedConvert spec;
    private final TypeDescriptor sourceType;
    private final TypeDescriptor targetType;
    private final TypeDescriptor arraySourceType;

    private static final TypeDescriptor integerType = TypeDescriptor.valueOf(Integer.class);

    /**
     * Intitializes instances of this type with given seed spec.
     *
     * Intitializes instances of this type with given seed spec.
     *
     * @param spec {@link ResolvedConvert} instance to prime converter with.
     */
    public FromIntegerToEnum(final ResolvedConvert spec) {
        Assert.state(Enum.class.isAssignableFrom(spec.to()),
                     "Target 'to' type must be enum: %s in convert: %s".formatted(spec.to(), spec.model()));
        this.spec = spec;
        this.sourceType = TypeDescriptor.valueOf(spec.from());
        this.arraySourceType = TypeDescriptor.array(this.sourceType);
        this.targetType = TypeDescriptor.valueOf(spec.to());
    }

    @Override
    public @Nullable Object convert(@Nullable Object src, TypeDescriptor srcType, TypeDescriptor destType) {
        return Option.when(srcType.isAssignableTo(integerType), src)
                     .orElse(() -> Option.when(srcType.isAssignableTo(arraySourceType), src)
                                         .filter(Collection.class::isInstance)
                                         .map(Collection.class::cast)
                                         .filter(Predicate.not(CollectionUtils::isEmpty)))
                     .filter(Objects::nonNull)
                     .mapTry(this::match)
                     .recover(NoSuchElementException.class, src)
                     .get();
    }

    @Override
    public @Nullable Set<ConvertiblePair> getConvertibleTypes() {
        final var toType = spec.to();
        return Set.of(new ConvertiblePair(Integer.class, toType),
                      new ConvertiblePair(arraySourceType.getType(), toType));
    }

    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {

        return (this.sourceType.isAssignableTo(sourceType) ||
                isArrayWithElementType(sourceType, this.sourceType)) &&
               this.targetType.isAssignableTo(targetType);
    }

    private Object match(Object src) {
        spec.properties()
            .filter((_ignored, prop) -> Property.Enum.class.isInstance(prop));
        return null;
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
}
