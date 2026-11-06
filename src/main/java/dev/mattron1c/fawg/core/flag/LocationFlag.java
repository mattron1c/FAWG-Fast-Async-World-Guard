package dev.mattron1c.fawg.core.flag;

import java.util.LinkedHashMap;
import java.util.Map;

/** world,x,y,z[,yaw,pitch] — "here" is substituted by the command layer. */
public class LocationFlag extends Flag<LocationFlag.Loc> {
    public record Loc(String world, double x, double y, double z, float yaw, float pitch) {
        @Override public String toString() {
            return world + "," + r(x) + "," + r(y) + "," + r(z) + "," + r(yaw) + "," + r(pitch);
        }
        private static String r(double d) { return (Math.rint(d * 100) / 100.0) + ""; }
    }

    public LocationFlag(String name, FlagCategory cat) { super(name, cat); }

    @Override public Loc parse(String in) throws InvalidFlagFormatException {
        String[] p = in.trim().replace(' ', ',').split(",+");
        if (p.length < 4) throw new InvalidFlagFormatException("Expected world,x,y,z[,yaw,pitch] or 'here'");
        try {
            return new Loc(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    p.length > 4 ? Float.parseFloat(p[4]) : 0f, p.length > 5 ? Float.parseFloat(p[5]) : 0f);
        } catch (NumberFormatException e) {
            throw new InvalidFlagFormatException("Coordinates must be numbers");
        }
    }

    @Override public Object marshal(Loc v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", v.world()); m.put("x", v.x()); m.put("y", v.y()); m.put("z", v.z());
        m.put("yaw", (double) v.yaw()); m.put("pitch", (double) v.pitch());
        return m;
    }

    @Override public Loc unmarshal(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map<?, ?> m) {
            try {
                return new Loc(String.valueOf(m.get("world")), d(m.get("x")), d(m.get("y")), d(m.get("z")), (float) d(m.get("yaw")), (float) d(m.get("pitch")));
            } catch (Exception e) { return null; }
        }
        try { return parse(raw.toString()); } catch (InvalidFlagFormatException e) { return null; }
    }

    private static double d(Object o) { return o instanceof Number n ? n.doubleValue() : o == null ? 0 : Double.parseDouble(o.toString()); }

    @Override public java.util.List<String> suggestions() { return java.util.List.of("here"); }

    @Override public String typeName() { return "location"; }
}
