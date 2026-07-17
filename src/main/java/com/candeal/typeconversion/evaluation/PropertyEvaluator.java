package com.candeal.typeconversion.evaluation;

import java.lang.invoke.MethodHandle;

import com.candeal.typeconversion.model.Property;

import org.jspecify.annotations.Nullable;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import io.vavr.control.Try;


/**
 * Evaluates properties to a result type {@code R}
 */
public class PropertyEvaluator extends SpelEvaluator<Property, Object> {

    public PropertyEvaluator(StandardEvaluationContext ctx) {
        super(ctx);
    }

    @Override
    protected Object evaluateInContext(@Nullable Property subject, StandardEvaluationContext ctx, Object... args) {

        return switch (subject) {
            case Property.Type.Method(MethodHandle getter, MethodHandle setter) -> Try.of(() -> getter.invoke(args)).get();
            default -> null;
        };
    }

}
