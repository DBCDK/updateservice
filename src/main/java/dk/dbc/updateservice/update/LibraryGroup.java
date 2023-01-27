/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum LibraryGroup {
    DBC("dbc", false, "dbc", "ffu", "lokbib"),
    FBS("fbs", true, "fbs", "fbslokal", "skole"),
    PH("ph", true, "ph"),
    SBCI("sbci", true, "sbci");

    private final String value;
    private final boolean fbs;
    private final List<String> rules;
    private static final Map<String, LibraryGroup> RULE_MAP = Arrays.stream(values())
            .flatMap(r -> r.rules.stream().map(e -> new KeyValue<>(e, r)))
            .collect(Collectors.toMap(e -> e.key, e -> e.value));

    LibraryGroup(final String value, boolean fbs, String... ruleMapping) {
        this.value = value;
        this.fbs = fbs;
        rules = Arrays.asList(ruleMapping);
    }

    public static Optional<LibraryGroup> fromRule(String rule) {
        return Optional.ofNullable(RULE_MAP.get(rule));
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return this.getValue();
    }

    public boolean isDBC() {
        return DBC == this;
    }

    // PH is also a FBS library
    public boolean isFBS() {
        return fbs;
    }

    public boolean isPH() {
        return PH == this;
    }

    public boolean isSBCI() {
        return SBCI == this;
    }

    public static class KeyValue<K, V> {
        public final K key;
        public final V value;

        public KeyValue(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
