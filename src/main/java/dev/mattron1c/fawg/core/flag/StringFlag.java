package dev.mattron1c.fawg.core.flag;

/** Free text (supports MiniMessage and legacy & colours when displayed). */
public class StringFlag extends Flag<String> {
    private final String def;

    public StringFlag(String name, FlagCategory cat) { this(name, cat, null); }

    public StringFlag(String name, FlagCategory cat, String def) { super(name, cat); this.def = def; }

    @Override public String defaultValue() { return def; }

    @Override public String parse(String in) {
        String s = in;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.replace("\\n", "\n");
    }

    @Override public Object marshal(String v) { return v; }

    @Override public String unmarshal(Object raw) { return raw == null ? null : raw.toString(); }

    @Override public String typeName() { return "text"; }
}
