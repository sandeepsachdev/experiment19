package com.example.freetotv.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Reference data for the country picker. Maps human-readable country names (shown in the UI and
 * accepted by the API) to the ISO 3166-1 alpha-2 codes that TVmaze expects.
 */
@Component
public class Countries {

    public record Country(String code, String name) {
    }

    // Ordered list of supported free-to-air markets; Australia first as the default.
    private static final List<Country> ALL = List.of(
            new Country("AU", "Australia"),
            new Country("NZ", "New Zealand"),
            new Country("GB", "United Kingdom"),
            new Country("IE", "Ireland"),
            new Country("US", "United States"),
            new Country("CA", "Canada"),
            new Country("DE", "Germany"),
            new Country("FR", "France"),
            new Country("ES", "Spain"),
            new Country("IT", "Italy"),
            new Country("NL", "Netherlands"),
            new Country("IN", "India"));

    private final Map<String, String> codeByName = new LinkedHashMap<>();
    private final Map<String, String> nameByCode = new LinkedHashMap<>();

    public Countries() {
        for (Country country : ALL) {
            codeByName.put(country.name().toLowerCase(Locale.ROOT), country.code());
            nameByCode.put(country.code(), country.name());
        }
    }

    public List<Country> all() {
        return ALL;
    }

    /**
     * Resolves a country name or ISO code (case-insensitive) to an ISO code, falling back to the
     * supplied default when the input is blank or unrecognised.
     */
    public String resolveCode(String input, String defaultCode) {
        if (StringUtils.hasText(input)) {
            String trimmed = input.trim();
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (nameByCode.containsKey(upper)) {
                return upper;
            }
            String byName = codeByName.get(trimmed.toLowerCase(Locale.ROOT));
            if (byName != null) {
                return byName;
            }
        }
        return defaultCode;
    }

    /** Returns the display name for a code, or the code itself if unknown. */
    public String nameFor(String code) {
        return nameByCode.getOrDefault(code, code);
    }
}
