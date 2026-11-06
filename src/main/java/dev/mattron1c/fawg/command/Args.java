package dev.mattron1c.fawg.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Splits "-x value" / "-y" options from positional arguments (WorldGuard style). */
public final class Args {
    public final List<String> pos = new ArrayList<>();
    public final Map<Character, String> values = new HashMap<>();
    public final Set<Character> switches = new HashSet<>();

    /**
     * @param valueFlags letters that take a value (eg. "gwph")
     * @param switchFlags letters that are booleans (eg. "aefsu")
     * @param stopAfter stop parsing options after this many positionals (rest is free text), -1 for never
     */
    public static Args parse(String[] raw, int from, String valueFlags, String switchFlags, int stopAfter) {
        Args a = new Args();
        boolean free = false;
        for (int i = from; i < raw.length; i++) {
            String t = raw[i];
            if (!free && t.length() == 2 && t.charAt(0) == '-' && Character.isLetter(t.charAt(1))) {
                char c = Character.toLowerCase(t.charAt(1));
                if (valueFlags.indexOf(c) >= 0 && i + 1 < raw.length) { a.values.put(c, raw[++i]); continue; }
                if (switchFlags.indexOf(c) >= 0) { a.switches.add(c); continue; }
            }
            a.pos.add(t);
            if (stopAfter >= 0 && a.pos.size() >= stopAfter) free = true;
        }
        return a;
    }

    public String get(int i) { return i < pos.size() ? pos.get(i) : null; }

    public String rest(int from) {
        if (from >= pos.size()) return null;
        return String.join(" ", pos.subList(from, pos.size()));
    }

    public boolean has(char c) { return switches.contains(c) || values.containsKey(c); }

    public String value(char c) { return values.get(c); }

    public int size() { return pos.size(); }

    public int intValue(char c, int def) {
        try { return values.containsKey(c) ? Integer.parseInt(values.get(c)) : def; } catch (NumberFormatException e) { return def; }
    }
}
