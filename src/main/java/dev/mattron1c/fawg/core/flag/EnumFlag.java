package dev.mattron1c.fawg.core.flag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EnumFlag<E extends Enum<E>> extends Flag<E> {
    private final Class<E> type;
    private final E def;

    public EnumFlag(String name, FlagCategory cat, Class<E> type) { this(name, cat, type, null); }

    public EnumFlag(String name, FlagCategory cat, Class<E> type, E def) { super(name, cat); this.type = type; this.def = def; }

    public Class<E> enumType() { return type; }

    @Override public E defaultValue() { return def; }

    @Override public E parse(String in) throws InvalidFlagFormatException {
        String k = in.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (E e : type.getEnumConstants()) if (e.name().equals(k)) return e;
        throw new InvalidFlagFormatException("Expected one of " + suggestions());
    }

    @Override public Object marshal(E v) { return v.name().toLowerCase(Locale.ROOT); }

    @Override public E unmarshal(Object raw) {
        if (raw == null) return null;
        try { return parse(raw.toString()); } catch (InvalidFlagFormatException e) { return null; }
    }

    @Override public String format(E v) { return v == null ? "unset" : v.name().toLowerCase(Locale.ROOT).replace('_', '-'); }

    @Override public List<String> suggestions() {
        List<String> l = new ArrayList<>();
        for (E e : type.getEnumConstants()) l.add(e.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        return l;
    }

    @Override public String typeName() { return "choice"; }
}
