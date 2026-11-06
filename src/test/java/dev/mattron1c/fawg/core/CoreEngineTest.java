package dev.mattron1c.fawg.core;

import dev.mattron1c.fawg.core.flag.FlagCategory;
import dev.mattron1c.fawg.core.flag.FlagMap;
import dev.mattron1c.fawg.core.flag.FlagRegistry;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.flag.StateFlag.State;
import dev.mattron1c.fawg.core.flag.StringFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.query.FlagResolver;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.region.RegionGroup;
import dev.mattron1c.fawg.core.region.Subject;
import dev.mattron1c.fawg.core.shape.Shape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoreEngineTest {
    static final FlagRegistry REG = new FlagRegistry();
    static final StateFlag PASS = REG.register(new StateFlag("passthrough", FlagCategory.PROTECTION, false));
    static final StateFlag BUILD = REG.register(new StateFlag("build", FlagCategory.PROTECTION, true).gated());
    static final StateFlag INTERACT = REG.register(new StateFlag("interact", FlagCategory.INTERACTION, true).gated().parents(BUILD));
    static final StateFlag DOORS = REG.register(new StateFlag("interact-doors", FlagCategory.INTERACTION, true).gated().parents(INTERACT));
    static final StateFlag PVP = REG.register(new StateFlag("pvp", FlagCategory.PVP, true));
    static final StringFlag GREETING = REG.register(new StringFlag("greeting", FlagCategory.MESSAGES));

    static Subject subject(int id) {
        UUID u = new UUID(0, id);
        return new Subject() {
            public UUID uuid() { return u; }
            public String name() { return "p" + id; }
            public boolean inGroup(String g) { return g.equals("vip") && id == 7; }
        };
    }

    FlagResolver resolver() { FlagResolver r = new FlagResolver(); r.passthroughFlag(PASS); return r; }

    @Test void polygonContainsEdgesAndVertices() {
        Shape.Polygon p = Shape.Polygon.of(List.of(new int[]{0, 0}, new int[]{10, 0}, new int[]{10, 10}, new int[]{0, 10}), 0, 20);
        assertTrue(p.contains(0, 5, 0));
        assertTrue(p.contains(10, 5, 5));
        assertTrue(p.contains(5, 5, 5));
        assertFalse(p.contains(11, 5, 5));
        assertFalse(p.contains(5, 21, 5));
    }

    @Test void membershipAndOwnership() {
        RegionWorld w = new RegionWorld("w");
        Region r = new Region("home", "w", Shape.Cuboid.of(0, 0, 0, 15, 255, 15));
        r.owners().addPlayer(subject(1).uuid());
        r.members().addGroup("vip");
        w.add(r);
        ApplicableSet set = w.query(5, 64, 5);
        FlagResolver fr = resolver();
        assertEquals(State.ALLOW, fr.test(set, BUILD, subject(1)));
        assertEquals(State.ALLOW, fr.test(set, BUILD, subject(7)));
        assertEquals(State.DENY, fr.test(set, BUILD, subject(2)));
        assertEquals(State.ALLOW, fr.test(w.query(100, 64, 100), BUILD, subject(2)));
    }

    @Test void specificFlagOverridesGeneral() {
        RegionWorld w = new RegionWorld("w");
        Region r = new Region("shop", "w", Shape.Cuboid.of(0, 0, 0, 31, 255, 31));
        r.flags().set(INTERACT, State.DENY);
        r.flags().set(DOORS, State.ALLOW);
        w.add(r);
        FlagResolver fr = resolver();
        ApplicableSet set = w.query(1, 1, 1);
        assertEquals(State.ALLOW, fr.test(set, DOORS, subject(3)));
        assertEquals(State.DENY, fr.test(set, INTERACT, subject(3)));
        fr.wgStrict(true);
        assertEquals(State.DENY, fr.test(set, DOORS, subject(3)), "strict mode combines like WorldGuard");
    }

    @Test void priorityAndParents() {
        RegionWorld w = new RegionWorld("w");
        Region big = new Region("big", "w", Shape.Cuboid.of(-100, 0, -100, 100, 255, 100));
        big.flags().set(PVP, State.DENY);
        big.flags().set(GREETING, "big");
        Region arena = new Region("arena", "w", Shape.Cuboid.of(0, 0, 0, 10, 255, 10));
        arena.priority(5);
        arena.flags().set(PVP, State.ALLOW);
        Region child = new Region("child", "w", Shape.Cuboid.of(50, 0, 50, 60, 255, 60));
        child.parent(big);
        w.addAll(List.of(big, arena, child));
        FlagResolver fr = resolver();
        assertEquals(State.ALLOW, fr.test(w.query(5, 5, 5), PVP, null));
        assertEquals(State.DENY, fr.test(w.query(-50, 5, -50), PVP, null));
        assertEquals("big", fr.value(w.query(55, 5, 55), GREETING, null), "inherited from parent");
        assertThrows(IllegalArgumentException.class, () -> big.parent(child));
    }

    @Test void regionGroups() {
        RegionWorld w = new RegionWorld("w");
        Region r = new Region("g", "w", Shape.Cuboid.of(0, 0, 0, 9, 9, 9));
        r.members().addPlayer(subject(1).uuid());
        r.flags().set(PVP, State.DENY);
        r.flags().setGroup(PVP, RegionGroup.NON_MEMBERS);
        w.add(r);
        FlagResolver fr = resolver();
        assertEquals(State.DENY, fr.test(w.query(1, 1, 1), PVP, subject(2)));
        assertEquals(State.ALLOW, fr.test(w.query(1, 1, 1), PVP, subject(1)));
    }

    @Test void exclusionsCarveHoles() {
        RegionWorld w = new RegionWorld("w");
        Region r = new Region("donut", "w", Shape.Cuboid.of(0, 0, 0, 20, 20, 20));
        r.excludes(List.of(Shape.Cuboid.of(5, 0, 5, 10, 20, 10)));
        w.add(r);
        assertTrue(w.query(2, 2, 2).contains(r));
        assertFalse(w.query(7, 2, 7).contains(r));
    }

    @Test void flagMapIsSparse() {
        FlagMap m = new FlagMap();
        m.set(GREETING, "hi");
        m.set(PVP, State.DENY);
        m.set(BUILD, State.ALLOW);
        assertEquals(3, m.size());
        m.set(PVP, null);
        assertEquals(2, m.size());
        assertNull(m.get(PVP));
        assertEquals("hi", m.get(GREETING));
    }

    @Test void indexMatchesBruteForce() {
        Random rnd = new Random(7);
        RegionWorld w = new RegionWorld("w");
        w.largeThreshold(64);
        List<Region> all = new java.util.ArrayList<>();
        for (int i = 0; i < 400; i++) {
            int x = rnd.nextInt(4000) - 2000, z = rnd.nextInt(4000) - 2000, s = 1 + rnd.nextInt(i % 50 == 0 ? 600 : 80);
            Region r = new Region("r" + i, "w", Shape.Cuboid.of(x, -64, z, x + s, 100 + rnd.nextInt(200), z + s));
            r.priority(rnd.nextInt(4));
            all.add(r);
        }
        w.addAll(all);
        for (int i = 0; i < 20000; i++) {
            int x = rnd.nextInt(4400) - 2200, y = rnd.nextInt(380) - 64, z = rnd.nextInt(4400) - 2200;
            Region[] got = w.queryRaw(x, y, z);
            long expected = all.stream().filter(r -> r.contains(x, y, z)).count();
            assertEquals(expected, got.length, "at " + x + "," + y + "," + z);
            for (int k = 1; k < got.length; k++) assertTrue(got[k - 1].priority() >= got[k].priority());
        }
    }
}
