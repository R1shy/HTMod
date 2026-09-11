# HTMod

Fabric 26.2 port of the implemented features from [HTPlugin](https://github.com/R1shy/HTPlugin).

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.5 or newer
- Fabric API
- WorldEdit Fabric 7.4.5 or newer

WorldEdit is a required runtime mod because it performs the scene clipboard copy.

## Sitting

Sitting follows the server-side behavior of [Sit!](https://github.com/Oth3r/Sit):

- Use `/sit` to sit on the block you are looking at or the block beneath you.
- Right-click a valid block with the configured hand restrictions to sit on it.
- Right-click another player to sit on them.
- Players can form unlimited sitting chains and additional passengers stack vertically.
- Sneak to dismount. Riders keep their own camera rotation.

By default, bottom stairs, bottom slabs, and carpets are valid seats. Full blocks and custom block rules can be enabled in the `sitting` section of `config/hadestown.json`. Seat entities are removed when their block becomes invalid, and the existing scene-control note blocks remain reserved for scene navigation.

## Configuration

The first server start creates `config/hadestown.json` from the bundled defaults. It contains the staff names, control positions, stage position, scene geometry, and configured world identifier.

If `config/hadestown.yml`, `config/hadestown.yaml`, or the legacy `config/config.yml` exists while the JSON file is absent, it is converted automatically. The YAML file is kept as a backup and the converted JSON becomes the active configuration.

All set coordinates use zero-based values: `x` is the set within a scene and `y` is the scene.

## Commands

```text
/scene set <x> <y>
```

Forward and reverse note blocks at the configured positions load the next or previous set. Source air blocks are pasted too, and entities and biomes are not copied.

Build with:

```text
./gradlew build
```
