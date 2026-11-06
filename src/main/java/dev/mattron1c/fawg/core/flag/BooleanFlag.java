package dev.mattron1c.fawg.core.flag;

import java.util.List;
import java.util.Locale;

public class BooleanFlag extends Flag<Boolean> {
    private final Boolean def;

    public BooleanFlag(String name, FlagCategory cat) { this(name, cat, null); }

    public BooleanFlag(String name, FlagCategory cat, Boolean def) { super(name, cat); this.def = def; }

    @Override public Boolean defaultValue() { return def; }

    @Override public Boolean parse(String in) throws InvalidFlagFormatException {
        return switch (in.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "allow", "enable", "enabled", "y", "1" -> true;
            case "false", "no", "off", "deny", "disable", "disabled", "n", "0" -> false;
            default -> throw new InvalidFlagFormatException("Expected true or false");
        };
    }

    @Override public Object marshal(Boolean v) { return v; }

    @Override public Boolean unmarshal(Object raw) {
        if (raw instanceof Boolean b) return b;
        if (raw == null) return null;
        try { return parse(raw.toString()); } catch (InvalidFlagFormatException e) { return null; }
    }

    @Override public List<String> suggestions() { return List.of("true", "false"); }

    @Override public String typeName() { return "true/false"; }
}
