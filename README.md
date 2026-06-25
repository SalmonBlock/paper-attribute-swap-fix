# PASF — Paper Attribute Swap Fix

A Paper 1.21.1 - 26.1.2 plugin to easily toggle attribute swapping on Paper servers, with fixes to make it as close to vanilla as possible.

## What is Attribute Swapping?

Attribute swapping ([MC-28289](https://bugs.mojang.com/browse/MC-28289)) is a vanilla Minecraft mechanic where swapping weapons within 1-2 ticks of an attack causes the game to mix attributes from both items — using one weapon's base damage with another weapon's enchantments.

Paper blocks this by default. PASF flips the switch with one command and patches the remaining edge cases (cooldown ticker, attribute carryover) so the behavior stays as close to vanilla as possible.

## How it Works

1. **Disables Paper's `update-equipment-on-player-actions`** at runtime via reflection
2. **Preserves the attack cooldown ticker** across item swaps using NMS reflection, counteracting Paper's cooldown reset
3. **Falls back to temporary attribute modifiers** if the config toggle is unavailable

## Versions

Two builds are published per release — pick the one matching your server:

| Folder | Server versions | Jar |
|--------|-----------------|-----|
| [`paper-26.1/`](paper-26.1) | Paper 26.1.x | `pasf-1.1.0.jar` |
| [`paper-1.21/`](paper-1.21) | Paper 1.21.1 – 1.21.11 | `pasf-1.1.0-paper1.21.jar` |

The code is identical; the builds differ only in the targeted Paper API.

## Installation

1. Drop the matching `pasf-1.1.0*.jar` into your server's `plugins/` folder
2. Restart the server
3. Attribute swapping is enabled by default

## Commands

| Command | Permission    | Description                        |
|---------|---------------|------------------------------------|
| `/pasf` | `pasf.toggle` | Toggle attribute swapping on/off |

Permission defaults to **op**.

## Config

```yaml
# config.yml
enabled: true
```

## Building

Requires Java 21 and Maven.

```sh
# Paper 26.1.x build
mvn -f paper-26.1/pom.xml clean package      # -> paper-26.1/target/pasf-1.1.0.jar

# Paper 1.21.1 - 1.21.11 build
mvn -f paper-1.21/pom.xml clean package      # -> paper-1.21/target/pasf-1.1.0-paper1.21.jar
```

## License

[LGPL-3.0-only](LICENSE)
