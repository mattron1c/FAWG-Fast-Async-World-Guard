#!/usr/bin/env bash
# Boots a real Paper server with FAWG, runs a command script and checks the log.
# usage: scripts/smoke-test.sh <minecraft-version> <plugin-jar> [basic|worldguard|fawe]
set -uo pipefail
MC="$1"; JAR="$(realpath "$2")"; MODE="${3:-basic}"
ROOT="$(pwd)"; DIR="$ROOT/run/$MC-$MODE"
rm -rf "$DIR"; mkdir -p "$DIR/plugins"; cd "$DIR"
UA="FastAsyncWorldGuard-CI (github.com/mattron1c/FAWG-Fast-Async-World-Guard)"

URL=$(curl -fsSL -H "User-Agent: $UA" "https://fill.papermc.io/v3/projects/paper/versions/$MC/builds" \
  | jq -r '(map(select(.channel == "STABLE")) + .) | .[0].downloads."server:default".url')
echo "Paper $MC → $URL"
curl -fsSL -H "User-Agent: $UA" -o paper.jar "$URL" || { echo "::error::could not download Paper $MC"; exit 1; }
echo "eula=true" > eula.txt
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\n' > server.properties
cp "$JAR" plugins/

modrinth() {
  curl -fsSL -H "User-Agent: $UA" "https://api.modrinth.com/v2/project/$1/version?loaders=%5B%22paper%22%2C%22bukkit%22%2C%22spigot%22%5D&game_versions=%5B%22$MC%22%5D" \
    | jq -r 'if length == 0 then "null" else .[0].files | ((map(select(.primary)) + .)[0].url) end'
}
case "$MODE" in
  worldguard)
    for s in worldedit worldguard; do
      u=$(modrinth "$s"); echo "$s → $u"
      [ "$u" != "null" ] && curl -fsSL -o "plugins/$s.jar" "$u"
    done
    mkdir -p plugins/WorldGuard/worlds/world plugins/WorldGuardRegionProtect
    python3 "$ROOT/scripts/gen-wg-regions.py" 3000 > plugins/WorldGuard/worlds/world/regions.yml
    printf 'wgRegionProtect:\n  regionProtect:\n    world:\n      - spawn\n' > plugins/WorldGuardRegionProtect/config.yml
    ;;
  fawe)
    u=$(modrinth fastasyncworldedit); echo "FAWE → $u"
    [ "$u" != "null" ] && curl -fsSL -o plugins/FastAsyncWorldEdit.jar "$u"
    ;;
esac

coproc SRV { timeout 600 java -Xms1G -Xmx3G -jar paper.jar --nogui > server.log 2>&1; }
for i in $(seq 1 300); do grep -q 'Done (' server.log 2>/dev/null && break; sleep 1; done
grep -q 'Done (' server.log || { echo "::error::server did not start"; tail -80 server.log; exit 1; }
send() { echo "$1" >&"${SRV[1]}"; sleep "${2:-2}"; }

send "fawg version"
send "fawg stats"
send "fawg flags"
send "fawg flags entities"
send "fawg flags damage 3"
send "rg help"
send "fawg help"
send "rg list"
if [ "$MODE" = "worldguard" ]; then
  send "fawg import worldguard" 25
  send "rg count"
  send "rg info -w world spawn"
  send "rg flags -w world spawn"
  send "rg info -w world poly-zone"
  send "rg info -w world spawn-shop"
  send "fawg benchmark 300000" 40
  send "fawg benchmark 1000000" 90
fi
send "rg flag -w world __global__ pvp deny" 2
send "rg flag -w world __global__ greeting <red>hello" 2
send "rg flags -w world __global__" 2
send "fawg benchmark 200000" 20
send "fawg backup create ci" 5
send "fawg backup list" 2
send "fawg reload" 8
send "rg save" 4
send "stop" 30
wait "$SRV_PID" 2>/dev/null

echo "================ FAWG log lines ================"
grep -n -iE "fastasyncworldguard|fawg|regions|benchmark|ns per|faster|import|flags" server.log | head -250
FAIL=0
grep -q "Enabling FastAsyncWorldGuard" server.log || { echo "::error::FAWG never enabled"; FAIL=1; }
if grep -q "Error occurred while enabling FastAsyncWorldGuard" server.log; then echo "::error::FAWG failed to enable"; FAIL=1; fi
if grep -nE "at dev\.mattron1c\.fawg" server.log; then
  echo "::error::Stack traces from FAWG:"; grep -n -B8 -A2 "at dev\.mattron1c\.fawg" server.log | head -150; FAIL=1
fi
if grep -nE "Command error|HUD action failed|Something went wrong" server.log; then FAIL=1; fi
exit $FAIL
