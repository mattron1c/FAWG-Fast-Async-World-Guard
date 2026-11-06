package dev.mattron1c.fawg.core.flag;

public class DoubleFlag extends Flag<Double> {
    private final double min, max;
    private final Double def;

    public DoubleFlag(String name, FlagCategory cat, double min, double max) { this(name, cat, min, max, null); }

    public DoubleFlag(String name, FlagCategory cat, double min, double max, Double def) {
        super(name, cat); this.min = min; this.max = max; this.def = def;
    }

    public double min() { return min; }
    public double max() { return max; }

    @Override public Double defaultValue() { return def; }

    @Override public Double parse(String in) throws InvalidFlagFormatException {
        try {
            double v = Double.parseDouble(in.trim());
            if (Double.isNaN(v) || v < min || v > max) throw new InvalidFlagFormatException("Value must be between " + min + " and " + max);
            return v;
        } catch (NumberFormatException e) {
            throw new InvalidFlagFormatException("Expected a decimal number");
        }
    }

    @Override public Object marshal(Double v) { return v; }

    @Override public Double unmarshal(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        if (raw == null) return null;
        try { return Double.parseDouble(raw.toString()); } catch (NumberFormatException e) { return null; }
    }

    @Override public String typeName() { return "decimal (" + min + " … " + max + ")"; }
}
