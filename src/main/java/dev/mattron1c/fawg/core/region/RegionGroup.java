package dev.mattron1c.fawg.core.region;

import java.util.Locale;

/** Region groups used to scope a flag value to a subset of players (WorldGuard compatible names). */
public enum RegionGroup {
    ALL(true, true, true),
    MEMBERS(true, true, false),
    OWNERS(true, false, false),
    NON_MEMBERS(false, false, true),
    NON_OWNERS(false, true, true),
    NONE(false, false, false);

    private final boolean owner, member, nonMember;

    RegionGroup(boolean owner, boolean member, boolean nonMember) {
        this.owner = owner; this.member = member; this.nonMember = nonMember;
    }

    public boolean contains(Association a) {
        return switch (a) {
            case OWNER -> owner;
            case MEMBER -> member;
            case NON_MEMBER -> nonMember;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static RegionGroup parse(String s) {
        if (s == null) return null;
        String k = s.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (k) {
            case "all", "everyone", "everybody", "*" -> ALL;
            case "members", "member", "m" -> MEMBERS;
            case "owners", "owner", "o" -> OWNERS;
            case "nonmembers", "non-members", "nonmember", "non-member", "nm" -> NON_MEMBERS;
            case "nonowners", "non-owners", "nonowner", "non-owner", "no" -> NON_OWNERS;
            case "none", "nobody" -> NONE;
            default -> null;
        };
    }
}
