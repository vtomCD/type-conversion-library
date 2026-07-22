package com.candeal.typeconversion.util;

import io.vavr.control.Option;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;
import tools.jackson.databind.node.POJONode;

public interface Fp {
    static Option<?> jsonOption(final JsonNode node, final String name) {
        return Option.ofOptional(node.optional(name))
                     .flatMap(cn -> switch (cn.getNodeType()) {
                         case JsonNodeType.ARRAY -> Option.of(cn.asArray());
                         case JsonNodeType.NULL -> Option.none();
                         case JsonNodeType.BINARY -> Option.of(cn.binaryValue());
                         case JsonNodeType.BOOLEAN -> Option.of(cn.asBoolean());
                         case JsonNodeType.MISSING -> Option.none();
                         case JsonNodeType.NUMBER -> Option.of(cn.numberValue());
                         case JsonNodeType.OBJECT -> Option.ofOptional(cn.asObjectOpt());
                         case JsonNodeType.POJO -> Option.of(((POJONode) cn).getPojo());
                         case JsonNodeType.STRING -> Option.of(cn.asString());
                     });

    }
}
