# Changelog

All notable changes to FastAsyncWorldGuard are listed here. Format: [Keep a Changelog](https://keepachangelog.com).

## [1.0.0] - 2026-11-06
### Added
- Chunk-indexed, lock-free region engine (cuboid, polygon, cylinder, sphere, global, exclusions).
- 1,000+ flags: all WorldGuard flags, all WorldGuard-ExtraFlags-Plus flags, WorldGuardRegionProtect locks, and
  generated families per mob, damage cause, spawn reason, potion effect, block / item category, inventory,
  teleport cause, ignition, explosion source, projectile and transformation.
- Specific-over-general flag resolution (`interact-doors` beats `interact`), optional WorldGuard strict mode.
- Anti ghost-block system (prediction lock + ping-aware resync) for Java and Bedrock players.
- Native dialog HUD (regions, flags, members, owners, settings, presets, locks, selection, admin) and chest GUI for Bedrock.
- Built-in selection wand and red particle visualizer; works without WorldEdit.
- WorldEdit / FAWE protection (edit sessions, FAWE mask manager, guarded commands, super pickaxe, brushes).
- `/fawg import worldguard` (regions, flags, addon flags, RegionProtect lists) and `/fawg cleanup confirm`.
- Async atomic saving, rolling zip backups, action log, PlaceholderAPI expansion, public API and events.
- `/fawg benchmark` side-by-side comparison with WorldGuard.
