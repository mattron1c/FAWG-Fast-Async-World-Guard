package dev.mattron1c.fawg.core.flag;

public class IntFlag extends Flag<Integer> {
    private final int min, max;
    private final Integer def;

    public IntFlag(String name, FlagCategory cat, int min, int max) { this(name, cat, min, max, null); }

    public IntFlag(String name, FlagCategory cat, int min, int max, Integer def) {
        super(name, cat); this.min = min; this.max = max; this.def = def;
    }

    public int min() { return min; }
    public int max() { return max; }

    @Override public Integer defaultValue() { return def; }

    @Override public Integer parse(String in) throws InvalidFlagFormatException {
        try {
            int v = (int) Math.round(Double.parseDouble(in.trim()));
            if (v < min || v > max) throw new InvalidFlagFormatException("Value must be between " + min + " and " + max);
            return v;
        } catch (NumberFormatException e) {
            throw new InvalidFlagFormatException("Expected a whole number");
        }
    }

    @Override public Object marshal(Integer v) { return v; }

    @Override public Integer unmarshal(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        if (raw == null) return null;
        try { return (int) Math.round(Double.parseDouble(raw.toString())); } catch (NumberFormatException e) { return null; }
    }

    @Override public String typeName() { return "number (" + min + " … " + max + ")"; }
}
