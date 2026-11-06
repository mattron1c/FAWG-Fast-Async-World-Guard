package dev.mattron1c.fawg.core.flag;

import dev.mattron1c.fawg.core.region.RegionGroup;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A region flag. Flags are registered once in the {@link FlagRegistry} and receive a dense integer id
 * which lets regions store their values in a flat array (O(1) lookups, no hashing on the hot path).
 */
public abstract class Flag<T> {
    private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9_\\-.]{0,63}$");

    private final String name;
    private final FlagCategory category;
    private String description = "";
    private String action;
    private RegionGroup defaultGroup = RegionGroup.ALL;
    int id = -1;
    private boolean builtin = true;

    protected Flag(String name, FlagCategory category) {
        String n = name.toLowerCase(Locale.ROOT);
        if (!VALID.matcher(n).matches()) throw new IllegalArgumentException("Invalid flag name: " + name);
        this.name = n;
        this.category = category;
    }

    public final String name() { return name; }
    public final int id() { return id; }
    public final FlagCategory category() { return category; }
    public String description() { return description; }
    public RegionGroup defaultGroup() { return defaultGroup; }
    public boolean builtin() { return builtin; }

    @SuppressWarnings("unchecked")
    public <F extends Flag<T>> F desc(String d) { this.description = d; return (F) this; }

    /** Phrase used in deny messages: "You can't &lt;action&gt; here". */
    public String action() { return action != null ? action : name.replace('-', ' '); }

    @SuppressWarnings("unchecked")
    public <F extends Flag<T>> F action(String a) { this.action = a; return (F) this; }

    @SuppressWarnings("unchecked")
    public <F extends Flag<T>> F group(RegionGroup g) { this.defaultGroup = g; return (F) this; }

    @SuppressWarnings("unchecked")
    public <F extends Flag<T>> F external() { this.builtin = false; return (F) this; }

    /** Parses user input (command / dialog). */
    public abstract T parse(String input) throws InvalidFlagFormatException;

    /** Converts to a YAML-friendly value. */
    public abstract Object marshal(T value);

    /** Reads a YAML value. Returns null when the stored value is invalid. */
    public abstract T unmarshal(Object raw);

    /** Human readable value. */
    public String format(T value) { return String.valueOf(value); }

    /** Value used when no region and no global region defines the flag. */
    public T defaultValue() { return null; }

    /** Tab completion hints for the value argument. */
    public List<String> suggestions() { return Collections.emptyList(); }

    /** Short type label for docs / GUIs. */
    public abstract String typeName();

    @SuppressWarnings("unchecked")
    public final T cast(Object o) { return (T) o; }

    @Override public String toString() { return name; }
    @Override public final int hashCode() { return name.hashCode(); }
    @Override public final boolean equals(Object o) { return o == this; }
}
