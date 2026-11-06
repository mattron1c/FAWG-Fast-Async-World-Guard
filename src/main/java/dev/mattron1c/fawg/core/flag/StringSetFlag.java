package dev.mattron1c.fawg.core.flag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Comma / space separated set (materials, entity types, commands, effects...). The normalizer may reject
 * an element by returning null.
 */
public class StringSetFlag extends Flag<Set<String>> {
    private final Function<String, String> normalizer;
    private final Supplier<Collection<String>> hints;

    public StringSetFlag(String name, FlagCategory cat, Function<String, String> normalizer, Supplier<Collection<String>> hints) {
        super(name, cat);
        this.normalizer = normalizer == null ? s -> s : normalizer;
        this.hints = hints == null ? Collections::emptyList : hints;
    }

    @Override public Set<String> parse(String in) throws InvalidFlagFormatException {
        Set<String> out = new LinkedHashSet<>();
        String s = in.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) s = s.substring(1, s.length() - 1);
        if (s.isEmpty()) return out;
        for (String part : s.split("\\s*,\\s*|\\s+(?=\\S)")) {
            if (part.isBlank()) continue;
            String n = normalizer.apply(part.trim());
            if (n == null) throw new InvalidFlagFormatException("Unknown entry '" + part + "'");
            out.add(n);
        }
        return Collections.unmodifiableSet(out);
    }

    @Override public Object marshal(Set<String> v) { return new ArrayList<>(v); }

    @Override public Set<String> unmarshal(Object raw) {
        if (raw == null) return null;
        Set<String> out = new LinkedHashSet<>();
        if (raw instanceof Collection<?> c) {
            for (Object o : c) { String n = normalizer.apply(String.valueOf(o)); if (n != null) out.add(n); }
        } else {
            try { return parse(raw.toString()); } catch (InvalidFlagFormatException e) {
                for (String p : raw.toString().split(",")) { String n = normalizer.apply(p.trim()); if (n != null) out.add(n); }
            }
        }
        return Collections.unmodifiableSet(out);
    }

    @Override public String format(Set<String> v) { return v == null ? "unset" : String.join(", ", v); }

    @Override public List<String> suggestions() { return new ArrayList<>(hints.get()); }

    @Override public String typeName() { return "list"; }
}
