package com.candeal.typeconversion.config;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.vavr.API;
import io.vavr.collection.SortedSet;
import io.vavr.collection.TreeSet;

/**
 * Provides way to configure and defaults for type conversion spec files location(s) and naming pattern.
 */
@ConfigurationProperties(prefix = "candeal.typeconversion")
@Configuration
public record Location(java.util.SortedSet<String> locations, String pattern) {
    /// All locations are treated as "optional"
    private static final SortedSet<String> DEFAILT_LOCATIONS =
        API.SortedSet("classpath:/", "classpath:/typeconversions/", "file:./typeconversions/", "file:./config/",
                      "file:./config/typeconversions/");
    private static final String DEFAULT_PATTERN = "*-typeconversions.{ext:yaml|yml}";
    private static final String FILE_SEPARATOR = System.getProperty("file.separator");

    /// Provides sensible defaults if parameters aren't supplied
    public Location {
        if (Objects.isNull(pattern) || pattern.isBlank()) {
            pattern = DEFAULT_PATTERN;
        }

        if (Objects.isNull(locations) || locations.isEmpty()) {
            locations = DEFAILT_LOCATIONS.toJavaSet();
        }
    }

    /**
     * Generates and returns a sorted set of complete location patterns, suitable for resource mapping.
     */
    public SortedSet<String> generateLocationPatterns() {
        final Function<String, String> appendPathOnlyIfLocationIsNotDir =
            location -> location.endsWith(FILE_SEPARATOR) ? location.concat(pattern) : location;
        return TreeSet.ofAll(locations)
                      .filter(Predicate.not(String::isBlank))
                      .map(appendPathOnlyIfLocationIsNotDir);
    }
}
