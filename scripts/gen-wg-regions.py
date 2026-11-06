#!/usr/bin/env python3
"""Generates a WorldGuard 7 regions.yml with N random regions (+ addon flags) for CI import / benchmark tests."""
import random, sys

n = int(sys.argv[1]) if len(sys.argv) > 1 else 1000
rnd = random.Random(1337)
out = ["regions:"]

def cuboid(rid, x1, y1, z1, x2, y2, z2, prio=0, flags=None, owners=None, parent=None):
    out.append(f"    {rid}:")
    out.append(f"        min: {{x: {min(x1,x2)}, y: {min(y1,y2)}, z: {min(z1,z2)}}}")
    out.append(f"        max: {{x: {max(x1,x2)}, y: {max(y1,y2)}, z: {max(z1,z2)}}}")
    out.append("        members: {}")
    out.append("        owners:" + (f" {{players: [{owners}]}}" if owners else " {}"))
    out.append("        flags:" + ("" if flags else " {}"))
    for k, v in (flags or {}).items():
        out.append(f"            {k}: {v}")
    out.append("        type: cuboid")
    out.append(f"        priority: {prio}")
    if parent:
        out.append(f"        parent: {parent}")

out.append("    __global__:")
out.append("        members: {}")
out.append("        flags: {}")
out.append("        owners: {}")
out.append("        type: global")
out.append("        priority: 0")

cuboid("spawn", -64, -64, -64, 64, 319, 64, 10, {
    "build": "deny", "pvp": "deny", "mob-spawning": "deny", "greeting": "'&cWelcome to spawn'",
    "farewell-title": "'Bye!'", "invincible": "allow", "build-group": "nonmembers",
    "walk-speed": "0.3", "keep-inventory": "true", "godmode": "allow",
    "give-effects": "[night_vision:0:false]", "blocked-cmds": "['/home', '/sethome']",
    "teleport": "{world: world, x: 0.5, y: -60.0, z: 0.5, yaw: 0.0, pitch: 0.0}",
    "some-third-party-flag": "keepme"}, owners="admin")
cuboid("spawn-shop", -10, -64, -10, 10, 319, 10, 0, {"chest-access": "allow"}, parent="spawn")

out.append("    poly-zone:")
out.append("        min-y: -64")
out.append("        max-y: 319")
out.append("        points:")
for x, z in [(200, 200), (260, 210), (300, 280), (230, 320), (190, 260)]:
    out.append(f"        - {{x: {x}, z: {z}}}")
out.append("        members: {}")
out.append("        owners: {}")
out.append("        flags: {pvp: allow}")
out.append("        type: poly2d")
out.append("        priority: 1")

for i in range(n):
    x, z = rnd.randint(-20000, 20000), rnd.randint(-20000, 20000)
    s = rnd.randint(8, 160)
    flags = {"build": "deny"} if i % 3 == 0 else ({"pvp": "deny"} if i % 3 == 1 else None)
    cuboid(f"claim_{i}", x, -64, z, x + s, 319, z + s, rnd.randint(0, 3), flags, owners=f"player{i % 97}")

print("\n".join(out))
