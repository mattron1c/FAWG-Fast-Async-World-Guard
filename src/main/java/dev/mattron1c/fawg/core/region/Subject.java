package dev.mattron1c.fawg.core.region;

import java.util.UUID;

/** Anything that can be a member of a region: normally an online/offline player. */
public interface Subject {
    UUID uuid();

    String name();

    /** Permission-group membership (eg. LuckPerms "group.vip"). */
    boolean inGroup(String group);
}
