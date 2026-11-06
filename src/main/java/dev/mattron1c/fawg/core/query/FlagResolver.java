package dev.mattron1c.fawg.core.query;

import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.flag.StateFlag.State;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.region.Association;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.region.RegionGroup;
import dev.mattron1c.fawg.core.region.Subject;

/**
 * Flag resolution engine. Allocation free on the hot path.
 *
 * Semantics (compatible with WorldGuard where it matters):
 *  - Only the highest priority regions that define a value are considered (lower priorities are shadowed).
 *  - A region inherits a flag from its parent chain when it does not define it.
 *  - A parent region present in the same set as its child is ignored (the child overrides it).
 *  - Region groups (-g members / owners / nonmembers...) limit who a value applies to.
 *  - For state flags multiple values at the same priority combine with DENY winning.
 *  - Specific flags override general ones (interact-doors beats interact) unless wgStrict mode is enabled,
 *    in which case every flag in the chain is combined and any DENY wins (exact WorldGuard behaviour).
 *  - Membership gated flags (build & friends) fall back to region membership when nothing is set.
 */
public final class FlagResolver {
    public enum Membership { NO_REGIONS, SUCCESS, FAIL }

    private volatile boolean wgStrict;

    public void wgStrict(boolean b) { this.wgStrict = b; }
    public boolean wgStrict() { return wgStrict; }

    // ----------------------------------------------------------------------------------------------- values

    /** Effective value of a flag on one region, walking parents and honouring region groups. */
    public static <T> T effective(Region region, Flag<T> flag, Subject subject) {
        for (Region cur = region; cur != null; cur = cur.parent()) {
            T v = cur.flags().get(flag);
            if (v != null) {
                RegionGroup g = cur.flags().group(flag);
                if (g == null) g = flag.defaultGroup();
                if (g == null) continue;
                Association a = subject == null ? Association.NON_MEMBER : region.association(subject);
                if (g.contains(a)) return v;
            }
        }
        return null;
    }

    /** Highest-priority value of a non-state flag (falls back to global, then the flag default). */
    public <T> T value(ApplicableSet set, Flag<T> flag, Subject subject) {
        T v = regionValue(set, flag, subject);
        if (v != null) return v;
        if (set.global() != null) {
            v = effective(set.global(), flag, subject);
            if (v != null) return v;
        }
        return flag.defaultValue();
    }

    /** Value from real regions only (no global / default), or null. */
    public <T> T regionValue(ApplicableSet set, Flag<T> flag, Subject subject) {
        Region[] rs = set.regions();
        if (rs.length == 0) return null;
        int minPriority = Integer.MIN_VALUE;
        T found = null;
        for (int i = 0; i < rs.length; i++) {
            Region r = rs[i];
            if (r.priority() < minPriority) break;
            if (ignored(rs, i)) continue;
            T v = effective(r, flag, subject);
            if (v != null) {
                if (found == null) { found = v; minPriority = r.priority(); }
                else if (flag instanceof StateFlag && v == State.DENY) found = v;
            }
        }
        return found;
    }

    /** Region i is ignored when it is a parent of an earlier (higher or equal priority) region in the set. */
    private static boolean ignored(Region[] rs, int i) {
        Region r = rs[i];
        for (int j = 0; j < i; j++) if (r.isAncestorOf(rs[j])) return true;
        // also ignore parents that appear before their children at the same priority (sorting puts deeper first)
        return false;
    }

    // ----------------------------------------------------------------------------------------------- state

    /**
     * Tests a state flag with its parent chain. Returns ALLOW/DENY, never null.
     */
    public State test(ApplicableSet set, StateFlag flag, Subject subject) {
        return test(set, flag.chain(), flag.chainGated(), subject);
    }

    public State test(ApplicableSet set, StateFlag[] chain, boolean gated, Subject subject) {
        if (wgStrict) {
            State combined = null;
            for (StateFlag f : chain) {
                State s = regionValue(set, f, subject);
                if (s == State.DENY) return State.DENY;
                if (s == State.ALLOW) combined = State.ALLOW;
            }
            if (combined != null) return combined;
        } else {
            for (StateFlag f : chain) {
                State s = regionValue(set, f, subject);
                if (s != null) return s;
            }
        }
        if (gated) {
            Membership m = membership(set, subject);
            if (m == Membership.FAIL) return State.DENY;
            if (m == Membership.SUCCESS) return State.ALLOW;
        }
        Region g = set.global();
        if (g != null) {
            for (StateFlag f : chain) {
                State s = effective(g, f, subject);
                if (s != null) return s;
            }
            if (gated && subject != null && !(g.owners().isEmpty() && g.members().isEmpty())) {
                return g.isMember(subject) ? State.ALLOW : State.DENY;
            }
        }
        for (StateFlag f : chain) {
            State d = f.defaultValue();
            if (d != null) return d;
        }
        return State.ALLOW;
    }

    public boolean allows(ApplicableSet set, StateFlag flag, Subject subject) {
        return test(set, flag, subject) == State.ALLOW;
    }

    /** WorldGuard-like membership test over the highest priority non-passthrough regions. */
    public Membership membership(ApplicableSet set, Subject subject) {
        Region[] rs = set.regions();
        if (rs.length == 0) return Membership.NO_REGIONS;
        int minPriority = Integer.MIN_VALUE;
        boolean any = false, nonMember = false;
        for (int i = 0; i < rs.length; i++) {
            Region r = rs[i];
            if (r.priority() < minPriority) break;
            if (passthrough != null && effective(r, passthrough, subject) == State.ALLOW) continue;
            if (ignored(rs, i)) continue;
            minPriority = r.priority();
            any = true;
            if (subject == null || !r.isMember(subject)) nonMember = true;
        }
        if (!any) return Membership.NO_REGIONS;
        return nonMember ? Membership.FAIL : Membership.SUCCESS;
    }

    private volatile StateFlag passthrough;

    /** The passthrough flag makes a region ignored for membership checks. */
    public void passthroughFlag(StateFlag f) { this.passthrough = f; }
}
