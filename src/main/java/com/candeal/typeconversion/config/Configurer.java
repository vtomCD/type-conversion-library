package com.candeal.typeconversion.config;

import com.candeal.typeconversion.model.Convert;
import com.candeal.typeconversion.model.Convert.Property;
import com.candeal.typeconversion.model.Options;
import com.candeal.typeconversion.model.ResolvedConvert;
import com.candeal.typeconversion.util.Fp;

import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.JsonNodeException;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeType;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static java.util.Map.Entry;

/**
 * Discovers typeconversion "convert" files in various configured locations, parses them, creates and registers
 * converters.
 */
@Slf4j
public class Configurer {
    private static final String FAILED_TO_PARSE_RESOURCE =
        "Failed to parse type conversion definitition from resource: {}";
    private static final String FAILED_TO_GET_RESOURCES = "Failed to resolve resource location pattern: '{}'";
    private static final YAMLMapper mapper = YAMLMapper.builder()
                                                       // Add parser features (e.g. YAMLParser.Feature) if any
                                                       .build();
    private final Location location;
    private final ConverterRegistry converterRegistry;

    /**
     * Initializes instances of this type with given arguments.
     *
     * @param location a {@link Location} config.
     * @param registry a {@link ConverterRegistry} bean.
     */
    public Configurer(final Location location, final ConverterRegistry registry) {
        this.location = location;
        this.converterRegistry = registry;
    }

    /**
     * Discovers type conversion definitions from a configured location patterns, loads and parses underlying
     * resources, creates Spring generic converters from them and registers the converters with Spring conversion
     * service.
     *
     * @throws RuntimeException if any errors were encountered in the process.
     */
    public void configure() {
        log.info("Staring processing type conversions from: {}", location);

        discover(location).flatMap(Configurer::parse)
                          .flatMap(Configurer::parseModels)
                          .flatMap(Configurer::resolve)
                          .flatMap(this::createConverters)
                          .flatMap(this::register)
                          .andThen(tcs -> log.info("{} type conversions processed successfully", tcs.size()))
                          .andFinally(() -> log.info("Type conversions processing complete."))
                          .get(); // Will throw RuntimeException on error
    }

    /**
     * @param location a {@link Location} to discover convert specs in.
     *
     * @return a {@link Try.Success} with sequence of resources, found at various configured location patterns or
     *         {@link Try.Failure} with due exception if otherwise any of location patterns failed to be discovered.
     */
    private static Try<Seq<Resource>> discover(final Location location) {
        log.info("Discovering type conversion definition(s)...");
        final var resolver = new PathMatchingResourcePatternResolver();
        return Try.traverse(location.generateLocationPatterns(),
                            locPattern -> Try.success(locPattern)
                                             .mapTry(resolver::getResources)
                                             .onFailure(x -> log.atError()
                                                                .setCause(x)
                                                                .setMessage(FAILED_TO_GET_RESOURCES)
                                                                .addArgument(locPattern)
                                                                .log()))
                  .map(resSeq -> resSeq.flatMap(LinkedHashSet::of))
                  .andThen(seq -> log.atInfo()
                                     .setMessage("Discovered {} type conversion definition(s)")
                                     .addArgument(seq::size)
                                     .log())
                  .andFinally(() -> log.info("Type conversion definitions discovery is complete"));
    }

    private static Try<Seq<JsonNode>> parse(final Seq<Resource> convertSpecs) {
        log.info("Parsing {} type conversion definition(s)...", convertSpecs.size());
        return Try.traverse(convertSpecs,
                            specResource -> Try.of(() -> mapper.readTree(specResource.getInputStream()))
                                               .onFailure(x -> log.atError()
                                                                  .setCause(x)
                                                                  .setMessage(FAILED_TO_PARSE_RESOURCE)
                                                                  .addArgument(specResource)
                                                                  .log()))
                  .andThen(jns -> log.info("Parsed {} type conversion definition(s)", jns.size()))
                  .andFinally(() -> log.info("Finished parsing type conversion definitions"));
    }

    private static Try<Seq<Convert>> parseModels(final Seq<JsonNode> parsedSpecs) {
        log.info("Loading {} type conversion definition(s)...", parsedSpecs.size());
        return Try.traverse(parsedSpecs, Configurer::parseModel)
                  .andThen(jns -> log.info("Loaded {} type conversion definition(s)", jns.size()))
                  .andFinally(() -> log.info("Finished loading type conversion definitions"));
    }

