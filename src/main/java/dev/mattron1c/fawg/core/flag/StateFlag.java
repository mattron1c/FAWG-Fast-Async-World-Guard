package dev.mattron1c.fawg.core.flag;

import java.util.List;
import java.util.Locale;

/**
 * allow / deny flag. A state flag may declare more general "parent" flags; resolution walks from the most
 * specific flag to the most general one. Membership-gated flags fall back to region membership (like build).
 */
public class StateFlag extends Flag<StateFlag.State> {
    public enum State { ALLOW, DENY;
        public static State of(boolean b) { return b ? ALLOW : DENY; }
    }

    private final State def;
    private boolean membershipGated;
    private StateFlag[] parents = new StateFlag[0];

    public StateFlag(String name, FlagCategory cat, State def) {
        super(name, cat);
        this.def = def;
    }

    public StateFlag(String name, FlagCategory cat, boolean defAllow) {
        this(name, cat, defAllow ? State.ALLOW : State.DENY);
    }

    public StateFlag gated() { this.membershipGated = true; this.gatedCache = null; return this; }

    public StateFlag parents(StateFlag... p) { this.parents = p; this.chainCache = null; return this; }

    private volatile StateFlag[] chainCache;
    private volatile Boolean gatedCache;

    public boolean membershipGated() { return membershipGated; }

    public StateFlag[] parents() { return parents; }

    @Override public State defaultValue() { return def; }

    @Override public State parse(String in) throws InvalidFlagFormatException {
        String s = in.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "allow", "true", "yes", "on", "enable", "enabled", "a", "y" -> State.ALLOW;
            case "deny", "false", "no", "off", "disable", "disabled", "d", "n" -> State.DENY;
            default -> throw new InvalidFlagFormatException("Expected allow or deny");
        };
    }

    @Override public Object marshal(State v) { return v.name().toLowerCase(Locale.ROOT); }

    @Override public State unmarshal(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Boolean b) return State.of(b);
        try { return parse(raw.toString()); } catch (InvalidFlagFormatException e) { return null; }
    }

    @Override public String format(State v) { return v == null ? "unset" : v.name().toLowerCase(Locale.ROOT); }

    @Override public List<String> suggestions() { return List.of("allow", "deny"); }

    @Override public String typeName() { return "allow/deny"; }

    /** Builds the resolution chain: this flag, then its parents recursively (depth first, no duplicates). */
    public StateFlag[] chain() {
        StateFlag[] c = chainCache;
        if (c != null) return c;
        java.util.ArrayList<StateFlag> out = new java.util.ArrayList<>(4);
        collect(this, out);
        return chainCache = out.toArray(new StateFlag[0]);
    }

    private static void collect(StateFlag f, java.util.List<StateFlag> out) {
        if (out.contains(f)) return;
        out.add(f);
        for (StateFlag p : f.parents) collect(p, out);
    }

    public boolean chainGated() {
        Boolean g = gatedCache;
        if (g != null) return g;
        boolean r = false;
        for (StateFlag f : chain()) if (f.membershipGated) { r = true; break; }
        return gatedCache = r;
    }
}
