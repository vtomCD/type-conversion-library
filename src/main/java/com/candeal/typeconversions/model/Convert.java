package com.candeal.typeconversions.model;

import java.lang.invoke.MethodHandle;

import org.springframework.expression.spel.standard.SpelExpression;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.TreeMap;
import io.vavr.collection.TreeSet;
import io.vavr.control.Option;

public record Convert(
    Option<Class<?>> from,
    Option<Class<?>> to,
    Option<String> id,
    TreeSet<Convert> extendsDefs,
    Option<Options> options,
    Map<String, MethodHandle> shortcuts,
    TreeMap<String, SpelExpression> pre,
    MethodHandle constructor,
    Map<String, Property> properties,
    List<SpelExpression> post
)
{
}