    // Parses given spec node with recursive descent
    private static Try<Convert> parseModel(final JsonNode specNode) {
        final var builder = Convert.builder();
        return Option.ofOptional(specNode.optional("convert"))
                     .toTry()
                     .andThen(node -> builder.from(classFor(node, "from")))
                     .andThen(node -> builder.to(classFor(node, "to")))
                     .andThen(node -> builder.options(Configurer.parseOptionsNode(Fp.jsonOption(node, "options"))))
                     .andThen(node -> builder.shortcuts(Configurer.parseShortcuts(Fp.jsonOption(node, "shortcuts"))))
                     .andThen(node -> builder.pre(Configurer.parsePre(Fp.jsonOption(node, "pre"))))
                     .andThen(node -> builder.constructor(Fp.jsonOption(node, "constructor")
                                                            .filter(String.class::isInstance)
                                                            .map(String.class::cast)))
                     .andThen(node -> builder.properties(Configurer.parseProperties(Fp.jsonOption(node, "properties"))))
                     .andThen(node -> builder.post(Configurer.parsePost(Fp.jsonOption(node, "post"))))
                     .map(_ignore -> builder.build());
    }

    // Parses "post" entries, returns ordered set of strings SpELs or an empty set if no post entries were found
    private static LinkedHashSet<String> parsePost(Option<?> jsonOption) {
        // Post element is expected to be an array or strings
        return jsonOption.filter(ArrayNode.class::isInstance)
                         .map(ArrayNode.class::cast)
                         .map(ArrayNode::values)
                         .map(LinkedHashSet::ofAll)
                         .mapTry(vals -> vals.filter(JsonNode::isString)
                                             .map(JsonNode::asString))
                         .onFailure(JsonNodeException.class, x -> log.atError()
                                                                     .setCause(x)
                                                                     .setMessage("Failed to treat element as string: {}")
                                                                     .addArgument(jsonOption)
                                                                     .log())
                         .getOrElse(LinkedHashSet::empty);
    }

    private static Map<String, Convert.Property> parseProperties(Option<?> jsonOption) {
        // Properties is an object
        return jsonOption.filter(ObjectNode.class::isInstance)
                  .map(ObjectNode.class::cast)
                  .map(ObjectNode::propertyStream)
                  .map(ps -> LinkedHashMap.ofAll(ps, Entry::getKey, Entry::getValue))
                  .map(ps -> ps.<Convert.Property>mapValues(jn -> switch (jn.getNodeType()) {
                      case JsonNodeType.STRING -> new Convert.Property.Spel(jn.asString());
                      case JsonNodeType.ARRAY -> parseConditionalProperty(jn.asArray());
                      default -> throw new UnsupportedOperationException("Invalid property type: %s".formatted(jn));
                  }))
            .get();
    }

    private static Convert.Property.Conditional parseConditionalProperty(ArrayNode asArray) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'parseConditionalProperty'");
    }

    private static LinkedHashMap<String, String> parsePre(Option<?> jsonOption) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'parsePre'");
    }

    private static Map<String, String> parseShortcuts(Option<?> jsonOption) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'parseShortcuts'");
    }

    private static Option<Options> parseOptionsNode(Option<?> jsonOption) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'parseOptionsNode'");
    }

    private static Try<Seq<ResolvedConvert>> resolve(final Seq<Convert> modelSpecs) {
        log.info("Resolving {} type conversion definition(s)...", modelSpecs.size());
        return Try.failure(new UnsupportedOperationException());
    }


    private Try<Seq<GenericConverter>> createConverters(final Seq<ResolvedConvert> converts) {
        log.info("Creating {} type converter(s)...", converts.size());
        return Try.failure(new UnsupportedOperationException());
    }

    private Try<Seq<GenericConverter>> register(final Seq<GenericConverter> converters) {
        log.info("Registering {} type conversion(s)...", converters.size());
        return Try.traverse(converters, converter -> Try.run(() -> converterRegistry.addConverter(converter))
                                                        .mapTo(converter));
    }

    private static Option<Class<?>> classFor(final JsonNode node, final String classChildNodeName) {
        return Fp.jsonOption(node, classChildNodeName)
                 .filter(String.class::isInstance)
                 .map(String.class::cast)
                 .<Class<?>>mapTry(Class::forName)
                 .toOption();
    }
}
